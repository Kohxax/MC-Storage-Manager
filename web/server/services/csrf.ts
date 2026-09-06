import type { H3Event } from 'h3';
import { deleteCookie, getCookie, getHeader, setCookie } from 'h3';
import { API_ERROR_CODES } from '../../shared/types/api';
import { ApiRequestError } from '../utils/api';
import { constantTimeStringEqual, createOpaqueToken } from './security';

export const CSRF_COOKIE_NAME = 'mcsm_csrf';

function cookieSecure(): boolean {
  return process.env.COOKIE_SECURE === 'true';
}

export function issueCsrfToken(event: H3Event): string {
  const token = createOpaqueToken(32);
  setCookie(event, CSRF_COOKIE_NAME, token, {
    httpOnly: false,
    secure: cookieSecure(),
    sameSite: 'lax',
    path: '/',
    maxAge: 60 * 60 * 12,
  });
  return token;
}

export function requireCsrf(event: H3Event): void {
  const cookieToken = getCookie(event, CSRF_COOKIE_NAME);
  const headerToken = getHeader(event, 'x-csrf-token');
  if (!cookieToken || !headerToken || !constantTimeStringEqual(cookieToken, headerToken)) {
    throw new ApiRequestError(API_ERROR_CODES.FORBIDDEN, 'CSRF token is required.', 403);
  }
}

export function clearCsrfToken(event: H3Event): void {
  deleteCookie(event, CSRF_COOKIE_NAME, { path: '/' });
}
