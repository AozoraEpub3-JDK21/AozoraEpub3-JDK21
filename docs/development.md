---
layout: default
title: 開発者向けガイド - AozoraEpub3
---

<nav style="background: #f6f8fa; padding: 1em; margin-bottom: 2em; border-radius: 6px;">
  <strong>📚 ドキュメント:</strong>
  <a href="./">ホーム</a> | 
  <a href="usage.html">使い方</a> | 
  <a href="narou-setup.html">narou.rb</a> |
  <strong>開発者向け</strong> | 
  <a href="epub33-ja.html">EPUB 3.3準拠</a> |
  <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a>
  <div style="float: right;">🌐 <a href="en/development.html">English</a></div>
</nav>

# 開発者向けガイド

AozoraEpub3の開発に参加する方、または内部実装を理解したい方向けのドキュメントです。

## 目次

- [開発環境のセットアップ](#開発環境のセットアップ)
- [ビルドとテスト](#ビルドとテスト)
- [プロジェクト構造](#プロジェクト構造)
- [コード構造とリファクタリング](#コード構造とリファクタリング)
- [テンプレート (Velocity)](#テンプレート-velocity)
- [EPUB 3.3 準拠への対応](#epub-33-準拠への対応)
- [対応している注記](#対応している注記)
- [外字処理](#外字処理)
- [GitHub Actions CI](#github-actions-ci)
- [貢献ガイドライン](#貢献ガイドライン)
- [よくある問題](#よくある問題)

---

## 開発環境のセットアップ

### 必要な環境

- **Java**: JDK 21 以上（ビルド時）、JRE 21 以上（実行時）
- **Gradle**: 8.0 以上（Gradle Wrapper 推奨）
- **Git**: バージョン管理
- **IDE**: IntelliJ IDEA, Eclipse, VS Code など

### リポジトリのクローン

```bash
git clone https://github.com/Harusame64/AozoraEpub3-JDK21.git
cd AozoraEpub3-JDK21
```

### IDE での開発

#### VS Code
1. Java Extension Pack をインストール
2. フォルダを開く
3. Gradle タスクが自動認識される

#### IntelliJ IDEA
1. File → Open → `build.gradle` を選択
2. "Import Gradle Project" で自動設定

#### Eclipse
```bash
./gradlew eclipse
```
その後、Eclipse で Import → Existing Projects

---

## ビルドとテスト

### 基本的なビルド

```bash
# クリーンビルド
./gradlew clean build

# FAT JAR 作成（すべての依存関係を含む）
./gradlew jar

# 配布パッケージ作成 (ZIP + TAR.GZ)
./gradlew dist
```

**重要**: `distZip` タスクは無効化されています。配布パッケージは `dist` タスクを使用してください。

### テストの実行

```bash
# すべてのテスト実行
./gradlew test

# レポート生成
./gradlew test --rerun-tasks
# → build/reports/tests/test/index.html
```

### 生成物の場所

- **FAT JAR**: `build/libs/AozoraEpub3.jar`
- **配布パッケージ**: 
  - `build/distributions/AozoraEpub3-<version>.zip`
  - `build/distributions/AozoraEpub3-<version>.tar.gz`
- **テストレポート**: `build/reports/tests/test/index.html`

### 実行方法

```bash
# GUI起動（引数なし）
java -jar build/libs/AozoraEpub3.jar

# CLI使用（UTF-8テキストから EPUB 生成）
java -jar build/libs/AozoraEpub3.jar -of -d out input.txt

# 縦書きサンプル
java -jar build/libs/AozoraEpub3.jar -enc UTF-8 test_data/test_title.txt

# 横書きサンプル
java -jar build/libs/AozoraEpub3.jar -enc UTF-8 -y test_data/test_yoko.txt
```

---

## プロジェクト構造

```
AozoraEpub3/
├── src/                       # メインソースコード
│   ├── AozoraEpub3.java       # CLI エントリポイント
│   ├── AozoraEpub3Applet.java # GUIエントリポイント
│   └── com/github/hmdev/      # パッケージルート
│       ├── converter/         # テキスト→EPUB変換
│       ├── epub/              # EPUB仕様関連
│       ├── io/                # ファイル・アーカイブ処理
│       ├── image/             # 画像処理
│       ├── config/            # 設定ファイル解析
│       └── pipeline/          # 変換パイプライン
│
├── test/                      # テストコード
│   ├── AozoraEpub3SmokeTest.java
│   ├── IniCssIntegrationTest.java
│   └── com/github/hmdev/      # 各パッケージのテスト
│
├── template/                  # Velocity テンプレート
│   ├── mimetype               # EPUB mimetype ファイル
│   ├── META-INF/
│   │   └── container.xml      # EPUB コンテナ定義
│   └── OPS/
│       ├── package.vm         # package.opf 生成
│       ├── toc.ncx.vm         # NCX 目次生成
│       └── css/               # CSS テンプレート
│           ├── vertical_text.vm
│           └── horizontal_text.vm
│
├── test_data/                 # テスト用データ
│   ├── test_title.txt         # タイトル・著者テスト
│   ├── test_ruby.txt          # ルビ変換テスト
│   ├── test_gaiji.txt         # 外字変換テスト
│   └── img/                   # テスト用画像
│
├── presets/                   # デバイスプリセット
│   ├── kobo__full.ini         # Kobo 最大サイズ
│   ├── kindle_pw.ini          # Kindle Paperwhite
│   └── reader.ini             # Sony Reader
│
├── chuki_*.txt                # 注記定義ファイル
│   ├── chuki_tag.txt          # 注記 → タグ変換
│   ├── chuki_alt.txt          # 外字 → 代替文字
│   ├── chuki_utf.txt          # 外字 → UTF-8
│   ├── chuki_ivs.txt          # 外字 → IVS
│   └── chuki_latin.txt        # ラテン文字変換
│
├── build.gradle               # Gradle ビルド定義
├── gradlew, gradlew.bat       # Gradle Wrapper
├── README.md                  # ユーザー向けドキュメント
└── DEVELOPMENT.md             # 本ドキュメントの原本
```

### 主要クラスの役割

#### エントリポイント
- `AozoraEpub3.java`: CLI処理、引数パース、変換実行
- `AozoraEpub3Applet.java`: Swingベースの GUI

#### 変換パイプライン
- `Epub3Writer`: EPUB 3形式でのファイル生成（Velocity使用）
- `AozoraEpubConverter`: 青空文庫注記のパース・変換
- `BookInfoReader`: タイトル・著者の抽出
- `CharacterConverter`: 外字・ルビの変換

#### I/O・アーカイブ
- `ArchiveTextExtractor`: zip/rar からのテキスト抽出（キャッシュ機能）
- `ArchiveCache`: アーカイブスキャン結果のキャッシュ
- `OutputNamer`: 出力ファイル名の生成

#### 画像処理
- `ImageInfoReader`: 画像メタデータ読み込み
- `ImageConverter`: 画像リサイズ・最適化

#### 設定
- `IniFile`: INI ファイルの解析
- `ConfigValues`: 設定値の保持

---

## コード構造とリファクタリング

### 最近の改善（v1.2.4以降）

#### モジュール化とクラス抽出

大規模な `AozoraEpub3.java`（元々645行）から責任を分離し、保守性を向上しました：

**抽出されたクラス:**

1. **OutputNamer** (`com.github.hmdev.io`): ファイル名生成ロジック（50行）
   - creator/title ベースの自動命名
   - ファイル名のサニタイズ（無効文字除去）
   - 拡張子のデフォルト設定

2. **WriterConfigurator** (`com.github.hmdev.pipeline`): Writer設定の集約（110行）
   - 画像パラメータ設定
   - 目次ネスト設定
   - スタイル設定（余白・行高・フォント）

3. **ArchiveTextExtractor** (`com.github.hmdev.io`): アーカイブ処理の統一（90行）
   - zip/rar/txtからのテキスト抽出
   - テキストファイル数のカウント
   - キャッシュ機構との連携

**リファクタリング効果:**
- `AozoraEpub3.java`: 645行 → 450行（**200行削減**）
- 新規クラス: 5クラス（計350行）
- 単体テスト追加: OutputNamerTest（4テスト）

詳細は [notes/refactor-plan.md](https://github.com/Harusame64/AozoraEpub3-JDK21/blob/master/notes/refactor-plan.md) を参照。

#### パフォーマンス最適化 🚀

**問題**: 1ファイルあたり4回のアーカイブスキャンが発生していました：
1. テキストファイル数のカウント
2. 書誌情報の取得
3. 画像リストの読み込み
4. 実際の変換処理

**解決策**: アーカイブキャッシュ機構の導入

- **ArchiveCache**: アーカイブを1回スキャンして情報をメモリに保持
  - テキストファイル内容（byte配列）
  - 画像ファイルエントリ名リスト
  - テキストファイル数

- **ArchiveScanner**: zip/rarの統一スキャナー
  - テキスト・画像エントリを1パスで収集
  - RAR一時ファイル展開の最適化

**最適化効果:**
- アーカイブスキャン: **4回 → 1回**（75%削減）
- 大容量zip/rar（100MB以上）の変換速度が大幅に向上
- メモリ使用量: 2GBアーカイブでも10-20MB程度のキャッシュ

**メモリ管理:**
- 変換完了後に `ArchiveTextExtractor.clearCache()` で自動解放
- テキストコンテンツのみキャッシュ（画像本体は都度読み込み）

詳細は [notes/archive-cache-optimization.md](https://github.com/Harusame64/AozoraEpub3-JDK21/blob/master/notes/archive-cache-optimization.md) を参照。

---

## テンプレート (Velocity)

### 設計方針

`Epub3Writer` は Velocity のグローバル初期化に依存しないよう **VelocityEngine の注入** に対応しています。

#### コンストラクタ

```java
// 推奨：VelocityEngine を注入（テスト容易・カスタマイズ可能）
new Epub3Writer(templatePath, velocityEngine)

// または旧方式（後方互換）
new Epub3Writer(templatePath)  // グローバル初期化を利用
```

#### テストでの使用例

```java
Properties p = new Properties();
p.setProperty("resource.loaders", "file");
p.setProperty("resource.loader.file.class", 
    "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
p.setProperty("resource.loader.file.path", 
    projectRoot.resolve("template").resolve("OPS").toString());
VelocityEngine ve = new VelocityEngine(p);

VelocityContext ctx = new VelocityContext();
ctx.put("title", "サンプルタイトル");
ctx.put("bookInfo", bookInfoObject);
ctx.put("sections", sectionList);
// ... その他のコンテキスト設定

Template t = ve.getTemplate("package.vm", "UTF-8");
StringWriter out = new StringWriter();
t.merge(ctx, out);
String opf = out.toString();
```

### テンプレートファイル一覧

| テンプレート | 用途 | 出力ファイル |
|-------------|------|-------------|
| `package.vm` | EPUB メタデータ・マニフェスト | `package.opf` |
| `toc.ncx.vm` | 目次（NCX形式） | `toc.ncx` |
| `css/vertical_text.vm` | 縦書きテキスト CSS | `vertical_text.css` |
| `css/horizontal_text.vm` | 横書きテキスト CSS | `horizontal_text.css` |
| `xhtml/*.vm` | 本文 XHTML | `*.xhtml` |

### INI値の CSS 反映

INI設定ファイルの値（`font_size`, `line_height` など）は Velocity コンテキストに配置され、テンプレートで CSS変数として利用されます。

**例（vertical_text.vm）:**
```velocity
:root {
  --font-size: ${fontSize}%;
  --line-height: ${lineHeight};
}
```

**関連テスト:**
- [CssTemplateRenderTest.java](https://github.com/Harusame64/AozoraEpub3-JDK21/blob/master/test/com/github/hmdev/config/CssTemplateRenderTest.java): 縦書きCSS
- [HorizontalCssTemplateRenderTest.java](https://github.com/Harusame64/AozoraEpub3-JDK21/blob/master/test/com/github/hmdev/config/HorizontalCssTemplateRenderTest.java): 横書きCSS

---

## EPUB 3.3 準拠への対応

### 検証項目（CI で自動化）

#### mimetype ファイル
- ZIP の先頭エントリ・非圧縮（STORED）で格納必須

#### package.opf (OPF ファイル)
- `unique-identifier` 必須
- `dc:identifier` が `urn:uuid:` 形式（RFC 4122 準拠）
- `dcterms:modified` ISO8601形式
- `language` = `ja`（日本語コンテンツ）
- `nav` item が manifest に存在
- `spine` の `page-progression-direction` 属性：
  - 縦書き → `rtl`（右から左）
  - 横書き → `ltr`（左から右）
- CSS の `writing-mode` と整合性：
  - 縦書き → `vertical-rl`
  - 横書き → 未指定または `horizontal-tb`

#### container.xml
- XML 整形式（xmllint で検証）

### Kindle (iPhone) 対応

`package.vm` L60 に以下を記載（ImageOnlyかつKindle時）：
```xml
<meta name="primary-writing-mode" content="horizontal-rl"/>
```

**保持テスト**: [PackageTemplateKindleMetaTest.java](https://github.com/Harusame64/AozoraEpub3-JDK21/blob/master/test/com/github/hmdev/epub/PackageTemplateKindleMetaTest.java)

### epubcheck による検証

```bash
# Gradle タスク（カスタム）
./gradlew epubcheck \
  -PepubDir=build/epub_local \
  -PepubcheckJar=build/tools/epubcheck-5.3.0/epubcheck.jar
```

**epubcheck の取得方法:**
```bash
mkdir -p build/tools
cd build/tools
curl -L -o epubcheck-5.3.0.zip \
  https://github.com/w3c/epubcheck/releases/download/v5.3.0/epubcheck-5.3.0.zip
unzip epubcheck-5.3.0.zip
```

---

## 対応している注記

### 基本的な注記（設定ファイルで対応）

- `chuki_tag.txt`: 注記 → ePubタグ変換定義
- `chuki_tag_suf.txt`: 前方参照型 → 開始終了型に変換
- `chuki_alt.txt`: 外字 → 代替文字マッピング
- `chuki_utf.txt`: 外字 → UTF-8マッピング
- `chuki_ivs.txt`: 外字 → IVS付きUTF-8マッピング
- `chuki_latin.txt`: ラテン文字 → UTF-8変換

### プログラムで処理する注記

```
- ページの左右中央
- ルビ付き注記の自動変換 ［＃「○」に「△」のルビ］ → ｜○《△》
- 傍点の自動変換 ［＃「○」に×傍点］ → ×ルビ化
- 字下げ複合処理（fold/indent計算）
- 割り注の改行追加
- 底本： による改ページ
```

### 未対応の注記

```
- 訂正と「ママ」（無視される）
- 左ルビ
- 行内の地付き
- ２段組
```

---

## 外字処理

青空文庫注記から外字を抽出・変換します：

```
※［＃「字名」、U+6DB6］                          → Unicode直接指定
※［＃「字名」、第3水準1-85-57］                  → JISコード → UTF-8
※［＃「さんずい＋垂」、UCS6DB6］                → UCS形式
```

対応コードが無い外字は `chuki_alt.txt` で代替文字に置換されます。

---

## GitHub Actions CI

### ワークフロー一覧

#### ci.yml（ビルド・テスト・EPUB生成・検証）
以下の処理を自動実行：
- ビルド (Gradle)
- テスト実行 (JUnit)
- サンプル EPUB 生成（縦書き・横書き・複数プリセット）
- `epubcheck` で仕様検証
- OPF XPath 検証（UUID形式、書字方向の整合）
- アクセシビリティ非致命サマリ（img alt欠落件数）
- 生成 EPUB を Artifacts にアップロード
- 手動実行対応（workflow_dispatch + 入力値）

**手動実行方法:**  
GitHub → Actions → CI → Run workflow

オプション入力:
- `ini_font_size`: INI の font_size（既定: 115）
- `ini_line_height`: INI の line_height（既定: 1.7）

#### test.yml（ユニットテストのみ）
- テスト実行 (JUnit)
- 手動実行対応

---

## 貢献ガイドライン

### バグ報告・機能提案

GitHub Issues で以下の情報を提供してください：

#### バグの場合
- 環境（OS、Java バージョン）
- 入力テキスト（or 最小再現例）
- エラーメッセージ・ログ
- 期待される動作 vs 実際の動作

#### 機能提案の場合
- 提案内容
- ユースケース
- 参考資料（あれば）

### プルリクエスト

1. リポジトリを fork して feature ブランチを作成
2. 変更を加え、テストを追加
3. `./gradlew test` で全テストをパス確認
4. PR を作成（説明・関連Issue記載）

### コーディング規約

- 既存スタイルに合わせる（Java標準）
- テストを必ず追加（JUnit 4）
- テンプレート変更時は対応テストも更新
- コミットメッセージは英語または日本語で明確に

---

## よくある問題

### Velocity リソース解決に失敗

**症状**: テスト実行時に `template/*.vm` が見つからない

**原因**: Gradle Worker が異なる作業ディレクトリで実行される

**解決策:**
- FileResourceLoader で絶対パスを指定
- `Paths.get(".").toAbsolutePath().normalize()` で projectRoot を取得
- `VelocityTestUtils` ユーティリティを活用

### Windows パス問題

**症状**: `\` がエスケープで失敗する

**解決策:**
- `Paths` API を使用（自動的に `/` に統一）
- または `forward slash` を明示

### テスト不安定性

**症状**: ローカルでは成功するが CI で失敗する

**原因**: 
- ファイル I/O のタイミング
- リソース解決パスの依存

**解決策:**
- 相対パスを避け、絶対パスまたはクラスパス指定
- テンプレートパスを configurable に

---

## パフォーマンス最適化のヒント

### 大容量テキスト変換時
- メモリを増やす: `java -Xmx2g ...`
- 強制改ページを有効（ファイル分割）

### 画像処理
- 縮小時の CPU 負荷が高い（Bicubic）
- 余白除去は PNG で時間がかかる

### EPUB 生成速度
- Velocity テンプレートの最適化（大量ループを避ける）
- 画像ストリーミング（大きい画像は逐次処理）

---

## リンク

- [🏠 ホームページ](index.html)
- [📖 使い方ガイド](usage.html)
- [📚 EPUB 3.3 対応](epub33-ja.html)
- [💻 GitHub リポジトリ](https://github.com/Harusame64/AozoraEpub3-JDK21)
- [🔧 オリジナルプロジェクト](https://github.com/hmdev/AozoraEpub3)
- [📝 青空文庫](https://www.aozora.gr.jp/)
- [📖 EPUB 3.3 仕様](https://www.w3.org/TR/epub-33/)
- [✅ epubcheck](https://github.com/w3c/epubcheck)

---

## ライセンス

GPL v3 - 詳細は [README](https://github.com/Harusame64/AozoraEpub3-JDK21#ライセンス) を参照

---

<footer style="margin-top: 40px; padding-top: 20px; border-top: 1px solid #ddd; font-size: 0.9em; color: #666;">
  <p>© 2025 AozoraEpub3-JDK21 Project</p>
  <p>
    <a href="index.html">ホーム</a> |
    <a href="usage.html">使い方</a> |
    <a href="development.html">開発者向け</a> |
    <a href="epub33-ja.html">EPUB 3.3</a> |
    <a href="https://github.com/Harusame64/AozoraEpub3-JDK21">GitHub</a>
  </p>
</footer>
