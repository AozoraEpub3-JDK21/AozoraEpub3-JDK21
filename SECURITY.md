# Security Policy

## サポート対象バージョン

| バージョン | サポート状態 |
|-----------|------------|
| 1.3.x-jdk21 (最新) | :white_check_mark: セキュリティ修正対象 |
| 1.2.x-jdk21 | :x: サポート終了 — 最新版へ更新してください |
| オリジナル版 (hmdev/AozoraEpub3) | :x: このフォークではサポート対象外 |

## 脆弱性の報告

セキュリティ上の問題を発見した場合は、**公開 Issue には書かず**、以下の方法で非公開にご連絡ください。

### 報告方法

1. **GitHub Security Advisories（推奨）**: [こちらから非公開レポートを作成](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/security/advisories/new)
2. **GitHub Private Vulnerability Reporting**: リポジトリの Security タブから報告

### 報告に含めてほしい情報

- 問題の概要と影響範囲
- 再現手順（可能であれば）
- 影響を受けるバージョン
- 修正案（あれば）

### 対応方針

- **初動**: 報告受領後、可能な限り速やかに確認します（目安: 1週間以内）
- **修正**: 確認された脆弱性は修正パッチを作成し、新バージョンとしてリリースします
- **公開**: 修正リリース後に、修正内容と影響範囲を公開します
- **クレジット**: 報告者の希望に応じて、リリースノートに謝辞を記載します

## セキュリティ対策の現状

- **CodeQL**: GitHub Actions で自動コードスキャンを実施
- **依存関係監視**: Gradle dependencyUpdates で定期チェック
- **CI 検証**: ビルド・テスト・epubcheck バリデーションを自動実行
- **配布物の検証**: リリースごとに SHA-256 チェックサムと artifact attestation を提供

## 正規配布について

このプロジェクトの正規配布物は **[GitHub Releases](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases) のみ**です。
検証方法は [VERIFY.md](VERIFY.md) を参照してください。
