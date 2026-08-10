# v1.5.0-jdk21 リリース計画と作業状態

最終更新: 2026-08-11

セッションをまたいで作業を再開するための状態記録。**残件は本書と
[`code-audit-followups.md`](code-audit-followups.md) を正とする。**

---

## 1. このリリースの中身

v1.4.0-jdk21（2026-08-01）以降に master へ入ったもの + 本セッションで作った未マージ分。

| 区分 | 内容 |
|---|---|
| 主役 | **EPUB プレビュー機能**（Phase 1・2 全段。#55 / #57 / #59 / #62〜#65 / #67 / #69 / #70） |
| 主役 | **FlatLaf による GUI モダン化**（本セッションで実装。下記 §3） |
| 修正 | ハーメルンの変換不能（#72）、CLI の目次既定値（#74 / 項目 22） |
| 修正 | カクヨムの新着話取りこぼし（項目 21）、GUI/CLI 既定値ドリフト（項目 24）、`ChapterPattern` の警告（項目 23）、`GothicUseBold` のタイポ（項目 25 の一部）、ini 探索パスの非対称（項目 26） |
| ドキュメント | CLI 記述の実装不一致を全面修正、`--preview` / `--library` / 本棚の説明追加 |

`RELEASE_NOTES.md` の「未リリース（v1.5.0-jdk21 予定）」節に下書き済み（PR #78 で
項目 21 / 23 / 24 / 25 / 26・同梱 ini 全キー化・FlatLaf の互換性影響まで反映済み）。
**リリース時はその節をバージョン節に書き換えるだけでよい**。

---

## 2. ブランチと PR の状態（2026-08-11 時点）

全ブランチが 3 ゲート（A=Codex / B=Opus / C=Fable）+ フレッシュコンテキストの
検証ラウンドを**指摘ゼロで通過**し、push・PR 作成済み。

| PR | ブランチ | 内容 | 状態 |
|---|---|---|---|
| #75 | `fix/ini-default-drift` | 項目 23/24/25(一部)/26。`SettingDefaults` 拡張、同梱 ini 全キー版（GUI 初期値）、ini 探索順、`-t` ヘルプ修正 | CI 待ち → マージ |
| #76 | `fix/kakuyomu-toc-cache-collision` | 項目 21 + symlink 再検証・早期失敗 | CI 待ち → マージ |
| #77 | `feat/flatlaf-ui`（worktree ブランチを rename して push） | FlatLaf 一式 | CI 待ち → マージ |
| #78 | `docs/preview-cli-and-en-options` | CLI ドキュメント修正 + リリースノート下書き拡充 | CI 待ち → マージ |

**マージ順（厳守）**: #75 → #76 → #77 → #78。#78 のドキュメントは #75/#77 の実装を
説明しているため必ず最後。`docs/ini-drift-inventory` ブランチは #75 に取り込み済みで PR 不要。
マージ判断はユーザーから Fable に委譲済み（2026-08-10）。

---

## 3. FlatLaf（GUI モダン化）の決定事項

設計書: [`flatlaf-plan.md`](flatlaf-plan.md)

- FlatLaf **3.7.2**。テーマは FlatLightLaf / FlatDarkLaf
- **既定はライト固定**（ユーザー判断 2026-08-10。README のスクリーンショットと初回起動の見た目を一致させるため）。`UiTheme=system` で OS 追従も選べる
- 切替は ini キー `UiTheme`（`system` / `light` / `dark`）+ GUI のプロファイル行右端のコンボ（即時反映）
- レイアウトの固定ピクセル（20〜48px、約 46 か所）を全廃し、実測メトリクスから導出。フレーム既定 900×680 / 最小 720×520
- フォントは UIDefaults 全走査置換をやめ `defaultFont` 1 キーに一本化（日本語フォント選択ロジックは維持）
- **Windows のみ目視確認済**（全 8 タブ + 詳細設定 8 パネル + 各ダイアログをダーク・ライト両方）。**macOS / Linux は未検証**
- EDT 化（`jFrame` 生成が EDT 外）は意図的にスコープ外

---

## 4. 設計判断の記録（迷ったら本節を読む）

### 4.1 同梱 `AozoraEpub3.ini` は「v1.4.x と同じ GUI 起動状態」（最終決定・#75 で実装）

経緯: 当初、参照 EPUB との byte 一致を守るため 8 キーだけ「従来出力を維持する値」にしたが、
ゲート B で **GUI も同梱 ini を読む**（`setPropsSelected` が `containsKey` 判定）ことが判明。
そのままだと新規展開した GUI が自動改ページ OFF 等で起動してしまう。

**最終決定（ユーザー判断 2026-08-10）**: 同梱 ini は GUI 初期値ベースに戻し、
`JavaAozoraVsReferenceTest` には専用 ini `test_data/reference_comparison.ini`
（旧 23 キー + 旧 CLI ハードコード実効値 8 キーの明示）を `-i` で渡して契約を独立させる。
これで「新規展開した GUI の起動状態 = v1.4.x と同一」かつ「参照 EPUB との byte 一致」が両立。
同梱 ini のまま CLI 変換していた場合の出力変化はリリースノートに記載済み。
参照 EPUB を作り直すときだけ `reference_comparison.ini` を更新する。

### 4.2 GUI 初期値の実測フィクスチャ

`test_data/gui_default_settings.ini` は、GUI をまっさらな作業ディレクトリで起動 → 終了させて
実際に保存された ini から実行時状態を除いたもの（116 キー）。
`SettingDefaultsGuiParityTest` がこれと既定値テーブルの一致を機械的に検証する。
**GUI のウィジェット初期値を変えたらフィクスチャを取り直すこと。**

### 4.3 実測値（項目 24 の挙動変更）

| 条件 | before | after |
|---|---|---|
| ini なし・1.8MB の見出し無しテキスト | 本文 XHTML 1 枚、表題ページなし | 本文 XHTML 5 枚、表題ページあり |
| narou.rb 連携先の実 ini（123 キー） | \_ | `dcterms:modified` 以外の差分ゼロ |

---

## 5. リリースまでの残作業

手順の詳細は [`release-procedure.md`](release-procedure.md) を**必ず最初から最後まで読む**こと。

- [x] 各ブランチの 3 ゲート + 検証ラウンド → push → PR（#75〜#78）
- [ ] PR #75 → #76 → #77 → #78 の順で CI 確認 → マージ
- [x] **README / docs のスクリーンショット撮り直し**（FlatLaf 適用後、本ブランチに含む）
- [ ] README / `docs/index.md` の「※プレビュー機能は未リリースです」注記を削除
      （`docs/en/index.md` には注記が入っていないので追加不要）
- [ ] バージョン更新 5 ファイル: `src/AozoraEpub3.java` / `NarouApiClient.java` /
      `build.gradle` / `docs/index.md` / `docs/en/index.md`
- [ ] `RELEASE_NOTES.md` の「未リリース」節をバージョン節へ（互換性影響は #78 で記載済み）
- [ ] `gradlew --no-daemon clean test` → `dist` → 配布物の必須ファイル目視（手順書 §3.4）。
      **FlatLaf の jar が増えるので `unzip -l` のサイズ確認も**
- [ ] リリース前 E2E ゲート（手順書 §2.1.1）。なろう / 青空文庫 / キャッシュ再利用 /
      旧キャッシュ互換 / 終了コード両方向 / `.exe` 経由 / epubcheck / 生成 EPUB の目視
- [ ] `.NET` ポート `JavaComparisonTests` 5/5（本セッションで一度 PASS 確認済み。マージ後に再確認）
- [ ] サブエージェントによる最終チェック（CLAUDE.md §7）→ タグ → `gh release create`

### リリースノートに必ず書くこと

- **CLI の目次既定値が GUI に揃った**（項目 22）。`-i` で `Chapter*` キーを持たない自作 ini を
  使っている場合は出力が変わる。戻すには ini に明示する
- **ini にキーが無いときの CLI 既定値が GUI に揃った**（項目 24）。自動改ページ・表題ページなどが
  効くようになる。**同梱 ini も GUI 初期値ベースの全キー版になったため、同梱 ini のまま
  CLI 変換していた場合も出力が変わる**（§4.1。従来出力へ戻す 8 キーはリリースノートに記載）
- **CLI が jar と同じ場所の ini を読むようになった**（項目 26）。配布フォルダ外から実行していた
  利用者は、これまで無視されていた同梱 ini が効くようになる
- ハーメルンは**旧キャッシュがあると初回だけ全話再取得**（#72）
- GUI の見た目が変わる（FlatLaf）。`UiTheme` で切替可能
- #69 の GUI 既存バグ修正（終了時に設定が保存されない / 数値書式の locale 依存で余白欄が壊れる）

---

## 6. 既知の未対応（本リリースには入れない）

`code-audit-followups.md` を参照。特に:

- 項目 18 / 19 / 20 / 25（残り）: `BodyMarginUnit` の連結不正、`ImageFloatType` の 1 ずれ、
  CLI 未配線の GUI 設定 5 件、柱注記の未対応
- プレビューの `Host` ヘッダ検証なし（DNS リバインディングへの多層防御。トークンがあるため実害は低い）
- FlatLaf: `JConfirmDialog` の固定 420px、`NarrowTitledBorder` の固定インセット、EDT 化

---

