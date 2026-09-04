import type { H3Event } from 'h3';
import { defineEventHandler, getHeader, setResponseHeader, setResponseStatus } from 'h3';
import type {
  ApiDetails,
  ApiErrorCode,
  ApiErrorResponse,
  ApiSuccessResponse,
} from '../../shared/types/api';
import { API_ERROR_CODES } from '../../shared/types/api';
import { createEntityId, isEntityId, parseEntityId, type EntityId } from '../../shared/types/id';
import { RevisionConflictError } from '../../shared/types/revision';

export class ApiRequestError extends Error {
  readonly code: ApiErrorCode;
  readonly statusCode: number;
  readonly details?: ApiDetails;

  constructor(code: ApiErrorCode, message: string, statusCode = statusForCode(code), details?: ApiDetails) {
    super(message);
    this.name = 'ApiRequestError';
    this.code = code;
    this.statusCode = statusCode;
    this.details = details;
  }
}

function statusForCode(code: ApiErrorCode): number {
  switch (code) {
    case API_ERROR_CODES.BAD_REQUEST:
      return 400;
    case API_ERROR_CODES.UNAUTHORIZED:
      return 401;
    case API_ERROR_CODES.FORBIDDEN:
      return 403;
    case API_ERROR_CODES.NOT_FOUND:
      return 404;
    case API_ERROR_CODES.CONFLICT:
    case API_ERROR_CODES.REVISION_CONFLICT:
      return 409;
    case API_ERROR_CODES.RATE_LIMITED:
      return 429;
    default:
      return 500;
  }
}

export function getApiRequestId(event: H3Event): EntityId {
  const existing = event.context.apiRequestId as EntityId | undefined;
  if (existing) {
    return existing;
  }

  const supplied = getHeader(event, 'x-request-id');
  const id = supplied && isEntityId(supplied) ? parseEntityId(supplied) : createEntityId();
  event.context.apiRequestId = id;
  setResponseHeader(event, 'x-request-id', id);
  return id;
}

export function apiSuccess<T>(event: H3Event, data: T, statusCode = 200): ApiSuccessResponse<T> {
  const requestId = getApiRequestId(event);
  setResponseStatus(event, statusCode);
  return { data, requestId };
}

export function apiError(event: H3Event, error: unknown): ApiErrorResponse {
  const requestId = getApiRequestId(event);
  const normalized = normalizeApiError(error);
  setResponseStatus(event, normalized.statusCode);
  return {
    error: {
      code: normalized.code,
      message: normalized.publicMessage,
      ...(normalized.details ? { details: normalized.details } : {}),
    },
    requestId,
  };
}

interface NormalizedApiError {
  code: ApiErrorCode;
  statusCode: number;
  publicMessage: string;
  details?: ApiDetails;
}

function normalizeApiError(error: unknown): NormalizedApiError {
  if (error instanceof ApiRequestError) {
    return {
      code: error.code,
      statusCode: error.statusCode,
      publicMessage: error.message,
      details: error.details,
    };
  }
  if (error instanceof RevisionConflictError) {
    return {
      code: API_ERROR_CODES.REVISION_CONFLICT,
      statusCode: 409,
      publicMessage: error.message,
    };
  }
  return {
    code: API_ERROR_CODES.INTERNAL_ERROR,
    statusCode: 500,
    publicMessage: 'An unexpected error occurred.',
  };
}

export function defineApiHandler<T>(handler: (event: H3Event) => T | Promise<T>) {
  return defineEventHandler(async (event) => {
    getApiRequestId(event);
    try {
      return await handler(event);
    } catch (error) {
      return apiError(event, error);
    }
  });
}
