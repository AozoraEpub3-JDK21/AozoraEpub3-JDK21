# AozoraEpub3

**Java 21対応版 / Gradle対応版**

> [!NOTE]
> **Read this in other languages:** [English](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21#readme) (Auto-translated by your browser)

青空文庫の注記入りテキストファイルを EPUB 3 ファイルに変換するツールです。

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
- Java 21 ベースで開発（Java 25 でも動作確認済み）
- iPhone版Kindle縦書き対応（※表題ページのレイアウトが画面比率により崩れることがあります）
- **高速変換**: 大容量アーカイブの処理を最適化（アーカイブスキャンを4回→1回に削減）

---

## 動作環境

**Java 21以降** が必要です（Java 25 でも動作確認済み）。

- **実行**: JRE 21以降で動作します（JDK不要）。[Adoptium Temurin](https://adoptium.net/) などのランタイム配布を推奨。
- **ビルド/開発**: JDK 21 が必要です（Gradle 9.2.1 で動作確認済み）。
- **推奨**: 最新の長期サポート版（LTS）である Java 21 を推奨しますが、Java 25 でも互換性を確認しています。

Java をお持ちでない場合は、[Adoptium](https://adoptium.net/) から Java 21 または Java 25 をダウンロードしてください。

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
- 起動スクリプト（`.bat` / `.sh`）
- テンプレートファイル（`template/`）
- プリセット設定（`presets/`）
- 外字定義（`gaiji/`）
- ドキュメント

**Windows（ZIP）:**
```
AozoraEpub3-1.2.7-jdk21.zip
```

**Linux/macOS（TAR.GZ）:**
```
AozoraEpub3-1.2.7-jdk21.tar.gz
```

### インストール手順

1. 上記リンクからお使いのOS向けファイルをダウンロード
2. ファイルを任意のフォルダに解凍
3. GUI起動方法（以下のいずれか）：
   - **Windows**: `AozoraEpub3.bat` をダブルクリック（推奨）
   - **Unix/Linux/macOS**: `AozoraEpub3.sh` を実行
   - **直接実行**: `java -jar AozoraEpub3.jar`

**注意**: Windows 11では `.jar` ファイルのダブルクリックが動作しないことがあります。その場合は `.bat` ファイルをご利用ください。

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
- **Windows 11**: `.jar` ファイルのダブルクリックが無反応になる場合があります。配布パッケージに同梱の `AozoraEpub3.bat` の使用を推奨します。
- **GUI フォント**: OSが英語設定の場合、日本語字形が環境依存フォントにマップされることがあります。本GUIは OS 別に日本語フォント候補（Windows: Yu Gothic UI/Meiryo）を優先適用することで違和感を軽減しています。

---

## 最近の変更（1.2.7-jdk21）

- **カクヨム (kakuyomu.jp) 対応**: `__NEXT_DATA__` JSON からエピソード全件取得・章構造・更新差分・傍点・あらすじに対応
- **ハーメルン extract.txt 更新**: `<font>` 廃止対応（`span[itemprop]` / `#honbun`）
- **セキュリティ修正**: CodeQL アラート全件対応（path-injection 30件、ReDoS #8–11/#65、command-injection #12/#67、partial-path-traversal #13）
- 閉鎖・休眠サイト (dNoVeLs / NEWVEL-LIBRARY / Arcadia) に警告コメントを追記

---

## 使い方（GUI）

### 基本的な流れ

1. **アプリケーション起動**
   - Windows: `AozoraEpub3.bat` をダブルクリック（推奨）
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
| `-enc <エンコード>` | 入力ファイルのエンコード | `-enc UTF-8` |
| `-t <タイプ>` | タイトル抽出方法 | `-t 0` (0=本文内から抽出) |
| `-c <画像>` | 表紙指定 | `-c cover.jpg` |
| `-d <パス>` | 出力先ディレクトリ | `-d ./output/` |
| `-ext <拡張子>` | 出力ファイル拡張子 | `-ext .kepub.epub` |
| `-of` | ファイル名から表題を生成 | |
| `-hor` | 横書きで出力 | |
| `-device <種別>` | 端末種別を指定 | `-device kindle` |

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
```

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

### 表示がおかしい

- 文字コード（エンコード）の指定を確認してください
- 青空文庫テキストは通常 MS932（Shift JIS）です
- UTF-8のテキストの場合は `-enc UTF-8` オプションで指定してください

### メモリ不足エラー

大きなファイルの場合、メモリを増やして実行してください：
```bash
java -Xmx2g -jar AozoraEpub3.jar input.txt
```

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
