# コード監査 follow-ups (2026-07-23)

`src/` 全体を対象としたコード監査（リソースリーク / パストラバーサル / 例外処理 / ネットワーク処理 / 文字コード / 状態リセット）で検出した残件の追跡ドキュメント。

親計画: [`modernization-plan.md`](modernization-plan.md) — 本ドキュメントの項目はステージ 0B 系（防御的コーディング）の追加分として扱う。

監査範囲・除外条件:

- 0B-4c (PR #30〜#35) で `/* 意図的: <理由> */` コメントを付与済みの空 catch は対象外
- ビルド・テストは実行せず、コード読解のみで判定（各項目の「状態」欄で実装後に更新する）

## 対応状況サマリ

| # | 深刻度 | 概要 | 状態 | PR |
|---|--------|------|------|-----|
| 1 | 🔴 高 | Web ページ由来 href / img src によるパストラバーサル | 未着手 | - |
| 2 | 🔴 高 | `Epub3Writer.write()` の例外握り潰しで失敗が成功扱い | ✅ 対応済 | #39 |
| 3 | 🟡 中 | 失敗・キャンセル時に入力 `src` が未クローズ | ✅ 対応済 | #39 |
| 4 | 🟡 中 | `ImageInputStream` 未クローズ | 未着手 | - |
| 5 | 🟡 中 | レガシー `URL.openStream()` 3 箇所にタイムアウトなし | 未着手 | - |
| 6 | 🟡 中 | URL サニタイズ regex が文字クラス欠落で実質無効 | 未着手 | - |
| 7 | 🟢 低 | 0B-4c 監査漏れの空 catch 2 件 | 未着手 | - |
| 8 | 🟢 低 | `new FileReader` のデフォルトエンコーディング依存 | 未着手 | - |
| 9 | 🟢 低 | パスなし URL 入力で `StringIndexOutOfBoundsException` | 未着手 | - |
| 10 | 🟢 低 | `getTextInputStream` の null 戻りで NPE | 未着手 | - |
| 11 | 🟢 低 | ソース内コメントの文字化け | 未着手 | - |

---

## 🔴 高

### 1. Web 小説ページ由来 href / img src によるパストラバーサル（任意ファイル書き込み）

**場所**

- `src/com/github/hmdev/util/CharUtils.java:178` — `escapeUrlToFile`
- `src/com/github/hmdev/web/WebAozoraConverter.java:897` / `:998` — 章キャッシュファイル生成
- `src/com/github/hmdev/web/WebAozoraConverter.java:1621` — 挿絵ファイル生成
- `src/com/github/hmdev/web/WebAozoraConverter.java:430` — 既存の `safeDstFile`（本文 txt にのみ適用）

**何が起きるか**

`escapeUrlToFile` は `?` `&` を `/` に、`: * | < > " \` を `_` に置換するだけで、**`..` セグメントと `/` を除去しない**。
章リンクは `href.attr("href")` の生値を連結して生成される（`WebAozoraConverter.java:825-835`）ため、取得先サイトが改ざんされている / 悪意あるサイトの場合に
`href="../../../../Users/x/AppData/..."` や `<img src="http://host/../../evil">` を返すと、
`cacheFile()`（`:2740-`、`mkdirs` + レスポンス本体書き込み）が **cachePath / dstPath 外の任意パスにファイルを作成・上書き**する。

PR #22/#23 で導入した `safeDstFile()` は本文 txt にしか適用されておらず、章キャッシュ・画像の 2 経路は未防御。

**修正方針**

- `chapterCacheFile` / `imageFile` の生成を `safeDstFile` 相当の normalize + `startsWith(base)` 検証に通す
  （`safeDstFile` は `dstPath` 固定なので、base を引数に取る形へ一般化する）
- `escapeUrlToFile` で `..` セグメントを `_` 化する多層防御も併用
- 回帰テスト: `..` を含む href / img src を持つ HTML フィクスチャで、base 外に書き込まれないことを検証

### 2. `Epub3Writer.write()` が全例外を握り潰し、失敗しても「変換完了」と報告される

**場所**

- `src/com/github/hmdev/writer/Epub3Writer.java:1130-1131` — `catch (Exception e) { logger.error(...) }` のみで再スローなし
- `src/AozoraEpub3.java:545-548` — 直後に「変換完了」をログ出力

**何が起きるか**

ディスクフル・テンプレートエラー・画像破損などで EPUB 出力が途中失敗しても `write()` は正常リターンし、
呼び出し側 `convertFile` は成功として扱う。結果として **壊れた .epub が成功として残り**、CLI 終了コードも 0 のまま。
narou.rb 連携では破損 EPUB が成功扱いで取り込まれる。

**修正方針**

- catch で再スロー（または失敗を boolean / 例外で伝播）
- 失敗時は出力途中の epubFile を削除
- CLI は非 0 終了コードを返す
- GUI 経路（`AozoraEpub3Applet`）でエラーダイアログ / LogAppender に確実に出ることを確認する

**注意**: 終了コードの変更は narou.rb 連携の外部インタフェース変更にあたる。
narou.rb 側は AozoraEpub3 の終了コードを見て成否を判定するため、
「今まで成功扱いだった失敗ケースが失敗になる」という**意図した振る舞い変更**である点をリリースノートに明記すること。

**リリースノート記載事項**（PR #39 で確定。次回リリース時にそのまま転記する）:

- **CLI の終了コードが変わります。** 変換に失敗した場合、これまで常に `0` を返していたのが `1` を返すようになります。
  narou.rb は終了コードで成否を判定するため、**これまで「成功」として取り込まれていた破損 EPUB が失敗として扱われる**ようになります。
  これは意図した変更です。誤検知（従来どおり成功すべき変換が失敗扱いになること）が起きないことは、
  画像デコード失敗・表紙取得失敗・不審アーカイブエントリなどが従来どおり局所的に握り潰されることを確認して担保しています。
- **変換に失敗した場合、出力途中の壊れた `.epub` は削除されます**（従来は残っていました）。変換をキャンセルした場合も同様です。
- `-h` / `--help` は終了コード `0`、引数なし・ini 不在・出力先不在・入力ファイル不在は `1` を返します。

---

## 🟡 中

### 3. EPUB 出力失敗・キャンセル時に入力 Reader が閉じられない（Windows でファイルロック残留）

**場所**

- `src/com/github/hmdev/writer/Epub3Writer.java:961` — `src.close()` は成功経路のみ
- `src/com/github/hmdev/writer/Epub3Writer.java:625` / `:686` — `canceled` による早期 return
- `src/com/github/hmdev/writer/Epub3Writer.java:1132-1143` — finally 節は `zos` しか閉じない

**何が起きるか**

変換キャンセルや途中例外で `src`（txt の場合 `Files.newInputStream` 直結）が未クローズのまま残る。
Windows では入力 txt がロックされ、削除・再変換に失敗し得る。

**修正方針**

finally 節で `src` もクローズする。#2 と同一ファイル・同一 try/finally を触るため、**#2 と同じ PR にまとめるのが自然**。

### 4. `ImageInputStream` 未クローズ（ImageIO ディスクキャッシュの一時ファイル滞留）

**場所**: `src/com/github/hmdev/info/ImageInfo.java:79-89`

**何が起きるか**

`ImageIO.createImageInputStream(is)` の戻り値 `iis` を `close()` していない。
ImageIO はデフォルトで `FileCacheImageInputStream`（temp ファイル）を作ることがあり、
大量画像を含む書籍の変換で一時ファイル / FD が GC 任せで滞留する。

**修正方針**

`try (ImageInputStream iis = ...)` にする。`iis.close()` は引数の `is` を閉じないため、呼び出し側のストリーム所有権に影響しない。

### 5. レガシー `URL.openStream()` にタイムアウトなし（無限ハング）

**場所**

- `src/com/github/hmdev/writer/Epub3Writer.java:701` — 表紙 URL 取得
- `src/AozoraEpub3Applet.java:4189` — 青空文庫 zip ダウンロード
- `src/com/github/hmdev/image/ImageUtils.java:89`

**何が起きるか**

HttpClient 移行済みの経路（`WebAozoraConverter` / `NarouApiClient` は connect 10s / request 30s 設定済み）と異なり、
この 3 箇所は `URLConnection` デフォルト（タイムアウト無制限）。応答しないサーバで**変換スレッドが永久にブロック**する。

**修正方針**

共有 `HttpClient` 経由に統一するか、`URLConnection` に connect / read タイムアウトを設定する。
定数はマジックナンバーにせず、既存の HttpClient 設定と同じ値（10s / 30s）を共有定数化する。

### 6. URL サニタイズ regex が文字クラスになっておらず実質無効

**場所**: `src/AozoraEpub3Applet.java:4180`

**何が起きるか**

```java
urlString.substring(...).replaceAll("\\?\\*\\&\\|\\<\\>\"\\\\", "_")
```

`[ ]` が無いため、リテラル連続文字列 `?*&|<>"\` にしかマッチしない。個々の禁止文字は置換されず、
`?` 等を含む URL で Windows のファイル作成が例外になる。
直後に `new File(urlPath).getName()` を使うためトラバーサルには直結しないが、サニタイズ意図が機能していない。

**修正方針**: `replaceAll("[?*&|<>\"\\\\]", "_")` に修正。

---

## 🟢 低

### 7. 0B-4c 監査漏れの空 catch（`意図的:` コメントなし）2 件

- `src/AozoraEpub3Applet.java:3154` — `catch (Exception e) {}`（D&D の transferData 取得）
- `src/com/github/hmdev/swing/JConfirmDialog.java:544` — `catch (MalformedURLException e1) {}`（アイコン読込）

実害は小さいが、0B-4c で確立した規約（`/* 意図的: <理由> */` コメント必須）の漏れ。コメントを追記する。

### 8. `new FileReader(file)` のデフォルトエンコーディング依存

**場所**: `src/AozoraEpub3Applet.java:3391` — `readInternetShortCut`

Java 18+ ではデフォルトが UTF-8 固定になるが、Windows の `.url` ファイルは ANSI (MS932) の場合がある。
`URL=` 行が ASCII なら実害なしだが、非 ASCII を含む URL で文字化けの可能性。charset を明示する。

### 9. パスなし URL 入力で `StringIndexOutOfBoundsException`

**場所**: `src/com/github/hmdev/web/WebAozoraConverter.java:481`

`urlString.indexOf('/', ...)` が -1 のまま `substring` に渡る。
`https://example.com`（末尾スラッシュなし）を入力し、かつ `:463-477` の末尾スラッシュ補正 HTTP 確認が失敗（オフライン等）した場合に例外。
補正成功時は顕在化しないため**要確認**（再現条件の確定が先）。indexOf の -1 チェックを追加する。

### 10. `getTextInputStream` の null 戻りで NPE

**場所**

- `src/AozoraEpub3.java:541` — `new InputStreamReader(null, encType)`
- `src/com/github/hmdev/io/ArchiveTextExtractor.java:61,72` — zip 内に txt がない場合等に null を返す

txt を含まない zip の変換で、ユーザー向けエラーが NPE メッセージになる（外側 catch があるためクラッシュはしない）。
null チェックして明示メッセージで return する。

### 11. ソース内コメントの文字化け (mojibake)

**場所**: `src/com/github/hmdev/web/WebAozoraConverter.java:847` / `:850`

`__NEXT_DATA__ 繝輔か繝ｼ繝ｫ繝舌ャ繧ｯ...` のように mojibake 化している。動作影響はないが、エンコーディング事故の痕跡。
同ファイルは編集時に注意が必要（UTF-8 CRLF、Edit ツールが失敗する場合はバイトレベル編集）。

---

## 監査で問題なしを確認した領域

以下は今回の監査で確認し、対応不要と判断した領域。再監査時の重複を避けるために記録する。

- **XXE**: `DocumentBuilderFactory` / SAX / StAX の使用ゼロ。HTML パースは jsoup のみで XXE 経路なし
- **HttpClient**: `WebAozoraConverter`（connect 10s / request 30s）、`NarouApiClient` ともタイムアウト設定済み。リトライは再ダウンロード 1 回のみで無限ループなし
- **ZIP/RAR 読み込み**: `ArchiveScanner` / `Epub3Writer` の zip 画像展開はファイルシステムに展開せず EPUB 内エントリ名のみ使用。zip 経路は `sanitizeArchiveEntryName`（`Epub3Writer.java:1112`）で防御済み。`ArchiveTextExtractor` のキャッシュは `AozoraEpub3.java:552` で `clearCache` 呼び出し済み
- **`Files.list()`**: 全 6 箇所（`AozoraEpub3Applet` 688/2654/4523、`AozoraEpub3Converter` 460、`GlyphConverter` 36、`Epub3Writer` 972）とも try-with-resources 済み
- **状態リセット**: `WebAozoraConverter` の FQDN キャッシュ再利用時のフィールドリセット（`:445-451`）、`englishSentences` / `kanjiNumbers` のクリア（`:1327-1329`）、`Epub3Writer.write` 冒頭の全コレクション clear（`:535-542`）を確認
- **設定ファイル読み書き**: `chuki_*.txt` / `extract.txt` / `update.txt` 系の Reader/Writer は try-finally または try-with-resources で全てクローズ済み、UTF-8 明示
- **`convertNarouTags` の `while(true)`** 2 箇所は終了条件あり（無限ループなし）

### 要確認（未追跡）

- `src/com/github/hmdev/web/WebAozoraConverter.java:2295` — `[jump:URL]` の `<a href>` 生成で URL が非エスケープ。後段の変換でのエスケープ有無を追跡していない

---

## 進め方

優先度順に着手する。各 PR は以下のゲートを通す:

1. 実装（Fable）
2. **ゲート A**: Codex による差分レビュー（`/codex-review`）
3. **ゲート B**: Opus サブエージェントによるフレッシュコンテキスト再レビュー（CLAUDE.md §7）
4. 指摘ゼロになるまで修正 → 再レビューを反復
5. `gradlew test` PASS を確認して PR 作成

PR 分割案:

| PR | 対象 | 理由 |
|----|------|------|
| A | #1 パストラバーサル | セキュリティ修正。単独で cherry-pick 可能にする |
| B | #2 + #3 | 同一 try/finally を触るため分離不可 |
| C | #4 + #5 + #6 | リソース / ネットワーク堅牢化 |
| D | #7〜#11 | 低リスクな整理。まとめて 1 PR |
