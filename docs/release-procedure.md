# AozoraEpub3 リリース手順書

最終更新: 2026-04-30
対象: AozoraEpub3-JDK21（Java 21、Gradle 9.2.1）

> 本書は **ローカルビルド + `gh release create`** 方式でのリリース手順を定める。
> CI（`release.yml`）はテスト＆ビルド検証のみを担当し、配布アーティファクトは生成しない。
> 経緯: Ubuntu CI 上でビルドすると `.ini` / `.txt` の行末が CRLF→LF に変換される可能性があり、ZIP/TAR 内の同一性が保証できない。`.gitattributes` も整備していないため、ローカルビルドを正とする運用に切り替えた（コミット `e7ceb5d`）。

---

## 1. リリース種別の判断

[SemVer](https://semver.org/lang/ja/) に準じる。サフィックス `-jdk21` は当面維持（Java 21 LTS ターゲット明示のため）。

| 種別 | 例 | 該当ケース |
|------|----|-----------|
| **patch** | `1.3.4 → 1.3.5` | バグ修正、内部リファクタ、出力 EPUB 互換維持の変更 |
| **minor** | `1.3.x → 1.4.0` | 新機能、CLI/設定追加、新サイト対応 |
| **major** | `1.x → 2.0.0` | 破壊的変更（出力 EPUB 構造の互換性なし、CLI 引数破壊） |

**判定の鉄則**: `.NET` ポート `JavaComparisonTests` 5/5 が PASS する変更は最大でも minor。FAIL する場合は major またはリリース対象外（互換維持を優先）。

---

## 2. リリース前チェックリスト

### 2.1 コード状態

- [ ] 対象 PR が master にマージ済み（または master が最新）
- [ ] `gradlew test` がローカルで PASS
- [ ] `.NET` ポート `JavaComparisonTests` 5/5 PASS（`D:\git\aozoraepub3-dotnet\tests\AozoraEpub3.Tests\` で `dotnet test --filter "FullyQualifiedName~JavaComparisonTests"`）
- [ ] CI（master 最終コミット）が GREEN
- [ ] `git status -s` がクリーン（前回 dist の残骸が残っていないか確認）

### 2.1.1 リリース前 E2E（**必須ゲート**）

ユニットテスト・`generateLocalSamples`・`.NET` 比較テストは**いずれもネットワークを使わない**ため、
「URL からダウンロード → キャッシュ生成 → 章結合 → EPUB 出力」の実経路はここでしか検証されない。
所要 10 分程度（初回のダウンロードを除く）。**リリース後ではなくリリース前に実施する**（以前は §5 のリリース後タスクだった）。

> **実施順の注意**: 本ゲートは jar と `.exe` を使うため、**先に §3.1〜§3.3（clean → test → dist）を実行して
> 配布物を用意してから**行う。つまり実際の流れは **§2.1 → §3.1〜3.3 → §2.1.1 → §3.4 以降**。
> ここで問題が見つかった場合は修正して §3.1 のクリーンからやり直すこと。

**毎回必須（最小セット）**

- [ ] **なろう短編 1 件**: URL → 変換 → EPUB 生成を確認
- [ ] **同じ URL で再実行** → 「キャッシュファイルを利用します」が出て再ダウンロードが走らないこと
- [ ] **青空文庫 URL 1 件**（`cards/NNNNNN/files/NNNN_NNNNN.html` の単話ページ）
      ※ zip URL（`NNNN_ruby_NNNN.zip`）の直接指定は **v1.4.0 で CLI も対応済み**
      （`docs/code-audit-followups.md` の「16.」）。zip を出力先にダウンロードしてから
      ローカル zip 入力と同じ経路で変換する。余力があれば zip URL も 1 件試す
- [ ] **旧バージョンのキャッシュ互換**: 前リリースで生成したキャッシュディレクトリに対して新バージョンを実行し、
      **キャッシュが再利用される（パスが変わって全再取得にならない）**こと
      ※ `CharUtils.escapeUrlToFile` / `replaceInvalidFileChars` を触ったリリースでは**最重要**
- [ ] **終了コードの両方向**: 成功時 `0`（narou.rb が成功判定に使う）/ 失敗時 `1`（入力ファイル不在などで確認。オフライン可）
- [ ] **`.exe` ランチャー経由で 1 変換**（配布物の実行経路。jar 直叩きでは検証されない）
- [ ] 生成した EPUB を `epubcheck` に通す
- [ ] **生成した EPUB を実際にリーダー（または展開して XHTML）で開き、目視確認する**
      - 末尾の**出典 URL のリンクが正常か**（`href` に `［＃` が混入していないか）
      - 目次が期待どおりか / 本文冒頭が文字化けしていないか / 挿絵が表示されるか

> **目視確認を必須にしている理由**: `epubcheck` は XML/EPUB 仕様の妥当性しか見ないため、
> **仕様上は valid だが内容が壊れている**不具合を検出できない。
> 実際、監査 #15（出典 URL の `href` に縦中横注記が混入してリンクが機能しない）は
> epubcheck を通過しており、生成物を目で見て初めて発見された。

**経路を触ったリリースのみ**

- [ ] `web/<site>/extract.txt` を変更した場合は該当サイトを追加で 1 件
- [ ] narou.rb 連携部分を変更した場合は narou.rb 経由でも 1 件（§5 にも補完として残す）

**やらなくてよいもの**: 12 サイト全網羅、GUI 経由の変換（§3.4 の起動確認で足りる）。

> **注**: 実サイトへのアクセスは 1 作品 1 回に留める。キャッシュヒットの確認は
> 2 回目の実行がキャッシュを使うことで成立するため、追加のダウンロードは発生しない。

### 2.2 環境

- [ ] JDK 21 がアクティブ: `java -version` で `21.x` 表示
- [ ] Windows ビルド環境（Launch4j で `.exe` を生成するため）
- [ ] `AozoraEpub3.ico` がプロジェクトルートに存在
- [ ] `gh auth status` でログイン済み
- [ ] SSH 署名鍵 `~/.ssh/id_ed25519_aozora_signing` が利用可能（タグ自動署名）

### 2.3 バージョン更新箇所

以下の **5 ファイル** すべてを新バージョンに更新する。漏れると VERSION 表示と JAR メタデータが食い違う：

| ファイル | 更新箇所 |
|---------|---------|
| `src/AozoraEpub3.java` | `public static final String VERSION = "1.x.x-jdk21";` |
| `src/com/github/hmdev/web/api/NarouApiClient.java` | User-Agent ヘッダ内のバージョン文字列 |
| `build.gradle` | `version = '1.x.x-jdk21'` |
| `docs/index.md` | バージョン・日付・ダウンロードリンク |
| `docs/en/index.md` | 英語版（日本語と必ずセットで更新） |

---

## 3. ビルド手順（Windows ローカル）

### 3.1 事前クリーンアップ（**必須**）

dist 失敗の主因は前回のビルド残骸混入。**毎回**クリーンから始める：

```bash
./gradlew --no-daemon clean
# Windows: gradlew.bat --no-daemon clean

# 念のため build/distributions/ と build/launch4j/ も手動で空であることを確認
ls build/distributions/ 2>/dev/null
ls build/launch4j/ 2>/dev/null
```

### 3.2 テスト実行

```bash
./gradlew --no-daemon clean test
```

**ゲート**: 失敗テストがあればここで停止。リリースに進まない。

### 3.3 配布パッケージ生成

```bash
./gradlew --no-daemon dist
```

`dist` タスクは `[zipDistribution, tarDistribution]` に依存し、内部で以下が走る：

```
build → jar → createExe → zipDistribution
                        ↘
                          dist
                        ↗
build → createLauncher → tarDistribution
```

**生成物**:
- `build/libs/AozoraEpub3.jar`（fat JAR、約 11 MB）
- `build/distributions/AozoraEpub3-1.x.x-jdk21.zip`（Windows 向け、`.exe` ランチャー同梱）
- `build/distributions/AozoraEpub3-1.x.x-jdk21.tar.gz`（Unix 向け、`.sh` ランチャー同梱）

### 3.4 成果物検証

```bash
# サイズと一覧確認
ls -lh build/libs/ build/distributions/

# ZIP 内容確認
unzip -l build/distributions/AozoraEpub3-*.zip | head -30

# TAR 内容確認
tar -tzf build/distributions/AozoraEpub3-*.tar.gz | head -30

# 必須ファイルが含まれているか目視チェック（§6.1 の頻発トラブル対策）
unzip -l build/distributions/AozoraEpub3-*.zip | grep -E 'chuki_|template/|gaiji/|web/|presets/|setting_narourb|AozoraEpub3\.(ini|ico|exe|jar)'
tar -tzf build/distributions/AozoraEpub3-*.tar.gz | grep -E 'chuki_|template/|gaiji/|web/|presets/|setting_narourb|AozoraEpub3\.(ini|ico|sh|jar)'

# 起動確認（fat JAR）
java -jar build/libs/AozoraEpub3.jar -h

# 起動確認（GUI）
java -jar build/libs/AozoraEpub3.jar &  # 5 秒以内に Window が出ること
```

**必須**: 上記 grep の出力に `chuki_*.txt`、`template/`、`gaiji/`、`web/`、`presets/`、`setting_narourb.ini`、`AozoraEpub3.ini` が**すべて**現れること。1 つでも欠けていれば §6.1 を参照して `build.gradle` の 3 箇所を確認する。

### 3.5 SHA256SUMS 生成

```bash
cd build/distributions
sha256sum AozoraEpub3-*.zip AozoraEpub3-*.tar.gz > SHA256SUMS
cat SHA256SUMS
cd ../..
```

### 3.6 サンプル EPUB 検証（推奨）

```bash
./gradlew --no-daemon generateLocalSamples
./gradlew --no-daemon epubcheck -PepubDir=build/epub_local
```

`.NET` ポート比較テストの 5 ケース確認も忘れない（§2.1 参照）。

---

## 4. タグ作成と GitHub Release

### 4.1 コミット & タグ

```bash
# バージョン更新コミット
git add src/AozoraEpub3.java src/com/github/hmdev/web/api/NarouApiClient.java \
        build.gradle docs/index.md docs/en/index.md
git commit -m "release: v1.x.x-jdk21 — <概要>"

# タグ作成（tag.gpgsign=true なので SSH 署名が自動で付く）
git tag v1.x.x-jdk21 -m "v1.x.x-jdk21 — <概要>"

# push（CI が走り、テスト＆ビルド検証だけ実行される）
git push origin master
git push origin v1.x.x-jdk21
```

### 4.2 GitHub Release 作成

```bash
gh release create v1.x.x-jdk21 \
  build/distributions/AozoraEpub3-1.x.x-jdk21.zip \
  build/distributions/AozoraEpub3-1.x.x-jdk21.tar.gz \
  build/distributions/SHA256SUMS \
  --title "v1.x.x-jdk21 — <タイトル>" \
  --notes-file release-notes.md
```

`release-notes.md` のテンプレート例:

```markdown
## 変更点

- <ハイライト 1>
- <ハイライト 2>

## 検証

- `gradlew test` PASS
- `.NET` ポート `JavaComparisonTests` 5/5 PASS
- epubcheck サンプル PASS

## SHA256

\`\`\`
<SHA256SUMS の中身>
\`\`\`

## アップグレード手順

ZIP を展開して上書き、または新規フォルダに展開して旧版から `AozoraEpub3.ini` をコピー。
```

### 4.3 attestation（任意・推奨）

`release.yml` での自動 attestation は廃止済み。手動付与が必要なら：

```bash
gh attestation generate build/distributions/AozoraEpub3-*.zip
gh attestation generate build/distributions/AozoraEpub3-*.tar.gz
```

---

## 5. リリース後タスク

- [ ] `gh release view v1.x.x-jdk21` でアセットが揃っていることを確認
- [ ] README のダウンロードリンク更新（必要なら別 PR）
- [ ] `docs/index.md` / `docs/en/index.md` の差分が GitHub Pages にデプロイされたことを確認
- [ ] narou.rb 連携で動作確認（少なくとも 1 ケース変換）
      ※ **主たる E2E 検証は §2.1.1 でリリース前に済ませる**。ここは配布物での最終確認という位置づけ
- [ ] `memory/MEMORY.md` のバージョン記載を更新（手動）
- [ ] 必要なら社内告知 / Issue クローズ

---

## 6. dist 失敗トラブルシューティング

`gradlew dist` が失敗する代表パターンと対処。**まず `--info --stacktrace` で再実行**して原因切り分けする：

```bash
./gradlew --no-daemon clean dist --info --stacktrace
```

### 6.1 配布物に必須ファイル（`chuki_*.txt` / `template/` / `gaiji/` / `web/` / `presets/`）が含まれない

**症状**: 生成された ZIP/TAR を解凍すると、本来必要な `chuki_*.txt` や `template/`、`gaiji/`、`setting_narourb.ini` 等のファイル/フォルダが**抜けている**。実行時に「テンプレートが見つからない」「外字が出ない」「チューキが解釈できない」等のエラーになる。**過去に最頻発した失敗モード**。

**原因**: `build.gradle` 内で配布物の include リストが **3 箇所に重複定義**されている：

| 箇所 | 行 | 使われる経路 |
|---|---|---|
| `task zipDistribution { ... }` | 203-239 | `./gradlew dist` の ZIP |
| `task tarDistribution { ... }` | 242-282 | `./gradlew dist` の TAR |
| `distributions { main { contents { ... } } }` | 493-527 | `./gradlew installDist` の展開ディレクトリ |

新しいリソース（`chuki_新規.txt`、`web/<新サイト>/`、新しいプリセット等）を追加した際、3 箇所のうち 1〜2 箇所しか更新せずにコミットすると、特定経路で生成したアーカイブから漏れる。**AI エージェント（Sonnet 等）の自動修正で特に頻発する**ため要警戒。

#### dist 生成経路マップ（どれを使うかで参照される include 定義が違う）

| コマンド | 生成物 | 参照される include 定義 |
|---|---|---|
| `./gradlew dist` ✅ | ZIP + TAR | `zipDistribution` + `tarDistribution` |
| `./gradlew zipDistribution` | ZIP のみ | `zipDistribution` のみ |
| `./gradlew tarDistribution` | TAR のみ | `tarDistribution` のみ |
| `./gradlew installDist` | 展開ディレクトリ | `distributions.main.contents` のみ |
| `./gradlew distZip` ❌ | **無効化**（`build.gradle:109`） | — |
| `./gradlew distTar` ❌ | **無効化**（`build.gradle:110`） | — |

**リリース時は必ず `./gradlew dist` を使う**。`zipDistribution`/`tarDistribution` の個別実行や `installDist` は確認用途に限定する。

#### 検証（リリース前に必ず実施）

```bash
cd build/distributions
# ZIP の必須ファイル目視チェック
unzip -l AozoraEpub3-*.zip | grep -E 'chuki_|template/|gaiji/|web/|presets/|setting_narourb|AozoraEpub3\.(ini|ico|exe|jar)'

# TAR の必須ファイル目視チェック
tar -tzf AozoraEpub3-*.tar.gz | grep -E 'chuki_|template/|gaiji/|web/|presets/|setting_narourb|AozoraEpub3\.(ini|ico|sh|jar)'
```

期待される最低限の出力例:
- `chuki_tag.txt`, `chuki_alt.txt`, `chuki_utf.txt`, `chuki_ivs.txt`, `chuki_latin.txt`, `chuki_tag_suf.txt` 等
- `template/OPS/package.vm`, `template/OPS/toc.ncx.vm`, `template/OPS/css/*.vm`, `template/META-INF/container.xml`, `template/mimetype`
- `gaiji/dakuten/u*-u*.ttf`（222 本）
- `web/www.aozora.gr.jp/extract.txt`, `web/ncode.syosetu.com/extract.txt`, `web/kakuyomu.jp/extract.txt`, `web/novel.syosetu.org/extract.txt` 他
- `presets/*.ini`
- `AozoraEpub3.ini`, `setting_narourb.ini`, `AozoraEpub3.ico`, `AozoraEpub3.jar`, `AozoraEpub3.exe`（ZIP のみ）/ `AozoraEpub3.sh`（TAR のみ）

**漏れを発見した場合**:
1. `build.gradle` の **3 箇所すべて**を確認し、修正
2. §3.1 の clean からやり直す
3. 再検証

**根本対処（別 PR 化予定）**: include リストを単一の Closure に集約し、3 タスクで使い回す形に build.gradle をリファクタする。`refactor/build-gradle-dist-consolidate` ブランチで別 PR として進める（Stage 0A と独立）。

---

### 6.2 `createExe` が失敗する（Launch4j）

**症状**: `Could not resolve all artifacts` / `ico file not found` / `Cannot run program "wine"`

| 原因 | 対処 |
|------|------|
| `AozoraEpub3.ico` がプロジェクトルートにない | リポジトリ最新を pull、ico 復元 |
| Launch4j が Wine を要求している（Linux/macOS で実行している） | **Windows でビルドする**。Launch4j は内部で Windows バイナリを生成するため、非 Windows では Wine が必要 |
| JDK 21 がアクティブでない | `java -version` 確認、`gradle.properties` の `org.gradle.java.home` 確認 |
| `tasks.jar.archiveFile` が空（`build/libs/` に JAR が無い） | `clean` 後に `gradlew jar` を単独で実行して `build/libs/AozoraEpub3.jar` の生成を確認 |
| Launch4j プラグイン `4.0.0` の API 変更 | `build.gradle:158-169` で `dontWrapJar = true` / `jarFiles = files(...)` 形式になっているか確認 |

### 6.3 `tarDistribution` が `.sh` を含まない

**症状**: `build/distributions/AozoraEpub3-*.tar.gz` を展開しても `AozoraEpub3.sh` が無い

**原因**: `createLauncher` タスクが outputs を宣言しておらず、Gradle が依存解決に失敗するケースがある。さらに `tarDistribution` が `build/distributions/` から読みつつ同じディレクトリに書き出す自己参照構造のため、初回実行時に `.sh` が未生成のことがある。

**対処**:
```bash
# createLauncher を単独で先に実行
./gradlew --no-daemon createLauncher
ls -la build/distributions/AozoraEpub3.sh  # 存在確認、実行権限 0755 確認

# 続いて dist を実行
./gradlew --no-daemon dist
```

それでも入らない場合は手動で TAR に追加:
```bash
cd build/distributions
gzip -d AozoraEpub3-*.tar.gz
tar --append -f AozoraEpub3-*.tar -C . AozoraEpub3.sh
gzip AozoraEpub3-*.tar
```

### 6.4 `build/libs/` に古い JAR が混入し、ZIP が肥大化

**症状**: 配布 ZIP のサイズが想定（〜25 MB）より大きい、`AozoraEpub3-old.jar` が同梱されている

**原因**: `from('build/libs') { include '*.jar' }` がワイルドカードで拾うため、前回のバージョンの JAR が残っているとそれも含まれる

**対処**: §3.1 の事前クリーンアップを徹底。`./gradlew clean` ではなく `rm -rf build/libs/*.jar` でも可

### 6.5 ZIP/TAR にテンプレート/外字が二重に含まれる

**症状**: `template/` や `gaiji/` 配下のファイルが ZIP 内で 2 箇所に存在

**原因**: 過去に `application` プラグインの `distZip` と `zipDistribution` の両方が同じファイルを含めて競合（コミット `de7c1e4` で対処済み、再発したらリグレッション）

**対処**:
- `build.gradle` の `distZip.enabled = false` / `distTar.enabled = false`（line 109-110）が残っているか確認
- `distributions { main { contents { ... } } }` ブロックと `task zipDistribution` ブロックの `from(...)` で重複指定がないか目視確認

### 6.6 文字化け / 行末コード破損

**症状**: 配布 ZIP 内の `.ini` や `.txt` を Windows メモ帳で開くと改行が消える

**原因**: 非 Windows 環境（Linux/macOS）でビルドし、`.ini` `.txt` の CRLF が LF に正規化された

**対処**: **Windows でビルドする**。これが本書冒頭で「ローカルビルドを正とする」と書いた根本理由。`.gitattributes` で `* text=auto eol=crlf` を設定すれば Linux でも CRLF を維持できるが、現状未整備（将来課題）

### 6.7 Gradle daemon の stale state

**症状**: コード変更したのに古いバイナリが入る、原因不明のクラスロードエラー

**対処**: 全コマンドに `--no-daemon` を付ける（本書で常用している理由）。さらに頑固な場合：
```bash
./gradlew --stop
rm -rf ~/.gradle/caches/build-cache-*
./gradlew --no-daemon clean dist
```

### 6.8 `version` が `unspecified` になる

**症状**: 出力 ZIP/TAR が `AozoraEpub3-unspecified.zip` という名前

**原因**: `build.gradle` の `version = '1.x.x-jdk21'` が未更新／コメントアウトされている

**対処**: `build.gradle:10` を確認

### 6.9 タグ署名が付かない

**症状**: GitHub Release ページに署名バッジが表示されない

**確認**:
```bash
git config --local --get tag.gpgsign        # → true
git config --local --get user.signingkey    # → ~/.ssh/id_ed25519_aozora_signing.pub
git tag -v v1.x.x-jdk21                      # → "Good signature" 表示
```

`tag.gpgsign=true` が `--local` で設定されている前提（v1.3.3-jdk21 以降の運用）。`--global` には設定しないこと。

### 6.10 `gh release create` がアセットアップロード失敗

**症状**: タイムアウト、`HTTP 502`

**対処**: アセット個別アップロードに切り替え：
```bash
gh release create v1.x.x-jdk21 --title "..." --notes-file release-notes.md
gh release upload v1.x.x-jdk21 build/distributions/AozoraEpub3-*.zip
gh release upload v1.x.x-jdk21 build/distributions/AozoraEpub3-*.tar.gz
gh release upload v1.x.x-jdk21 build/distributions/SHA256SUMS
```

### 6.11 `epubcheck` のダウンロード失敗

**症状**: `setupEpubcheck` タスクで GitHub release からの取得が失敗

**対処**: `dist` 自体は epubcheck を必要としない。ローカルに既に epubcheck.jar があるなら `-PepubcheckJar=/path/to/epubcheck.jar` を付けて回避

`setupEpubcheck` の自動ダウンロード（`ant.get`）は失敗することがあるが、`curl` なら通る:

```bash
curl -sL -o build/tools/epubcheck-5.3.0.zip --create-dirs \
  https://github.com/w3c/epubcheck/releases/download/v5.3.0/epubcheck-5.3.0.zip
cd build/tools && unzip -q -o epubcheck-5.3.0.zip && cd ../..
./gradlew --no-daemon epubcheck -PepubDir=build/epub_local
```

> **注意**: jar が無い状態で `epubcheck` タスクを実行すると「epubcheck failed for N file(s)」という
> **EPUB 側に問題があるかのようなメッセージ**で落ちる。実体は jar 不在なので、まず jar の存在を確認する。
> 壊れていた `tools/epubcheck-5.2.0.jar`（未追跡・9 bytes の `Not Found`）は削除済み。
> ローカル検証には `tools/epubcheck-5.1.0/epubcheck.jar` が使える。

---

## 7. 緊急ロールバック

公開した Release に致命的バグが発覚した場合：

```bash
# 1. Release を draft に戻す（アセット URL を無効化）
gh release edit v1.x.x-jdk21 --draft

# 2. 修正版を作成（patch バンプ）
# ... 修正 → §3-§4 をやり直し → v1.x.(x+1)-jdk21 リリース

# 3. 旧 Release は draft のまま残す（履歴保持）
# 完全削除する場合のみ:
# gh release delete v1.x.x-jdk21 --cleanup-tag --yes
```

**注意**: タグ削除は `git push origin --delete v1.x.x-jdk21` も必要。**force push に等しい破壊的操作**なので、本当に削除する場合のみ。通常は draft 化＋次バージョン公開で対応する。

---

## 8. 関連ドキュメント

- [CLAUDE.md](../CLAUDE.md) — Claude Code 向けコードベース案内
- [AGENTS.md](../AGENTS.md) — Codex 向けコードベース案内
- [docs/development.md](development.md) — 開発者向けガイド
- [docs/modernization-plan.md](modernization-plan.md) — モダン化計画書
- [SECURITY.md](../SECURITY.md) — セキュリティポリシー
- [VERIFY.md](../VERIFY.md) — ダウンロード検証手順

## 9. 改訂履歴

- 2026-08-01: 監査 #16 の修正（CLI `-url` の zip / txtz / rar 対応）に合わせて §2.1.1 を更新。§6.11 の epubcheck jar の記述を現状に合わせて更新
- 2026-07-25: v1.3.7-jdk21 リリース時の実施結果を反映。§2.1.1 の青空文庫 URL の記述を訂正（zip URL 直接指定は CLI 未対応）、§6.11 に epubcheck の手動取得手順と紛らわしい失敗メッセージの注意を追記
- 2026-04-30: 初版作成（v1.3.5-jdk21 リリースに向けた整備）
