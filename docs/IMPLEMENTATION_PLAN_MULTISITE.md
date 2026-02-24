# マルチサイト対応 実装計画書

**作成日:** 2026-02-18
**最終更新:** 2026-02-24
**ステータス:** Phase 2 進行中 🔨 (Phase 2-1/2-3/2-4 実装済み)

---

## 1. 背景・目的

現在 AozoraEpub3 は小説家になろうを中心に10サイトの `extract.txt` を同梱しているが、
主要サイトである**カクヨム (kakuyomu.jp)** が未対応。

narou gem (Ruby) のマルチサイト設計を参考に、Java 側の対応範囲を調査した結果、
**カクヨム対応は `extract.txt` の追加 + Java 1箇所の修正で実現**できることが判明した。

本計画書では、カクヨム対応を主軸に、既存サイト定義の更新も含めた作業内容を整理する。

---

## 2. 調査結果サマリ

### 2.1 アーキテクチャ比較

| 項目 | narou gem (Ruby) | AozoraEpub3 (Java) |
|------|-----------------|---------------------|
| サイト定義方式 | YAML + 正規表現 | `extract.txt` + CSS セレクタ (Jsoup) |
| 対応サイト数 | 6 | 10 → **11** (カクヨム追加) |
| テキスト変換 | `ConverterBase` (全サイト共通) | `WebAozoraConverter` (全サイト共通) |
| サイト判定 | URL 正規表現マッチ | FQDN ディレクトリ名マッチ |
| API 連携 | なろう API (バッチ更新チェック) | なろう API (メタデータ取得) |

### 2.2 サイト対応状況

| サイト | ドメイン | narou gem | AozoraEpub3 | 対応方針 |
|--------|---------|:---------:|:-----------:|----------|
| 小説家になろう | `ncode.syosetu.com` | ✅ | ✅ | 維持 |
| 小説家になろう R18 | `novel18.syosetu.com` | ✅ | ✅ | 維持 |
| **カクヨム** | **`kakuyomu.jp`** | ✅ | **✅ 追加** | **完了** |
| ハーメルン | `syosetu.org` | ✅ | ✅ | extract.txt 更新検討 |
| 暁 | `www.akatsuki-novels.com` | ✅ | ✅ | 維持 |
| Arcadia | `www.mai-net.net` | ✅ | ✅ | 維持 |
| novelist.jp | `novelist.jp` | ❌ | ✅ | 維持 (独自) |
| FC2 小説 | `novel.fc2.com` | ❌ | ✅ | 維持 (独自) |
| dNoVeLs | `www.dnovels.net` | ❌ | ✅ | 維持 (独自) |
| NEWVEL-LIBRARY | `www.newvel.jp` | ❌ | ✅ | 維持 (独自) |

### 2.3 現行エンジンの設計

`WebAozoraConverter` はサイト定義を完全にデータ駆動で処理する:

- **サイト判定**: URL から FQDN を抽出 → `web/<FQDN>/extract.txt` を探索
- **HTML 解析**: Jsoup による CSS セレクタベースの要素抽出
- **コンバータキャッシュ**: `static HashMap<String, WebAozoraConverter>` でシングルトン管理
- **テキスト変換**: narou.rb 互換パイプライン (`NarouFormatSettings` 制御) は全サイト共通適用

---

## 3. 作業項目

### Phase 1: カクヨム対応 ✅ **完了 (2026-02-19)**

#### 1-1. カクヨム現行 HTML 構造の調査 ✅ **完了 (2026-02-19 実機確認)**

**調査方法**: PowerShell スクリプトでカクヨム実サイトの HTML 取得・解析
(ツール: `tools/check_kakuyomu.ps1` 〜 `tools/check_kakuyomu4.ps1`)

**発見事項**:

| 調査項目 | 結果 |
|---------|------|
| レンダリング方式 | **Next.js + Apollo GraphQL SPA** (CSR) |
| TOC 目次 HTML の <a> タグ | **約7件のみ** (154話の作品でも7件しかない) |
| 全エピソード一覧の所在 | `<script id="__NEXT_DATA__">` JSON に全件格納 |
| TOC CSS クラス名 | **CSS Modules (ハッシュ付き)** に変更 → `widget-toc-*` 系は完全消滅 |
| エピソードページ CSS | `widget-episodeTitle` / `widget-episodeBody` は**現行も有効** |
| 著者名 | `__NEXT_DATA__` JSON の `"activityName"` (最初の出現 = 作者) |
| あらすじ | `__NEXT_DATA__` JSON の `"introduction"` フィールド |
| タイトル | `<title>` タグ (末尾の " - カクヨム" を除去) |

**`__NEXT_DATA__` JSON 構造** (Apollo GraphQL キャッシュ形式):
```json
{
  "props": { "pageProps": { "apolloState": {
    "Work:16817330649941556887": { "title": "...", "author": { "__ref": "..." } },
    "Episode:16817330650193350811": { "title": "...", "publishedAt": "..." },
    "UserAccount:xxx": { "activityName": "著者名", "name": "..." },
    "introduction": "あらすじ..."
  }}}
}
```

#### 1-2. `web/kakuyomu.jp/extract.txt` の作成・更新 ✅ **完了 (2026-02-19)**

**成果物**: `web/kakuyomu.jp/extract.txt`

**確定セレクタ一覧** (実機確認済み 2026-02-19):

| ExtractId | CSS セレクタ | 取得方法 | ステータス |
|-----------|-------------|---------|-----------|
| `TITLE` | `title:0` | `<title>` タグから " - カクヨム" を除去 | ✅ 実機確認 |
| `AUTHOR` | `script#__NEXT_DATA__:0` | JSON の `"activityName"` (最初) | ✅ 実機確認 |
| `DESCRIPTION` | `script#__NEXT_DATA__:0` | JSON の `"introduction"` | ✅ 実機確認 |
| `HREF` | `a[href*="/episodes/"]` | HTML から ~7件のみ (JSON フォールバックが本体) | ✅ フォールバック用 |
| `SUB_UPDATE` | (コメントアウト) | HTML に対応要素なし、Phase 2 検討 | ⚠️ Phase 2 |
| `CONTENT_CHAPTER` | (コメントアウト) | エピソードページに章情報なし、Phase 2 検討 | ⚠️ Phase 2 |
| `CONTENT_SUBTITLE` | `p.widget-episodeTitle:0` | エピソードページ (現行クラス名維持) | ✅ 実機確認 |
| `CONTENT_ARTICLE` | `div.widget-episodeBody:0,.widget-episode-inner:0` | エピソードページ (現行クラス名維持) | ✅ 実機確認 |
| `CONTENT_PREAMBLE` | (コメントアウト) | カクヨムでは前書き区分なし | N/A |
| `CONTENT_APPENDIX` | (コメントアウト) | カクヨムでは後書き区分なし | N/A |

#### 1-3. Java コード変更: `__NEXT_DATA__` フォールバック ✅ **完了 (2026-02-19)**

**背景**: TOC ページの HTML には ~7エピソードの `<a>` タグのみが存在し、154話のような長編は対応不可。全エピソードは `__NEXT_DATA__` JSON から取得する必要がある。

**変更ファイル**: `src/com/github/hmdev/web/WebAozoraConverter.java`

**追加内容 1: `extractEpisodesFromNextData()` メソッド**

```java
/**
 * __NEXT_DATA__ JSON からエピソードURLリストを抽出する (カクヨムなど Next.js サイト対応)
 * Apollo GraphQL キャッシュの "Episode:ID" エントリを出現順・重複なしで収集する
 */
private List<String> extractEpisodesFromNextData(Document doc, String urlString) {
    Elements scripts = doc.select("script#__NEXT_DATA__");
    if (scripts.isEmpty()) return null;
    String json = scripts.get(0).html();
    Pattern workPattern = Pattern.compile("/works/(\\d+)");
    Matcher workMatcher = workPattern.matcher(urlString);
    if (!workMatcher.find()) return null;
    String workId = workMatcher.group(1);
    Pattern epPattern = Pattern.compile("\"Episode:(\\d+)\"");
    Matcher epMatcher = epPattern.matcher(json);
    Vector<String> result = new Vector<String>();
    HashSet<String> seen = new HashSet<String>();
    while (epMatcher.find()) {
        String epId = epMatcher.group(1);
        if (seen.add(epId)) {
            result.add(this.baseUri + "/works/" + workId + "/episodes/" + epId);
        }
    }
    return result.isEmpty() ? null : result;
}
```

**追加内容 2: `convertToAozoraText()` にフォールバック呼び出し**

```java
// __NEXT_DATA__ JSON フォールバック: HTMLのhrefs件数より多い場合に使用 (カクヨムなど Next.js サイト対応)
List<String> nextDataEpisodes = extractEpisodesFromNextData(doc, urlString);
if (nextDataEpisodes != null && nextDataEpisodes.size() > chapterHrefs.size()) {
    LogAppender.println("__NEXT_DATA__ JSON からエピソード一覧取得: " + nextDataEpisodes.size() + "話");
    chapterHrefs.clear();
    chapterHrefs.addAll(nextDataEpisodes);
}
```

**動作原理**:

| 条件 | 挙動 |
|------|------|
| カクヨム (154話): JSON 154件 > HTML 7件 | JSON の154件を使用 ✅ |
| 通常サイト: `Episode:` なし → null | フォールバックなし、通常処理 |
| URL に `/works/` がない → null | フォールバックなし、通常処理 |
| JSON ≤ HTML の件数 | フォールバックなし、HTML を使用 |

#### 1-4. 動作テスト ✅ **完了 (2026-02-19 実機確認)**

**テスト作品**: 「他校の氷姫を助けたら、お友達から始める事になりました」(皐月陽龍)
**URL**: `https://kakuyomu.jp/works/16817330649941556887`
**話数**: 154話

**テスト結果**:
- ✅ タイトル取得成功 (title タグから "- カクヨム" 除去)
- ✅ 著者名取得成功 (`__NEXT_DATA__` JSON の activityName)
- ✅ 全154話のエピソード一覧取得 (`__NEXT_DATA__` JSON フォールバック)
- ✅ 本文テキスト取得・変換成功 (`div.widget-episodeBody`)
- ✅ 話タイトル取得成功 (`p.widget-episodeTitle`)
- ✅ テキストファイル生成完了

---

### Phase 2: カクヨム固有の課題対応 ✅ **2-1/2-3/2-4 完了 (2026-02-24)**

#### 2-1. エピソード章構造 (`CONTENT_CHAPTER`) の対応 ✅ **完了 (2026-02-24)**

- **課題**: 章区切り情報はエピソードページに存在しない。TOC の `__NEXT_DATA__` JSON には `TableOfContentsChapter` エントリがあり、各エピソードの章タイトルは取得可能。
- **実装内容**:
  - `extractEpisodesFromNextData()` 内で `buildEpisodeChapterMapFromNextData()` を呼び出し
  - `buildEpisodeChapterMapFromNextData()`: JSON 内の `"TableOfContentsChapter:ID":` ブロックを走査し、各チャプターの `"title"` と `"__ref":"Episode:ID"` を収集、epId → chapterTitle のマップを構築
  - インスタンスフィールド `HashMap<String, String> nextDataEpisodeChapterMap` にエピソードURL → 章タイトルを格納
  - エピソード処理ループで `CONTENT_CHAPTER` が null の場合に `nextDataEpisodeChapterMap` をフォールバックとして使用
- **動作原理**:
  - 章なし作品: `TableOfContentsChapter` エントリなし → `nextDataEpisodeChapterMap` null → 従来通りフラット出力
  - 章あり作品: 章タイトルが変わったときに `［＃大見出し］` を出力 (既存の `CONTENT_CHAPTER` 処理ロジックを流用)

#### 2-2. 更新日時 (`SUB_UPDATE`) の対応

- **課題**: TOC HTML に更新日時の `<span>` 等が存在しない。`__NEXT_DATA__` JSON の `"publishedAt"` が利用可能。
- **対策案**: `extractEpisodesFromNextData()` を拡張し、`publishedAt` をエピソードURLとセットで返す。
- **状態**: ⏳ Phase 2 残

#### 2-3. カクヨム固有のテキスト変換 ✅ **完了 (2026-02-24)**

- **課題**: `<em class="emphasisDots">` による傍点表現がテキストとして失われる。
- **実装内容**: `_printNode()` に `em.emphasisDots` ハンドラを追加:
  ```java
  } else if ("em".equals(elem.tagName()) && elem.hasClass("emphasisDots")) {
      bw.append("［＃傍点］");
      _printNode(bw, node);
      bw.append("［＃傍点終わり］");
  ```
- `<em>` 以外の一般タグ (class なし等) は既存の `else` ブランチでテキストのみ出力 (変更なし)

#### 2-4. あらすじ script 要素対応 + JSON 改行復元 ✅ **完了 (2026-02-24)**

- **課題1**: `getExtractFirstElement()` が `<script>` 要素を返すと `printNode()` が DataNode (スクリプト内容) を処理できず、あらすじが空になる。
- **課題2**: `__NEXT_DATA__` JSON の `introduction` フィールドの `\n` エスケープが改行として表示されない。
- **実装内容**: DESCRIPTION 処理コードで `description.tagName()` が `"script"` の場合に分岐:
  - `getExtractText()` でテキスト取得 (正規表現を適用して JSON から introduction を抽出)
  - `.replace("\\r\\n", "\n").replace("\\r", "\n").replace("\\n", "\n")` で JSON エスケープを実改行に変換
  - 改行で分割して行ごとに `printText()` + `bw.append('\n')` で出力
  - `<script>` 以外の通常 HTML 要素は従来通り `printNode()` を使用

---

### Phase 3: 既存サイト定義の更新 (任意)

#### 3-1. ハーメルン (`novel.syosetu.org`) の extract.txt 更新
- **課題**: 現行の extract.txt が古い HTML 構造に基づいている可能性
- **作業**: 現行サイトの HTML を確認し、必要に応じてセレクタを更新

#### 3-2. 閉鎖・変更サイトの整理
- **作業**: 各サイトの現行稼働状況を確認し、閉鎖済みサイトの extract.txt にコメント追記

---

## 4. 技術的な詳細

### 4.1 extract.txt のフォーマット

```
# コメント行
内部ID<TAB>CSSセレクタ[:位置][,代替セレクタ[:位置]]<TAB>正規表現<TAB>置換後
```

- **CSSセレクタ**: Jsoup の CSS セレクタ構文 (jQuery 互換)
- **位置指定**: `:0` = 最初, `:-1` = 最後, `:0:1:2` = 複数指定, 省略 = 全て
- **カンマ区切り**: 複数セレクタ指定時、先頭を優先 (フォールバック)
- **正規表現/置換**: 抽出した innerHTML に対する後処理 (任意)
- **`script` タグの取得**: `element.html()` でスクリプト内容を取得 → JSON パースに利用可能

### 4.2 利用可能な ExtractId 一覧

| 区分 | ExtractId | 説明 |
|------|-----------|------|
| 設定 | `COOKIE` | HTTP Cookie ヘッダ値 |
| 設定 | `PAGE_REGEX` | URL パターン (現在無効) |
| 目次 | `SERIES` | シリーズ名 |
| 目次 | `TITLE` | 作品タイトル |
| 目次 | `AUTHOR` | 著者名 |
| 目次 | `DESCRIPTION` | あらすじ |
| 目次 | `COVER_IMG` | 表紙画像 (`<img>`) |
| 目次 | `COVER_HREF` | 表紙画像リンク |
| 目次 | `HREF` | 各話リンク |
| 目次 | `SUB_UPDATE` | 各話更新日時 |
| 目次 | `CONTENT_UPDATE_LIST` | 更新日時文字列 (フォールバック) |
| 目次 | `SUBTITLE_LIST` | 各話タイトル (フォールバック) |
| 目次 | `PAGE_NUM` | ページ番号 |
| 目次 | `PAGE_URL` | ページ URL テンプレート |
| 本文 | `CONTENT_CHAPTER` | 大見出し (章) |
| 本文 | `CONTENT_SUBTITLE` | 中見出し (話タイトル) |
| 本文 | `CONTENT_UPDATE` | 本文ページ内の更新日時 |
| 本文 | `CONTENT_IMG` | 本文外画像 |
| 本文 | `CONTENT_ARTICLE` | 本文 |
| 本文 | `CONTENT_ARTICLE_START` | 本文抽出開始位置 |
| 本文 | `CONTENT_ARTICLE_END` | 本文抽出終了位置 |
| 本文 | `CONTENT_ARTICLE_SEPARATOR` | 複数要素間セパレータ |
| 本文 | `CONTENT_PREAMBLE` | 前書き |
| 本文 | `CONTENT_PREAMBLE_START` | 前書き開始位置 |
| 本文 | `CONTENT_PREAMBLE_END` | 前書き終了位置 |
| 本文 | `CONTENT_APPENDIX` | 後書き |
| 本文 | `CONTENT_APPENDIX_START` | 後書き開始位置 |
| 本文 | `CONTENT_APPENDIX_END` | 後書き終了位置 |

### 4.3 `__NEXT_DATA__` JSON 対応の設計パターン

カクヨムのような Next.js サイトは HTML に動的データを持たず `<script id="__NEXT_DATA__">` JSON に格納する。このパターンへの対応方法:

**extract.txt での対応** (メタ情報取得):
```
# script#__NEXT_DATA__ を CSS セレクタで選択、element.html() でJSON全文取得
# 正規表現でJSONフィールドを抽出
AUTHOR	script#__NEXT_DATA__:0	.*?"activityName":"([^"]+)".*	$1
DESCRIPTION	script#__NEXT_DATA__:0	.*?"introduction":"([^"]+)".*	$1
```

**Java コードでの対応** (エピソード一覧取得):
`extract.txt` の正規表現では複数マッチを扱えないため、
Java の `extractEpisodesFromNextData()` メソッドで全エピソード ID を収集し、
HTML の hrefs より件数が多い場合にフォールバックとして使用する。

### 4.4 narou gem YAML との設計思想の違い

| 観点 | narou gem | AozoraEpub3 |
|------|-----------|-------------|
| HTML 解析 | 正規表現でテキストマッチ | Jsoup DOM ツリー走査 |
| 堅牢性 | HTML 変更に弱い (正規表現依存) | やや強い (CSS セレクタは構造変更に強い) |
| 目次パース | 1つの巨大正規表現で全情報抽出 | `HREF`/`SUB_UPDATE` 等を個別に抽出 |
| 本文抽出 | 正規表現グループで body/intro/postscript 分離 | CSS セレクタで `CONTENT_ARTICLE`/`PREAMBLE`/`APPENDIX` 個別指定 |
| 更新チェック | API (なろう) or TOC スクレイピング | `SUB_UPDATE` タグ比較 |

---

## 5. リスク・懸念事項

| リスク | 影響度 | 対策 | 状態 |
|--------|:------:|------|------|
| カクヨムが HTML を変更する (TOC CSS クラス変更) | 高 | `__NEXT_DATA__` JSON フォールバックは CSS クラスに依存しないため影響なし | ✅ 対応済 |
| カクヨムが `__NEXT_DATA__` 構造を変更する | 中 | `"Episode:ID"` の命名規則は Apollo キャッシュ標準であり変更リスク低 | 監視継続 |
| エピソードページの `widget-*` クラスが変更される | 中 | 監視・発生時に extract.txt を更新 | 監視継続 |
| 2 階層章構造の非対応 | 低 | Phase 2-1 で対応。現状は章なしのフラット出力 | Phase 2 |
| レート制限 (429 エラー) | 中 | ダウンロード間隔の設定確認。必要なら wait 追加 | 監視継続 |
| 著作権・利用規約 | - | 個人利用の範囲であることを前提とする | - |

---

## 6. 作業見積・進捗

| Phase | 作業内容 | Java 変更 | 状態 |
|-------|----------|:---------:|------|
| **1-1** | カクヨム HTML 構造調査 (実機確認) | 不要 | ✅ 完了 |
| **1-2** | `web/kakuyomu.jp/extract.txt` 作成 | 不要 | ✅ 完了 |
| **1-3** | `__NEXT_DATA__` フォールバック実装 | 必要 | ✅ 完了 |
| **1-4** | 動作テスト (154話 実機確認) | 不要 | ✅ 完了 |
| **2-1** | 章構造 (`CONTENT_CHAPTER`) 対応 | 必要 | ✅ 完了 (2026-02-24) |
| **2-2** | 更新日時 (`SUB_UPDATE`) 対応 | 必要 | ⏳ Phase 2 残 |
| **2-3** | 傍点 (`em.emphasisDots`) テキスト変換 | 必要 | ✅ 完了 (2026-02-24) |
| **2-4** | あらすじ改行復元 + script 要素対応 | 必要 | ✅ 完了 (2026-02-24) |
| **3-1** | ハーメルン extract.txt 更新 | 不要 | ⏳ Phase 3 |
| **3-2** | 閉鎖サイト整理 | 不要 | ⏳ Phase 3 |

---

## 7. 参考資料

- narou gem ソース: `/c/Ruby32-x64/lib/ruby/gems/3.2.0/gems/narou-3.8.2/`
  - サイト定義: `webnovel/*.yaml`
  - ダウンローダ: `lib/downloader.rb`
  - HTML 変換: `lib/html.rb`
  - テキスト変換: `lib/converterbase.rb`
- AozoraEpub3 ソース:
  - サイト定義: `web/<FQDN>/extract.txt`
  - コンバータ: `src/com/github/hmdev/web/WebAozoraConverter.java`
  - ExtractInfo: `src/com/github/hmdev/web/ExtractInfo.java`
  - サンプル: `web/extract_sample.txt`
- 調査スクリプト: `tools/check_kakuyomu.ps1` 〜 `tools/check_kakuyomu4.ps1`
- narou gem リポジトリ: https://github.com/whiteleaf7/narou
- カクヨム: https://kakuyomu.jp/
