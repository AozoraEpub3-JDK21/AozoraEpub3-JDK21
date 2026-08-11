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

## 残件 1: FC2 小説の対応が現行サイトで機能しない — 未対応

**症状**: 目次ページは HTTP 200 で取得できるが
「SERIES/TITLE : タイトルがありません」で変換不能。

**原因**: FC2 小説がサイト構造を全面変更しており、`web/novel.fc2.com/extract.txt` の
セレクタが 1 つも現行 HTML に存在しない (2026-08-11 実測):
- `TITLE .sh_heading_main_a` → 消滅。現行はタイトルが `og:title` メタと本文中の別要素
- `CONTENT_ARTICLE .novel_maincontent .novel_body` → 消滅。現行は `novel_read` /
  `novel_page` / `novel_first` 等の新クラス体系
- 2026-08-09 のハーメルン全面刷新 (#72 で修正) と同型の作業が必要

**修正方針**: 現行 HTML を採取して extract.txt を書き直す (TITLE / AUTHOR / HREF /
SUB_UPDATE / CONTENT_ARTICLE / PAGE_URL)。FC2 は「目次 = mode=tc / 本文 = ページ分割」
という他サイトと違う構造なので、PAGE_URL のページネーション確認が必須。
着手時はハーメルン修正 (PR #72) の進め方を参照。

## 残件 2: 消滅 3 サイトの extract.txt の扱い — 未決

dnovels.net / mai-net.net / newvel.jp は DNS ごと消滅しており検証不能。
選択肢: (a) extract.txt を残置 (害はないが検証不能なまま配布)、
(b) 削除して README の記載からも外す。README:223 の対応サイト明記分
(なろう/R18/カクヨム/ハーメルン/暁/novelist.jp/FC2) には含まれていないため急ぎではない。
