# AozoraEpub3 リリースノート

## バージョン: 1.2.6-jdk21（安定リリース）

**リリース日**: 2026年1月24日

### 変更概要

- 依存ライブラリを最新安定版へ更新
  - commons-cli: 1.11.0
  - commons-collections4: 4.5.0
  - commons-compress: 1.28.0
  - commons-lang3: 3.20.0
  - jsoup: 1.22.1
  - junrar: 7.5.7
  - batik-transcoder: 1.19
- CLIヘルプAPIの非推奨警告を解消
  - `org.apache.commons.cli.HelpFormatter` → `org.apache.commons.cli.help.HelpFormatter`
  - `printHelp(syntax, header, options, footer, autoUsage)` へ移行
- 依存更新レポート用ワークフローを追加（`dependencyUpdates` 実行結果をArtifacts保存）

### 検証結果

```
Build: ✓ BUILD SUCCESSFUL
Tests: ✓ 全テスト成功
Distributions: ✓ ZIP/TAR 生成済み
```

## バージョン: 1.2.5-jdk21

**リリース日**: 2025年12月20日

### パフォーマンス最適化 🚀

- **アーカイブスキャンの高速化**: 大容量zip/rarファイルの変換速度を大幅に改善
  - アーカイブスキャン回数を **4回→1回に削減**（75%削減）
  - `ArchiveCache`: アーカイブ内容をメモリにキャッシュして再利用
  - `ArchiveScanner`: zip/rarを1回のパスで効率的にスキャン
  - 2GBアーカイブでもキャッシュは10-20MB程度の省メモリ設計
  - 変換完了後に自動的にキャッシュを解放

**効果**: 大容量アーカイブ（100MB以上）や多数の画像を含むファイルの変換が大幅に高速化されます。

### コード品質向上

- **リファクタリング**: 大規模な `AozoraEpub3.java` を機能別に分割
  - `OutputNamer`: ファイル名生成ロジックを抽出（50行）
  - `WriterConfigurator`: Writer設定を集約（110行）
  - `ArchiveTextExtractor`: アーカイブ処理を統一（90行）
  - メインクラス: 645行 → 450行（**200行削減**）
  - 保守性・テスタビリティが向上

- **テスト追加**: 単体テストを追加
  - `OutputNamerTest`: ファイル名生成ロジックのテスト（4テスト）
  - 全93テスト: ✓ 全て成功

- **コードクリーンアップ**: 未使用のインポートとメソッドを削除
  - コンパイルワーニング: 0

### ドキュメント改善

- **開発ドキュメント拡充**:
  - `notes/refactor-plan.md`: リファクタリング計画の詳細
  - `notes/archive-cache-optimization.md`: パフォーマンス最適化の技術詳細
  - `DEVELOPMENT.md`: コード構造とリファクタリングセクションを追加
  - `README.md`: 高速変換機能を特徴に追加

### 検証結果

```
GitHub Actions: ✓ All checks passing
Build: ✓ BUILD SUCCESSFUL
Tests: ✓ 93 tests passed (0 failures)
Code Quality: ✓ 0 warnings, 0 errors
```

---

## バージョン: 1.2.3-jdk21

**リリース日**: 2025年12月18日

### セキュリティ修正

- **ZipSlip脆弱性対策**: ZIP抽出時のパストラバーサル攻撃を防止
  - `ImageInfoReader.java`: `sanitizeArchiveEntryName()`メソッドを追加
  - アーカイブエントリの絶対パス・相対パストラバーサル（`..`）・不正シンボルを検出・排除
  - GitHub Code Scanning Alert #2を解決

- **GitHub Actions権限設定**: GITHUB_TOKENの最小権限化
  - `.github/workflows/ci.yml`: `permissions: {contents: read}`を明示的に設定
  - セキュリティベストプラクティスに準拠
  - GitHub Code Scanning Alert #1を解決

### 改善

- **ドキュメント更新**: narou.rb のGitHubリンク修正
  - README.md: `https://github.com/whiteleaf7/narou` に更新
  
- **Copilot指示ドキュメント更新**: コミットメッセージの言語規約を明記
  - `.github/copilot-instructions.md`: 「全てのコミットメッセージは日本語」を追記

### 検証結果

```
GitHub Code Scanning: ✓ 0 alerts (Alert #1, #2を完全解決)
Build: ✓ BUILD SUCCESSFUL
Tests: ✓ All tests passed
```

---

## バージョン: 1.2.2-jdk21

**リリース日**: 2025年12月17日

### 新機能

- **EPUB 3.3 対応**: EPUBCheck 5.3.0にアップグレード
  - EPUB 3.3仕様準拠（W3C Recommendation 2025年3月27日）
  - 後方互換性維持（EPUB 3.2と同じpackage version="3.0"属性を使用）

### バグ修正

- **CLIモード引数バグ修正**: コマンドライン引数を指定した際にGUIが起動する問題を修正
  - `MANIFEST.MF`のMain-Classを`AozoraEpub3`に変更
  - `AozoraEpub3Applet.main()`に引数チェックを追加し、引数がある場合は`AozoraEpub3.main()`に委譲

### 改善

- **ビルドプロセス改善**: 配布タスクの信頼性向上
  - `createLauncher`タスクでdistributionsディレクトリを自動作成
  - copilot-instructions.mdにビルドタスク詳細を文書化（jar/dist/distZip の違いを明記）

### 技術的変更

- EPUBCheck: 5.2.0 → 5.3.0
- バージョン表記を1.2.2に統一（`build.gradle`, `AozoraEpub3.java`, `AozoraEpub3Applet.java`）

### 検証結果

```
EPUBCheck 5.3.0
Validating using EPUB 3.3 rules.
No errors or warnings detected.
Messages: 0 fatals / 0 errors / 0 warnings / 0 infos
```

---

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
