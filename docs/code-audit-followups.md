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
| 13 | 🟢 低 | 末尾が空白のパスセグメントで `InvalidPathException` | 未着手 | - |

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

### 13. 末尾が空白のパスセグメントで `InvalidPathException`

**場所**: `src/com/github/hmdev/web/WebAozoraConverter.java` の章キャッシュ / 挿絵パス生成（#12 と同じ経路）

#12 の実測中に発見した別件。Windows の `Path` はセグメント末尾の空白を許さない。

```
Path.of("dir").resolve("bar ")  → java.nio.file.InvalidPathException: Trailing char < > at index 3: bar
```

`InvalidPathException` は `RuntimeException` なので、`safeResolve` の `catch (IOException)` では捕まらず上位に伝播する。
URL 由来のパスセグメントが空白で終わる場合（href に生の空白が含まれるサイト）に、章スキップではなく変換全体が中断し得る。

なお**末尾がドット**のセグメント（`foo.`）は Windows が黙って `foo` に切り詰めるため例外にはならないが、
`foo.` と `foo` が同じキャッシュファイルに衝突する。実害は小さい。
（予約名 + 末尾ドット `NUL.` は #12 と同じ症状になるが、こちらは PR #42 で対応済み。）

再現性の確認と、`escapeUrlToFile` 側でのトリム（`..` / 予約名と同じ層で処理）が対処案。

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
| E | #12 | キャッシュファイル名が変わるため単独 PR にして影響を切り分ける |
