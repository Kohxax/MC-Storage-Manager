import { describe, expect, it } from 'vitest';
import { isIsoDateTime, nowIsoDateTime, parseIsoDateTime } from '../shared/types/datetime';
import { createEntityId, isEntityId, parseEntityId } from '../shared/types/id';
import { INITIAL_REVISION, isRevision, nextRevision, parseRevision } from '../shared/types/revision';

describe('shared API contract types', () => {
  it('creates and validates canonical UUID v4 IDs', () => {
    const id = createEntityId();
    expect(isEntityId(id)).toBe(true);
    expect(parseEntityId(id.toUpperCase())).toBe(id.toLowerCase());
    expect(isEntityId('not-an-id')).toBe(false);
  });

  it('normalizes timestamps to ISO-8601 UTC', () => {
    const timestamp = nowIsoDateTime();
    expect(isIsoDateTime(timestamp)).toBe(true);
    expect(parseIsoDateTime(timestamp)).toBe(timestamp);
    expect(isIsoDateTime('2025-01-01T00:00:00Z')).toBe(false);
  });

  it('keeps revisions non-negative and monotonic', () => {
    expect(isRevision(INITIAL_REVISION)).toBe(true);
    expect(nextRevision(INITIAL_REVISION)).toBe(1);
    expect(parseRevision(0)).toBe(INITIAL_REVISION);
    expect(() => parseRevision(-1)).toThrow(TypeError);
    expect(() => parseRevision(1.5)).toThrow(TypeError);
  });
});
