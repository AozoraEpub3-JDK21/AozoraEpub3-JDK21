# Web 小説サイト対応状況 (実変換 dogfood の記録)

`web/` 配下に extract.txt を持つ全 12 サイトの実変換確認の記録。
サイト側の HTML 変更はユニットテストでは検出できないため、リリース前と
Web 変換まわりの変更時にここを更新しながら回す
(`memory/feedback_dogfood_real_sites.md` 参照)。

## 2026-08-11 全サイト dogfood (CLI、v1.5.1 実装後の master)

コマンド: `java -jar build/libs/AozoraEpub3.jar -of -d <出力先> -url "<URL>"`

| サイト | 検証 URL | 結果 |
|---|---|---|
| 小説家になろう (ncode.syosetu.com) | `n9623lp` ほか | ✅ (2026-08-11 リリース後 dogfood) |
| カクヨム (kakuyomu.jp) | `works/822139840468926025` | ✅ (同上) |
| ハーメルン (novel.syosetu.org) | 章あり `402358` / 章なし `422019` | ✅ (同上 + HamelnE2ETest) |
| 青空文庫 (www.aozora.gr.jp) | `cards/000035/files/1567_14913.html` | ✅ (表題二重は #80 で修正) |
| **なろう R18 (novel18.syosetu.com)** | `n0037mn` (ノクターン) | ✅ EPUB 生成・タイトル/mimetype 正常。COOKIE over18=yes で年齢認証も通過 |
| **暁 (www.akatsuki-novels.com)** | `stories/index/novel_id~8654` | ✅ EPUB 178KB 生成 |
| **novelist.jp** | `388.html` (WHITE BOOK) | ✅ EPUB 471KB 生成 |
| **2.novelist.jp (二次創作)** | `6027.html` (ゆらのと、373 ページ) | ✅ EPUB 687KB 生成。PAGE_URL のページネーションも 373 ページ完走 |
| **FC2小説 (novel.fc2.com)** | `novel.php?mode=tc&nid=600` | ❌ **変換不能「SERIES/TITLE : タイトルがありません」** (下記) |
| www.dnovels.net | — | ⚠️ **サイト消滅** (DNS 解決不可) |
| www.mai-net.net | — | ⚠️ **サイト消滅** (DNS 解決不可) |
| www.newvel.jp | — | ⚠️ **サイト消滅** (DNS 解決不可) |

補足: なろう R18 の出力ファイル名が超長タイトルでフルパス 228 文字になった。
Windows の 260 文字制限までは余裕があったが、深い出力先に変換すると超え得る
(Git Bash の `ls` は表示に失敗した)。出力ファイル名の長さ上限は将来検討。

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
