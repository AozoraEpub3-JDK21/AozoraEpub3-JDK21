# EPUB プレビュー機能 実装計画

作成日: 2026-08-08 / 改訂: 2026-08-08 (要件追加) / 対象バージョン: v1.5.0-jdk21 (予定)

## 背景・目的

narou.rs / narou.rb や本アプリで生成した EPUB が「実際どう見えるか」を確認するために、
毎回 Kindle 実機へ転送するのは往復コストが大きい。生成直後にその場でレイアウトを
確認できる手段を用意する。

確認したい主な観点:

- 縦書きの行折り位置
- ルビ (`<ruby>`) の衝突・はみ出し
- 挿絵の収まり
- 濁点合成フォント (`dakutenType=2`, `gaiji/dakuten/` の TTF 222 本) の見え方

## 要件一覧

| ID | 要件 | 出所 |
|---|---|---|
| R0 | 生成 EPUB をその場でプレビュー。GUI に入り口、CLI からも起動 | 初回要望 |
| R1 | 近代的なフォントを選択できる | 追加要望 |
| R2 | 章 / セクションへのジャンプ | 追加要望 |
| R3 | 特定フォルダから EPUB をインデックス生成し、一覧から選択 (本棚) | 追加要望 |
| R4 | ダークモード | 追加要望 |
| R5 | Kindle / Kobo / iPhone のレイアウト枠でのチェック | 初回要望 |
| R6 | 縦書き / 横書きなど CSS・メタ情報を確認できる (インスペクタ) | 追加要望 |

---

## レンダリング方式の決定

**採用: EPUB を一時展開し、JDK 内蔵 `com.sun.net.httpserver.HttpServer` で
127.0.0.1 限定配信 → OS 既定ブラウザ (`java.awt.Desktop.browse()`) で開く。**

### 比較検討

| 方式 | 縦書き `vertical-rl` | ルビ | 埋め込み `@font-face` | 依存増 | 判定 |
|---|---|---|---|---|---|
| **HTTP + 既定ブラウザ** | 確実に◎ (Blink / WebKit) | ◎ | ◎ | **ゼロ** | **採用** |
| JavaFX WebView | 不確実 (古い WebKit fork、実機検証が必要) | 不確実 | 要検証 | openjfx を OS 別に数十MB | 却下 |
| Swing `JEditorPane` | 不可 (HTML 3.2 相当) | 不可 | 不可 | ゼロ | 却下 |
| JCEF (埋め込み Chromium) | ◎ | ◎ | ◎ | 100MB 超を OS 別に | 却下 |
| 外部ビューアー呼び出し (calibre 等) | ビューアー依存 | 同左 | 同左 | ゼロ | 補助手段としてのみ検討 |

### 採用根拠 (調査で裏取り済み)

- `template/OPS/css/vertical_text.vm:12-14` は **標準の `writing-mode: vertical-rl`**
  を `-webkit-` / `-epub-` prefix と併せて出力している。モダンブラウザでそのまま
  縦書きレンダリングされる。
- `module-info.java` は存在せず classpath 運用のため、`com.sun.net.httpserver`
  (jdk.httpserver) が **追加依存ゼロ**で利用できる。fat JAR のサイズは増えない。
- `java.awt.Desktop` は Swing 利用のため既に `java.desktop` に依存済みで、
  Windows / macOS / Linux の 3 OS で動作する。

### `file://` 直開きを採らない理由

`application/xhtml+xml` の扱いと `@font-face` の読み込みが
ブラウザ・OS ごとに不安定なため。HTTP 配信であることが本質。

---

## 調査で判明した重要事実 (設計に直結)

### F1. 生成 EPUB の本文フォント指定は環境依存で、実機とは一致しない

`template/OPS/css/vertical_font.css:5` は

```css
font-family: "@ＭＳ 明朝", "@MS Mincho", "ヒラギノ明朝 ProN W3", "HiraMinProN-W3", serif, sans-serif;
```

を指定している。先頭の `@` プレフィックスは **Windows の縦書き用回転フォント**を
指す旧来の EPUB リーダー向けの命名規約。

**実測 (2026-08-08 / Windows 11 + Chrome、本ビューアーの文字幅比較による推定)**:

| ファミリ | 判定 |
|---|---|
| `@ＭＳ 明朝` | 解決される |
| `@MS Mincho` | 解決される |
| `ヒラギノ明朝 ProN W3` | 解決されない |
| `HiraMinProN-W3` | 解決されない |

macOS 専用フォントが正しく「解決されない」と出ているため判定手法自体は機能しており、
**Windows の Chrome は `@` 付きファミリ名を解決している**と考えられる。
当初「ブラウザには存在しないファミリ名として無視される」と想定していたが、
これは実測と合わないため撤回する。

> 要検証 (未確認のまま断定しないこと):
> - 解決先が `ＭＳ 明朝` と同一の字形か、`@` の 90 度回転が適用されるか
> - macOS / Linux のブラウザでどう解決されるか (該当フォントが無いため挙動が変わるはず)

**確実に言えること**: 表示されるフォントは閲覧環境にインストールされたフォント次第で、
Kindle / Kobo / Apple Books の実機既定フォントとは**一致しない**。
したがって **R1 のフォント選択機能は "おまけ" ではなく、
プレビューで見た目を実機に近づけるための中核機能**である。
一方、UI で「ブラウザでは無視される」と断定してはならない。
空の選択肢は「EPUB の指定のまま」と表記する。

同ファイルは `!important` 付きの `font-family` も持つ (`:10`, `:38`, `:57`, `:75`)。
上書き用に注入する CSS は `!important` と十分な specificity を確保する必要がある。

### F2. 目次の階層情報は toc.ncx にしかない

- `template/OPS/toc.ncx.vm` は `navPoint` を `chapter.NavClose` で閉じる構造を持ち、
  **入れ子 (階層) の目次を出力できる**。
- `template/OPS/xhtml/xhtml_nav.vm` の toc nav は `<ol>` 直下に `li.chapter` を
  並べる **フラット構造**。

**帰結**: 目次パースは **toc.ncx 優先 → nav.xhtml フォールバック → spine のみ** の順とする。
(Fable の初期案は nav 優先だったが、本プロジェクトの出力では逆が正しい)

### F3. 目次リンクはフラグメント付き

`xhtml_nav.vm` / `toc.ncx.vm` はいずれも
`<a href="${chapter.SectionId}.xhtml#${chapter.ChapterId}">` の形でフラグメントを出力する。
**R2 のセクションジャンプは `#id` 対応が必須**。単なる spine 単位のページ送りでは要件を満たさない。

### F4. landmarks nav と kindle モードの nav

- `xhtml_nav.vm` は `epub:type="landmarks"` の nav も出力する (`hidden` / `display:none`)。
  目次抽出時に拾ってはならない。
- kindle モードでは toc nav が `<nav id="toc">` となり **`epub:type="toc"` が付かない**。
  抽出条件は `epub:type="toc"` **または** `id="toc"` とする。

### F5. dakuten 合成フォントとフォント上書きの相互作用

`dakutenType=2` では `.dakuten*` クラスに `@font-face` の合成 TTF が当たる。
本文フォントを上書きしても `@font-face` 側が勝つため**壊れはしない**が、
合成グリフの字形は生成元フォント固定なので、本文を別フォントにすると
**その文字だけ字形が浮く**。UI の注記対象とする。

---

## 「実機再現」は謳わない

Kindle は KFX 独自エンジン (しかも `presets/kindle_pw.ini` は `Ext=.mobi` で、
実機に渡るのは EPUB ですらない)、Kobo も独自エンジン、iPhone は Apple Books = WebKit。
**実機の描画を完全再現することは原理的に不可能。**

したがって:

- 機能名は「デバイスプレビュー」ではなく **「レイアウトプレビュー (画面サイズ近似)」**
- UI に「画面サイズ・フォントの近似表示です。実機の描画とは異なります」と明記する
- 実機とのページ番号一致は謳わない
- F1 を踏まえ、フォントについても「実機の既定フォントとは異なる」と明記する

再現できるのは行折り・ルビ衝突・挿絵の収まり・濁点フォントの傾向確認であり、
「実機転送の往復を減らす」という本来の目的はこれで満たせる。

---

## 個別要件の設計

### R1. フォント選択

**供給源: OS にインストール済みのフォントを Java 側で列挙する。**
`java.awt.GraphicsEnvironment.getAvailableFontFamilyNames(Locale.JAPANESE)` で
実在するファミリ名を取得し、ビューアーへ JSON で渡す。

- ブラウザ側の `queryLocalFonts()` は Chrome 限定かつ権限ダイアログが必要なため採らない。
- **Web フォントの同梱は行わない** (Noto Serif JP はサブセットなしで 5〜10MB あり、
  fat JAR を太らせない方針と衝突する)。

UI は「推奨リスト (実在するものだけ表示)」+「インストール済み全ファミリ」の 2 段構成。
推奨リスト (縦書きメトリクス対応を確認できたもの / できないものは要検証と明記):

| 分類 | 候補 | 備考 |
|---|---|---|
| 明朝 | 游明朝 (Yu Mincho) / BIZ UDP明朝 / ヒラギノ明朝 ProN / Noto Serif JP / 源ノ明朝 | Windows 11 標準は游明朝・BIZ UD |
| ゴシック | 游ゴシック / BIZ UDPゴシック / ヒラギノ角ゴ ProN / Noto Sans JP / Meiryo | |
| 教科書体・その他 | UD デジタル教科書体 / Klee One / Zen Old Mincho | インストールされていれば表示 |

**既定値は UD デジタル教科書体**（`FontCatalog.DEFAULT_BODY_PREFERENCE` の先頭）。
縦書きの日本語を読むのに素直な字形で、Windows 10 以降に標準搭載されている。
無い環境では 游明朝 → BIZ UDP明朝 → ヒラギノ明朝 ProN → Noto Serif JP の順に落ち、
どれも無ければ「EPUB の指定のまま」になる。

設定値の `fontFamily` は 3 状態を区別する:

| 値 | 意味 |
|---|---|
| `null` | 未設定。サーバが薦める既定 (`defaultBody`) を適用する |
| `""` | ユーザーが「EPUB の指定のまま」を明示的に選んだ。上書きしない |
| フォント名 | ユーザーが選んだフォント |

併せて調整できるもの: フォントサイズ倍率、行間 (`line-height`)、**本文の上下 / 左右余白**。
いずれも iframe 内へ注入する `<style>` で実現する。

余白について: 生成 EPUB は `html { margin: 0; padding: 0 }` 相当で出力されるため、
ブラウザで開くと本文が画面端まで詰まって読みづらい。
`html { box-sizing: border-box; padding: <上下>em <左右>em }` を注入して調整する。
縦書きでは上下余白が行長を、左右余白が読み始め / 読み終わりの余白を決める。
既定値は上下 1.5em / 左右 1.5em。

> 要検証: 各フォントの縦書き用字形置換 (GSUB `vert` / `vrt2`) がブラウザで
> 正しく効くか。特に括弧・長音記号の回転。実装後に実物で確認する。

### R2. 章 / セクションジャンプ

- **目次サイドパネル**をビューアーに持つ (開閉可、既定は開)。
- 階層目次を toc.ncx から構築 (F2)。フォールバックは nav.xhtml → spine。
- 各項目は `{spine index, fragment}` を持ち、クリックで
  該当 XHTML をロード → `#id` 要素へ `scrollIntoView()` (F3)。
- 縦書きでも `scrollIntoView()` は機能するが、`block`/`inline` の対応が
  writing-mode により入れ替わる点に注意する。
- キーボード: 章送り (`[` / `]`)、ページ送り (後述)、目次トグル (`t`)。

### ページ送りの操作 (マウス / キーボード)

読み進む向きは書字方向で変わるため、`readingAxis()` の符号から判断する
(縦書き `vertical-rl` は左が「次へ」、横書きと `vertical-lr` は逆)。

| 操作 | 内容 |
|---|---|
| 本文の**左右端 30% をクリック** | ページ送り。中央 40% はリンク操作と文字選択のために空ける |
| **ホイール** | ページ送り。1 ノッチで飛びすぎないよう 180ms 間引く。Ctrl+ホイールはブラウザのズームなので触らない |
| 左右端の**オーバーレイボタン** | ホバーで現れる。操作の存在に気づけるようにするため |
| ← / → / Space / PageUp / PageDown | 同上 |
| `[` / `]` | セクション送り |

クリックでページ送りする際は、`<a>` を踏んだ場合と文字選択中は何もしない。

### R3. 本棚 (フォルダインデックス)

指定フォルダ配下を再帰スキャンし `.epub` / `.kepub.epub` を収集、
書誌メタデータと表紙サムネイル付きの一覧を出す。

- メタデータは各 EPUB の OPF から `dc:title` / `dc:creator` / 更新日時を取得。
  **全体を展開せず ZIP のエントリを直接読む** (`java.util.zip.ZipFile`)。
  解釈が展開経路とずれないよう、OPF の読み取りは `OpfParser.parse(Document, String)` を共用する
  (`XmlUtils.parse(byte[], String)` と併せて Phase 2 で追加した入口)。
- 表紙は OPF の `properties="cover-image"` (EPUB3) または
  `<meta name="cover" content="...">` (EPUB2) から特定し、
  縮小した PNG/JPEG をメモリ or キャッシュに保持。
  - EPUB2 の `meta[name=cover]` が**表紙ページ (XHTML) を指している** EPUB が実在するため、
    media-type が `image/` であることを確かめる
  - どちらの宣言も無い EPUB 向けに「id か href に `cover` を含む画像」の推測を最後に置く。
    外しても「表紙なし」になるだけなので許容する
  - **manifest にあっても ZIP に実在しない**表紙があるため、スキャン時に存在を確認して落とす
    (持たせたままだとサムネイル要求のたびに 404 になる)
- **インデックスキャッシュ**: `path / size / mtime / title / creator / coverEntry` を保存し、
  `size` + `mtime` 一致で再パースを省く。

  > **形式は JSON ではなく行指向のテキスト** (`~/.aozoraepub3/preview-library.tsv`) に変更した。
  > このプロジェクトには **JSON パーサが無い**。`Json` は「追加依存ゼロ」方針のもとで書かれた
  > **出力専用**のヘルパで、読む側を持っていない。読み書き両方が要るキャッシュのために
  > JSON ライブラリを足すのは方針と衝突し、自前パーサを書けば「壊れた入力の誤解釈」を
  > 新たに抱える。キャッシュは再生成できるので、曖昧さの無い形式を優先した。
  > 1 行 1 冊のタブ区切りで、値に含まれうるタブ・改行・バックスラッシュのみエスケープし、
  > null は `\0` で表して空文字と区別する。列数が合わない行は 1 行だけ捨て、
  > 先頭行の世代が違うファイルは丸ごと読み捨てる。
- **上限**: 走査の深さ 8 (`DEFAULT_MAX_DEPTH`)、冊数 2000 (`MAX_BOOKS`)、
  キャッシュ 5000 行 (`MAX_ENTRIES`)。冊数上限に達したら `warn` を出す
  (黙って切り詰めると「全部見えている」と誤解される)。
  シンボリックリンクは辿らない (ループと本棚の外への脱出を避けるため)。
- 壊れた EPUB・EPUB でない `.epub` が 1 つあっても**その 1 冊を飛ばして走査を続ける**。
  権限不足で読めないディレクトリも同様。
- 一覧からの選択時に **初めてその EPUB を展開する (遅延展開)**。
  全冊を先に展開しない。
- 既定のスキャン対象は **GUI の出力先フォルダ**。

> **この一般化は Phase 1 で実装済み。** URL は `/p/{token}/book/{bookId}/...` で、
> `PreviewSession` が `bookId` の採番・遅延展開・同一パスの重複排除まで持っている。
> Phase 2 は `LibraryScanner` / `LibraryIndexCache` と本棚 UI を載せるだけでよい。

### R4. ダークモード

3 状態トグル: **システム追従 / ライト / ダーク**。設定は `localStorage` に保持。

- ビューアーシェル (ヘッダ・目次パネル) は CSS カスタムプロパティで切り替え。
- **本文 (iframe 内)** はダーク用 CSS を注入して上書きする:
  `html, body { background: #1a1a1a !important; color: #d8d4cc !important; }`
  ルビ・傍点・見出しの色指定も併せて上書きする。
- `filter: invert(1)` 方式は挿絵まで反転するため**採らない**
  (挿絵の見え方確認というプレビュー本来の目的を損なう)。
- 挿絵は反転しないため、白背景の挿絵はダーク時に浮く。これは実機でも同様であり
  仕様とする (注記する)。
- Phase 2 以降でセピアを追加検討。

### R5. デバイスレイアウト枠

- iframe を実寸 px の枠に固定して表示。枠外は台紙色。
- プロファイルは変換設定と混ざらないよう **`preview/devices/*.ini` を新設**する。
  `presets/*.ini` は変換パラメータであり、画面 px 以外の情報 (物理 DPI・既定フォント・
  グレースケール有無) を持たず、また **iPhone 相当のプリセットが存在しない**ため。
  初期値としては `presets/kindle_pw.ini` の `DispW=629 / DispH=984` 等を流用する。
- E-ink グレースケールは `filter: grayscale(1)` で表現 (階調数や残像は再現しない)。

#### 仮想ページとページ送り (R5 に含める / Phase 1 では実装しない)

「1 画面 = 1 ページ」として通しページ番号を出し、ページ単位で送る機能。
**デバイス枠と同時に実装する**。理由:

- ページ送りはビューポート幅が固定されて初めて意味を持つ。
  可変幅のままページ番号を出しても、ウィンドウを変えるたびに総ページ数が変わる。
- 縦書きでは行が横に積まれ、1 行の送り幅 = `line-height` (px)。
  ビューポート幅がその整数倍でないと、**ページ境界で行が縦半分に切れる**。
  デバイス枠があれば iframe の幅を
  `floor(枠幅 / 行送り) * 行送り` に丸めて枠内で中央寄せできるため、
  この問題が構造的に解消する。可変幅のままでは解けない。

実装内容 (Phase 3):
- 行送り (`line-height` の px 値) を実測し、iframe 幅をその整数倍に丸める
- `pageCount = ceil(scrollWidth / clientWidth)`、位置 = `page * clientWidth`
- vertical-rl の `scrollLeft` は符号が処理系依存なため、
  一度だけ実測して符号を決める (仕様の想定を決め打ちしない)
- ページスライダ・ページ番号表示・左右タップでのページ送り
- セクションを跨いで戻るときは前セクションの最終ページに着地させる

Phase 1 のページ送りは「1 画面ぶんスクロールし、端に達したら隣のセクションへ移る」
挙動に留める (ページ番号は出さない)。

### R6. インスペクタ (メタ情報 / CSS 確認)

ビューアーに「情報」パネルを持ち、EPUB の中身を読まずに素性が分かるようにする。
narou.rs / narou.rb の出力が意図どおりか (縦書きになっているか、
package version は何か、フォントが埋まっているか) をその場で確認するのが狙い。

**宣言値と実効値を必ず分けて表示する。** EPUB の CSS に何と書いてあるか (宣言値) と、
ブラウザが実際にどう解釈したか (実効値) は F1 のように食い違うため、
片方だけでは誤診する。

| タブ | 内容 | 取得元 |
|---|---|---|
| 書誌 | `dc:title` / `dc:creator` / `dc:language` / `dc:identifier` / `dc:publisher` / `dcterms:modified` | OPF (Java) |
| 構成 | `<package version>` / `page-progression-direction` / `rendition:layout` / spine 件数 / 章数 | OPF (Java) |
| 内訳 | manifest を media-type 別に集計 (XHTML / CSS / 画像 / フォント) と各サイズ・合計 | OPF + ZIP エントリ (Java) |
| 実効スタイル | 表示中セクションの `writing-mode` / `direction` / `font-family` / `font-size` / `line-height` を `getComputedStyle` で取得 (**実効値**) | iframe (JS) |
| CSS | 適用中の CSS ファイル一覧と全文表示。`writing-mode` / `font-family` の該当行をハイライト (**宣言値**) | 展開済みファイル (JS fetch) |
| フォント | `@font-face` で埋め込まれたフォント一覧 (dakuten 合成 TTF の本数を含む) | manifest (Java) |

- 縦書き / 横書きの判定は **`getComputedStyle(doc.documentElement).writingMode` を正**とし、
  併せて OPF の `page-progression-direction` (縦書きなら通常 `rtl`) を並べて表示する。
  両者が矛盾する場合は警告バッジを出す。
- 「実効フォント」は指定されたファミリ名ではなく、**実際に採用されたフォント**を知りたい。
  ブラウザ標準 API では取得できないため、
  Canvas で候補ファミリごとに文字幅を測り fallback と一致するかで推定する
  (完全ではないため「推定」と明示する)。
  > 要検証: この推定手法の精度。困難なら「宣言値のみ + F1 の注記」に留める。
- CSS 全文表示は `<pre>` へのプレーンテキスト出力とし、
  シンタックスハイライトのためのライブラリは導入しない (依存ゼロ方針)。

---

## アーキテクチャ

新規パッケージ `com.github.hmdev.preview`。
`AozoraEpub3Applet.java` は既に 5271 行あるため、**GUI 側の追加は facade 呼び出しのみ**に
留め、ロジックは全て preview パッケージに置く。

| クラス | 責務 | Phase |
|---|---|---|
| `EpubExtractor` | EPUB (ZIP) を一時ディレクトリへ展開。Zip Slip / zip bomb 対策 | 1 |
| `SpineItem` | spine 1 項目 (EPUB 内相対パス) の record | 1 |
| `TocEntry` | 目次 1 項目 (ラベル・spine index・fragment・子要素) の record | 1 |
| `OpfParser` | `container.xml` → OPF → spine 順 / manifest / メタデータ | 1 |
| `TocParser` | toc.ncx → nav.xhtml → spine の順で階層目次を構築 | 1 |
| `EpubInspection` | 書誌・構成・manifest 内訳・埋め込みフォント一覧の集計 (R6 の Java 側) | 1 |
| `FontCatalog` | インストール済みフォント列挙 + 推奨リストの実在フィルタ | 1 |
| `PreviewServer` | `HttpServer` 配信。loopback / ランダムポート / トークン / パストラバーサル防止 | 1 |
| `PreviewSession` | 1 セッションの寿命 (展開先・サーバ・URL・マウント済み book) | 1 |
| `PreviewLauncher` | 展開 → サーバ起動 → `Desktop.browse()` の facade。**GUI と CLI の唯一の入口** | 1 |
| `LibraryEntry` | 本棚 1 冊の書誌 (パス・サイズ・更新時刻・書名・著者・表紙エントリ) の record | 2 |
| `LibraryScanner` | フォルダ再帰スキャン + ZIP 直読みの OPF メタデータ + 表紙位置の特定 | 2 |
| `LibraryIndexCache` | インデックスの永続化 (size + mtime で無効化) | 2 |
| `DeviceProfile` / `DeviceProfileLoader` | `preview/devices/*.ini` の読み込み | 3 |
| `assets/viewer.html` / `viewer.css` / `viewer-*.js` (7 分割) | ビューアーシェル (JAR 同梱リソース) | 1 |

`sourceSets.main.resources.srcDir = 'src'` のため、`src/com/github/hmdev/preview/assets/`
配下はそのままクラスパスリソースとして JAR に入る。

### EPUB を「読む」コードは新規

既存 `src/` には EPUB を書く側 (`Epub3Writer`) しか無く、読む側のコードは存在しない。
`OpfParser` / `TocParser` がこの機能の新規性の核となる。

- `container.xml` / OPF / toc.ncx は JDK 内蔵 DOM (`DocumentBuilderFactory`) でパースする。
  XXE 対策として `FEATURE_SECURE_PROCESSING` / `disallow-doctype-decl` /
  外部エンティティ無効化を設定する。
- 名前空間差異を吸収するため `getElementsByTagNameNS("*", ...)` を用いる。
- nav.xhtml は DOCTYPE を持つため DOM ではなく **jsoup (既存依存) でパース**する。
- `spine` の `linear="no"` を除外する (表紙ページ等が章順に混ざるのを防ぐ)。

### HTTP エンドポイント

| パス | 内容 |
|---|---|
| `GET /p/{token}/` | ビューアーシェル (viewer.html) |
| `GET /p/{token}/asset/*` | viewer.css / viewer-*.js (クラスパスリソース。`ALLOWED_ASSETS` の白リスト) |
| `GET /p/{token}/api/session` | フォント一覧・テーマ既定値などの初期情報 (JSON) |
| `GET /p/{token}/api/book/{bookId}` | spine + 階層目次 (JSON)。初回アクセスで遅延展開 |
| `GET /p/{token}/api/book/{bookId}/inspect` | 書誌・構成・manifest 内訳・埋め込みフォント (JSON) |
| `GET /p/{token}/book/{bookId}/*` | 展開済み EPUB 内のファイル |
| `GET /p/{token}/api/settings` | 保存済みの表示設定 (JSON) |
| `POST /p/{token}/api/settings` | 表示設定を保存 |
| `POST /p/{token}/api/heartbeat?tab={id}` | タブが生きていることの通知 (204) |
| `POST /p/{token}/api/bye?tab={id}` | タブを閉じた通知 (204、`sendBeacon`) |
| `GET /p/{token}/api/library` | 本棚一覧 (JSON) — Phase 2 |
| `GET /p/{token}/api/library/cover/{bookId}` | 表紙サムネイル — Phase 2 |

`/p/{token}` (末尾スラッシュ無し) は `308` でクエリごと `/p/{token}/` へ送る。

> **エンドポイントを足すときの不変条件: 状態を変える操作は必ず POST にする。**
> 発信元検査 (後述の Origin 検査) は「GET / HEAD 以外」を条件に掛かっているため、
> 状態を変える操作を GET で足すと**無検査で通る**。
> 既に `api/book/{bookId}` / `.../inspect` / `book/**` の GET は
> `PreviewSession.ensureExtracted` (ZIP 展開) を誘発する前例があるので、
> 「GET は副作用ゼロ」という前提には寄りかからないこと。
> Phase 2 で本棚の再スキャンや削除を足す場合は POST にする。

### セキュリティ設計

ローカル HTTP サーバであるため以下を必須とする。

1. `InetAddress.getLoopbackAddress()` に bind (LAN へ露出させない)
2. ポートは `0` を指定して OS 任せのランダム割り当て
3. URL パスに起動ごとの UUID トークンを含める (`/p/{uuid}/...`)。不一致は 404
4. パストラバーサル防止: リクエストパスを URL デコード後 `normalize()` し、
   展開 root の `startsWith` 検査を通す
5. `bookId` は内部で採番した不透明 ID とし、ユーザー入力パスをそのまま使わない
6. `.xhtml` は `application/xhtml+xml`、`.ttf` は `font/ttf` 等 Content-Type を明示
7. 一時ディレクトリは JVM 終了時に shutdown hook で再帰削除。
   Windows でブラウザがフォントを掴んで削除に失敗する場合に備え、
   **起動時に前回の残骸も掃除する**。
   ただし GUI と CLI の同時起動などプレビューは並行しうるため、
   各セッションは展開先に `.lock` を作って `FileLock` を保持し、
   **掃除はロックを取得できたディレクトリだけ**に限定する
   (実行中の他セッションの展開先を消さないため)
8. **EPUB 内のスクリプトを実行させない**。本文 XHTML はビューアーと同一オリジンで
   配信されるため、対策が無いと信頼できない EPUB が `window.parent` や
   `api/session` に到達できてしまう。二重に防ぐ:
   - ビューアーの iframe に `sandbox="allow-same-origin"` を付ける
     (`allow-scripts` は与えない。`allow-same-origin` は親からの CSS 注入と
     `getComputedStyle` に必要なので残す)
   - `/book/**` のレスポンスに
     `Content-Security-Policy: script-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'`
9. `api/session` に **EPUB の絶対パスを含めない** (ビューアーは使わないため)
10. リクエストパスのデコードに `URLDecoder` を使わない。
    あれはフォーム用で `+` を空白に変換するため、
    ファイル名に `+` を含む EPUB が 404 になる。独自の `decodePath` で
    パーセントエスケープのみを解き、壊れていれば 400 を返す
11. ハンドラは `Exception` を捕捉する。`InvalidPathException` などの非検査例外で
    接続が切れると原因が追えなくなるため、必ず応答を返す
12. **状態を変える POST は発信元を検査する** (CSRF)。`Origin` / `Sec-Fetch-Site` を
    ルーティングで一括して見て、他所を指していれば 403。詳細は後述の
    「POST エンドポイントの Origin 検査」

### 表示設定の永続化

ブラウザの localStorage は **スキーム + ホスト + ポート**で分離される。
プレビューサーバは毎回ランダムポートで起動するため、
localStorage だけに保存すると設定は起動のたびに必ず失われる。

そのため `PreviewSettingsStore` がサーバ側のファイルを正とする。

- 保存先: `~/.aozoraepub3/preview-settings.json`
  (アプリの配置先が読み取り専用でも書けるようにするため)
- API: `GET /api/settings` で取得、`POST /api/settings` で保存
- 上限 64KB、JSON オブジェクトの形をしていないものは拒否
- localStorage は起動直後の表示用キャッシュとして併用する

### GUI からの起動はワーカースレッドで行う

`FontCatalog.detect()` (`GraphicsEnvironment.getAvailableFontFamilyNames()`) は
Windows の初回呼び出しで数秒かかることがあり、
一時ディレクトリの再帰削除と `Desktop.browse()` も待ちが発生する。
EDT で実行すると GUI が固まるため、`AozoraEpub3Applet.openPreview()` は
デーモンスレッドで起動し、ログ出力とエラーダイアログのみ `invokeLater` で EDT に戻す。

### 展開のライフサイクル

「変換 → プレビュー → 設定を変えて再変換 → プレビュー」はこの機能の中心的な使い方であり、
出力パスは通常同じになる。ここを取り違えると古いレイアウトを見続けることになるため、
以下を守る。

- `addBook` は同じ絶対パスなら既存の bookId を返す
  (同じ本を開くたびに展開先が増えないようにするため。
  dakuten の TTF 222 本が押した回数だけ複製されるのを避ける)
- `ensureExtracted` は展開時の **サイズ + 更新時刻 (`FileTime`)** を覚えておき、
  変化していたら展開し直す
- **展開は必ず新しいディレクトリ (`{bookId}-v{n}`) に対して行い、
  成功してから切り替えて古い方を消す。**
  先に消すと、変換で書き込み途中の `.epub` を掴んだときに
  直前まで正常だった展開結果まで失われて表示できなくなる
- 展開済みの本は、**元 EPUB が消えていても配り続ける**。
  `serveBookFile` はファイル要求のたびに `ensureExtracted` を通るため、
  元ファイルの存在を必須にすると kindlegen が `.epub` を消した瞬間に
  表示中のプレビューが丸ごと 500 になる
- 更新判定に失敗した場合 (stat できない等) は「変化なし」に倒す

### URL の正規化

`/p/{token}` のように末尾スラッシュが無いと、ページ内の相対 URL が
`/p/api/...` に解決されて壊れる。**308** で `/p/{token}/` へ送る
(メソッドを変えないため 301 ではなく 308)。
`?book=...` を落とすと既定の本が開いてしまうのでクエリは引き継ぐ。

### GUI 入り口

`AozoraEpub3Applet` のファイル選択ボタン (`jButtonFile`, 1078 行付近) の隣に
「プレビュー」ボタンを追加する。変換完了後に最後に出力された `.epub` を保持し、
ボタンを有効化する。Phase 2 以降は同ボタンの副メニューに「本棚を開く」を追加する。

### CLI

`--preview` を追加する。既存の 14 オプション
(`-h -i -t -tf -c -ext -of -d -enc -hor -device -url -interval -cache -narou`) と衝突しない。

対象は位置引数から決める。引数付きオプションにすると `-preview input.txt` のように
後続の入力ファイルを吸ってしまい曖昧になるため、**引数なしのフラグ**とする。

- 位置引数が全て `.epub`: 変換せずそのままプレビューする
  (`-url` が併用されている場合は変換対象があるので通常フローへ)
- それ以外: 通常どおり変換し、最後に出力された EPUB を開く
- **ブラウザが閉じられるか Ctrl-C まで待機**する。
  即終了するとサーバが落ちてブラウザが読めなくなる

#### ブラウザを閉じたら CLI も終わる (heartbeat)

即終了できない一方、待ち続けると「タブを閉じたのに java プロセスが裏に残る」ことになる。
ブラウザの終了を直接検知する API は無いため、**ビューアーからの heartbeat**で判断する。

- ビューアーは**タブごとに ID を生成**し、15 秒ごとに
  `POST /p/{token}/api/heartbeat?tab={id}` を送る
- タブを閉じるときは `pagehide` で `POST /p/{token}/api/bye?tab={id}` を `sendBeacon` する
- サーバはタブ ID ごとに最終確認時刻を持つ
- 終了条件は `PreviewServer.isViewerGone()` が持つ (CLI は 2 秒間隔で見に行くだけ):
  1. 生きているタブが 1 つでもあれば終了しない
     (最終確認から 5 分経過したタブは閉じられたものとみなす)
  2. 全て居なくなったら、**閉じた通知によるものなら 20 秒** (`CLOSE_GRACE_MILLIS`)、
     **単に通信が途絶えただけなら 5 分** (`IDLE_TIMEOUT_MILLIS`) で終了
- GUI から起動した場合はアプリの寿命に合わせるため、この監視は行わない

**なぜタブ単位で持つ必要があるか**: サーバ全体で 1 つのフラグにすると、
タブを 2 つ開いて前面を閉じたときに、残ったバックグラウンドタブにも
短い猶予 (20 秒) が適用される。バックグラウンドタブの heartbeat は
約 60 秒間隔まで間引かれるため、**生きているタブがあるのにサーバが終了する**。
5 分の猶予を設けた意味が閉じた通知の経路で失われてしまう。

**なぜ無通信の猶予が 5 分と長いか**: バックグラウンドタブでは `setInterval` が
ブラウザに抑制される (Chrome は hidden タブのタイマーを概ね 1 分に 1 回まで間引き、
条件によっては freeze する)。送信間隔 15 秒に対して猶予を 1 分程度にすると、
**タブを開いたまま別のアプリを見ていただけでサーバが終了する**。
補助として `visibilitychange` / `pageshow` / `focus` でも即座に heartbeat を送り、
抑制されていた分の穴を埋める。

**なぜ閉じた通知だけ 20 秒と短いか**: タブを閉じたことが分かっているなら待つ必要がない。
ただしタブを複数開いている場合、閉じたのは 1 つだけかもしれないので、
heartbeat 間隔 (15 秒) より長く待ち、その間に別タブから heartbeat が届いたら
通知を取り消して継続する。

ブラウザが起動しなかった場合は 5 分で終了する。
URL はログに出力しているので、手動で開けば heartbeat が始まり継続する。

時間の計測には `System.nanoTime()` を使う。壁時計だと PC のスリープ復帰や
時刻補正で 5 分以上跳んだときに、見ている最中でも終了してしまう。

```bash
java -jar AozoraEpub3.jar --preview foo.epub          # 変換せず表示
java -jar AozoraEpub3.jar -of -d out --preview in.txt # 変換してから表示
```

Phase 2 でディレクトリ指定 (本棚) を受ける場合は、
位置引数がディレクトリのときの分岐として追加する。

---

## 段階リリース計画

### Phase 1 — プレビュー本体 (R0 + R1 + R2 + R4 + R6)

ビューアーシェル内で完結する要件をまとめて実装する。
Java 側の追加は `FontCatalog` (インストール済みフォント列挙) と
`EpubInspection` (OPF 集計) のみで、いずれも既に必要な `OpfParser` の上に
数十行を載せるだけで済む。フォント選択・目次ジャンプ・ダークモード・
インスペクタは HTML/JS/CSS の作業が中心となるため、
分割するより 1 本にまとめたほうが総コストが低い。

- EPUB 一時展開 / OPF・spine 解決 / 階層目次 (toc.ncx 優先)
- ローカル HTTP サーバ (セキュリティ設計込み)
- ビューアーシェル: 目次サイドパネル・章/セクションジャンプ・
  フォント選択 (サイズ・行間含む)・ダークモード 3 状態・キーボードページ送り・
  インスペクタパネル (書誌 / 構成 / 内訳 / 実効スタイル / CSS / フォント)
- Swing GUI のプレビューボタン / CLI `--preview` フラグ
- ユニットテスト

規模感 (見積 → **実績**): Java 1,200〜1,600 行 → 同程度、
HTML/JS/CSS 900〜1,100 行 → **約 1,870 行**、テスト 350 行 → **105 件**。
`AozoraEpub3Applet` 差分は 30 行以内 → **約 83 行**
(半分はコメントと、変換完了時の `running` フラグ順序修正)。

JS が見積の倍近くなったのは、ページ送りの書字方向対応・heartbeat・インスペクタが
当初の想定より厚くなったため。Phase 2 / 3 の見積もりも同様に膨らむ前提で考えること。

### Phase 2 着手前に片付けたい構造的な課題

- ~~**`viewer.js` が 1 ファイル 1,300 行超**~~ → **対応済み (2026-08-08)**。
  既存のセクション区切りに沿って 7 ファイルへ分割した。

  | ファイル | 行数 | 担当 |
  |---|---|---|
  | `viewer-core.js` | 152 | 設計メモ・定数・`state`/`el`・起動・heartbeat |
  | `viewer-util.js` | 68 | `kvTable` / `formatBytes` / `getJson` 等 |
  | `viewer-settings.js` | 176 | 設定の読み書き・フォント選択 |
  | `viewer-toc.js` | 115 | セクション / 目次 |
  | `viewer-frame.js` | 249 | iframe への介入・ページ送り |
  | `viewer-events.js` | 260 | イベント・テーマ適用 |
  | `viewer-inspector.js` | 313 | インスペクタ |

  ES モジュール化はしていない。`state` / `el` を含む相互参照が多く、
  JS 側にユニットテストが無い状態では import/export の張り替えを検証しきれないため、
  **古典スクリプトのまま行単位でコピーしてセマンティクスを変えない**方針を採った。
  そのため次の 2 点に注意すること。

  - **各ファイルの先頭に `'use strict';` が要る**。古典スクリプトでは
    strict モードがスクリプト単位で効くため、書き忘れたファイルだけ sloppy mode になる
  - **`viewer-core.js` を最初に読み込む**。定数・`state`・`el` の `const` 宣言があり、
    後続ファイルの top-level から参照すると TDZ に当たる
    (関数定義しか持たないファイル同士は順序非依存)

  Phase 2 で本棚を足す場合は `viewer-library.js` を新設する。
  ファイルを増やしたら `PreviewServer.ALLOWED_ASSETS` と `viewer.html` の
  script タグを両方更新すること。更新漏れは
  `PreviewServerTest.everyAssetReferencedByTheShellIsServable` が
  `viewer.html` の参照を走査して検出する。

  分割後の運用ルール:

  - **新モジュールは自分のイベントを自分で bind する**。
    `viewer-events.js` の `bindEvents()` は既に 105 行あり、
    ここへ足し続けると分割の意味が消える
  - **同名の top-level 関数を別ファイルで定義しない**。古典スクリプトでは
    後から読み込んだ側が無警告で上書きする (モジュールと違いエラーにならない)
  - **`state` のフィールドは `viewer-core.js` のリテラルに全て宣言する**。
    動的に生やすと、分割後は「どこで生まれたか」を grep しないと追えない
  - ES モジュール化のトリガーは **JS ユニットテスト基盤の導入時**、
    または Phase 3 着手時。それまでは古典スクリプトのままとする (債務として認識)
- **static 状態が 2 つ増えている** (`PreviewLauncher.current` /
  `AozoraEpub3.lastOutputFile`)。CLAUDE.md の「グローバル状態を増やさない」に照らすと
  借金であり、Phase 2 で `--preview <dir>` を足す際に肥大させないこと

### Phase 2 — 本棚 (R3)

規模が大きいので 4 つの PR に分ける。スタック PR は 2026-08-08 に事故を起こしている
(`docs/ci-followups.md` §2) ため、**いずれも master から切って順に進める**。

| 段 | 内容 | 状態 |
|---|---|---|
| C1 | `LibraryEntry` / `LibraryScanner` / `LibraryIndexCache` + テスト | 実装済 (2026-08-09) |
| C2 | 表紙サムネイル生成 + `api/library` / `api/library/cover/{bookId}` + セッション連携 | 未着手 |
| C3 | 本棚 UI (`viewer-library.js` + CSS + `viewer.html`)。グリッド表示・書名/著者/更新日のソート・絞り込み | 未着手 |
| C4 | 入口: CLI `--preview <dir>` / GUI「本棚を開く」/ 変換後の自動プレビュー (下記 (a)) | 未着手 |

規模感: Java 500〜700 行 + JS/CSS 250 行 + テスト 150 行
(Phase 1 の実績どおり、見積の 1.5〜2 倍に膨らむ前提で見ること)。

#### Phase 2 で入れる小機能 (2026-08-08 ユーザー提案)

**(a) 変換完了後にプレビューを自動で開く (既定 OFF)**

- **既定は OFF**。今までどおり変換して終わる動作を変えない (下位互換)
- 設定の置き場所は `AozoraEpub3.ini` (`AozoraEpub3Applet.props`)。
  既存キーの命名 (`LastDir` / `UILang`) に合わせて `AutoPreview` とする
- **INI キーだけでは足りない。Swing 側にチェックボックスと保存処理が要る**
  (オプション画面への追加 + `props` への書き戻し)。UI 文言は ja/en 両方
- 発火点は `AozoraEpub3Applet.java` の「プレビュー対象を更新」箇所 (現状 4124 付近)。
  **kindlegen 経路ではリネーム後に上書きされる**ため、自動オープンはリネーム完了後に行う
- CLI は明示の `--preview` があるので対象外とする (フラグ優先)

**(b) プレビュー画面から EPUB のフォルダを開く**

**実装済み (2026-08-08)。** `POST /p/{token}/api/book/{bookId}/reveal` +
`FileRevealer` + ビューアーの 📂 ボタン。

- **パスをリクエストから受け取らず、bookId から解決した EPUB のみを対象にする**
  (任意パスを開かせない)。`PathUtils` と同じ思想
- **Windows で `explorer /select,<path>` は使えない。**当初これで実装したが、
  実機で**ユーザーのマイドキュメントが開いた**。`ProcessBuilder` は空白を含む引数を
  丸ごと引用符で囲むため、Explorer が `"/select,D:\...\[テスト] 目次.epub"` という
  1 個の解釈不能な引数を受け取るため。Java からは引用の仕方を選べない。
  **親フォルダを `java.awt.Desktop.open(File)` で開く** (パスを API として渡すので
  空白や日本語で壊れない)。フォールバックは `explorer` / `xdg-open` にフォルダを渡す。
  macOS だけは `open -R` でファイルを選択状態にできる (argv が素直に渡るため)
- **存在しないパスでファイラを起動しない。**kindlegen 経路では EPUB を消してから
  展開済みのものを配信し続けることがあり、そのまま起動すると Windows は
  やはりマイドキュメントを開き、macOS の `open -R` は無言で何も開かない。
  サーバ側でフォルダの存在を確認して 404 を返し、`FileRevealer` でも二重に塞ぐ
- `.NET` ポートへ移植する際も上記 2 点をそのまま踏襲すること

参考にした先行実装 (2026-08-08 にソースを確認):

| | narou.rb | narou.rs |
|---|---|---|
| 実体 | `Helper.open_directory` がサーバ側で OS のファイラを起動 (Windows は `explorer "file:///<path>"`、mac は `open`、Linux は `xdg-open` 相当。`/select` は使わない) | `src/commands/folder.rs` が `narou_rs::compat::open_directory` を呼ぶ |
| 入口 | Web UI の `post "/api/folder"`。`select_valid_novel_ids` で **ID を検証しサーバ側でパス解決**。生パスは受け取らない | `resolve_target_to_id(target)` → `novel_dir_for_record` で **同じくサーバ側解決** |
| 保護 | 既定で LAN の私有 IP にバインド。Digest 認証は**任意設定** | 未確認 |

どちらも「ID を受けてサーバ側でパスを解決する」設計で、上記 (b) の方針と一致する。
本アプリは 127.0.0.1 固定 + トークン必須なので、保護は narou.rb より厳しい。

### Phase 3 — デバイスレイアウト枠 + 仮想ページ (R5)

- `preview/devices/*.ini` (Kindle PW / Kobo Glo / Kobo Clara / iPhone) の新設
- 実寸枠表示・グレースケール
- **仮想ページとページ送り** (上記のとおりデバイス枠とセットでないと成立しない)
- i18n 追加

規模感: Java 200 行 + JS/CSS 600 行。

### Phase 4 以降 (未着手・要検討)

- セピアテーマ
- `.kepub.epub` の挙動確認
- しおり / 読書位置の保持
- アプリ内ウィンドウ表示の要否判断 (要ユーザー確認)

---

## テスト方針

`test/com/github/hmdev/preview/` に配置し、ネットワーク・ブラウザ起動に依存しない
決定論的ユニットテストとする。

- `EpubExtractorTest` — 正常展開、Zip Slip (`../` エントリ) の拒否
- `OpfParserTest` — container.xml → OPF → spine 順、`linear="no"` 除外、メタデータ取得
- `TocParserTest` — toc.ncx の階層構築、フラグメント保持、nav.xhtml フォールバック、
  landmarks nav を拾わないこと、kindle モードの `<nav id="toc">` を拾うこと
- `FontCatalogTest` — 推奨リストの実在フィルタ (環境非依存になるよう注入可能に設計)
- `EpubInspectionTest` — `<package version>` / `page-progression-direction` の抽出、
  manifest の media-type 別集計、埋め込みフォント本数のカウント
- `PreviewServerTest` — loopback bind、トークン不一致で 404、
  パストラバーサルで 404、Content-Type の付与、
  iframe の sandbox に `allow-scripts` が無いこと、`script-src 'none'` の付与、
  `api/session` が絶対パスを漏らさないこと、
  ファイル名の `+` が空白に化けないこと、壊れたエスケープで 400 を返すこと、
  設定 API の往復、
  他オリジンからの POST を 403 にすること (`Origin` / `Sec-Fetch-Site` / `Origin: null` /
  ポート違い)、両ヘッダとも無い POST は従来どおり通ること
- `PreviewSessionTest` — 遅延展開、二重展開しないこと、
  **実行中セッションの展開先を掃除で消さないこと**、持ち主の居ない残骸は消すこと
- `PreviewSettingsStoreTest` — 保存と読み込み、オブジェクト以外の拒否、サイズ上限
- `LibraryScannerTest` — 再帰スキャンと書誌の取得、**ディスクに何も展開しないこと**、
  `.epub` 以外を拾わないこと、壊れた 1 冊でスキャン全体を落とさないこと、
  表紙の 3 経路 (EPUB3 property / EPUB2 meta / 名前からの推測)、
  EPUB2 の meta が XHTML を指す場合に画像として使わないこと、
  ZIP に実在しない表紙を落とすこと、深さ上限、キャッシュの再利用と無効化
- `LibraryIndexCacheTest` — 往復、null と空文字の区別、値に含まれるタブ・改行で
  列がずれないこと、壊れた行だけを捨てること、世代違いを丸ごと捨てること、
  他フォルダの記録が消えないこと、上限で古いものから溢れること

`Desktop.browse()` はヘッドレス CI で失敗するため、`PreviewLauncher` は
「サーバ起動まで」と「ブラウザ起動」を分離し、テストは前者のみを対象とする。

---

## 既知の制約

### 本文中のルート相対 URL は解決できない

本文 XHTML が `<img src="/images/a.jpg">` のように**先頭スラッシュ**で参照している場合、
ブラウザはサーバのオリジン直下 (`/images/a.jpg`) を要求する。
プレビューは `/p/{token}/book/{bookId}/...` 配下で配信しているため、
この要求はトークンにも書籍にも一致せず 404 になる。

**修正しない判断**とその理由:

- EPUB 3 は Content Document 内の参照を**相対 URL とすることを求めており**、
  絶対パス参照は仕様に適合しない。本アプリの出力も相対 URL のみ
- 解決するには (a) 配信時に本文を書き換える、(b) Referer から書籍を推定して
  オリジン直下でも配信する、のいずれかになる。
  (a) は本文改変で「実際の見え方を確認する」という目的と衝突し、
  (b) はトークンによる保護を Referer で迂回できることになり退行になる
- OPF の manifest href については、先頭スラッシュを
  EPUB ルート基準として解決する (`OpfPackage.resolve`)。
  こちらはサーバ側で完結するため対応済み

将来対応する場合は Phase 3 以降で、(a) を「表示専用の書き換え」として
明示的にオン/オフできる形にするのが妥当。

### プラットフォーム差 (Windows / macOS / Linux)

2026-08-08 の 3 ゲートレビューで洗い出した OS 依存の残差。
**「展開先ルートの外へ出さない」という安全性の保証は `PathUtils` が文字列段階で全 OS 同一に行う**
(`..` とドライブ修飾)。OS のパス解決に判定を委ねていた実装は CI で Linux だけ落ちたため修正済み。

一方、**ファイル名を表現できるかは OS 依存で、意図的に揃えていない**。
W3C EPUB 3.3 OCF はファイル名に `" * : < > ? \ |`・末尾ドット・制御文字を使ってはならない
(MUST NOT) と定めているが、Linux / macOS は実際には受け付けるため、
仕様違反の EPUB でも Linux では表示できている。
これを全 OS で一律に拒否すると「今まで読めていた本が読めなくなる」ので、次の扱いとする。

| 事象 | 挙動 | 実装 |
|---|---|---|
| その OS で表現できない名前 (`a?b.jpg` を Windows で) | 該当エントリのみ欠落 + `warn` ログ | `PathUtils.resolveInside` が null、`EpubExtractor` が skip |
| Windows 予約デバイス名 (`OPS/NUL`) | 書き込みが成功したように見えて実体が残らない → `warn` ログ | `EpubExtractor` の展開後 `Files.exists` チェック |
| 予約名ディレクトリ配下 (`OPS/NUL/x.jpg`) | 当該エントリのみ skip (以前は展開全体が失敗) | `createDirectories` は例外を投げずに何も作らないため、`isDirectory` で作成を確認 |
| 大文字小文字を区別しない FS での衝突 (`Text.xhtml` / `text.xhtml`) | 先勝ちで後を skip + `warn` (以前は無警告で後勝ち上書き) | `EpubExtractor` の畳み込みキー照合 |
| Unicode 正規化 (NFC/NFD) の揺れ | 吸収しない。href と ZIP エントリ名で正規化形が異なる EPUB は Linux で 404 になりうる | 未対応 |

### POST エンドポイントの Origin 検査 (CSRF) — 対応済み (2026-08-09)

2026-08-08 のレビューで指摘。当時サーバ内に `Origin` / `Host` / `Sec-Fetch-Site` の
検査は 1 件も無く、防御はパスに載せたトークン単独だった。単純 POST はプリフライトを
伴わないため、**トークンを知る第三者のページからクロスオリジンで POST を打てる**。
`reveal` の追加で影響が「ローカル閲覧」から**「プロセス (ファイラ) の起動」へ昇格**し、
加えて `bookId` は `b1` 連番 (`PreviewSession`) で推測が容易だった。

**対応**: `PreviewServer.handle` のルーティングで、POST と判定した直後に
`isAllowedPostSource()` を 1 回だけ通す。個々のハンドラに置くとエンドポイントを
足したときに漏れるため、経路を 1 箇所に絞っている。これで
`api/heartbeat` / `api/bye` / `api/settings` / `api/book/{id}/reveal` が一律に守られる。
不一致は **403** を返す。

判定は 2 つのヘッダを**独立に**見て、どちらか一方でも他所を指していたら拒否する。
いずれも Forbidden header name なのでページの JavaScript からは詐称できない。

| ヘッダ | 受け付ける値 |
|---|---|
| `Sec-Fetch-Site` | `same-origin` / `none` (`cross-site` / `same-site` は拒否) |
| `Origin` | `loopbackOrigins()` が列挙する自分自身のオリジンのみ |

**両方とも無い場合は許可する。** ブラウザは GET / HEAD 以外では常に `Origin` を付ける
(Fetch 仕様) ため、無いということはブラウザ発ではない。CSRF は被害者のブラウザを
踏み台にする攻撃なので、ブラウザ以外からの POST はこの脅威の対象外であり、
弾いても防御にはならない (ローカルでコードを実行できる相手には元より意味がない)。
一方で弾くと curl / `java.net.http` からの利用が壊れる。

**Origin はホスト名を解決せず表記を列挙して比較する** (`loopbackOrigins`)。
`InetAddress.getByName(origin のホスト)` で判定すると、攻撃者が指定した文字列で
名前解決が走ることになる。ブラウザに渡すのは `urlHost()` 由来の 1 つだけだが、
URL はログにも出るためユーザーが `localhost` で開き直すことがある。そこで
`urlHost` 由来 / `localhost` / `127.0.0.1` / `[::1]` の 4 表記 (実ポート付き) を許可する。
`Set.of` は重複を許さず、bind 先が IPv4 のとき `urlHost` が `127.0.0.1` と衝突して
落ちるため `Set.copyOf(List.of(...))` を使っている。

ビューアー側 (`viewer-*.js`) の変更は不要。`fetch` も `sendBeacon` も同一オリジンなので
ブラウザが `Origin` と `Sec-Fetch-Site: same-origin` を自動で付ける。

**DNS リバインディングは対象外とした**。攻撃者が自ドメインを 127.0.0.1 に再解決させても、
`/p/{token}/` の UUID トークンとランダムポートの両方を知らなければ何も読めない。
`Host` ヘッダ検査を足しても、この構成では得られる防御が無い。

### 残件: 推奨フォントリストが Windows 実測のみ

`FontCatalog` の推奨フォント判定は `installed.contains()` の完全一致で、
Windows 11 の実測フォント名に基づいている。macOS の `ヒラギノ明朝 ProN W3` 等では
1 件も一致せず `getDefaultBody()` / `getDefaultMincho()` が null になり、
推奨欄が空になる可能性がある (mac / Linux 実機未検証)。
実機で確認し、OS ごとの候補名を持たせるか、部分一致に緩めるかを決めること。

## 落とし穴 (実装時に踏まないこと)

- **`file://` で直接開かない** — フォントと XHTML MIME で破綻する
- **Kindle プレビューの意味** — プレビューできるのは変換元 EPUB であり
  kindlegen 後の mobi/KFX ではない。UI 注記が必要
- **`linear="no"`** を除外しないと章順がズレる
- **landmarks nav を目次として拾わない** (F4)
- **`@ＭＳ 明朝` の扱いを断定しない** (F1) — Windows の Chrome は解決するという実測がある。
  「ブラウザでは無視される」とも「実機と同じ」とも書かない。言えるのは
  「閲覧環境のフォント次第で、実機の既定フォントとは一致しない」まで
- **vertical-rl の `scrollLeft` 符号** — CSSOM 仕様では vertical-rl / RTL の
  スクロール原点は右端にあり `scrollLeft` は 0 〜 負値を取る。符号を直接計算せず
  `scrollBy()` に相対値を渡し、スクロール量が変化したかで端を検出する
- **ダークモードで `filter: invert()` を使わない** — 挿絵確認が壊れる
- **インスペクタで宣言値と実効値を混ぜない** (R6) — F1 のとおり両者は食い違う。
  「CSS にこう書いてある」と「ブラウザがこう解釈した」を別欄にする
- **`<package version>` は "3.0" 固定が正しい** — EPUB 3.3 でも `version="3.0"`。
  インスペクタで "3.3" でないことを異常として表示しない
  (`memory/feedback_epub_package_version.md` 参照)
- **本棚で全冊を先に展開しない** — ディスクと時間を浪費する。遅延展開必須
- **`AozoraEpub3Applet.java` の再肥大** — preview パッケージ側にロジックを置く規律を守る
- **一時ディレクトリの削除失敗** — Windows でファイルハンドルが残る場合があるため、
  削除失敗を致命エラーにせず起動時掃除で回収する

---

## 決定事項 (2026-08-08 ユーザー確認済み)

1. **レンダリング方式は「既定ブラウザで開く」** — アプリ内ウィンドウ表示は Phase 4 以降の検討事項
2. **Phase 1 の範囲は R0 + R1 + R2 + R4 + R6** — ビューアーシェル内で完結する要件をまとめる
3. **Phase 2 = 本棚 (R3)、Phase 3 = デバイス枠 (R5)** — 日常の使い勝手を優先
4. **フォントは OS インストール済みのみを列挙する** — 配布サイズを増やさない。
   推奨フォントが 1 つも無い環境ではブラウザ既定にフォールバックし、その旨を UI に表示する

### 実装者判断で確定させた細部 (再確認不要)

- ダークモードは 3 状態 (システム追従 / ライト / ダーク)、`localStorage` に保持
- 本文のダーク化は CSS 上書きで行う (`filter: invert()` は挿絵が壊れるため不採用)
- CSS 表示にシンタックスハイライトライブラリは導入しない (依存ゼロ方針)
- CLI `--preview` は heartbeat が途絶えるか Ctrl-C まで待機する
  (当初は Ctrl-C のみとしていたが、ブラウザを閉じた後もプロセスが裏に残るため変更)
- 本棚のスキャン既定は GUI 出力先フォルダ、サブフォルダ再帰は ON (深さ上限を設ける)
- デバイスプロファイルは `preview/devices/*.ini` を新設 (`presets/*.ini` は流用しない)
