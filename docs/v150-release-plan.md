# v1.5.0-jdk21 リリース計画と作業状態

最終更新: 2026-08-10

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

`RELEASE_NOTES.md` の「未リリース（v1.5.0-jdk21 予定）」節に下書き済み。
**リリース時はその節をバージョン節に書き換えるだけでよい**（項目 21 / 24 / 26 の追記は必要）。

---

## 2. 未マージのブランチ（すべてローカル、未 push）

| ブランチ | コミット数 | 内容 | ゲート |
|---|---|---|---|
| `fix/ini-default-drift` | 4 | 項目 24 / 23 / 25 / 26。`SettingDefaults` 拡張、同梱 ini 全キー化、ini 探索修正 | A・B 済（指摘なし）→ **C 未実施** |
| `fix/kakuyomu-toc-cache-collision` | 1 | 項目 21。カクヨムの新着話取りこぼし | A・B 済（指摘なし）→ **C 未実施** |
| `docs/preview-cli-and-en-options` | 3 | CLI ドキュメントの実装不一致修正 + プレビュー説明 | 事実確認ゲート・B 済 → **C 未実施** |
| `docs/ini-drift-inventory` | 1 | ini 全キー棚卸しの記録（`fix/ini-default-drift` に取り込み済み） | — |
| `worktree-agent-a99e816fe008d21a0` | 6 | **FlatLaf 一式**。`.claude/worktrees/agent-a99e816fe008d21a0` の worktree にある | 実装内で再レビュー済 |

再開手順: 各ブランチで **ゲート C（Fable）** → push → PR → CI → マージ。
`gh pr merge` は auto mode の分類器に止められるため、ユーザーに実行してもらう。

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

### 4.1 同梱 `AozoraEpub3.ini` は「GUI 初期値」ではなく「従来と同じ出力になる値」

同梱 ini を GUI の初期状態と同じ全キー版にしたところ、`JavaAozoraVsReferenceTest` の
narou.rb 由来 5 ケースが全滅した。原因は、このテストが作業ディレクトリをプロジェクトルートに
して実行するため**リポジトリの同梱 ini を読んでおり**、参照 EPUB は旧・同梱 ini（24 キー）で
変換した出力と一致する契約になっていたこと。

**「同梱 ini = GUI 初期値」と「同梱 ini = narou.rb 参照出力と byte 一致」は両立しない。**
ユーザー判断で後者を優先し、出力に影響する 8 キー（`TocVertical` / `PageBreak` / `FitImage` /
`ImageSizeType` / `SinglePageSizeW` / `SinglePageSizeH` / `JpegQuality` / `MaxCoverLine`）だけ
従来の実効値を入れている。理由は ini 内のコメントにも書いた。

`SettingDefaults` の拡張自体は残るので、**キーを持たない手書き ini を使う利用者のドリフトは解消済み**。

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

- [ ] 各ブランチのゲート C（Fable）→ push → PR → マージ（FlatLaf の worktree ブランチを含む）
- [ ] **README / docs のスクリーンショット撮り直し**（FlatLaf 適用後の新 UI）。
      `docs/assets/screenshot-app.png` と、必要なら `screenshot-preview.png`
- [ ] README / `docs/index.md` の「※プレビュー機能は未リリースです」注記を削除
      （`docs/en/index.md` には注記が入っていないので追加不要）
- [ ] バージョン更新 5 ファイル: `src/AozoraEpub3.java` / `NarouApiClient.java` /
      `build.gradle` / `docs/index.md` / `docs/en/index.md`
- [ ] `RELEASE_NOTES.md` の「未リリース」節をバージョン節へ。項目 21 / 24 / 26 の記載を足す
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
  効くようになる。**同梱 ini を使う限り出力は不変**
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

## 7. 中断時点の状態（2026-08-10 深夜、`/clear` 直前）

### すぐやること（優先順）

1. **`fix/ini-default-drift` の must-fix 1 を片付ける**。同梱 `AozoraEpub3.ini` の 8 キー
   （`TocVertical` / `PageBreak` / `FitImage` / `ImageSizeType` / `SinglePageSizeW` /
   `SinglePageSizeH` / `JpegQuality` / `MaxCoverLine`）は **GUI にも効く**
   （`AozoraEpub3Applet.java:4916` の `setPropsSelected` が `props.containsKey` 判定）。
   いまの「従来出力を維持する値」を入れたままだと、**配布物を新規展開した GUI が
   自動改ページ OFF・画像合わせ OFF・目次横書きで起動する**。
   **ユーザー判断（2026-08-10）**: 同梱 ini は GUI 初期値に戻し、
   `test/JavaAozoraVsReferenceTest.java` に**専用 ini を `-i` で明示的に渡す**。
   渡す ini は master の 24 キー版（参照 EPUB を作ったときの設定）を
   `test_data/` に置いたものにする。**このテスト変更はユーザー承認済み**
2. **`AozoraEpub3IniResolutionTest.prefersIniBesideJar` が赤**。ini 探索順を
   「カレント優先 → jar フォールバック」に変えたため。**Fable に設計判断を依頼したが
   結果は受け取れていない**（セッション終了のため）。再開時に再依頼するか、
   自分で決めてテストを書き直す
3. ゲート C（Fable）を 3 ブランチに対して実施 → push → PR → マージ
4. リリースノートに互換性影響を追記（`DakutenType` 0→1 / `TitlePage` -1→1 /
   `MaxCoverLine` 実質無制限→10 / `GothicUseBold` が効くようになる / ini 探索順の変更）

### ゲート B（2026-08-10）の結果

`fix/ini-default-drift` に対して must-fix 2 件・should-fix 2 件。上記 1・2 が must-fix。
残る should-fix は「リリースノート未反映」。nice-to-have として:

- パリティテストは表のキーしか走査しない。逆方向（CLI が読むキーが表にあるか）の検査があるとよい
- **既存バグ（master 由来）**: `AozoraEpub3Applet.java:2213` の
  `jRadioTitleHorizontal = new JRadioButton(...)` が `group.add(jRadioPageMarginUnit0)` を
  潰しており、横書きラジオがオーファン化 + 余白単位ラジオがグループ未登録。要記録

検証済みで問題なしとされた点: フィクスチャ 116 キーとソースの一致、`PageBreak` ブロックの
意味不変、`-i` 指定時の挙動不変、同梱 ini × コードの実効値の全キー照合
（差は `ImageFloatW/H` のみで `ImageFloatType=0` のため無害）。

### スクリーンショット（済）

`docs/assets/screenshot-app.png` を FlatLaf 適用後（ライト、886x533）に差し替え、
`screenshot-app-dark.png` を追加済み（ブランチ `docs/v150-release-plan`）。
`screenshot-preview.png` はブラウザ表示なので据え置き。

### 未 push ブランチの最新

| ブランチ | 先頭コミット | 備考 |
|---|---|---|
| `fix/ini-default-drift` | `adc8331` wip | **テスト 1 件が赤**。上記 1・2 が残件 |
| `fix/kakuyomu-toc-cache-collision` | `66b74a5` | 緑。ゲート C 待ち |
| `docs/preview-cli-and-en-options` | `7e98daf` | 緑。ゲート C 待ち |
| `docs/v150-release-plan` | 本書 + スクショ | — |
| `worktree-agent-a99e816fe008d21a0` | `c25ddad` | FlatLaf 一式。ライト既定 |

### ゲート C（Fable）の判断 — ini 探索順（2026-08-10、受領済み）

**結論: カレント優先 → jar フォールバック（現行実装を承認）。**

- jar 隣の ini は配布物に必ず受動的に存在するが、カレントに ini を置くのは**利用者の能動的な意思表示**。
  jar 優先ではその意思が常に無言で潰され、本修正が消そうとしている「無言で無視される事故」を
  別の形で再生産する
- 後方互換も cwd 優先が厳密に優位。従来 CLI は cwd のみだったので、cwd 優先なら既存の全ケースが
  不変で「cwd に無いときだけ」挙動が増える純増。jar 優先は既存運用を退行させる
- git / npm 等の「ローカル設定がインストール済み既定を上書きする」慣習とも一致。narou.rb は無影響

**テストの直し方（重要な構造指摘）**: 単なる期待値の書き換えではなく意図ごと書き直す。
その前に構造問題を直すこと — 現在の `resolveDefaultIniFile` は相対 `File` がプロセスの
カレントに解決されるため、**全テストがリポジトリルートの `AozoraEpub3.ini` の存在に暗黙依存**
している（`ignoresDirectoryWithSameName` は cwd 側の ini が先にヒットして jar 側の判定に
到達しておらず、**偶然 PASS している**）。
推奨: `workingDir` を引数に取るオーバーロードを足してハーメチックにし、次の 5 ケースにする。

1. 両方あれば cwd が勝つ
2. cwd に無ければ jar 隣
3. `jarPath` が空 / null
4. 同名ディレクトリは無視（cwd 側・jar 側の両方）
5. どちらにも無ければ cwd 相対の File を返す

**ログとドキュメント**: 現行の「設定ファイルを読み込みました: <絶対パス>」は維持。加えて
(a) 両方存在するとき「jar 隣の ini は使用しません: <path>」を info で 1 行、
(b) どちらも無く既定値で起動するときも info を 1 行（**元バグの本質は沈黙**）。
README / リリースノートに探索順 `-i` → cwd → jar 隣 を明記し、
「GUI は常に jar 隣を読み書きする」非対称も書く。

**あわせて指摘された残件**:

- ~~`src/AozoraEpub3.java:175` のインラインコメントが実装と逆~~ → 修正済（`e254e9a`）
- 任意ディレクトリの野良 `AozoraEpub3.ini` を拾う挙動は従来からだが、明文化される以上ドキュメントに注意書きを
- `src/AozoraEpub3.java:63-67` の jarPath 導出が `";"` 固定分割で **Unix の `:` に非対応**。
  `-jar` 起動では無害な既存問題だが、マルチ OS 方針上 docs に項目として残す価値あり
- `JavaAozoraVsReferenceTest` への `-i` 明示は**本 PR 内で先に入れるのが安全**
  （リポジトリ ini が +138 行変わっており比較結果が動きうるため）
