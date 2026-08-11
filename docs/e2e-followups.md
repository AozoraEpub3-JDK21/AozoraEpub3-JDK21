# E2E 検証の残件

リリース前 E2E をチェックリスト（`docs/release-procedure.md` §2.1.1）で運用開始したうえでの、
自動化・整備の残件。起票日: 2026-07-25

## 初回実施の結果（2026-07-25、v1.3.7 リリース前）

`docs/release-procedure.md` §2.1.1 を新設して初めて実施した結果。**回帰はゼロ、既存バグを 1 件発見**。

| 検証項目 | 結果 |
|---|---|
| 青空文庫 URL 変換（`1567_14913.html`） | ✅ 成功、exit 0 |
| **v1.3.6 キャッシュとの互換** | ✅ HTML キャッシュのハッシュが完全一致、再ダウンロードなし |
| EPUB 出力（v1.3.6 vs master） | ✅ 差分はタイムスタンプ 2 箇所のみ（`dcterms:modified` と `変換日時`） |
| なろう 146 話・初回 | ✅ 全話取得（3 秒間隔、8 分超） |
| **なろう・2 回目（キャッシュヒット）** | ✅ **11 秒**、章の再ダウンロード 0 件、EPUB は 760,729 bytes で完全一致 |
| 成功時の終了コード | ✅ 0 |

**最大の関心事だった「監査 #12/#13/#14 でキャッシュパス生成を 3 回変更した影響」は完全にクリア**。
既存ユーザーのキャッシュが無効化される事態は発生しない。

**発見した既存バグ**: 出典 URL の `<a href>` に縦中横注記が混入する（`docs/code-audit-followups.md` の監査 #15）。
v1.3.6 にも存在するため回帰ではないが、**ユニットテストでは検出できずリリース前 E2E で初めて見つかった**もので、
この検証を導入した価値を示す実例になった。

## 2 回目の実施結果（2026-07-25、監査 #15 修正後・配布物での最終確認）

1 回目で発見した監査 #15（出典 URL の href 破損）を修正したうえで、**`gradlew dist` で生成した
配布 ZIP を展開し `AozoraEpub3.exe` 経由**で再実施した結果。

| 検証項目 | 結果 |
|---|---|
| `.exe` 経由の青空文庫 URL 変換（新規キャッシュ） | ✅ EPUB 22,993 bytes、exit 0 |
| `.exe` 経由のなろう変換（既存キャッシュ 147 話） | ✅ 「更新はありません」、章の再ダウンロード 0 件 |
| **監査 #15 の修正確認（実 EPUB）** | ✅ 壊れた href **1 件 → 0 件**、表示テキストの `<span class="tcy">` は維持 |
| 修正前後の EPUB 差分 | ✅ 760,729 → 760,706 bytes（差 23 bytes = 注記記法の除去分）、エントリ数 159 で同一 |
| epubcheck 5.3.0 | ✅ 2 ファイルとも 致命的エラー 0 / エラー 0 / 警告 0 |
| 目視確認（目次・本文冒頭・出典リンク） | ✅ 目次 148 項目正常、ルビ正常、文字化けなし |
| 失敗時の終了コード | ✅ 入力ファイル不在で exit 1 |
| 成功時の終了コード | ✅ 0 |

**発見した既存バグ 2 件**（いずれも本リリースの回帰ではない）:

- CLI `-url` に zip URL を直接指定すると変換できない（GUI 経路にしかない分岐）。
  `docs/code-audit-followups.md` の「16.」に起票し、**v1.4.0 で修正済み**
  （判定・ダウンロードを `ArchiveUrlUtils` に共通化し、CLI はローカル zip 入力と同じ経路で変換する）
- `generateLocalSamples` の 1 件が epubcheck `RSC-007`（タイトルページの外字画像が `<img src="null"/>`）。
  同「17.」に起票し、**v1.3.7 で修正済み**。CI がこれを検出できていなかった理由も同項に記録した

**運用上の注意**: `gradlew --no-daemon clean` の後は `build/tools/` の epubcheck も消えるため、
`setupEpubcheck` の自動ダウンロードが失敗すると `epubcheck` タスクが「N ファイルで失敗」という
紛らわしいメッセージで落ちる（実体は epubcheck jar 不在）。
`curl -sL -o build/tools/epubcheck-5.3.0.zip https://github.com/w3c/epubcheck/releases/download/v5.3.0/epubcheck-5.3.0.zip`
で手動取得して展開すれば通る。`tools/epubcheck-5.2.0.jar`（未追跡・9 bytes の `Not Found`）は壊れているので使わないこと。

## 背景

`generateLocalSamples`・ユニットテスト・`.NET` ポート比較テストは**いずれもネットワークを使わない**ため、
「URL からダウンロード → キャッシュ生成 → 章結合 → EPUB 出力」の実経路が自動テストで一度も通っていない。
`AozoraRealTest` 等のネットワーク依存テストは CI で実行されない。

このため v1.3.7 では手動チェックリストで補うことにしたが、恒久的には自動化したい。

## 1. localhost フィクスチャサーバによる E2E（優先）

**方針**: 実サイトにアクセスせず、ローカル HTTP サーバ + 保存済み HTML フィクスチャで
Web 変換の全経路を決定論的に回す。

**実現可能性の根拠**: `WebAozoraConverter.createWebAozoraConverter` は URL の FQDN から
`web/<fqdn>/extract.txt` を引く。したがって**テスト専用の configPath に `localhost` 用 `extract.txt` を置けば**、
`http://localhost:<port>/...` を入力にして本番と同じ経路（ダウンロード → キャッシュ → 章結合 → EPUB）を
通せるはず。

- 既存の `test_data/aozora/*.html` フィクスチャが流用できる可能性がある
- サーバは JDK 標準の `com.sun.net.httpserver.HttpServer` で足りる
- CI に載せられる（外部依存なし・レート制限なし・スクレイピングの礼儀の問題なし）

**未確認**: 上記の configPath 差し替えが実際に機能するか。**プロトタイプで検証が必要**。

**トレードオフ**: フィクスチャは撮った時点の HTML なので、実サイトの HTML 変更には追従できない。
実サイト変更の検出は引き続きリリース前チェックリスト（手動）に頼る。
両者は代替関係ではなく補完関係。

**CI での実サイトアクセスは非推奨**: 不安定・レート制限・スクレイピングの礼儀に加え、
CLAUDE.md の「テストコード内でネットワーク呼び出しをしない」方針と矛盾する。

## 2. `AozoraRealTest` の JUnit + Assume 化

`CLAUDE.md` のテスト一覧では「実ネットワークテスト(assume でスキップ)」と記載されているが、
**実際には JUnit テストではなく `main()` を持つクラスで、`Assume` による制御もない**（2026-07-25 時点）。

E2E 整備のタイミングで JUnit 4 + `Assume.assumeTrue(isNetworkAvailable())` の形に揃えると、
`gradlew test` から一貫して扱えるようになる。あわせて CLAUDE.md の記述も実態に合わせて修正する。

## 3. キャッシュ後方互換の自動検証

`CharUtils.escapeUrlToFile` / `replaceInvalidFileChars` を変更すると、
**既存ユーザーのキャッシュが全て無効化されて再ダウンロードが走る**リスクがある
（監査 #12 / #13 / #14 で実際に触った）。

現状は「実 URL 形の出力が 1 文字も変わらないこと」をユニットテストで固めているが、
**キャッシュディレクトリ構造レベルでの後方互換**は手動確認（§2.1.1）に頼っている。

1 のフィクスチャサーバが実現すれば、「旧バージョンで生成したキャッシュを流し込んで
再ダウンロードが発生しないこと」を自動化できる。

## 4. `HamelnE2ETest` の hameln_nochapter が削除済み作品を指している — ✅ 対応済（2026-08-11）

2026-08-11 の dogfood で判明。`test/HamelnE2ETest.java` の hameln_nochapter ケースが使う
`https://novel.syosetu.org/7/` は**投稿者により削除済み**（ハーメルンは「投稿者が削除、
もしくは間違ったアドレスを指定しています」ページを HTTP 200 で返すため、変換は
「各話のリンク先URLが取得できません」で失敗する）。

**対応**: `https://novel.syosetu.org/422019/`（リリカルAnswer、3 話・章なしフラット
話リスト・年齢制限ゲートなし）へ差し替え。差し替え後に
`gradlew test --tests HamelnE2ETest.testHamelnNoChapterWork -DhamelnE2E=true` で
実変換成功（EPUB 34,113 bytes、exit 0）を確認済み。
章あり `402358`（137 話）は 2026-08-11 時点で実在・変換成功を確認済み。
検証用 URL の一覧は `memory/feedback_dogfood_real_sites.md` にもある。

## 関連

- `docs/release-procedure.md` §2.1.1 — リリース前 E2E チェックリスト
- `docs/code-audit-followups.md` — #12 / #13 / #14（キャッシュパス生成の変更履歴）
- `CLAUDE.md` の「テスト計画・方針」
