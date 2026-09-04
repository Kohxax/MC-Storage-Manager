import type { EntityId } from './id';

export const API_ERROR_CODES = {
  BAD_REQUEST: 'BAD_REQUEST',
  UNAUTHORIZED: 'UNAUTHORIZED',
  FORBIDDEN: 'FORBIDDEN',
  NOT_FOUND: 'NOT_FOUND',
  CONFLICT: 'CONFLICT',
  REVISION_CONFLICT: 'REVISION_CONFLICT',
  RATE_LIMITED: 'RATE_LIMITED',
  INTERNAL_ERROR: 'INTERNAL_ERROR',
} as const;

export type ApiErrorCode = (typeof API_ERROR_CODES)[keyof typeof API_ERROR_CODES];
export type ApiDetails = Record<string, unknown>;

export interface ApiErrorBody {
  code: ApiErrorCode;
  message: string;
  details?: ApiDetails;
}

export interface ApiErrorResponse {
  error: ApiErrorBody;
  requestId: EntityId;
}

export interface ApiSuccessResponse<T> {
  data: T;
  requestId: EntityId;
}

export type ApiResponse<T> = ApiSuccessResponse<T> | ApiErrorResponse;

export function isApiErrorResponse<T>(response: ApiResponse<T>): response is ApiErrorResponse {
  return 'error' in response;
}
