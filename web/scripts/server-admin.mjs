import { randomBytes, randomUUID, scryptSync } from 'node:crypto';
import { isAbsolute, resolve } from 'node:path';
import process from 'node:process';
import BetterSqlite3 from 'better-sqlite3';

function usage() {
  console.error('Usage:\n  pnpm server:admin create <name> <public-url>\n  pnpm server:admin rotate <server-id>\n  pnpm server:admin list');
  process.exitCode = 2;
}
function databasePath(value) {
  const path = value.startsWith('file:') ? decodeURIComponent(value.slice(5)) : value;
  return isAbsolute(path) ? path : resolve(path);
}
function apiKeyHash(apiKey) {
  const salt = randomBytes(16);
  const derived = scryptSync(apiKey, salt, 32, { N: 16_384, r: 8, p: 1 });
  return ['scrypt', 'N=16384', 'r=8', 'p=1', salt.toString('base64url'), derived.toString('base64url')].join('$');
}
function createKey() { return `mcsm_${randomBytes(32).toString('base64url')}`; }

const [command, first, second] = process.argv.slice(2);
if (!['create', 'rotate', 'list'].includes(command) || (command === 'create' && (!first || !second)) || (command === 'rotate' && !first)) {
  usage();
} else {
  const database = new BetterSqlite3(databasePath((process.env.DATABASE_URL ?? './data/storage.db').trim()), { fileMustExist: true });
  try {
    if (command === 'list') {
      const rows = database.prepare('SELECT id, name, public_url AS publicUrl, updated_at AS updatedAt, revision FROM servers ORDER BY name').all();
      console.table(rows);
    } else if (command === 'create') {
      const publicUrl = new URL(second).toString().replace(/\/$/, '');
      const id = randomUUID();
      const apiKey = createKey();
      database.prepare(`INSERT INTO servers (id, name, api_key_hash, public_url, revision) VALUES (?, ?, ?, ?, 0)`).run(id, first, apiKeyHash(apiKey), publicUrl);
      console.log(`Server ID: ${id}`);
      console.log(`API key (shown once): ${apiKey}`);
    } else {
      const apiKey = createKey();
      const result = database.prepare(`UPDATE servers SET api_key_hash = ?, updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), revision = revision + 1 WHERE id = ?`).run(apiKeyHash(apiKey), first);
      if (result.changes !== 1) throw new Error(`Server not found: ${first}`);
      console.log(`Server ID: ${first}`);
      console.log(`New API key (shown once): ${apiKey}`);
    }
  } finally { database.close(); }
}
