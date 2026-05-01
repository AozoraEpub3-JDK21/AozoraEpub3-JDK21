# AozoraEpub3 リリースノート

## バージョン: 1.3.6-jdk21

**リリース日**: 2026年5月1日

### ハイライト

- **JDK 26 完全対応**: ビルド・全テスト実行・GUI 起動を JDK 26 (2026-03-17 GA) で CI 検証済
- **内部コード現代化**: SLF4J ロガー、`java.nio.file.Path`、`java.time`、空 catch 監査
- 配布物は **Java 21 ターゲットビルド** (class file version 65) のまま — JDK 21 LTS から JDK 26 まで全環境で動作します

### JDK 26 対応の内訳

| 項目 | PR | 対応内容 |
|------|----|----------|
| JEP 504 (`JApplet` 削除) | v1.3.5 で完了 | `extends JApplet` → `extends JPanel` に変更 |
| JDK 26 toolchain での `compileJava` 検証 | #29 | `build.gradle` に `-PjavaToolchainVersion=<NN>` を導入。CI matrix に JDK 26 を追加 |
| JDK 26 での全テスト実行 | #36 | JUnit 4 の test detection が JDK 26 のリフレクション挙動変化で `InvalidTestClassError` を発生させていた問題を `build.gradle` の test exclude (`AozoraFullFlowTest` / `AozoraRealTest` / `VelocityTestUtils` / `**/*$*.class`) で解消 |
| JDK 26 での GUI smoke test | #36 | CI で fat-jar を JDK 26 ランタイムで起動し、Xvfb 経由で Swing GUI が立ち上がることを確認 |

### 内部コード現代化

| 領域 | PR | 変更点 |
|------|----|--------|
| `Vector` → `ArrayList` | #17 | 24 ファイルでレガシーコレクションを撤去 |
| `java.io.File` → `java.nio.file.Path` | #19〜#27 | 14 ファイルを内部 Path 化（公開 API は File 維持で後方互換） |
| `java.time` 移行 | #18 | `Date` / `SimpleDateFormat` を撤去（後述のロケールバグ修正を含む） |
| SLF4J 実利用化 | #12 / #14 / #15 / #16 | 11 ファイルで `e.printStackTrace()` をロガー呼び出しに置換 |
| 空 catch 監査 | #30〜#35 | 11 ファイル / 133 occ の `catch (...) {}` に意図コメントを付与（grep 「意図的:」で追跡可能） |

### バグ修正

- **`dcterms:modified` の EPUB 3.3 仕様違反 (#18)**: タイ仏暦・日本和暦などのロケールで `Date` + `SimpleDateFormat` の組み合わせが非グレゴリオ歴の年号を出力し、epubcheck が `OPF-054` で reject していた問題を修正。`Instant.now()` + `DateTimeFormatter.ISO_INSTANT` で常に UTC グレゴリオ歴の `YYYY-MM-DDThh:mm:ssZ` 形式を出力するようにした
- **青空文庫 URL から CLI -url で本文取得できなかった問題を修正**: extract.txt のディレクトリ名が `web/aozora.gr.jp/` で、実際の URL ホスト名 `www.aozora.gr.jp` と不一致 → サイト定義が読み込まれていなかった。`web/www.aozora.gr.jp/` に rename して解決。さらに同 extract.txt の `HREF`/`SUBTITLE_LIST`/`SUB_UPDATE` が「単話 HTML 完結」形式の青空文庫作品では誤動作していた (他作品リンクを各話と誤認識して本文 placeholder のみ出力) ためコメントアウトし、`docToAozoraText` 経路で `.main_text` から本文を直接抽出するようにした

### Breaking changes

- `BookInfo` 等の公開フィールドで `Vector` → `ArrayList` に変更 (#17)。**バイナリ互換性が崩れる**ため、AozoraEpub3 を外部ライブラリとして利用しているコードは再コンパイルが必要

### `InterruptedException` の扱い変更

- `WebAozoraConverter` の sleep ループで `InterruptedException` を silent swallow していた箇所を、`Thread.currentThread().interrupt()` + `return null` に変更 (#35)。`ExecutorService.shutdownNow()` 等の外部 interrupt が来た場合に正しくダウンロードを終了します

### 動作環境

- **最低要件**: Java 21 以降
- **推奨**: Java 25 LTS（Temurin による JDK 26 のバイナリ配布が出揃い次第、推奨を更新予定）
- **互換性**: Java 21 LTS / Java 25 LTS で動作確認済（JDK 26 でもビルド・全テスト・GUI smoke test PASS）

### 検証結果

```
Build (JDK 21 toolchain): ✓ BUILD SUCCESSFUL
Tests:
  - JDK 21: ✓ 182 tests, 0 failures, 0 errors, 9 skipped
  - JDK 26: ✓ 全テスト PASS (workflow_dispatch CI)
GUI smoke test:
  - JDK 21: ✓ Xvfb 経由で起動確認
  - JDK 26: ✓ Xvfb 経由で起動確認
.NET ポート JavaComparisonTests: ✓ 5/5 PASS (byte-identical 出力維持)
```

---

## バージョン: 1.3.1-jdk21

**リリース日**: 2026年3月7日

### バグ修正

- **サブタイトル行の漢数字変換（完全修正）**: WebAozoraConverter の printText() 内でサブタイトルの数字が漢数字に変換される問題を修正。AozoraTextFinalizer 側の判定順序も修正し、見出し行の数字を確実に全角数字のまま保持するようにした

### ドキュメント

- macOS/Linux でビルド後に JAR をルートディレクトリにコピーする手順を DEVELOPMENT.md に追加

---

## バージョン: 1.3.0-jdk21

**リリース日**: 2026年3月7日

### 新機能

- **narou.rb 互換テキスト前処理**
  - 空行圧縮 (`packBlankLine`): 連続空行を圧縮
  - 前書き・後書き検出 (`detectAndMarkAuthorComments`): 注記タグで囲む
  - 漢数字変換 (`convertNumToKanji`): 注記内・URL行・見出し行を保護しつつ変換
  - 英字全角化 (`alphabetToZenkaku`): 注記内・URL行を保護しつつ変換
  - 二分アキ + 自動字下げ改善 (`halfIndentBracketAndAutoIndent`): narou.rb 互換の50%閾値判定
  - 読了表示 (`appendEndOfBook`): 重複出力を修正
  - `NarouFormatSettings` のデフォルト値を narou.rb `ORIGINAL_SETTINGS` に統一

- **CSS・注記定義更新**
  - `vertical_font.css` を narou.rb 互換の完全版に更新（line-height, .introduction, .postscript, .half_em_space 等）
  - `chuki_tag.txt` に二分アキ・前書き・後書きの注記定義を追加

- **CLI URLオプション**
  - `-url` オプションでWeb小説URLからの直接変換をコマンドラインから実行可能
  - `-narou` オプションで narou.rb 互換フォーマット設定を適用
  - `-interval` / `-cache` オプションで取得間隔・キャッシュ制御

### バグ修正

- **サブタイトル行の漢数字変換**: 見出し行の数字が漢数字に変換される問題を修正（narou.rb 互換: 見出し行は全角数字変換のみ）
- **JSON `\/` エスケープ**: あらすじに `\/` が残る問題を修正（NarouApiClient.unescapeJson）
- **読了表示2重出力**: WebAozoraConverter と AozoraTextFinalizer の両方で出力されていた問題を修正
- **注記内の数字・英字変換**: `［＃米印、1-2-8］` 等の注記内数字が変換される問題を修正
- **URL行の変換**: URL含有行の数字・英字が変換されてリンクが壊れる問題を修正
- **GUI テキストボックス**: バージョン/Java/OS 表示を削除

### 検証結果

```
Build: ✓ BUILD SUCCESSFUL
Tests: ✓ 全テスト成功 (165テスト)
```

---

## バージョン: 1.2.14-jdk21

**リリース日**: 2026年2月28日

### バグ修正

- **初回起動時のデフォルト言語が英語になる問題を修正**
  - **根本原因**: 配布用 `AozoraEpub3.ini` に開発中の設定 `UILang=en` が残っていた。
  - 初回起動（`AozoraEpub3.ini` が存在しない状態）でも英語UIになっていた。
  - `UILang=en` を削除し、コード側のデフォルト（日本語）が使われるよう修正。

### 検証結果

```
Build: ✓ BUILD SUCCESSFUL
Tests: ✓ 全テスト成功
起動確認: ✓ 初回起動で日本語UIが表示される
```

---

## バージョン: 1.2.13-jdk21

**リリース日**: 2026年2月28日

### バグ修正

- **exe ダブルクリック起動できない問題を修正**
  - **根本原因**: v1.2.12 の i18n 対応リファクタリング中に、画面サイズ設定UI の `jTextDispH = new JTextField("800")` および `panel.add(label)` が誤って削除されていた。
  - GUI 起動時に `NullPointerException` が発生し、exe をダブルクリックしても起動しない状態だった。
  - 対象ファイル: `AozoraEpub3Applet.java`（画面サイズ入力フィールド初期化コードを復元）

### 検証結果

```
Build: ✓ BUILD SUCCESSFUL
Tests: ✓ 全テスト成功
GUI起動: ✓ exe ダブルクリックで正常起動確認
```

---

## バージョン: 1.2.12-jdk21

**リリース日**: 2026年2月28日

### 新機能

- **多言語対応 UI（日本語 / 英語）**
  - GUIの全タブ（詳細設定・目次・スタイル・Web・ログ）のUI文字列を `I18n.t()` に置換
  - `narou.rb互換 フォーマット設定` ダイアログも完全対応
  - デフォルト言語: 日本語（`Locale.forLanguageTag("ja")`）
  - 英語UIへの切り替えは `AozoraEpub3.ini` の `ui_lang=en` で変更可能
  - 対象ファイル:
    - `AozoraEpub3Applet.java`: ~200箇所を `I18n.t()` に置換
    - `NarouFormatSettingsDialog.java`: 全文字列を `I18n.t()` に置換
    - `messages_ja.properties` / `messages_en.properties`: 約340キーを追加

### 検証結果

```
Build: ✓ BUILD SUCCESSFUL
Tests: ✓ 全テスト成功 (154テスト)
```

---

## バージョン: 1.2.11-jdk21

**リリース日**: 2026年2月28日

### バグ修正

- **Web小説 各話欠落の修正**（小説家になろう）
  - **根本原因**: 前書きdivが `class="js-novel-text p-novel__text p-novel__text--preface"` を持つため、`CONTENT_ARTICLE` セレクターが前書きを本文として誤マッチし、本文がスキップされていた。
  - **修正1**: `ncode.syosetu.com/extract.txt` の `CONTENT_ARTICLE` セレクターに `:not(.p-novel__text--preface)` を追加して前書きを除外
  - **修正2**: `ExtractInfo.java` のセレクターパース処理を改修 — `:not()` 等のCSS疑似クラスを含むセレクター文字列でも末尾の数値インデックス（`:0` 等）を正しく分離して解析できるよう対応
  - **修正3**: キャッシュファイルに本文が存在しない話を検出した場合、自動でキャッシュ削除・再ダウンロードするフォールバック処理を追加
  - 実機確認: `n7673ff` ep29/ep31 が正常取得できることを確認（15KB超の本文）

### 検証結果

```
Build: ✓ BUILD SUCCESSFUL
Tests: ✓ 全テスト成功 (154テスト)
EPUBCheck 5.1.0: ✓ 0 fatals / 0 errors / 0 warnings / 0 infos
実機テスト: ✓ n7673ff 332話 正常変換確認
```

---

## バージョン: 1.2.10-jdk21

**リリース日**: 2026年2月28日

### 新機能

- **Web小説 各話の更新日時・初回公開日 GUI ON/OFF 機能**
  - 「narou.rb互換 フォーマット設定」ダイアログに2つのチェックボックスを追加
    - **更新日時を各話に表示** (`show_post_date`): 各話の最終更新日時を本文末に表示（初期値: OFF）
    - **初回公開日を各話に表示** (`show_publish_date`): 改稿済の話の初回公開日を更新日時と別行で表示（初期値: OFF）
  - 表示形式: `2024/01/10 公開` / `2024/01/20 更新` の形式で別行出力
  - `setting_narourb.ini` に保存、narou.rb `setting.ini` とキー互換
  - 小説家になろう (`ncode.syosetu.com` / `novel18.syosetu.com`) の `extract.txt` を更新:
    - `CONTENT_UPDATE_LIST`: 更新日時のみ抽出するよう正規表現を修正
    - `CONTENT_PUBLISH_LIST` を新規追加（`span[title]` 属性から初回公開日を取得）

---

## バージョン: 1.2.7-jdk21

**リリース日**: 2026年2月26日

### 新機能

- **カクヨム (kakuyomu.jp) 対応**
  - `web/kakuyomu.jp/extract.txt` を新規追加
  - Next.js SPA の `__NEXT_DATA__` JSON からエピソード全件取得（HTML の `<a>` タグが数件しかない問題を解決）
  - 章構造（`CONTENT_CHAPTER`）: `TableOfContentsChapter` エントリから章タイトルを抽出して `［＃大見出し］` を出力
  - 更新日時差分（`SUB_UPDATE`）: `publishedAt` を使って変更なしエピソードをスキップ
  - 傍点（`<em class="emphasisDots">`）: `［＃傍点］…［＃傍点終わり］` に変換
  - あらすじ: `__NEXT_DATA__` JSON の `introduction` フィールドから取得、`\n` エスケープを改行に復元
  - 実機確認: 154話作品でタイトル・著者・全話・本文・話タイトル取得確認済み

- **ハーメルン (syosetu.org) extract.txt 更新**
  - `<font>` タグ廃止に対応 → `span[itemprop]` / `#honbun` セレクタに変更
  - TITLE: `span[itemprop=name]` + サイト名除去 regex
  - AUTHOR: `span[itemprop=author]`
  - CONTENT_SUBTITLE: `span[style]:1`（`.ss` 内2番目スタイル付き `<span>`）
  - CONTENT_ARTICLE: `#honbun`（本文要素が集約されているため正確に取得可能）

### セキュリティ修正

- **CodeQL アラート全件対応**
  - `java/path-injection` 30件: `getCanonicalFile()` + `startsWith()` でパス検証を強化
  - `java/polynomial-redos` (#8–11, #65): `BookInfo.java` の正規表現を possessive quantifier に修正
  - `java/command-line-injection` (#12, #67): kindlegen 実行ファイル名検証 + `getCanonicalPath()` 正規化
  - `java/partial-path-traversal` (#13): `isCacheFile()` の `startsWith` 修正

### その他

- 閉鎖・休眠サイト (dNoVeLs / NEWVEL-LIBRARY / Arcadia) の `extract.txt` に警告コメントを追記
- `test_data/` 自動生成ファイル・`.claude/settings.local.json` を `.gitignore` に追加

### 検証結果

```
Build: ✓ BUILD SUCCESSFUL
Tests: ✓ 全テスト成功
Distributions: ✓ ZIP/TAR.GZ 生成済み
カクヨム実機: ✓ 154話取得・変換確認
```

---

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
  - `AozoraEpub3.bat`
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
