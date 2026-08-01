# コード監査 follow-ups (2026-07-23)

`src/` 全体を対象としたコード監査（リソースリーク / パストラバーサル / 例外処理 / ネットワーク処理 / 文字コード / 状態リセット）で検出した残件の追跡ドキュメント。

親計画: [`modernization-plan.md`](modernization-plan.md) — 本ドキュメントの項目はステージ 0B 系（防御的コーディング）の追加分として扱う。

監査範囲・除外条件:

- 0B-4c (PR #30〜#35) で `/* 意図的: <理由> */` コメントを付与済みの空 catch は対象外
- ビルド・テストは実行せず、コード読解のみで判定（各項目の「状態」欄で実装後に更新する）

## 対応状況サマリ

| # | 深刻度 | 概要 | 状態 | PR |
|---|--------|------|------|-----|
| 1 | 🔴 高 | Web ページ由来 href / img src によるパストラバーサル | ✅ 対応済 | #38 |
| 2 | 🔴 高 | `Epub3Writer.write()` の例外握り潰しで失敗が成功扱い | ✅ 対応済 | #39 |
| 3 | 🟡 中 | 失敗・キャンセル時に入力 `src` が未クローズ | ✅ 対応済 | #39 |
| 4 | 🟡 中 | `ImageInputStream` / `ImageOutputStream` 未クローズ | ✅ 対応済 | #40 |
| 5 | 🟡 中 | レガシー `URL.openStream()` 3 箇所にタイムアウトなし | ✅ 対応済 | #40 |
| 6 | 🟡 中 | URL サニタイズ regex が文字クラス欠落で実質無効 | ✅ 対応済 | #40 |
| 7 | 🟢 低 | 0B-4c 監査漏れの空 catch 2 件 | ❌ 誤検出（対応不要） | #41 |
| 8 | 🟢 低 | `new FileReader` のデフォルトエンコーディング依存 | ✅ 対応済 | #41 |
| 9 | 🟡 中 | パスなし URL 入力で `StringIndexOutOfBoundsException` | ✅ 対応済 | #41 |
| 10 | 🟢 低 | `getTextInputStream` の null 戻りで NPE | ✅ 対応済 | #41 |
| 11 | 🟢 低 | ソース内コメントの文字化け（ログ文字列を含む） | ✅ 対応済 | #41 |
| 12 | 🟢 低 | Windows 予約デバイス名でキャッシュが毎回無効化される | ✅ 対応済 | #42 |
| 13 | 🟡 中 | 制御文字・末尾空白のパスセグメントで `InvalidPathException`（変換全体が中断） | ✅ 対応済 | #44 |
| 14 | 🟢 低 | 青空 zip 直接 DL 経路の `replaceInvalidFileChars` が `:` と制御文字を除去しない | ✅ 対応済 | #45 |
| 15 | 🟡 中 | 出典 URL の `<a href>` に縦中横注記が混入しリンクが機能しない | ✅ 対応済（Java 側） | #47 |
| 16 | 🟡 中 | CLI `-url` に zip / txtz / rar の URL を渡すと変換できない | ✅ 対応済 | #50 |
| 17 | 🟡 中 | タイトルページの外字画像が `<img src="null"/>` になる | ✅ 対応済 | #49 |

---

## 🔴 高

### 1. Web 小説ページ由来 href / img src によるパストラバーサル（任意ファイル書き込み）

**場所**

- `src/com/github/hmdev/util/CharUtils.java:196` — `escapeUrlToFile`（監査時の `:178` から PR #42 で移動）
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

**リリースノート記載事項**（PR #39 で確定。**PR #43 で `RELEASE_NOTES.md` の「未リリース（v1.3.7-jdk21 予定）」節に転記済み** — リリース時はその節をバージョン節へ書き換えるだけでよい）:

- **CLI の終了コードが変わります。** 変換に失敗した場合、これまで常に `0` を返していたのが `1` を返すようになります。
  narou.rb は終了コードで成否を判定するため、**これまで「成功」として取り込まれていた破損 EPUB が失敗として扱われる**ようになります。
  これは意図した変更です。誤検知（従来どおり成功すべき変換が失敗扱いになること）が起きないことは、
  画像デコード失敗・表紙取得失敗・不審アーカイブエントリなどが従来どおり局所的に握り潰されることを確認して担保しています。
- **変換に失敗した場合、出力途中の壊れた `.epub` は削除されます**（従来は残っていました）。変換をキャンセルした場合も同様です。
- `-h` / `--help` は終了コード `0`、`-i` の ini 不在・`-d` の出力先不在・入力ファイル不在は `1` を返します。

**訂正（PR #43、実測）**: 当初「引数なしは `1`」としていたが誤り。JAR の `Main-Class` は `AozoraEpub3Applet`（GUI）であり、
`java -jar AozoraEpub3.jar` を引数なしで実行すると **GUI が起動する**（終了コードは CLI の成否と無関係）。
`1` を返すのは `AozoraEpub3` クラスを直接起動した場合（`java -cp AozoraEpub3.jar AozoraEpub3`、narou.rb の呼び出し形式）。
なお `java -jar` 経由でも引数を渡した CLI 実行では終了コードは正しく伝播する（成功 `0` / 入力不在・ini 不在・出力先不在 `1` を実測確認）。

**narou.rb 側の実装確認（PR #43、narou 3.9.1 の実ソースで確認）**:

`lib/novelconverter.rb:177-200` が `java ... -cp AozoraEpub3.jar AozoraEpub3 ...` を実行し、`res[2].success?`（終了コード 0）で成否を判定する。
同 `:193-194` に「AozoraEpub3はエラーだとしてもexitコードは0なので、失敗した場合はjavaが実行できない場合と確定できる」というコメントがあり、
**非 0 をすべて「Java が動かなかった」と解釈**して `JavaがインストールされていないかAozoraEpub3実行時にエラーが発生しました` を表示する。

- **成功パスは不変**: 実測で終了コード `0` + stdout に `変換完了` を 1 回出力（narou.rb が見る両方の signal を維持）
- **失敗時も破壊的ではない**: narou.rb は非 0 を正しくハンドルして `:error` を返す。直前に `stream_io.puts res` で AozoraEpub3 の出力をそのまま表示するため真因は見える
- **旧実装の穴**: narou.rb には stdout をスキャンする二次的な安全網（`:214` の `error_list` と `:233` の `変換完了` 判定）もあるが、**stdout しか見ない**。
  旧実装の握り潰しは `logger.error` 経由で slf4j-simple の既定出力先である **stderr** に出ていたため、この安全網では捕まえられなかった（`LogAppender` は stdout）。今回塞いだのはこの穴
- **誤検知リスクは実質ゼロ**: `convertFile` が false を返すのは (a) zip 内に txt がない、(b) `epubWriter.write()` が例外、(c) キャンセル、の 3 つのみ。
  narou.rb は .txt を渡し CLI はキャンセルしないため、(b) の真の出力失敗しか該当しない
- **残る実害はメッセージの分かりにくさのみ**（narou.rb 側の問題でこちらからは直せない）。README / `docs/usage.md` / `docs/narou-setup.md` / `docs/index.md`（各 ja/en）に FAQ として記載済み

**#2 の記述の訂正（PR #43、実装確認）**: 上の「何が起きるか」で失敗要因として挙げた
「ディスクフル・テンプレートエラー・**画像破損**」のうち、**画像破損は失敗要因ではない**。
`src/com/github/hmdev/image/ImageUtils.java:392-395` が `writeImage()` 全体を `catch (Exception)` で包み、
`LogAppender.println("画像読み込みエラー: ...")` を出すだけで再スローしないため、
画像が壊れていても `Epub3Writer.write()` は失敗せず終了コードは `0` のまま（これは「誤検知が起きない」ことの担保そのものでもある）。
ユーザー向けドキュメントでは失敗要因を「ディスクの空き容量不足 / 出力先の書き込み権限」に限定した。

**`narou convert -v` は失敗時に効かない（PR #43、実ソース確認）**: narou.rb は終了コードが 0 以外だと
`novelconverter.rb:195-200` で `return :error` するため、verbose 出力ブロック（`:218-223`）に到達しない。
ただし `:197` の `stream_io.puts res` が `[stdout, stderr, Process::Status]`（`lib/helper.rb:393`）を全文表示するため、
**`-v` なしで真因を確認できる**。

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

### 4. `ImageInputStream` / `ImageOutputStream` 未クローズ（ImageIO ディスクキャッシュの一時ファイル滞留）

**場所**

- `src/com/github/hmdev/info/ImageInfo.java` — `getImageInfo` の `createImageInputStream`
- `src/com/github/hmdev/image/ImageUtils.java` — `writeImage` の `createImageOutputStream`（png / jpeg の 2 箇所）

**何が起きるか**

`ImageIO.createImageInputStream(is)` の戻り値 `iis` を `close()` していない。
ImageIO はデフォルトで `FileCacheImageInputStream`（temp ファイル）を作ることがあり、
大量画像を含む書籍の変換で一時ファイル / FD が GC 任せで滞留する。

**修正方針**

`try (ImageInputStream iis = ...)` にする。`iis.close()` は引数の `is` を閉じないため、呼び出し側のストリーム所有権に影響しない。

**追記（PR #40）**: 同根でより高頻度な漏れが `src/com/github/hmdev/image/ImageUtils.java:413` / `:417` にあった。
`imageWriter.setOutput(ImageIO.createImageOutputStream(zos))` も未クローズで、**画像 1 枚ごとに**
`FileCacheImageOutputStream` の temp ファイルが滞留していた。`FileCacheImageOutputStream.close()` も
引数の `zos` は閉じない仕様のため、同じく try-with-resources 化して対応した。

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

### 7. 0B-4c 監査漏れの空 catch（`意図的:` コメントなし）2 件 → ❌ 誤検出（対応不要）

- `src/AozoraEpub3Applet.java` — `catch (Exception e) {}`（D&D の transferData 取得）
- `src/com/github/hmdev/swing/JConfirmDialog.java` — `catch (MalformedURLException e1) {}`（アイコン読込）

**結論: 2 件とも誤検出。対応不要。**

いずれも**ブロックコメント内の dead code** で、grep ベースの監査がコメント内をヒットさせたもの。

- Applet 側は `/*class DropListener implements DropTargetListener ... }*/`（3110 行目付近から始まる）の内側
- JConfirmDialog 側は `/*jButtonFit = ... panel.add(jButtonFit);*/` の内側

実際に `/* 意図的: ... */` コメントを追記しようとすると、**内側の `*/` が外側のブロックコメントを途中で終端させてコンパイルエラーになる**（PR #41 で確認）。
0B-4c の規約漏れではないため、`grep 「意図的:」` での追跡対象からも外してよい。

**教訓**: 空 catch の監査を grep で行う場合、ブロックコメント内の dead code を除外すること。

### 8. `new FileReader(file)` のデフォルトエンコーディング依存

**場所**: `src/AozoraEpub3Applet.java:3391` — `readInternetShortCut`

Java 18+ ではデフォルトが UTF-8 固定になるが、Windows の `.url` ファイルは ANSI (MS932) の場合がある。
`URL=` 行が ASCII なら実害なしだが、非 ASCII を含む URL で文字化けの可能性。charset を明示する。

**対応（PR #41）**: `new FileReader(file, StandardCharsets.UTF_8)` で **UTF-8 を明示**した。
Java 18+ の既定と同じためバイト単位で挙動不変（回帰ゼロ）だが、
**MS932 で書かれた `.url` の非 ASCII を読む問題は解消していない**。
OS が生成する InternetShortcut の `URL=` 値は percent-encoding された ASCII のため実害はほぼないが、
手編集された MS932 ファイルへの対応が必要になったら別課題として扱う。

### 9. パスなし URL 入力で `StringIndexOutOfBoundsException`

**場所**: `src/com/github/hmdev/web/WebAozoraConverter.java` の 2 箇所
（`createWebAozoraConverter` と `convertToAozoraText`。監査時の `:481` からは移動している）

`urlString.indexOf('/', ...)` が -1 のまま `substring` に渡る。

**訂正（PR #41）**: 当初「末尾スラッシュ補正が失敗した場合のみ」「要確認」としていたが、**無条件に再現する**。
呼び出し順は `createWebAozoraConverter`（`AozoraEpub3.java` / `AozoraEpub3Applet.java` から）→ `convertToAozoraText` であり、
**末尾スラッシュ補正は後者の中にある**。したがって `https://example.com`（パスなし）入力は
ネットワーク状態と無関係に `createWebAozoraConverter` で即例外になり、
ユーザーには `エラーが発生しました : begin 0, end -1, length 19` という意味不明なメッセージが出る。
深刻度も 🟢 低 → 🟡 中 に訂正。

該当は 2 箇所（`createWebAozoraConverter` と `convertToAozoraText`）。前者は -1 チェック、
後者は補正できなかった場合にルート扱い（`/` 付与）にして、補正成功時と同じ結果に収束させる。

### 10. `getTextInputStream` の null 戻りで NPE

**場所**

- `src/AozoraEpub3.java:541` — `new InputStreamReader(null, encType)`
- `src/com/github/hmdev/io/ArchiveTextExtractor.java:61,72` — zip 内に txt がない場合等に null を返す

txt を含まない zip の変換で、ユーザー向けエラーが NPE メッセージになる（外側 catch があるためクラッシュはしない）。
null チェックして明示メッセージで return する。

**訂正（PR #41）**: 監査が挙げた `InputStreamReader(null, ...)` は**真因ではなかった**。
png 1 枚だけの zip / cbz を CLI 変換すると、実際には**その手前**で落ちる:

```
java.lang.NullPointerException: Cannot read field "insertTitleToc" because "bookInfo" is null
	at AozoraEpub3.run(AozoraEpub3.java:401)
```

`imageOnly` 時は `bookInfo` が null のままで `if (!bookInfo.insertTitleToc && ...)` に到達する。
また `getBookInfo` が null を返した場合も同じ経路で NPE になる。
PR #41 では以下の 3 点をまとめて対応した:

- `getTextInputStream` の null チェック（監査が挙げた箇所）
- `getBookInfo` が null を返した場合に明示メッセージ + `errorCount++` で次のファイルへ
- `imageOnly` 時に `bookInfo` が null のまま参照される箇所に null ガード

GUI 経路は先に BookInfo を生成するため元から影響なし。

### 11. ソース内コメントの文字化け (mojibake)

**場所**: `src/com/github/hmdev/web/WebAozoraConverter.java:847` / `:850`

`__NEXT_DATA__ 繝輔か繝ｼ繝ｫ繝舌ャ繧ｯ...` のように mojibake 化している。
同ファイルは編集時に注意が必要（Edit ツールが失敗する場合はバイトレベル編集）。

**訂正（PR #41）**: 「動作影響なし」ではなかった。2 行のうち片方は**ユーザー可視のログ文字列**で、
カクヨム等で `__NEXT_DATA__` フォールバックが発動すると GUI / CLI のログに文字化けが表示されていた。
復元内容は CP932 の round-trip decode で機械的に検証済み。`src/` 全体を再スキャンして残存 mojibake はゼロ。

### 12. Windows 予約デバイス名でキャッシュが毎回無効化される

**場所**: `src/com/github/hmdev/web/WebAozoraConverter.java:1056` 付近（章キャッシュの存在チェック。監査時の `:1024` からは PR #37〜#41 の一連の変更で移動している）

URL 由来のパスセグメントが Windows の予約デバイス名（`CON` / `NUL` / `PRN` / `AUX` / `COM1`〜`COM9` / `LPT1`〜`LPT9`）になると、
`Files.write(Path.of("dir/host.example.com/NUL"), ...)` は成功するのに `Files.exists` は false を返す。
その結果、当該話は毎回「キャッシュなし、再ダウンロードを試みます」を繰り返し、最終的に章が欠落する。

パストラバーサルではなく既存の不具合（#1 の PR スコープ外）。
`CharUtils.escapeUrlToFile` で予約デバイス名のセグメントをリネームする（末尾に `_` を付ける等）のが対処案。
ただし**既存キャッシュのファイル名が変わる**ため、影響範囲を評価してから実施すること。

**実測結果（PR #42、Windows 11 26200 + JDK NIO）**: 監査の記述は正しかったが、症状の出る範囲は想定より狭い。

| セグメント名 | `Files.write` | `Files.exists` | 実ファイル生成 |
|---|---|---|---|
| `NUL` | 成功 | **false** | されない（`Files.size` も `FileSystemException`） |
| `NUL.` / `NUL...` | 成功 | **false** | されない |
| `CON` / `PRN` / `AUX` / `COM0` / `COM1` / `COM¹` / `LPT1` / `CON.` / `COM1.` | 成功 | true | **される**（size 一致） |
| `NUL.txt` / `NUL.txt.` / `COM9.html` / `con.example.com` | 成功 | true | される |

つまりこの環境で #12 の症状（毎回再ダウンロード → 章欠落）を再現するのは **`NUL`（末尾ドット付きを含む）のみ**。
Windows は名前末尾の `.` と ` ` を無視するため、`NUL.` は `NUL` と同一視される。
ただし予約デバイス名の解決は Windows のバージョン・API 経路に依存し、古い Windows では `CON` 等が
デバイスに解決され得る（その場合キャッシュ読み込みの `Jsoup.parse` がコンソール入力待ちでハングし得るため、`NUL` より悪い）。
そのため防御的に予約デバイス名の全集合を対象にした。

**対応（PR #42）**: `CharUtils.escapeUrlToFile` に予約デバイス名セグメントの無害化を追加した。

- 対象: `CON` / `PRN` / `AUX` / `NUL` / `COM0`〜`COM9` / `LPT0`〜`LPT9` と上付き数字の別名（`COM¹` `COM²` `COM³` / `LPT¹` `LPT²` `LPT³`）。大文字小文字は区別しない（`Locale.ROOT` で正規化）。
  Microsoft の「Naming Files, Paths, and Namespaces」が列挙するのは `CON` / `PRN` / `AUX` / `NUL` / `COM1`〜`COM9` / `LPT1`〜`LPT9` と上付き数字別名で、
  **`COM0` / `LPT0` は列挙に含まれない**が、同ドキュメントの NT Namespaces 節に「`COM0` と `COM1` は `Serial0` / `Serial1` への symlink」とあるため防御的に含めた。
  `CONIN$` / `CONOUT$` / `CLOCK$` は列挙外（コンソール API / レガシー DOS 由来）のため対象外。
  Windows 11 実機ではこの 3 つも `COM0` / `LPT0` も実ファイルとして作成できることを確認済み（つまり `COM0` / `LPT0` は過剰リネームだが、該当する実 URL は存在しない）
- **末尾の `.` と ` ` を除いてから比較する**。Windows がこれらを無視するため `NUL.` も `NUL` と同じ症状になる（上表で実測）
- 無害化: セグメント末尾に `_` を付ける（既存の `..` → `__` が同長置換なのに対し、こちらは 1 文字付加）。
  `NUL._` / `NUL_` / `COM1 _` はいずれも実ファイルとして作成できることを実測確認済み
- **予約名そのもの（末尾 `.` / ` ` を除く）のみを対象とし、`NUL.txt` / `con.example.com` のような「予約名 + 拡張子」「予約名で始まるホスト名」は変更しない**。
  上表のとおりこれらは Windows 11 で実ファイルとして正常に作れるため、リネームすると**動作しているキャッシュを無効化するだけ**になる。
  特にホスト名は先頭セグメント（`escapeUrlToFile` の入力の第 1 セグメント）なので、変更するとサイト単位でキャッシュツリーごと移動してしまう。
  なお `CON` 等は Windows 11 では実ファイルを作れるがリネーム対象に含めている（古い Windows への防御）。
  「実測で壊れるもの + 古い Windows で壊れ得る予約名そのもの」は対象、「Windows 11 実測で拡張子付きファイルとして扱われるもの」は対象外、という基準。
  **残存リスク**: Microsoft のドキュメントは "Also avoid these names followed immediately by an extension; for example, NUL.txt and NUL.tar.gz are both equivalent to NUL." と明記しており、
  仕様上は「予約名 + 拡張子」も予約扱いである。Windows 11 実機ではそうならないことを確認した上で、
  上記のキャッシュ無効化リスクを避けるためにあえて対象外にしている。
  古い Windows や別の API 経路では `nul.html` のようなセグメントで #12 が再発し得る（そのような実 URL は現時点で存在しない）
- **影響範囲**: `escapeUrlToFile` の出力はローカルキャッシュパスだけでなく、
  `WebAozoraConverter.java:1659-1670` で組み立てた `imagePath` が同 `:1703` で `［＃挿絵（images/...）入る］` として青空注記に埋め込まれ、
  **生成 EPUB 内の画像ファイル名にもなる**。したがってリネームは EPUB 出力（→ `.NET` ポートの byte-identical 比較テスト）にも波及し得る。
  ただし対象は予約デバイス名と一致するセグメントのみで、対応 12 サイトの実 URL 形にも比較テストの 5 ケースにも該当がないため、実出力は不変
- **既存キャッシュへの影響**: リネーム対象になるのは、そもそも Windows で正しくキャッシュできていなかった名前だけなので、
  Windows では実質的な破棄は発生しない。非 Windows では該当セグメントを含む URL のキャッシュが 1 回だけ再取得になるが、
  プラットフォーム間でキャッシュパスを一致させるため OS 判定は入れずに無条件適用とした
- **本 PR のスコープ外（未対応）**: 姉妹関数 `CharUtils.replaceInvalidFileChars` は無害化していない。
  同関数の唯一の呼び出し元 `src/AozoraEpub3Applet.java:4190` は青空文庫 zip の直接ダウンロード経路で、
  (a) URL 末尾が `.zip` / `.txtz` / `.rar` の場合しか通らず、(b) 結果を `new File(urlPath).getName()` で最終セグメントだけ使うため、
  ファイル名は必ず拡張子付き（`NUL.zip` 等）になる。実測でこれは実ファイルとして作成でき、
  さらにこの経路には `exists()` によるキャッシュ判定が無いため #12 の症状（毎回再ダウンロード → 章欠落）は起きない。
  古い Windows で `NUL.zip` がデバイスに解決される場合はダウンロード内容が捨てられるが、その場合は後続の変換が明示的に失敗する
- テスト: `test/com/github/hmdev/util/CharUtilsReservedDeviceNameTest.java`。
  無害化の網羅に加え、**実 URL 相当の入力で出力が 1 文字も変わらないこと**、
  空文字列 / 連続スラッシュ / 先頭スラッシュで入力が壊れないこと、
  無害化後パスが実ファイルとして `write` → `exists` できること（Windows では無害化前の `NUL` / `NUL.` がここで落ちる）を検証

### 13. 制御文字・末尾空白のパスセグメントで `InvalidPathException`

**場所**: `src/com/github/hmdev/util/CharUtils.java` の `escapeUrlToFile` と、
`src/com/github/hmdev/web/WebAozoraConverter.java` の `safeResolve` 呼び出し全 6 箇所（#12 と同じ経路）

#12 の実測中に発見した別件。Windows の `Path` はセグメント末尾の空白を許さない。

```
Path.of("dir").resolve("bar ")  → java.nio.file.InvalidPathException: Trailing char < > at index 3: bar
```

`InvalidPathException` は `RuntimeException` なので、`safeResolve` の `catch (IOException)` では捕まらず上位に伝播する。
URL 由来のパスセグメントが空白で終わる場合（href に生の空白が含まれるサイト）に、章スキップではなく変換全体が中断し得る。

なお**末尾がドット**のセグメント（`foo.`）は Windows が黙って `foo` に切り詰めるため例外にはならないが、
`foo.` と `foo` が同じキャッシュファイルに衝突する。実害は小さい。
（予約名 + 末尾ドット `NUL.` は #12 と同じ症状になるが、こちらは PR #42 で対応済み。）

**実測結果（PR #44、Windows 11 26200 + JDK NIO）**: 例外になる条件は当初の想定より広く、また狭かった。

| セグメントの内容 | `Path.resolve` |
|---|---|
| **制御文字 (0x00-0x1F、TAB 含む) を含む** — 位置を問わない | **`InvalidPathException`** |
| **末尾が半角スペース** | **`InvalidPathException`** |
| 先頭・中間の半角スペース（`" foo"` / `"fo o"`） | OK（実ファイルも作成できる） |
| 末尾がドット（`"foo."`） | OK（Windows が `foo` に切り詰め） |
| 末尾が全角スペース（`"foo　"`） | OK |
| 末尾が「スペース + ドット」（`"foo ."`） | OK（最終文字がドットのため） |

つまり **(a) 制御文字はどこにあってもアウト、(b) 半角スペースは末尾だけアウト**。
「末尾空白」だけを見ていた当初の想定では制御文字を取りこぼす一方、
中間・先頭のスペースまで潰すと不要な変更になっていた。深刻度も 🟢 低 → 🟡 中 に訂正
（章 1 件のスキップで済むはずが**変換全体が中断**するため）。

**対応（PR #44）**: 2 層で修正した。

1. **`CharUtils.escapeUrlToFile` でサニタイズ（根本対処）**
   - 制御文字 (0x00-0x1F) を位置を問わず `_` に置換
   - セグメント末尾の半角スペースを `_` に置換（`" (?= *(?:/|$))"`）。連続空白はすべて置換され、空白のみのセグメントは `_` の並びになる
   - **予約デバイス名の判定（#12）より後**に実行する。`"COM1 "` は先に予約名として `"COM1 _"` になり、その時点で末尾スペースではなくなるため二重処理にならない
   - 末尾ドットは例外にならないため**対象外**とした（#12 の `NUL.txt` と同じ判断基準。変更すると動作しているキャッシュを無効化するだけになる）
2. **`WebAozoraConverter.safeResolve` で `InvalidPathException` → `IOException` に変換（多層防御）**
   - 呼び出し元は 6 箇所あり、扱いは 2 通りに分かれる。変換を `safeResolve` 側に 1 箇所入れるだけで、どちらも既存の扱いに載る

     | 呼び出し元 | `IOException` 時の扱い |
     |---|---|
     | `:946` / `:1058`（章キャッシュ）、`:1693`（挿絵） | **その 1 件だけスキップ**して次へ進む |
     | `:548` / `:552`（本文キャッシュと `dstPath` 検証）、`:639`（`safeDstFile` 経由の出力 txt） | 上位へ伝播し変換を終了（本文が作れない以上これが正しい） |

   - 1 で取りこぼす未知の不正パス（OS 依存の制約など）でも、`RuntimeException` のまま突き抜けて変換全体が中断することを防げる
   - なお `:477` / `:1692` の `Path.of(this.dstPath)` は `try` の外で評価されるため、`dstPath` 自体が不正な場合はこの変換の対象外。
     ただし `dstPath` は URL 由来ではなく `:548` の検証を先に通るため、実際には到達しにくい

**既存キャッシュへの影響**: サニタイズ対象になるのは、そもそも Windows では例外で書き込めていなかった名前だけなので、
Windows では実質的な破棄は発生しない。非 Windows では該当セグメントを含む URL のキャッシュが 1 回だけ再取得になるが、
プラットフォーム間でキャッシュパスを一致させるため OS 判定は入れずに無条件適用とした（#12 と同じ方針）。

**テスト**: `test/com/github/hmdev/util/CharUtilsInvalidPathCharsTest.java`（17 件）と
`test/com/github/hmdev/web/WebAozoraConverterSafeResolveTest.java` に 1 件追加。
上表の「OK」側が従来出力のまま変わらないことと、無害化後パスが実際に解決・書き込みできることを検証。
`safeResolve` のテストは非 Windows では `Assume` でスキップする。

**将来のリファクタ候補（今回は見送り）**: `escapeUrlToFile` には現在 4 種類のルールが積み上がっている
（`..` 無害化 = #1 / 予約デバイス名 = #12 / 制御文字・末尾スペース = #13）。
実装は「全文字列に対する regex 3 パス + セグメント split（`escapeReservedDeviceNames`）」の混成で、
**順序依存がコメント頼み**になっている。4 ルールとも本質はセグメント単位の処理なので、
「split → 各セグメントにルールを順に適用 → join」のパイプラインに寄せると順序依存が局所化し、将来のルール追加に強くなる。

ただし現状の順序の健全性は検証済みで、**後段ルールが前段ルールの入力を再生成するケースはない**ため今すぐの必要はない:

- `"NUL "` → 予約名判定で `"NUL _"` になり、末尾が `_` なので末尾スペース regex に不一致
- 制御文字 → `_` 置換は `..` や予約デバイス名を新たに生成しない
- `isWindowsReservedName` は末尾の `.` と ` ` しか strip しないため `_` 付きは再判定されない

### 14. 青空 zip 直接ダウンロード経路の `replaceInvalidFileChars` が `:` と制御文字を除去しない

**場所**: `src/com/github/hmdev/util/CharUtils.java` の `replaceInvalidFileChars` と、
唯一の呼び出し元 `src/AozoraEpub3Applet.java:4190-4203`

`replaceInvalidFileChars` は `[?*&|<>"\\]` を `_` にするだけで、制御文字 (0x00-0x1F) を除去しない。
Windows では制御文字を含む名前をファイル API が受け付けないため、**サニタイズできたはずの名前でダウンロードが失敗する**。

**訂正（PR #45、実測）**: 当初 #13 と同一クラス（`InvalidPathException` が `catch (IOException)` を
すり抜ける）と書いていたが**誤り**。Windows 11 + JDK 21 で実測した実際の経路は次のとおり。

| 呼び出し | 実測結果 |
|---|---|
| `:4194` `srcFile.getCanonicalPath()` | **`IOException`（チェック例外）** ← ここで先に落ちる |
| `:4195` `srcFile.getParentFile().toPath()` | OK（親は `dstPath` で URL 由来文字を含まない） |
| `:4203` `srcFile.toPath()` | `InvalidPathException` になるが**到達しない** |

さらに唯一の生きた呼び出し元 `AozoraEpub3Applet.java:4469` は `:4471` の `catch (Exception e)` 配下なので、
仮に `RuntimeException` が飛んでも外へは抜けない。
（`:3199` にも `convertWeb` の呼び出しがあるが、`:3110` から始まる `/*class DropListener ... }*/` の
**ブロックコメント内の死コード**。監査 #7 と同じ「ブロックコメント内が grep にヒットする」パターンなので注意。）

したがって **#13 のような「未チェック例外のすり抜け」は起きない**。実際の症状は
「不正な名前のダウンロードが `IOException` で失敗し、ユーザーには `エラーが発生しました` としか出ない」というもの。
本項は堅牢性・一貫性の改善（監査 #6 でこの関数に持たせたサニタイズ意図の完遂）に位置づけられる。深刻度 🟢 低のまま。

**適用範囲が #13 より狭い理由**:

- この経路は URL 末尾が `.zip` / `.txtz` / `.rar` の場合しか通らない。`:4187` の `ext` 判定は最後の `.` 以降を
  全部取るため、`foo.zip ` は `"zip "`、`foo.zip?x=1` は `"zip?x=1"` となり不一致になる。
  よって **URL は必ず拡張子で終わり、末尾スペースは構造上あり得ない**
- `new File(urlPath).getName()` で最終セグメントのみを使うため、途中セグメントの不正文字・`..`・予約デバイス名は影響しない
- 残るのは「最終セグメントに `:` または制御文字が含まれる URL」（例 `http://host/a:b.zip` / `http://host/foo<0x01>bar.zip`）

**対応（PR #45）**: `replaceInvalidFileChars` に制御文字 (0x00-0x1F) と **`:`** の置換を追加した。

`:` はレビュー（ゲート C）で発見した積み残し。`escapeUrlToFile` は `:` を `_` にしていたのに
この関数だけ素通りさせており、**2 つの関数で対策範囲が乖離**していた。
`:` は RFC 3986 上パスセグメント内で percent-encoding 不要の合法文字なので
`http://host/a:b.zip` は正規のリンクとして到達し、`a:b.zip` が制御文字と同じく
`getCanonicalPath()` で `IOException` になることを実測で確認した
（制御文字は URL 中では通常 `%01` 形式で到達し本経路はデコードしないため、**実際の遭遇確率は `:` の方が高い**）。

再発防止として、制御文字の正規表現を `CharUtils.CONTROL_CHARS` に共有定数化し、
`escapeUrlToFile` と `replaceInvalidFileChars` の双方から参照するようにした。
#13 と違い**末尾スペースの考慮は不要**（上記のとおり URL 末尾が拡張子で終わるため構造上発生しない）。

- 上記の「適用範囲が #13 より狭い理由」は `replaceInvalidFileChars` の javadoc にも書き、
  新しい呼び出し元が生まれたときに前提が崩れないようにした
- 0x20（半角スペース）と 0x7F（DEL）は Windows のパスとして有効なので置換しない。境界をテストで固定した
- テスト: `test/com/github/hmdev/util/CharUtilsReplaceInvalidFileCharsTest.java` に 4 件追加（既存 5 件 + 新規 4 件 = 9 件）
- ミューテーション確認: 制御文字置換を外すと 1 件、`:` を外すと 1 件が赤化

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

### 15. 出典 URL の `<a href>` に縦中横注記が混入しリンクが機能しない

**発見**: 2026-07-25、v1.3.7 リリース前 E2E（`docs/release-procedure.md` §2.1.1）で実際に生成した EPUB を見て発見。
**v1.3.6 でも同じ壊れ方をするため既存バグ**（今回の変更による回帰ではない）。

**症状**: URL から変換した EPUB の末尾にある出典リンクの `href` に、青空文庫の注記記法が混入する。

```html
<a href="https://www.aozora.gr.jp/cards/［＃縦中横］000035［＃縦中横終わり］/files/［＃縦中横］1567［＃縦中横終わり］_［＃縦中横］14913［＃縦中横終わり］.html">https://www.aozora.gr.jp/cards/<span class="tcy"><span>000035</span></span>/...</a>
```

**表示テキスト側は正しく `<span class="tcy">` に変換されている**のに `href` 属性だけが壊れており、**クリックしても飛べない**。

**真因**（コードを読んで確認済み）: `src/com/github/hmdev/web/AozoraTextFinalizer.java`

1. `enchantMidashi()`（`:640-`）が `［＃改ページ］` 直後の底本行を `［＃中見出し］` で包む
2. `convertNumToKanji()`（`:363-386`）で、**見出し分岐（`:369`）が URL ガード（`:379` の `line.contains("://")`）より先に評価される**ため、
   底本行は URL ガードに到達しない。URL 保護が設計されているのに素通りする
3. `convertNumToZenkakuLine()` は `［＃...］` 注記区間をスキップするが **`<...>` タグを考慮していない**ため、
   `convertNumsToZenkakuInSegment()`（`:448-466`）が href 内の 2 桁以上の数字を `［＃縦中横］` で包む

`src/com/github/hmdev/web/WebAozoraConverter.java:1182-1189` が生の `<a href="URL">` を書き出すのが引き金だが、
**壊しているのはファイナライザ**。後段の `AozoraEpub3Converter` は `checkTcyPrev` / `checkTcyNext` で
タグを読み飛ばしており**無実**（当初は converter の自動縦中横を疑ったが誤りだった）。

**修正方針**: `AozoraTextFinalizer` の 3 つのセグメント走査
（`convertNumToZenkakuLine` / `convertNumToKanjiLine` / `alphabetToZenkakuLine`）に、
**既存の `［＃...］` スキップと対称な形で `<...>` タグスキップを追加**する共通ヘルパを入れる。

- `［＃縦中横］` で囲む案は不成立（注記自体がスキップされるだけで、間のテキストは変換される）
- converter 側で抑止する案は的外れ（混入はその前段で発生済み）
- `<a>` は converter が公式サポートする記法なので、生 HTML の埋め込み自体をやめる案は過剰

**他の自動変換の状況**（調査済み）: `alphabetToZenkaku` は `://` スキップあり、
`spaceHyphenation` は全角スペースのみ対象、濁点フォントはかな限定で、いずれも実害なし。
`replace.txt` はユーザー定義の全文置換なので href も対象になるが仕様として容認。

**`.NET` ポートへの影響**: 修正すると EPUB 出力が変わるため `JavaComparisonTests` 5 件のうち複数が落ちる
（なろうの ncode は数字を含む）。**Java と .NET へ同一修正を同時に入れる必要がある**。

**対応（PR #47、Java 側）**: `AozoraTextFinalizer` に共通ヘルパ
`convertOutsideChukiAndTags(String, UnaryOperator<String>)` を追加し、
構造が同一だった 3 メソッドを置き換えた。`［＃...］` と `<...>` のうち先に現れた方を境界に読み飛ばす。

**タグ判定は `</?[a-zA-Z][^>]*>` に限定**している（レビューで 2 度修正した箇所）:

- 当初 `<` から次の `>` までを無条件にタグ扱いしていたが、**顔文字 `(>_<)` などが対で誤検出**され、
  間のテキストが変換されなくなる退行があった（Opus ゲートが検出）
- また「閉じ `>` が無い場合は残りを変換継続」としていたため、**裸の `<` の後ろにある注記の中身まで変換**されていた
  （Codex ゲートが検出）。例: `A<B ［＃ここから1字下げ］` の `1` が変換される
- 「`<` の直後が英字」かつ「閉じ `>` がある」ものだけをタグとみなすことで、両方が同時に解消した。
  中間テキストに現れるタグは `<a href="...">` と `</a>` のみなので実用上の取りこぼしもない

**真因（`:369` の見出し分岐が `:379` の URL ガードより先）は意図的に残している**。
順序を入れ替えると底本行が丸ごと変換対象外になり、**表示テキストの縦中横まで失われて見た目が変わる**ため。
タグ区間だけを保護する本修正なら、href は正常化しつつ表示は従来どおりになる（実測で確認済み）。

**検証**:

| 項目 | 修正前 | 修正後 |
|---|---|---|
| 生成 EPUB の `href` 内の注記 | 1 件 | **0 件** |
| 表示テキストの `class="tcy"` | 3 個 | **3 個（維持）** |

`test/AozoraTextFinalizerTest.java` に 7 件追加（`gradlew test` 全 273 件 PASS）。
`testHrefNotBrokenByTcy` が修正前に RED であること、
タグ判定から英字条件を外すミューテーションで `testEmoticonIsNotTreatedAsTag` が RED になることを確認済み。

> **テスト作成時の注意**: 通常行のテストで href に絶対 URL を使うと、
> `convertNumToKanji` の行レベル URL ガード（`line.contains("://")`）で行ごとスキップされ、
> **タグスキップを一切通らないトートロジーになる**。相対パス（`/a/12345/b.html`）を使うこと。

**本修正でカバーできない同種問題（`[jump:]` 由来アンカー）**: レビュー（ゲート C）で判明。

`WebAozoraConverter.java:2372` が `[jump:URL]` 記法から生成する `<a href="URL">URL</a>` は、
**同じ `printText` の中で**タグ非対応の変換を通過するため、ファイナライザに届く前に壊れる。

- `:2486` `convertNumbersToKanji`（`arabicToKanji` は PH 領域しかスキップしない）
- `:2493` `insertSeparateSpace`（半角 `?` の直後に全角アキを挿入するため、**クエリ付き URL を破壊**する）
- `:2494` `convertTatechuyoko` ほか

`enableNarouTag` 有効時のみ発現する。本修正は finalize 段の対策なので防げない。
底本行（`:1182-1189` で生成 → finalize 段で処理）とは経路が違う点に注意。
**別途 `printText` 側にも同様のタグ保護が必要**。

**`.NET` ポートへの移植（完了）**: `D:\git\aozoraepub3-dotnet` の
`src/AozoraEpub3.Core/Web/AozoraTextFinalizer.cs` に同等の修正を入れた（全 472 件 PASS）。

移植時に判明した事実:

- **`JavaComparisonTests` は落ちなかった**。同テストは `input.txt`（**青空テキスト**）を入力にしており、
  `AozoraTextFinalizer` は URL → txt の段で動くため**比較経路に含まれない**。
  フィクスチャの `input.txt` に `<a href>` 底本行が存在しないことも確認済み。
  「修正すると比較テストが落ちる」という当初の想定は誤りだった
- `.NET` には既に `TransformOutsideAnnotations(line, transform)` ヘルパがあったため、
  正規表現を `［＃[^］]*］|</?[a-zA-Z][^>]*>` に拡張するだけでタグ除外が入った。
  Java のような手書きループの移植は不要だった
- あわせて `DecimalPointRegex` の適用を transform の内側へ移した。
  従来は行全体に適用しており、`/v1.5/` のような URL を含む href を壊し得たため

### `.NET` ポートとの既存乖離（本修正で判明、未対応）

**1. `ConvertNumToKanji` の判定順** — 本修正で解消済み。

`.NET` は `ShouldSkipConversion`（`://` チェック）が見出し分岐より**先**にあり、
底本行を丸ごとスキップしていた。Java は見出し分岐が先。
順序を Java に合わせた（`AozoraTextFinalizer.cs` の該当箇所にコメントあり）。

**2. 見出し行の数字変換ルール — 未対応の乖離**

| 桁数 | Java | `.NET` |
|---|---|---|
| 1 桁 | 全角 | 全角 |
| 2 桁 | 縦中横 | 縦中横 |
| **3 桁以上** | **縦中横** | **全角** |

Java の `convertNumsToZenkakuInSegment` は `digits.length() >= 2` で縦中横、
`.NET` の該当箇所は `digits.Length == 2` のみ縦中横。
**どちらが narou.rb 準拠として正しいか未確認**。比較テストがこの経路を通らないため検出されていなかった。
別途どちらかに寄せる必要がある。

### `[jump:]` 経路の残作業

上記の同種問題（`printText` 内でタグ非対応の変換が走る）は Java / `.NET` とも未対応。

### 要確認（未追跡）

- ~~`src/com/github/hmdev/web/WebAozoraConverter.java:2295` — `[jump:URL]` の `<a href>` 生成で URL が非エスケープ。後段の変換でのエスケープ有無を追跡していない~~
  → **後段で壊れることを E2E で実証した**。監査 #15 として起票済み（下記）

---

## 16. CLI `-url` に zip / txtz / rar の URL を渡すと変換できない（GUI 経路との乖離） — ✅ 修正済み

**発見**: v1.3.7 リリース前 E2E（2026-07-25、2 回目）。**既存仕様であり本リリースの回帰ではない**。

**症状**:

```
$ java -jar AozoraEpub3.jar -url "https://www.aozora.gr.jp/cards/000035/files/1567_ruby_4948.zip" -d out
https://www.aozora.gr.jp/.../1567_ruby_4948.zip を読み込みます
HTTP Response Code: 200
 : List Loaded.
SERIES/TITLE : タイトルがありません
https://www.aozora.gr.jp/.../1567_ruby_4948.zip は変換できませんでした   ← exit 1
```

**原因**: 拡張子が `.zip` / `.txtz` / `.rar` の URL を「アーカイブを直接ダウンロードして変換する」経路に
振り分ける分岐が **GUI 側にしかない**。

- GUI（DnD / URL 入力）: `AozoraEpub3Applet.java:3188` と `:4186` の 2 箇所に分岐がある
- CLI: `AozoraEpub3.java:265-334` の `-url` 処理は**無条件に `WebAozoraConverter`** に渡すため、
  zip をスクレイピング対象の HTML として扱おうとして失敗する

**影響**: 青空文庫のテキスト zip（`NNNN_ruby_NNNN.zip`）を CLI から直接指定できない。
回避策は zip を手元にダウンロードして入力ファイルとして渡すこと（この経路は正常に動作する）。

**対応**: 拡張子判定とダウンロード処理を `com.github.hmdev.util.ArchiveUrlUtils` に切り出し、
GUI（`AozoraEpub3Applet.convertWeb`）と CLI（`AozoraEpub3.run` の `-url` ループ）の両方から呼ぶようにした。

- `ArchiveUrlUtils.isArchiveUrl(url)` — GUI にあった判定（`lastIndexOf('.')` 以降を小文字化して
  `zip` / `txtz` / `rar` と比較）をそのまま移設。クエリ文字列付き URL の扱いを変えると
  GUI の既存挙動が変わるため意図的に据え置いている
- `ArchiveUrlUtils.downloadArchive(url, dstPath)` — 出力先へのダウンロード。
  ファイル名は `CharUtils.replaceInvalidFileChars`（監査 #14）でサニタイズし、
  取得は `NetUtils.openStream`（監査 #5 のタイムアウト付き）で行う。
  失敗時は途中まで書かれたファイルを削除する。同名ファイルは上書き（GUI と同じ挙動）
- CLI はダウンロードしたファイルを変換対象リストに積み、**ローカルの zip / rar 入力と
  まったく同じ変換経路**（`targetFileNames` のループ）で処理する。
  ダウンロード失敗は `errorCount` に加算され exit 1、成功時は従来どおり exit 0 と
  stdout の「変換完了」（narou.rb 互換シグナル）を維持する
- `-d` 未指定時のダウンロード先はカレントディレクトリ

実測（修正後）:

```
$ java -jar AozoraEpub3.jar -url "https://www.aozora.gr.jp/cards/000035/files/1567_ruby_4948.zip" -d out
出力先にダウンロードします : ...\out\1567_ruby_4948.zip
変換開始 : out\1567_ruby_4948.zip
変換完了[0.1s] : ...\out\[太宰治] 走れメロス.epub    ← exit 0
```

テスト: `test/com/github/hmdev/util/ArchiveUrlUtilsTest.java`（拡張子判定・保存先ファイル名・
`file:` URL でのダウンロード／上書き／失敗時のクリーンアップ）と
`test/AozoraEpub3ArchiveUrlTest.java`（CLI 経路の振り分け。ネットワークを使う E2E は
`-DarchiveUrlE2E=true` で opt-in）。

> **GUI 側の既知の挙動（本修正では変更なし）**: GUI の zip URL 経路はダウンロードのみを行い、
> 変換までは実行しない（ダウンロード後に `continue` している）。共通化にあたってもこの挙動は維持した。
> 変換まで行うべきかは別途判断が必要。

> **注**: `docs/release-procedure.md` §2.1.1 の「青空文庫 URL 1 件（`_ruby_` を含む zip パターンを推奨）」は
> **card ページ URL または HTML 単話ページ**を指す。zip URL の直接指定は v1.4.0 以降 CLI でも利用できる。

---

## 17. タイトルページの外字画像が `<img src="null"/>` になり epubcheck が ERROR になる — ✅ 修正済み

**発見**: v1.3.7 リリース前の `generateLocalSamples` + epubcheck（2026-07-25）。
**v1.3.6 の配布 JAR でも同じ EPUB が生成されることを実測済み。既存バグであり本リリースの回帰ではない。**

**症状**: `test_data/test_title.txt` から生成した EPUB で

```
ERROR(RSC-007): OPS/xhtml/title.xhtml(59,95):
  参照されているリソース "OPS/xhtml/null" がEPUB内に見つかりません.
```

該当箇所（`title.xhtml`）:

```html
<div class="orgtitle">…タイトル<span class="gaiji"><img src="null"/></span>&amp;&lt;&gt;…</div>
```

**再現条件**: **表題行に画像外字注記があり、かつ参照先の画像ファイルが存在しない**とき。
`test_title.txt` は `fig46187_03.png` を参照しているが `test_data/` に実体がなく、変換時に
`[WARN] 画像ファイルなし (6) : fig46187_03.png` が出る。本文側は画像なしとして処理されるが、
**タイトルページ側は `chuki_tag.txt:713` の `外字画像 <span class="gaiji"><img src="%s"/></span>` に
`null` が埋め込まれたまま出力される**。

**真因**: `AozoraEpub3Converter.convertTitleLineToEpub3`（タイトル行用の変換）に
`writer.getImageFilePath()` の **null チェックが無かった**。
本文側 `convertTextLineToEpub3`（同ファイル :2150-2155）には `if (imgFileName != null)` があり、
画像が解決できないときは何も出力しない。タイトル側だけがガードを持たず、`String.format` に
`null` が渡って文字列 `"null"` が `src` に埋まっていた。

**CI が緑だった理由**（実測で確認。当初「`build/libs/` に ini が無いため」と書いたが誤り）:

1. CI の最初の生成（`ci.yml:162`）はリポジトリ直下の `AozoraEpub3.ini`（`TitlePage=2`）を使うため、
   **タイトルページ入りの `build/epub_out/test_title.epub` を正しく生成していた**
   （CI ログにも title ページ変換由来の `[WARN] 画像ファイルなし (0)` が出ている）
2. しかし後段の「INI マップ確認」ステップ（`ci.yml:190`）が
   `-i build/epub_out/sample.ini -of -d build/epub_out test_data/test_title.txt` を実行し、
   **同名ファイルを上書き**する。`sample.ini` は CSS 検証用の 6 キーしか持たずタイトルページが出ない
3. その結果、epubcheck が検証していたのは**タイトルページの無い方の EPUB** だった

epubcheck 5.2.0（CI 版）でも 5.3.0（ローカル）でも、タイトルページ入りの EPUB は
同じ `RSC-007` を報告することを実測済み。**バージョン差ではなく検証対象の取り違え**。

**影響度**: 🟢 低〜🟡 中。入力側に「表題行の画像外字 + 画像ファイル欠落」が揃った場合のみ。
ただし成果物は epubcheck ERROR になり、EPUB 3.3 非準拠。

**修正内容**（v1.3.7）:

1. `convertTitleLineToEpub3` に本文側と同じ null ガードを追加し、画像が解決できない外字は
   `<img>` を出力しないようにした
2. ユニットテスト 2 件を追加（`AozoraEpub3ConverterTest`）— 画像なしで `<img>` が出ないこと、
   画像ありでは従来どおり出力されること
3. CI に「local samples に対する epubcheck」ステップを追加。`generateLocalSamples` は
   リポジトリ直下の `AozoraEpub3.ini` を使うためタイトルページ経路を通る（上書き問題の影響も受けない）

---

## 依存ライブラリ更新の判断（2026-07-25）

### junrar 7.5.10 → 8.0.0 — **見送り（時期尚早）**

**API 面の移行コストはゼロ**と確認済み。8.0.0 の破壊的変更は 3 点だけで、いずれも本プロジェクトは未使用:

- `UnsupportedRarV5Exception` の削除
- `FileHeader#getFileNameString` / `getFileNameW` の削除
- `BaseBlock#getHeaderSize()` の削除

`src/` が使うのは `Archive`（`new Archive(File)` / `getFileHeaders()` / `nextFileHeader()` / `extractFile()`）、
`FileHeader`（`isDirectory()` / `getFileName()`）、`RarException` のみ（import しているのは 6 ファイル）。

**実利も明確**: RAR5 は WinRAR の既定形式で 7.5.10 では読めない。CBR 入力で普通に遭遇する。
`.NET` ポートの byte-identical 比較テスト 5 件は txt / zip / Web 経路のみなので影響なし。

**それでも見送る理由**: 8.0.0 は **2026-07-23 公開**（Maven Central の `maven-metadata.xml` の
`lastUpdated=20260723071908` で確認）で、**RAR5 デコーダの新規実装を含むメジャー版**。
公開直後で実地の実績情報がなく、8.0.1 も出ていない。

**再検討の条件**: 8.0.1 の公開、または公開から 1〜2 か月経って重大な issue が上がっていないこと。

### Gradle wrapper 9.2.1 → 9.6.1 — **v1.3.7 リリース後に実施**

9.x 系内のマイナー更新で破壊的変更なし。ただし 9.6 の目玉（Configuration Cache のヒット率改善）は
本プロジェクトが config cache 未使用のため**実利がほぼない**。急ぐ理由はないが、
放置すると Gradle 10 移行時の差分が肥大するのでパッチ追従として実施する。

進め方: wrapper 更新 + バージョン文字列 5 箇所（`CLAUDE.md` / `AGENTS.md` / `README.md` /
`docs/release-procedure.md` / `docs/modernization-plan.md`）の更新のみの単独 PR。
検証は (a) CI matrix（JDK 21 / 25 / 26）で `gradlew test`、
(b) **9.2.1 と 9.6.1 それぞれで `gradlew dist` を実行し `unzip -l` / `tar -tzf` のファイルリストを diff**
（過去の include 漏れ事故対策）。**リリース直前には入れない**。

### 先にやるべき準備: RAR 経路のテスト整備

`test_data/` に **RAR / CBR のフィクスチャが 1 件も無く、RAR 経路は完全に無テスト**（2026-07-25 時点で確認）。
このまま junrar を上げると回帰に気づけない。junrar 更新の前に、
RAR4 フィクスチャ + 抽出テストを追加して現行動作のベースラインを確立すること。
これは junrar のバージョン判断と独立して価値がある。

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
| E | #12 | キャッシュファイル名が変わるため単独 PR にして影響を切り分ける |
