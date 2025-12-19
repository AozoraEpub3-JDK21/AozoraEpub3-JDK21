
# [仕様書] EpubCheck 統合および高度エラー解析機能

## 1. プロジェクト概要

**対象:** AozoraEpub3-JDK21 (Java製 EPUB変換ツール)
**目的:** 生成されたEPUBの規格準拠チェックを行い、エラー発生時に「修正すべき元の原稿やテンプレート」を日本語で具体的に指示する機能を実装する。
**役割:** あなたはシニアJavaエンジニアとして、以下の仕様に基づきコードを実装してください。

**対象バージョン:** EPUB 3.2 / EpubCheck 5.x（JSON出力対応版）

## 2. システム構成と処理フロー

```text
[Main App]
  │
  ├─ (1) EPUB出力プロセス
  │       ├─ [Release Mode] ──> 標準EPUB出力 (提出用)
  │       └─ [Debug Mode] ────> 標準EPUB + `aozora-source-map.json` (検証用)
  │
  ├─ (2) バリデーション実行 (ProcessBuilder)
  │       ↓
  │    [External: epubcheck.jar]
  │       ↓ JSON Output
  │
  └─ (3) 解析エンジン (Analysis Engine)
          ├─ JSON Parsing
          ├─ Source Mapping (逆引き特定)
          ├─ Translation (日本語化)
          ↓
      [UI: 診断レポート画面]

```

## 3. コアロジック: 逆引き解析の仕組み (Technical Core)

本機能の核心は、EpubCheckの出力結果と、アプリ独自のソースマップを **「EPUB内部のファイルパス」をキーにして結合（Join）すること** である。パスは区切り文字を`/`に正規化し、`OPS/`（本プロジェクトの標準構成）を前提とする。

### A. EpubCheckからの出力 (Input 1)

EpubCheck（`--json` または `-j` オプション）は、**EPUB内部のパス** に対してエラーを報告する。

```json
{
  "messages": [
    {
      "ID": "RSC-005",
      "severity": "ERROR",
      "message": "element \"div\" not allowed here...",
      "locations": [
        {
          "path": "OPS/text/chapter1.xhtml",  // <--- [Key]
          "line": 45
        }
      ]
    }
  ]
}

```

### B. アプリが生成するソースマップ (Input 2)

EPUB生成時に `OPS/aozora/aozora-source-map.json`（アプリ専用名前空間）として同梱する定義ファイル。初期版では「ファイル単位の逆引き」を対象とする。

```json
{
  "files": {
    "OPS/text/chapter1.xhtml": {       // <--- [Key]
      "sourceName": "第1章_出会い.txt",   // ユーザーに見せるファイル名
      "templateName": "novel_template_v2.html", // 修正すべきテンプレート
      "type": "TEXT_CONTENT"
    },
    "OEBPS/css/style.css": {
      "sourceName": "custom_style.css",
      "templateName": null,
      "type": "STYLESHEET"
    }
  }
}

```

### C. 解決ロジック (Resolution)

1. エラーログから `path` ("OEBPS/text/chapter1.xhtml") を取得。
2. ソースマップから同パスの情報を検索。
3. **「第1章_出会い.txt（テンプレート: novel_template_v2.html）」** という情報をユーザーへのアドバイスとして結合する。必要に応じてエンコード差異（URLエンコード・大文字小文字）を正規化して一致判定を行う。

## 4. クラス設計 (Java)

### 4.1. データモデル

* **`EpubErrorType` (Enum):**
* エラーコード（`RSC-005` 等）とカテゴリ（`RSC`/`OPF`/`NAV`/`PKG`/`CSS` など）を保持し、詳細文言は外部化（`i18n` リソース）する。頻出 20〜30 件は辞書を整備し、未定義コードは WARN として検知して追補可能にする。


* **`SourceInfo` (Class):**
* `sourceName`, `templateName`, `originalLineOffset` などを保持。



### 4.2. 機能クラス

* **`EpubValidator`:**
* `ProcessBuilder` を使用し `java -jar lib/epubcheck.jar target.epub --json -q` を実行（タイムアウト・終了コード・stderr を考慮）。
* 標準出力から JSON を取得し、Jackson / Gson でパースする（スキーマ差異に耐えるパーサ層を設計）。


* **`SourceMapper`:**
* EPUB（ZIP）内の `OPS/aozora/aozora-source-map.json` を読み込む。
* `resolve(String epubPath)` メソッドで `SourceInfo` を返す（区切り・大小文字・URLエンコードを正規化）。


* **`ErrorAnalyzer`:**
* Validatorの結果とMapperの情報を統合し、CLI/UI表示用の `DiagnosticReport` リストを作成する。



## 5. UI/UX 仕様

### 5.1. 出力設定（CLIオプション）

変換時のオプションとして以下を追加する。

* `--include-source-map`（デフォルト: 有効）: `OPS/aozora/aozora-source-map.json` を同梱する。
* `--no-source-map`: ソースマップを同梱しない（逆引きなしの翻訳のみ）。
* 将来的に `--release`（ストア提出用）を導入し、デバッグ用メタを一括で除去する。（初期リリースでは `--no-source-map` のみで十分）



### 5.2. 結果出力（段階導入）

* 初期版: CLIに診断レポート（テキスト/JSON/HTMLのいずれか）を出力。
* 次版: JDialog/JavaFX によるインタラクティブ画面（合格/不合格、リスト、詳細パネル）。
* 詳細パネルの例:
  > **エラー内容:** タグの不整合
  > **原因:** `<div>` タグが正しく閉じられていません。
  > **修正箇所:** 原稿 **「第3章.txt」**
  > **ヒント:** テンプレート **「novel_template.html」** の構造を確認してください。



## 6. 実装ステップ (Copilotへの指示用)

以下のフェーズに分けてコードを提示してください。

### Phase 1: バリデーターの実装

* EpubCheckを外部プロセスで起動し、JSONを取得・パースする `EpubValidator` クラスを作成する。
* Jackson または Gson ライブラリを使用する想定。

### Phase 2: 辞書データの構築

* `EpubErrorType` Enumを作成し、**主要なEpubCheckエラーコード（RSC/CSS/NAV/OPF/PKG 系など頻出 20〜30 件）** のカテゴリ・タイトルを保持。
* 詳細文言は `i18n` リソース化し、メッセージ差分に耐える設計にする。未定義コードは WARN として検知。

### Phase 3: ソースマップ生成と逆引きロジック

* EPUB出力処理のモックを作成し、ファイルパスのMapを作成してJSONに書き出すロジック。
* 読み込んだJSONを使ってエラーパスを解決する `SourceMapper` クラスの実装。

### Phase 4: UI統合

* 初期は CLI レポート出力、次段階で Swing/JavaFX による結果表示画面を実装。

## 7. 依存・配布・ライセンス

* 依存: `epubcheck.jar` を `lib/epubcheck.jar` として配布パッケージ（`gradlew dist`）に同梱する。
* ライセンス: サードパーティの告知は `THIRD-PARTY-NOTICES.txt` に追記し、必要に応じて `lib/LICENSE/` も同梱する。
* パッケージング遵守: `mimetype` は ZIP 先頭・無圧縮・内容 `application/epub+zip`。既存パッケージャでの維持をチェック項目に含める。

## 8. 受け入れ基準（Acceptance Criteria）

* 変換オプションでソースマップを同梱/非同梱できる（デフォルト同梱）。
* EpubCheck の JSON を取得・パースし、最低限「ファイル単位の逆引き」を付与した診断レポートを出力できる。
* 代表的なエラーコード（20 件以上）に対する日本語解説が表示される（`i18n` リソースベース）。
* CI でサンプル EPUB が `epubcheck` 合格し、失敗時は要約レポートが生成される。

## 9. テスト計画

* ユニット: `EpubValidator`（タイムアウト/終了コード/JSONパースの堅牢性）、`SourceMapper`（ZIP読み込み/パス正規化）、`ErrorAnalyzer`（結合ロジック）。
* フィクスチャ: 最小構成 EPUB（意図的な 1〜2 件の典型エラーを含む）を `test_data` に用意し、解析結果をアサート。
* 統合: `gradlew test` で OS 差異を `Paths` API で吸収。ネットワーク不要・パス独立。

## 10. 実装メモ（正規化と互換性）

* パスは `/` 区切りに正規化し、`OPS/` ベースで管理。URLエンコード/大小文字の差異はマッパ層で吸収。
* EpubCheck JSON はバージョンでフィールド差異がありうるため、パーサ層でスキーマ互換性を担保（例: `ID`/`code`、`locations[].path` 等）。
* 初期版は「ファイル単位の逆引き」。行単位の逆引き（`originalLineOffset`）は Velocity テンプレートとの整合を検討のうえ段階的導入。

---
