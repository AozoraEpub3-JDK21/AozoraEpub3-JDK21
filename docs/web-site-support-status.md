# Web 小説サイト対応状況 (実変換 dogfood の記録)

`web/` 配下に extract.txt を持つ全 12 サイトの実変換確認の記録。
サイト側の HTML 変更はユニットテストでは検出できないため、リリース前と
Web 変換まわりの変更時にここを更新しながら回す
(`memory/feedback_dogfood_real_sites.md` 参照)。

## 2026-10-08 全サイト dogfood (CLI、master 3bf4533 の jar、`web/` はノクターンを直した枝)

コマンド: `java -jar AozoraEpub3.jar -url "<URL>" -d out -cache .cache`（作品ごとに空のキャッシュ）。
**08-11 の確認は「EPUB ができたか」までだったので、ノクターンの本文が空でも ✅ になった。** 今回は EPUB を展開して、本文の字数・空に近いページ・見出しの数・ログのエラーまで数えた。

| サイト | 作品 | 結果 | xhtml | 本文の字数 | 大見出し/中見出し | 気づいたこと |
|---|---|---|---|---|---|---|
| なろう | `n9623lp`（章なし 151 話） | ✅ | 153 | 519,742 | 0/152 | |
| なろう | `n3353hv`（章 2・17 話） | ⚠️ | 19 | 83,376 | **0**/18 | 章題が取れない（各話のページに `.p-eplist__chapter-title` が無い） |
| ノクターン | `n0037mn`・`n7014fx` | ✅（この枝） | 10・4 | 50,215・13,418 | 0/9・0/3 | master の規則では本文 0 字（上の節） |
| カクヨム | `822139840468926025`（149 話） | ✅ | 151 | 409,460 | 0/150 | |
| カクヨム | `16818093073762858076`（章 2・17 話） | ⚠️ | 20 | 42,238 | 2/19 | 章の中扉の後に改ページが無く、第1話が左右中央の節に入る（3,043 字） |
| ハーメルン | `402358`・`422019` | ❌ | — | — | — | 一覧が HTTP 403。Cloudflare のチャレンジ（`cf-mitigated: challenge`）で、トップページも 403。この台の回線でだけか未確認 |
| 青空文庫 | `1567_14913.html`・`1567_ruby_4948.zip` | ✅ | 3・2 | 10,176・10,314 | | |
| 暁 | `novel_id~8654`（57 話） | ✅ | 59 | 97,963 | 0/58 | |
| novelist.jp | `388.html`（340 ページ） | ✅ | 342 | 128,132 | 0/18 | |
| 2.novelist.jp | `6027.html`（373 ページ） | ✅ | 375 | 326,053 | 0/204 | 20 字未満のページ 1 枚は作品の結び（「ありがとうございました！ 完」） |
| FC2 小説 | `nid=600`（443 ページ） | ✅ | 445 | 515,337 | 0/336 | 表紙の画像はサイトの側でエラーページへ転送され 404（作品の表紙が無い） |
| FC2 小説 | `nid=218411`（2 ページ） | ⚠️ | 4 | 565 | 0/3 | 本文は全部取れる（短い作品）。挿絵 2 枚が `https:/nimg/...`（スラッシュ 1 つ）になって取れない |

消滅扱いのサイト: `www.newvel.jp` は今も DNS を引けない。`www.dnovels.net` は DNS が引けるようになり http は 403。`www.mai-net.net` は http が 200 を返す（https は証明書のエラー）。3 つとも DEFUNCT のまま（変換はしていない）。

## 2026-08-11 全サイト dogfood (CLI、v1.5.1 実装後の master)

コマンド: `java -jar build/libs/AozoraEpub3.jar -of -d <出力先> -url "<URL>"`

| サイト | 検証 URL | 結果 |
|---|---|---|
| 小説家になろう (ncode.syosetu.com) | `n9623lp` ほか | ✅ (2026-08-11 リリース後 dogfood) |
| カクヨム (kakuyomu.jp) | `works/822139840468926025` | ✅ (同上) |
| ハーメルン (novel.syosetu.org) | 章あり `402358` / 章なし `422019` | ✅ (同上 + HamelnE2ETest) |
| 青空文庫 (www.aozora.gr.jp) | `cards/000035/files/1567_14913.html` | ✅ (表題二重は #80 で修正) |
| **なろう R18 (novel18.syosetu.com)** | `n0037mn` (ノクターン) | ✅ EPUB 生成・タイトル/mimetype 正常。COOKIE over18=yes で年齢認証も通過。**2026-10-08 訂正: 本文は空だった**（下記「2026-10-08 ノクターンの本文」） |
| **暁 (www.akatsuki-novels.com)** | `stories/index/novel_id~8654` | ✅ EPUB 178KB 生成 |
| **novelist.jp** | `388.html` (WHITE BOOK) | ✅ EPUB 471KB 生成 |
| **2.novelist.jp (二次創作)** | `6027.html` (ゆらのと、373 ページ) | ✅ EPUB 687KB 生成。PAGE_URL のページネーションも 373 ページ完走 |
| **FC2小説 (novel.fc2.com)** | `novel.php?mode=tc&nid=600` | ❌→✅ **v1.5.1 で修正済み** (残件 1 参照。挿絵のルート相対 src は既知問題として残る) |
| www.dnovels.net | — | ⚠️ **サイト消滅** (DNS 解決不可) |
| www.mai-net.net | — | ⚠️ **サイト消滅** (DNS 解決不可) |
| www.newvel.jp | — | ⚠️ **サイト消滅** (DNS 解決不可) |

補足: なろう R18 の出力ファイル名が超長タイトルでフルパス 228 文字になった。
Windows の 260 文字制限までは余裕があったが、深い出力先に変換すると超え得る
(Git Bash の `ls` は表示に失敗した)。出力ファイル名の長さ上限は将来検討。

## 2026-10-08 ノクターンの本文 — ✅ 修正

**症状**: ノクターン（`novel18.syosetu.com`）の Web 変換で、各話に「CONTENT_ARTICLE : 本文が取得できません」と出て、本文の無い EPUB ができる。変換は「変換完了」・終了コード 0 になる。
上の 08-11 の確認は「EPUB ができた」までしか見ておらず、本文の有無を見ていなかった。

**原因**: ノクターンもなろう本体と同じ 2024 年の作り変え（`p-eplist__*`・`p-novel__*`）になっていたが、`web/novel18.syosetu.com/extract.txt` は旧クラス名（`#novel_honbun`・`.novel_subtitle`・`.long_update` など）のままだった。

**対応**: `web/novel18.syosetu.com/extract.txt` を、なろう本体（`web/ncode.syosetu.com/extract.txt`）と、年齢確認の `COOKIE over18=yes` の行を除いて同じ内容にした（旧クラス名は後ろに残す）。
なろう本体の規則を直したときに写し忘れたのが原因なので、ファイルの頭に「本体を直したらここも直す」と書いた。

**確認**（mac、master の jar に枝の `web/` を当てて変換）: `n7014fx`（連載 2 話）は本文 0 字 → 13,418 字、`n0037mn`（連載 8 話）は 50,215 字、短編 `n0271mw` も本文が出る。
話の題（中見出し）・前書き・あとがきもそれぞれの位置に出る。なろう本体の規則は変えていない。

**残したこと**:
- 章題が取れない（なろう本体も同じ。各話のページに `.p-eplist__chapter-title` が無い）。章の中扉の改ページの直しの後に、なろう本体と一緒に直す
- 短編では作品名が中見出しとしてもう一度出る（なろう本体も同じ。narou.rb も短編の話の題に作品名を使う）。
  話の題を外すと、仕上げの処理（`AozoraTextFinalizer.enchantMidashi`、改ページ直後の行を見出しにする）が本文の最初の段落を見出しにするので、外さない
- 作者名にリンクが無い作品では、なろう API が使えないとき作者が取れない（なろう本体も同じ。`.p-novel__author:0` を足すと「作者：」が付いたまま入る）
- 本文が全話で空でも「変換完了」・終了コード 0 になる作り

## 残件 1: FC2 小説の対応が現行サイトで機能しない — ✅ 修正済み (2026-08-11、v1.5.1 向け)

**症状**: 目次ページは HTTP 200 で取得できるが
「SERIES/TITLE : タイトルがありません」で変換不能。

**原因 (精査後の訂正)**: 当初「セレクタ全滅」と記録したが誤りで、死んでいたのは
**TITLE の `.sh_heading_main_a` の 1 つだけ**だった (TITLE 取得失敗で変換が最初に
中断するため全滅に見えた)。`.username` / `.novel_comment` / `.novel_img` /
`.noveldescription` / `.novel_first` / `.novel_maincontent` 系のセレクタは
2026-08-11 時点の HTML でも健在。

**対応**: `TITLE .default_page_title:0,.sh_heading_main_a:0` に変更 (旧セレクタは
キャッシュ済み HTML 用に候補として残置)。短編 `nid=218006` (42 xhtml) で実変換成功、
mimetype / メタデータ正常を確認。

**派生の既知問題 (未対応)**: FC2 の挿絵は `src="/nimg/..."` の**ルート相対 URL** で、
converter が解決できず「画像ファイルなし images/__/nimg/...」で取り込まれない
(画像 URL 自体は HTTP 200 で生きている)。ルート相対 src の解決は全サイトに波及する
コード修正のため v1.5.1 では見送り。修正時は `printImage` / 画像 URL 解決経路を調査のこと。

## 残件 2: 消滅 3 サイトの extract.txt の扱い — ✅ DEFUNCT マーカーで対応 (2026-08-11、v1.5.1 向け)

ユーザー要件「URL を貼った人に『もうないよ』と伝えたい」に基づき、削除ではなく
**extract.txt に `DEFUNCT	<説明文>` マーカーを追加**する方式を実装:

- `ExtractInfo.ExtractId` に `DEFUNCT` を追加。値はセレクタではなく利用者向け説明文
  (**タブ・カンマ・コロンを含めないこと** — extract.txt のパースがこれらを区切りに使う)
- `WebAozoraConverter#convertToAozoraText` の先頭 (URL 補正の HTTP アクセスより前) で
  チェックし、「このサイトはサービスを終了しているため変換できません : <説明文>」を
  表示して変換中断 (exit 1)。ネットワークに出ないため消滅済みドメインでも即応答
- dnovels.net / mai-net.net / newvel.jp の 3 サイトに DEFUNCT を定義
- テスト: `test/WebAozoraConverterDefunctTest.java` (3 サイトの中断 + 稼働中 9 サイトに
  DEFUNCT が誤定義されていないことの網羅チェック)

**`.NET` ポートへの移植残**: FC2 の extract.txt 修正 (web/ のコピーを持つ場合) と
DEFUNCT マーカーの解釈 (`WebAozoraConverter.cs` の ExtractId 相当 + 変換前チェック) は
`aozoraepub3-dotnet` 側に未移植。次回ポートバック時に対応
(`D:\git\aozoraepub3-dotnet\docs\java-port-back-guide.md` の運用に従う)。
