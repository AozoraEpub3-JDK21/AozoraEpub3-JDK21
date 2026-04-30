# AozoraEpub3 モダン化計画書

最終更新: 2026-04-30
対象バージョン: v1.3.4-jdk21
対象ブランチ: `docs/modernization-plan`

> 本書は AozoraEpub3 を Java ベースのまま中長期に保守可能な状態に持っていくための段階的計画である。
> Kotlin/Scala への横展開、外部サービス化、Spring などの重量フレームワーク導入は **対象外**。

---

## 1. 現状サマリ

### 1.1 数値で見る規模

| 項目 | 値 |
|------|-----|
| 総 Java 行数 | 22,278 行 |
| クラス数 | 約 50 |
| 1500 行超の巨大クラス | 5 本（合計 14,040 行 ≒ 全体の 63%） |
| Java toolchain | 21（25 互換ターゲット） |
| Gradle | 9.2.1 |
| テストフレームワーク | JUnit 4.13.2 |
| 主要依存 | Velocity 2.4.1 / JSoup 1.22.1 / Commons Compress 1.28 / Junrar 7.5.8 / Batik 1.19 / SLF4J 2.0.16 |

### 1.2 巨大クラス（責務集中の箇所）

| クラス | 行数 | 主責務 | モダン化の主な論点 |
|------|------|--------|--------|
| `AozoraEpub3Applet` | 5,251 | Swing GUI、設定 UI、ドラッグ&ドロップ、進捗 | `JApplet` 継承（[JEP 504](https://openjdk.org/jeps/504) により JDK 26 で削除 / JDK 25 が最後にサポート）、デフォルトパッケージ、UI とロジック密結合 |
| `AozoraEpub3Converter` | 3,422 | 青空文庫 → XHTML 変換中核 | 80 個超の `boolean` フラグ、コンストラクタなし、責務多重 |
| `WebAozoraConverter` | 2,752 | Web スクレイピング → 青空テキスト化 | static `HashMap` シングルトン、サイト別ロジック混在 |
| `Epub3Writer` | 1,528 | ZIP パッケージング、Velocity、画像処理、CSS 生成 | 4 種の責務が一本化 |
| `JConfirmDialog` | 1,087 | プロファイル確認ダイアログ | UI 状態とビジネスロジック密結合 |

### 1.3 沃地（既に現代的な部分）

- Java 21 toolchain、Gradle 9 系
- `java.net.http.HttpClient`（HTTP/2、Cookie 自動）採用済
- Velocity でテンプレート分離済（`template/` 配下）
- SLF4J 導入済（呼び出し側がログを使えていない箇所はあり）
- テスト階層化（`test/com/github/hmdev/{config,converter,epub,image,info,io,util,validator}`）
- **`.NET` ポート（`aozoraepub3-dotnet`）との byte-identical 比較テスト** がリファクタの安全網
  - 検証済みケース: n9623lp / n8005ls / n0063lr / kakuyomu_822139840468926025 / aozora_1567_14913

### 1.4 主要な技術的負債

| 種別 | 箇所数 | 影響 |
|------|------|------|
| `Vector` / `Hashtable` 等レガシーコレクション | 24 ファイル / 112 occ | 不要な同期オーバーヘッド、API 古さ |
| `e.printStackTrace()` / 空 catch | 15 ファイル / 158 occ | エラーハンドリング不在、原因不明の沈黙失敗 |
| `JApplet` 継承 | 1 箇所（`AozoraEpub3Applet.java:116`） | **deprecated for removal**。OpenJDK [JEP 504](https://openjdk.org/jeps/504) により **JDK 26 で `javax.swing.JApplet` 削除**（JDK 25 が最後にサポート）。JDK 26 でコンパイル不能になる |
| `new Date()` + `SimpleDateFormat` | 多数 | スレッド安全でない、`java.time` 未活用 |
| `java.io.File` 中心 | 24 ファイル | Windows パス問題の温床 |
| `Properties` 経由の型なし設定 | `WriterConfigurator` 全体 | 100 行超の try-catch 連発 |
| 静的グローバル状態 | `Velocity.init` / `WebAozoraConverter.converters` | テスト隔離困難、並列実行不可 |
| デフォルトパッケージのメインクラス | `AozoraEpub3` / `AozoraEpub3Applet` | パッケージ整理の障害 |
| JUnit 4 | 全テスト | パラメタライズ・並列実行が弱い |

---

## 2. 設計原則

1. **互換性最優先**: 出力 EPUB は byte-identical を維持する。`.NET` ポート比較テストが PASS し続けることを各段階のゲートにする。
2. **段階的にコミット**: 1 PR を小さく保つ。リネーム/フォーマット/ロジック変更を混ぜない。
3. **重量フレームワーク不採用**: Spring / Quarkus 等は導入しない。手動 DI で十分な規模。
4. **GUI は維持**: Swing を活かす。JavaFX 全面書き直しは ROI が低い。
5. **CLI を一級市民に**: narou.rb など他ツールが直接呼ぶ用途を尊重し、CLI を中心に磨く。
6. **Java バージョン**: 当面 Java 21 LTS をターゲット維持。将来的に Java 25 LTS（2025-09 リリース予定）への切替を視野。

---

## 3. 段階別ロードマップ

### ステージ 0A — JDK 26 互換確保（推定 2〜3 日、低リスク・最優先）

> **目的**: JDK 26 でコンパイル不能になる `JApplet` 継承を**先に外す**。他の機械置換は混ぜない。
> このステージだけを単独 PR として先行マージし、ステージ 0B 以降の土台を整える。

| # | 項目 | 影響範囲 | 検証方法 |
|---|------|---------|--------|
| 0A-1 | `AozoraEpub3Applet` の `extends JApplet` を外し、`JPanel`/`JComponent` ベースの構造へ変更（`JFrame` は `main` 内で組み立て） | `AozoraEpub3Applet.java` のみ（クラス名は維持） | 起動・GUI 操作の手動確認、smoke test |
| 0A-2 | `Main-Class`／`mainClass`／`launch4j` 設定は **据え置き**（`AozoraEpub3Applet`） | `build.gradle:104, 129, 160` 確認のみ | ビルド + 配布 ZIP 起動 |
| 0A-3 | CLI エントリ `AozoraEpub3` も据え置き（narou.rb 互換維持） | 変更なし | narou.rb 経由変換の手動確認 |
| 0A-4 | JDK 26 EA でコンパイルが通ることを確認（CI に matrix 追加） | `.github/workflows/` | CI matrix |

**ゲート条件**:
- [ ] JDK 21 ビルド・全テスト PASS
- [ ] JDK 26 EA でコンパイル PASS（`-Xlint:removal` 警告ゼロ）
- [ ] `.NET` ポート `JavaComparisonTests` 5/5 PASS
- [ ] 配布 ZIP 起動・GUI 主要操作の手動確認
- [ ] narou.rb から `java -cp AozoraEpub3.jar AozoraEpub3 ...` の動作確認

### ステージ 0B — レガシー API 機械置換（推定 1〜2 週、低リスク、ステージ 0A 後）

> **目的**: 挙動を変えない範囲で API を現代化。各項目を**個別 PR**にする。

| # | 項目 | 影響範囲 | 検証方法 |
|---|------|---------|--------|
| 0B-1 | `Vector` → `ArrayList`（同期不要箇所のみ） | 24 ファイル | 全テスト + 比較テスト |
| 0B-2 | `java.io.File` → `java.nio.file.Path`（段階的） | 24 ファイル、複数 PR に分割 | 全テスト |
| 0B-3 | `new Date()` + `SimpleDateFormat` → `java.time` | `WebAozoraConverter` 等 | 単体テスト |
| 0B-4 | `e.printStackTrace()` / 空 catch を SLF4J ログに統一 | 15 ファイル | 全テスト |

**ゲート条件**: 各 PR で全テスト + 比較テスト 5/5 PASS。

### ステージ 0C — テスト基盤・ソース配置（推定 2〜3 週、中リスク）

> **目的**: 後続ステージのリファクタを支えるテスト基盤と標準ディレクトリ配置に揃える。
> 機械置換とは性質が異なるため**ステージ 0B と分離**。

| # | 項目 | 影響範囲 | 検証方法 |
|---|------|---------|--------|
| 0C-1 | JUnit 4 → JUnit 5（Jupiter）+ AssertJ | 全テストファイル | 全テスト |
| 0C-2 | ソース配置を Maven 標準（`src/main/java`、`src/test/java`）へ | `build.gradle`、全ファイル移動 | 全テスト + ビルド + IDE 設定 |
| 0C-3 | デフォルトパッケージのメインクラスを `com.github.hmdev.cli` / `com.github.hmdev.gui` へ移動。**旧 FQCN（デフォルトパッケージ）にラッパークラスを残置**（narou.rb 互換、最低 1 マイナーバージョン） | `AozoraEpub3` / `AozoraEpub3Applet` | smoke test、起動確認、narou.rb 経由動作確認、CI ワークフロー |

**ゲート条件**:
- [ ] `gradlew test` PASS
- [ ] `.NET` ポート `JavaComparisonTests` 5/5 PASS
- [ ] CI（ビルド → epubcheck → サンプル EPUB 生成）GREEN
- [ ] narou.rb 互換ケース（n9623lp 等）の手動確認

**注意点**:
- 0C-1 は JUnit 4 と 5 を**並行稼働**できる（`junit-vintage-engine`）。一気に書き換えず、新規テストから JUnit 5 を使い、既存は順次移行
- 0C-2 のソース配置変更は他のステージと**混ぜない**（git diff が大量のリネームになるため、レビュー困難）。`git mv` で履歴を保つ
- 0C-3 は narou.rb が `java -cp AozoraEpub3.jar AozoraEpub3` で直接呼ぶ点に注意。**Main-Class マニフェスト・mainClass 設定の更新を忘れずに**、旧 FQCN ラッパー（数行）を最低 1 マイナーバージョン残置

### ステージ 1 — アーキテクチャ整理（推定 3〜4 週、中リスク）

> **目的**: 巨大クラスを意味のある単位に分割し、設定を型安全にする。

#### 1-A. 設定の型安全化

- `record ConversionConfig(...)` を導入し、`Properties` 受け渡しを置換
- `WriterConfigurator.apply()` の try-catch 山積みを宣言的バインディングへ
- INI パース層に**バリデーション**を入れる（範囲チェック、必須キー検出）
- `AozoraEpub3Converter` の 80+ boolean を意味グループに集約：
  - `record TcyConfig(boolean autoYoko, boolean autoYokoNum1, ...)`
  - `record IndentConfig(boolean forceIndent, boolean removeHeadSpace, ...)`
  - `record PageBreakConfig(boolean force, int byteSize, int emptyLines, ...)`
  - `record ChapterDetectionConfig(boolean autoChapterName, ...)`

#### 1-B. 巨大クラスの責務分割

| 分割前 | 分割後（案） |
|------|------------|
| `Epub3Writer` (1,528 行) | `EpubPackager`（ZIP）／`SectionRenderer`（XHTML 出力）／`ManifestBuilder`（OPF/NAV）／`TemplateRenderer`（Velocity ラッパ）／`ResourceLayout`（パス定数集約） |
| `WebAozoraConverter` (2,752 行) | `SiteExtractor`（extract.txt 駆動）／`PageFetcher`（HttpClient ラッパ）／`AozoraTextWriter`（青空書式出力）／`SiteRegistry`（Singleton 解消） |
| `AozoraEpub3Converter` (3,422 行) | `LineTokenizer`／`RubyResolver`／`ChukiHandler`（注記）／`IndentResolver`／`ChapterDetector`／`OutputEmitter` |

**進め方**: いきなり全分割せず、**まず内部メソッドをパッケージプライベートクラスに切り出し**、外部 API は維持。Phase ごとに比較テストでゲート。

#### 1-C. 静的グローバル状態の解消

- `Velocity.init()` の static 呼び出しを `VelocityEngine` インスタンス注入へ（既に `AGENTS.md` で原則明記済 → 実装が追従していない）
- `WebAozoraConverter.converters` static `HashMap` を `SiteRegistry` インスタンスへ
- HttpClient 共有を Singleton から DI へ

**ゲート条件**:
- [ ] 比較テスト 5/5 PASS
- [ ] 並列テスト実行可能（`gradlew test --parallel`）

### ステージ 2 — Java 21 言語機能の活用（ステージ 1 と並行可、低〜中リスク）

| 機能 | 適用候補 |
|------|--------|
| `record` | **テンプレート未参照かつ setter 不要のクラスのみ**（候補は §2.1 で要選別） |
| `sealed interface` | `PageBreakType` / `RubyCharType` のバリアント網羅 |
| pattern matching for switch | 注記タイプ判定、章タイトル種別判定 |
| text blocks | テンプレート断片、ログメッセージ |
| `var` | 局所変数推論（イテレータ・ビルダ系） |
| Stream API | `Vector` / `for` ループ反復の置換 |
| `try-with-resources` | リソース解放の徹底（既存箇所のリーク確認） |
| `Optional` | 戦略的に少量だけ（過剰使用禁物） |

#### 2.1 record 化の互換性リスク（重要）

**Velocity の getter 解決規約と record アクセサが噛み合わない**。

実コードの該当例（テンプレート側）:
```
template/OPS/package.vm:118       #if ($image.IsCover)
template/OPS/toc.ncx.vm:25         #if ($chapter.ChapterName)
template/OPS/toc.ncx.vm:32         #if ($chapter.ChapterId)
template/OPS/xhtml/xhtml_nav.vm    $chapter.ChapterName / $chapter.ChapterId
```

Velocity の `UberspectImpl` は `$obj.Foo` を以下の順で解決する:

1. `getFoo()` / `getfoo()` / `isFoo()`（JavaBean 規約）
2. `Foo()` / `foo()` —**実際にはこの形でも解決されうる**が、引数なし・public が条件で、Velocity のバージョンや uberspector 設定に依存

しかし record の component アクセサは **JavaBeans の `isXxx()` を自動生成しない**。component 名が `cover` なら `cover()`、`isCover` なら `isCover()` というように、**component 名そのままのメソッド名**になる。したがって既存の `$image.IsCover`（`isCover()` または `getIsCover()` を期待）に一致する保証はなく、record 化するとテンプレート解決が静かに壊れる可能性がある。さらに `BookInfo.java` には Bean 風 getter/setter が **41 件** あり、外部から setter で書き換える前提のフィールドも多数（record の不変性とも噛み合わない）。

**したがって以下の方針とする**:

| クラス | record 化 | 理由 |
|------|---------|------|
| `BookInfo` | ✗ | テンプレート参照あり、setter 多数、可変性必須 |
| `ChapterInfo` / `ChapterLineInfo` | ✗ | テンプレート参照あり（`$chapter.ChapterName/Id`） |
| `ImageInfo` | ✗ | テンプレート参照あり（`$image.IsCover`） |
| `SectionInfo` | △ | テンプレート参照状況を要監査。参照されていれば Bean 維持 |
| `GaijiInfo` | △ | 同上 |
| `CoverEditInfo` / `ProfileInfo` | ○候補 | 内部 DTO のみで使用されていれば record 化可 |
| ステージ 1-A で新設する `*Config` 系 record | ○ | テンプレートに渡さないため安全 |

**実装手順**:
1. **監査フェーズ**: `template/` 全体を grep し、`$obj.Property` 形式で参照されているクラスを列挙
2. テンプレート参照ありのクラスは **Bean 互換維持**（record にしない、または record + Bean 互換アダプタを別途用意）
3. record 化対象は新規 DTO（特にステージ 1-A の `*Config`）と、テンプレート未参照かつ setter 不要のクラスに限定
4. Velocity の解決を変える代替案として `VelocityContext` に詰める前にアダプタで Bean 風オブジェクトに変換する方式もあるが、**メリットに対してコストが大きい**ため当面採用しない

### ステージ 3 — GUI モダン化（**要相談、ステージ 1 と独立**）

#### 比較

| 案 | コスト | メリット | デメリット | 推奨度 |
|----|------|--------|---------|------|
| **A. Swing 維持＋FlatLaf** | 小 | 既存 UI 構造そのまま、見た目改善、`JApplet` 撤去のみ | UI 設計の古さは残る | ★★★ |
| **B. JavaFX 移行** | 大 | 現代的 UI、CSS スタイリング、FXML | 5,251 行の書き直し、JavaFX が JDK 同梱でない | ★ |
| **C. CLI 強化＋Web UI 別実装** | 中 | スクレイピング機能の API 化、ヘッドレス運用容易 | デスクトップ層が薄くなる、Web UI 保守負担 | ★★（補完案） |
| **D. Compose for Desktop（Kotlin）** | 大 | Kotlin で記述、宣言的 UI | Kotlin 導入は本計画の範囲外 | ✗ |

**推奨**: **A 案（Swing 維持 + FlatLaf）** + 必要に応じ **C 案（CLI 強化）**。AozoraEpub3 は narou.rb 等から CLI 呼び出しされるバッチ変換ツールが本質。GUI 全書き換えは ROI が低い。

#### A 案の具体作業

1. FlatLaf 依存追加、起動時 LaF 設定
2. `JConfirmDialog` 等の冗長レイアウトを `MigLayout` または `GroupLayout` に整理
3. ドラッグ&ドロップを Java 11+ 標準 API へ寄せる
4. `EventQueue.invokeLater` の網羅、長時間処理の `SwingWorker` 化（既存箇所の見直し）

### ステージ 4 — 配布・運用（ステージ 0 完了後にいつでも）

| 項目 | 内容 | 優先度 |
|------|------|------|
| `jpackage` | Windows .exe、macOS .dmg、Linux .deb のネイティブインストーラ生成 | 中 |
| `jlink` | 軽量カスタム JRE 同梱 → ユーザー Java インストール不要 | 中 |
| Launch4j 撤去 | jpackage で代替可能 | jpackage 導入後 |
| Dependabot / Renovate | 依存最新化自動化 | 高 |
| SBOM 生成（CycloneDX） | サプライチェーン透明性 | 高 |
| Sigstore / コミット署名 | 既に SSH タグ署名は導入済（v1.3.3-jdk21）。コミット署名は未導入 | 中 |
| GraalVM Native Image | 起動高速化。Velocity の動的特性で困難な可能性あり、PoC 必要 | 低 |
| JPMS（モジュール化） | fat JAR 配布なら必須ではない | 低 |

### ステージ 5 — テスト基盤強化（ステージ 0 と並行可）

- ApprovalTests または `EpubOutputComparisonTest` 系の体系化（ゴールデン EPUB 管理）
- パラメタライズドテスト（JUnit 5 後）でサイト別 extract.txt を網羅
- ネットワーク E2E（`AozoraRealTest` / `HamelnE2ETest`）の安定化（`Assume` 制御維持）
- mutation testing（PIT）— 中核ロジックに限定
- カバレッジ計測（JaCoCo）— CI レポート

---

## 4. 実装順序の推奨

```
Day 1-3:    ステージ 0A（JDK 26 互換確保）
               └─ JApplet 継承を外す単独 PR（クラス名・mainClass は据え置き）
Week 1-2:   ステージ 0B（レガシー API 機械置換、各項目を個別 PR）
               ├─ 0B-4 ログ統一
               ├─ 0B-1 Vector → ArrayList
               ├─ 0B-3 Date/SimpleDateFormat → java.time
               └─ 0B-2 File → Path（複数 PR に分割）
Week 3-5:   ステージ 0C（テスト基盤・ソース配置）
               ├─ 0C-1 JUnit 4 → 5（vintage 並行稼働、新規から JUnit 5）
               ├─ 0C-2 Maven 標準配置（単独 PR、ロジック変更混入禁止）
               └─ 0C-3 メインクラス移動 + 旧 FQCN ラッパー残置（単独 PR）
Week 6-9:   ステージ 1（アーキテクチャ整理）
               ├─ 1-A 設定の record 化（*Config 新設、テンプレート非参照のみ）
               ├─ 1-C 静的状態解消
               └─ 1-B 巨大クラス分割
並行:       ステージ 2（言語機能、§2.1 の互換性監査を先に完了させる）
Week 5+:    ステージ 4（配布、ステージ 0A 完了後にいつでも）
未定:       ステージ 3 GUI（要方針合意）
```

---

## 5. リスクと緩和策

| リスク | 影響 | 緩和策 |
|------|------|------|
| 出力 EPUB の byte 差分発生 | narou.rb 連携・既存ユーザー破壊 | 各 PR で `.NET` ポート `JavaComparisonTests` 5/5 確認をゲート化 |
| 0C-2（Maven 配置）の git diff 肥大化 | レビュー困難 | 単独 PR、ロジック変更を一切混ぜない、`git mv` でリネーム追跡 |
| `JApplet` 撤去で Web 起動を期待するユーザー | 影響想定: ほぼゼロ（既に GUI は JFrame で動作） | リリースノートに明記 |
| record 化で Velocity テンプレート呼び出し失敗 | 出力崩壊 | §2.1 の方針に従い、**テンプレート参照クラスは Bean 互換維持**（record 化対象外）。record 化前に `template/` 配下の `$obj.Property` 参照を**全件監査**し、参照ありクラスを除外。PR 単位で全テンプレートのレンダリングテストを必須ゲート化 |
| narou.rb の `java -cp` 直接呼びがメインクラス移動で壊れる | 連携破壊 | 旧クラス名でラッパー（数行）を最低 1 マイナーバージョン残す。`AozoraEpub3` クラスは現状でも CLI エントリだが、移動時に旧 FQCN を残置 |
| 並行作業で衝突 | マージコスト | ステージ単位で feature ブランチを切る、ステージ 0A を最初に完了させる |

---

## 6. 非対象（やらないこと）

- Kotlin / Scala / Groovy への移行
- Spring / Quarkus / Micronaut 等の DI コンテナ導入
- 出力 EPUB 仕様の変更（EPUB 3.3 から動かさない）
- 青空文庫テキスト形式の入力仕様変更
- データベース導入（現状は INI / プロパティで十分）
- マイクロサービス化、SaaS 化

---

## 7. 進捗管理

- 本計画書はリビジョンを記録: `docs/modernization-plan.md`
- 各ステージは GitHub Issue / Project でトラック
- ステージ完了時にこの計画書を更新（完了日・実コスト記録）
- メモリ: `memory/MEMORY.md` に進捗ハイライトのみ記録

---

## 8. 次のアクション（合意事項待ち）

1. ステージ 0A（`JApplet` 継承外し・クラス名据え置き）から着手で良いか
2. ステージ 3（GUI 方針）— A 案（Swing 維持＋FlatLaf）で合意してよいか
3. ステージ 0C-2（Maven 配置）— `git mv` でリネーム追跡する方針で良いか
4. ステージ 0C-3（メインクラス移動）— 旧 FQCN ラッパー残置期間（1 マイナー / 1 メジャー）
5. ステージ 2 の record 化 — テンプレート参照の監査結果次第で対象を決める方針で良いか

承認が得られた項目から個別 PR 化していく。
