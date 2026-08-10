# FlatLaf 導入計画（GUI モダン化、v1.5.0-jdk21 予定）

対象: `src/AozoraEpub3Applet.java`（GUI）。CLI（`src/AozoraEpub3.java`）には一切影響させない
（`AozoraEpub3Applet.main()` 冒頭の `args.length > 0` 分岐で CLI へ委譲した後に L&F 処理があるため、
L&F 関連コードはこの分岐より後に置き続けること）。

## 1. 決定事項

### 1.1 依存
- `com.formdev:flatlaf:3.7.2`（Maven Central 最新安定版。extras / themes-pack は不要。
  `FlatMacLightLaf` 等も core に同梱されているが今回は使わない）
- ライセンス: Apache-2.0 → `THIRD-PARTY-NOTICES.txt` に追記する
- fat JAR への増分は約 800KB で軽微

### 1.2 テーマ
- 使用テーマ: **FlatLightLaf / FlatDarkLaf（標準テーマ）**。
  - FlatMac* 系は macOS で実機確認できないため今回は見送り（残件参照）
- 既定値: **ライト固定（light）**。`UiTheme=system` を選べば OS 追従も選択できる
  - 理由: README のスクリーンショットがライトのため、初回起動の見た目とドキュメントを一致させる（ユーザー判断 2026-08-10）
  - 選択肢は「システム追従 / ライト / ダーク」の 3 択のまま。EPUB プレビュー機能の 3 択と UX を揃える
- OS ダークモード検出（`UiTheme=system` 選択時のみ使用。best-effort、失敗時は light、各コマンド 1 秒タイムアウト）:
  - Windows: `reg query HKCU\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize /v AppsUseLightTheme` → `0x0` なら dark
  - macOS: `defaults read -g AppleInterfaceStyle` → 正常終了かつ `Dark` なら dark
  - Linux: `gsettings get org.gnome.desktop.interface color-scheme` → `dark` を含めば dark

### 1.3 テーマ切り替え手段
- ini キー: `UiTheme` = `system` | `light` | `dark`（**AozoraEpub3.ini 全体設定**。プロファイルではない。
  キー欠落・不正値は `light` 扱い）
- GUI: トップのプロファイル行の右端（horizontal glue の後）に「テーマ」ラベル + 3 択コンボを追加
  - 変更時は即時適用（`UIManager.setLookAndFeel` → `FlatLaf.updateUI()`）+
    `props.setProperty("UiTheme", ...)`（保存自体は既存の windowClosing の props 保存に乗る）
  - 切替アニメーション `FlatAnimatedLafChange` は flatlaf-extras 側のため未使用（core のみ依存）
- i18n: `messages_ja.properties` / `messages_en.properties` の**両方**に追加
  - `ui.label.theme`（テーマ / Theme）、`ui.theme.system`（システム追従 / System）、
    `ui.theme.light`（ライト / Light）、`ui.theme.dark`（ダーク / Dark）
- 実装置き場: 新規 `src/com/github/hmdev/gui/UiThemeManager.java`
  - `enum Mode { SYSTEM, LIGHT, DARK }`（ini 値との相互変換つき）
  - `static boolean isSystemDark()`（上記 best-effort 検出）
  - `static void setup(Mode, String fontFamily)`（起動時: setLookAndFeel + defaultFont 設定）
  - `static void switchTo(Mode, String fontFamily)`（実行時切替: アニメーション付き updateUI）
  - 日本語フォント候補選択ロジック（OS 別候補 → GraphicsEnvironment 照合）もこのクラスへ移動し、
    Applet 側の `getPreferredJapaneseFontName()` は委譲に変更（重複排除）

### 1.4 起動時の適用順序（`main()` の変更）
1. `args.length > 0` → CLI 委譲（現状維持、これより前に何も足さない）
2. `AozoraEpub3.ini` を軽量 Properties ロードして `UiTheme` を先読み
   （applet の props ロードはコンポーネント生成後のため、L&F 決定用に main() で別途読む）
3. `UiThemeManager.setup(mode, jpFont)` — 内部で
   `UIManager.setLookAndFeel(FlatLightLaf/FlatDarkLaf)` →
   `UIManager.put("defaultFont", new FontUIResource(jpFont, PLAIN, 既定サイズ))`
4. 既存の Windows システム L&F 分岐（:5502-5525）と `applyJapaneseFontDefaults()` 呼び出し（:5532）は削除

### 1.5 フォント処理の一本化
- `applyJapaneseFontDefaults()`（:139 の実体）: UIDefaults の `*font` キー全走査置換は
  FlatLaf の `defaultFont` 機構・スケーリングと衝突するため**廃止**。
  OS 別フォント候補選択ロジックは `UiThemeManager` に移して `defaultFont` 1 キーに集約
- :4199 前後のコメントブロック内のコピー（死にコード）は削除
- 英語 OS で日本語字形が中華フォントに化ける問題への対策なので、候補リスト
  （Windows: Yu Gothic UI / Meiryo…、mac: Hiragino…、Linux: Noto CJK…）は必ず維持する

### 1.6 レイアウト（固定ピクセル排除 — 最重要）
方針: **決め打ち高さを全廃し、実フォントメトリクス / コンポーネントの preferredSize から導出**。
FlatLaf はコントロールが従来より背が高いため、20/22/26/28/48px 決め打ちは文字潰れを起こす。

- `init()` 冒頭（:585-599）の共有 Dimension を導出値に置換:
  - プローブ: `JTextField` / `JComboBox` / `JButton` を一時生成し
    `rowH = max(それぞれの preferredSize.height) + 4` を算出
  - `panelSize = new Dimension(1920, rowH)`、`panelSize28 = new Dimension(1920, rowH + 2)`
  - `detailPanelSize`: TitledBorder + JRadioButton 1 行入りのプローブ JPanel を組んで
    `getPreferredSize().height` を実測（旧 48px 相当）
  - `panelVMaxSize = new Dimension(640, rowH - 4)` 等、比率でなく実測から
  - `text3/text4/text5`: 幅は現行の FontMetrics 計算を維持、高さは
    `text.getPreferredSize().height` に変更（旧 20 固定）
  - `combo3`: 幅は `text3.width + コンボ矢印幅の余裕(28 目安)`、高さは JComboBox の preferredSize
- 個別リテラル（`setPreferredSize` 62 / `setMaximumSize` 66 / `new Dimension` 36 件）を全数レビューし、
  高さを制約しているものは導出値 or `comp.getPreferredSize().height` に置換。
  幅のみの制約（300px 等）は原則維持（余裕を見て +10% 程度の拡大は可）
- フレーム既定サイズ（保存値が無いときのみ有効）: 最小 720x520 / 初期 900x680 に拡大し、
  mac 特別扱いを撤廃（メトリクス導出により OS 差はレイアウト側で吸収）
- `DividerLocation` 既定（:604、230/350）: `rowH` 比で導出（旧 230 ≒ rowH26 × 8.8）

### 1.7 ハードコード色の除去（5 か所）
| 行 | 現状 | 置換 |
|---|---|---|
| :1023/:1029 | `jComboDstPath.setForeground(Color.gray/black)` | `UIManager.getColor("ComboBox.disabledForeground")` / `UIManager.getColor("ComboBox.foreground")` |
| :2361/:4510 | `jLabelApiStatus.setForeground(Color.GRAY)` | `UIManager.getColor("Label.disabledForeground")` |
| :4507 | `new Color(0,128,0)`（緑） | `UIManager.getColor("Actions.Green")`、null なら現行値へフォールバック |
| :2552 | `LineBorder(Color.lightGray,1)` | `UIManager.getColor("Component.borderColor")`、null なら lightGray |
| :2629 | `LineBorder(Color.white,3)` | `UIManager.getColor("TextArea.background")` で同色枠（ダークで白枠が浮く問題の解消） |

テーマ実行時切替後も追従させるため、これらは可能なら「設定時に UIManager から取得」する
リスナー/メソッド経由とし、コンストラクション時 1 回きりの固定にしない
（最低限、switchTo 後に再適用されること）。

### 1.8 コミット分割（1 ブランチ内で意味単位）
1. FlatLaf 依存追加 + UiThemeManager + main() の L&F 差し替え + フォント一本化 + i18n/ini + THIRD-PARTY-NOTICES
2. レイアウトのメトリクス導出化（共有 Dimension + 個別リテラル + フレーム既定サイズ）
3. ハードコード色の UIManager 化
4. docs（本計画書）

## 2. スコープ外（残件）

- **EDT 化**: `jFrame` 生成が main スレッド直（:5533 付近）。初期化順序の変化が別不具合を
  誘発しうるため本作業には含めない。後続 PR で `SwingUtilities.invokeLater` 化を検討
- **FlatMac* テーマ**: macOS 実機確認ができ次第、mac のみ FlatMacLight/DarkLaf に
  切り替えるか検討
- **macOS / Linux での実機レイアウト確認**: 本作業の目視確認は Windows のみ。
  メトリクス導出化により原理的には追従するはずだが、リリース前に可能なら実機確認
- **README スクリーンショットの撮り直し**: 見た目が大きく変わるため v1.5.0 リリース時に更新
- **AozoraEpub3.ini.template への UiTheme 追記**（存在する場合）と README への設定説明

## 3. 検証手順

1. `./gradlew --no-daemon jar` / `./gradlew --no-daemon test` 全件 PASS
2. GUI 起動して全タブ（変換 / 画像1 / 画像2 / 詳細設定 / 目次 / スタイル / Web / プレビュー）を
   スクリーンショットで目視 — 文字潰れ・コンポーネント欠けゼロになるまで反復
3. テーマ 3 択の切替が即時反映され、`AozoraEpub3.ini` に `UiTheme` が保存されること
4. CLI 非干渉: `java -jar build/libs/AozoraEpub3.jar -h` と
   `-of -d <out> test_data/test_ruby.txt` の変換が従来どおり動くこと
