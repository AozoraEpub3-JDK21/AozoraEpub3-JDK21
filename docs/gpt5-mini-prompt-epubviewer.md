# GPT‑5 mini — EpubViewerFrame 実装プロンプト（AozoraEpub3 用）

## 概要
あなたは Java 21 のエキスパートです。既存の Swing アプリケーション（AozoraEpub3）に対して、EPUB ファイルを表示するサブウィンドウ `EpubViewerFrame.java` を追加してください。

リポジトリの前提:
- Java 21（Gradle toolchain）
- 既存 GUI は Swing（`AozoraEpub3Applet` がメイン）
- テンプレートや出力は既存のプロジェクト規約に従うこと（例：コミットメッセージは日本語）

---

## 目的（あなたが行うこと）
1. 新規クラス `EpubViewerFrame.java` を `src/` に追加する。
2. `build.gradle` に必要な依存を追加する（epub4j と JavaFX）。
3. 単体で起動して手動確認できること（`main()` を含めるかテストで起動可能）。

---

## 必須要件（厳守）
- クラスは `javax.swing.JFrame` を継承すること。
- コンストラクタは `File` または `String`（epub のパス）を受け取るオーバーロードを用意すること。
- UI 構成:
  - Swing の `JFXPanel` を使って JavaFX の `WebView` を埋め込む。
  - JavaFX 操作は必ず `Platform.runLater(...)` で行うこと。
  - Swing 側の初期化/更新は EDT（SwingUtilities）で行うこと。
- EPUB 読み込み:
  - 依存ライブラリ: `io.documentnode:epub4j-core:4.0.0`（`EpubReader` を利用）
  - EPUB から「最初の spine（本文）に対応する HTML」を取得して表示すること。
  - 重要: 相対パスの画像/CSS を正しく表示するために、EPUB を一時ディレクトリに展開して `file:///` ベースで読み込むこと（推奨）。
- エラーハンドリング: ファイル不存在／読み込み失敗はユーザーにダイアログで通知し、ログにも出すこと。
- 縦書きテスト: `writing-mode: vertical-rl` を含む HTML が WebView で表示できること（フォントはデフォルトで可）。
- 必要な `import` 文は全てソースに含めること。

---

## 実装の細部（期待する振る舞い）
- UI:
  - Frame タイトルに開いたファイル名を表示。
  - シンプルなツールバー（読み込み状態、リロード、閉じる）を含めると良い。
- スレッド設計:
  - EPUB の読み取りとファイル展開はバックグラウンドスレッド（`SwingWorker` 等）で行い、UI 更新は EDT または JavaFX Thread を介して行う。
- 一時ファイル:
  - OS の一時フォルダに epub を展開（例: `Files.createTempDirectory("epubviewer-")`）。ウィンドウ閉鎖時に非同期で削除。
- ロギング/通知:
  - 進捗やエラーは簡易的に `JOptionPane` と `System.err` / logger で出す。
- テスト可能性:
  - EPUB 展開と「最初の spine HTML 検出」ロジックは private メソッドに分け、ユニットテストを書きやすくする。

---

## build.gradle に追加すべき依存（必ず追加）
```gradle
// EPUB ライブラリ
implementation 'io.documentnode:epub4j-core:4.0.0'

// JavaFX（WebView + Swing ブリッジ）
implementation 'org.openjfx:javafx-controls:21'
implementation 'org.openjfx:javafx-swing:21'
implementation 'org.openjfx:javafx-web:21'

// CI/配布向けに各プラットフォームの runtime classifier を追加（推奨）
runtimeOnly 'org.openjfx:javafx-controls:21:win'
runtimeOnly 'org.openjfx:javafx-controls:21:mac'
runtimeOnly 'org.openjfx:javafx-controls:21:linux'
```
> 注意: JavaFX はネイティブライブラリを含むため、CI／配布の際はプラットフォーム別ランタイムの確認を行うこと。

---

## 期待するファイル出力
- `src/EpubViewerFrame.java`（新規）
- （任意だが推奨）`test/com/.../EpubViewerFrameTest.java` — 展開・first-spine 検出のユニットテスト
- `build.gradle` の依存追加（コミットあるいは PR にて）

---

## 受け入れ基準（Acceptance criteria）✅
- 指定した `.epub` を開くと最初の本文 HTML が WebView に表示される。
- 画像・CSS（相対パス）が正しくレンダリングされる。
- `writing-mode: vertical-rl` を含む HTML の縦組表示が確認できる。
- 例外発生時にユーザーにダイアログで通知される。
- `./gradlew test` が通る（追加したユニットテストがあれば含む）。

---

## コーディング/PR のルール（必ず守る）
- 変更は最小限に留める。大きな修正は別 PR で。
- テンプレートや Velocity の初期化慣例に従う（今回直接関係しないが一貫性を保つ）。
- 既存のスタイルに合わせる（簡潔なヘルパーを作成して大きなメソッドを避ける）。
- ライセンスヘッダは追加しない。
- Git コミットメッセージは日本語で記述（例: "epub ビューアフレームを追加 — EpubViewerFrame.java"）。

---

## サンプル実装ヒント（モデルが従うべきコードスニペット）
```java
// 主要な API を想定した例（実装時は import を正しく揃えること）
public class EpubViewerFrame extends JFrame {
    public EpubViewerFrame(File epubFile) { ... }
    public EpubViewerFrame(String path) { this(new File(path)); }

    private void initUI() {
        JFXPanel fxPanel = new JFXPanel(); // Swing thread
        add(fxPanel, BorderLayout.CENTER);
        Platform.runLater(() -> {
            WebView webView = new WebView();
            fxPanel.setScene(new Scene(webView));
        });
    }

    private Path extractEpubToTemp(File epubFile) throws IOException {
        // epub4j の EpubReader を使い、OPF/spine などを解析してファイルを一時展開
    }
}
```

---

## テスト手順（手動）
1. `./gradlew jar` を実行してビルドする（JavaFX 依存が解決されることを確認）。
2. `EpubViewerFrame.main(new String[]{"path/to/sample.epub"})` で起動して表示を確認。
3. 画像・CSS の相対パスが正しく読み込まれることを確認。
4. 縦書き（writing-mode）を含む EPUB を開いて縦組が表示されることを確認。

---

## 例: 期待されるコミットメッセージ（日本語）
- "EPUB ビューアを追加: EpubViewerFrame を実装（epub4j + JavaFX）"
- "build.gradle に JavaFX と epub4j 依存を追加"

---

> 補足: 実装中に外部ライブラリの API 名（`EpubReader` のパッケージ名等）が不明な場合は、`io.documentnode:epub4j-core:4.0.0` の Javadoc または IDE の自動インポートで正しい FQN を解決してください。

---

完了したら、変更点の一覧、追加したテスト、及び手動テストの手順を PR の説明に含めてください。