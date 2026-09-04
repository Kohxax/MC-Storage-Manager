import { z } from 'zod';
import { getCookie } from 'h3';
import { publicPlayer, redeemLoginToken } from '../../services/auth';
import { apiSuccess, defineApiHandler } from '../../utils/api';
import { readSchemaBody } from '../../utils/validation';
import { CSRF_COOKIE_NAME } from '../../services/csrf';

const schema = z.object({ token: z.string().min(40).max(128) }).strict();

export default defineApiHandler(async (event) => {
  const body = await readSchemaBody(event, schema);
  const context = redeemLoginToken(event, body.token);
  return apiSuccess(event, {
    player: publicPlayer(context),
    session: { id: context.session.id, expiresAt: context.session.expiresAt },
    csrfToken: getCookie(event, CSRF_COOKIE_NAME) ?? null,
  });
});
