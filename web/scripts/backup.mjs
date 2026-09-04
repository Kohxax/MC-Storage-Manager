import { mkdirSync } from 'node:fs';
import { basename, dirname, isAbsolute, resolve } from 'node:path';
import process from 'node:process';
import BetterSqlite3 from 'better-sqlite3';

function resolveDatabasePath(value) {
  const pathValue = value.startsWith('file:') ? decodeURIComponent(value.slice(5)) : value;
  return isAbsolute(pathValue) ? pathValue : resolve(pathValue);
}

const databaseUrl = (process.env.DATABASE_URL ?? './data/storage.db').trim();
if (databaseUrl === ':memory:') {
  throw new Error('An in-memory database cannot be backed up.');
}

const sourcePath = resolveDatabasePath(databaseUrl);
const timestamp = new Date().toISOString().replaceAll(':', '-');
const defaultDestination = resolve('backups', `${basename(sourcePath)}.${timestamp}.backup`);
const destinationPath = resolve(process.argv[2] ?? defaultDestination);

if (sourcePath === destinationPath) {
  throw new Error('Backup destination must differ from the source database.');
}

mkdirSync(dirname(destinationPath), { recursive: true });
const database = new BetterSqlite3(sourcePath, { readonly: true, fileMustExist: true });
try {
  await database.backup(destinationPath);
  const backup = new BetterSqlite3(destinationPath, { readonly: true, fileMustExist: true });
  let result;
  try {
    result = backup.pragma('quick_check', { simple: true });
  } finally {
    backup.close();
  }
  if (result !== 'ok') {
    throw new Error(`Backup database quick_check failed: ${String(result)}`);
  }
  console.log(`SQLite backup created: ${destinationPath}`);
} finally {
  database.close();
}
