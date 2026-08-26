# AozoraEpub3 リリースノート

## 未リリース

（次期バージョンの変更をここに追記する。リリース時は正式なバージョン節へ書き換えること。）

---

## バージョン: 1.6.1-jdk21

**リリース日**: 2026年8月26日

### ハイライト

- **端末で表示できない外字を注記表示にフォールバックする設定を追加**（新機能・**既定は無効**）。
  JIS 第4水準の `𢌞`（`※［＃「廴＋囘」、第4水準2-12-11］`）のような文字は、
  端末のフォントにグリフが無いと `?` や □ になる。AozoraEpub3 側からは端末が表示できるか
  判定できないため、**文字の出力をやめて何の字か分かる注記に置き換える**選択肢を用意した。
  X 上で報告された事象（Xteink X3 で `𢌞` が `?` になる）への対応。

  | 設定 | 注記経由の第4水準 | 直接記述された第4水準 | 第3水準 | 第1・2水準 |
  |---|---|---|---|---|
  | 無効（既定） | `𢌞り` | `𢌞り` | `俠客` | `崎` `亜` |
  | 有効 | `〓（「廴＋囘」）り` | `〓り` | `俠客` | `崎` `亜` |
  | 有効＋水準コード | `〓（「廴＋囘」、第4水準2-12-11）り` | `〓り` | `俠客` | `崎` `亜` |

  - 抑止する水準は「第3水準以上 / 第4水準以上（既定）/ JIS規格外のみ」から選べる
  - **1 文字フォント（`gaiji/*.ttf`）が登録されている文字は抑止しない**。
    フォントがあれば端末フォントに依存せず正しく表示できるため
  - 注記を経由しない生の文字（本文に直接書かれた第3・第4水準）も抑止対象。
    異体字セレクタ（IVS/VS）が付いている場合はセレクタごと置き換える
  - GUI は「スタイル」タブの「外字」欄。水準の指定と水準コードの有無は
    「水準の設定...」の詳細ダイアログに置き、現在値をボタン横に常時表示する
  - ini キー: `GaijiFallback`（`1`/空）、`GaijiFallbackLevel`（`3`/`4`/`9`、既定 `4`）、
    `GaijiFallbackCode`（`1`/空）。GUI と CLI の両方に配線済み

**設定を変更しない限り、出力される EPUB は従来と 1 バイトも変わらない。**

### ドキュメント

- 「外字の設定」ページを日英で新規追加（`docs/gaiji-settings.md` / `docs/en/gaiji-settings.md`）。
  GUI のスクリーンショット付きで、水準の選び方と 1 文字フォントの置き方を説明している
- 公開ドキュメント全 12 ページをレビューし、事実として誤っていた記述を修正
  - 電書協 EPUB 3 制作ガイドのリンク先が ebookjapan.jp（無関係の電子書店）→ `ebpaj.jp/counsel/guide`
  - テンプレートのパスが実体と違っていた（`OPS/nav.xhtml.vm` → `OPS/xhtml/xhtml_nav.vm` 等）
  - Web 取得のレート制限「1.5 秒」→ 実際は GUI 0.5 秒 / CLI 1.0 秒
  - IDPF EPUB 3.3 Standard → EPUB 3.3 仕様（W3C 勧告）、Gradle 8 → 9.6.1、推奨 JDK を Java 25 LTS に統一
  - 存在しないクラス名（`AozoraEpubConverter` / `IniFile` / `ConfigValues` 等）を実在の名前に
  - CLI オプション表に `--check-update` が抜けていたのを追加

### 内部

- `JisLevelUtil` を追加。`x-SJIS_0213` の先頭バイト（面2 = 0xF0-0xFC）で JIS 水準を判定する。
  JDK 21 に `x-euc-jis-2004` が無く SS3 方式が使えないためこの方式を採った。
  `chuki_utf.txt` 自身が持つ水準表記 3,698 件と照合し 99.70% 一致を確認している
- レビューで見つかった 3 件の判定漏れを修正（いずれも「水準判定より手前で文字を出力していた」もの）
  - 注記経由の外字で 1 文字フォントの免除が効いていなかった
  - 異体字セレクタ付きの文字が判定を素通りしていた（`convertTcyText` の IVS/VS 4 分岐）
  - 本文とルビの長さが同じとき（`俠《き》`）に判定を素通りしていた

### 検証

- パッケージ付きユニットテスト 305 件 PASS（新規は `JisLevelUtilTest` 9 件、`GaijiFallbackTest` 24 件）
- `test_data/test_gaiji.txt`（第3・第4水準を網羅、MS932）で実変換し、4 通りの設定すべてで
  epubcheck エラー 0・警告 0 を確認
- 1 文字フォントの免除も実物の TTF を `gaiji/u2231e.ttf` に置いて確認（`@font-face` 生成・manifest 登録・epubcheck 通過）

---

## バージョン: 1.6.0-jdk21

**リリース日**: 2026年8月23日

### ハイライト

- **バージョン更新確認を追加**（新機能）。GitHub の最新リリースと実行中のバージョンを
  比較して、更新の有無だけを知らせる。**ダウンロードと差し替えは行わない**
  （自動更新なし・起動時の自動チェックもなし）。
  - GUI: 画面上部の「更新確認」ボタン。更新があればダウンロードページ（紹介ページ）を
    開くか確認するダイアログを出す
  - CLI: `--check-update`（短縮形 `-cu`）。確認できれば終了コード 0（更新の有無は
    反映しない）、通信できなければ 1
  - タグの数値部分だけで比較し、`v` 接頭辞と `-jdk21` などのビルド識別子は無視する
  - オフライン・プロキシ配下・GitHub API のレート制限（未認証で 1 時間あたり 60 回）では
    確認に失敗するが、変換機能には影響しない

- **表題ページの「横書き」が保存も復元もできなかった問題を修正**。GUI で「横書き」を
  選んでも ini の `TitlePage` が `1`（中央）のままになり、変換結果も中央になっていた。
  原因は過去の i18n 一括置換（`90256ae`）で `group.add(jRadioPageMarginUnit0);` の行が
  `jRadioTitleHorizontal = new JRadioButton(...)` に上書きされ、フィールドが二重代入
  されていたこと。画面に貼られたラジオとフィールドが別インスタンスになっていた。
  同じ 1 行が原因で「スタイル」タブの余白単位（字/%）が排他選択にならない不具合もあった
- **「スタイル」タブの余白パネルが 1 つに合体していた表示崩れを修正**（同じ事故で
  `panel = new JPanel();` が欠落していた）
- **`@page` の余白が不正な CSS になっていた問題を修正**。同じ事故で入力欄の初期値が
  失われ、ini に `PageMargin` が無い配布時の状態では `margin: em em em em` を
  出力していた
- **ダークテーマでポップアップメニューの文字が黒いままだった問題を修正**。
  「言語」「端末設定」のメニューとログ欄の右クリックメニューが、暗い背景に黒文字で
  表示されて読めなかった。`JPopupMenu` は表示するまでどのウィンドウにも属さないため、
  テーマ切替時の一括更新が届いていなかった
- **表紙設定「表紙無し」が ini に保存されない問題を修正**。上流で
  「入力ファイル名と同じ画像」が選択肢に挿入された際に保存条件が更新されていなかった。
  あわせて保存値を UI 言語に依存しない形式（`#samefile` / `#none`）に変更したため、
  言語を切り替えても設定が保持される。旧形式（表示ラベル）の ini も読み込める
- **CLI の余白設定が不正な CSS になる問題を修正**。ini の余白単位（`0` = 字 / `1` = %）を
  数値にそのまま連結していたため、`presets/kobo_glo.ini` などを `-i` で渡すと
  `margin: 00 0.50 00 00` が出力されていた（GUI 経路は正常）

### 互換性

- 出力 EPUB が変わるのは以下のみ。いずれも従来が不正な CSS だったケースの修正:
  - **GUI**: ini に `PageMargin` が無い状態での変換。`margin: em em em em` →
    `margin: 0.5em 0.5em 0.5em 0.5em`
  - **CLI**: ini に `PageMargin` / `BodyMargin` がある変換（同梱プリセットを含む）。
    `margin: 00 0.50 00 00` → `margin: 0em 0.5em 0em 0em`
- `AozoraEpub3.ini` の `Cover` の書式が変わる（`#samefile` / `#none`）。読み込みは
  旧形式にも対応しているため、既存の ini はそのまま使える
- CLI オプションの追加のみで、既存のオプションの挙動に変更はない

### 検証

- `gradlew test` 509 件・失敗 0（`PreviewServerTest` は既存の不具合により除外。
  `docs/code-audit-followups.md` 項目 31 で別途対応）
- サブエージェントによるフレッシュコンテキスト再レビュー 2 本の指摘を反映

---

## バージョン: 1.5.2-jdk21

**リリース日**: 2026年8月18日

### ハイライト

- **GUI「詳細設定」タブのホイールスクロールが極端に遅い問題を修正**。
  マウスホイール 1 ノッチで 3 ピクセルしか動かず、下端の設定（字下げ・改ページ等）に
  たどり着くまで延々とホイールを回す必要があった。
  原因は `JScrollPane` のビューが `Scrollable` 非実装の `JPanel`（BoxLayout）のため
  unit increment が既定の 1px（= ホイール 1 ノッチ 3px）になっていたこと。
  1 行分の高さ（起動時に実フォントメトリクスから算出する値。実行中のテーマ切替では
  再計算されない）を unit increment に設定し、1 ノッチで約 3 行分スクロールするようにした。
  ページ送り（block increment）は未設定のままとし、JDK 既定のビューポート高
  = 1 画面送りを使う

### 互換性

- GUI の操作性のみの修正。変換ロジック・出力 EPUB・CLI・ini 設定に変更はない
  （`.NET` ポートの byte-identical 比較テスト 5/5 PASS）

### 検証

- `gradlew test` 557 件・失敗 0（ネットワーク依存テスト等はスキップ）
- `.NET` ポート `JavaComparisonTests` 5/5 PASS
- GUI を起動し「詳細設定」タブのホイール送り量を目視確認

---

## バージョン: 1.5.1-jdk21

**リリース日**: 2026年8月11日

### ハイライト

- **青空文庫 HTML URL の表題二重を修正** (#80)。単話ページ（例: 走れメロス）の変換で
  表題が「走れメロス 走れメロス」となり、出力ファイル名・タイトルページ・プレビューの
  書名すべてに二重表題が出ていた。extract.txt の SERIES と TITLE が同一要素にマッチする
  場合に series を出力しないガードを追加（`.NET` ポートにも同時修正済み）
- **エンコード後の自動プレビューが CLI・narou.rb 経由でも動作** (#81)。
  GUI の「変換完了後に自動でプレビューを開く」（ini の `AutoPreview=1`）を有効にすると、
  narou.rb / narou.rs 等の CLI 呼び出しでも変換完了後に EPUB がブラウザで開く。
  プレビューは自分自身を `-preview` 付きの別プロセスとして起動するため、
  呼び出し側（narou.rb のバッチ処理）はブロックされない。タブを閉じれば
  プレビュー用プロセスは自動終了する。複数作品の一括変換では作品ごとにタブが開くため、
  一括処理の間はオフ推奨
- **FC2 小説の変換不能を修正** (#82)。サイト改修で作品タイトルの見出しが変わり
  「SERIES/TITLE : タイトルがありません」で変換できなくなっていた
- **サービス終了サイトの案内表示** (#82)。閉鎖済みサイト（dNoVeLs / Arcadia /
  NEWVEL-LIBRARY）の URL を指定すると「このサイトはサービスを終了しているため
  変換できません」と理由を明示して中断する（終了コード 1）。
  あわせて extract.txt の未知キーを警告 + スキップに変更（前方互換）
- **CLI の画像自動余白除去を修正** (#80)。ini の `AutoMarginNombreSize` が反映されず、
  `AutoMarginPadding` がノンブル高さの値で上書きされていた（GUI と同じ挙動に統一）
- **HamelnE2ETest の検証 URL 更新・Gradle wrapper 9.6.1 化** (#80)

### 互換性

- 出力 EPUB の構造は不変（`.NET` ポートの byte-identical 比較テスト 5/5 PASS）
- 出力が変わるのは以下のみ:
  - 青空文庫 HTML URL 変換: 表題の二重が解消（修正目的そのもの）
  - `AutoMargin=1` + `AutoMarginPadding` / `AutoMarginNombreSize` を設定した CLI 変換:
    画像の余白除去が GUI と同じ結果になる
- `AutoPreview` は既定オフ。有効にしない限り従来と挙動は変わらない

### 検証

- `gradlew test` 557 件・失敗 0（ネットワーク依存テスト等はスキップ）/ CI 全緑
- `.NET` ポート `JavaComparisonTests` 5/5 PASS
- 全 12 対応サイトの実変換 dogfood（`docs/web-site-support-status.md`）

---

## バージョン: 1.5.0-jdk21

**リリース日**: 2026年8月11日

### ハイライト

- **EPUB プレビュー機能** (#55 / #57 / #59 / #62〜#65 / #67 / #69 / #70)。
  変換した EPUB を Kindle 等の実機へ転送する前に、既定のブラウザでそのまま確認できる。
  縦書きの行折り・ルビの衝突・挿絵の収まり・濁点合成フォント（`dakutenType=2`）の見え方を
  変換直後に確かめる用途
  - **追加依存はゼロ**。EPUB を一時ディレクトリへ展開し、JDK 内蔵の `com.sun.net.httpserver` で
    ループバックアドレス（`127.0.0.1`、IPv6 優先環境では `::1`）のランダムポートに
    URL トークン付きで配信する。外部からは接続できない
  - 階層目次から章・見出しへジャンプ（`toc.ncx` 由来）、フォント（既定は UD デジタル教科書体）・
    文字サイズ・行間・上下左右の余白、ダークモード（システム追従 / ライト / ダーク）、
    インスペクタ（書誌 / 構成 / manifest 内訳 / 実効スタイル / CSS / 埋め込みフォント）、
    ページ送り（左右端クリック / ホイール / ← → / Space）
  - **本棚**: フォルダ（最大 8）に置いた EPUB を表紙サムネイル付きのグリッドで一覧。
    CLI は `--library <フォルダ>`、GUI は「プレビュー」タブで設定する
  - CLI は `--preview`（入力が `.epub` だけなら変換せずそのまま表示）。GUI は変換完了後に
    「プレビュー」ボタンが有効になり、「変換完了後に自動でプレビューを開く」設定も追加した
  - 表示設定は `~/.aozoraepub3/preview-settings.json` に保存される
  - 画面サイズ・フォントの近似表示であり、**実機再現は謳わない**
    （Kindle は KFX、Kobo・Apple Books も独自エンジンのため原理的に不可能）
- **GUI の見た目を FlatLaf でモダン化** (#77)。ライト / ダークテーマを同梱し、既定はライト。
  ini の `UiTheme` キー（`system` / `light` / `dark`）または画面右上のコンボボックスで
  切り替えられる（`system` は OS のテーマに追従、切り替えは即時反映）。
  レイアウトの固定ピクセル指定（約 46 か所）を実測メトリクス由来の値に置き換え、
  ウィンドウ既定サイズは 900×680（最小 720×520）になった。
  設計書: `docs/flatlaf-plan.md`。Windows で全タブ・全ダイアログをライト / ダーク両方で
  目視確認済み（macOS / Linux は未検証）
- **ハーメルンの変換不能を修正** (#72)。2026 年 8 月頃の話一覧 HTML 刷新
  （`<table>` → `.episode-list__items`）に追従。`extract.txt` のセレクタ更新に加え、
  章マッピングのガード、改稿日時を指紋に含める `SUB_UPDATE`、章あり作品の各話見出しから
  章名を剥がす `CONTENT_SUBTITLE` を修正した
- **カクヨムで新着話を取り逃していた不具合を修正** (#76、監査項目 21)。
  カクヨムは URL 末尾に `/` が無いため、一覧の保存先 `<workId>` と各話の保存先
  `<workId>/<episodeId>` が同じ名前を取り合い、各話ディレクトリが先にできていると
  一覧のキャッシュを更新できなかった。例外は握られて古いキャッシュが使われるため、
  ユーザーには「キャッシュファイルを利用します」としか見えないまま**新着話が反映されない**
  状態が続いていた。書き込み先を `<workId>/index.html` に寄せて解消（読み出し側は以前から
  このフォールバックを持っていた）
- **CLI で章見出しが目次に入らない不具合を修正**（監査項目 22、#74）。
  同梱 `AozoraEpub3.ini` に `Chapter*` キーが 1 つも無いため、CLI では `ChapterH` /
  `ChapterName` 等がすべて false になり、中見出しが目次に載らなかった（GUI は正常）。
  GUI と CLI が共有する既定値テーブル `SettingDefaults` を新設して解消した

### 互換性

- **CLI の目次まわりの既定値が GUI に揃った**。`-i` で `Chapter*` キーを持たない自作 ini を
  使っている場合、章見出しと表題が目次に入るように**出力が変わる**（意図した修正）。
  従来の目次に戻したい場合は ini に `ChapterH=` / `ChapterH1=` / `ChapterH2=` /
  `ChapterH3=` / `ChapterName=` / `TitleToc=` を明示する。
  GUI が保存した ini（全キーを持つ）と narou.rb 連携は出力不変
- **CLI が読む ini のキー名の誤りを 2 件修正した**（項目 22 に同梱）。`ChapterNumParenTitle` は
  CLI 側が `hapterNumParenTitle`（先頭の C 欠落）を読んでいて**常に false** だった。
  目次の最大文字数は GUI が `MaxChapterNameLength` で保存するのに CLI は `ChapterNameLength` を
  読んでいたため、**GUI で設定した値が CLI に届かず 64 のまま**だった。
  どちらも該当キーを ini に書いていた利用者は CLI の出力が変わる（設定が効くようになる）。
  旧名 `ChapterNameLength` は手書き ini 向けに互換読み出しとして残している
- **CLI の `-h` ヘルプの `-t` の値対応の誤記を修正した** (#75)（正: `3`:表題のみ(1行) /
  `4`:表題+著者名のみ(2行) / `5`:なし。旧ヘルプは「4:なし」と誤記）。コードの挙動は
  従来から変わらない — 旧ヘルプに従って `-t 4` を「なし」のつもりで使っていた場合、
  実際には以前から「表題+著者名のみ」で動いていた。「なし」にするには `-t 5` を指定する
- **目次以外も、ini にキーが無いときの CLI 既定値が GUI に揃った** (#75、項目 24)。
  ini を渡さない場合やキーの少ない自作 ini では、表題ページが出る（従来はなし）・
  濁点付き仮名が「重ねる」方式で表示される（`DakutenType` 0 → 1。合成フォント方式は 2）・
  自動改ページが効く・大きい挿絵が画面サイズに合わせて縮小される・
  表紙に「先頭の挿絵」を使う場合に挿絵を探す範囲が本文 10 行目までになる
  （`MaxCoverLine` 0 → 10。従来は制限なし）など**出力が変わる**。
  実測では ini なしの 1.8MB 見出し無しテキストが
  本文 XHTML 1 枚 → 5 枚 + 表題ページになった。GUI が保存した全キーの ini と
  narou.rb 連携先の実 ini（123 キー）では `dcterms:modified` 以外の差分ゼロを確認済み
- **同梱 `AozoraEpub3.ini` を 23 キーから全キー版に拡充した** (#75)。値は「新規展開した GUI が
  v1.4.x と同じ状態で起動する」ように、旧 ini にあったキーはそのまま・無かったキーは
  GUI のウィジェット初期値。**同梱 ini のまま CLI 変換していた場合も上記（項目 24）と
  同じ変化が起きる** — 目次ページが縦書きになる・自動改ページが効く・画像の画面サイズ
  調整が有効になる・JPEG 品質が 80 → 85 になるなど（これらのキーは項目 24 の既定値にも
  同じ値が入っているため、旧 ini を使い続けても結果は同じ）。GUI で一度でも設定を
  保存した環境は ini が全キーで上書き済みのため不変。従来の CLI 出力に固定したい場合は、
  使用する ini に次の 8 キーを明示する:
  `TocVertical=` / `PageBreak=` / `FitImage=` / `ImageSizeType=2` / `SinglePageSizeW=480` /
  `SinglePageSizeH=640` / `JpegQuality=80` / `MaxCoverLine=0`
  （リポジトリの `test_data/reference_comparison.ini` が同じ値を持つ）
- **CLI が jar と同じ場所の ini も読むようになった** (#75、項目 26)。探索順は
  `-i` 明示 → カレントディレクトリ → jar と同じ場所（同梱 ini）。配布フォルダの外から
  `java -jar` していた利用者は、これまで**無言で無視されていた**同梱 ini（= GUI で保存した
  設定）が効くようになり出力が変わりうる。カレント優先なので、カレントに ini を置く
  既存の運用は不変。どのファイルを読んだか・既定値で起動したかを起動ログに 1 行出す
- **`GothicUseBold` が効くようになった** (#75、項目 25 の一部)。CLI 側のキー名タイポ
  （`gothicUseBold`）で常に無効だったため、ini に書いていた利用者はゴシック注記が
  太字になる
- `ChapterPattern=1` だけを書いて `ChapterPatternText` が無い手書き ini で、
  null パターンによる「パターンが不正」警告が出なくなった（項目 23。無指定として扱う）
- **ハーメルンは旧バージョンのキャッシュがあると初回だけ全話を取り直す**。
  `SUB_UPDATE` の保存形式と各話 href の形が変わったため（2 回目以降は通常どおり差分取得）
- #72 で章が復活する作品では、既存の未対応挙動（章タイトルページの `［＃ここから柱］` が
  未対応で目次ラベルが章名でなく作品名になる — `docs/code-audit-followups.md` 項目 20）が
  見えるようになる
- `.NET` ポート `JavaComparisonTests` 5/5 PASS を維持。**同一設定を明示して変換すれば**
  出力 EPUB は byte 単位で従来と一致する（比較テストは全設定を固定した ini を使うため、
  上記のキー不在時の既定値変更・同梱 ini 拡充の影響を受けない）

### その他

- **GUI が終了時に設定を保存するようになった** (#69)。JDK21 対応（`26057a0`）で `finalize()` を
  `saveProperties()` にリネームした際、`windowClosing` の呼び出しが `Object.finalize()` のまま
  残っていた既存バグ。副作用としてウィンドウ位置・サイズ・出力先履歴なども再び保存される
- 小数点がカンマの環境（de / fr など）で数値書式が壊れ、`PageMargin` が 5 要素に分裂して
  設定画面が `ArrayIndexOutOfBoundsException` になる既存バグを修正
  （`Locale.ROOT` 固定 + 欄数の上限ガード）
- プレビューの状態を変える POST に `Origin` / `Sec-Fetch-Site` 検査を追加（CSRF 対策、#62）
- スタック PR でも CI が起動するよう `pull_request.branches` を `'**'` に (#61)
- jsoup 1.22.2 → 1.23.1 (#56)
- README・サイトにアプリ画面／プレビュー画面のスクリーンショットを追加 (#71)

---

## バージョン: 1.4.0-jdk21

**リリース日**: 2026年8月1日

### ハイライト

- **表紙（タイトルページ）の長タイトル自動調整** (#50)。なろう系の長いタイトル（最大 100 文字）が
  表紙に収まらず著者名と重なる問題を修正。タイトルの表示文字数（ルビ・タグを除く）に応じて
  font-size を 6 段階で自動調整し、46 文字以上では余白スペーサー抑制とタイトル領域の
  `min-height` 化で重なりを防止する。閾値は なろう / カクヨム / ハーメルン 2,496 作品の
  実測分布に基づく（設計書: `docs/title-page-autofit-plan.md`）
- **CLI の `-url` でアーカイブ URL（zip / txtz / rar）を直接指定可能に** (#51、監査 #16)。
  従来は GUI / ドラッグ&ドロップのみ対応だった青空文庫 zip などの直接変換が CLI でも動作する。
  ダウンロード処理は GUI と共通化（`ArchiveUrlUtils`）

### 互換性

- 表示文字数 45 文字以下のタイトルでは title.xhtml は byte 単位で不変（narou.rb 連携に影響なし）。
  ルビ・外字を含むタイトルは判定基準の改善（タグ込み長 → 表示文字数）により過剰縮小が解消される
- `.NET` ポートへ同時移植済み（aozoraepub3-dotnet#29、`JavaComparisonTests` 5/5 PASS 維持。
  n9623lp の reference.epub は新出力で再生成）

### その他

- ドキュメントの `-interval` デフォルト値の記載を実装に合わせて修正（0.5 秒 → 1.0 秒）
- 壊れていた `tools/epubcheck-5.2.0.jar`（9 バイトの Not Found 残骸、未追跡）を削除し、
  手順書 §6.11 の記述を更新

---

## バージョン: 1.3.7-jdk21

**リリース日**: 2026年7月25日

### ハイライト

- **`src/` 全体のコード監査（監査 #1〜#17）にもとづくバグ修正リリース**。パストラバーサル、リソースリーク、例外の握り潰し、Windows のファイル名制約、出典 URL の破損などを一括で修正しました
- **EPUB の出力に失敗したとき、CLI が終了コード `1` を返すようになります**（後述の Breaking changes）。narou.rb 連携では、これまで「成功」として取り込まれていた破損 EPUB が失敗として扱われるようになります
- 出力 EPUB の構造は変わりません（`.NET` ポート `JavaComparisonTests` 5/5 PASS で byte-identical を維持）

### ⚠️ Breaking changes

- **CLI の終了コードが変わります。** 変換に失敗した場合、これまで常に `0` を返していたのが `1` を返すようになります (#39)。

  | 終了コード | 意味 |
  |---|---|
  | `0` | すべての入力ファイルの変換に成功（`-h` / `--help` も `0`） |
  | `1` | 1 つ以上の入力ファイルで変換に失敗した／`-i` の INI ファイル・`-d` の出力先ディレクトリ・入力ファイルが存在しない／オプションの指定が不正、または入力ファイルも `-url` も指定されていない |

  引数なしで実行した場合は GUI が起動するため、上表の対象外です。

  **背景**: `Epub3Writer.write()` が全例外を握り潰していたため、ディスクの空き容量不足や出力先の書き込み権限がないなどで EPUB の書き出しが途中で中断しても「変換完了」と報告され、**壊れた `.epub` が成功として残っていました**。

  **narou.rb 連携への影響**: narou.rb は終了コードで成否を判定するため、**これまで「成功」として取り込まれていた破損 EPUB が失敗として扱われる**ようになります。これは意図した変更です。誤検知（従来どおり成功すべき変換が失敗扱いになること）が起きないことは、画像デコード失敗・表紙取得失敗・不審アーカイブエントリなどが従来どおり局所的に握り潰されることを確認して担保しています。

  なお narou.rb（3.9.1 時点）は「AozoraEpub3 はエラーでも終了コード 0 を返す」前提で実装されており、0 以外を「Java が動かなかった」と解釈するため、失敗時に **`JavaがインストールされていないかAozoraEpub3実行時にエラーが発生しました` という実態と食い違うメッセージ**を表示します。真の原因は同メッセージの直前に出力される AozoraEpub3 のログ（`エラーが発生しました : ...`）で確認できます。詳細は [docs/narou-setup.md](docs/narou-setup.md) を参照してください。

- **変換に失敗した場合、出力途中の壊れた `.epub` は削除されます**（従来は残っていました）。変換をキャンセルした場合も同様です。

### バグ修正

- **EPUB 出力失敗の握り潰しを修正 (#39)**: 上記のとおり、失敗を呼び出し側へ伝播するようにしました。あわせて、変換失敗・キャンセル時に入力 Reader が閉じられず Windows で入力ファイルがロックされ続ける問題も修正しています。
- **Web 小説ページ由来の href / img src によるパストラバーサルを修正 (#38)**: 取得先サイトが `..` を含むリンクを返した場合に、キャッシュ・出力ディレクトリ外へファイルを書き込めてしまう問題を修正しました。
- **ImageIO のリソースリーク・ネットワークタイムアウト・URL サニタイズを修正 (#40)**: 画像 1 枚ごとに一時ファイルが滞留する問題、応答しないサーバで変換スレッドが永久にブロックする問題、サニタイズ用 regex が文字クラス欠落で実質無効だった問題を修正しました。
- **パスなし URL・画像のみアーカイブでの例外を修正 (#41)**: `https://example.com`（パスなし）の入力で `begin 0, end -1` という意味不明なエラーになる問題、画像だけを含む zip / cbz の変換が NPE になる問題、カクヨム等のフォールバック時にログが文字化けする問題を修正しました。
- **Windows 予約デバイス名でキャッシュが毎回無効化される問題を修正 (#42)**: URL 由来のパスセグメントが `NUL` などになると、キャッシュの書き込みは成功するのに存在しない扱いになり、毎回再ダウンロードを繰り返して最終的に章が欠落する問題を修正しました。
- **特定のパスを含む URL で変換全体が中断する問題を修正 (#44)**: URL 由来のパスセグメントに制御文字が含まれる、または末尾が半角スペースになると、Windows で `InvalidPathException` が発生し、本来は該当する章 1 件のスキップで済むはずの状況で変換全体が止まっていた問題を修正しました。
- **`:` や制御文字を含む青空文庫 zip の直接ダウンロードが失敗する問題を修正 (#45)**: ダウンロードファイル名のサニタイズが `:` と制御文字を素通りさせていたため、Windows のファイル API が受け付けられない名前になって保存に失敗していた問題を修正しました。
- **タイトルページの外字画像でリンク切れの `<img>` が出力される問題を修正 (#49)**: 表題行に画像外字の注記があり、参照先の画像ファイルが存在しない場合に `<img src="null"/>` が出力され、`epubcheck` が `RSC-007` で reject する EPUB になっていた問題を修正しました。本文側は以前から画像が解決できないときに `<img>` を出力しない扱いで、タイトルページ側だけが揃っていませんでした。**v1.3.6 以前にも存在する既存バグ**です。
- **出典 URL のリンクが機能しない問題を修正 (#47)**: URL から変換した EPUB の末尾に付く出典リンクの `href` に青空文庫の注記記法（`［＃縦中横］` など）が混入し、リンクを開けなくなっていた問題を修正しました。**v1.3.6 以前にも存在する既存バグ**で、`epubcheck` は通過するため生成物の目視確認で発見されたものです。

### ドキュメント

- **`AozoraEpub3.exe` 起動時の SmartScreen 警告について、回避手順を追加しました。** 「Windows によって PC が保護されました」は、(1) exe が未署名であることと (2) ダウンロードした ZIP の Mark of the Web がエクスプローラでの展開時に中のファイルへ伝播することの、2 条件が重なって表示されます。**ZIP を展開する前に**右クリック →プロパティ →「許可する」にチェックを入れる（または `Unblock-File`）と、警告自体が出なくなります。詳細は README / [使い方ガイド](https://aozoraepub3-jdk21.github.io/AozoraEpub3-JDK21/usage.html) を参照してください
- CLI の終了コードの説明を README / 使い方ガイド / narou.rb 導入ガイド（各 ja/en）に追加しました (#43)

### 依存ライブラリ

- jsoup 1.22.2 / slf4j 2.0.17 / ben-manes versions plugin 0.54.0 に更新 (#37)

---

## バージョン: 1.3.6-jdk21

**リリース日**: 2026年5月1日

### ハイライト

- **JDK 26 完全対応**: ビルド・全テスト実行・GUI 起動を JDK 26 (2026-03-17 GA) で CI 検証済
- **内部コード現代化**: SLF4J ロガー、`java.nio.file.Path`、`java.time`、空 catch 監査
- 配布物は **Java 21 ターゲットビルド** (class file version 65) のまま — JDK 21 LTS から JDK 26 まで全環境で動作します

### JDK 26 対応の内訳

| 項目 | PR | 対応内容 |
|------|----|----------|
| JEP 504 (`JApplet` 削除) | v1.3.5 で完了 | `extends JApplet` → `extends JPanel` に変更 |
| JDK 26 toolchain での `compileJava` 検証 | #29 | `build.gradle` に `-PjavaToolchainVersion=<NN>` を導入。CI matrix に JDK 26 を追加 |
| JDK 26 での全テスト実行 | #36 | JUnit 4 の test detection が JDK 26 のリフレクション挙動変化で `InvalidTestClassError` を発生させていた問題を `build.gradle` の test exclude (`AozoraFullFlowTest` / `AozoraRealTest` / `VelocityTestUtils` / `**/*$*.class`) で解消 |
| JDK 26 での GUI smoke test | #36 | CI で fat-jar を JDK 26 ランタイムで起動し、Xvfb 経由で Swing GUI が立ち上がることを確認 |

### 内部コード現代化

| 領域 | PR | 変更点 |
|------|----|--------|
| `Vector` → `ArrayList` | #17 | 24 ファイルでレガシーコレクションを撤去 |
| `java.io.File` → `java.nio.file.Path` | #19〜#27 | 14 ファイルを内部 Path 化（公開 API は File 維持で後方互換） |
| `java.time` 移行 | #18 | `Date` / `SimpleDateFormat` を撤去（後述のロケールバグ修正を含む） |
| SLF4J 実利用化 | #12 / #14 / #15 / #16 | 11 ファイルで `e.printStackTrace()` をロガー呼び出しに置換 |
| 空 catch 監査 | #30〜#35 | 11 ファイル / 133 occ の `catch (...) {}` に意図コメントを付与（grep 「意図的:」で追跡可能） |

### バグ修正

- **`dcterms:modified` の EPUB 3.3 仕様違反 (#18)**: タイ仏暦・日本和暦などのロケールで `Date` + `SimpleDateFormat` の組み合わせが非グレゴリオ歴の年号を出力し、epubcheck が `OPF-054` で reject していた問題を修正。`Instant.now()` + `DateTimeFormatter.ISO_INSTANT` で常に UTC グレゴリオ歴の `YYYY-MM-DDThh:mm:ssZ` 形式を出力するようにした
- **青空文庫 URL から CLI -url で本文取得できなかった問題を修正**: extract.txt のディレクトリ名が `web/aozora.gr.jp/` で、実際の URL ホスト名 `www.aozora.gr.jp` と不一致 → サイト定義が読み込まれていなかった。`web/www.aozora.gr.jp/` に rename して解決。さらに同 extract.txt の `HREF`/`SUBTITLE_LIST`/`SUB_UPDATE` が「単話 HTML 完結」形式の青空文庫作品では誤動作していた (他作品リンクを各話と誤認識して本文 placeholder のみ出力) ためコメントアウトし、`docToAozoraText` 経路で `.main_text` から本文を直接抽出するようにした

### Breaking changes

- `BookInfo` 等の公開フィールドで `Vector` → `ArrayList` に変更 (#17)。**バイナリ互換性が崩れる**ため、AozoraEpub3 を外部ライブラリとして利用しているコードは再コンパイルが必要

### `InterruptedException` の扱い変更

- `WebAozoraConverter` の sleep ループで `InterruptedException` を silent swallow していた箇所を、`Thread.currentThread().interrupt()` + `return null` に変更 (#35)。`ExecutorService.shutdownNow()` 等の外部 interrupt が来た場合に正しくダウンロードを終了します

### 動作環境

- **最低要件**: Java 21 以降
- **推奨**: Java 25 LTS（Temurin による JDK 26 のバイナリ配布が出揃い次第、推奨を更新予定）
- **互換性**: Java 21 LTS / Java 25 LTS で動作確認済（JDK 26 でもビルド・全テスト・GUI smoke test PASS）

### 検証結果

```
Build (JDK 21 toolchain): ✓ BUILD SUCCESSFUL
Tests:
  - JDK 21: ✓ 182 tests, 0 failures, 0 errors, 9 skipped
  - JDK 26: ✓ 全テスト PASS (workflow_dispatch CI)
GUI smoke test:
  - JDK 21: ✓ Xvfb 経由で起動確認
  - JDK 26: ✓ Xvfb 経由で起動確認
.NET ポート JavaComparisonTests: ✓ 5/5 PASS (byte-identical 出力維持)
```

---

## バージョン: 1.3.1-jdk21

**リリース日**: 2026年3月7日

### バグ修正

- **サブタイトル行の漢数字変換（完全修正）**: WebAozoraConverter の printText() 内でサブタイトルの数字が漢数字に変換される問題を修正。AozoraTextFinalizer 側の判定順序も修正し、見出し行の数字を確実に全角数字のまま保持するようにした

### ドキュメント

- macOS/Linux でビルド後に JAR をルートディレクトリにコピーする手順を DEVELOPMENT.md に追加

---

## バージョン: 1.3.0-jdk21

**リリース日**: 2026年3月7日

### 新機能

- **narou.rb 互換テキスト前処理**
  - 空行圧縮 (`packBlankLine`): 連続空行を圧縮
  - 前書き・後書き検出 (`detectAndMarkAuthorComments`): 注記タグで囲む
  - 漢数字変換 (`convertNumToKanji`): 注記内・URL行・見出し行を保護しつつ変換
  - 英字全角化 (`alphabetToZenkaku`): 注記内・URL行を保護しつつ変換
  - 二分アキ + 自動字下げ改善 (`halfIndentBracketAndAutoIndent`): narou.rb 互換の50%閾値判定
  - 読了表示 (`appendEndOfBook`): 重複出力を修正
  - `NarouFormatSettings` のデフォルト値を narou.rb `ORIGINAL_SETTINGS` に統一

- **CSS・注記定義更新**
  - `vertical_font.css` を narou.rb 互換の完全版に更新（line-height, .introduction, .postscript, .half_em_space 等）
  - `chuki_tag.txt` に二分アキ・前書き・後書きの注記定義を追加

- **CLI URLオプション**
  - `-url` オプションでWeb小説URLからの直接変換をコマンドラインから実行可能
  - `-narou` オプションで narou.rb 互換フォーマット設定を適用
  - `-interval` / `-cache` オプションで取得間隔・キャッシュ制御

### バグ修正

- **サブタイトル行の漢数字変換**: 見出し行の数字が漢数字に変換される問題を修正（narou.rb 互換: 見出し行は全角数字変換のみ）
- **JSON `\/` エスケープ**: あらすじに `\/` が残る問題を修正（NarouApiClient.unescapeJson）
- **読了表示2重出力**: WebAozoraConverter と AozoraTextFinalizer の両方で出力されていた問題を修正
- **注記内の数字・英字変換**: `［＃米印、1-2-8］` 等の注記内数字が変換される問題を修正
- **URL行の変換**: URL含有行の数字・英字が変換されてリンクが壊れる問題を修正
- **GUI テキストボックス**: バージョン/Java/OS 表示を削除

### 検証結果

```
Build: ✓ BUILD SUCCESSFUL
Tests: ✓ 全テスト成功 (165テスト)
```

---

## バージョン: 1.2.14-jdk21

**リリース日**: 2026年2月28日

### バグ修正

- **初回起動時のデフォルト言語が英語になる問題を修正**
  - **根本原因**: 配布用 `AozoraEpub3.ini` に開発中の設定 `UILang=en` が残っていた。
  - 初回起動（`AozoraEpub3.ini` が存在しない状態）でも英語UIになっていた。
  - `UILang=en` を削除し、コード側のデフォルト（日本語）が使われるよう修正。

### 検証結果

```
Build: ✓ BUILD SUCCESSFUL
Tests: ✓ 全テスト成功
起動確認: ✓ 初回起動で日本語UIが表示される
```

---

## バージョン: 1.2.13-jdk21

**リリース日**: 2026年2月28日

### バグ修正

- **exe ダブルクリック起動できない問題を修正**
  - **根本原因**: v1.2.12 の i18n 対応リファクタリング中に、画面サイズ設定UI の `jTextDispH = new JTextField("800")` および `panel.add(label)` が誤って削除されていた。
  - GUI 起動時に `NullPointerException` が発生し、exe をダブルクリックしても起動しない状態だった。
  - 対象ファイル: `AozoraEpub3Applet.java`（画面サイズ入力フィールド初期化コードを復元）

### 検証結果

```
Build: ✓ BUILD SUCCESSFUL
Tests: ✓ 全テスト成功
GUI起動: ✓ exe ダブルクリックで正常起動確認
```

---

## バージョン: 1.2.12-jdk21

**リリース日**: 2026年2月28日

### 新機能

- **多言語対応 UI（日本語 / 英語）**
  - GUIの全タブ（詳細設定・目次・スタイル・Web・ログ）のUI文字列を `I18n.t()` に置換
  - `narou.rb互換 フォーマット設定` ダイアログも完全対応
  - デフォルト言語: 日本語（`Locale.forLanguageTag("ja")`）
  - 英語UIへの切り替えは `AozoraEpub3.ini` の `ui_lang=en` で変更可能
  - 対象ファイル:
    - `AozoraEpub3Applet.java`: ~200箇所を `I18n.t()` に置換
    - `NarouFormatSettingsDialog.java`: 全文字列を `I18n.t()` に置換
    - `messages_ja.properties` / `messages_en.properties`: 約340キーを追加

### 検証結果

```
Build: ✓ BUILD SUCCESSFUL
Tests: ✓ 全テスト成功 (154テスト)
```

---

## バージョン: 1.2.11-jdk21

**リリース日**: 2026年2月28日

### バグ修正

- **Web小説 各話欠落の修正**（小説家になろう）
  - **根本原因**: 前書きdivが `class="js-novel-text p-novel__text p-novel__text--preface"` を持つため、`CONTENT_ARTICLE` セレクターが前書きを本文として誤マッチし、本文がスキップされていた。
  - **修正1**: `ncode.syosetu.com/extract.txt` の `CONTENT_ARTICLE` セレクターに `:not(.p-novel__text--preface)` を追加して前書きを除外
  - **修正2**: `ExtractInfo.java` のセレクターパース処理を改修 — `:not()` 等のCSS疑似クラスを含むセレクター文字列でも末尾の数値インデックス（`:0` 等）を正しく分離して解析できるよう対応
  - **修正3**: キャッシュファイルに本文が存在しない話を検出した場合、自動でキャッシュ削除・再ダウンロードするフォールバック処理を追加
  - 実機確認: `n7673ff` ep29/ep31 が正常取得できることを確認（15KB超の本文）

### 検証結果

```
Build: ✓ BUILD SUCCESSFUL
Tests: ✓ 全テスト成功 (154テスト)
EPUBCheck 5.1.0: ✓ 0 fatals / 0 errors / 0 warnings / 0 infos
実機テスト: ✓ n7673ff 332話 正常変換確認
```

---

## バージョン: 1.2.10-jdk21

**リリース日**: 2026年2月28日

### 新機能

- **Web小説 各話の更新日時・初回公開日 GUI ON/OFF 機能**
  - 「narou.rb互換 フォーマット設定」ダイアログに2つのチェックボックスを追加
    - **更新日時を各話に表示** (`show_post_date`): 各話の最終更新日時を本文末に表示（初期値: OFF）
    - **初回公開日を各話に表示** (`show_publish_date`): 改稿済の話の初回公開日を更新日時と別行で表示（初期値: OFF）
  - 表示形式: `2024/01/10 公開` / `2024/01/20 更新` の形式で別行出力
  - `setting_narourb.ini` に保存、narou.rb `setting.ini` とキー互換
  - 小説家になろう (`ncode.syosetu.com` / `novel18.syosetu.com`) の `extract.txt` を更新:
    - `CONTENT_UPDATE_LIST`: 更新日時のみ抽出するよう正規表現を修正
    - `CONTENT_PUBLISH_LIST` を新規追加（`span[title]` 属性から初回公開日を取得）

---

## バージョン: 1.2.7-jdk21

**リリース日**: 2026年2月26日

### 新機能

- **カクヨム (kakuyomu.jp) 対応**
  - `web/kakuyomu.jp/extract.txt` を新規追加
  - Next.js SPA の `__NEXT_DATA__` JSON からエピソード全件取得（HTML の `<a>` タグが数件しかない問題を解決）
  - 章構造（`CONTENT_CHAPTER`）: `TableOfContentsChapter` エントリから章タイトルを抽出して `［＃大見出し］` を出力
  - 更新日時差分（`SUB_UPDATE`）: `publishedAt` を使って変更なしエピソードをスキップ
  - 傍点（`<em class="emphasisDots">`）: `［＃傍点］…［＃傍点終わり］` に変換
  - あらすじ: `__NEXT_DATA__` JSON の `introduction` フィールドから取得、`\n` エスケープを改行に復元
  - 実機確認: 154話作品でタイトル・著者・全話・本文・話タイトル取得確認済み

- **ハーメルン (syosetu.org) extract.txt 更新**
  - `<font>` タグ廃止に対応 → `span[itemprop]` / `#honbun` セレクタに変更
  - TITLE: `span[itemprop=name]` + サイト名除去 regex
  - AUTHOR: `span[itemprop=author]`
  - CONTENT_SUBTITLE: `span[style]:1`（`.ss` 内2番目スタイル付き `<span>`）
  - CONTENT_ARTICLE: `#honbun`（本文要素が集約されているため正確に取得可能）

### セキュリティ修正

- **CodeQL アラート全件対応**
  - `java/path-injection` 30件: `getCanonicalFile()` + `startsWith()` でパス検証を強化
  - `java/polynomial-redos` (#8–11, #65): `BookInfo.java` の正規表現を possessive quantifier に修正
  - `java/command-line-injection` (#12, #67): kindlegen 実行ファイル名検証 + `getCanonicalPath()` 正規化
  - `java/partial-path-traversal` (#13): `isCacheFile()` の `startsWith` 修正

### その他

- 閉鎖・休眠サイト (dNoVeLs / NEWVEL-LIBRARY / Arcadia) の `extract.txt` に警告コメントを追記
- `test_data/` 自動生成ファイル・`.claude/settings.local.json` を `.gitignore` に追加

### 検証結果

```
Build: ✓ BUILD SUCCESSFUL
Tests: ✓ 全テスト成功
Distributions: ✓ ZIP/TAR.GZ 生成済み
カクヨム実機: ✓ 154話取得・変換確認
```

---

## バージョン: 1.2.6-jdk21（安定リリース）

**リリース日**: 2026年1月24日

### 変更概要

- 依存ライブラリを最新安定版へ更新
  - commons-cli: 1.11.0
  - commons-collections4: 4.5.0
  - commons-compress: 1.28.0
  - commons-lang3: 3.20.0
  - jsoup: 1.22.1
  - junrar: 7.5.7
  - batik-transcoder: 1.19
- CLIヘルプAPIの非推奨警告を解消
  - `org.apache.commons.cli.HelpFormatter` → `org.apache.commons.cli.help.HelpFormatter`
  - `printHelp(syntax, header, options, footer, autoUsage)` へ移行
- 依存更新レポート用ワークフローを追加（`dependencyUpdates` 実行結果をArtifacts保存）

### 検証結果

```
Build: ✓ BUILD SUCCESSFUL
Tests: ✓ 全テスト成功
Distributions: ✓ ZIP/TAR 生成済み
```

## バージョン: 1.2.5-jdk21

**リリース日**: 2025年12月20日

### パフォーマンス最適化 🚀

- **アーカイブスキャンの高速化**: 大容量zip/rarファイルの変換速度を大幅に改善
  - アーカイブスキャン回数を **4回→1回に削減**（75%削減）
  - `ArchiveCache`: アーカイブ内容をメモリにキャッシュして再利用
  - `ArchiveScanner`: zip/rarを1回のパスで効率的にスキャン
  - 2GBアーカイブでもキャッシュは10-20MB程度の省メモリ設計
  - 変換完了後に自動的にキャッシュを解放

**効果**: 大容量アーカイブ（100MB以上）や多数の画像を含むファイルの変換が大幅に高速化されます。

### コード品質向上

- **リファクタリング**: 大規模な `AozoraEpub3.java` を機能別に分割
  - `OutputNamer`: ファイル名生成ロジックを抽出（50行）
  - `WriterConfigurator`: Writer設定を集約（110行）
  - `ArchiveTextExtractor`: アーカイブ処理を統一（90行）
  - メインクラス: 645行 → 450行（**200行削減**）
  - 保守性・テスタビリティが向上

- **テスト追加**: 単体テストを追加
  - `OutputNamerTest`: ファイル名生成ロジックのテスト（4テスト）
  - 全93テスト: ✓ 全て成功

- **コードクリーンアップ**: 未使用のインポートとメソッドを削除
  - コンパイルワーニング: 0

### ドキュメント改善

- **開発ドキュメント拡充**:
  - `notes/refactor-plan.md`: リファクタリング計画の詳細
  - `notes/archive-cache-optimization.md`: パフォーマンス最適化の技術詳細
  - `DEVELOPMENT.md`: コード構造とリファクタリングセクションを追加
  - `README.md`: 高速変換機能を特徴に追加

### 検証結果

```
GitHub Actions: ✓ All checks passing
Build: ✓ BUILD SUCCESSFUL
Tests: ✓ 93 tests passed (0 failures)
Code Quality: ✓ 0 warnings, 0 errors
```

---

## バージョン: 1.2.3-jdk21

**リリース日**: 2025年12月18日

### セキュリティ修正

- **ZipSlip脆弱性対策**: ZIP抽出時のパストラバーサル攻撃を防止
  - `ImageInfoReader.java`: `sanitizeArchiveEntryName()`メソッドを追加
  - アーカイブエントリの絶対パス・相対パストラバーサル（`..`）・不正シンボルを検出・排除
  - GitHub Code Scanning Alert #2を解決

- **GitHub Actions権限設定**: GITHUB_TOKENの最小権限化
  - `.github/workflows/ci.yml`: `permissions: {contents: read}`を明示的に設定
  - セキュリティベストプラクティスに準拠
  - GitHub Code Scanning Alert #1を解決

### 改善

- **ドキュメント更新**: narou.rb のGitHubリンク修正
  - README.md: `https://github.com/whiteleaf7/narou` に更新
  
- **Copilot指示ドキュメント更新**: コミットメッセージの言語規約を明記
  - `.github/copilot-instructions.md`: 「全てのコミットメッセージは日本語」を追記

### 検証結果

```
GitHub Code Scanning: ✓ 0 alerts (Alert #1, #2を完全解決)
Build: ✓ BUILD SUCCESSFUL
Tests: ✓ All tests passed
```

---

## バージョン: 1.2.2-jdk21

**リリース日**: 2025年12月17日

### 新機能

- **EPUB 3.3 対応**: EPUBCheck 5.3.0にアップグレード
  - EPUB 3.3仕様準拠（W3C Recommendation 2025年3月27日）
  - 後方互換性維持（EPUB 3.2と同じpackage version="3.0"属性を使用）

### バグ修正

- **CLIモード引数バグ修正**: コマンドライン引数を指定した際にGUIが起動する問題を修正
  - `MANIFEST.MF`のMain-Classを`AozoraEpub3`に変更
  - `AozoraEpub3Applet.main()`に引数チェックを追加し、引数がある場合は`AozoraEpub3.main()`に委譲

### 改善

- **ビルドプロセス改善**: 配布タスクの信頼性向上
  - `createLauncher`タスクでdistributionsディレクトリを自動作成
  - copilot-instructions.mdにビルドタスク詳細を文書化（jar/dist/distZip の違いを明記）

### 技術的変更

- EPUBCheck: 5.2.0 → 5.3.0
- バージョン表記を1.2.2に統一（`build.gradle`, `AozoraEpub3.java`, `AozoraEpub3Applet.java`）

### 検証結果

```
EPUBCheck 5.3.0
Validating using EPUB 3.3 rules.
No errors or warnings detected.
Messages: 0 fatals / 0 errors / 0 warnings / 0 infos
```

---

## バージョン: 1.2.1-jdk21

**リリース日**: 2025年12月17日

### 新機能

- **GUI機能の復活**: オリジナルのhmdev版GUIをJDK21対応で復活
  - `AozoraEpub3Applet.java`をメインクラスとして直接起動可能に
  - ドラッグ&ドロップによるファイル指定
  - 各種EPUB設定をGUIから簡単に変更可能

- **Windows向けランチャーバッチファイル**:
  - `AozoraEpub3.bat`
  - `AozoraEpub3.bat` (英語版、ASCII)
  - Windows 11の.jar ダブルクリック問題を回避
  - javaw.exeを使用してコンソールなしで起動

- **Unix/Linux/macOS向けシェルスクリプト**:
  - `AozoraEpub3.sh` (実行権限付き)
  - クロスプラットフォーム対応

### 改善

- FAT-JAR配布版に起動用バッチ/シェルスクリプトを同梱
- GUI全体のフォントをOS別日本語フォントに統一（Windows: Yu Gothic UI/Meiryo優先）
  - 英語OS環境でも日本語字形の違和感を軽減
  - テキスト領域のフォントサイズを13ptに改善（可読性向上）
- README.mdとDEVELOPMENT.mdにGUI起動方法、開発者向けビルド手順を詳細に記載
- 配布はFAT版のみに統一（シンプルで配布しやすい構成）

### 技術的変更

- `AozoraEpub3Launcher.java`を削除し、アーキテクチャを簡素化
- `application.mainClass`を`AozoraEpub3Applet`に変更
- Gradleビルドにランチャー生成タスク (`createLauncher`) を追加

---

## バージョン: 1.2.0-jdk21

**リリース日**: 2025年12月16日

## 概要

AozoraEpub3 を Java 21 と最新ツールチェーンに対応させました。

## 主な更新

- **Gradle 9.2.1 / Java 21** でビルド・テスト
- **依存ライブラリ**: Velocity 2.4.1、JSoup 1.18.1、Apache Commons 各種を最新化
- **EPUB テンプレート**: 外字フォント対応、Kindle・iOS レイアウト改善
- **Web 機能**: レート制限 1500ms（最小 1000ms）、narou.rb 連携対応
- **セキュリティ**: Git 匿名著者設定、.gitignore 強化
- **ドキュメント**: README・DEVELOPMENT.md・ライセンス更新

## テスト・互換性

- JUnit 4.13.2 全 5 テスト合格 ✓
- 既存の入力・プリセットと完全互換 ✓
- EPUB 3.2・電書協ガイド対応 ✓

## 既知の問題

- **iPhone Kindle**: 縦書きタイトルページのレイアウトが画面比率で変動する場合あり
- **ncode.syosetu.com**: HTML 構造が変わった場合、セレクタ更新が必要な可能性

## インストール

[GitHub Releases](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases) から以下をダウンロード：
- **Windows**: `AozoraEpub3-1.2.0-jdk21.zip`
- **Linux/macOS**: `AozoraEpub3-1.2.0-jdk21.tar`

## 謝辞

- **オリジナル作成者**: hmdev
- **本プロジェクト**: AozoraEpub3-JDK21 チーム
- **連携**: narou.rb プロジェクト

---

詳細は [README.md](README.md) / [DEVELOPMENT.md](DEVELOPMENT.md) を参照してください。
