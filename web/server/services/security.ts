import { createHash, randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';

const API_KEY_SCRYPT_N = 16_384;
const API_KEY_SCRYPT_R = 8;
const API_KEY_SCRYPT_P = 1;
const API_KEY_SALT_BYTES = 16;

export function createOpaqueToken(byteLength = 32): string {
  return randomBytes(byteLength).toString('base64url');
}

export function hashOpaqueToken(token: string): string {
  return createHash('sha256').update(token, 'utf8').digest('hex');
}

export function hashApiKey(apiKey: string): string {
  if (!apiKey || apiKey.length < 16) {
    throw new TypeError('API key must contain at least 16 characters.');
  }
  const salt = randomBytes(API_KEY_SALT_BYTES);
  const derived = scryptSync(apiKey, salt, 32, {
    N: API_KEY_SCRYPT_N,
    r: API_KEY_SCRYPT_R,
    p: API_KEY_SCRYPT_P,
  });
  return [
    'scrypt',
    `N=${API_KEY_SCRYPT_N}`,
    `r=${API_KEY_SCRYPT_R}`,
    `p=${API_KEY_SCRYPT_P}`,
    salt.toString('base64url'),
    derived.toString('base64url'),
  ].join('$');
}

/** Supports the current scrypt format and legacy sha256:<hex> values during migration. */
export function verifyApiKey(apiKey: string, encodedHash: string): boolean {
  if (!apiKey || !encodedHash) return false;
  try {
    if (encodedHash.startsWith('sha256:')) {
      return constantTimeStringEqual(hashOpaqueToken(apiKey), encodedHash.slice('sha256:'.length));
    }
    if (/^[0-9a-f]{64}$/i.test(encodedHash)) {
      return constantTimeStringEqual(hashOpaqueToken(apiKey), encodedHash);
    }
    const [algorithm, nPart, rPart, pPart, saltEncoded, derivedEncoded] = encodedHash.split('$');
    if (algorithm !== 'scrypt' || !nPart || !rPart || !pPart || !saltEncoded || !derivedEncoded) return false;
    const N = Number(nPart.replace(/^N=/, ''));
    const r = Number(rPart.replace(/^r=/, ''));
    const p = Number(pPart.replace(/^p=/, ''));
    if (!Number.isSafeInteger(N) || !Number.isSafeInteger(r) || !Number.isSafeInteger(p)) return false;
    const salt = Buffer.from(saltEncoded, 'base64url');
    const expected = Buffer.from(derivedEncoded, 'base64url');
    const actual = scryptSync(apiKey, salt, expected.length, { N, r, p });
    return actual.length === expected.length && timingSafeEqual(actual, expected);
  } catch {
    return false;
  }
}

export function constantTimeStringEqual(left: string, right: string): boolean {
  const leftBuffer = Buffer.from(left, 'utf8');
  const rightBuffer = Buffer.from(right, 'utf8');
  if (leftBuffer.length !== rightBuffer.length) return false;
  return timingSafeEqual(leftBuffer, rightBuffer);
}
