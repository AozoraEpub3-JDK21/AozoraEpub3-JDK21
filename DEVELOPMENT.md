# DEVELOPMENT.md

AozoraEpub3 の開発に参加するための情報をまとめています。

---

## 開発者向けドキュメント

このドキュメントは以下を対象としています：
- プロジェクト構造を理解したい方
- バグ修正・機能追加をしたい方
- ビルド・テスト環境を構築したい方

利用者向けのドキュメントは [README.md](README.md) を参照してください。

---

## プロジェクト構造

```
AozoraEpub3/
├── src/                           # Java ソースコード
│   ├── AozoraEpub3.java          # GUI起動クラス
│   ├── AozoraEpub3Applet.java    # アプレット（GUIメイン）
│   └── com/github/hmdev/         # 主要なロジック
│       ├── config/               # 設定・解析（INI、プリセット）
│       ├── epub/                 # EPUB生成・検証
│       ├── image/                # 画像処理
│       ├── parser/               # テキスト解析
│       ├── util/                 # ユーティリティ
│       ├── validator/            # EPUB検証ロジック
│       └── writer/               # EPUB3Writer（テンプレート駆動）
├── test/                          # Javaテストコード
│   └── com/github/hmdev/         # テスト（JUnit 4）
├── template/                      # Velocityテンプレート（EPUB生成）
│   ├── OPS/
│   │   ├── package.vm            # package.opf テンプレート
│   │   ├── toc.ncx.vm            # NCX (目次) テンプレート
│   │   ├── css/                  # CSSテンプレート（VelocityでINI値を反映）
│   │   └── xhtml/                # XHTML テンプレート
│   └── META-INF/
│       └── container.xml         # EPUB容器定義
├── test_data/                     # テスト用テキストファイル
├── presets/                       # デバイスプリセット（INI）
├── web/                           # Web小説サイト定義
├── .github/
│   ├── copilot-instructions.md   # Copilot指針（このプロジェクト特有）
│   └── workflows/                # GitHub Actions CI/CD
│       ├── ci.yml                # ビルド・テスト・EPUB生成・検証
│       └── test.yml              # ユニットテストのみ
├── build.gradle                   # Gradleビルド設定
├── gradlew / gradlew.bat         # Gradle Wrapper
└── README.md                      # 利用者向けドキュメント
```

---

## 技術スタック

### Java環境
- **Java**: 21（実行はJRE 21で可、開発/ビルドはJDK 21が必要）
- **ビルドツール**: Gradle 9.2.1（Wrapper 同梱）
- **テスト**: JUnit 4.13.2

### 主要ライブラリ（抜粋）
- **Apache Velocity 2.4.1**: テンプレートエンジン（EPUB生成）
- **JSoup 1.18.1**: HTML解析
- **Apache Commons**: CLI, Collections, Compress, Lang3
- **Junrar 7.5.5**: RAR解凍
- **Apache Batik 1.18**: 画像トランスコーディング（JAI代替）
- **SLF4J 2.0.16**: ロギング

### CI/CD
- **GitHub Actions**: ビルド、テスト、EPUB生成・検証の自動化
- **epubcheck 5.2.0**: EPUB 3 仕様検証（同梱しない。必要時はダウンロード）
- **xmllint**: XML形式検証

---

## ビルド・テスト

### ビルド

```bash
# JARファイル生成（src/のコンパイル）
./gradlew jar

# 配布パッケージ生成（ZIP/TAR.GZ）
./gradlew distZip  # または distTar

# クリーン（build/ディレクトリ削除）
./gradlew clean
```

生成物は `build/libs/` に出力されます。

### テスト実行

```bash
# 全テスト実行
./gradlew test

# 特定のテストクラスのみ実行
./gradlew test --tests "com.github.hmdev.config.CssTemplateRenderTest"

# テストカバレッジ計測（オプション）
./gradlew test --scan
```

テスト結果は `build/reports/tests/test/index.html` に出力されます。

### EPUB検証（開発者向け）

- ランタイム: JRE 21 で実行可。
- epubcheck は配布物に同梱しません（EPL-2.0 / GPL-2.0-or-later）。必要に応じて開発者が取得してください。

手順例（ローカル確認）:

```bash
# サンプルEPUB生成と検証（ネット接続必要）
./gradlew clean generateLocalSamples epubcheck -PepubDir=build/epub_local

# 既に epubcheck-5.2.0 をダウンロード済みの場合（例: build/tools 配下）
./gradlew epubcheck -PepubDir=build/epub_local -PepubcheckJar=build/tools/epubcheck-5.2.0/epubcheck.jar
```

取得方法（参考）:

```bash
mkdir -p build/tools
cd build/tools
curl -L -o epubcheck-5.2.0.zip https://github.com/w3c/epubcheck/releases/download/v5.2.0/epubcheck-5.2.0.zip
unzip epubcheck-5.2.0.zip
```

---

## テンプレート（Velocity）について

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
p.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
p.setProperty("resource.loader.file.path", projectRoot.resolve("template").resolve("OPS").toString());
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

### テンプレートファイル

| テンプレート | 用途 | 出力 |
|-------------|------|------|
| `package.vm` | EPUB メタデータ・マニフェスト | `package.opf` |
| `toc.ncx.vm` | 目次（NCX形式） | `toc.ncx` |
| `css/vertical_text.vm` | 縦書きテキスト CSS | `vertical_text.css` |
| `css/horizontal_text.vm` | 横書きテキスト CSS | `horizontal_text.css` |
| `xhtml/*.vm` | 本文 XHTML | `*.xhtml` |

### INI値の CSS 反映

INI設定ファイルの値（`font_size`, `line_height` など）は Velocity コンテキストに配置され、テンプレートで CSS変数として利用されます。

```velocity
:root {
  --font-size: ${fontSize}%;
  --line-height: ${lineHeight};
}
```

テスト時に INI→CSS 反映を検証：
- [test/com/github/hmdev/config/CssTemplateRenderTest.java](test/com/github/hmdev/config/CssTemplateRenderTest.java): 縦書きCSS
- [test/com/github/hmdev/config/HorizontalCssTemplateRenderTest.java](test/com/github/hmdev/config/HorizontalCssTemplateRenderTest.java): 横書きCSS

---

## EPUB 3.2 仕様への対応

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

保持テスト: [test/com/github/hmdev/epub/PackageTemplateKindleMetaTest.java](test/com/github/hmdev/epub/PackageTemplateKindleMetaTest.java)

---

## 対応している注記

### 基本的な注記

設定ファイルで対応：
- `chuki_tag.txt`: 注記 → ePubタグ変換定義
- `chuki_tag_suf.txt`: 前方参照型 → 開始終了型に変換
- `chuki_alt.txt`: 外字 → 代替文字マッピング
- `chuki_utf.txt`: 外字 → UTF-8マッピング
- `chuki_ivs.txt`: 外字 → IVS付きUTF-8マッピング
- `chuki_latin.txt`: ラテン文字 → UTF-8変換

### プログラムで処理

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

## 外字（がいじ）処理

青空文庫注記から外字を抽出・変換：

```
※［＃「字名」、U+6DB6］                          → Unicode直接指定
※［＃「字名」、第3水準1-85-57］                  → JISコード → UTF-8
※［＃「さんずい＋垂」、UCS6DB6］                → UCS形式
```

対応コードが無い外字は `chuki_alt.txt` で代替文字に置換。

---

## GitHub Actions CI

### ワークフロー一覧

#### ci.yml（ビルド・テスト・EPUB生成・検証）
- ビルド (Gradle)
- テスト実行 (JUnit)
- サンプル EPUB 生成（縦書き・横書き・複数プリセット）
- `epubcheck` で仕様検証
- OPF XPath 検証（UUID形式、書字方向の整合）
- アクセシビリティ非致命サマリ（img alt欠落件数）
- 生成 EPUB を Artifacts にアップロード
- 手動実行対応（workflow_dispatch + 入力値）

**手動実行:**  
GitHub → Actions → CI → Run workflow
- オプション入力:
  - `ini_font_size`: INI の font_size（既定: 115）
  - `ini_line_height`: INI の line_height（既定: 1.7）

#### test.yml（ユニットテストのみ）
- テスト実行 (JUnit)
- 手動実行対応

---

## Copilot指針

プロジェクト特有の開発方針は `.github/copilot-instructions.md` を参照してください。

主要な内容：
- Velocity の注入方式とテスト構成
- テンプレートリソース解決のベストプラクティス
- EPUB仕様の要点（テスト対象）
- 共通的なワーニング・トラブルシューティング

---

## 貢献ガイドライン

### バグ報告・機能提案

GitHub Issues で以下の情報を提供してください：

1. **バグの場合**
   - 環境（OS、Java バージョン）
   - 入力テキスト（or 最小再現例）
   - エラーメッセージ・ログ
   - 期待される動作 vs 実際の動作

2. **機能提案の場合**
   - 提案内容
   - ユースケース
   - 参考資料（あれば）

### プルリクエスト

1. fork して feature ブランチを作成
2. 変更・テスト追加
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

**解決**:
- FileResourceLoader で絶対パスを指定
- `Paths.get(".").toAbsolutePath().normalize()` で projectRoot を取得
- `VelocityTestUtils` ユーティリティを活用

### Windows パス問題

**症状**: `\` がエスケープで失敗

**解決**:
- `Paths` API を使用（自動的に / に統一）
- または `forward slash` を明示

### テスト不安定性

**症状**: ローカルでは成功、CI で失敗

**原因**: 
- ファイル I/O のタイミング
- リソース解決パスの依存

**解決**:
- 相対パスを避け、絶対パスまたはクラスパス指定
- テンプレート路を configurable に

---

## パフォーマンス最適化のヒント

1. **大容量テキスト変換時**
   - メモリを増やす: `java -Xmx2g ...`
   - 強制改ページを有効（ファイル分割）

2. **画像処理**
   - 縮小時の CPU 負荷が高い（Bicubic）
   - 余白除去は PNG で時間がかかる

3. **EPUB 生成速度**
   - Velocity テンプレートの最適化（大量ループを避ける）
   - 画像ストリーミング（大きい画像は逐次処理）

---

## リンク

- [AozoraEpub3 GitHub](https://github.com/Harusame64/AozoraEpub3-Java21)
- [オリジナルプロジェクト](https://github.com/hmdev/AozoraEpub3)
- [青空文庫](https://www.aozora.gr.jp/)
- [EPUB 3.2 仕様](https://www.w3.org/publishing/epub32/)
- [epubcheck](https://github.com/w3c/epubcheck)

---

## ライセンス

GPL v3 - 詳細は [README.md](README.md#ライセンス) を参照
