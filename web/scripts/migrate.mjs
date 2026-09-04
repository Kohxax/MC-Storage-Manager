import { mkdirSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, isAbsolute, resolve } from 'node:path';
import process from 'node:process';

const databaseUrl = (process.env.DATABASE_URL ?? './data/storage.db').trim();
if (databaseUrl !== ':memory:') {
  const pathValue = databaseUrl.startsWith('file:')
    ? decodeURIComponent(databaseUrl.slice(5))
    : databaseUrl;
  const databasePath = isAbsolute(pathValue) ? pathValue : resolve(pathValue);
  mkdirSync(dirname(databasePath), { recursive: true });
}

const drizzleKit = resolve('node_modules', 'drizzle-kit', 'bin.cjs');
const result = spawnSync(
  process.execPath,
  [drizzleKit, 'migrate', '--config', './drizzle.config.ts'],
  { stdio: 'inherit', env: process.env, windowsHide: true },
);

if (result.error) {
  throw result.error;
}
process.exitCode = result.status ?? 1;
