# MC Storage Manager

Paper 26.2サーバー上で登録したストレージ範囲の在庫を集計し、認証済みプレイヤーがWeb UIから確認・管理するためのプロジェクトです。

## 構成

- `plugin/`: Java 25、Paper 26.2、WorldEdit対応プラグイン
- `web/`: Nuxt 4、Nitro、Tailwind CSS、Drizzle ORM、SQLiteアプリ
- `docs/`: API規約と運用手順
- `compose.yaml`: WebアプリとCloudflare Tunnelの本番構成

## クイックスタート

```powershell
Set-Location plugin
.\gradlew.bat build

Set-Location ..\web
corepack enable
pnpm install
Copy-Item .env.example .env.local
pnpm db:migrate
pnpm dev
```
