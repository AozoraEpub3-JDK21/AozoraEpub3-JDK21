---
layout: default
lang: ja
title: 使い方 - AozoraEpub3-JDK21
description: AozoraEpub3-JDK21の使い方ガイドです。GUIでのファイル選択・デバイスプリセット設定・変換実行、コマンドライン（CLI）の全オプション、Kobo・Kindle向けプリセット、縦書き・ルビ・外字の変換設定、Velocityテンプレートのカスタマイズ方法、よくあるトラブルシューティングを解説します。
---

<div style="text-align: right; margin-bottom: 1em;">
  <a href="en/usage.html">🌐 English</a>
</div>

<nav style="background: #f6f8fa; padding: 1em; margin-bottom: 2em; border-radius: 6px;">
  <strong>📚 ドキュメント:</strong>
  <a href="./">ホーム</a> | 
  <a href="usage.html">使い方</a> | 
  <a href="narou-setup.html">narou.rb</a> |
  <a href="narou-rs-setup.html">narou.rs</a> |
  <a href="development.html">開発者向け</a> | 
  <a href="epub33-ja.html">EPUB 3.3準拠</a> |
  <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a>
</nav>

## 使い方ガイド

## 目次

- [GUI での使い方](#gui-での使い方)
- [主な設定項目](#主な設定項目)
- [コマンドライン実行](#コマンドライン実行)
- [テンプレートのカスタマイズ](#テンプレートのカスタマイズ)
- [トラブルシューティング](#トラブルシューティング)

---

## GUI での使い方

### 基本的な流れ

1. **アプリケーション起動**
   - Windows: `AozoraEpub3.exe` をダブルクリック（推奨）
   - Unix/Linux/macOS: `AozoraEpub3.sh` を実行
   - または: `java -jar AozoraEpub3.jar`
   
2. **ファイル指定**
   - 変換したい青空文庫テキストファイル（`.txt` または `.zip`）をドラッグ&ドロップ
   - または「ファイル選択」から開く
   
3. **設定調整**（オプション）
   - 表題・著者名の抽出方法
   - 表紙画像の指定
   - 縦書き/横書きの選択
   - 出力形式（.epub / .kepub.epub など）
   
4. **変換実行**
   - 変換ボタンをクリック
   - 完了後、入力ファイルと同じフォルダに EPUB ファイルが生成されます

### Web小説サイトから直接変換

Web小説サイト（ニコニコ小説、小説家になろう など）のURLまたはURLショートカット（`.url`）をドラッグ&ドロップで取得・変換できます。（`web/` 以下に定義ファイルがあるサイトのみ）

**narou.rb互換 フォーマット設定**: GUIメニューから「Web小説設定」を開くと以下の項目を設定できます:

| 設定項目 | INIキー | 説明 | 初期値 |
|---------|---------|------|--------|
| 更新日時を各話に表示 | `show_post_date` | 各話の最終更新日時を本文末に表示 | OFF |
| 初回公開日を各話に表示 | `show_publish_date` | 改稿済の話の初回公開日を表示（更新日時と別行） | OFF |
| 前書き・後書きの自動検出 | `enable_author_comments` | `*44`/`*48` 個パターンで前書き・後書きを検出 | ON |
| 自動行頭字下げ | `enable_auto_indent` | 行頭の字下げを自動挿入 | ON |
| 改ページ直後の見出し化 | `enable_enchant_midashi` | 改ページ後の最初の行を見出しとして処理 | ON |
| 空行圧縮 | `enable_pack_blank_line` | 連続空行を圧縮 | ON |
| 漢数字変換 | `enable_convert_num_to_kanji` | アラビア数字を漢数字に変換 | ON |
| 英字全角化 | `enable_alphabet_to_zenkaku` | 短い英単語を全角に変換 | ON |
| 読了表示 | `enable_display_end_of_book` | 末尾に読了マークを表示 | ON |
| かぎ括弧内自動連結 | `enable_auto_join_in_brackets` | かぎ括弧内の行を自動連結 | ON |
| 行末読点自動連結 | `enable_auto_join_line` | 読点で終わる行を次行と連結 | ON |

設定は `setting_narourb.ini` に保存され、narou.rb の `setting.ini` とキー互換があります。

**注意事項:**
- **レート制限**: サイトへの負荷を避けるため、各話取得時に1.5秒の間隔を設けています
- **HTML構造変更**: サイトのレイアウト変更により動作しない場合があります（特に「小説家になろう」は構造が変更されており、現在未対応の可能性があります）
- **長編作品**: 話数が多い作品は完了まで時間がかかります（100話で約3分）
- **推奨**: Web取得機能は実験的機能です。確実に変換したい場合は、手動でテキストをダウンロードして変換することをお勧めします

---

## 主な設定項目

### 表題設定

- **本文内タイトル利用**：本文内からタイトルと著者名を抽出
- **ファイル名優先**：`[著作者名] 表題.epub` 形式のファイル名から取得

### 表紙

- **先頭の挿絵**：最初の画像を表紙に設定
- **ファイル名と同じ画像**：入力ファイル名と同じ画像ファイル（`.png` / `.jpg`）を表紙に設定
- **カスタム指定**：任意の画像ファイルまたはURLを指定

### ページ出力

- **表紙ページ**：ePubの先頭に表紙を追加
- **表題ページ**：タイトル・著者名ページを出力
- **目次ページ**：目次を生成して出力

### ファイル形式

| 拡張子 | 対応デバイス | 説明 |
|--------|------------|------|
| `.epub` | 標準 | 標準的な EPUB 3.3 準拠形式（EPUB 3.2後方互換） |
| `.kepub.epub` | Kobo | Kobo向け拡張形式 |
| `.fxl.kepub.epub` | Kobo | Kobo固定レイアウト用 |
| `.mobi` | Kindle | Kindle形式（kindlegenjの別途インストール必要） |

### 画像処理

- **挿絵除外**：テキスト内の挿絵を含めない
- **画像縮小**：端末のサイズ制限に合わせて縮小
- **余白除去**：画像の不要な余白を自動削除
- **回転対応**：端末の縦横比に合わせて自動回転

### 詳細設定

- **縦書き/横書き**：本文の方向を指定
- **強制改ページ**：長いファイルを複数に分割（Reader等で処理を軽くします）
- **自動縦中横**：半角数字と記号を縦に並べて表示
- **行の高さ/文字サイズ**：レイアウトを調整

---

## 変換時の注意

### テキスト修正が必要な場合

変換時のログに表示される以下の内容は、元のテキストを修正することをお勧めします：

- **コメントエラー**：不正な注記構文
- **外字変換エラー**：対応していない外字コード
- **仕様外の注記**

### 外字（がいじ）の取り扱い

青空文庫の注記仕様で定義された外字は自動変換します：
```
※［＃「字名」、U+XXXX］  → UTF-8に変換
※［＃「字名」、第X水準X-XX-XX］  → UTF-8に変換（対応表利用）
```

対応コードが無い外字は代替文字で出力します。

### 4バイト文字について

Koboなど一部の端末では、4バイト文字（絵文字など）が行内で表示されない制限があります。設定で「4バイト文字変換」を無効にすると、4バイト文字を代替文字「〓」で表示し、小書きで元の字を注記として表示します。

---

## コマンドライン実行

GUIを起動せずにコマンドラインで直接変換実行するには、入力ファイルを引数として指定します。

### 基本的な使い方

```bash
## GUI起動（引数なし）
java -jar AozoraEpub3.jar

## 入力ファイルを指定（CLI実行）
java -jar AozoraEpub3.jar [オプション] 入力ファイル
```

### 使い分け

| 実行方式 | 用途 | コマンド |
|---------|------|---------|
| **GUI** | 対話的な操作（推奨） | `java -jar AozoraEpub3.jar` |
| **CLI** | バッチ処理・スクリプト化 | `java -jar AozoraEpub3.jar -d out input.txt` |

### 主なオプション

| オプション | 説明 | 例 |
|----------|------|-----|
| `-h, --help` | ヘルプを表示 | |
| `-i <ファイル>` | INI設定ファイルを指定 | `-i settings.ini` |
| `-enc <エンコード>` | 入力ファイルのエンコード（既定 `MS932`） | `-enc UTF-8` |
| `-t <種別>` | 本文内の表題種別（`0`:表題→著者名（既定） / `1`:著者名→表題 / `2`:表題→著者名（副題優先） / `3`:表題のみ(1行) / `4`:表題+著者名のみ(2行) / `5`:なし） | `-t 1` |
| `-tf` | 入力ファイル名を表題に利用 | |
| `-c <画像>` | 表紙画像（`0`:先頭の挿絵 / `1`:入力ファイル名と同じ画像 / ファイル名 or URL） | `-c cover.jpg` |
| `-d <パス>` | 出力先ディレクトリ | `-d ./output/` |
| `-ext <拡張子>` | 出力ファイル拡張子 | `-ext .kepub.epub` |
| `-of` | 出力ファイル名を入力ファイル名に合わせる（既定は `[著者名] 表題.epub`） | |
| `-hor` | 横書きで出力（既定は縦書き） | |
| `-device <種別>` | 端末種別を指定 | `-device kindle` |
| `-url <URL>` | Web小説URL・アーカイブURL（zip / txtz / rar）から直接変換（複数指定可） | `-url https://ncode.syosetu.com/nXXXX/` |
| `-narou` | narou.rb互換フォーマット設定を適用 | |
| `-interval <秒>` | ページ取得間隔（`-url` 指定時のみ有効、既定 1.0 秒） | `-interval 1.5` |
| `-cache <パス>` | キャッシュディレクトリ（`-url` 指定時のみ有効、既定は jar と同じ場所の `.cache`） | `-cache .cache` |
| `--preview` | 変換した EPUB を既定ブラウザでプレビュー表示 | `--preview foo.epub` |
| `--library <フォルダ>` | フォルダを本棚として開く（複数指定可、最大 8 個） | `--library ./output/` |

CLI オプションはここに挙げたものがすべてです。文字サイズ・行間・余白・画像縮小・外字・濁点フォント
などの詳細設定にコマンドライン引数はありません。GUI で設定して保存した ini か、`presets/` の
プリセットを `-i` で渡してください。

```bash
java -jar AozoraEpub3.jar -i presets/kindle_pw.ini -of -d ./output/ input.txt
```

`presets/` に同梱しているプリセット:

- `kobo__full.ini` — Kobo 最大サイズ
- `kobo_glo.ini` — Kobo Glo
- `kobo_touch.ini` — Kobo Touch
- `kindle_pw.ini` — Kindle Paperwhite
- `reader.ini` — Sony Reader
- `reader_t3.ini` — Sony Reader T3

#### ini でしか指定できない主な設定

長いファイルを複数ページ（XHTML）に分割する自動改ページ:

```ini
PageBreak=1
# ページがこのサイズ（KB）を超えたら改ページ
PageBreakSize=400
# 空行が PageBreakEmptyLine 行続き、かつページが PageBreakEmptySize (KB) を超えていたら改ページ
PageBreakEmpty=1
PageBreakEmptyLine=2
PageBreakEmptySize=300
# 章見出しがあり、かつページが PageBreakChapterSize (KB) を超えていたら改ページ
PageBreakChapter=1
PageBreakChapterSize=200
```

`*Size` はいずれも「そのきっかけで改ページし始める最小ページサイズ（KB）」です。空行や章見出しが
あっても、ページがその大きさに達するまでは分割されません。
なお、無条件改ページの閾値（`PageBreakSize`）が、有効にした `PageBreakEmptySize` /
`PageBreakChapterSize` より小さい場合、内部でそれらの最大値まで引き上げられます
（空行・章見出しでの改ページ機会を無条件分割が先取りしないようにするため）。

目次を入れ子にするかどうか:

```ini
NavNest=1
NcxNest=1
```

`0` にすると目次はフラットになります。どの見出しを目次に載せるかは `Chapter*` キー
（`ChapterH1`〜`ChapterH3`、`ChapterName` など）で決まります。

#### 設定ファイル（AozoraEpub3.ini）の探し方

CLI は次の順で設定ファイルを読みます。見つからなければ既定値（GUI の初期状態と同じ）で
動作し、その旨をログに 1 行出力します。

1. `-i <ファイル>` で明示したファイル
2. カレントディレクトリの `AozoraEpub3.ini`
3. jar と同じ場所の `AozoraEpub3.ini`（同梱の設定ファイル）

**GUI は起動時のカレントディレクトリの `AozoraEpub3.ini` を読み書きします**。
`AozoraEpub3.exe` / `AozoraEpub3.sh` やダブルクリックで起動した場合はカレントが
配布フォルダになるため、実質的に jar と同じ場所の同梱 ini が使われます。
配布フォルダの外から CLI を実行するときは 3. により、この（通常の起動方法で GUI が
読み書きしている）同梱 ini が使われます。
なお 2. により、作業ディレクトリに `AozoraEpub3.ini` という名前のファイルがあると
意図せずそちらが優先されます。どのファイルを読んだかは起動時のログ
`設定ファイルを読み込みました: <パス>` で確認できます。

### EPUB プレビュー

変換結果を実機へ転送する前にブラウザで確認できます。

```bash
# 既存の EPUB をそのまま表示（変換しない）
java -jar AozoraEpub3.jar --preview foo.epub

# 変換してから表示
java -jar AozoraEpub3.jar -of -d ./output/ --preview input.txt
```

GUI では変換完了後に「プレビュー」ボタンが有効になります。「プレビュー」タブの
「変換完了後に自動でプレビューを開く」をオンにすると、変換のたびに自動で開きます（既定はオフ）。

| 操作 | 内容 |
|------|------|
| 目次パネル（`☰` / `t` キー） | 章・見出しへジャンプ |
| `Aa` ボタン | フォント・文字サイズ・行間・上下左右の余白 |
| `◐` ボタン | テーマ切替（システム追従 / ライト / ダーク） |
| `ⓘ` ボタン | 書誌・構成・manifest 内訳・実効スタイル・CSS・埋め込みフォント |
| 本文の左右端クリック / ホイール / ← → / Space | ページ送り |
| `[` `]` | セクション送り |

本文フォントの既定は UD デジタル教科書体（無い環境では游明朝などへ順に落ちます）。
表示設定は `~/.aozoraepub3/preview-settings.json` に保存され、次回起動時に復元されます。

サーバはループバックアドレス（`127.0.0.1`、IPv6 優先環境では `::1`）のランダムポートに
URL トークン付きで待ち受けるため、外部からは接続できません。
CLI ではブラウザを閉じると自動的に終了します（Ctrl-C でも終了）。

#### 本棚

EPUB を置いているフォルダを本棚として開くと、表紙サムネイル付きの一覧から選んで表示できます。
サブフォルダも探索します。登録できるフォルダは最大 8 個です。

```bash
# 本棚だけを開く（入力ファイルを省略）
java -jar AozoraEpub3.jar --library ./output/

# 複数の本棚を開く
java -jar AozoraEpub3.jar --library ./output/ --library ./novels/

# 変換してプレビューしつつ、本棚も一緒に開く
java -jar AozoraEpub3.jar -of -d ./output/ --library ./output/ input.txt
```

GUI では「プレビュー」タブでフォルダを追加し、「本棚を開く」で表示します。
登録したフォルダは `AozoraEpub3.ini` に保存されます。

> 画面サイズ・フォントの近似表示です。Kindle / Kobo / Apple Books は独自の描画エンジンを
> 使うため、実機とまったく同じ見た目にはなりません。

### 実行例

```bash
## 標準的な変換
java -jar AozoraEpub3.jar input.txt

## 出力先を指定
java -jar AozoraEpub3.jar -d ./books/ input.txt

## Kobo形式で出力
java -jar AozoraEpub3.jar -ext .kepub.epub input.txt

## UTF-8エンコードで出力先を指定
java -jar AozoraEpub3.jar -enc UTF-8 -d ./output/ input.txt

## 複数ファイルを一括変換
java -jar AozoraEpub3.jar -d ./books/ file1.txt file2.txt file3.txt

## Web小説URLから直接変換
java -jar AozoraEpub3.jar -url https://ncode.syosetu.com/nXXXX/ -d ./output/

## narou.rb互換設定で変換
java -jar AozoraEpub3.jar -url https://ncode.syosetu.com/nXXXX/ -narou -d ./output/

## 青空文庫のテキストzip URLから直接変換（v1.4.0〜）
java -jar AozoraEpub3.jar -url https://www.aozora.gr.jp/cards/000035/files/1567_ruby_4948.zip -d ./output/
```

> `-url` に `.zip` / `.txtz` / `.rar` を指すURLを渡した場合は、HTMLの取得ではなく
> アーカイブを出力先（`-d`、未指定ならカレントディレクトリ）にダウンロードしてから、
> ローカルのアーカイブを指定した場合とまったく同じ経路で変換します。
> 青空文庫のテキストzipは Shift_JIS のため、`-enc` は既定の `MS932` のままで構いません。

> 注記: CLIのヘルプ表示は Commons CLI の新パッケージ `org.apache.commons.cli.help.HelpFormatter` を使用しており、従来と同等の形式で出力されます（内部非推奨APIの解消）。

### 終了コード

CLI 実行時は、シェルスクリプトや外部ツールから成否を判定できるよう終了コードを返します。

| 終了コード | 意味 |
|---|---|
| `0` | すべての入力ファイルの変換に成功（`-h` / `--help` も `0`） |
| `1` | 1 つ以上の入力ファイルで変換に失敗した／`-i` の INI ファイル・`-d` の出力先ディレクトリ・入力ファイルが存在しない／オプションの指定が不正、または入力ファイルも `-url` も指定されていない（ヘルプを表示して終了） |

```bash
java -jar AozoraEpub3.jar -of -d output novel.txt
if [ $? -ne 0 ]; then
  echo "変換に失敗しました"
fi
```

> 引数なしで `java -jar AozoraEpub3.jar` を実行した場合は GUI が起動します（CLI 実行ではないため上表の対象外です）。

> **v1.3.7-jdk21 での変更点**: v1.3.6-jdk21 以前は、変換に失敗しても**常に `0` を返していました**。
> ディスクの空き容量不足や出力先の書き込み権限がないなどで EPUB の書き出しが途中で中断した場合でも「変換完了」と報告され、
> **壊れた `.epub` が成功として残る**問題があったためです。
> v1.3.7-jdk21 以降は失敗時に `1` を返し、**出力途中の壊れた `.epub` は削除されます**（変換をキャンセルした場合も同様）。
>
> 画像デコード失敗・表紙取得失敗・不審なアーカイブエントリなどは従来どおり局所的に処理を続行するため、
> これまで成功していた変換が失敗扱いになることはありません。
> narou.rb と連携している場合は [narou.rb 導入ガイド](narou-setup.html)も参照してください。

---

## テンプレートのカスタマイズ

AozoraEpub3では、EPUB生成に使用するVelocityテンプレートをカスタマイズできます。

### テンプレートの優先順位

1. **外部テンプレート（優先）**: `AozoraEpub3.jar`と同じフォルダの`template/`ディレクトリ
2. **内蔵テンプレート（フォールバック）**: JAR内に含まれるデフォルトテンプレート

### カスタマイズ方法

1. 配布ZIPに含まれる`template/`フォルダから編集したいテンプレートを確認
2. 必要なテンプレートファイル（`.vm`ファイル）を編集
3. `AozoraEpub3.jar`と同じ場所に`template/`フォルダがあることを確認

```
配布ディレクトリ構成例:
your-directory/
├── AozoraEpub3.jar
├── template/           ← カスタマイズ可能
│   ├── OPS/
│   │   ├── package.vm
│   │   ├── nav.xhtml.vm
│   │   ├── css/
│   │   │   ├── vertical_text.vm
│   │   │   └── horizontal_text.vm
│   │   └── xhtml/
│   │       └── *.vm
│   └── META-INF/
│       └── *.vm
├── gaiji/              ← 外字追加可能
├── presets/            ← プリセット編集可能
└── web/
```

外部の`template/`フォルダが存在しない場合、JAR内のデフォルトテンプレートが自動的に使用されます。

### 編集可能な主なテンプレート

| テンプレート | 用途 |
|-------------|------|
| `OPS/package.vm` | EPUB メタデータ・マニフェスト |
| `OPS/nav.xhtml.vm` | ナビゲーション目次 |
| `OPS/css/vertical_text.vm` | 縦書きCSS |
| `OPS/css/horizontal_text.vm` | 横書きCSS |
| `OPS/xhtml/*.vm` | 本文XHTML生成 |

### 注意事項

- テンプレートファイルはUTF-8エンコーディングで保存してください
- Velocityの文法に従って記述してください
- 不正なテンプレートはEPUB生成エラーの原因となります
- バックアップを取ってから編集することを推奨します

---

## トラブルシューティング

### 変換できない注記が多い

- 青空文庫の仕様外にある注記は対応していません
- ログに表示される注記を確認し、元のテキストを修正してください
- 対応している注記については、[開発者向けドキュメント](development.html#対応している注記) を参照

### ファイルが開かない

- `java` コマンドがインストールされているか確認してください
- Java 25推奨（Java 21以降であれば動作します。`java -version` で確認）

### `AozoraEpub3.exe` の起動時に「Windows によって PC が保護されました」と出る

Microsoft Defender SmartScreen の警告です。**マルウェアが検出されたわけではありません。**

次の 2 つの条件が重なると表示されます。

1. `AozoraEpub3.exe` にコード署名がない（個人 OSS のため証明書を取得していません）
2. ダウンロードした ZIP に **Mark of the Web**（インターネット由来を示す印）が付いており、**エクスプローラで展開すると中のファイルすべてに伝播する**

SmartScreen は Mark of the Web が付いたファイルにしか反応しません。裏を返せば、**ZIP を展開する前に「許可する」にしておけば警告自体が出なくなります**。展開ツールによって挙動が変わるのも同じ理由です（7-Zip などは既定で Mark of the Web を伝播しません）。

**推奨手順（展開前に 1 回だけ）**

1. ダウンロードした `AozoraEpub3-*.zip` を右クリック →「プロパティ」
2. 全般タブ下部の「セキュリティ: このファイルは他のコンピューターから取得したものです。」の**「許可する」にチェック** → OK
3. **その後で** ZIP を展開する

PowerShell なら次の 1 行でも同じです。

```powershell
Unblock-File .\AozoraEpub3-*.zip
```

すでに展開してしまった場合は、展開先フォルダに対して実行してください。

```powershell
Get-ChildItem -Recurse .\AozoraEpub3-* | Unblock-File
```

警告画面が出てしまった場合は「詳細情報」→「実行」でも起動できます（この選択は記録されるので、同じファイルに対して繰り返し聞かれることはありません）。展開前に許可しておく利点は、**警告画面自体が出ないことと、同梱の `.jar` や設定ファイルにも Mark of the Web が残らないこと**です。

> この手順は SmartScreen の警告に対するものです。Windows 11 の **Smart App Control** が有効な環境では、Mark of the Web の有無に関わらず未署名アプリがブロックされることがあります。その場合はこの手順では回避できません。

配布物が正規のものであることは SHA-256 チェックサムで検証できます（[VERIFY.md](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/blob/master/VERIFY.md)）。

### 表示がおかしい

- 文字コード（エンコード）の指定を確認してください
- 青空文庫テキストは通常 MS932（Shift JIS）です
- UTF-8のテキストの場合は `-enc UTF-8` オプションで指定してください

### メモリ不足エラー

大きなファイルの場合、メモリを増やして実行してください：
```bash
java -Xmx2g -jar AozoraEpub3.jar input.txt
```

### 既知の問題

- iOS版Kindleで表題ページ（title.xhtml）のレイアウトが画面比率によって上下位置ずれ・改ページすることがあります。現状は端末依存のため回避策はなく、必要に応じて「表題ページ出力を無効にする」「カスタム表紙のみ出力する」設定をご検討ください。
- Windows 11で `.jar` ダブルクリックが無反応になる場合があります。配布パッケージに同梱の `AozoraEpub3.exe` の使用を推奨します。
- GUIフォントについて: OSが英語設定の場合、日本語字形が環境依存フォントにマップされることがあります。本GUIは OS 別に日本語フォント候補（Windows: Yu Gothic UI/Meiryo）を優先適用することで違和感を軽減しています。

---

## ライセンス

### ソースコードおよびバイナリ

[GPL v3](http://www.gnu.org/licenses/gpl-3.0.html)

ソースコードの流用、改変、再配布を行った場合も GPL v3 が適用されます。

### 変換データ

AozoraEpub3で変換した ePubファイルの著作権は入力データと同一になります。  
ePubファイルの修正や配布は入力データの著作権内で自由に行うことができます。

---

<div style="text-align: center; margin-top: 3em; padding-top: 2em; border-top: 1px solid #e1e4e8;">
  <p><a href="./">ホーム</a> | <a href="development.html">開発者向けドキュメント</a> | <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a></p>
</div>
