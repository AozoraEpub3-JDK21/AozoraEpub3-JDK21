# AozoraEpub3 リリースノート

## バージョン: 1.2.1-jdk21

**リリース日**: 2025年12月17日

### 新機能

- **GUI機能の復活**: オリジナルのhmdev版GUIをJDK21対応で復活
  - `AozoraEpub3Applet.java`をメインクラスとして直接起動可能に
  - ドラッグ&ドロップによるファイル指定
  - 各種EPUB設定をGUIから簡単に変更可能

- **Windows向けランチャーバッチファイル**:
  - `AozoraEpub3起動.bat` (日本語版、Shift_JIS)
  - `AozoraEpub3.bat` (英語版、ASCII)
  - Windows 11の.jar ダブルクリック問題を回避
  - javaw.exeを使用してコンソールなしで起動

- **Unix/Linux/macOS向けシェルスクリプト**:
  - `AozoraEpub3.sh` (実行権限付き)
  - クロスプラットフォーム対応

### 改善

- FAT-JAR配布版に起動用バッチ/シェルスクリプトを同梱
- GUI全体のフォントをOS別日本語フォントに統一（Windows: Yu Gothic UI/Meiryo優先）
  - 英語OS環境でも日本語字形の違和感を軽減
  - テキスト領域のフォントサイズを13ptに改善（可読性向上）
- README.mdとDEVELOPMENT.mdにGUI起動方法、開発者向けビルド手順を詳細に記載
- 配布はFAT版のみに統一（シンプルで配布しやすい構成）

### 技術的変更

- `AozoraEpub3Launcher.java`を削除し、アーキテクチャを簡素化
- `application.mainClass`を`AozoraEpub3Applet`に変更
- Gradleビルドにランチャー生成タスク (`createLauncher`) を追加

---

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
