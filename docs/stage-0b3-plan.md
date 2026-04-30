# ステージ 0B-3 実装プラン — `new Date()` + `SimpleDateFormat` → `java.time`

最終更新: 2026-04-30
対象ブランチ: `master` から派生する `chore/0b-3-java-time` (仮)
親計画: [`docs/modernization-plan.md`](modernization-plan.md) §3 ステージ 0B-3

---

## 1. 目的

レガシー日付 API (`java.util.Date` / `java.text.SimpleDateFormat`) を `java.time` (Instant / DateTimeFormatter) に置換する。副次効果として `Epub3Writer` の `static final SimpleDateFormat` が抱える thread-unsafe を解消する。

## 2. スコープ

### 2.1 対象 occurrence

src 4 ファイル + test 1 ファイル、計 ~13 箇所。

| ファイル | 行 | 内容 | 区分 |
|---|---|---|---|
| `src/com/github/hmdev/writer/Epub3Writer.java` | 20, 242, 559 | `static final SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")` を `bookInfo.modified` (Date) のフォーマットに使用 → EPUB OPF の `<dcterms:modified>` | static SimpleDateFormat (thread-unsafe) |
| `src/com/github/hmdev/web/WebAozoraConverter.java` | 19, 21, 50, 1099 | `final SimpleDateFormat("yyyy/MM/dd HH:mm:ss")` で「変換日時」テキスト出力 | instance member |
| `src/AozoraEpub3Applet.java` | 44, 45, 4575-4579 | `addProfile()` 内のローカル `SimpleDateFormat("yyyyMMdd-HHmmss")` でプロファイル ini ファイル名タイムスタンプ生成 | local |
| `src/com/github/hmdev/info/BookInfo.java` | 5, 118, 185, 393-399 | `public Date modified` + Bean compat getter/setter + `this.modified = new Date()` | **API 公開済 → スコープ外** |
| `test/com/github/hmdev/epub/PackageTemplateSmokeTest.java` | 7, 47 | テスト内 `new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date())` | test |

### 2.2 スコープ外（明示的に残す）

| 項目 | 理由 | 対応先 |
|---|---|---|
| `System.currentTimeMillis()` (AozoraEpub3.java:531/544、AozoraEpub3Applet.java:4089/4138、WebAozoraConverter.java:365/859) | 経過時間計測 / キャッシュ expiry 用途。計画書の「`new Date()` + `SimpleDateFormat` → `java.time`」スコープ外。`Instant`/`Duration` 化は別議題 | 触らない |
| `BookInfo.public Date modified` フィールド・getter/setter | 公開 API かつ CLAUDE.md / 計画書 §2.1 で BookInfo は record 化禁止＝ **Bean 互換維持クラス**。型変更は外部呼び出しを破壊する可能性 | ステージ 1-A の record / Bean 互換アダプタ整理と合わせる |
| `BookInfo` コンストラクタ内の `this.modified = new Date()` | 上記フィールド型維持の前提下では `Date` インスタンスを作るしかない | 同上 |

## 3. 実装方針

### 3.1 Epub3Writer

```java
// before
final static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
...
velocityContext.put("modified", dateFormat.format(bookInfo.modified));

// after
private static final DateTimeFormatter MODIFIED_FORMATTER =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneId.systemDefault());
...
velocityContext.put("modified", MODIFIED_FORMATTER.format(bookInfo.modified.toInstant()));
```

### 3.2 WebAozoraConverter

```java
// before
final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
...
bw.append(dateFormat.format(new Date()));

// after
private final DateTimeFormatter dateFormat =
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());
...
bw.append(dateFormat.format(Instant.now()));
```

**instance final を維持** (Codex [P2] 対応): static 化すると `withZone(ZoneId.systemDefault())` のタイムゾーン捕捉タイミングが「インスタンス生成時」→「クラスロード時」に変わる。通常運用では問題化しないが、`TimeZone.setDefault()` を絡めるテストや長寿命プロセスで互換性が崩れる。元コード (`final SimpleDateFormat`) はインスタンス生成時に TZ 捕捉なので、**完全互換のため instance final のまま DateTimeFormatter 化**する。

### 3.3 AozoraEpub3Applet.addProfile

```java
// before
SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
File profile = new File(profilePath.getPath()+"/"+dateFormat.format(new Date())+".ini");
int i = 1;
while (profile.exists()) {
    profile = new File(profilePath.getPath()+"/"+dateFormat.format(new Date())+i+".ini");
    i++;
}

// after
DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    .withZone(ZoneId.systemDefault());
File profile = new File(profilePath.getPath()+"/"+dateFormat.format(Instant.now())+".ini");
int i = 1;
while (profile.exists()) {
    profile = new File(profilePath.getPath()+"/"+dateFormat.format(Instant.now())+i+".ini");
    i++;
}
```

**ループ内で `Instant.now()` を毎回再評価** (Codex [P2] 対応): 元コード `dateFormat.format(new Date())` はループ内でも時刻を再評価するため、衝突発生時に秒が進めば候補は `新しい秒 + i` になる。これを 1 回評価に簡素化すると候補が常に `最初の秒 + i` になり**挙動が変わる**。挙動互換のため元コード通りループ内で都度評価する。

### 3.4 PackageTemplateSmokeTest

```java
// before
String modified = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date());

// after
String modified = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
    .withZone(ZoneId.systemDefault())
    .format(Instant.now());
```

## 4. 互換性保持戦略

### 4.1 byte-identical 出力の維持

- 全 `DateTimeFormatter` に `withZone(ZoneId.systemDefault())` を付与し、`SimpleDateFormat` のデフォルト挙動 (`TimeZone.getDefault()`) と一致させる
- フォーマット文字列は不変
- `bookInfo.modified` (Date) は `Date#toInstant()` で `Instant` に変換 → 同一時点を表す
- `new Date()` は `Instant.now()` に置換 — 同じシステムクロックの「現在時刻」を取得し、本 PR の対象フォーマットはすべて秒以上の精度（`yyyy-MM-dd'T'HH:mm:ss'Z'` / `yyyy/MM/dd HH:mm:ss` / `yyyyMMdd-HHmmss`）で出力するため、`Date` と `Instant` の内部分解能差は出力に影響しない

### 4.2 ロケール由来 Calendar の意図的 ISO/Gregorian 化 (Codex 3 巡目 [P2] / Option B 採用)

`SimpleDateFormat(String)` はデフォルト FORMAT locale の `Calendar` を使うため、非グレゴリオロケールでは年表記が変化する:

| ロケール | SimpleDateFormat 出力 (`yyyy-MM-dd...`) | 本 PR の DateTimeFormatter (ISO) |
|---|---|---|
| `ja_JP`, `en_US` (Gregorian 系) | `2026-04-30...` | `2026-04-30...` ✅ 互換 |
| `ja_JP_JP` (和暦 era) | `8-04-30...` (令和 8 年) | `2026-04-30...` ❌ 意図的非互換 |
| `th_TH` (仏暦) | `2569-04-30...` | `2026-04-30...` ❌ 意図的非互換 |

**判断 (Option B)**: 完全互換よりも EPUB 仕様準拠を優先し、**ISO/Gregorian 年で正規化**する方針を採用。

#### 採用理由

1. **完全互換の実装コストが見合わない**: `withChronology(Chronology.ofLocale(...))` recipe では補えない:
   - `Chronology.ofLocale()` は legacy locale (`th_TH` 国コード推測) を仏暦にマップしない (BCP 47 `u-ca-buddhist` 拡張のみ参照)。SDF の `Calendar.getInstance(locale)` は `th_TH` で BuddhistCalendar を返すが、java.time 側は ISO のまま → recipe では補えない
   - `ja_JP_JP` (variant "JP") では JapaneseChronology を捕捉できるが、`yyyy` パターンの semantic 差 (SDF: unpadded "8" / DTF: zero-padded "0008") で完全互換にならない
   - 実装上の選択肢として `DateTimeFormatterBuilder.appendValue(YEAR_OF_ERA)` + 明示的 chronology マッピングがあるが、コード複雑化 (各 occurrence で ~15 行) に対する実運用上の対価が小さい
2. **EPUB 3.3 仕様準拠寄り**: `<dcterms:modified>` は ISO 8601 (Gregorian) を要求するため、和暦/仏暦年を OPF に書く方が本来 invalid
3. **想定ユーザーで影響者ゼロ**: AozoraEpub3 ユーザーが `ja_JP_JP` / `th_TH` を default locale にする想定的にゼロ。`ja_JP` / `en_US` (Gregorian) では完全 byte-identical を維持

#### 影響範囲

| 出力経路 | 影響内容 |
|---|---|
| `Epub3Writer.MODIFIED_FORMATTER` → OPF `<dcterms:modified>` | `ja_JP_JP` / `th_TH` ロケール下で年表記が ISO 化 (元: 和暦/仏暦) |
| `WebAozoraConverter.dateFormat` → 「変換日時」ヘッダ行 | 同上 |
| `AozoraEpub3Applet.addProfile` → プロファイル ini ファイル名 | 同上 (ファイル名 prefix の年表記が変化) |

#### 意図の明示: `withLocale(Locale.ROOT)`

production code の formatter には `withLocale(Locale.ROOT)` を明示する。`DateTimeFormatter` のデフォルト locale は `Locale.getDefault(Locale.Category.FORMAT)` だが、`Locale.ROOT` を明示することで「ロケール非依存に ISO/Gregorian 年で出力する意図」がコード読者に伝わる:

```java
static final DateTimeFormatter MODIFIED_FORMATTER =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withLocale(Locale.ROOT)        // ← 意図表示: ロケール非依存
        .withZone(ZoneId.systemDefault());
```

`Locale.ROOT` 明示の有無で実出力は変わらない (`withChronology` を付けない限り `DateTimeFormatter` は IsoChronology を使うため)。これは挙動変更ではなく**ドキュメントとしてのコード**である。

#### 検証方針

`th_TH` ロケール下で「DTF (Locale.ROOT 明示版 / デフォルト版どちらも) が ISO 年を出力 / SDF が仏暦年を出力」する**差異を明示的に確認するテスト** (`normalizesNonGregorianLocaleToIsoYear`) を `Epub3WriterDateFormatTest` / `WebAozoraConverterDateFormatTest` に配置。これにより本 PR の意図的挙動 (ISO 化) がコードに固定される。

#### release notes 反映

`memory/release_notes_pending.md` に「次リリース時にロケール由来年表記の ISO 化を behavior change として明記」する追加エントリを記録。

### 4.3 タイムゾーン捕捉タイミング (Codex [P2] 対応)

`withZone(ZoneId.systemDefault())` の評価タイミングを**元コードと一致させる**:

| 場所 | 元コード | 本 PR 後 | TZ 捕捉タイミング |
|---|---|---|---|
| `Epub3Writer.dateFormat` | `static final SimpleDateFormat` | `static final DateTimeFormatter` | クラスロード時 (元と同じ) |
| `WebAozoraConverter.dateFormat` | `final SimpleDateFormat` (instance) | `final DateTimeFormatter` (instance) | インスタンス生成時 (元と同じ) |
| `AozoraEpub3Applet.addProfile` | method-local `SimpleDateFormat` | method-local `DateTimeFormatter` | メソッド呼び出し時 (元と同じ) |

これにより `TimeZone.setDefault()` が絡む（テストや長寿命プロセス上の）シナリオでも互換性を保つ。

### 4.4 既存の TZ 仕様違反について

`<dcterms:modified>` の `'Z'` literal suffix + システム TZ ローカル時刻の組み合わせは、技術的には W3C EPUB 3.3 の dcterms:modified UTC 要件 に違反している既存 bug。本 PR では byte-identical 維持優先で**現状挙動を保存**し、UTC 化は **ステージ S (EPUB 3.3 準拠強化)** に follow-up として記録する。

## 5. 検証手順

### 5.1 新規ユニットテスト追加 (Codex [P2] 対応 — 必須)

`.NET` ポート比較テストの `NormalizeEpubText` は `dcterms:modified` を正規化するため、**今回の変更でいちばん危ない日時フォーマット差分は検出できない**。固定 `Instant` を formatter に通して期待文字列を直接アサートするテストを追加する。

#### 5.1.1 テスト配置方針 (Codex [P2] 反映)

`com.github.hmdev.util` 配下に集中配置する初版案は撤回。`Epub3Writer` (`com.github.hmdev.writer`)、`WebAozoraConverter` (`com.github.hmdev.web`)、`AozoraEpub3Applet` (デフォルトパッケージ) でパッケージが異なり、

- 名前付きパッケージ間で package-private は越えられない
- デフォルトパッケージのクラスは名前付きパッケージから `import` 不可

ため、**テストは対象クラスのパッケージにミラーして分散配置**する。これは既存テスト構造 (`test/com/github/hmdev/{writer,web,epub,...}`) とも整合する。

#### 5.1.2 新規テストファイル

| 対象 | テストファイル | アサート対象 |
|---|---|---|
| `Epub3Writer.MODIFIED_FORMATTER` | `test/com/github/hmdev/writer/Epub3WriterDateFormatTest.java` | package-private static 定数を直接 format 呼び出し、固定 `Instant` で期待文字列と一致確認 |
| `WebAozoraConverter.dateFormat` | `test/com/github/hmdev/web/WebAozoraConverterDateFormatTest.java` | package-private instance 定数を直接 format 呼び出し、同様にアサート |
| `AozoraEpub3Applet.addProfile` の formatter | （テスト追加見送り — §5.1.4 参照） | 手動確認 + コードレビュー |

#### 5.1.3 アサート例 (Asia/Tokyo 固定時)

| formatter | 入力 | 期待出力 |
|---|---|---|
| `Epub3Writer.MODIFIED_FORMATTER` | `Instant.parse("2026-04-30T12:00:00Z")` | `"2026-04-30T21:00:00Z"` (literal `Z` + システム TZ ローカル時刻 = JST 21:00) |
| `WebAozoraConverter.dateFormat` (新コンバータインスタンスの) | 同上 | `"2026/04/30 21:00:00"` |

テスト本体では `@Before` で `TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))`、`@After` で元の TZ に戻す。これにより「システム TZ + literal `'Z'` という現状挙動」を**未正規化のまま**ロックダウンする。

`WebAozoraConverter.dateFormat` は instance final のため、`@Before` で TZ 切替→新規 `WebAozoraConverter` インスタンス生成→`dateFormat` を読み出す順序に注意（インスタンス生成時に TZ 捕捉される設計のため）。

#### 5.1.4 `AozoraEpub3Applet.addProfile` の扱い

`addProfile()` は private、formatter は method-local。公開するためには helper static method を切り出す等の production 改変が必要。一方このタイムスタンプは**プロファイル ini ファイル名生成のみ**に使われ、出力 EPUB / 変換結果には混ざらない。リスクは低いと判断し:

- 本 PR ではテスト追加を**見送る**
- format pattern 文字列 `"yyyyMMdd-HHmmss"` の等価性はコードレビューで確認
- §5.5 の手動確認 (GUI でプロファイル新規保存 → ファイル名形式確認) で担保

production に test 用の抽象化を持ち込まず最小変更を維持する判断。Codex 指摘の「フォーマッタ生成を named package の共通 utility に切り出す」案は将来 (例えばステージ 1-A の設定 record 化と合わせて) 余地として残す。

#### 5.1.5 package-private エクスポート方針

- `Epub3Writer` の formatter は元コード `final static SimpleDateFormat dateFormat` (default access) → 本 PR では `static final DateTimeFormatter MODIFIED_FORMATTER` (package-private) で踏襲。これにより同一パッケージのテストから直接アクセス可能
- `WebAozoraConverter.dateFormat` も同様に default access (`final DateTimeFormatter dateFormat`) で踏襲
- 統合テスト案として、固定 `BookInfo` を渡して OPF テンプレートをレンダリングし `<dcterms:modified>` 値を直接検証するテストを `Epub3WriterDateFormatTest` 内に追加するのも可（package 同居なので問題なし）

### 5.2 既存テスト

```bash
./gradlew clean test
```
全テスト PASS を確認。

### 5.3 `.NET` ポート比較テスト (補助検証)

`D:\git\aozoraepub3-dotnet` で `JavaComparisonTests` 5/5 PASS を確認。**ただし** タイムスタンプは `NormalizeEpubText` で正規化されるため、これは「TZ 捕捉タイミングや format 文字列の崩れを検出する手段ではなく、**それ以外の出力 byte 差分が出ていないことを確認する手段**」と位置づける（Codex [P2] 指摘どおり）。本来の format 互換性は §5.1 の未正規化アサーションで担保する。

### 5.4 サブエージェント再レビュー

実装後、フレッシュコンテキストの general-purpose エージェントを起動して差分レビューを依頼。指摘ゼロまで反復。

### 5.5 手動確認 (任意)

GUI 起動 → プロファイル新規保存でファイル名 `yyyyMMdd-HHmmss.ini` 形式が維持されていることを確認。

## 6. リスクと緩和

| リスク | 緩和策 |
|---|---|
| `withZone()` 漏れによるタイムゾーンずれ | 全 4 occurrence で `withZone(ZoneId.systemDefault())` を明示。レビュー時にチェックリスト化 |
| TZ 捕捉タイミングの変化 (Codex [P2]) | §4.2 の表に従い、static / instance / method-local の区分を元コードと完全一致させる。§5.1 のユニットテストで `TimeZone.setDefault()` を切り替えてロックダウン |
| 日時フォーマット差分が `.NET` 比較の正規化で隠れる (Codex [P2]) | §5.1 の固定 `Instant` を入力にした未正規化アサーションを必須化 |
| `BookInfo.modified` の Date 型に手を入れて API 破壊 | 本 PR スコープ外として明示。release_notes_pending.md には**記載しない**（ステージ 1-A 着手時に対応） |
| `.NET` ポート比較で byte 差分発生 | NormalizeEpubText が dcterms:modified 以外を吸収していないことを期待。万一 PASS しない場合は実装を rollback して原因分析 |
| Trial & Error 2 回上限超過 | 同一箇所で 2 回連続失敗したら 3 回目は試さずユーザー報告。テストエラーが出たら自分でテストを書き換えない |

## 7. PR 構成

- 単一 PR で完結（規模が小さく機械的）
- ブランチ名候補: `chore/0b-3-java-time`
- コミット粒度: 1 コミット推奨（ファイル数 5、変更行数 ~30 行程度の見込み）
- PR タイトル例: `refactor: SimpleDateFormat/Date → java.time（ステージ 0B-3）`

## 8. 完了後の作業

1. `master` を pull
2. `memory/MEMORY.md` のモダン化計画ステータスを 0B-3 完了に更新
3. `memory/modernization_plan.md` のテーブル該当行を ✅ 完了に更新
4. **ステージ S 向け follow-up** として「`<dcterms:modified>` の UTC 化 (現状はシステム TZ + 'Z' literal で仕様違反)」を `docs/modernization-plan.md` のステージ S セクション（または新規 follow-ups doc）に追記
5. 次ステージ (0B-2 File→Path もしくは 0B-4c 空 catch 監査) の方針確認に進む

## 9. ユーザー確認事項

着手前に以下 4 点の合意を得る:

1. **BookInfo.modified の Date 型維持**をスコープ外とする (ステージ 1-A 行き)
2. **既存 dcterms:modified の TZ 仕様違反**は本 PR では触らず、ステージ S follow-up として記録する
3. **TZ 捕捉タイミングを完全互換にする** (Codex [P2] 反映): `Epub3Writer` は static、`WebAozoraConverter` は instance final、`AozoraEpub3Applet.addProfile` は method-local — それぞれ元コードに合わせる
4. **テスト配置をパッケージ分散方式にする** (Codex [P2] 反映): `Epub3Writer` / `WebAozoraConverter` 用テストは対象パッケージにミラー配置、`AozoraEpub3Applet` は手動確認のみ。共通 utility への formatter 切り出し案は採用しない

## 10. 改訂履歴

- 2026-04-30 初版
- 2026-04-30 Codex レビュー (1 巡目) 反映:
  - [P2] §5.1 として固定 `Instant` 未正規化アサーションを必須化
  - [P2] §3.2 / §4.2 で `WebAozoraConverter.dateFormat` を instance final 維持に変更（static 化撤回）
  - [P2] §3.3 で `addProfile` のループ内 `Instant.now()` 再評価を維持（簡素化撤回）
  - [P3] §4.1 の精度説明を「秒精度出力に影響しない」表現に修正
- 2026-04-30 Codex レビュー (2 巡目) 反映:
  - [P2] §5.1 のテスト配置を `com.github.hmdev.util` 集中→対象パッケージ分散へ変更。`AozoraEpub3Applet` (デフォルトパッケージ) はテスト追加見送り、§9 確認事項 4 として明示
- 2026-04-30 Codex レビュー (3 巡目) 反映 + Option A 試行 → Option B 採用に転換:
  - [P2] 当初 Option A (`withChronology(Chronology.ofLocale(...))` recipe) を試行したが、Trial 1 (ja_JP_JP) で yyyy パディング差、Trial 2 (th_TH) で `Chronology.ofLocale()` が BuddhistChronology を返さず ISO 年のままであることが判明
  - Trial & Error 2 回上限に従いユーザー判断を仰ぎ、**Option B (意図的 ISO/Gregorian 正規化) に転換**
  - 全 4 occurrence から `withChronology(...)` を削除し、関連 import (`Chronology`, `Locale`) も整理
  - §4.2 を「ロケール由来 Calendar の意図的 ISO/Gregorian 化」として書き直し、採用理由・影響範囲・検証方針・release notes 反映を明記
  - テストは `formatterRecipeMatchesLegacyFor*Locale` を撤回し、代わりに `normalizesNonGregorianLocaleToIsoYear` (DTF が ISO 年 / SDF が仏暦年を出力する差異を明示確認) を追加
- 2026-04-30 Codex レビュー (4 巡目) 反映:
  - [P2] Codex 提案の「`Locale.ROOT` 等で意図を明示」を採用。全 4 occurrence の formatter に `.withLocale(Locale.ROOT)` を追加し、ロケール非依存挙動が意図的であることをコード上で明示
  - `Locale.ROOT` 明示の有無で実出力は変わらず (DateTimeFormatter デフォルト Chronology は IsoChronology のため)、挙動変更なし・ドキュメントとしてのコードという位置づけ
  - テスト `normalizesNonGregorianLocaleToIsoYear` を更新し、`Locale.ROOT` 明示版とデフォルト版の DTF 両方を検証 (どちらも ISO 年を出力することを確認)
  - 計画書 §4.2 に「意図の明示: withLocale(Locale.ROOT)」サブセクションを追加
