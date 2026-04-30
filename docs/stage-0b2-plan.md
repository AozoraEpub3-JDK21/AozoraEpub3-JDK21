# ステージ 0B-2 実装プラン — `java.io.File` → `java.nio.file.Path`

最終更新: 2026-04-30 (初版レビュー反映済 — §10 改訂履歴参照)
対象ブランチ: `master` から派生する `chore/0b-2-N-<summary>` (PR ごとに分岐)
親計画: [`docs/modernization-plan.md`](modernization-plan.md) §3 ステージ 0B-2

---

## 1. 目的

レガシー `java.io.File` を `java.nio.file.Path` (+ `java.nio.file.Files`) に置換し、Windows パス問題・大文字小文字・シンボリックリンク追跡などの取り扱いを現代的にする。副次効果として `FileInputStream` / `FileOutputStream` を `Files.newInputStream` / `Files.newOutputStream` に置換し、リソース解放の徹底にも寄与する。

このステージは計画書 §3 で「24 ファイル、複数 PR に分割」と明記されている。**実測値 (本計画書執筆時 / `src/` のみ集計)**:

| 項目 | 値 |
|---|---|
| `import java.io.File;` を含むファイル | 24 |
| `\bFile\b` 出現 (型名・変数名混在) | 257 occ |
| `new File(` 出現 | 89 occ |
| `FileInputStream` または `FileOutputStream` (import 含む) | **72 occ across 17 ファイル** |
| `listFiles` / `mkdir(s)` / `setWritable` / `deleteOnExit` / `renameTo` / `Reader` / `Writer` / `Filter` / `NotFoundException` 関連を**合算**した値 | 152 occ across 19 ファイル |
| 公開 API (`public/protected` で File を引数/戻り値に持つ method・constructor) | ~25 シグネチャ |

(注: 集計は `src/` 配下のみ。`test/` / 過去ブランチは含まない。`152 occ` は `FileInputStream/FileOutputStream` のみではなく上記 9 種を合算した値であることに注意。)

## 2. スコープ

### 2.1 File 使用パターンの分類 (Explore 調査結果)

| 難易度 | ファイル数 | 内訳 |
|---|---|---|
| ★★★ (巨大クラス、API 破壊リスク高) | 4 | `AozoraEpub3.java`, `AozoraEpub3Applet.java`, `WebAozoraConverter.java`, `AozoraEpub3Converter.java` |
| ★★ (公開 API に File、外部 API 境界あり) | 12 | `Epub3Writer.java`, `ImageInfoReader.java`, `ArchiveTextExtractor.java`, `ArchiveCache.java`, `ArchiveScanner.java`, `BookInfo.java`, `GaijiInfo.java`, `ProfileInfo.java`, `ImageInfo.java`, `AozoraTextFinalizer.java`, `LatinConverter.java`, `NarouFormatSettings.java` |
| ★ (純粋ローカル、影響限定) | 8 | `OutputNamer.java`, `ImageUtils.java`, `GlyphConverter.java`, `AozoraGaijiConverter.java`, `JConfirmDialog.java`, `JCoverImagePanel.java`, `NarouFormatSettingsDialog.java`, `Epub3ImageWriter.java` |

### 2.2 公開 API シグネチャと caller の対応表

PR ごとに caller を含めて完結させるための一覧 (中間 PR でビルド不能を防ぐため、Phase B 化時はこの表で示す全 caller を同 PR で更新する)。

| 対象クラス | 行 | シグネチャ | 同 PR で更新が必要な caller |
|---|---|---|---|
| `OutputNamer` | L19 | `static File generateOutFile(File, File, BookInfo, boolean, String)` | `AozoraEpub3.java`, `AozoraEpub3Applet.java` (それぞれ outFile 取得経路で利用) |
| `AozoraEpub3` | L462/501/527/561 | `getOutFile/getBookInfo/convertFile/getSameCoverFileName(File, ...)` | `AozoraEpub3#main` 内・`AozoraEpub3Applet` (CLI/GUI 両ルート) |
| `AozoraEpub3Applet` | L4406 | `ConvertWorker(ArrayList<File>, ArrayList<String>, ArrayList<File>, File)` | 同クラス内 GUI 起点コードのみ |
| `BookInfo` | L127 | **`public File srcFile;` (public フィールド)** | `AozoraEpub3Applet.java:5252,5259`, `JConfirmDialog.java:729`, `BookInfo.java:184` (内部) — 計 3 箇所 |
| `BookInfo` | L182 | `BookInfo(File srcFile)` | `AozoraEpub3.java:397`, `AozoraEpub3Applet.java:3731`, `AozoraEpub3Converter.java:650`, テスト 4 ファイル (`OutputNamerTest:22,36,50,64`, `EpubMimetypeTest:62`, `AozoraEpub3ConverterTest:25`, `IniCssIntegrationTest:69`) |
| `ImageInfoReader` | L65 | `ImageInfoReader(boolean isFile, File srcFile)` | `AozoraEpub3.java`, `AozoraEpub3Applet.java` |
| `ImageInfoReader` | L86 | `File getImageFile(String)` | (caller を Grep で要再確認、現状 0 件 — 直接 caller なし) |
| `ImageInfoReader` | L92 | `File getImageFileSafe(String)` | **`Epub3Writer.java:1054` のみ** |
| `ImageInfoReader` | L240 | `void loadZipImageInfos(File, boolean)` | `AozoraEpub3.java:392`, `AozoraEpub3Applet.java:3694` |
| `ImageInfoReader` | L250 | `void loadRarImageInfos(File, boolean)` | `AozoraEpub3.java:390`, `AozoraEpub3Applet.java:3691` |
| `Epub3Writer` | L524 | `void write(AozoraEpub3Converter, BufferedReader, File, String, File, BookInfo, ImageInfoReader)` | `AozoraEpub3.java#convertFile` |
| `Epub3Writer` | L1303 | `void addGaijiFont(String, File)` | `AozoraEpub3Converter.java:2620` (引数は `gaijiInfo.getFile()` 経由) |
| `AozoraEpub3Converter` | L647 | `BookInfo getBookInfo(File, BufferedReader, ImageInfoReader, TitleType, boolean)` | `AozoraEpub3.java:511` |
| `ArchiveCache` | L27 | `ArchiveCache(File, String)` | `ArchiveTextExtractor.java:31`, `ImageInfoReader.java:258` |
| `ArchiveTextExtractor` | L40/49/76/86 | `clearCache/getTextInputStream/countZipText/countRarText(File, ...)` | `AozoraEpub3.java`, `AozoraEpub3Applet.java`, テスト数件 |
| `WebAozoraConverter` | L158 | `static WebAozoraConverter createWebAozoraConverter(String, File)` | `AozoraEpub3.java:270`, `AozoraEpub3Applet.java:4197`, テスト 6 件 |
| `WebAozoraConverter` | L284 | `void loadFormatSettings(File)` | `AozoraEpub3.java:281`, `AozoraEpub3Applet.java:4213` |
| `WebAozoraConverter` | L419/L437 | `File convertToAozoraText(...)` (戻り値 File) | `AozoraEpub3.java:285`, `AozoraEpub3Applet.java:4249`, `AozoraFullFlowTest.java:32`, `AozoraRealTest.java:32` |
| `AozoraTextFinalizer` | L76/L86 | `void finalize(File)` / `void finalize(File, File)` | `WebAozoraConverter.java` 内、テスト |
| `LatinConverter` | L25 | `LatinConverter(File)` | `AozoraEpub3Converter.java` (要再確認) |
| `NarouFormatSettings` | L228/253/387/494 | `loadReplacePatterns/load/generateDefaultIfMissing/save(File)` | `WebAozoraConverter.java`, `AozoraEpub3.java`, `AozoraEpub3Applet.java`, `NarouFormatSettingsDialog.java` |
| `ProfileInfo` | L25 | `void update(File)` | `AozoraEpub3Applet.java:4612` 周辺 |
| `GaijiInfo` | L12/L22/L26 | `GaijiInfo(String, File)` / `File getFile()` / `void setFile(File)` | `AozoraEpub3Converter.java:2620` 周辺、`Epub3Writer.java:985` (`gaijiInfo.getFile()`) |
| `JConfirmDialog` | L918 | `void showDialog(File, String, ...)` | `AozoraEpub3Applet.java` 内 |
| `NarouFormatSettingsDialog` | L76 | `NarouFormatSettingsDialog(Image, NarouFormatSettings, File)` | `AozoraEpub3Applet.java` 内 |
| `ImageInfo` | L57 | `static ImageInfo getImageInfo(File)` | `Epub3Writer.java`, `AozoraEpub3Converter.java`, `ImageInfoReader.java` (要再 Grep) |

### 2.3 File 固有 API の使用箇所

| File API | 出現箇所 | Path 系の対応 |
|---|---|---|
| `getCanonicalFile()` (パストラバーサル対策) | `AozoraEpub3.java:489-490`, `ImageInfoReader.java:94-95`, `AozoraTextFinalizer.java:88-89`, `WebAozoraConverter.java:427-428` | **要使い分け**: 存在前提なら `Path.toRealPath()`、存在しないかも知れない先 (出力先・一時ファイル・候補名) は `Path.toAbsolutePath().normalize()` または「親を `toRealPath()` してファイル名を resolve」(§3.2.1 参照) |
| `setWritable(true)` | `AozoraEpub3.java:495`, `OutputNamer.java:54` | クロスプラットフォーム一発 API なし。**初版では File 経由維持** (`path.toFile().setWritable(true)`) |
| `deleteOnExit()` | `AozoraEpub3Applet.java:3987,4612`, `ArchiveScanner.java:53` | java.nio.file に直接対応なし。**初版では File 経由維持** |
| `renameTo()` | `AozoraEpub3Applet.java:4131,4135`, `WebAozoraConverter.java:2731,2733` | `Files.move(src, dst[, REPLACE_EXISTING])` に置換。caller ごとの atomic/上書き挙動を確認 |
| `listFiles()` | `AozoraEpub3Applet.java:684,2644,4505`, `WebAozoraConverter.java:182` | `Files.list(Path)` (try-with-resources 必須) または `Files.walk` |
| `mkdir()/mkdirs()` | `AozoraEpub3.java:256`, `AozoraEpub3Applet.java:502,4169,4240,4368` | `Files.createDirectories(Path)` |
| `FileInputStream/FileOutputStream` | 72 occ across 17 files | `Files.newInputStream(Path)` / `Files.newOutputStream(Path, StandardOpenOption...)` (外部 API 境界で File 引数必須なケースは File 経由維持) |

### 2.4 既に部分的に Path 化されている箇所

`ImageInfoReader.java:419,436` は `Files.newInputStream(this.srcFile.toPath(), StandardOpenOption.READ)` の形に部分的 Path 化済。**0B-2-4 で同クラス内部の File→Path 統一を完成させると、L419/L436 の `.toPath()` 呼び出しが不要になる**。

## 3. 実装方針

### 3.1 中核戦略の見直し: 「Phase A only で 0B-2 を完走、Phase B は別ステージ」

**初版で提示した案 2 (内部経路完全 Path 化 + メイン経路のみ Phase B) は中間 PR がビルド不能になる可能性があるとのレビュー指摘 [P1] を受け、撤回**。代替として以下の 3 案を改めて提示し、§9 ユーザー確認事項のトップで合意を得る:

| 案 | 概要 | 中間 PR ビルド可能性 | API 破壊 | 最終形到達 |
|---|---|---|---|---|
| **案 1 (推奨): Phase A only で 24 ファイル完走 / Phase B は別ステージ** | 全 PR で公開 API は File 維持。各クラス内部のローカル変数・private フィールド・private method・FileInputStream/FileOutputStream のみ Path 化。公開 API の File→Path 化は別ステージ (例: 0B-2-Z または 0D)、もしくはステージ 1-A の DTO 整理と統合 | ◎ 全 PR でビルド可能 | ✗ なし | △ 0B-2 単体では中途半端 (公開フィールド・戻り値が File のまま) |
| 案 2: PR ごとに「公開 API + 全 caller」をセットで Phase B 化 | §2.2 の表の caller 列に従い、Phase B PR は対象クラス + 全 caller を同 PR で更新。中間 PR は「Phase A のみ」で完結させる。最終 PR で完全形 | ◎ ただし PR が caller 横断で大きくなる | ✓ あり (Breaking changes 記録) | ◎ 0B-2 単体で完全形 |
| 案 3: 単一巨大 PR で全公開 API 一気に Path 化 | 全 24 ファイルを 1 PR に統合 | ✗ 単一 PR なので問題なし | ✓ あり | ◎ 一発到達 |

**初版の推奨を案 2 から案 1 に変更**。理由:

1. **中間 PR ビルド可能性を最優先**: レビュー [P1] で指摘されたとおり、公開 API 戻り値型変更 (`File → Path`) を caller 更新と分離すると中間 PR がコンパイルエラーになる。これを回避するには「PR 内で対象クラス + 全 caller を同時更新」(案 2) か「Phase B を完全に別ステージに切り出す」(案 1) のどちらかが必要。
2. **0B-2 のスコープを「内部実装の現代化」に絞る**: 公開 API の破壊的変更 (Breaking changes) はステージ 1-A の record 化や DTO 整理とまとめた方が、0B-1 の Codex [P2] 指摘で経験した「単発 Breaking 通告のオーバーヘッド」を集約できる。
3. **0B-2 完走後の状態でも byte-identical 出力は確実に維持される**: 内部 Path 化のみなので動作変化ゼロ。

案 2 も**容認可能な代替**として残す。ユーザーが「案 2 の利点 (0B-2 単体で完全形に到達) を優先したい」と判断するなら、§4.1 の PR 表に従って caller を同 PR でセット更新する。

### 3.2 File 固有 API の取り扱い

#### 3.2.1 `getCanonicalFile()` → `Path.toRealPath()` の使い分け (レビュー [P2] 反映)

`File.getCanonicalFile()` と `Path.toRealPath()` は意味が同じだが完全等価ではない。**特に存在しないパスでは `toRealPath()` が `NoSuchFileException` を投げる**ため、出力先や一時ファイル候補名に対する単純置換は危険:

| API | シンボリックリンク追跡 | Windows での 8.3 short name 展開 | 存在しないパスでの挙動 |
|---|---|---|---|
| `File.getCanonicalFile()` | する | する | 例外を投げない (親の解決を試みる) |
| `Path.toRealPath()` | する (デフォルト)。`LinkOption.NOFOLLOW_LINKS` 制御可 | する | **`NoSuchFileException` を投げる** |
| `Path.toAbsolutePath().normalize()` | しない | しない | 例外を投げない |

**箇所別の置換方針** (レビュー [P2] 指摘の「base / candidate で分ける」案を採用):

| 出現箇所 | 用途 | 既存挙動 (File) | 置換方針 |
|---|---|---|---|
| `AozoraEpub3.java:489-490` | `dstPath.getCanonicalFile()` / `outFile.getCanonicalFile()` をパストラバーサル対策で比較 | dstPath は存在前提、outFile は未生成可能性あり | **base (`dstPath`) は `Path.toRealPath()`、outFile は親を `toRealPath()` してファイル名を resolve** |
| `ImageInfoReader.java:94-95` | `srcParentPath` (base) と `srcParentPath + fileName` (candidate) を解決して比較 | base は存在前提、candidate は zip/rar 内エントリ名で外部入力 | base は `Path.toRealPath()`、candidate は `parent.resolve(fileName).normalize()` でパストラバーサル攻撃の `../` を弾く |
| `AozoraTextFinalizer.java:88-89` | `baseDir.getCanonicalFile()` / `txtFile.getCanonicalFile()` | baseDir は存在前提、txtFile も既に書き込み済 | 両方 `Path.toRealPath()` |
| `WebAozoraConverter.java:427-428` | `dstPath` (base) と `dstPath + fileName` (candidate) | base は存在前提、candidate は出力先の予定位置 | base は `Path.toRealPath()`、candidate は `parent.resolve(fileName).normalize()` |

各箇所で IOException catch の例外型 (`FileNotFoundException` / `NoSuchFileException` / `IOException`) を再点検し、必要なら catch 句を `IOException` に統一する。テスト追加候補: パストラバーサル試行 (`../../../etc/passwd` 形式) を candidate で渡し、resolve 後に base 配下に収まらないことを確認。

#### 3.2.2 `setWritable(true)` の扱い

`AozoraEpub3.java:495` と `OutputNamer.java:54` の 2 occ のみ。Path API にクロスプラットフォーム一発の対応がないため**初版では File 経由のまま残す** (`path.toFile().setWritable(true)`)。byte-identical 出力影響ゼロ。

#### 3.2.3 `deleteOnExit()` と `renameTo()` の取り扱い

- `deleteOnExit()` (3 occ): java.nio.file に直接対応なし。**初版では File 経由のまま残す**。
- `renameTo()` (4 occ): `Files.move(src, dst)` に置換。caller ごとに `REPLACE_EXISTING` の有無を確認:
  - `AozoraEpub3Applet.java:4131` (mobiTmpFile → mobiFile): mobiFile が存在する可能性 → `REPLACE_EXISTING` 付与
  - `AozoraEpub3Applet.java:4135` (outFile → outFileOrg): 同上 → `REPLACE_EXISTING` 付与
  - `WebAozoraConverter.java:2731-2733`: ディレクトリリネーム → File.renameTo の挙動を Files.move で再現できるか個別確認

#### 3.2.4 `FileInputStream` / `FileOutputStream` 置換

72 occ across 17 files。原則 `Files.newInputStream(Path)` / `Files.newOutputStream(Path, StandardOpenOption...)` に置換。ただし以下の例外:

- 外部ライブラリが File 引数しか受け付けない場合 (例: `ImageIO.read(File)`, `Velocity FileResourceLoader`) は File 経由維持
- BufferedReader/Writer のラップ層は `Files.newBufferedReader(Path, Charset)` / `Files.newBufferedWriter(Path, Charset, StandardOpenOption...)` を優先

### 3.3 フィールド `BookInfo.srcFile` の扱い (レビュー [P1] 反映)

初版の「(c) Path 化 + 旧 File getter を `@Deprecated` 残置」は**直接フィールドアクセス (`bookInfo.srcFile`) に対する互換にならない**とのレビュー指摘を受け、3 案を再整理:

| 案 | 概要 | 既存外部コード互換 | 既存内部コード破壊 | 最終形 |
|---|---|---|---|---|
| (a) **[Phase A only / 案 1 採用時の唯一の選択肢]** `public File srcFile` を維持 | 何もしない。0B-2 では touch しない | ◎ | なし | △ 公開フィールドが File のまま残る |
| (b) [Phase B] **`@Deprecated public File srcFile` を残し、`public Path srcPath` を新規追加。両者を同期 (setter で両方更新)** | 公開フィールド両方アクセス可。getter/setter 二重提供 | ◎ | なし (新規 Path 派は外部に opt-in) | ○ 互換維持しつつ Path 利用可 |
| (c) [Phase B、破壊的] `public File srcFile` を削除し `public Path srcPath` のみ提供 | 公開フィールドを置換 | ✗ Breaking change | あり (3 箇所修正) | ◎ クリーン |

**ユーザー確認事項**: 案 1 採用なら自動的に (a)。案 2 採用なら (b) または (c) の選択。

### 3.4 公開 API 互換 (overload) で「File / Path 両方を受け付ける」案

案 2 採用時の補助策として、`createWebAozoraConverter(String, File)` の隣に `createWebAozoraConverter(String, Path)` を追加し、File 版を `@Deprecated` 化する選択肢もある。Java では戻り値型のみ異なる overload は不可だが、**引数型による overload は可能**。これは「Phase B を緩衝する段階的移行」として案 2 の中で採用できる。

ただし overload を増やすと API 表面が増えて将来の整理コストが高まる。**初版方針: overload は使わず、Phase A only (案 1) または明示的破壊変更 (案 2/3) のどれかで一本化する**。

## 4. PR 構成案

### 4.1 案 1 採用時の PR 分割 (推奨、Phase A only)

| PR | 対象ファイル | 区分 | 公開 API | 主な検証 |
|---|---|---|---|---|
| **0B-2-1** ✅ PR #19 | `ImageUtils`, `GlyphConverter`, `AozoraGaijiConverter`, `Epub3ImageWriter` (4 ファイル) | ★ | 維持 | 全テスト + 比較 |
| **0B-2-2** ✅ PR #20 | `ArchiveCache`, `ArchiveScanner`, `ArchiveTextExtractor` (3 ファイル) | ★★ | 維持 | 全テスト + 比較 |
| **0B-2-3** ✅ PR #21 | `ProfileInfo`, `ImageInfo`, `LatinConverter` (3 ファイル、`GaijiInfo` は §4.1.1 に分離) | ★★ | 維持 | 全テスト + 比較 |
| **0B-2-4** ✅ PR #22 | `ImageInfoReader` (内部 Path-first 化、`getImageFileSafe` のパストラバーサル検証 Path API 化を含む) | ★★ | 維持 | 全テスト + 比較 + Codex レビュー |
| **0B-2-5** ⏳ | `AozoraTextFinalizer`, `NarouFormatSettings`, `JConfirmDialog` (3 ファイル、`NarouFormatSettingsDialog` は §4.1.1 に分離)。`AozoraTextFinalizer` は §3.2.1 の `getCanonicalFile()` 置換 2 occ を含むが、PR #22 で確立した `Files.exists ? toRealPath : normalize` パターンを踏襲する | ★★ | 維持 | 全テスト + 比較 |
| **0B-2-6** | `Epub3Writer` 単独 (元 0B-2-6 から `AozoraEpub3Converter` を分離。`addGaijiFont` の File 引数や Velocity 等の外部 API 境界が複雑) | ★★★ | 維持 | 全テスト + 比較 + Codex レビュー |
| **0B-2-7** | `AozoraEpub3Converter` 単独 (元 0B-2-6 から分離) | ★★★ | 維持 | 全テスト + 比較 + Codex レビュー |
| **0B-2-8** | `WebAozoraConverter` 単独 (元 0B-2-7、`getCanonicalFile()` 8 occ 中 2 個を含む) | ★★★ | 維持 | 全テスト + 比較 + Codex レビュー |
| **0B-2-9** | `AozoraEpub3` + `AozoraEpub3Applet` + `BookInfo` (top-level、`bookInfo.srcFile` 公開フィールド・全 GUI 経路、File occ ~114) | ★★★ | 維持 | 全テスト + 比較 + Codex レビュー + 手動 GUI/CLI 確認 |

**全 PR で API 破壊なし**。中間 PR ビルド可能性は確実。`RELEASE_NOTES.md` への Breaking changes 記録は不要 (内部 Path 化のみのため)。最終 PR (0B-2-9) のみサブエージェント再レビュー必須。

### 4.1.1 Phase A only スコープでは触らないファイル (案 1 採用時)

以下 2 ファイルは初版計画 §4.1 の 0B-2-1 対象に含めていたが、実装着手時に「Phase A only スコープでは内部 Path 化の余地がない / 外部境界のため File 維持必須」と判明したため**全 PR スコープから除外**する (PR #19 でも未変更):

| ファイル | 除外理由 | 将来扱い |
|---|---|---|
| `src/com/github/hmdev/io/OutputNamer.java` | 公開 API (`static File generateOutFile(File, File, BookInfo, boolean, String)`) のみで内部処理が小さく、戻り値が File。Phase A only では内部 Path 化の余地がほぼなく、無理に Path 化すると最後に `.toFile()` で戻す必要があり可読性低下 | 案 2 / Phase B でこの公開 API ごと Path 化する際に扱う (caller: `AozoraEpub3.java`, `AozoraEpub3Applet.java` の `outFile` 取得経路) |
| `src/com/github/hmdev/swing/JCoverImagePanel.java` | Swing DnD API (`DataFlavor.javaFileListFlavor`) が `List<File>` を返す外部境界。`bookInfo.coverFileName = files.get(0).getAbsolutePath();` のように File を直接 String 化しており、Path 化する利点が薄い | 案 2 / Phase B で `BookInfo.srcFile` 公開フィールドを Path 化する際に併せて検討 |
| `src/com/github/hmdev/info/GaijiInfo.java` | public getter `File getFile()` / setter `void setFile(File)` で File 出入りする 39 行の単純 DTO。フィールドを Path 化しても getter で `gaijiPath.toFile()` 復路にする必要があり可読性低下のみ | 案 2 / Phase B でこの公開 getter/setter ごと Path 化する際に扱う (caller: `Epub3Writer.java:985` の `gaijiInfo.getFile()`) |
| `src/com/github/hmdev/swing/NarouFormatSettingsDialog.java` | private フィールド `File settingsFile` のみで、ctor 引数 + `settings.save(settingsFile)` 経由 (`NarouFormatSettings.save(File)` は公開 API 維持)。Path 化するなら `path.toFile()` 復路にする必要があり可読性低下のみ | 案 2 / Phase B で `NarouFormatSettings.save/load(File)` を Path 化する際に併せて扱う |

これにより `docs/stage-0b2-plan.md` §2.1 ★/★★ファイルのうち、本 0B-2 (案 1) スコープで実際に Path 化対象となるのは: 0B-2-1 で 4 個、0B-2-2 で 3 個、0B-2-3 で 3 個、0B-2-4 で 1 個、0B-2-5 で 3 個 (合計 14 個)。除外 4 個 (`OutputNamer`, `JCoverImagePanel`, `GaijiInfo`, `NarouFormatSettingsDialog`) は案 2 / Phase B 行き。

### 4.2 案 2 採用時の PR 分割 (Phase B 含む、参考)

ユーザーが「0B-2 単体で公開 API も Path 化したい」と判断した場合の代替案。**各 Phase B PR は対象クラス + §2.2 の表で示した全 caller を同 PR で更新**:

| PR | Phase B 対象 API | 同 PR で更新する caller (中間 PR ビルド可能性確保) |
|---|---|---|
| **0B-2-A** | (純粋ローカル群: `OutputNamer.generateOutFile` 戻り値・引数 Path 化など) | `AozoraEpub3.java`, `AozoraEpub3Applet.java` の `getOutFile` 利用箇所 |
| **0B-2-B** | (DTO 系: `GaijiInfo.getFile/setFile` 戻り値・引数 Path 化、`BookInfo.srcFile` 公開フィールド変更) | `Epub3Writer.java:985`, `AozoraEpub3Applet.java:5252,5259`, `JConfirmDialog.java:729` |
| **0B-2-C** | `ImageInfoReader#getImageFileSafe/loadZip/loadRar` 全公開 API Path 化 | `Epub3Writer.java:1054`, `AozoraEpub3.java:390/392`, `AozoraEpub3Applet.java:3691/3694` |
| **0B-2-D** | `Epub3Writer#write/addGaijiFont`, `AozoraEpub3Converter#getBookInfo`, `ArchiveTextExtractor` static method 4 個 | `AozoraEpub3.java#convertFile/getBookInfo`, `AozoraEpub3Converter.java:2620` |
| **0B-2-E** | `WebAozoraConverter#createWebAozoraConverter/loadFormatSettings/convertToAozoraText` 全公開 API | `AozoraEpub3.java:270/281/285`, `AozoraEpub3Applet.java:4197/4213/4249`, `AozoraFullFlowTest.java:32`, `AozoraRealTest.java:32`, `WebAozoraConverterNarouCompatTest.java:41,585`, `WebAozoraConverterHamelnChapterTest.java:30`, `WebAozoraConverterApiTest.java:50,81`, `NarouRbOutputComparisonTest.java:52` |
| **0B-2-F** | `AozoraTextFinalizer#finalize`, `NarouFormatSettings` 各種 | `WebAozoraConverter.java` 内、テスト |
| **0B-2-G** | `JConfirmDialog#showDialog`, `NarouFormatSettingsDialog` | `AozoraEpub3Applet.java` 内 |
| **0B-2-H** | `AozoraEpub3` 全公開 static method 4 個, `AozoraEpub3Applet.ConvertWorker` constructor | (top-level なので caller なし、main 経路のみ) |

各 Phase B PR は **caller を含めた完結スコープ**で組む。release_notes 記録 (§8) は各 PR で必須。サブエージェント再レビューも必須。

### 4.3 ブランチ命名

`chore/0b-2-N-<summary>` 形式 (例: `chore/0b-2-1-pure-local`, `chore/0b-2-9-top-level`)。

## 5. 互換性保持戦略

### 5.1 byte-identical 出力の維持

各 PR で `.NET` ポート `JavaComparisonTests` 5/5 PASS を確認。Path 化はファイル読み書きのストリーム取得経路を変えるが、**読み書きされる byte 列は不変**であるため、出力 EPUB は byte-identical を維持できるはず。

- `Files.newBufferedReader(path, charset)` と `new BufferedReader(new InputStreamReader(new FileInputStream(file), charset))` で BOM・改行・charset の扱いが等価か全箇所で確認。
- charset 省略は許容しない (JDK 21 デフォルト UTF-8 だが環境変数 file.encoding 影響を排除するため明示)。

### 5.2 例外型の変化

- `getCanonicalFile()` 系 → `Path.toRealPath()` で IOException 系 (具象 `NoSuchFileException`) に変化。catch 句の型を `IOException` に統一する判断を per-file で。
- `FileInputStream` コンストラクタは `FileNotFoundException`、`Files.newInputStream` は `IOException` (具象 `NoSuchFileException`)。catch 句の例外型を確認。

### 5.3 並列性 / Thread safety

- `Path` は immutable & thread-safe (`File` も同じ)。並列性挙動は不変。

## 6. 検証手順

### 6.1 各 PR 共通

```bash
./gradlew clean test
```
全テスト PASS を確認。

### 6.2 `.NET` ポート比較テスト

`D:\git\aozoraepub3-dotnet\tests\AozoraEpub3.Tests\JavaComparisonTests.cs` で 5/5 PASS を確認。各 PR ごと。

### 6.3 サブエージェント再レビュー (CLAUDE.md §7 強制命令)

- 案 1 採用時: 0B-2-9 (top-level) のみサブエージェント再レビュー必須。それ以外は API 破壊なしのため軽量レビューで可。
- 案 2 採用時: 全 Phase B PR でサブエージェント再レビュー必須。指摘ゼロまで反復。

### 6.4 手動確認 (0B-2-8 / 0B-2-9 のみ)

GUI 起動 → ファイル選択ダイアログ・ドラッグ&ドロップ・出力先指定の動作確認。

### 6.5 narou.rb 連携確認 (0B-2-9 のみ)

`java -cp build/libs/AozoraEpub3.jar AozoraEpub3 ...` での CLI 引数経由変換を smoke テスト (n9623lp 等)。

## 7. リスクと緩和

| リスク | 緩和策 |
|---|---|
| 中間 PR ビルド不能 (公開 API 戻り値型変更を caller 更新と分離) | **案 1 採用で根本回避**。案 2 採用時は §2.2 の caller 表に従い対象クラス + 全 caller を同 PR で更新 |
| `getCanonicalFile()` → `toRealPath()` の `NoSuchFileException` 発生 | §3.2.1 の表に従い、base (存在前提) は `toRealPath()`、candidate (未生成可能性) は `parent.resolve(...).normalize()` で使い分け |
| `setWritable(true)` の Path API 不在 | §3.2.2 で File 経由のまま残す |
| `deleteOnExit()` の Path API 不在 | §3.2.3 で File 経由のまま残す |
| `renameTo()` の `Files.move` 置換時、既存ファイルを `REPLACE_EXISTING` なしで move して上書きせず例外になる | §3.2.3 で caller ごとの上書き挙動を確認 |
| 公開 API 戻り値型変更によるバイナリ互換性破壊 (案 2 採用時) | 案 2 PR ごとに Breaking changes を §8 で記録 |
| `BookInfo.srcFile` 公開フィールド変更 | §3.3 (a/b/c) のいずれかをユーザー確認で確定。(b) なら互換維持、(c) なら Breaking changes 記録 |
| 単一 PR の diff 肥大化 (特に 0B-2-9 / 0B-2-H) | 案 1 採用で 0B-2-9 のみが大規模 (~500-700 行)、案 2 採用なら更に大きくなる可能性 |
| 例外型 (FileNotFoundException → NoSuchFileException) 変化 | catch 句を `IOException` 型に統一する判断を per-file で。具象例外で catch している箇所はテスト追加 |
| `Files.newBufferedReader` charset 省略時の environment 依存 | charset を明示 (UTF-8 / MS932 用途別) |
| Trial & Error 2 回上限超過 | 同一箇所で 2 回連続失敗したら 3 回目は試さずユーザー報告 (CLAUDE.md §3) |

## 8. リリースノート反映 (レビュー [P2] 反映)

**初版で「`memory/release_notes_pending.md` に記録」と書いた箇所は撤回**。CLAUDE.md §2 強制命令 (「残件・todo・backlog は memory ではなく docs/ に書く」) との整合性のため、以下のいずれかに反映する:

1. **直接 `RELEASE_NOTES.md` の Unreleased セクションに記録** (推奨): リリース時にバージョン bump して確定。本リポジトリの `RELEASE_NOTES.md` は現状 Unreleased セクションを持たないため、初回追加時に新設する。
2. 新規 `docs/release-notes-pending.md` を作成して docs/ 永続化に移行: 既存の `memory/release_notes_pending.md` (PR #17 / #18 分の記録) もこちらへ移行する別タスクが発生する。

**初版方針 (本計画書では)**: 案 1 採用なら本ステージは API 破壊なしのためリリースノート反映は不要 (内部 Path 化のみ)。案 2 採用時は Phase B PR ごとに `RELEASE_NOTES.md` 直接追記する形を採用 (上記 1)。

既存の `memory/release_notes_pending.md` を docs/ に移行する作業は本ステージスコープ外。別タスクで対応 (CLAUDE.md §2 違反の解消は別 PR、計画書 §3 のロードマップとは別軸)。

## 9. ユーザー確認事項

着手前に以下の合意を得る:

1. **Phase A/B 戦略の選択** (§3.1): **案 1 (Phase A only で 24 ファイル完走、Phase B は別ステージ、推奨)** / 案 2 (PR ごとに対象 + 全 caller セット Phase B 化) / 案 3 (単一巨大 PR) のどれを採るか。
2. **`getCanonicalFile()` の置換戦略** (§3.2.1): base は `Path.toRealPath()` + candidate は `parent.resolve(...).normalize()` という箇所別使い分けで良いか。あるいは全箇所一律 `toAbsolutePath().normalize()` (シンボリックリンク非追跡) に揃えるか。
3. **`setWritable()` / `deleteOnExit()`** (§3.2.2/§3.2.3): File 経由維持 (`path.toFile().setWritable(true)` 等) で良いか。
4. **`BookInfo.srcFile` 公開フィールドの扱い** (§3.3): 案 1 採用 → 自動的に (a) (`File` のまま放置) / 案 2 採用時は (b) (互換維持: 旧 + 新 srcPath 同期) または (c) (破壊的 rename) のどちらか。
5. **PR 分割粒度** (§4.1/§4.2): 案 1 なら 9 PR、案 2 なら 8 PR (caller 横断)。これで良いか、統合・細分化が必要か。
6. **リリースノート反映先** (§8): 案 1 採用なら反映不要、案 2 採用時は `RELEASE_NOTES.md` 直接追記方式で良いか。既存 `memory/release_notes_pending.md` の docs/ 移行は別タスクで対応する方針で良いか。
7. **サブエージェント再レビューの許容ラウンド数** (§6.3): 案 1 採用なら 0B-2-9 のみ、案 2 採用なら全 Phase B PR (5 本以上)。許容ラウンド数を事前合意 (PR #18 は 4 巡レビュー)。

## 10. 改訂履歴

- 2026-04-30 初版 (調査結果と PR 分割案、ユーザー確認 6 点を含む)
- 2026-04-30 初版レビュー反映:
  - [P1] 案 2 (内部経路完全 Path 化 + メイン経路のみ Phase B) は中間 PR ビルド不能リスクとの指摘を受け推奨を**案 1 (Phase A only) に変更**。案 2 / 案 3 も §3.1 / §4.2 で代替案として残す。Phase B PR は対象クラス + 全 caller をセットで定義する PR 表を §4.2 に追加
  - [P1] §3.3 `BookInfo.srcFile` の互換策を再整理。「`@Deprecated public File srcFile` 残置 + `Path srcPath` 同期」(b) を新設し、初版の (c) (フィールド rename + getter 残置) は破壊的変更として性格を明確化
  - [P2] §3.2.1 `getCanonicalFile()` → `Path.toRealPath()` を箇所ごとに「base は `toRealPath()`、candidate は `parent.resolve(...).normalize()`」と使い分ける方針に変更。出現箇所 4 ファイル × 8 occ 個別の置換方針を表形式で追加
  - [P2] §8 リリースノート反映先を `memory/release_notes_pending.md` から `RELEASE_NOTES.md` の Unreleased セクション直接追記方式に変更 (CLAUDE.md §2 整合)。既存 memory ファイルの docs/ 移行は別タスクで対応する旨を注記
  - [P3] §1 数値メトリクスを訂正。`FileInputStream/FileOutputStream` は **72 occ across 17 ファイル** (初版で「152 occ」と書いたのは listFiles + mkdir + setWritable + deleteOnExit + renameTo + Reader + Writer + Filter + NotFoundException を合算した値で、`FileInputStream/FileOutputStream` 単独ではない旨を明記)
  - §2.2 公開 API シグネチャ表に「同 PR で更新が必要な caller」列を追加。`ImageInfoReader#getImageFileSafe` の唯一 caller は `Epub3Writer.java:1054`、`WebAozoraConverter#convertToAozoraText` の caller は src 2 + test 4、など個別に列挙
  - §2.4 「既に部分的に Path 化されている箇所」を新設。`ImageInfoReader.java:419,436` で既に `Files.newInputStream(this.srcFile.toPath(), ...)` の形に部分対応済の事実を反映
- 2026-04-30 2 巡目レビュー反映 (条件付き GO 後の軽微修正):
  - [P2] §2.2 caller 表の誤記訂正。`BookInfo(File srcFile)` の caller を `WebAozoraConverter.java` (誤) から `AozoraEpub3.java:397` / `AozoraEpub3Applet.java:3731` / `AozoraEpub3Converter.java:650` + テスト 4 ファイル (`OutputNamerTest`, `EpubMimetypeTest`, `AozoraEpub3ConverterTest`, `IniCssIntegrationTest`) に修正。`ArchiveCache(File, String)` の caller に `ImageInfoReader.java:258` を追加 (初版は `ArchiveTextExtractor.java` のみ記載で要再 Grep 注記済の状態だった)。なお案 2 を実際に選ぶ場合は §2.2 の他エントリも `src,test` 全体の grep 結果で同様に網羅検証する追補作業が必要 (本表は今回のレビュー指摘対象 2 件のみ修正済)
  - [P3] §4.1 line 181 の古い `release_notes_pending` 表記を `RELEASE_NOTES.md への Breaking changes 記録は不要 (内部 Path 化のみのため)` に置換
  - [P3] §2.4 line 83 の PR 番号参照を `0B-2-2 ないし 0B-2-3` から `0B-2-4` (§4.1 の `ImageInfoReader` 担当 PR と整合) に修正
- 2026-04-30 0B-2-1 PR #19 レビュー反映:
  - [P3] §4.1 の 0B-2-1 対象を実装差分 (PR #19) に揃えて 4 ファイル (`ImageUtils`, `GlyphConverter`, `AozoraGaijiConverter`, `Epub3ImageWriter`) に縮小。PR #19 マーク (`✅`) を付与
  - §4.1.1 「Phase A only スコープでは触らないファイル」を新設。`OutputNamer.java` / `JCoverImagePanel.java` を 0B-2 全 PR スコープから除外し、案 2 / Phase B (公開 API Path 化) で扱う旨を明記。これにより後続 PR の担当範囲追跡で取りこぼし・重複が起きないようにする
- 2026-04-30 0B-2-2 完了: PR #20 マーク (`✅`) を §4.1 表に付与
- 2026-04-30 0B-2-3 着手時の事前評価反映:
  - §4.1 0B-2-3 対象から `GaijiInfo` を除外し、`ProfileInfo` / `ImageInfo` / `LatinConverter` の 3 ファイルに縮小
  - §4.1.1 「Phase A only スコープでは触らないファイル」に `GaijiInfo.java` を追加 (public getter/setter で File 出入りするため Phase A 余地なし、案 2 / Phase B 行き)
  - 0B-2 案 1 スコープから除外されるファイル合計: 3 個 (`OutputNamer`, `JCoverImagePanel`, `GaijiInfo`)
- 2026-04-30 PR #22 (0B-2-4 ImageInfoReader) Codex レビュー反映 + PR 粒度方針再編成:
  - PR #22 の P2 指摘 (`getImageFileSafe` の symlink 追跡喪失) → `Files.exists(candidate) ? candidate.toRealPath() : candidate` の 2 段階チェックで修正、symlink エスケープ攻撃検出を維持
  - **PR 粒度方針の更新** (Codex メタ提案反映): Phase A only の機械置換は 3〜5 ファイル単位でまとめる、意味のある挙動変更だけ単独 PR とする方針に転換。残 0B-2-5〜9 (元 5 PR) を再編成し、0B-2-5 で 3 ファイル合体、0B-2-6/0B-2-7/0B-2-8/0B-2-9 を意味のある単独 PR に分解 (合計 5 PR、内訳変更)
  - §4.1 表に PR # マーク (`✅ PR #19/#20/#21/#22`) を全て付与、0B-2-5 以降の対象ファイルを再構成
  - §4.1.1 に `NarouFormatSettingsDialog.java` を追加 (除外 4 個目: private フィールド + ctor 引数のみで Phase A 余地なし)
