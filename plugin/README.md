# MC Storage Manager Paper Plugin

Paper 26.2用のストレージ範囲・在庫同期プラグインです。WorldEditを必須依存として宣言しています。

## 必要環境

- JDK 25（Paper 26.2 のJava要件）
- 同梱のGradle Wrapper 9.7.1

## ビルドとテスト

```powershell
Set-Location plugin
.\gradlew.bat test build
```

ビルド成果物は `build/libs/mc-storage-manager-0.1.0-SNAPSHOT.jar` に作成されます。生成したJARと、対応するWorldEditをPaper 26.2サーバーの`plugins`ディレクトリに配置してください。

## 構成

- `dev.bokukoha.mcstoragemanager.core`: Paper APIに依存しないアプリケーションコード
- `dev.bokukoha.mcstoragemanager.platform`: Paper/Bukkit APIを使うアダプター
- `MCStorageManagerPlugin`: Paperプラグインのエントリーポイント

## 権限

- `storage.region.create` — 範囲登録（既定: OP）
- `storage.region.manage.own` — 自分の範囲管理（既定: OP）
- `storage.region.manage.any` — 全範囲の管理（既定: OP）
- `storage.web.login` — WebログインURL発行（既定: 全員）
- `storage.admin` — 上記すべて（既定: OP）

## コマンド

- `/storage create <name>` — WorldEditで選択した範囲を登録
- `/storage web` — 5分間有効な本人専用ログインURLを発行
- `/storage web revoke` — 本人のWebセッションを失効

同期を有効にする場合は `config.yml` の `sync.endpoint`、`sync.server-id`、`sync.api-key` と
`web.public-api-url` を設定してください。HTTP処理は非同期で、未確認のコンテナ差分は
`pending-sync.yml` へ保存されます。
