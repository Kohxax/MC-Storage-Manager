import type { H3Event } from 'h3';
import { getRouterParam, readBody } from 'h3';
import { z, type ZodType } from 'zod';
import { API_ERROR_CODES } from '../../shared/types/api';
import { parseEntityId } from '../../shared/types/id';
import { ApiRequestError } from './api';

export async function readSchemaBody<T>(event: H3Event, schema: ZodType<T>): Promise<T> {
  let body: unknown;
  try {
    body = await readBody(event);
  } catch {
    throw new ApiRequestError(API_ERROR_CODES.BAD_REQUEST, 'Request body must be valid JSON.', 400);
  }
  const parsed = schema.safeParse(body);
  if (!parsed.success) {
    throw new ApiRequestError(API_ERROR_CODES.BAD_REQUEST, 'Request validation failed.', 400, {
      issues: parsed.error.issues.map((issue) => ({ path: issue.path.join('.'), message: issue.message })),
    });
  }
  return parsed.data;
}

export function requiredRouteId(event: H3Event, name = 'id'): string {
  const value = getRouterParam(event, name);
  if (!value) throw new ApiRequestError(API_ERROR_CODES.NOT_FOUND, 'Resource was not found.', 404);
  try {
    return parseEntityId(value);
  } catch {
    throw new ApiRequestError(API_ERROR_CODES.NOT_FOUND, 'Resource was not found.', 404);
  }
}

export const revisionSchema = z.number().int().nonnegative();
