import type { H3Event } from 'h3';
import { getHeader, setResponseHeader } from 'h3';
import { API_ERROR_CODES } from '../../shared/types/api';
import { ApiRequestError } from '../utils/api';

interface Bucket {
  count: number;
  resetAt: number;
}

class SlidingWindowRateLimiter {
  private readonly buckets = new Map<string, Bucket>();

  consume(key: string, limit: number, windowMs: number, now = Date.now()): { allowed: boolean; retryAfterSeconds: number } {
    const current = this.buckets.get(key);
    if (!current || current.resetAt <= now) {
      this.buckets.set(key, { count: 1, resetAt: now + windowMs });
      return { allowed: true, retryAfterSeconds: 0 };
    }
    if (current.count >= limit) {
      return { allowed: false, retryAfterSeconds: Math.max(1, Math.ceil((current.resetAt - now) / 1000)) };
    }
    current.count += 1;
    return { allowed: true, retryAfterSeconds: 0 };
  }

  clear(): void {
    this.buckets.clear();
  }
}

export const rateLimiter = new SlidingWindowRateLimiter();

export function clientKey(event: H3Event): string {
  // Forwarded headers are client-controlled unless the deployment explicitly
  // declares a trusted reverse proxy. Without this opt-in, rate limiting must
  // use the socket peer so an attacker cannot rotate X-Forwarded-For values.
  if (process.env.TRUST_PROXY_HEADERS === 'true') {
    const cloudflareIp = getHeader(event, 'cf-connecting-ip');
    if (cloudflareIp) return cloudflareIp.trim().slice(0, 128);
    const forwarded = getHeader(event, 'x-forwarded-for');
    if (forwarded) return forwarded.split(',')[0]!.trim().slice(0, 128);
    const realIp = getHeader(event, 'x-real-ip');
    if (realIp) return realIp.trim().slice(0, 128);
  }
  return event.node?.req?.socket?.remoteAddress?.slice(0, 128) ?? 'local';
}

export function enforceRateLimit(event: H3Event, bucket: string, limit: number, windowMs = 60_000): void {
  const result = rateLimiter.consume(`${bucket}:${clientKey(event)}`, limit, windowMs);
  if (!result.allowed) {
    setResponseHeader(event, 'retry-after', result.retryAfterSeconds);
    throw new ApiRequestError(API_ERROR_CODES.RATE_LIMITED, 'Too many requests.', 429, {
      retryAfterSeconds: result.retryAfterSeconds,
    });
  }
}
