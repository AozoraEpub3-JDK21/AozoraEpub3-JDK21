# なろうAPI統合 - 詳細設計書

**作成日**: 2025年12月17日  
**対象ブランチ**: `experiment/dev-work`

---

## 1. 概要

### 1.1 目的
小説家になろうの公式APIを活用し、以下を実現する：
- **安定性向上**: HTMLスクレイピングからAPI取得への移行
- **効率化**: レート制限緩和（1日80,000リクエスト）
- **メタデータ強化**: 評価ポイント、ブックマーク数などの取得
- **後方互換性**: 既存のHTMLスクレイピングをフォールバックとして維持

### 1.2 対象サイト
- `ncode.syosetu.com` (小説家になろう)
- `novel18.syosetu.com` (ノクターンノベルズ - 別APIあり)

---

## 2. アーキテクチャ設計

### 2.1 コンポーネント構成

```
┌─────────────────────────────────────────┐
│      AozoraEpub3Applet (GUI)           │
│  ┌─────────────────────────────────┐   │
│  │  Web変換タブ                     │   │
│  │  ☑ なろうAPI使用                 │   │
│  │  ☐ HTMLフォールバック            │   │
│  └─────────────────────────────────┘   │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│     WebAozoraConverter (オーケストレータ) │
│  ┌─────────────────────────────────┐   │
│  │ API優先 → HTMLフォールバック     │   │
│  └─────────────────────────────────┘   │
└───────┬───────────────────┬─────────────┘
        │                   │
        ▼                   ▼
┌──────────────────┐ ┌─────────────────────┐
│ NarouApiClient   │ │ Jsoup (HTML解析)    │
│  - メタデータ取得 │ │  - 本文取得         │
│  - 作品検索      │ │  - フォールバック   │
└──────────────────┘ └─────────────────────┘
```

---

## 3. 詳細設計

### 3.1 新規クラス: `NarouApiClient`

**配置**: `src/com/github/hmdev/web/api/NarouApiClient.java`

#### 責務
- なろう小説APIとの通信
- JSON/YAMLレスポンスのパース
- レート制限の管理（1日80,000回、400MB）
- エラーハンドリング

#### 主要メソッド

```java
package com.github.hmdev.web.api;

public class NarouApiClient {
    // APIエンドポイント
    private static final String API_ENDPOINT = "https://api.syosetu.com/novelapi/api/";
    
    // レート制限管理
    private RateLimiter rateLimiter;
    
    /**
     * Nコードから作品メタデータを取得
     * @param ncode 作品コード (例: "n0001a")
     * @return NovelMetadata 作品情報
     * @throws ApiException API呼び出し失敗
     */
    public NovelMetadata getNovelMetadata(String ncode) throws ApiException;
    
    /**
     * 作品の章一覧を取得 (URLから推測)
     * 注: APIでは章データは取得できないため、HTML取得と組み合わせ
     * @param ncode 作品コード
     * @return List<ChapterInfo> 章情報リスト
     */
    public List<ChapterInfo> getChapterList(String ncode) throws ApiException;
    
    /**
     * 検索条件で作品を検索
     * @param params 検索パラメータ
     * @return List<NovelMetadata> 検索結果
     */
    public List<NovelMetadata> searchNovels(SearchParams params) throws ApiException;
    
    /**
     * レート制限チェック
     * @return boolean リクエスト可能ならtrue
     */
    public boolean canMakeRequest();
}
```

---

### 3.2 データモデル

#### 3.2.1 `NovelMetadata`

```java
package com.github.hmdev.web.api.model;

import java.time.LocalDateTime;

public class NovelMetadata {
    private String ncode;              // Nコード (n0001a)
    private String title;              // タイトル
    private String writer;             // 作者名
    private int userid;                // 作者ID
    private String story;              // あらすじ
    private int biggenre;              // 大ジャンル (1:恋愛, 2:ファンタジー等)
    private int genre;                 // ジャンル (101:異世界〔恋愛〕等)
    private String keyword;            // キーワード
    private LocalDateTime generalFirstup;   // 初回掲載日
    private LocalDateTime generalLastup;    // 最終掲載日
    private int novelType;             // 1:連載, 2:短編
    private int end;                   // 0:完結, 1:連載中
    private int generalAllNo;          // 全話数
    private int length;                // 作品文字数
    private int time;                  // 読了時間(分)
    private int isstop;                // 長期停止中フラグ
    private int globalPoint;           // 総合評価ポイント
    private int favNovelCnt;           // ブックマーク数
    private int reviewCnt;             // レビュー数
    private LocalDateTime novelupdatedAt;   // 作品更新日時
    
    // getters/setters
}
```

#### 3.2.2 `ChapterInfo`

```java
package com.github.hmdev.web.api.model;

import java.time.LocalDateTime;

public class ChapterInfo {
    private String title;              // 章タイトル
    private String subtitle;           // サブタイトル
    private String href;               // 相対URL (/n0001a/1/)
    private LocalDateTime updateDate;  // 更新日時
    private boolean isChapter;         // 章区切りかどうか
    
    // getters/setters
}
```

#### 3.2.3 `SearchParams`

```java
package com.github.hmdev.web.api.model;

public class SearchParams {
    private String word;               // 検索ワード
    private Integer biggenre;          // 大ジャンル
    private Integer genre;             // ジャンル
    private String type;               // 作品タイプ (t:短編, r:連載中等)
    private Integer minlen;            // 最小文字数
    private Integer maxlen;            // 最大文字数
    private String order;              // ソート順 (new, hyoka等)
    private Integer lim;               // 取得件数 (1-500)
    
    // Builder パターン
    public static class Builder { ... }
}
```

---

### 3.3 WebAozoraConverter の拡張

#### 変更点

```java
public class WebAozoraConverter {
    // 追加フィールド
    private NarouApiClient apiClient;
    private boolean useApi = false;
    private boolean apiFallbackEnabled = true;
    
    /**
     * API使用を有効化
     */
    public void setUseApi(boolean useApi) {
        this.useApi = useApi;
        if (useApi && apiClient == null) {
            apiClient = new NarouApiClient();
        }
    }
    
    /**
     * 変換実行 (API対応版)
     * 1. API有効時: メタデータをAPIから取得
     * 2. 章一覧: HTMLから取得 (APIでは不可)
     * 3. 本文: HTMLから取得 (APIでは不可)
     * 4. API失敗時: HTMLフォールバック
     */
    public File convertToAozoraText(String urlString, File cachePath, ...) {
        // Nコード抽出
        String ncode = extractNcode(urlString);
        
        if (useApi && ncode != null) {
            try {
                // APIからメタデータ取得
                NovelMetadata metadata = apiClient.getNovelMetadata(ncode);
                LogAppender.println("API取得成功: " + metadata.getTitle());
                
                // メタデータをキャッシュ
                cacheMetadata(metadata);
                
                // 章一覧・本文はHTMLから取得（継続）
                return convertWithApiMetadata(metadata, urlString, cachePath, ...);
                
            } catch (ApiException e) {
                if (apiFallbackEnabled) {
                    LogAppender.println("API取得失敗、HTMLフォールバック: " + e.getMessage());
                    // 既存のHTML処理へ
                } else {
                    throw e;
                }
            }
        }
        
        // 既存のHTML処理
        return convertWithHtml(urlString, cachePath, ...);
    }
    
    /**
     * URLからNコードを抽出
     * 例: https://ncode.syosetu.com/n0001a/ → n0001a
     */
    private String extractNcode(String url) {
        Pattern pattern = Pattern.compile(".*/([nN]\\d{4}[a-zA-Z]+)/.*");
        Matcher matcher = pattern.matcher(url);
        return matcher.matches() ? matcher.group(1).toLowerCase() : null;
    }
}
```

---

### 3.4 GUI変更 (AozoraEpub3Applet)

#### 追加UI要素

**Webタブ内の新規コンポーネント**:

```java
// API設定セクション (interval設定の下に追加)
JCheckBox jCheckUseNarouApi;      // "なろうAPI使用"
JCheckBox jCheckApiFallback;      // "API失敗時HTML取得"
JLabel jLabelApiStatus;           // API使用状況表示
JButton jButtonApiTest;           // APIテストボタン

// 初期化
jCheckUseNarouApi = new JCheckBox("なろうAPI使用");
jCheckUseNarouApi.setToolTipText(
    "小説家になろう公式APIを使用します。\n" +
    "メタデータ取得が高速化され、レート制限が緩和されます。");
jCheckUseNarouApi.setSelected(true); // デフォルトON

jCheckApiFallback = new JCheckBox("API失敗時HTML取得");
jCheckApiFallback.setToolTipText(
    "API取得失敗時に従来のHTML取得にフォールバックします。");
jCheckApiFallback.setSelected(true);

jLabelApiStatus = new JLabel("API未使用");
jLabelApiStatus.setForeground(Color.GRAY);

// WebAozoraConverterへの設定反映
webConverter.setUseApi(jCheckUseNarouApi.isSelected());
webConverter.setApiFallbackEnabled(jCheckApiFallback.isSelected());
```

#### レイアウト変更

```
[Web変換タブ]
┌────────────────────────────────────┐
│ URL: [テキストフィールド        ] │
│ キャッシュパス: [              ] │
│ 取得間隔: [0.5] 秒              │
│                                    │
│ ☑ なろうAPI使用                    │  ← 新規
│   └ ☑ API失敗時HTML取得           │  ← 新規
│   API状態: 利用可能 (12345/80000) │  ← 新規
│   [APIテスト]                      │  ← 新規
│                                    │
│ ☑ 更新時のみ出力                  │
│ ☐ 追加更新分のみ                  │
└────────────────────────────────────┘
```

---

## 4. エラー処理

### 4.1 API例外階層

```java
package com.github.hmdev.web.api.exception;

public class ApiException extends Exception {
    public ApiException(String message) { super(message); }
    public ApiException(String message, Throwable cause) { super(message, cause); }
}

// レート制限超過
public class RateLimitException extends ApiException {
    private LocalDateTime retryAfter;
    public RateLimitException(LocalDateTime retryAfter) {
        super("レート制限超過。再試行可能時刻: " + retryAfter);
        this.retryAfter = retryAfter;
    }
}

// ネットワークエラー
public class NetworkException extends ApiException { ... }

// パースエラー
public class ParseException extends ApiException { ... }
```

### 4.2 フォールバック戦略

```
API呼び出し
    ├─ 成功 → API結果を使用
    ├─ NetworkException
    │   └─ Fallback有効 → HTML取得
    │       └─ Fallback無効 → エラー通知
    ├─ RateLimitException
    │   └─ 待機 → リトライ (最大3回)
    └─ ParseException
        └─ Fallback有効 → HTML取得
```

---

## 5. キャッシュ戦略

### 5.1 APIレスポンスキャッシュ

```
.cache/
  ├─ ncode.syosetu.com/
  │   ├─ n0001a/
  │   │   ├─ index.html          # 既存 (章一覧HTML)
  │   │   ├─ metadata.json       # 新規 (APIメタデータ)
  │   │   ├─ metadata.timestamp  # 新規 (取得日時)
  │   │   ├─ 1.html              # 既存 (各話本文)
  │   │   └─ 2.html
```

### 5.2 キャッシュ有効期限

- **メタデータ**: 1時間 (更新チェック用)
- **本文HTML**: 無期限 (更新日時で比較)

---

## 6. レート制限管理

### 6.1 制限値 (なろうAPI)

- **リクエスト数**: 80,000回/日
- **転送量**: 400MB/日
- **推奨間隔**: なし（制限内であれば連続可）

### 6.2 実装

```java
package com.github.hmdev.web.api.util;

public class RateLimiter {
    private static final int MAX_REQUESTS_PER_DAY = 80000;
    private static final long MAX_BYTES_PER_DAY = 400L * 1024 * 1024;
    
    private AtomicInteger requestCount = new AtomicInteger(0);
    private AtomicLong bytesTransferred = new AtomicLong(0);
    private LocalDateTime resetTime;
    
    public boolean canMakeRequest() {
        checkAndResetIfNeeded();
        return requestCount.get() < MAX_REQUESTS_PER_DAY 
            && bytesTransferred.get() < MAX_BYTES_PER_DAY;
    }
    
    public void recordRequest(int bytes) {
        requestCount.incrementAndGet();
        bytesTransferred.addAndGet(bytes);
    }
    
    private void checkAndResetIfNeeded() {
        if (LocalDateTime.now().isAfter(resetTime)) {
            requestCount.set(0);
            bytesTransferred.set(0);
            resetTime = LocalDateTime.now().plusDays(1);
        }
    }
}
```

---

## 7. 実装計画

### 7.1 Phase 1: 基盤構築 (2-3日)

- [ ] `NarouApiClient` 基本実装
  - [ ] JSON/YAMLパーサー (Jackson or Gson)
  - [ ] HTTP通信 (HttpURLConnection)
  - [ ] エラーハンドリング
- [ ] データモデル実装
  - [ ] `NovelMetadata`
  - [ ] `ChapterInfo`
  - [ ] `SearchParams`
- [ ] ユニットテスト作成
  - [ ] モックレスポンスでテスト
  - [ ] エラーケーステスト

### 7.2 Phase 2: WebAozoraConverter統合 (2-3日)

- [ ] `WebAozoraConverter` API対応
  - [ ] `useApi` フラグ追加
  - [ ] Nコード抽出ロジック
  - [ ] APIメタデータ→青空形式変換
  - [ ] HTMLフォールバック実装
- [ ] キャッシュ機能拡張
  - [ ] `metadata.json` 保存/読み込み
  - [ ] タイムスタンプ管理
- [ ] 統合テスト

### 7.3 Phase 3: GUI実装 (1-2日)

- [ ] `AozoraEpub3Applet` UI追加
  - [ ] チェックボックス配置
  - [ ] ステータスラベル
  - [ ] APIテストボタン
- [ ] イベントハンドラ実装
- [ ] 設定の永続化 (properties)

### 7.4 Phase 4: テスト・ドキュメント (1-2日)

- [ ] 実機テスト
  - [ ] なろう人気作品での動作確認
  - [ ] API制限テスト
  - [ ] フォールバックテスト
- [ ] ユーザードキュメント作成
- [ ] RELEASE_NOTES更新

**総所要時間**: 6-10日

---

## 8. リスクと対策

| リスク | 影響 | 対策 |
|--------|------|------|
| API仕様変更 | 高 | HTML取得をフォールバックとして維持 |
| レート制限超過 | 中 | ローカルキャッシュ活用、制限カウンター |
| ネットワークエラー | 中 | リトライ機構、タイムアウト設定 |
| JSON解析失敗 | 低 | エラーハンドリング、フォールバック |
| 後方互換性 | 低 | 既存機能を削除せず、API機能を追加 |

---

## 9. 将来拡張

### 9.1 追加機能候補

- [ ] **ランキングAPI統合** (`/man/rankapi/`)
  - 日間/週間/月間ランキングから一括DL
- [ ] **ユーザー検索API** (`/man/userapi/`)
  - お気に入り作者の新作自動取得
- [ ] **R18 API対応** (`/xman/api/`)
  - ノクターンノベルズ対応
- [ ] **バッチ検索機能**
  - 「完結済み異世界ファンタジー」等の条件一括DL
- [ ] **差分更新最適化**
  - `novelupdated_at` での効率的な更新チェック

### 9.2 パフォーマンス改善

- [ ] gzip圧縮有効化 (`gzip=5`)
- [ ] 並列リクエスト (ThreadPool)
- [ ] `of` パラメータで必要項目のみ取得

---

## 10. 参考資料

- [なろう小説API仕様](https://dev.syosetu.com/man/api/)
- [なろうランキングAPI](https://dev.syosetu.com/man/rankapi/)
- [商標ガイドライン](https://hinaproject.co.jp/hina_guideline.html)

---

## 付録A: API呼び出し例

### メタデータ取得

```bash
# Nコード指定
curl "https://api.syosetu.com/novelapi/api/?ncode=n0001a&out=json"

# gzip圧縮
curl "https://api.syosetu.com/novelapi/api/?ncode=n0001a&out=json&gzip=5"

# 必要項目のみ
curl "https://api.syosetu.com/novelapi/api/?ncode=n0001a&of=t-w-s-ga&out=json"
```

### 検索

```bash
# ファンタジージャンル、完結済み、10万字以上
curl "https://api.syosetu.com/novelapi/api/?biggenre=2&type=er&minlen=100000&lim=20&out=json"
```

---

**次のステップ**: 実装開始前にレビュー＆承認をお願いします。
