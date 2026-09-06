import { mkdirSync } from 'node:fs';
import { dirname, isAbsolute, resolve } from 'node:path';
import BetterSqlite3 from 'better-sqlite3';
import { drizzle } from 'drizzle-orm/better-sqlite3';
import type { BetterSQLite3Database } from 'drizzle-orm/better-sqlite3';
import { schema } from './schema';

export type AppDatabase = BetterSQLite3Database<typeof schema>;
/** Query methods shared by the root database and a synchronous transaction. */
export type DatabaseExecutor = Pick<AppDatabase, 'select' | 'insert' | 'update' | 'delete'>;

export interface DatabaseHandle {
  db: AppDatabase;
  sqlite: BetterSqlite3.Database;
  path: string;
  close: () => void;
}

/** Resolve a SQLite URL without baking a machine-specific path into the app. */
export function resolveDatabasePath(databaseUrl: string): string {
  const value = databaseUrl.trim();
  if (value === ':memory:') {
    return value;
  }

  const pathValue = value.startsWith('file:') ? decodeURIComponent(value.slice(5)) : value;
  return isAbsolute(pathValue) ? pathValue : resolve(process.cwd(), pathValue);
}

export function createDatabase(databaseUrl: string): DatabaseHandle {
  const path = resolveDatabasePath(databaseUrl);
  if (path !== ':memory:') {
    mkdirSync(dirname(path), { recursive: true });
  }

  const sqlite = new BetterSqlite3(path);
  sqlite.pragma('foreign_keys = ON');
  sqlite.pragma('busy_timeout = 5000');
  if (path !== ':memory:') {
    sqlite.pragma('journal_mode = WAL');
  }

  const db = drizzle(sqlite, { schema });
  let closed = false;
  return {
    db,
    sqlite,
    path,
    close: () => {
      if (!closed) {
        sqlite.close();
        closed = true;
      }
    },
  };
}

let runtimeHandle: DatabaseHandle | undefined;

/** Nitro-side singleton. Tests and scripts should use createDatabase directly. */
export function getDatabaseHandle(): DatabaseHandle {
  const config = useRuntimeConfig();
  const databaseUrl = String(config.databaseUrl ?? process.env.DATABASE_URL ?? './data/storage.db');
  const path = resolveDatabasePath(databaseUrl);

  if (!runtimeHandle || runtimeHandle.path !== path) {
    runtimeHandle?.close();
    runtimeHandle = createDatabase(databaseUrl);
  }
  return runtimeHandle;
}

export function closeRuntimeDatabase(): void {
  runtimeHandle?.close();
  runtimeHandle = undefined;
}
