import { getCookie } from 'h3';
import { publicPlayer, requireWebSession } from '../services/auth';
import { apiSuccess, defineApiHandler } from '../utils/api';
import { CSRF_COOKIE_NAME, issueCsrfToken } from '../services/csrf';

export default defineApiHandler((event) => {
  const context = requireWebSession(event);
  const csrfToken = getCookie(event, CSRF_COOKIE_NAME) ?? issueCsrfToken(event);
  return apiSuccess(event, {
    player: publicPlayer(context),
    session: { id: context.session.id, expiresAt: context.session.expiresAt },
    csrfToken,
  });
});
