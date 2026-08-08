# 中見出し変換で XHTML が整形式でなくなるバグ (2026-08-08 発見)

## 概要

特定の青空文庫記法を含む見出し行を変換すると、**`<div class="chapN">` が閉じられないまま**
本文の `<p>` が続き、生成された XHTML が整形式でなくなる。
EPUB 3 の XHTML は整形式であることが必須であり、この EPUB は
`epubcheck` を通らず、実機のリーダーでも表示が壊れる。

**プレビュー機能 (`docs/epub-preview-plan.md`) の動作確認中に発見した既存バグ。**
プレビューは XHTML を `application/xhtml+xml` で配信するため、
ブラウザが XML として厳密にパースし、次のエラーを表示して発覚した。

```
This page contains the following errors:
error on line 88 at column 8: Opening and ending tag mismatch: div line 77 and body
Below is a rendering of the page up to the first error.
```

## 再現手順

```bash
java -jar build/libs/AozoraEpub3.jar -of -d <出力先> test_data/test_chapter.txt
# 出力された EPUB の OPS/xhtml/0005.xhtml が整形式でない
```

整形式かどうかは XML パーサに通せば判定できる (PowerShell の例):

```powershell
[xml](Get-Content OPS\xhtml\0005.xhtml -Raw -Encoding UTF8)
# → The 'div' start tag on line 77 position 4 does not match the end tag of 'body'.
```

## 壊れる入力と壊れない入力

`test_data/test_chapter.txt` の該当行:

| 行 | 入力 | 結果 |
|---|---|---|
| 316 | `［＃中見出し］○○○○○※［＃始め二重山括弧］中見出し※［＃終わり二重山括弧］○○○○○［＃中見出し終わり］` | 正常 |
| 319 | `［＃中見出し］○○○○○※［＃米印］※［＃米印］※［＃米印］※［＃始め二重山括弧］中見出し…` | 正常 |
| **322** | `［＃中見出し］○○○○○※※［＃米印］※［＃始め二重山括弧］中見出し…` | **壊れる** |
| **325** | `［＃中見出し］○○○○○※※［＃始め二重山括弧］中見出し※［＃終わり二重山括弧］○○○○○…` | **壊れる** |
| 328 | `［＃中見出し］○○○○○※特殊記号中見出し○○○○○［＃中見出し終わり］` | 正常 |

生成された XHTML の比較:

```html
<!-- 正常 (L316) -->
　　<div class="chap2">○○○○○《中見出し》○○○○○</div><p>　本文本文…</p>

<!-- 壊れている (L322) — </div> が無く、見出し文字も途中で切れている -->
　　<div class="chap2">○○○○○※※<p>　本文本文…</p>

<!-- 壊れている (L325) -->
　　<div class="chap2">○○○○○※<p>　本文本文…</p>
```

条件は「**素の `※` の直後に、注記開始の `※［＃…］` が続く**」場合。
`※［＃米印］` が単独で連続する L319 は正常なので、
`※` + `※［＃` の並びで注記の開始位置検出がずれ、
見出しの終端処理まで巻き込んで壊れていると推測される (要調査)。

## 影響

- 生成 EPUB が整形式でない → `epubcheck` NG
- 実機リーダーで該当セクションが表示されない / 途中で切れる
- `application/xhtml+xml` で配信するプレビューでもエラー表示になる

CI がこの入力を変換していないため、これまで検出されていなかった。

## 調査の入口

- `src/com/github/hmdev/converter/AozoraEpub3Converter.java` の
  中見出し (`chap1` / `chap2` / `chap3`) の開始・終了タグ出力箇所
- `※［＃…］` (外字・特殊記号注記) の検出とスキップ処理
- 見出し行の終端 (`［＃中見出し終わり］`) の検出

## 対応方針 (未着手)

1. まず整形式かどうかを検証するテストを追加する。
   `test_data/test_chapter.txt` を変換し、全 XHTML を XML パーサに通して整形式を確認する。
   これは他の記法での同種バグも一度に捕まえられる
2. 上記 2 行を最小の再現ケースとして切り出す
3. 注記検出のずれを修正する
4. CI で `epubcheck` にかける入力に `test_chapter.txt` 由来の EPUB を追加する

## 備考

本件は **EPUB プレビュー機能 (Phase 1) の PR のスコープ外**。
プレビュー側は不正な XHTML をそのまま配信しているだけで、動作は正しい。
別 PR で対応する。
