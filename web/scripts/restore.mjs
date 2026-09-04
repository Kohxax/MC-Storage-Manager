import { existsSync, mkdirSync } from 'node:fs';
import { basename, dirname, isAbsolute, resolve } from 'node:path';
import process from 'node:process';
import BetterSqlite3 from 'better-sqlite3';

function resolveDatabasePath(value) {
  const pathValue = value.startsWith('file:') ? decodeURIComponent(value.slice(5)) : value;
  return isAbsolute(pathValue) ? pathValue : resolve(pathValue);
}

const arguments_ = process.argv.slice(2);
const sourceArgument = arguments_.find((argument) => argument !== '--force');
if (!sourceArgument || !arguments_.includes('--force')) {
  throw new Error('Usage: pnpm db:restore <backup-file> --force (stop the web app first)');
}

const databaseUrl = (process.env.DATABASE_URL ?? './data/storage.db').trim();
if (databaseUrl === ':memory:') {
  throw new Error('An in-memory database cannot be restored.');
}

const sourcePath = resolve(sourceArgument);
const targetPath = resolveDatabasePath(databaseUrl);
if (!existsSync(sourcePath)) {
  throw new Error(`Backup file does not exist: ${sourcePath}`);
}
if (sourcePath === targetPath) {
  throw new Error('Backup source must differ from the target database.');
}

mkdirSync(dirname(targetPath), { recursive: true });
const timestamp = new Date().toISOString().replaceAll(':', '-');
if (existsSync(targetPath)) {
  const recoveryPath = resolve('backups', `${basename(targetPath)}.${timestamp}.before-restore`);
  mkdirSync(dirname(recoveryPath), { recursive: true });
  const current = new BetterSqlite3(targetPath, { readonly: true, fileMustExist: true });
  try {
    await current.backup(recoveryPath);
  } finally {
    current.close();
  }
  console.log(`Current database recovery copy: ${recoveryPath}`);
}

const source = new BetterSqlite3(sourcePath, { readonly: true, fileMustExist: true });
try {
  const sourceCheck = source.pragma('quick_check', { simple: true });
  if (sourceCheck !== 'ok') {
    throw new Error(`Backup database quick_check failed: ${String(sourceCheck)}`);
  }
  await source.backup(targetPath);
} finally {
  source.close();
}

const restored = new BetterSqlite3(targetPath, { readonly: true, fileMustExist: true });
try {
  const restoredCheck = restored.pragma('quick_check', { simple: true });
  if (restoredCheck !== 'ok') {
    throw new Error(`Restored database quick_check failed: ${String(restoredCheck)}`);
  }
} finally {
  restored.close();
}

console.log(`SQLite database restored: ${targetPath}`);
