# MC Storage Manager Web

Nuxt 4 / Nitro と SQLite を同一プロジェクトにまとめた Web アプリケーションです。
Node.js **24.20.0** と pnpm **10.12.1** を固定しています。

## Windows での開発

```powershell
pnpm install
pnpm db:migrate
pnpm dev
```

開発サーバーは `http://127.0.0.1:3000` で起動します。

```powershell
pnpm build
pnpm start
```

SQLite のパスは `DATABASE_URL` で変更できます（既定値は `./data/storage.db`）。
`.env.example` を `.env.local` にコピーして調整してください。

## 構成

- `app/`: Nuxt 4 の画面と Material 3 風セマンティックトークン
- `server/api/`: Nitro API（`/api/health` を含む）
- `server/db/schema.ts`: Drizzle の初期スキーマ
- `server/db/repositories/`: DB アクセスを閉じ込めるリポジトリ層
- `shared/types/`: API エラー、日時、ID、revision の共有契約
- `server/db/migrations/`: Drizzle Kit 用の初期 migration

## Phase 3–4 API

プラグイン向けエンドポイントは `Authorization: Bearer <API key>`（または
`X-API-Key`）と `X-Server-Id` を必ず送ります。API キーは平文保存せず、
`server/services/security.ts` の scrypt 形式ハッシュと照合します。

- `POST /api/plugin/auth/link`
- `POST /api/plugin/auth/revoke`
- `POST /api/plugin/players/sync`
- `POST /api/plugin/regions`
- `PATCH|DELETE /api/plugin/regions/:id`
- `POST /api/plugin/containers/batch`（`idempotencyKey` 必須）
- `GET /api/plugin/changes` / `POST /api/plugin/changes/:id/result`

Web 向けの保護された API は login token redeem 後に HttpOnly セッション Cookie と
CSRF Cookie を発行します。状態変更リクエストには `X-CSRF-Token` ヘッダーが必要です。
本番では `COOKIE_SECURE=true`（または `NODE_ENV=production`）にして Secure Cookie を有効にしてください。

サーバー登録、APIキーのローテーション、SQLiteバックアップ・復元は `pnpm server:admin`、
`pnpm db:backup`、`pnpm db:restore` を使います。詳細は `../docs/OPERATIONS.md` を参照してください。
