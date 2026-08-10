# AozoraEpub3-JDK21

**Java 21〜26対応 / Gradle対応版**

> [!NOTE]
> **Read this in other languages:** [English](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21#readme) (Auto-translated by your browser)

青空文庫の注記入りテキストファイルを EPUB 3 ファイルに変換するツールです。

> [!IMPORTANT]
> **正規配布について**: このプロジェクトの正規配布物は [GitHub Releases](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases) のみです。
> 各リリースには SHA-256 チェックサム・GitHub artifact attestation を添付しています。
> 検証方法は [VERIFY.md](VERIFY.md) を参照してください。
> 非公式フォークや再配布物は、同名であっても正規版とは限りません。

## このプロジェクトについて

このプロジェクトは [hmdev/AozoraEpub3](https://github.com/hmdev/AozoraEpub3) を元に、Java 21対応および [narou.rb](https://github.com/whiteleaf7/narou) での利用を目的として改変したものです。

- **元プロジェクト**: [hmdev/AozoraEpub3](https://github.com/hmdev/AozoraEpub3)
- **ライセンス**: GPL v3（元作者に帰属）
- **目的**: narou.rb との連携、Java 21 への対応

---

## ライセンス

- **AozoraEpub3 の再配布・改変には GPL v3** が適用されます（元作者に帰属）。


## 特徴

 - 青空文庫テキスト（txt/zip）を EPUB 3.3 準拠（EPUB 3.2後方互換）で変換
- Web小説サイトのHTMLから青空文庫形式テキストを取得して変換
- 画像zip/rarを EPUB 3 に変換
- 縦書き・横書きに対応
- 日本の主要電子書籍リーダー（Kobo, Kindle, Reader等）に対応
- Java 21 ベースで開発（Java 25 LTS まで動作確認済み、JDK 26 でも CI ビルド/テスト PASS）
- iPhone版Kindle縦書き対応（※表題ページのレイアウトが画面比率により崩れることがあります）
- **高速変換**: 大容量アーカイブの処理を最適化（アーカイブスキャンを4回→1回に削減）
- **EPUB プレビュー**: 変換した EPUB を実機へ移す前にブラウザで確認（本棚から複数の EPUB を選んで表示）

---

## 画面

ファイルまたは URL をドラッグ＆ドロップすると EPUB に変換します。

<img src="https://raw.githubusercontent.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/master/docs/assets/screenshot-app.png" alt="変換画面。表題・表紙・ページ出力などの設定欄と、ファイルをドロップするテキストエリア" width="626">

変換した EPUB は、リーダーアプリに移す前に**ブラウザでそのままプレビュー**できます。  
縦書き・ルビ・段組・目次を実機に近い形で確認できます。

<img src="https://raw.githubusercontent.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/master/docs/assets/screenshot-preview.png" alt="ブラウザプレビュー。左に章立ての目次、右に縦書き本文が段組で表示されている">

> 画面例は青空文庫の『銀河鉄道の夜』（宮沢賢治、著作権保護期間満了）を変換したものです。
> 画像は配布パッケージからも参照できるよう GitHub 上の絶対 URL で読み込んでいます。

---

## 動作環境

**Java 21以降** が必要です（Java 25 LTS まで動作確認済み）。

- **実行**: JRE 21以降で動作します（JDK不要）。[Adoptium Temurin](https://adoptium.net/) などのランタイム配布を推奨。配布物は Java 21 ターゲットでビルドされている（class file version 65）ため、JDK 21 LTS から JDK 25 LTS までいずれの環境でも動作します（JDK 26 ランタイムも CI で起動確認済）。
- **ビルド/開発**: JDK 21 が必要です（Gradle 9.2.1 launcher は JDK 21 固定）。`./gradlew -PjavaToolchainVersion=26 jar` で JDK 26 toolchain によるビルドも可能です。
- **推奨**: **Java 25 LTS** を推奨します（Java 21 LTS でも動作します）。Temurin による JDK 26 のバイナリ配布が出揃い次第、推奨を更新する予定です。

Java をお持ちでない場合は、[Adoptium](https://adoptium.net/) から Java 25 LTS をダウンロードしてください。

### 対応OS

- Windows 10 以降
- macOS
- Ubuntu

---

## インストール

### 方法 1: リリース版をダウンロード（推奨）

最新版は [GitHub Releases](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases) から取得できます。

配布パッケージには以下が含まれます：
- **FAT JAR**（依存関係込みの単一JAR）
- 起動スクリプト（`.exe` / `.sh`）
- テンプレートファイル（`template/`）
- プリセット設定（`presets/`）
- 外字定義（`gaiji/`）
- ドキュメント

**Windows（ZIP）:**
```
AozoraEpub3-1.4.0-jdk21.zip
```

**Linux/macOS（TAR.GZ）:**
```
AozoraEpub3-1.4.0-jdk21.tar.gz
```

### インストール手順

1. 上記リンクからお使いのOS向けファイルをダウンロード
2. **Windows のみ・展開前に推奨**: ダウンロードした ZIP を右クリック →「プロパティ」→ 下部の「許可する」にチェック → OK（`Unblock-File .\AozoraEpub3-*.zip` でも同じ）。これをしてから展開すると、`.exe` 起動時の SmartScreen 警告を回避できます（[理由と詳細](#aozoraepub3exe-の起動時にwindows-によって-pc-が保護されましたと出る)）
3. ファイルを任意のフォルダに解凍
4. GUI起動方法（以下のいずれか）：
   - **Windows**: `AozoraEpub3.exe` をダブルクリック（推奨）
   - **Unix/Linux/macOS**: `AozoraEpub3.sh` を実行
   - **直接実行**: `java -jar AozoraEpub3.jar`

**注意**: Windows 11では `.jar` ファイルのダブルクリックが動作しないことがあります。その場合は `.exe` ファイルをご利用ください。

### 方法 2: ソースからビルド

開発版の場合は以下の手順でビルドしてください：

```bash
git clone https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21.git
cd AozoraEpub3-JDK21
./gradlew dist
# build/distributions/ に配布パッケージ（zip / tar.gz）が生成されます
```

**重要**: `distZip` タスクは無効化されています。配布パッケージの生成には必ず `dist` を使用してください。

**ビルドタスク詳細:**
- **`./gradlew jar`**: FAT JAR を生成（`build/libs/AozoraEpub3.jar`、依存関係込みの単一JAR）
- **`./gradlew dist`**: 配布パッケージを生成（ZIP / TAR.GZ、JAR + スクリプト + ドキュメント + テンプレート）【推奨】
- **`./gradlew test`**: テスト実行
- **`./gradlew dependencyUpdates`**: 依存ライブラリの更新候補をレポート

**依存ライブラリ更新（開発者向け）:**
- 依存ライブラリは定期的に最新の安定版へ更新しています（alpha/beta/RCは除外）
- CLI の非推奨APIを最新版に移行し、警告を解消
- 更新候補確認: `./gradlew dependencyUpdates`
- 詳細は [RELEASE_NOTES.md](RELEASE_NOTES.md) を参照

---

## 既知の問題

- **iOS版Kindle**: 表題ページ（title.xhtml）のレイアウトが画面比率によって上下位置ずれ・改ページすることがあります。端末依存の制限のため、必要に応じて「表題ページ出力を無効にする」または「カスタム表紙のみ出力する」設定をご検討ください。
- **Windows 11**: `.jar` ファイルのダブルクリックが無反応になる場合があります。配布パッケージに同梱の `AozoraEpub3.exe` の使用を推奨します。
- **GUI フォント**: OSが英語設定の場合、日本語字形が環境依存フォントにマップされることがあります。本GUIは OS 別に日本語フォント候補（Windows: Yu Gothic UI/Meiryo）を優先適用することで違和感を軽減しています。

---

## 最近の変更

### v1.4.0-jdk21 (2026-08-01)
- **表紙（タイトルページ）の長タイトル自動調整**: 長いタイトルが表紙に収まらず著者名と重なる問題を修正。表示文字数に応じて文字サイズを 6 段階で自動調整（45 文字以下は従来と同一出力）
- **CLI で青空文庫 zip 等のアーカイブ URL を直接指定可能に**: `-url` に zip / txtz / rar の URL を渡すとダウンロードしてそのまま変換

### v1.3.7-jdk21 (2026-07-25)
- **コード監査（#1〜#17）にもとづくバグ修正リリース**: パストラバーサル、ImageIO のリソースリーク、ネットワークタイムアウト欠如、Windows のファイル名制約によるキャッシュ無効化・変換中断などを一括修正
- **出典 URL のリンク破損を修正**: URL から変換した EPUB の末尾に付く出典リンクの `href` に注記記法（`［＃縦中横］` 等）が混入し、リンクを開けなくなっていた既存バグを修正
- **タイトルページのリンク切れ `<img>` を修正**: 表題行の画像外字で参照先の画像が無い場合に `<img src="null"/>` を出力し、`epubcheck` が reject する EPUB になっていた既存バグを修正
- **⚠️ Breaking changes**: 変換に失敗したときの CLI 終了コードが `0` → `1` に変更。あわせて出力途中の壊れた `.epub` は削除されます。narou.rb 連携では、これまで「成功」として取り込まれていた破損 EPUB が失敗として扱われます（意図した変更）
- 出力 EPUB の構造は不変（`.NET` ポートの byte-identical 比較テスト 5/5 PASS）

### v1.3.6-jdk21 (2026-05-01)
- **JDK 26 完全対応**: ビルド・全テスト実行・GUI 起動を JDK 26 (2026-03-17 GA) で CI 検証済（v1.3.5 で対応した [JEP 504](https://openjdk.org/jeps/504) `JApplet` 削除に加え、JUnit 4 の test detection を整備）。配布物は Java 21 ターゲットでビルド (class file version 65) のため、JDK 21 LTS〜JDK 26 のいずれの環境でも動作します
- **内部コード現代化**: SLF4J ロガー導入、`java.io.File` → `java.nio.file.Path` 移行、`java.time` API 採用、空 catch ブロック 133 occ の意図コメント整備
- **バグ修正**: `dcterms:modified` が仏暦圏（タイ等）・日本和暦ロケール環境で EPUB 3.3 仕様違反になる問題を修正
- **Breaking changes**: `BookInfo` 等の公開フィールドで `Vector` → `ArrayList` に変更。バイナリ互換性のため、外部ライブラリとして AozoraEpub3 を使うコードは要再コンパイル

### v1.3.5-jdk21 (2026-04-30)
- **JDK 26 互換確保**: [JEP 504](https://openjdk.org/jeps/504) により JDK 26 で削除予定の `JApplet` 継承を撤去（`JPanel` ベースに変更）。`mainClass` / CLI エントリ / narou.rb 連携への影響なし
- **詳細設定タブ表示崩れ修正（Windows）**: TitledBorder + 設定行が 8px 程度に圧縮されて文字と部品が重なる既存バグを修正（パネル高 28→48px 統一）

### v1.3.4-jdk21 (2026-04-09)
- **ハーメルン章サポート**: TOC テーブルから章タイトル取得、複数作品連続変換時の状態リセット修正
- E2E テストインフラ整備

### v1.3.3-jdk21 (2026-04-04)
- セキュリティ・信頼性向上: SECURITY.md / VERIFY.md 追加、release ワークフロー整備、SSH タグ署名導入

### v1.3.2-jdk21 (2026-04-02)
- **ハーメルン対応**: HttpClient 移行、Cookie/前書き/後書き対応、TCY 二重ネスト根本修正、Web 取得間隔調整
- カクヨム等のキャッシュパス衝突修正、Windows フルパス長制限対応

### v1.3.0–v1.3.1-jdk21
- **narou.rb 互換テキスト前処理**: 空行圧縮・漢数字変換・英字全角化・二分アキ・前書き/後書き検出・読了表示
- CSS / 注記定義の完全版更新、CLI `-url` オプション、サブタイトル漢数字変換の完全修正

---

## 使い方（GUI）

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

Web小説サイトのURLまたはURLショートカット（`.url`）をドラッグ&ドロップで取得・変換できます。（`web/` 以下に定義ファイルがあるサイトのみ）

**対応サイト**: 小説家になろう / 小説家になろう R18 / カクヨム / ハーメルン / 暁 / novelist.jp / FC2小説 など

**narou.rb互換 フォーマット設定**: GUIメニューから「Web小説設定」を開くと、以下の項目を設定できます:

| 設定項目 | 説明 | 初期値 |
|---------|------|--------|
| 更新日時を各話に表示 | 各話の最終更新日時を本文末に表示 | OFF |
| 初回公開日を各話に表示 | 改稿済の話の初回公開日を表示（更新日時と別行） | OFF |
| 前書き・後書きの自動検出 | `*44`/`*48` 個パターンで前書き・後書きを検出 | ON |
| 自動行頭字下げ | 行頭の字下げを自動挿入 | ON |
| 改ページ直後の見出し化 | 改ページ後の最初の行を見出しとして処理 | ON |
| 空行圧縮 | 連続空行を圧縮 | ON |
| 漢数字変換 | アラビア数字を漢数字に変換 | ON |
| 英字全角化 | 短い英単語を全角に変換 | ON |
| 読了表示 | 末尾に読了マークを表示 | ON |
| かぎ括弧内自動連結 | かぎ括弧内の行を自動連結 | ON |
| 行末読点自動連結 | 読点で終わる行を次行と連結 | ON |

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
# GUI起動（引数なし）
java -jar AozoraEpub3.jar

# 入力ファイルを指定（CLI実行）
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

CLI オプションは上記がすべてです。文字サイズ・行間・余白・画像縮小・外字などの詳細設定に
コマンドライン引数はありません。GUI で保存した ini か `presets/*.ini` を `-i` で渡してください。

### 設定ファイル（AozoraEpub3.ini）の探し方

CLI は次の順で設定ファイルを読みます。見つからなければ既定値（GUI の初期状態と同じ）で
動作し、その旨をログに 1 行出力します。

1. `-i <ファイル>` で明示したファイル
2. カレントディレクトリの `AozoraEpub3.ini`
3. jar と同じ場所の `AozoraEpub3.ini`（同梱の設定ファイル）

**GUI は起動時のカレントディレクトリの `AozoraEpub3.ini` を読み書きします**。
`AozoraEpub3.exe` やダブルクリックで起動した場合はカレントが配布フォルダになるため、
実質的に jar と同じ場所の同梱 ini が使われます（`AozoraEpub3.sh` は実行した
ディレクトリがカレントになる点に注意）。
配布フォルダの外から CLI を実行するときは 3. により、この（通常の起動方法で GUI が
読み書きしている）同梱 ini が使われます。
なお 2. により、作業ディレクトリに `AozoraEpub3.ini` という名前のファイルがあると
意図せずそちらが優先されます。どのファイルを読んだかは起動時のログ
`設定ファイルを読み込みました: <パス>` で確認できます。

### EPUB プレビュー

変換した EPUB を Kindle 等の実機へ転送する前に、既定のブラウザで確認できます。
縦書きの行折り・ルビの衝突・挿絵の収まり・濁点合成フォントの見え方を確かめる用途です。

```bash
# 既存の EPUB をそのまま表示（変換しない）
java -jar AozoraEpub3.jar --preview foo.epub

# 変換してから表示
java -jar AozoraEpub3.jar -of -d ./output/ --preview input.txt
```

GUI では変換完了後に「プレビュー」ボタンが有効になります。「プレビュー」タブの
「変換完了後に自動でプレビューを開く」をオンにすると、変換のたびに自動で開きます（既定はオフ）。

- 目次パネルから章・見出しへジャンプ
- フォント（既定は UD デジタル教科書体）・文字サイズ・行間・上下左右の余白を調整
- ダークモード（システム追従 / ライト / ダーク）
- EPUB 情報（書誌・構成・manifest 内訳・実効スタイル・CSS・埋め込みフォント）の確認
- ページ送り: 本文の左右端クリック / ホイール / ← → / Space

サーバはループバックアドレス（`127.0.0.1`、IPv6 優先環境では `::1`）のランダムポートに
URL トークン付きで待ち受け、外部からは接続できません。
CLI ではブラウザを閉じると自動的に終了します（Ctrl-C でも終了）。
表示設定は `~/.aozoraepub3/preview-settings.json` に保存されます。

#### 本棚（複数の EPUB を並べて開く）

変換済み EPUB を置いているフォルダを「本棚」として登録すると、表紙サムネイル付きの一覧から
選んでプレビューできます。サブフォルダも探索します。登録できるフォルダは最大 8 個です。

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

### 例

```bash
# 標準的な変換
java -jar AozoraEpub3.jar input.txt

# 出力先を指定
java -jar AozoraEpub3.jar -d ./books/ input.txt

# Kobo形式で出力
java -jar AozoraEpub3.jar -ext .kepub.epub input.txt

# UTF-8エンコードで出力先を指定
java -jar AozoraEpub3.jar -enc UTF-8 -d ./output/ input.txt

# 複数ファイルを一括変換
java -jar AozoraEpub3.jar -d ./books/ file1.txt file2.txt file3.txt

# Web小説URLから直接変換
java -jar AozoraEpub3.jar -url https://ncode.syosetu.com/nXXXX/ -d ./output/

# narou.rb互換設定で変換
java -jar AozoraEpub3.jar -url https://ncode.syosetu.com/nXXXX/ -narou -d ./output/

# 青空文庫のテキストzip URLから直接変換（v1.4.0〜。.txtz / .rar も同様）
java -jar AozoraEpub3.jar -url https://www.aozora.gr.jp/cards/000035/files/1567_ruby_4948.zip -d ./output/
```

### 終了コード

| 終了コード | 意味 |
|---|---|
| `0` | すべての入力ファイルの変換に成功（`-h` / `--help` も `0`） |
| `1` | 1 つ以上の入力ファイルで変換に失敗した／`-i` の INI ファイル・`-d` の出力先ディレクトリ・入力ファイルが存在しない／オプションの指定が不正、または入力ファイルも `-url` も指定されていない |

引数なしで実行した場合は GUI が起動するため、上表の対象外です。

> **v1.3.7-jdk21 での変更点**: v1.3.6-jdk21 以前は、変換に失敗しても常に `0` を返していました。
> v1.3.7-jdk21 以降は失敗時に `1` を返し、出力途中の壊れた `.epub` は削除されます。
> 詳細は [docs/usage.md](docs/usage.md#終了コード)、narou.rb 連携時の注意は [docs/narou-setup.md](docs/narou-setup.md) を参照してください。

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
- 対応している注記については、[DEVELOPMENT.md](DEVELOPMENT.md#対応している注記) を参照

### ファイルが開かない

- `java` コマンドがインストールされているか確認してください
- Java 21以降がインストールされているか確認（`java -version` で確認）

### `AozoraEpub3.exe` の起動時に「Windows によって PC が保護されました」と出る

Microsoft Defender SmartScreen の警告です。**マルウェアが検出されたわけではありません。**

原因は 2 つの条件が重なることです。

1. `AozoraEpub3.exe` にコード署名がない（個人 OSS のため証明書を取得していません）
2. ダウンロードした ZIP に **Mark of the Web**（インターネット由来を示す印）が付いており、エクスプローラで展開すると**中のファイルすべてに伝播する**

SmartScreen は Mark of the Web が付いたファイルにしか反応しません。そのため、**ZIP を展開する前に「許可する」にしておけば警告は出なくなります**。展開ツールによって出たり出なかったりするのも同じ理由です（7-Zip などは既定で Mark of the Web を伝播しません）。

**推奨手順（展開前に 1 回だけ）**

1. ダウンロードした `AozoraEpub3-*.zip` を右クリック →「プロパティ」
2. 全般タブの下部にある「セキュリティ: このファイルは他のコンピューターから取得したものです。」の**「許可する」にチェック** → OK
3. **その後で** ZIP を展開する

PowerShell なら次の 1 行でも同じです。

```powershell
Unblock-File .\AozoraEpub3-*.zip
```

展開後に気づいた場合は、展開先フォルダに対して次を実行してください。

```powershell
Get-ChildItem -Recurse .\AozoraEpub3-* | Unblock-File
```

すでに警告画面が出てしまった場合は「詳細情報」→「実行」でも起動できます（この選択は記録されるので、同じファイルに対して繰り返し聞かれることはありません）。展開前に許可しておく利点は、**警告画面自体が出ないことと、同梱の `.jar` や設定ファイルにも Mark of the Web が残らないこと**です。

> この手順は SmartScreen の警告に対するものです。Windows 11 の **Smart App Control** が有効な環境では、Mark of the Web の有無に関わらず未署名アプリがブロックされることがあります。その場合はこの手順では回避できません。

配布物が正規のものであることは SHA-256 チェックサムで検証できます。手順は [VERIFY.md](VERIFY.md) を参照してください。

### 表示がおかしい

- 文字コード（エンコード）の指定を確認してください
- 青空文庫テキストは通常 MS932（Shift JIS）です
- UTF-8のテキストの場合は `-enc UTF-8` オプションで指定してください

### メモリ不足エラー

大きなファイルの場合、メモリを増やして実行してください：
```bash
java -Xmx2g -jar AozoraEpub3.jar input.txt
```

### narou.rb で「JavaがインストールされていないかAozoraEpub3実行時にエラーが発生しました」と出る

Java は正しく入っているのにこのメッセージが出る場合、実際には **EPUB の出力に失敗している**可能性があります。

narou.rb は「AozoraEpub3 はエラーでも終了コード 0 を返す」という前提で作られているため、0 以外の終了コードをすべて「Java が動かなかった」と解釈します。AozoraEpub3 は v1.3.7-jdk21 から変換失敗時に `1` を返すようになったため、このメッセージが出るようになりました（意図した変更です。詳細は上記「[終了コード](#終了コード)」）。

真の原因は、このメッセージの直前に narou.rb が全文表示する AozoraEpub3 の出力（`エラーが発生しました : ...` の行）で確認できます。`narou convert -v` はこの場面では追加情報になりません（終了コードが 0 以外だと verbose 出力に到達する前に処理を打ち切るため）。

詳細は [docs/narou-setup.md](docs/narou-setup.md) を参照してください。

---

## ライセンス

### ソースコードおよびバイナリ

[GPL v3](http://www.gnu.org/licenses/gpl-3.0.html)

ソースコードの流用、改変、再配布を行った場合も GPL v3 が適用されます。

### 変換データ

AozoraEpub3で変換した ePubファイルの著作権は入力データと同一になります。  
ePubファイルの修正や配布は入力データの著作権内で自由に行うことができます。

---

## 更新履歴

詳細は [README_Changes.txt](README_Changes.txt) を参照してください。

---

## 開発への参加

バグ報告・機能提案・プルリクエストを歓迎します。  
詳細は [DEVELOPMENT.md](DEVELOPMENT.md) を参照してください。
