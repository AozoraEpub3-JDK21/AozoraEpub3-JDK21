# タイトルページ長タイトル自動調整 計画書

作成: 2026-08-01 / 設計: Fable

## 背景・問題

なろう系 Web 小説はタイトルが長文化しており(最大 100 文字)、EPUB のタイトルページ
(`template/OPS/xhtml/title_horizontal.vm`、narou.rb 互換設定では表紙として表示される)で
タイトルと著者名が 1 画面に収まらないケースがある。

現行の調整は 1 段階のみ:

```velocity
#if (${TITLE.length()} > 30)
font-size:1.75em;
#else
font-size:2em;
#end
```

構造的な問題(実 EPUB `n3823mj.epub`・90 文字タイトルで確認):

1. 31 文字でも 150 文字でも同じ 1.75em
2. `.upper`(タイトル領域)が `height:50%` 固定 → あふれた分は著者名領域に重なる/ページ外へ
3. SERIES / ORGTITLE / SUBTITLE / SUBORGTITLE が無い場合に `.space`(padding-top:5% =
   幅基準)が最大 4 個積まれ、長タイトル時ほど縦領域を浪費(なろう作品は毎回 4 個)
4. `${TITLE.length()}` は ruby 変換後の **HTML タグ込み長** → ルビ付きタイトルで過剰縮小

## タイトル文字数統計(2026-08-01 実測)

取得方法: なろう API(hyoka/monthly/weekly/daily/new 各 500 件)、カクヨム ランキング 8 ページ、
ハーメルン ランキング 3 ページ。unique タイトルのみ。スクリプトと生データは調査時の
scratchpad(`title_stats.py` / `titles_raw.json`)。

| サイト | n | median | p75 | p90 | p95 | p99 | max |
|---|---|---|---|---|---|---|---|
| 小説家になろう | 1808 | 27 | 41 | 57 | 66 | 93 | 100 |
| カクヨム | 588 | 33 | 47 | 68 | 80 | 96 | 100 |
| ハーメルン | 100 | 16 | 23 | 33 | 48 | 81 | 81 |
| **合計** | **2496** | **28** | **42** | **59** | **70** | **94** | **100** |

- 3 サイトともタイトル上限は 100 文字
- 30 文字超(現行の唯一の閾値)が 44%。60 文字超 9%、80 文字超 2.6%
- 分布はなだらかで、多段階の縮小ラダーが適切

## 設計

### B: Java 側で表示文字数を計算して Velocity へ渡す

`Epub3Writer.java` のタイトルページ生成部(現 673 行付近)で、変換後タイトルから
**表示文字数**を計算し `TITLE_LENGTH`(Integer)をコンテキストに投入する。

```java
/** 表示文字数: <rt>/<rp> の中身と全タグを除去し、img/実体参照は 1 文字と数える */
static int displayTextLength(String html)
```

- `<rt>…</rt>` `<rp>…</rp>` を除去(ルビの親文字のみ数える)
- `<img …>`(外字画像)は 1 文字(〓 相当)として数える
- 残りのタグを除去、`&…;` 実体参照は 1 文字と数える
- code point 数を返す(サロゲートペア対応)

### A: テンプレートの多段階フォントラダー

`title_horizontal.vm`:

| 表示文字数 | font-size | 統計上の該当率 |
|---|---|---|
| ≤30 | 2em(現行維持) | 56% |
| 31–45 | 1.75em(現行維持) | 23% |
| 46–60 | 1.6em | 12% |
| 61–80 | 1.4em | 6% |
| 81–120 | 1.25em | 3% |
| ≥121 | 1.1em | 0%(安全網) |

構造調整(**46 文字以上のときのみ**適用 — 45 以下は現行出力を byte 単位で維持):

- `.space` div を出力しない(縦領域の 20% を回収)
- `.upper` の `height:50%` を外して自然フローにする(著者名との重なりを防止)
- `.upper` の `padding-top` を 10% → 5% に縮小

後方互換フォールバック(旧 jar + 新テンプレートの組合せ対策):

```velocity
#if (!${TITLE_LENGTH})
#set ($TITLE_LENGTH = ${TITLE.length()})
#end
## TITLE も無い場合は #set が no-op になるため 0 扱い(タイトル無しでも旧レイアウト維持)
#if (!${TITLE_LENGTH})
#set ($TITLE_LENGTH = 0)
#end
```

フォールバック時の `${TITLE.length()}` はタグ込み・UTF-16 単位のため、旧 jar + ルビ多用
タイトルでは実際より長く判定され過剰に縮小される。6 段ラダー化で旧来(2 段)より影響が
出やすいが、新 jar では `TITLE_LENGTH`(タグ除去済)が使われるため問題にならない。

`title_middle.vm`(縦書き中央)も同じ `TITLE_LENGTH` で簡易ラダーを適用:
≤45 → 1.75em(現行維持)/ 46–80 → 1.4em / ≥81 → 1.2em。

### 非スコープ

- SUBTITLE / CREATOR の長さ対応(統計上問題になっていない)
- 表紙画像の自動生成(案 C)— 将来課題
- CSS `vw` 単位等のリーダー依存の自動フィット(対応がバラバラなため不採用)
- `${title_border}`(Java から未設定の死に変数)の整理

## 互換性

- **表示文字数 45 以下のタイトルは出力 byte 不変**(directive 行は Velocity が改行ごと
  swallow するため、分岐追加でも出力に影響しない)。実測確認済み(2026-08-01):
  旧 jar+旧テンプレート vs 新 jar+新テンプレートで、10 文字・39 文字・TITLE_MIDDLE の
  title.xhtml が byte 一致。
- 比較テスト 5 ケースのタイトル長: aozora_1567_14913=8 / n0063lr=25 /
  kakuyomu_822139840468926025=42 / n8005ls=45 / **n9623lp=100**。
  n9623lp のみ title.xhtml が変化(意図した変更)。Java 側 `JavaAozoraVsReferenceTest` は
  n9623lp の reference.epub を新出力で再生成して 5/5 PASS を確認済み。
- **残件(.NET ポート側)**: `aozoraepub3-dotnet` に本機能を移植するまで、.NET の
  `JavaComparisonTests[n9623lp]` は fail する。移植内容 = `TITLE_LENGTH` 投入
  (displayTextLength 相当)+ テンプレート 2 本の変更。
- narou.rb / MyNobel_rs 経由の変換でも、配布物の `template/` 差し替えだけで有効
  (テンプレートは jar 内にも同梱されるため、jar 更新でも有効)。

## テスト

1. `Epub3WriterDisplayTextLengthTest`(unit): プレーン / ruby 付き / img 外字 / 実体参照 /
   null / サロゲートペア
2. `TitlePageAutofitTemplateTest`(Velocity レンダリング、`VelocityTestUtils` 使用):
   - 各閾値の境界ペア(30/31・45/46・60/61・80/81・120/121)で期待 font-size が出力される
     (font-size は `.subtitle` 等の固定ルールにも含まれるため `.title { }` ブロックを抽出して検査)
   - 46 文字以上で `.space` が消え `height:50%` が外れる
   - 45 文字以下で現行と同一の CSS 断片(2em / 1.75em、height:50%、space 4 個)
   - `TITLE_LENGTH` 未設定時に `TITLE.length()` フォールバックが機能する(horizontal / middle 両方)
   - TITLE も無い場合に旧レイアウトを維持する(horizontal / middle 両方)
3. ミューテーション確認(feedback_review_gates): 実装を元に戻すとテストが赤くなることを確認
4. CI の epubcheck は既存フローで担保

## リリース

v1.3.8 に同乗予定。`.NET` ポートへの追随タスクを `docs/code-audit-followups.md` ではなく
本件 PR の説明と `aozoraepub3-dotnet` 側 issue で扱う。
