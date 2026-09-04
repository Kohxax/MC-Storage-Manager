import { spawnSync } from 'node:child_process';
import process from 'node:process';

const migration = spawnSync(process.execPath, ['./scripts/migrate.mjs'], {
  stdio: 'inherit',
  env: process.env,
  windowsHide: true,
});

if (migration.error) {
  throw migration.error;
}
if (migration.status !== 0) {
  process.exit(migration.status ?? 1);
}

await import('./start.mjs');
