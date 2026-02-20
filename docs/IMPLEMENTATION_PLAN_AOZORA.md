# 青空文庫（aozora.gr.jp）対応 実装計画

作成日: 2026-02-18

## 概要

本計画は、AozoraEpub3に「青空文庫の作品ページ（HTML）をURL指定で直接変換」する機能を追加するための作業計画書である。現在の `WebAozoraConverter` はデータ駆動で `web/<FQDN>/extract.txt` を参照しており、青空文庫用のサイト定義がないためデフォルトでは変換できない。これを追加し、GUIからのURL貼り付け／ドラッグ&ドロップで動作するようにする。

## 目的

- 青空文庫の作品ページをURL指定で直接Aozoraテキストに変換できるようにする。
- GUIのWeb変換フロー（`AozoraEpub3Applet.convertWeb`）と整合させ、ダウンロード／キャッシュ／テキスト化を行う。

## 範囲

- 対象ドメイン: `aozora.gr.jp`（作品本文ページ、目次ページ）
- 既存の `WebAozoraConverter` を用いた実装（新クラスは不要）
- 必要な構成ファイル: `web/aozora.gr.jp/extract.txt`（必須）、`web/aozora.gr.jp/replace.txt`（任意）

## 前提・注意点

- 青空文庫のHTML構造は作品や時期で差があるため、複数の抽出ルールを用意する可能性がある。
- 著作権/ライセンスに注意（青空文庫は多くがパブリックドメインまたは利用許諾ありだが、個別作品の権利表示を尊重する）。

## 成果物

- `web/aozora.gr.jp/extract.txt` サイト定義
- （必要なら）`web/aozora.gr.jp/replace.txt` 置換パターン
- 単体テスト・統合テスト（`test/`に追加）
- ドキュメント更新（本ファイル、READMEの追記）

## 実装ステップ（高水準）

1. 青空文庫の代表的なページを収集しHTML構造を解析（目次・本文・章タイトル・注記・表紙画像・刊行情報など）
   - 目標: 代表サンプル3〜5件
2. `extract.txt` を作成（CSSセレクタ/正規表現を用いた抽出ルール）
   - 抽出ID: `HREF`, `TITLE`, `AUTHOR`, `COVER_IMG`, `CONTENT_ARTICLE`, `SUBTITLE_LIST`, `SUB_UPDATE`, `CONTENT_UPDATE_LIST` 等
3. 必要な文字列置換ルールを `replace.txt` に定義（タグの除去、特殊注記の変換など）
4. `WebAozoraConverter` の変換を試行し、出力のAozoraテキストを確認・調整
5. 単体テスト作成: `WebAozoraConverter` の抽出・docToAozoraTextの主要処理をテスト
6. GUIでのエンドツーエンド確認（`AozoraEpub3Applet`のWeb変換フロー）
7. ドキュメント更新・PR作成

## 詳細タスク（チェックリスト）

- [x] 代表サンプルの収集（例: ある一作品の目次ページと本文ページ）
- [x] `extract.txt` 初版作成
- [x] ローカルキャッシュでの変換検証
- [x] `replace.txt` で必要な置換追加
- [x] 自動テスト（単体・統合）追加
- [x] 実URLでのダウンロード・EPUB化・バリデーション検証
- [ ] GUI操作でのマニュアル確認
- [ ] リスク評価と回避策記載
- [ ] PR作成（コミットメッセージは日本語）

## テスト観点

- 目次から全話のURLが正しく抽出されること（※fixtureで検証）
- 各話本文が `docToAozoraText` により期待する青空文庫マークアップ（ルビ・注記・改行等）になること
- 生成される `converted.txt` にタイトル・著者・掲載URLが含まれること
- 表紙画像が `cover.jpg` / `converted.png` として保存されること（該当作品）
- 更新チェック（更新分のみ変換／スキップ）フラグが正しく動くこと

## 具体的受け入れ条件（定量）

- 目次サンプルで少なくとも3件以上の章URLを抽出できること
- `convertToAozoraText(...)` 実行後、出力先に `converted.txt` が存在し先頭10行にタイトル行があること
- 単体fixture（`test_data/aozora/`）を用いた自動テストがネットワーク不要で成功すること
- 既存の `gradlew test` を通過し、既存機能に回帰がないこと

## 自動テスト仕様（追加案）

1) 単体テスト — `WebAozoraConverterAozoraUnitTest`
   - fixtures: `test_data/aozora/index.html`, `test_data/aozora/ch01.html`
   - 検証: `createWebAozoraConverter` が `null` でないこと、`getExtractElements` でHREFが取得できること、`docToAozoraText` の出力に章見出し・段落が含まれること

2) 統合テスト — `WebAozoraConverterAozoraIntegrationTest`
   - fixturesローカルキャッシュを作成して `convertToAozoraText` を実行
   - 検証: `converted.txt` の存在、`cover.jpg`（存在すれば）の出力、`converted.txt` 内にタイトルと著者が含まれること

3) E2Eスモーク（任意・手動/CIオプション）
   - GUIの `convertWeb` フローでローカルfixtureを読み込みEPUBが生成されることを人手で確認（将来的にヘッドレス化検討）

## テスト用 fixture（リポジトリに追加）

- `test_data/aozora/index.html` — 目次（複数章への相対リンク）
- `test_data/aozora/ch01.html` — 第1章（本文の代表例）
- `test_data/aozora/ch02.html` — 第2章（必要に応じて）
- `test_data/aozora/cover.jpg` — 表紙サンプル（任意）

> CI注意: テストはネットワーク非依存とし、fixture を使って `gradlew test` で動作すること。

## `replace.txt` テンプレ（例・調整必須）

- 目的: ページ固有のヘッダ／フッタ／余分な案内文を除去するための正規表現を定義
- 形式: `ExtractId\t<pattern>\t<replacement>`（`web/<domain>/replace.txt` の書式に合わせる）

例:
```
CONTENT_ARTICLE	^.*底本：.*$	
CONTENT_ARTICLE	^\s*目次へ.*$	
CONTENT_ARTICLE	<small class="credit">.*?</small>	
```
(上記は例なので実ページ検証で調整してください)

## 見積（詳細・概算）

- 代表サンプル収集・fixture作成: 0.5日
- `extract.txt` / `replace.txt` 作成・調整: 0.5〜1日
- 単体・統合テスト作成: 0.5日
- GUI動作確認・E2E: 0.5日
- ドキュメント/CI更新・PR作成: 0.5日

## リスクと対応

- 青空文庫のHTML差異: 複数パターンを用意して柔軟に対応（fixtureを増やす）。
- JSレンダリング不要（青空文庫は静的HTMLなので問題なし）。
- ネットワーク依存テストはCIで禁止し、すべてfixtureで代替する。


## 参考（作業メモ）

- 既存の `web/` 下の `extract.txt` をテンプレとして流用する。
- テストは `test/WebAozoraConverterApiTest.java` の構成を参考に進める。

---

次の作業: まず代表ページのHTMLを収集して `web/aozora.gr.jp/extract.txt` の草案を作成します。
