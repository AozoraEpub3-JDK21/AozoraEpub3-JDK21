# AozoraEpub3 – Copilot Project Instructions

These instructions tailor Copilot to this repository so it can generate correct, maintainable changes and avoid common pitfalls.

## Project Overview
- Purpose: Convert Aozora-style text into EPUB 3.2, supporting Ruby, vertical writing, images, and device presets.
- Language/Build: Java 21 (Gradle 8), JUnit 4.13 tests.
- Templates: Apache Velocity templates under `template/` control most XHTML/CSS generation.
- CLI: `AozoraEpub3` (main class) orchestrates parsing, conversion, and packaging.

## Key Paths
- Java sources: `src/`
- Tests: `test/`
- Templates: `template/` (e.g., `template/OPS/css/vertical_text.vm`)
- Presets (INI): `presets/*.ini`
- Test data: `test_data/`
- Distribution scripts: `build/scripts/`

## Build & Run

### Important: Build Tasks (混乱防止)
プロジェクトはカスタムビルドタスクを使用しています：

1. **FAT JAR作成** (単一実行可能JAR)
   - コマンド: `./gradlew jar` (Windows: `gradlew.bat jar`)
   - 出力: `build/libs/AozoraEpub3.jar`
   - 用途: すべての依存関係を含む単一JAR

2. **配布パッケージ作成** (ZIP/TAR)
   - コマンド: `./gradlew dist` (Windows: `gradlew.bat dist`)
   - 出力: 
     - `build/distributions/AozoraEpub3-<version>.zip`
     - `build/distributions/AozoraEpub3-<version>.tar.gz`
   - 内容: JAR + ランチャースクリプト + ドキュメント + テンプレート
   - 注意: `distZip`は無効化済み。`dist`を使用すること

3. **テスト実行**
   - コマンド: `./gradlew test` (Windows: `gradlew.bat test`)

### 実行方法
- Run CLI: `java -jar build/libs/AozoraEpub3.jar [options] input.txt`
- Sample conversion (UTF-8): `java -jar build/libs/AozoraEpub3.jar -of -d out input.txt`
- GUI起動: 引数なしで実行 `java -jar build/libs/AozoraEpub3.jar`

## CI & Validation
- GitHub Actions workflow builds, runs tests, generates sample EPUBs, and runs `epubcheck`.
- Basic checks for EPUB 3.2 and 電書協/電書連ガイド対応 are included as shell assertions.
- When adding tests, prefer small, deterministic unit tests over end-to-end unless necessary.

## Epubcheck Integration (現状メモ)
- feature/epubchecker は master を取り込み済みでテスト済み（`gradlew test` 114/114）。
- EPUB 書き出し直後に `EpubcheckRunner` が自動実行（`lib/epubcheck.jar` がある場合）。`-Depubcheck.jar=/path/to/jar` で上書き可、タイムアウト30s、エラー数をログに集計。
- ソースマップ: `--include-source-map` がデフォルト、`--no-source-map` で無効化。生成ファイルは `OPS/aozora/aozora-source-map.json`。`SourceMapper` が `/` 区切りで正規化し逆引きする。
- 解析パイプライン: `EpubValidator` (JSON取得) → `ErrorAnalyzer` (SourceMapperと結合) → ログ出力。未実装の辞書/i18nやUIは DESIGN_EPUBCHECKER.md のフェーズ2以降で拡張予定。

### IndexNow (Docs 配信・検索エンジン通知)
- docs 配下のサイト更新時は `docs/sitemap.xml` に新規/更新ページが含まれることを前提に IndexNow 送信を行う（ワークフローでサイトマップから自動収集）。
- ホスト確認用キーは `docs/fad6fa3a81974f6aa0740a0861fbaefe.txt`（内容はキー文字列の1行）。ページ追加時、キーファイルの配置は変更不要。
- Actions Variables: `INDEXNOW_HOST` と `INDEXNOW_BASE_URL` を必要に応じて設定。未設定でもサイトマップの最初のURLから自動検出するが、ホスト移行時は変数の整合性を優先。
- 送信タイミング: push（docs/** の変更）と手動実行（workflow_dispatch）に加え、毎日 03:00 UTC（JST 12:00）の定期実行で送信。
- ワークフローのログに「Discovered N URL(s)」と先頭20件サンプル、`Request body` の `urlList` が全件含まれていることを確認。必要ならサイトマップ生成/掲載ページを見直す。

## Coding Guidelines
- Keep changes minimal and focused; align with existing style.
- Avoid introducing global state. If needed (e.g., Velocity), allow dependency injection.
- Favor small helpers over large monolith methods; keep public APIs stable.
- Validate inputs and fail fast with clear messages.
- Don’t add license headers unless explicitly requested.- **Git Commits**: All commit messages **must be in Japanese**. Format should clearly describe the what/why of changes.
## Templates (Velocity) – Important
- Velocity resources live under `template/`. Use relative paths consistently from a configurable `templatePath`.
- Do NOT hard-code absolute paths. Tests and CI may run with different working directories.
- Avoid calling `Velocity.init()` unconditionally inside constructors. Prefer:
  - Accepting a `VelocityEngine` instance, or
  - Initializing only if not already configured.
- Keep placeholders and conditionals simple. Avoid mixing presentation with business logic.

## Presets / INI → CSS
- Device presets and INI values map to CSS via Velocity templates.
- Add or adjust CSS variables by threading values through the model/context used by templates.
- When adding new INI keys, update:
  - Parsing/validation logic
  - `Epub3Writer`/converter context population
  - Corresponding `.vm` templates
  - Unit tests covering both parsing and emitted CSS

## Testing
- Use JUnit 4 (`org.junit.Test`).
- Put fixtures under `test_data/` and avoid relying on the process working directory.
- Prefer unit tests for:
  - Parsing (INI, Aozora text features)
  - Small rendering functions
  - EPUB packaging helpers
- End-to-end tests calling the CLI can be flaky in Gradle workers due to path/resource differences. If unavoidable, run in a temporary directory and assert on produced `.epub` contents.
- Use `epubcheck` in CI for final validation; no network calls in tests.

## Common Pitfalls
- Velocity resource resolution failing under tests: configure a FileResourceLoader pointing to `template/` and avoid double-initializing Velocity.
- Referencing files like `chuki_latin.txt` relative to the working directory: prefer resolving via project root or classpath resources.
- Windows path separators: prefer forward slashes or `Paths` APIs.
- Zip packaging: ensure the `mimetype` file is stored uncompressed and first, per EPUB spec.

## PR Checklist (for Copilot)
- Build passes locally: `gradlew test`.
- New logic covered by tests in `test/`.
- Templates compile and render with expected context keys.
- CI workflow unaffected or updated if needed.
- Sample EPUBs pass `epubcheck` (verified by CI).

## Helpful Snippets
- Configure Velocity (tests):
  ```java
  Properties p = new Properties();
  p.setProperty("resource.loaders", "file");
  p.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
  p.setProperty("resource.loader.file.path", projectRoot.resolve("template").toString());
  Velocity.init(p);
  ```
- Read a file from `test_data/`:
  ```java
  Path data = Paths.get("test_data", "test_title.txt");
  String s = Files.readString(data, StandardCharsets.UTF_8);
  ```

## When Unsure
- Prefer asking for the intended device/preset (e.g., Kobo, Kindle).
- Confirm whether a change belongs in Java code or the Velocity templates.
- If a Velocity context key is missing, check writer/converter population and tests.
 - Docs ページを追加・更新する場合は、`docs/sitemap.xml` に反映されているか確認し、IndexNow ワークフローが URL を拾えることを前提に作業する（個別のURL列挙は不要）。
