/** Optimistic-concurrency revision. Revisions start at zero and only increase. */
export type Revision = number & { readonly __revision: unique symbol };

export const INITIAL_REVISION = 0 as Revision;

export function isRevision(value: unknown): value is Revision {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
}

export function parseRevision(value: number): Revision {
  if (!isRevision(value)) {
    throw new TypeError('Revision must be a non-negative safe integer.');
  }
  return value;
}

export function nextRevision(value: Revision): Revision {
  const next = value + 1;
  if (!isRevision(next)) {
    throw new RangeError('Revision overflow.');
  }
  return next;
}

export class RevisionConflictError extends Error {
  readonly code = 'REVISION_CONFLICT' as const;

  constructor(message = 'The resource was changed by another request.') {
    super(message);
    this.name = 'RevisionConflictError';
  }
}
