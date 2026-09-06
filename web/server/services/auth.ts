import type { H3Event } from 'h3';
import { deleteCookie, getCookie, getHeader, setCookie } from 'h3';
import { API_ERROR_CODES } from '../../shared/types/api';
import { nowIsoDateTime } from '../../shared/types/datetime';
import { ApiRequestError } from '../utils/api';
import { getDatabaseHandle, type AppDatabase } from '../db/client';
import { LoginTokenRepository } from '../db/repositories/login-tokens';
import { PlayerRepository } from '../db/repositories/players';
import { ServerRepository } from '../db/repositories/servers';
import { SessionRepository } from '../db/repositories/sessions';
import type { Server } from '../db/schema';
import { enforceRateLimit } from './rate-limit';
import { clearCsrfToken, issueCsrfToken } from './csrf';
import { createOpaqueToken, hashApiKey, hashOpaqueToken, verifyApiKey } from './security';
import { assertPermission, getPlayerPermissions, PERMISSIONS } from './permissions';
import { parseEntityId } from '../../shared/types/id';

export const SESSION_COOKIE_NAME = 'mcsm_session';
const SESSION_TTL_SECONDS = 60 * 60 * 12;
const LOGIN_TOKEN_TTL_SECONDS = 60 * 5;

export interface PluginAuthContext {
  server: Server;
}

export interface WebAuthContext {
  session: NonNullable<ReturnType<SessionRepository['findActiveByTokenHash']>>;
  player: NonNullable<ReturnType<PlayerRepository['findById']>>;
  serverId: string | null;
  permissions: string[];
}

export interface RedeemedLoginToken {
  session: WebAuthContext['session'];
  player: WebAuthContext['player'];
  serverId: string;
  permissions: string[];
  rawSession: string;
}

function cookieSecure(): boolean {
  // Public HTTPS deployments opt in through COOKIE_SECURE=true.  Do not infer
  // this from NODE_ENV: a production build can also be served on local HTTP.
  return process.env.COOKIE_SECURE === 'true';
}

function sessionCookieOptions() {
  return {
    httpOnly: true,
    secure: cookieSecure(),
    sameSite: 'lax' as const,
    path: '/',
    maxAge: SESSION_TTL_SECONDS,
  };
}

function readPluginApiKey(event: H3Event): string | undefined {
  const authorization = getHeader(event, 'authorization');
  if (authorization) {
    const match = /^Bearer\s+(.+)$/i.exec(authorization.trim());
    if (match?.[1]) return match[1].trim();
  }
  const headerKey = getHeader(event, 'x-api-key');
  return headerKey?.trim() || undefined;
}

export function requirePluginAuth(event: H3Event): PluginAuthContext {
  enforceRateLimit(event, 'plugin', 120);
  const apiKey = readPluginApiKey(event);
  if (!apiKey) {
    throw new ApiRequestError(API_ERROR_CODES.UNAUTHORIZED, 'Plugin authentication is required.', 401);
  }

  const database = getDatabaseHandle().db;
  const servers = new ServerRepository(database);
  const requestedServerId = getHeader(event, 'x-server-id')?.trim();
  if (!requestedServerId) {
    throw new ApiRequestError(API_ERROR_CODES.BAD_REQUEST, 'The x-server-id header is required.', 400);
  }
  let serverId: string;
  try {
    serverId = parseEntityId(requestedServerId);
  } catch {
    throw new ApiRequestError(API_ERROR_CODES.UNAUTHORIZED, 'Invalid plugin credentials.', 401);
  }
  const server = servers.findById(serverId);
  if (server && verifyApiKey(apiKey, server.apiKeyHash)) {
    event.context.pluginServer = server;
    return { server };
  }
  throw new ApiRequestError(API_ERROR_CODES.UNAUTHORIZED, 'Invalid plugin credentials.', 401);
}

export function getPluginAuth(event: H3Event): PluginAuthContext | undefined {
  return event.context.pluginServer ? { server: event.context.pluginServer } : undefined;
}

export function createApiKeyHash(apiKey: string): string {
  return hashApiKey(apiKey);
}

export function createLoginLink(event: H3Event, serverId: string, playerId: string): { url: string; expiresAt: string } {
  const database = getDatabaseHandle().db;
  const server = new ServerRepository(database).findById(serverId);
  if (!server) throw new ApiRequestError(API_ERROR_CODES.NOT_FOUND, 'Server was not found.', 404);
  const rawToken = createOpaqueToken(32);
  const expiresAt = new Date(Date.now() + LOGIN_TOKEN_TTL_SECONDS * 1000).toISOString();
  new LoginTokenRepository(database).create({
    serverId,
    playerId,
    tokenHash: hashOpaqueToken(rawToken),
    expiresAt,
  });
  let url: URL;
  try {
    url = new URL('/auth/redeem', server.publicUrl);
  } catch {
    throw new ApiRequestError(API_ERROR_CODES.INTERNAL_ERROR, 'Server public URL is invalid.', 500);
  }
  url.searchParams.set('token', rawToken);
  return { url: url.toString(), expiresAt };
}

export function requireWebSession(event: H3Event): WebAuthContext {
  const existing = event.context.webAuth as WebAuthContext | undefined;
  if (existing) return existing;
  const rawToken = getCookie(event, SESSION_COOKIE_NAME);
  if (!rawToken || rawToken.length < 32) {
    throw new ApiRequestError(API_ERROR_CODES.UNAUTHORIZED, 'Authentication is required.', 401);
  }
  const database = getDatabaseHandle().db;
  const session = new SessionRepository(database).findActiveByTokenHash(hashOpaqueToken(rawToken), nowIsoDateTime());
  if (!session) {
    throw new ApiRequestError(API_ERROR_CODES.UNAUTHORIZED, 'Authentication is required.', 401);
  }
  const player = new PlayerRepository(database).findById(session.playerId);
  if (!player) {
    throw new ApiRequestError(API_ERROR_CODES.UNAUTHORIZED, 'Authentication is required.', 401);
  }
  const permissions = getPlayerPermissions(database, player, session.serverId);
  const context = { session, player, serverId: session.serverId ?? null, permissions };
  event.context.webAuth = context;
  return context;
}

/**
 * Exchanges a one-time login token and creates its session atomically.
 * Any validation or persistence failure rolls the token consumption back.
 */
export function redeemLoginTokenInDatabase(database: AppDatabase, rawToken: string): RedeemedLoginToken {
  if (!/^[A-Za-z0-9_-]{40,128}$/.test(rawToken)) {
    throw new ApiRequestError(API_ERROR_CODES.UNAUTHORIZED, 'The login token is invalid or expired.', 401);
  }
  const rawSession = createOpaqueToken(32);
  const expiresAt = new Date(Date.now() + SESSION_TTL_SECONDS * 1000).toISOString();
  const loginTokens = new LoginTokenRepository(database);
  const players = new PlayerRepository(database);
  const sessions = new SessionRepository(database);

  return database.transaction((transaction) => {
    const token = loginTokens.consumeIn(transaction, hashOpaqueToken(rawToken));
    if (!token?.playerId) {
      throw new ApiRequestError(API_ERROR_CODES.UNAUTHORIZED, 'The login token is invalid or expired.', 401);
    }
    const player = players.findByIdIn(transaction, token.playerId);
    if (!player) {
      throw new ApiRequestError(API_ERROR_CODES.UNAUTHORIZED, 'The login token is invalid or expired.', 401);
    }
    assertPermission(transaction, player, PERMISSIONS.WEB_LOGIN, token.serverId);
    const session = sessions.createIn(transaction, {
      serverId: token.serverId,
      playerId: player.id,
      tokenHash: hashOpaqueToken(rawSession),
      expiresAt,
    });
    const permissions = getPlayerPermissions(transaction, player, token.serverId);
    return { session, player, serverId: token.serverId, permissions, rawSession };
  });
}

export function redeemLoginToken(event: H3Event, rawToken: string): WebAuthContext {
  enforceRateLimit(event, 'auth-redeem', 5);
  const redeemed = redeemLoginTokenInDatabase(getDatabaseHandle().db, rawToken);
  setCookie(event, SESSION_COOKIE_NAME, redeemed.rawSession, sessionCookieOptions());
  issueCsrfToken(event);
  const context = {
    session: redeemed.session,
    player: redeemed.player,
    serverId: redeemed.serverId,
    permissions: redeemed.permissions,
  };
  event.context.webAuth = context;
  return context;
}

export function publicPlayer(context: WebAuthContext) {
  return { ...context.player, permissions: context.permissions };
}

export function logout(event: H3Event): void {
  const context = requireWebSession(event);
  new SessionRepository(getDatabaseHandle().db).revokeById(context.session.id);
  deleteCookie(event, SESSION_COOKIE_NAME, { path: '/' });
  clearCsrfToken(event);
  event.context.webAuth = undefined;
}
