# AozoraEpub3 JDK21対応 初期リリース

## バージョン: 1.2.0-jdk21

**リリース日**: 2025年12月16日

## 概要

AozoraEpub3 を Java 21 と最新ツールチェーンに対応させました。

## 主な更新

- **Gradle 9.2.1 / Java 21** でビルド・テスト
- **依存ライブラリ**: Velocity 2.4.1、JSoup 1.18.1、Apache Commons 各種を最新化
- **EPUB テンプレート**: 外字フォント対応、Kindle・iOS レイアウト改善
- **Web 機能**: レート制限 1500ms（最小 1000ms）、narou.rb 連携対応
- **セキュリティ**: Git 匿名著者設定、.gitignore 強化
- **ドキュメント**: README・DEVELOPMENT.md・ライセンス更新

## テスト・互換性

- JUnit 4.13.2 全 5 テスト合格 ✓
- 既存の入力・プリセットと完全互換 ✓
- EPUB 3.2・電書協ガイド対応 ✓

## 既知の問題

- **iPhone Kindle**: 縦書きタイトルページのレイアウトが画面比率で変動する場合あり
- **ncode.syosetu.com**: HTML 構造が変わった場合、セレクタ更新が必要な可能性

## インストール

[GitHub Releases](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases) から以下をダウンロード：
- **Windows**: `AozoraEpub3-1.2.0-jdk21.zip`
- **Linux/macOS**: `AozoraEpub3-1.2.0-jdk21.tar`

## 謝辞

- **オリジナル作成者**: hmdev
- **本プロジェクト**: AozoraEpub3-JDK21 チーム
- **連携**: narou.rb プロジェクト

---

詳細は [README.md](README.md) / [DEVELOPMENT.md](DEVELOPMENT.md) を参照してください。
