import { createEventStream } from 'h3';
import { requireWebSession } from '../services/auth';
import { subscribeStorageEvents } from '../services/events';
import { ApiRequestError, defineApiHandler } from '../utils/api';
import { API_ERROR_CODES } from '../../shared/types/api';

export default defineApiHandler(async (event) => {
  const { serverId } = requireWebSession(event);
  if (!serverId) {
    throw new ApiRequestError(API_ERROR_CODES.FORBIDDEN, 'A server-scoped session is required.', 403);
  }

  const stream = createEventStream(event);
  const unsubscribe = subscribeStorageEvents(serverId, stream);
  stream.onClosed(unsubscribe);
  await stream.push({ event: 'ready', data: '{}' });
  return stream.send();
});
