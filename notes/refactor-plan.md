# AozoraEpub3 リファクター計画

## 背景
- `src/AozoraEpub3.java` に CLI/設定読み込み、入出力制御、アーカイブ展開、Writer 設定など多数の責務が集中し可読性が低下。
- 機能追加とリファクターを分離し、段階的に責務分割する。

## 分割方針（役割別）
- **CLI/設定読み込み**: commons-cli Options 定義、INI/CLI マージ。
- **出力パラメータ組み立て**: 画像リサイズ・スタイル・TOC・ページ分割など Writer への setter 呼び出し。
- **入力判定とテキスト抽出**: zip/rar/txt/cbz 判定、テキスト件数取得、InputStream 提供。
- **1ファイル処理フロー**: BookInfo 生成、画像のみ判定、表紙決定、Writer 委譲。
- **出力ファイル命名**: 作者/タイトル/拡張子からのファイル名生成。

## 新しい構造イメージ（パッケージ案）
1. `com.github.hmdev.cli`
   - `CliOptionsFactory` (Options 構築)
   - `CliConfigMerger` (CLI+INI を `ConversionConfig` に統合)
2. `com.github.hmdev.config`
   - `ConversionConfig` (縦横、画像リサイズ、章設定、カバー指定などを保持)
   - `PresetLoader` (AozoraEpub3.ini 読み込み)
3. `com.github.hmdev.io`
   - `ArchiveTextExtractor` (zip/rar 切替、txt 件数、InputStream 提供)
   - `CoverResolver` (同名画像探索・指定パス検証)
   - `OutputNamer` (出力ファイル名生成)
4. `com.github.hmdev.pipeline`
   - `ConversionOrchestrator` (1ファイル処理手順の集約)
   - `WriterConfigurator` (Epub3Writer/Epub3ImageWriter へのパラメータ適用)
5. `com.github.hmdev.app`
   - `AozoraEpub3Cli` (main: 設定構築 → オーケストレーター実行)

## 進め方（小さなステップ）
1. 回帰テストの足場を用意
   - 出力ファイル名生成、zip/rar テキスト検出、カバー指定 (0/1/URL/同名画像) の振る舞いをユニット化。
2. Writer へのパラメータ組み立てを `WriterConfigurator` に抽出（副作用なしメソッド）。
3. `ArchiveTextExtractor` に zip/rar/txt 処理を移動し、呼び出し元は抽象 API のみ利用。
4. `OutputNamer` と `CoverResolver` を切り出し、フロー中の条件分岐を削減。
5. CLI+INI 統合を `ConversionConfig` にまとめ、main は「設定構築→オーケストレーター実行」に縮小。
6. `AozoraEpub3.java` を薄くし、`AozoraEpub3Applet` も新構成を呼ぶだけに調整。

## テスト方針
- ユニット: `ArchiveTextExtractor` (zip/rar テキスト検出)、`OutputNamer` (ファイル名生成)、`CoverResolver` (パス解決) を個別テスト。
- スモーク: 小さな txt/zip を変換する CLI テストを追加し、既存挙動の回帰を確認。

## 注意事項
- テンプレートパスは相対のまま維持（jarPath+template/）。
- Velocity 初期化を二重化しない。既存 Writer 依存は温存。
- CLI オプションの互換性を壊さない（名前・デフォルトは維持）。
