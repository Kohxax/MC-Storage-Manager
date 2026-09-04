/** All API and persisted timestamps are canonical ISO-8601 UTC strings. */
export type IsoDateTime = string & { readonly __isoDateTime: unique symbol };

const ISO_UTC_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/;

export function isIsoDateTime(value: unknown): value is IsoDateTime {
  if (typeof value !== 'string' || !ISO_UTC_PATTERN.test(value)) {
    return false;
  }
  const date = new Date(value);
  return !Number.isNaN(date.getTime()) && date.toISOString() === value;
}

export function parseIsoDateTime(value: string): IsoDateTime {
  if (!isIsoDateTime(value)) {
    throw new TypeError('Expected a canonical ISO-8601 UTC timestamp.');
  }
  return value;
}

export function nowIsoDateTime(): IsoDateTime {
  return new Date().toISOString() as IsoDateTime;
}
