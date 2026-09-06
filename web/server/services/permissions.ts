import { and, eq } from 'drizzle-orm';
import type { Player, Region } from '../db/schema';
import { playerServerPermissions, regionAcl } from '../db/schema';
import type { DatabaseExecutor } from '../db/client';
import { API_ERROR_CODES } from '../../shared/types/api';
import { ApiRequestError } from '../utils/api';

export const PERMISSIONS = {
  REGION_VIEW: 'storage.region.view',
  REGION_CREATE: 'storage.region.create',
  REGION_MANAGE_OWN: 'storage.region.manage.own',
  REGION_MANAGE_ANY: 'storage.region.manage.any',
  WEB_LOGIN: 'storage.web.login',
  ADMIN: 'storage.admin',
} as const;

/**
 * Returns permissions for the server represented by the current web session.
 * The legacy player column is used only for unscoped records and old sessions;
 * server-bound records must have an explicit row in player_server_permissions.
 */
export function getPlayerPermissions(database: DatabaseExecutor, player: Player, serverId?: string | null): string[] {
  if (serverId) {
    return (
      database
        .select({ permissions: playerServerPermissions.permissions })
        .from(playerServerPermissions)
        .where(and(eq(playerServerPermissions.serverId, serverId), eq(playerServerPermissions.playerId, player.id)))
        .get()?.permissions ?? []
    );
  }
  return Array.isArray(player.permissions) ? [...player.permissions] : [];
}

function isLinkedToServer(database: DatabaseExecutor, player: Player, serverId: string): boolean {
  return Boolean(
    database
      .select({ playerId: playerServerPermissions.playerId })
      .from(playerServerPermissions)
      .where(and(eq(playerServerPermissions.serverId, serverId), eq(playerServerPermissions.playerId, player.id)))
      .get(),
  );
}

function hasPermission(database: DatabaseExecutor, player: Player, permission: string, serverId?: string | null): boolean {
  return getPlayerPermissions(database, player, serverId).includes(permission);
}

function isAdmin(database: DatabaseExecutor, player: Player, serverId?: string | null): boolean {
  return hasPermission(database, player, PERMISSIONS.ADMIN, serverId);
}

/**
 * `currentServerId` is optional for callers that predate server-scoped web
 * sessions. Web API callers pass it explicitly, including null, so a session
 * created for server A cannot enumerate server B's regions.
 */
function isRegionInSessionScope(database: DatabaseExecutor, player: Player, region: Region, currentServerId?: string | null): boolean {
  if (!region.serverId) return true;
  if (currentServerId !== undefined && currentServerId !== region.serverId) return false;
  return isLinkedToServer(database, player, region.serverId);
}

function permissionServerId(region: Region, currentServerId?: string | null): string | null | undefined {
  if (currentServerId !== undefined) return currentServerId;
  return region.serverId;
}

export function canViewRegion(
  database: DatabaseExecutor,
  player: Player,
  region: Region,
  currentServerId?: string | null,
): boolean {
  if (!isRegionInSessionScope(database, player, region, currentServerId)) return false;
  const serverId = permissionServerId(region, currentServerId);
  if (isAdmin(database, player, serverId) || region.ownerPlayerId === player.id) return true;
  if (hasPermission(database, player, PERMISSIONS.REGION_VIEW, serverId)) {
    const acl = database
      .select()
      .from(regionAcl)
      .where(and(eq(regionAcl.regionId, region.id), eq(regionAcl.playerId, player.id), eq(regionAcl.principalType, 'player')))
      .get();
    return Boolean(acl);
  }
  const acl = database
    .select()
    .from(regionAcl)
    .where(and(eq(regionAcl.regionId, region.id), eq(regionAcl.playerId, player.id), eq(regionAcl.principalType, 'player')))
    .get();
  return Boolean(acl);
}

export function canManageRegion(
  database: DatabaseExecutor,
  player: Player,
  region: Region,
  currentServerId?: string | null,
): boolean {
  if (region.status === 'deleted' || !isRegionInSessionScope(database, player, region, currentServerId)) return false;
  const serverId = permissionServerId(region, currentServerId);
  if (isAdmin(database, player, serverId) || hasPermission(database, player, PERMISSIONS.REGION_MANAGE_ANY, serverId)) {
    return true;
  }
  if (region.ownerPlayerId === player.id && hasPermission(database, player, PERMISSIONS.REGION_MANAGE_OWN, serverId)) {
    return true;
  }
  const acl = database
    .select()
    .from(regionAcl)
    .where(and(eq(regionAcl.regionId, region.id), eq(regionAcl.playerId, player.id), eq(regionAcl.principalType, 'player')))
    .get();
  return acl?.permission === 'manager';
}

/** Deliberately returns NOT_FOUND for unauthorized resources to avoid IDOR enumeration. */
export function assertRegionView(
  database: DatabaseExecutor,
  player: Player,
  region: Region | undefined,
  currentServerId?: string | null,
): Region {
  if (!region || region.status === 'deleted' || !canViewRegion(database, player, region, currentServerId)) {
    throw new ApiRequestError(API_ERROR_CODES.NOT_FOUND, 'Region was not found.', 404);
  }
  return region;
}

export function assertRegionManage(
  database: DatabaseExecutor,
  player: Player,
  region: Region | undefined,
  currentServerId?: string | null,
): Region {
  if (!region || !canManageRegion(database, player, region, currentServerId)) {
    throw new ApiRequestError(API_ERROR_CODES.NOT_FOUND, 'Region was not found.', 404);
  }
  return region;
}

export function assertPluginRegion(region: Region | undefined, serverId: string): Region {
  if (!region || !region.serverId || region.serverId !== serverId || region.status === 'deleted') {
    throw new ApiRequestError(API_ERROR_CODES.NOT_FOUND, 'Region was not found.', 404);
  }
  return region;
}

export function assertPermission(
  database: DatabaseExecutor,
  player: Player,
  permission: string,
  serverId?: string | null,
): void {
  if (!hasPermission(database, player, permission, serverId) && !isAdmin(database, player, serverId)) {
    throw new ApiRequestError(API_ERROR_CODES.FORBIDDEN, 'You do not have permission to perform this action.', 403);
  }
}
