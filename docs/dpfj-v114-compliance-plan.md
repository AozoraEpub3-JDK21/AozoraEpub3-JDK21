# 電書連 EPUB 3 制作ガイド v1.1.4 準拠化 実装プラン

最終更新: 2026-05-27 (初版 + 同日 レビュー指摘 2 回反映 §8 改訂履歴参照)
対象ブランチ: `master` から派生する `feat/dpfj-v114-<stage>-<summary>` (PR ごとに分岐)
親計画: [`docs/modernization-plan.md`](modernization-plan.md) ステージ S/S+ の一部として実施

---

## 1. 目的

2025-10-24 に公開された電書連（旧電書協、デジタル出版者連盟）EPUB 3 制作ガイド v1.1.4 と AozoraEpub3 の生成物との差分を解消し、**電書連準拠 EPUB**を出力できるようにする。

ガイド v1.1.4 の主な変更点（更新履歴 PDF より）:

- 日本電子書籍出版社協会 (電書協) → デジタル出版者連盟 (電書連) への名称変更
- EPUB 3 の参照先を **EPUB 3.3** に統一
- OPF prefix に `dpfj: https://www.dpfj.or.jp/` を追加
- `ebpaj:guide-version` を 1.1.4 に更新、`dpfj:guide-version` を併記
- `file-as` / `display-seq` を**廃止**（タイトル・著者・出版社の整列カナ・表示順序）
- ナビゲーション文書と ncx 同梱時の優先解釈規定を簡素化
- 画像形式に WebP を追加（EPUB 3.3 で正式採用）— **本計画書ではスコープ外**（§2.4 参照）
- RS による対応を想定しない HTML 要素と CSS プロパティ項目を削除
- サンプルファイル名に日付サフィックス追加（`dpfj-sample_YYYY-MM-DD.epub` 等）

## 2. スコープ

ディープリサーチ（並列 3 エージェントで実施、2026-05-27）の結果、11 項目の差分が判明。**破壊度**で 3 段階に分類する。

### 2.1 ステージ分類

| ステージ | 破壊度 | 内容 | 対象 PR | byte-identical テスト影響 |
|---|---|---|---|---|
| **A** | 🟢 純粋追加 | OPF メタ追加、CSS 互換補強 | PR #1 | 軽（ゴールデン更新のみ） |
| **B** | 🟡 中（要再生成） | CSS バグ修正（無効値・deprecated 修正） | PR #2 | 中（5 件全件再ゴールデン） |
| **C** | 🔴 後方互換破壊 | NCX / `<guide>` / `file-as` 削除 | PR #3+ (オプトイン化) | 大（モード切替で旧互換維持） |

### 2.2 差分一覧（出典: ディープリサーチ）

| # | カテゴリ | 該当箇所 | ステージ | 影響 |
|---|---|---|---|---|
| 1 | CSS | `template/OPS/css/vertical.css:253` `text-combine: horizontal` のみで `text-combine-upright: all` 欠落 | B | 現代 RS で縦中横が崩れる |
| 2 | CSS | `template/OPS/css/vertical.css:252` セレクタ `.tcy span` が 2 階層 span 前提 | B | 電書連 CSS との相互非互換 |
| 3 | CSS | `template/OPS/css/vertical.css:297` `text-orientation: sideways-right` が deprecated | B | Chromium で無視 |
| 4 | CSS | `template/OPS/css/vertical.css:64` の `vertical-align: center` が無効値（`vertical_image.css` には該当指定なし、レビュー指摘で訂正） | B | 現在は無視されている、修正で挙動変化の可能性 |
| 5 | OPF | `template/OPS/package.vm:31-33` & `:46` で `rendition:layout` 二重出力 | A | epubcheck WARN/ERROR の可能性 |
| 6 | OPF | `package.vm:3` prefix に `ebpaj:` / `dpfj:` 未宣言 | A | 電書連自己宣言不可 |
| 7 | OPF | `ebpaj:guide-version` / `dpfj:guide-version` meta 未出力 | A | 同上 |
| 8 | OPF | `package.vm:9, 15` で v1.1.4 廃止の `file-as` を出力 | C | 読み上げソフトに影響可 |
| 9 | OPF | `template/OPS/toc.ncx.vm` 出力 + `package.vm:127, 131-133` で NCX 参照 | C | 旧 RS で目次喪失 |
| 10 | OPF | `package.vm:158-173` の `<guide>` 要素 (EPUB 3 で deprecated) | C | 旧 RS で表紙/目次ショートカット喪失 |
| 11 | OPF | `package.vm:12-16` で `dc:creator` に `role` meta なし | A | 推奨レベル |

### 2.3 epubcheck 影響

現状でも epubcheck はすべて通過済み（CI 検証済）。本ガイド準拠化は**仕様違反の修正**ではなく、**準拠スキーム整合**が主目的。

### 2.4 スコープ外項目（明示的に除外）

ガイド v1.1.4 の変更点のうち、以下は本計画書のスコープ**外**とする。準拠化を名乗る際は本節とセットで参照。

#### 2.4.1 WebP 画像形式対応

- ガイド変更点: P.11 で JPEG/PNG/GIF に WebP を追加（EPUB 3.3 で許可）
- 現行実装上の不整合:
  - `src/com/github/hmdev/writer/Epub3Writer.java:726` の表紙画像フォーマット検査が正規表現 `^(png|jpg|jpeg|gif)$` で **WebP を拒否**
  - `src/com/github/hmdev/image/ImageInfoReader.java:170-189` の拡張子補正ループが png/jpg/jpeg/gif のみで **WebP を探索しない**
  - 表紙以外の本文画像処理経路（`Epub3ImageWriter` 等）でも WebP 取扱の検証が必要
- 除外理由: WebP 対応は画像処理基盤（`ImageInfoReader` + `Epub3Writer` + 表紙トリミング GUI + 各 Web 変換サイトの画像取得）にまたがる**横断的変更**で、本計画書（OPF メタ・CSS・deprecated 整理）の射程を超える
- 後続対応: 別途 `docs/webp-support-plan.md` 等で独立計画化を推奨。本計画書ではガイド「自己宣言レベル」での v1.1.4 準拠を目指し、画像形式拡張は別作業とする

#### 2.4.2 XHTML 構造の電書連準拠化

- 該当変更点: `<html class="hltr|vrtl">` / `<body class="p-text">` / cover の `<body epub:type="cover">` / 注釈リンク `.noteref` / `.note` / 独立 colophon ページ
- 除外理由: 青空文庫プレーンテキスト由来の出力源には注釈リンクや独立奥付の概念がないため、自然な範囲を超える
- 後続対応: 計画書 §3.4「ステージ C+」に記録のみ

## 3. 実装計画

### 3.1 ステージ A: メタデータ準拠（PR #1）

ブランチ: `feat/dpfj-v114-a-metadata`

#### 3.1.1 対象変更

1. `template/OPS/package.vm:2-3` の prefix 属性に追加:
   ```xml
   prefix="rendition: http://www.idpf.org/vocab/rendition/#
           ebpaj: http://www.ebpaj.jp/
           dpfj: https://www.dpfj.or.jp/"
   ```

2. `template/OPS/package.vm:26` の `dcterms:modified` 行直後に追加:
   ```xml
   <meta property="ebpaj:guide-version">1.1.4</meta>
   <meta property="dpfj:guide-version">1.1.4</meta>
   ```
   ハードコード文字列で OK（外部設定化は本ステージのスコープ外）。

3. `package.vm:12-16` の `dc:creator` ブロックに追加:
   ```xml
   <meta refines="#creator" property="role" scheme="marc:relators">aut</meta>
   ```

4. `package.vm:31-33` の svgImage 分岐内 `rendition:layout` と `:46` の同要素を統合し、`bookInfo.ImageOnly` で 1 回だけ出力するロジックに修正。

#### 3.1.2 テスト戦略

- `./gradlew test` 全件 PASS 確認
- byte-identical 比較テスト（`.NET` ポート `JavaComparisonTests`）の OPF 比較で**差分が想定通り**であることを確認
- 5 件のゴールデン EPUB を再生成し、`.NET` 側ゴールデンも更新
- epubcheck CI で全件 PASS 確認

#### 3.1.3 影響度

純粋追加のため、既存挙動の後退なし。後方互換性 100%。

### 3.2 ステージ B: CSS バグ修正（PR #2）

ブランチ: `feat/dpfj-v114-b-css-fix`

#### 3.2.1 対象変更

1. `template/OPS/css/vertical.css:252-256` を以下に修正:
   ```css
   .tcy, .tcy span {
       -webkit-text-combine: horizontal;
       -webkit-text-combine-upright: all;
       text-combine-upright: all;
       -epub-text-combine: horizontal;
   }
   ```
   セレクタ拡張（`.tcy, .tcy span`）により既存の 2 階層 span 出力（`chuki_tag.txt:224-225`）と単独 `.tcy` の両方に対応。

2. `template/OPS/css/vertical.css:293-301` の `.swr` に `sideways` を併記:
   ```css
   .swr {
       text-orientation: sideways-right;   /* 旧 RS 互換 */
       text-orientation: sideways;          /* 標準 */
       -webkit-text-orientation: sideways;
       -epub-text-orientation: sideways;
   }
   ```

3. `template/OPS/css/vertical.css:64` の `vertical-align: center` を `vertical-align: middle` に修正（無効値修正）。`span.img.fpage` セレクタの float 単ページ画像表示用指定。
   - `vertical_image.css` には該当指定**なし**（初版で誤参照、レビュー §9 で訂正）
   - 影響: 単ページ float 表示時の縦中央揃え（`fpage` クラス利用箇所のみ）

#### 3.2.2 テスト戦略

- byte-identical 比較テスト 5 件**全件**で CSS 差分が出る
- 各テストケース（`n9623lp` / `n8005ls` / `n0063lr` / `kakuyomu_822139840468926025` / `aozora_1567_14913`）を再生成
- `.NET` ポート側ゴールデンも全件更新
- **目視確認必須**: `vertical-align: center → middle` 修正で画像配置が変わる可能性がある画像系テストケース（`span.img.fpage` を使う**単ページ float 画像**入り作品 1 件以上）を Apple Books / Thorium / Kindle Previewer 3 で開いて表示確認
- 縦中横テストケース（kakuyomu `dakutenType=2` 必須）で `.tcy` 表示確認

#### 3.2.3 影響度

- 中: CSS 構造は変わるが、HTML 出力は不変。byte-identical 比較ゴールデンの更新のみで対応可能。
- 古い RS (`text-combine: horizontal` 認識・`text-combine-upright` 非認識) を使うユーザーには改善方向（追記）。

### 3.3 ステージ C: deprecated 整理（PR #3 以降、オプトイン化）

ブランチ: `feat/dpfj-v114-c-deprecated-opt-in`

#### 3.3.1 設計方針

破壊的変更を**設定スイッチでオプトイン化**する。デフォルトは現行挙動を維持し、`v1.4.0` 等のメジャーバージョンでデフォルト切替を**別途検討**。

#### 3.3.2 フラグの流通経路（実装必須）

レビュー指摘 (2026-05-27) を反映。プロジェクトには `AozoraEpub3.ini.template` は**存在しない**。実在は `AozoraEpub3.ini`（直接 `Properties` で読込）。既存設定の boolean は `true/false` ではなく **`"1"` か空文字**で読まれる慣習（`src/AozoraEpub3.java:128-150`）。CLI / GUI / Velocity の 3 経路すべてに届くよう以下を実装する:

##### (a) 設定ファイル `AozoraEpub3.ini`

```ini
# 電書連 v1.1.4 完全準拠モード
# 1: NCX / <guide> / file-as を出力しない (v1.1.4 サンプル完全準拠)
# 空: 後方互換維持（既定）
# Note: 旧 RS (旧 Kindle 機・旧 kobo・旧 Sony Reader) では目次表示が劣化する可能性
DpfjCompliantMode=
```

既存の `CoverPage` / `MarkId` / `CommentPrint` 等の boolean キー命名・値慣習に合わせる。

##### (b) CLI 読込（`src/AozoraEpub3.java`）

`props.load(...)` 直後の boolean 抽出ブロック（L133-150 周辺）に追加:

```java
boolean dpfjCompliantMode = "1".equals(props.getProperty("DpfjCompliantMode"));
```

抽出した値は以下のように下流に渡す（具体的な引数追加箇所は実装時に決定）:

- `Epub3Writer` のフィールド or setter（OPF 出力 / NCX 出力分岐に使用）
- `BookInfo` のフィールド（呼出側 / GUI saved state 共有用、必要に応じて）

##### (c) GUI 読込・保存（`src/AozoraEpub3Applet.java`、default package）

- 本ファイルは default package（`com.github.hmdev` 配下ではない）。プロジェクト構造で唯一の例外的配置
- 既存の `WriterConfigurator.apply` 系の経路に合流させるか、独立 `applet.dpfjCompliantMode` フィールドで保持
- 詳細設定タブにチェックボックスを追加（既存の boolean チェックボックス（自動横書き等）の近傍）
- save/load で `props.setProperty("DpfjCompliantMode", checkbox.isSelected() ? "1" : "")`

##### (d) Velocity Context への注入（`src/com/github/hmdev/writer/Epub3Writer.java`）

`writeOpf` 系メソッドで `velocityContext.put("chapters", ...)` を呼んでいる箇所（既存 L946, L954 等の近傍）に同居させる:

```java
velocityContext.put("dpfjCompliantMode", this.dpfjCompliantMode);
```

これで `package.vm` の `#if (${dpfjCompliantMode})` 分岐が機能する。**この put が抜けると Velocity 式は false 評価され、結果として「常に旧挙動」になるため、ステージ C 完了条件で必ず確認**。

##### (e) NCX zip entry 抑止（`src/com/github/hmdev/writer/Epub3Writer.java:953-959`）

```java
if (!this.dpfjCompliantMode) {
    velocityContext.put("chapters", chapterInfos);
    zos.putArchiveEntry(new ZipArchiveEntry(OPS_PATH+TOC_FILE));
    bw = new BufferedWriter(new OutputStreamWriter(zos, "UTF-8"));
    mergeTemplate(templatePath+OPS_PATH+TOC_VM, bw);
    bw.flush();
    zos.closeArchiveEntry();
}
```

OPF の `<item href="toc.ncx">` 抑止 (`package.vm` 側、§3.3.3-2) と**セット**で動かす必要がある。片方だけだと「manifest に書いてあるのに zip に無い」または「zip にあるのに manifest にない」状態となり epubcheck エラーになる。

#### 3.3.3 対象変更（`dpfjCompliantMode=true` 時のみ適用）

1. **`package.vm:9, 15` の `file-as` 出力をフラグで抑止**
   ```velocity
   #if (!${dpfjCompliantMode} && ${titleAs})
       <meta refines="#title" property="file-as">${titleAs}</meta>
   #end
   ```

2. **`package.vm:127, 131-134` の NCX 関連出力をフラグで抑止**
   ```velocity
   #if (!${dpfjCompliantMode})
       <item href="toc.ncx" id="ncx" media-type="application/x-dtbncx+xml"/>
   #end
   ...
   #if (${dpfjCompliantMode})
     <spine page-progression-direction="...">
   #else
     <spine page-progression-direction="..." toc="ncx">
   #end
   ```

3. **`package.vm:158-173` の `<guide>` 要素全体をフラグで抑止**
   ```velocity
   #if (!${dpfjCompliantMode} && (${bookInfo.InsertCoverPage} || ${bookInfo.InsertTocPage}))
     <guide>
       ...
     </guide>
   #end
   ```

4. **`toc.ncx` zip entry の抑止**: §3.3.2 (e) を参照。`Epub3Writer.java:953-959` の zip entry 書込を `dpfjCompliantMode` で gating。

> **重要**: §3.3.3-2（OPF 側の NCX 参照抑止）と §3.3.2 (e)（zip entry 抑止）は**必ずセット**で実装し、片方だけ反映された不整合状態を完了条件 §6.3 のテストで検出する。

#### 3.3.4 テスト戦略

- `dpfjCompliantMode=false`（既定）で**全既存テスト PASS**を維持（byte-identical 含む）
- `dpfjCompliantMode=true` 用の新テストケース追加:
  - `EpubDpfjComplianceTest.java`（新規）: 全 5 件相当を `dpfjCompliantMode=true` で生成し、サンプル EPUB と OPF 構造比較
  - epubcheck 通過確認
- 旧 RS 互換性確認は本プロジェクトでは検証不能 → README に「旧 RS で目次劣化の可能性あり」を明記

#### 3.3.5 影響度

- 既定動作は不変、後方互換性 100%
- オプトイン時のみ電書連準拠 EPUB を生成

### 3.4 ステージ C+: 追加準拠化（将来検討）

以下は本計画書スコープ外だが、フル準拠を目指す場合の選択肢として記録:

- `<html class="hltr|vrtl">` 付与（`xhtml_header.vm:3`）
- `<body class="p-cover|p-text|p-colophon">` 付与（ページ種別）
- cover の `<body epub:type="cover">` 追加
- 注釈リンク（`.noteref` / `.note` / `.super`）対応 — 青空文庫テキストに概念なし、Web 小説の脚注機能向け
- 独立 colophon ページ生成 — 青空文庫テキストの「底本：」セクション解析が必要

これらは「electronic book 制作者が手書きする」前提のガイド設計であり、変換ツールの自然な範囲を超える。将来 PR で個別に検討。

## 4. リリース戦略

### 4.1 バージョニング

| バージョン | 内容 |
|---|---|
| v1.3.7-jdk21 | ステージ A のみ (PR #1) — 電書連 v1.1.4 自己宣言可 |
| v1.3.8-jdk21 | ステージ B (PR #2) — CSS バグ修正 |
| v1.4.0-jdk21 | ステージ C (PR #3) — `dpfjCompliantMode` フラグ追加（デフォルト false） |
| v2.0.0-jdk21 (将来) | `dpfjCompliantMode=true` デフォルト化を検討 |

### 4.2 リリースノート記載例

#### v1.3.7-jdk21

```markdown
## 電書連 EPUB 3 制作ガイド v1.1.4 準拠化（メタデータ）

2025-10-24 公開の電書連ガイド v1.1.4 に対応:
- OPF に `ebpaj:guide-version=1.1.4` / `dpfj:guide-version=1.1.4` meta を追加
- prefix に `ebpaj:` / `dpfj:` を宣言
- `dc:creator` に MARC relator role meta (`aut`) を追加
- 固定レイアウト時の `rendition:layout` 二重出力を修正

既存 EPUB の挙動・表示に変化はありません（追加のみ）。
```

## 5. リスクと緩和策

| リスク | 確率 | 影響 | 緩和策 |
|---|---|---|---|
| byte-identical テストのゴールデン更新漏れ | 中 | テスト失敗 | ステージ B で 5 件全件を一括再生成、`.NET` 側 `JavaComparisonTests` の `NormalizeEpubText` も更新 |
| `vertical-align: middle` 修正で画像配置が変化 | 低 | 既存 EPUB との visual diff | ステージ B で Apple Books / Thorium / Kindle Previewer 3 目視確認必須 |
| `.tcy` セレクタ拡張で表示崩れ | 低 | 縦中横の二重表示 | ステージ B で kakuyomu (`dakutenType=2`) ケースを再生成し目視確認 |
| ステージ C で旧 Kindle ユーザーから目次喪失クレーム | 中 | サポート負荷 | デフォルト OFF にして README で警告。`dpfjCompliantMode=true` のみで適用 |
| サブエージェント調査ベースで PDF 本文未読 | 低 | 細部の差分見落とし | 公式付録 EPUB 3 種（自己宣言 v1.1.4）を一次根拠とする。必要なら追加調査 |

## 6. 検証チェックリスト

### 6.1 ステージ A 完了条件

- [ ] `./gradlew test` 全件 PASS
- [ ] byte-identical テスト 5 件のゴールデン更新済み
- [ ] `.NET` ポート `JavaComparisonTests` 全件 PASS（ゴールデン更新済み）
- [ ] CI で epubcheck PASS
- [ ] 生成 EPUB の `OPS/package.opf` に以下が含まれることを目視確認（AozoraEpub3 の OPF 出力先は `Epub3Writer.java:119` `PACKAGE_FILE = "package.opf"` で固定。電書連サンプルの `item/standard.opf` とは命名が異なる点に注意）:
  - `prefix="... ebpaj: http://www.ebpaj.jp/ dpfj: https://www.dpfj.or.jp/"`
  - `<meta property="ebpaj:guide-version">1.1.4</meta>`
  - `<meta property="dpfj:guide-version">1.1.4</meta>`
  - `<meta refines="#creator" property="role" scheme="marc:relators">aut</meta>`
- [ ] `rendition:layout` が固定レイアウト型でも 1 回だけ出力されることを確認

### 6.2 ステージ B 完了条件

- [ ] 上記すべて
- [ ] kakuyomu `dakutenType=2` テストケースを Apple Books / Thorium で開き縦中横正常表示確認
- [ ] 画像入りなろう作品で画像配置に意図しない変化がないことを確認
- [ ] `vertical.css` / `vertical_image.css` の修正箇所の単体 CSS パーステスト追加（任意）

### 6.3 ステージ C 完了条件

- [ ] 上記すべて
- [ ] `dpfjCompliantMode=`（空、既定）で全既存テスト PASS（後方互換性確認、INI 値は空文字 = false）
- [ ] `dpfjCompliantMode=1` 用の `EpubDpfjComplianceTest` 全件 PASS
- [ ] 両モードで epubcheck PASS
- [ ] GUI 詳細タブにチェックボックスが追加されており、Windows 11 / macOS で表示崩れなし
- [ ] `README.md` / `docs/usage.md` / `docs/en/usage.md` (ペア更新) に `DpfjCompliantMode` 記載

#### 6.3.1 フラグ流通経路の検証（レビュー指摘反映）

- [ ] **CLI 経路**: `AozoraEpub3.ini` に `DpfjCompliantMode=1` を設定し `java -jar AozoraEpub3.jar -of -d out input.txt` で生成、OPF に NCX/`<guide>`/`file-as` が**含まれない**ことを確認
- [ ] **GUI 経路**: GUI でチェックボックス ON → 設定保存 → 再起動して状態維持 → 変換実行 → 同上の OPF 内容を確認
- [ ] **Velocity context 経路**: 簡易テストで `velocityContext.put("dpfjCompliantMode", true)` が呼ばれていることを確認（put 抜けで「常に旧挙動」になるバグを防ぐ）

#### 6.3.2 ZIP / OPF 整合性の検証（レビュー指摘反映）

`dpfjCompliantMode=1` で生成した EPUB に対し、新規テスト `EpubDpfjComplianceTest` で以下を**全件アサート**:

- [ ] **ZIP entry 検査**: 生成された `.epub` を `ZipFile` で開き、`OPS/toc.ncx` エントリが**存在しない**ことを確認
- [ ] **OPF manifest 検査**: `OPS/package.opf` を XML パースし、`<manifest>` 配下に `id="ncx"` または `href="toc.ncx"` を持つ `<item>` が**存在しない**ことを確認
- [ ] **OPF spine 検査**: `<spine>` 要素に `toc` 属性が**付与されていない**ことを確認
- [ ] **OPF guide 検査**: `<guide>` 要素が**存在しない**ことを確認
- [ ] **OPF metadata 検査**: `<meta property="file-as">` が**存在しない**ことを確認
- [ ] **逆方向検査**: `dpfjCompliantMode=`（既定）で生成した EPUB では上記すべてが**存在する**ことを確認（後方互換性の証跡）
- [ ] epubcheck で警告ゼロ（manifest と zip の不整合検出も含む）

## 7. 参考資料

### 7.1 電書連 公式

- `D:\git\電書連\dpfj_epub3guide_ver1.1.4\dpfj-guide-v114_2025-10-24.pdf` — ガイド本体（v1.1.4）
- `D:\git\電書連\dpfj_epub3guide_ver1.1.4\dpfj-guide-v114_revision-history_2025-10-24.pdf` — 更新履歴
- `D:\git\電書連\dpfj_epub3guide_ver1.1.4\付録\dpfj-sample_2025-09-11.epub` — 正解実装サンプル
- `D:\git\電書連\dpfj_epub3guide_ver1.1.4\付録\book-template_2025-09-11.epub` — リフロー型テンプレ
- `D:\git\電書連\dpfj_epub3guide_ver1.1.4\付録\fixedlayout-template_2025-07-01.epub` — 固定レイアウト型テンプレ
- `D:\git\電書連\dpfj_epub3guide_ver1.1.4\付録\CSS機能一覧_2015-01-01.pdf` — CSS 機能一覧

### 7.2 W3C 仕様

- [W3C EPUB 3.3 Recommendation](https://www.w3.org/TR/epub-33/)
- [CSS Writing Modes Level 3 Recommendation (2019-12-10)](https://www.w3.org/TR/css-writing-modes-3/)
- [Unicode Standard Annex #50 Vertical Text Layout](https://www.unicode.org/reports/tr50/)

### 7.3 関連プロジェクト内ドキュメント

- [`docs/modernization-plan.md`](modernization-plan.md) — モダン化計画書（親計画）
- [`docs/release-procedure.md`](release-procedure.md) — リリース手順書（ステージ完了時に参照）
- [`docs/epub33-ja.md`](epub33-ja.md) — EPUB 3.3 仕様の日本語サマリ
- `D:\git\aozoraepub3-dotnet\docs\java-port-back-guide.md` — .NET ポート ↔ Java 互換ガイド

### 7.4 ディープリサーチ実施記録

2026-05-27 にディープリサーチエージェント 3 並列で実施:
- OPF / メタデータ差分（10 差分検出）
- CSS スタイル差分（14 観点）
- XHTML 構造差分（9 観点）

詳細結果は本計画書の §2.2 に集約。エージェント生成物は session log に保存（恒久保存はしない）。

## 8. 改訂履歴

| 日付 | 改訂内容 | 担当 |
|---|---|---|
| 2026-05-27 | 初版作成（ディープリサーチ結果反映） | Claude (Opus 4.7) |
| 2026-05-27 | レビュー指摘 4 件反映: ①§2.4 WebP / XHTML 構造をスコープ外として明示 ②§2.2 #4 と §3.2.1 で `vertical_image.css:64` 誤参照を `vertical.css:64` に訂正 ③§3.3.2 でフラグの CLI/GUI/Velocity 流通経路と `AozoraEpub3.ini`（`.template` なし、`1`/空文字慣習）を具体化 ④§6.3.1 / §6.3.2 で ZIP entry と OPF manifest/spine/guide/file-as の不在検証を完了条件に追加 | Claude (Opus 4.7) |
| 2026-05-27 | レビュー指摘 2 件追加反映: ⑤`AozoraEpub3Applet.java` のパスを `src/com/github/hmdev/AozoraEpub3Applet.java` → `src/AozoraEpub3Applet.java`（default package、唯一の例外的配置）に訂正（§3.3.2 (c)、§3.3.3） ⑥生成 OPF のファイル名を `standard.opf` → `OPS/package.opf` に訂正（`Epub3Writer.java:119` `PACKAGE_FILE` 定数、`META-INF/container.xml` の rootfile path に基づく）。電書連サンプルの `item/standard.opf` との命名差異を §6.1 / §6.3.2 に注記 | Claude (Opus 4.7) |
