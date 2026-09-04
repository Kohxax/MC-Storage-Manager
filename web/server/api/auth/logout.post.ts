import { requireWebSession, logout } from '../../services/auth';
import { requireCsrf } from '../../services/csrf';
import { apiSuccess, defineApiHandler } from '../../utils/api';

export default defineApiHandler((event) => {
  requireWebSession(event);
  requireCsrf(event);
  logout(event);
  return apiSuccess(event, { loggedOut: true });
});
