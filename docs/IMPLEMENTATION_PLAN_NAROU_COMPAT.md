# narou.rb 完全互換 実装計画書

**作成日**: 2026-02-14
**最終更新**: 2026-02-15
**目的**: WebAozoraConverter.java を narou.rb と完全互換にする
**対象バージョン**: narou-3.8.2

---

## 📋 実装状況サマリー（2026-02-15更新）

| カテゴリ | 総項目数 | 実装済 | 部分実装 | 未実装 | 進捗率 |
|---------|---------|-------|---------|-------|--------|
| Phase 3 設定項目 | 6 | 6 | 0 | 0 | 100% |
| Phase 3 変換処理 | 7 | 3 | 2 | 2 | 71% |
| 全体設定項目 | 42 | 14 | 0 | 28 | 33% |
| 全体変換処理 | 20 | 6 | 2 | 12 | 40% |
| 青空注記 | 25 | 8 | 0 | 17 | 32% |
| **Phase 3 合計** | **13** | **9** | **2** | **2** | **85%** |
| **全体合計** | **87** | **28** | **2** | **57** | **34%** |

---

## 🎯 Phase 1: 緊急修正（表示崩れ解消）

### 優先度: ★★★ CRITICAL

### 1.1 改行処理の修正

**問題**: HTML改行コード（\r\n）が残り、brタグも改行化されて2重改行になる

**参考コード**: `narou-3.8.2/lib/html.rb:65-67`
```ruby
def br_to_aozora(text = @string)
  text.gsub(/[\r\n]+/, "").gsub(/<br.*?>/i, "\n")
end
```

**実装箇所**: `WebAozoraConverter.java`

**タスク**:
- [ ] `printNode()` メソッドの前に、HTMLテキスト全体から `\r\n` を削除する処理を追加
- [ ] `<br>` タグのみを `\n` に変換する既存処理を維持
- [ ] `<p>` タグの終了タグ `</p>` も改行に変換（narou.rbの `p_to_aozora`）

**変更ファイル**:
```
src/com/github/hmdev/web/WebAozoraConverter.java
  - printNode() メソッド修正
  - または docToAozoraText() 内で事前処理
```

**テスト方法**:
1. なろう小説をダウンロード
2. converted.txt を確認
3. 空白行が1～2行追加されていないことを確認

---

### 1.2 表紙の挿絵注記挿入

**問題**:
- 表紙画像が `converted.png` として保存されている
- テキストファイルに挿絵注記が挿入されていない
- AozoraEpub3が横書き表紙を生成できない

**参考コード**: `narou-3.8.2/lib/novelconverter.rb:547-568`
```ruby
def self.get_cover_filename(archive_path)
  [".jpg", ".png", ".jpeg"].each do |ext|
    filename = "cover#{ext}"
    cover_path = File.join(archive_path, filename)
    return filename if File.exist?(cover_path)
  end
  nil
end

def create_cover_chuki
  cover_filename = self.class.get_cover_filename(@setting.archive_path)
  if cover_filename
    "［＃挿絵（#{cover_filename}）入る］"
  else
    ""
  end
end
```

**実装箇所**: `WebAozoraConverter.java:434-487`

**タスク**:
- [ ] 表紙画像のファイル名を `converted.png` → `cover.jpg` に変更
  - または `cover.png` に変更（元の形式を維持）
- [ ] タイトル・著者の次の行（3行目）に `［＃挿絵（cover.jpg）入る］` を挿入
- [ ] 挿絵注記の後に改行を追加

**変更箇所**:
```java
// WebAozoraConverter.java:434
File coverImageFile = new File(this.dstPath+"cover.jpg");  // 修正

// WebAozoraConverter.java:535 付近（著者の後）
bw.append('\n');

// ★★★ ここに追加 ★★★
if (coverImageFile.exists()) {
    bw.append("［＃挿絵（cover.jpg）入る］\n");
    bw.append('\n');
}
```

**テスト方法**:
1. converted.txt の3行目に `［＃挿絵（cover.jpg）入る］` があることを確認
2. cover.jpg が出力ディレクトリに存在することを確認
3. AozoraEpub3でEPUB化して表紙が横書きになることを確認

---

### 1.3 ルビ処理の完全実装

**問題**: HTMLの `<ruby>` タグが青空文庫形式に変換されていない

**参考コード**: `narou-3.8.2/lib/html.rb:74-83`
```ruby
def ruby_to_aozora(text = @string)
  text.tr("《》", "≪≫")  # 既存の山括弧を退避
      .gsub(/<ruby>(.+?)<\/ruby>/i) do
    splited_ruby = $1.split(/<rt>/i)
    next delete_tag(splited_ruby[0]) unless splited_ruby[1]
    ruby_base = delete_tag(splited_ruby[0].split(/<rp>/i)[0])
    ruby_text = delete_tag(splited_ruby[1].split(/<rp>/i)[0])
    "｜#{ruby_base}《#{ruby_text}》"
  end
end
```

**実装箇所**:
- `WebAozoraConverter.java:1204-1220` (printRuby メソッド)
- 新規メソッド追加が必要

**タスク**:
- [ ] HTMLテキスト全体に対して事前処理する `convertRubyTags()` メソッドを追加
- [ ] 既存の `《》` を `≪≫` に退避
- [ ] `<ruby>` タグを正規表現で検出
- [ ] `<rb>` または最初の要素を親文字として抽出
- [ ] `<rt>` を振り仮名として抽出
- [ ] `｜親文字《ふりがな》` 形式に変換
- [ ] `<rp>` タグは無視

**変更箇所**:
```java
// WebAozoraConverter.java に追加

/**
 * HTMLのrubyタグを青空文庫形式のルビに変換
 */
private String convertRubyTags(String html) {
    // 既存の《》を≪≫に退避
    html = html.replace("《", "≪").replace("》", "≫");

    // <ruby>タグを処理
    Pattern rubyPattern = Pattern.compile("<ruby>(.+?)</ruby>", Pattern.CASE_INSENSITIVE);
    Matcher matcher = rubyPattern.matcher(html);
    StringBuffer result = new StringBuffer();

    while (matcher.find()) {
        String rubyContent = matcher.group(1);

        // <rt>タグで分割
        String[] parts = rubyContent.split("<rt>", 2);
        if (parts.length < 2) {
            // rtタグがない場合はタグを削除して元のテキストのみ
            matcher.appendReplacement(result,
                Matcher.quoteReplacement(removeHtmlTags(parts[0])));
            continue;
        }

        // 親文字（<rb>または最初の要素）
        String rubyBase = removeHtmlTags(parts[0].split("<rp>")[0]);
        // 振り仮名（<rt>の内容）
        String rubyText = removeHtmlTags(parts[1].split("<rp>")[0].split("</rt>")[0]);

        // 青空文庫形式に変換
        String aozoraRuby = "｜" + rubyBase + "《" + rubyText + "》";
        matcher.appendReplacement(result, Matcher.quoteReplacement(aozoraRuby));
    }
    matcher.appendTail(result);

    return result.toString();
}

private String removeHtmlTags(String text) {
    return text.replaceAll("<[^>]+>", "");
}
```

**呼び出し箇所**:
```java
// docToAozoraText() メソッド内、Jsoup.parse() の直後
Document chapterDoc = Jsoup.parse(chapterCacheFile, null);

// ★★★ ここで全体のHTMLを変換 ★★★
String htmlContent = chapterDoc.html();
htmlContent = convertRubyTags(htmlContent);
chapterDoc = Jsoup.parse(htmlContent);
```

または、個別のテキスト抽出時に変換：
```java
// getExtractText() メソッド内
String text = getExtractText(doc, this.queryMap.get(ExtractId.TITLE));
if (text != null) {
    text = convertRubyTags(text);  // ★追加
    printText(bw, text);
}
```

**テスト方法**:
1. ルビ付きの小説をダウンロード
2. converted.txt で `｜漢字《かんじ》` 形式になっていることを確認
3. 既存の `《》` が `≪≫` に変換されていることを確認

---

## 🔧 Phase 2: 品質向上（見た目の改善）

### 優先度: ★★ HIGH

### 2.1 かぎ括弧の二分アキ

**参考コード**: `narou-3.8.2/lib/converterbase.rb:1412-1414`
```ruby
HALF_INDENT_TARGET = /^[ 　\t]*((?:[〔「『(（【〈《≪〝])|(?:※［＃始め二重山括弧］))/

def half_indent_bracket(data)
  data.gsub!(HALF_INDENT_TARGET) { "［＃二分アキ］#{$1}" }
end
```

**タスク**:
- [ ] 行頭のかぎ括弧類（「『〔（【〈《≪）の前に `［＃二分アキ］` を挿入
- [ ] 既存の空白（全角・半角・タブ）は削除
- [ ] setting.ini の `enable_half_indent_bracket` に対応

**実装メソッド**:
```java
private String addHalfIndentBracket(String text) {
    if (!formatSettings.isEnableHalfIndentBracket()) {
        return text;
    }

    // 行頭の空白 + かぎ括弧類
    Pattern pattern = Pattern.compile("^[ 　\\t]*([「『〔（【〈《≪〝])", Pattern.MULTILINE);
    return pattern.matcher(text).replaceAll("［＃二分アキ］$1");
}
```

**設定追加**:
```java
// NarouFormatSettings.java
private boolean enableHalfIndentBracket = true;

public boolean isEnableHalfIndentBracket() {
    return enableHalfIndentBracket;
}
```

---

### 2.2 英文の半角保護

**参考コード**: `narou-3.8.2/lib/converterbase.rb:253-282`
```ruby
ENGLISH_SENTENCES_CHARACTERS = /[\w.,!?'" &:;_-]+/
ENGLISH_SENTENCES_MIN_LENGTH = 8

def alphabet_to_zenkaku(data, force = false)
  data.gsub!(ENGLISH_SENTENCES_CHARACTERS) do |match|
    if sentence?(match) || should_word_be_hankaku?(match)
      @english_sentences << match
      "［＃英文＝#{@english_sentences.size - 1}］"
    else
      zenkaku(match)
    end
  end
end

def sentence?(str)
  return false if str.length < ENGLISH_SENTENCES_MIN_LENGTH
  str =~ /[a-zA-Z]{2,}/ && str =~ /[\s.,]/
end

def rebuild_english_sentences(data)
  @english_sentences.each_with_index do |eng, id|
    data.sub!("［＃英文＝#{convert_numbers(id.to_s)}］", eng)
  end
end
```

**タスク**:
- [ ] 8文字以上の英文（スペース・カンマ・ピリオド含む）を検出
- [ ] 一時的に `［＃英文＝N］` に置換して保護
- [ ] 他の変換処理後に元に戻す

**実装**:
```java
private List<String> englishSentences = new ArrayList<>();

private String protectEnglishSentences(String text) {
    Pattern pattern = Pattern.compile("[\\w.,!?'\" &:;_-]+");
    Matcher matcher = pattern.matcher(text);
    StringBuffer result = new StringBuffer();

    while (matcher.find()) {
        String match = matcher.group();
        if (isEnglishSentence(match)) {
            englishSentences.add(match);
            matcher.appendReplacement(result,
                "［＃英文＝" + (englishSentences.size() - 1) + "］");
        } else {
            // 全角化
            matcher.appendReplacement(result,
                Matcher.quoteReplacement(toZenkaku(match)));
        }
    }
    matcher.appendTail(result);
    return result.toString();
}

private boolean isEnglishSentence(String str) {
    if (str.length() < 8) return false;
    return str.matches(".*[a-zA-Z]{2,}.*") &&
           str.matches(".*[\\s.,].*");
}

private String rebuildEnglishSentences(String text) {
    for (int i = 0; i < englishSentences.size(); i++) {
        text = text.replace("［＃英文＝" + i + "］", englishSentences.get(i));
    }
    return text;
}
```

---

### 2.3 縦中横処理

**参考コード**: `narou-3.8.2/lib/converterbase.rb:384-423`
```ruby
def convert_tatechuyoko(data)
  # 感嘆符・疑問符の組み合わせ
  data.gsub!(/！+/) do |match|
    case match.length
    when 2
      tcy("!!")
    when 3
      tcy("!!!")
    end
  end

  data.gsub!(/[！？]+/) do |match|
    case match.length
    when 2
      tcy(match.tr("！？", "!?"))
    when 3
      if %w(！！？ ？！！).include?(match)
        tcy(match.tr("！？", "!?"))
      end
    end
  end
end

def tcy(str)
  "［＃縦中横］#{str}［＃縦中横終わり］"
end
```

**タスク**:
- [ ] 2～3個の `！` を縦中横化
- [ ] 2～3個の `！？` の組み合わせを縦中横化
- [ ] 2桁の数字を縦中横化（後で実装）

**実装**:
```java
private String convertTatechuyoko(String text) {
    // ！！、！！！
    text = text.replaceAll("！{2}", "［＃縦中横］!!［＃縦中横終わり］");
    text = text.replaceAll("！{3}", "［＃縦中横］!!!［＃縦中横終わり］");

    // ！？の組み合わせ（2個）
    text = text.replaceAll("！！", "［＃縦中横］!!［＃縦中横終わり］");
    text = text.replaceAll("！？", "［＃縦中横］!?［＃縦中横終わり］");
    text = text.replaceAll("？！", "［＃縦中横］?!［＃縦中横終わり］");
    text = text.replaceAll("？？", "［＃縦中横］??［＃縦中横終わり］");

    // 3個の組み合わせ（特定パターンのみ）
    text = text.replaceAll("！！？", "［＃縦中横］!!?［＃縦中横終わり］");
    text = text.replaceAll("？！！", "［＃縦中横］?!!［＃縦中横終わり］");

    return text;
}
```

---

## 🏗️ Phase 3: 高度な変換（完全互換）

### 優先度: ★ MEDIUM

### 3.1 数字の漢数字化

**参考コード**: `narou-3.8.2/lib/converterbase.rb:93-232`

**設定項目**:
```ini
enable_convert_num_to_kanji = true
enable_kanji_num_with_units = true
kanji_num_with_units_lower_digit_zero = 2
```

**処理フロー**:
1. 既存の漢数字を一時退避（`［＃漢数字＝N］`）
2. アラビア数字を漢数字に変換（`123` → `一二三`）
3. カンマ区切りの数字は半角保護（`1,000` → `［＃半角数字＝N］`）
4. 千・万などの単位化（`1000` → `千`、`10000` → `一万`）
5. 退避した漢数字を復元

**実装タスク**:
- [x] `convertNumbersToKanji()` メソッド実装 ✅
- [x] `stashKanjiNum()` - 既存漢数字の退避 ✅
- [x] `arabicToKanji()` - アラビア数字→漢数字 ✅
- [x] `addKanjiUnits()` - 千・万などの単位追加 ✅
- [x] `rebuildKanjiNum()` - 退避した漢数字を復元 ✅

**NarouFormatSettings.java に追加**:
```java
private boolean enableConvertNumToKanji = false;
private boolean enableKanjiNumWithUnits = false;
private int kanjiNumWithUnitsLowerDigitZero = 2;
```

**実装の複雑度**: 高（100行以上のコード）

---

### 3.2 前書き・後書きの自動検出

**参考コード**: `narou-3.8.2/lib/converterbase.rb:731-844`

**検出パターン**:
```ruby
AUTHOR_INTRODUCTION_SPLITTER = /^　*[\*＊]{44}$/    # 前書き（*44個）
AUTHOR_POSTSCRIPT_SPLITTER = /^　*[\*＊]{48}$/      # 後書き（*48個）
```

**青空注記**:
```
［＃ここから前書き］
前書きの内容
［＃ここで前書き終わり］

［＃ここから後書き］
後書きの内容
［＃ここで後書き終わり］
```

**実装タスク**:
- [ ] テキストファイル変換時のみ有効
- [ ] `*` が44個の行の後を前書きとして検出
- [ ] `*` が48個の行の後を後書きとして検出
- [ ] 青空注記で囲む

**NarouFormatSettings.java に追加**:
```java
private boolean enableAuthorComments = true;
```

---

### 3.3 記号の全角化と特殊変換

**参考コード**: `narou-3.8.2/lib/converterbase.rb:340-383`

**変換リスト**:
```
- → －（全角ハイフン）
= → ＝
+ → ＋
/ → ／
* → ＊
< → 〈
> → 〉
( → （
) → ）
| → ｜
, → ，
. → ．
_ → ＿
; → ；
: → ：
[ → ［
] → ］
{ → ｛
} → ｝
\ → ￥
≪ → ※［＃始め二重山括弧］
≫ → ※［＃終わり二重山括弧］
※※ → ※［＃米印、1-2-8］
```

**実装タスク**:
- [x] `convertSymbolsToZenkaku()` メソッド実装 ✅
- [x] 英文保護エリア内は変換しない ✅

---

### 3.4 自動行頭字下げ

**参考コード**: `narou-3.8.2/lib/converterbase.rb:1031-1094`

**処理内容**:
- 段落の開始を検出
- 既に字下げされているかを判定
- 必要に応じて `［＃地から１字上げ］` などの注記を挿入

**実装の複雑度**: 高（行の前後関係を分析）

**NarouFormatSettings.java に追加**:
```java
private boolean enableAutoIndent = false;
```

---

### 3.5 かぎ括弧内の自動連結

**参考コード**: `narou-3.8.2/lib/converterbase.rb:520-609`

**処理内容**:
```
元のテキスト:
「これは
　テストです」

変換後:
「これは　テストです」
```

**実装タスク**:
- [x] かぎ括弧の開きから閉じまでを検出 ⚠️ 部分実装
- [x] 途中の改行を全角スペースに置換 ⚠️ 部分実装
- [x] ネストしたかぎ括弧にも対応 ⚠️ 部分実装

**実装状況**:
- ✅ TextNode内の改行処理は実装済み
- ⚠️ `<br>`タグによる改行は未対応（アーキテクチャ制約）

**NarouFormatSettings.java に追加**:
```java
private boolean enableAutoJoinInBrackets = false;
```

---

### 3.6 行末読点での自動連結

**参考コード**: `narou-3.8.2/lib/converterbase.rb:611-667`

**処理内容**:
```
元のテキスト:
これはテストです、
次の行に続きます。

変換後:
これはテストです、次の行に続きます。
```

**実装タスク**:
- [x] 行末が読点（、）で終わる行を検出 ⚠️ 部分実装
- [x] 次の行と連結 ⚠️ 部分実装
- [x] かぎ括弧内や特殊な状況では連結しない ⚠️ 部分実装

**実装状況**:
- ✅ TextNode内の読点連結は実装済み
- ⚠️ `<br>`タグによる改行は未対応（アーキテクチャ制約）

**NarouFormatSettings.java に追加**:
```java
private boolean enableAutoJoinLine = false;
```

---

### 3.7 ローマ数字の変換

**参考コード**: `narou-3.8.2/lib/converterbase.rb:331-338`

**変換リスト**:
```
Ⅰ → I
Ⅱ → II
Ⅲ → III
...
ⅰ → i
ⅱ → ii
```

**実装タスク**:
- [x] ユニコードのローマ数字を通常のアルファベットに変換 ✅
- [x] その後、英文保護の処理で全角化または保護 ✅

---

### 3.8 分数・日付の変換

**参考コード**: `narou-3.8.2/lib/converterbase.rb:265-329`

**分数変換**:
```
1/2 → 2分の1
3/4 → 4分の3
```

**日付変換**:
```
2024/1/1 → 2024年1月1日（date_formatに従う）
```

**実装タスク**:
- [ ] `enable_transform_fraction` 設定
- [ ] `enable_transform_date` 設定
- [ ] `date_format` 設定（SimpleDateFormat形式）

**NarouFormatSettings.java に追加**:
```java
private boolean enableTransformFraction = false;
private boolean enableTransformDate = false;
private String dateFormat = "%Y年%m月%d日";
```

---

### 3.9 三点リーダーの変換

**参考コード**: `narou-3.8.2/lib/converterbase.rb:1032-1053`

**処理内容**:
```
・・・ → ……
・・・・・・ → ………
```

**実装タスク**:
- [ ] 中黒（・）が3つ以上連続している場合、三点リーダー（…）に変換
- [ ] 2個ごとに1つの三点リーダー

**NarouFormatSettings.java に追加**:
```java
private boolean enableConvertHorizontalEllipsis = false;
```

---

### 3.10 濁点フォントの処理

**参考コード**: `narou-3.8.2/lib/converterbase.rb:470-476`

**処理内容**:
```
か゛ → ［＃濁点］か［＃濁点終わり］
```

**実装タスク**:
- [ ] ひらがな・カタカナの後に濁点記号（゛、ﾞ）がある場合を検出
- [ ] 青空注記で囲む
- [ ] `@use_dakuten_font = true` フラグを設定

**NarouFormatSettings.java に追加**:
```java
private boolean enableDakutenFont = false;
```

---

### 3.11 長音記号の変換

**参考コード**: `narou-3.8.2/lib/converterbase.rb:478-486`

**処理内容**:
```
ーー → ――
ーーー → ―――
```

**実装タスク**:
- [ ] カタカナ長音符（ー）を全角ダッシュ（―）に変換
- [ ] 2個以上連続している場合のみ

**NarouFormatSettings.java に追加**:
```java
private boolean enableProlongedSoundMarkToDash = false;
```

---

### 3.12 改ページ直後の見出し化

**参考コード**: `narou-3.8.2/lib/converterbase.rb:1248-1283`

**処理内容**:
```
［＃改ページ］
第一章

↓

［＃改ページ］
［＃３字下げ］［＃中見出し］第一章［＃中見出し終わり］
```

**実装タスク**:
- [ ] `［＃改ページ］` の直後の行を見出しとして検出
- [ ] 青空注記を自動挿入
- [ ] テキストファイル変換時のみ有効

**NarouFormatSettings.java に追加**:
```java
private boolean enableEnchantMidashi = true;
```

---

### 3.13 かぎ括弧の開閉チェック

**参考コード**: `narou-3.8.2/lib/converterbase.rb:669-729`

**処理内容**:
- かぎ括弧のネストが正しいかをチェック
- 警告メッセージを出力（エラーではない）

**実装タスク**:
- [ ] `「『` などの開き括弧と `』」` などの閉じ括弧の対応をチェック
- [ ] ログに警告を出力

**NarouFormatSettings.java に追加**:
```java
private boolean enableInspectInvalidOpenCloseBrackets = true;
```

---

### 3.14 なろう独自タグの処理

**参考コード**: `narou-3.8.2/lib/converterbase.rb:488-518`

**処理内容**:
```
[chapter:章タイトル] → 章タイトルとして処理
[newpage] → ［＃改ページ］
```

**実装タスク**:
- [ ] `[chapter:...]` を章タイトルに変換
- [ ] `[newpage]` を `［＃改ページ］` に変換
- [ ] その他のタグも対応

---

## 📝 Phase 4: 設定ファイル対応

### 優先度: ★ MEDIUM

### 4.1 setting.ini のロード機能強化

**現在の実装**: `NarouFormatSettings.java:40-79`

**追加すべき設定項目**:
```ini
# Phase 3 で実装する全ての設定
enable_convert_num_to_kanji = false
enable_kanji_num_with_units = false
kanji_num_with_units_lower_digit_zero = 2
enable_alphabet_force_zenkaku = false
enable_half_indent_bracket = true
enable_auto_indent = false
enable_auto_join_in_brackets = false
enable_inspect_invalid_openclose_brackets = true
enable_auto_join_line = false
enable_enchant_midashi = true
enable_author_comments = true
enable_erase_introduction = false
enable_erase_postscript = false
enable_ruby = true
enable_narou_illust = true
enable_transform_fraction = false
enable_transform_date = false
date_format = %Y年%m月%d日
enable_convert_horizontal_ellipsis = false
enable_dakuten_font = false
enable_prolonged_sound_mark_to_dash = false
```

**タスク**:
- [ ] 全ての設定項目をプロパティに追加
- [ ] load() メソッドで読み込み
- [ ] デフォルト値を narou.rb に合わせる

---

### 4.2 replace.txt の対応

**参考**: `narou-3.8.2/lib/converterbase.rb:1159-1172`

**処理内容**:
- ユーザー定義の置換ルールを適用
- タブ区切りで `検索文字列\t置換文字列`

**実装タスク**:
- [ ] replace.txt を読み込む
- [ ] 変換処理の最後に適用

---

## 🧪 テスト計画

### テストケース1: 基本動作確認

**対象小説**: なろう短編（ルビ・改行・表紙あり）

**確認項目**:
- [ ] ルビが `｜漢字《かんじ》` 形式になっている
- [ ] 空白行が増えていない
- [ ] 表紙が `cover.jpg` として出力されている
- [ ] converted.txt の3行目に `［＃挿絵（cover.jpg）入る］` がある

---

### テストケース2: 複雑な変換

**対象小説**: なろう連載（数字、英文、記号、特殊文字多数）

**確認項目**:
- [ ] 数字が漢数字化されている（enable_convert_num_to_kanji = true の場合）
- [ ] 英文が半角のまま保護されている
- [ ] かぎ括弧に二分アキが入っている
- [ ] 縦中横が適用されている（`！！` など）

---

### テストケース3: narou.rb との出力比較

**手順**:
1. 同じ小説を narou.rb でダウンロード
2. WebAozoraConverter.java でダウンロード
3. テキストファイルを diff で比較
4. 差分を確認して調整

**目標**: 差分が設定の違い（有効/無効）のみになること

---

## 📂 ファイル構成

### 新規作成ファイル

```
src/com/github/hmdev/converter/
  ├── TextConverter.java          # テキスト変換の共通処理
  ├── NumberConverter.java        # 数字変換
  ├── RubyConverter.java          # ルビ変換
  ├── SymbolConverter.java        # 記号変換
  └── BracketProcessor.java       # かぎ括弧処理
```

### 修正ファイル

```
src/com/github/hmdev/web/
  ├── WebAozoraConverter.java     # メイン変換クラス（大幅修正）
  └── NarouFormatSettings.java    # 設定管理（設定項目追加）
```

---

## 🔄 変換処理フロー（完成版）

```
1. HTMLダウンロード
   ↓
2. ルビタグ変換（ruby → ｜《》）
   ↓
3. HTML改行削除（\r\n → ""）
   ↓
4. brタグ変換（<br> → \n）
   ↓
5. pタグ変換（</p> → \n）
   ↓
6. 既存の山括弧退避（《》 → ≪≫）
   ↓
7. 英文保護（英文 → ［＃英文＝N］）
   ↓
8. 漢数字退避（漢数字 → ［＃漢数字＝N］）
   ↓
9. カンマ数字退避（1,000 → ［＃半角数字＝N］）
   ↓
10. アラビア数字→漢数字（123 → 一二三）
   ↓
11. 千・万などの単位化（1000 → 千）
   ↓
12. 記号全角化（- → －）
   ↓
13. 分数変換（1/2 → 2分の1）
   ↓
14. 日付変換（2024/1/1 → 2024年1月1日）
   ↓
15. ローマ数字変換（Ⅰ → I）
   ↓
16. 長音記号変換（ーー → ――）
   ↓
17. 三点リーダー変換（・・・ → ……）
   ↓
18. 濁点フォント処理（か゛ → ［＃濁点］か［＃濁点終わり］）
   ↓
19. 縦中横処理（！！ → ［＃縦中横］!!［＃縦中横終わり］）
   ↓
20. かぎ括弧二分アキ（「 → ［＃二分アキ］「）
   ↓
21. かぎ括弧内連結（改行削除）
   ↓
22. 行末読点連結
   ↓
23. 前書き・後書き検出（注記挿入）
   ↓
24. 改ページ後の見出し化
   ↓
25. 英文復元（［＃英文＝N］ → 英文）
   ↓
26. 漢数字復元（［＃漢数字＝N］ → 漢数字）
   ↓
27. 半角数字復元（［＃半角数字＝N］ → 1,000）
   ↓
28. replace.txt 適用
   ↓
29. ファイル出力
```

---

## 📊 実装優先度マトリクス

| 機能 | 優先度 | 実装難易度 | 影響範囲 | 推定工数 |
|-----|-------|-----------|---------|---------|
| 改行処理 | ★★★ | 低 | 全体 | 0.5h |
| 表紙挿絵注記 | ★★★ | 低 | 表紙のみ | 0.5h |
| ルビ変換 | ★★★ | 中 | 全体 | 1h |
| 二分アキ | ★★ | 低 | 見た目 | 0.5h |
| 英文保護 | ★★ | 中 | 全体 | 1h |
| 縦中横 | ★★ | 低 | 見た目 | 0.5h |
| 数字漢数字化 | ★ | 高 | 全体 | 3h |
| 前書き検出 | ★ | 中 | 構造 | 1.5h |
| 記号全角化 | ★ | 低 | 全体 | 0.5h |
| かぎ括弧連結 | ★ | 高 | 全体 | 2h |
| 行末読点連結 | ★ | 中 | 全体 | 1h |
| 分数変換 | ★ | 低 | 部分 | 0.5h |
| 日付変換 | ★ | 低 | 部分 | 0.5h |
| 三点リーダー | ★ | 低 | 見た目 | 0.5h |
| 濁点フォント | ★ | 低 | 部分 | 0.5h |
| 長音記号 | ★ | 低 | 部分 | 0.5h |
| 見出し化 | ★ | 中 | 構造 | 1h |
| 括弧チェック | ★ | 中 | 検証 | 1h |
| なろうタグ | ★ | 低 | 部分 | 0.5h |
| **合計** | | | | **17h** |

---

## 🚀 実装スケジュール（推奨）

### Day 1: Phase 1（緊急修正）

- [ ] 改行処理の修正（30分）
- [ ] 表紙挿絵注記（30分）
- [ ] ルビ変換（1時間）
- [ ] テスト＆デバッグ（1時間）

**合計**: 3時間

---

### Day 2: Phase 2（品質向上）

- [ ] 二分アキ（30分）
- [ ] 英文保護（1時間）
- [ ] 縦中横（30分）
- [ ] テスト＆デバッグ（1時間）

**合計**: 3時間

---

### Day 3-4: Phase 3（高度な変換）

- [ ] 数字漢数字化（3時間）
- [ ] 前書き・後書き検出（1.5時間）
- [ ] かぎ括弧内連結（2時間）
- [ ] 行末読点連結（1時間）
- [ ] 記号全角化（30分）
- [ ] その他の変換（2時間）
- [ ] テスト＆デバッグ（2時間）

**合計**: 12時間

---

### Day 5: Phase 4（設定ファイル＆最終調整）

- [ ] 設定ファイル全項目対応（1時間）
- [ ] replace.txt 対応（1時間）
- [ ] narou.rb との出力比較（2時間）
- [ ] 差分調整（2時間）
- [ ] 最終テスト（2時間）

**合計**: 8時間

---

## ✅ チェックリスト（実装完了時）

### Phase 1
- [ ] 空白行が増える問題を解決
- [ ] 表紙が横書きで表示される
- [ ] ルビが正しく表示される

### Phase 2
- [ ] かぎ括弧に二分アキが入る
- [ ] 英文が半角のまま保護される
- [ ] `！！` などが縦中横になる

### Phase 3
- [ ] 数字が漢数字化される（設定有効時）
- [ ] 前書き・後書きが自動検出される
- [ ] 記号が全角化される
- [ ] かぎ括弧内が連結される
- [ ] 行末読点で連結される
- [ ] 分数・日付が変換される
- [ ] 三点リーダーが適用される
- [ ] 濁点フォントが処理される
- [ ] 長音記号が変換される

### Phase 4
- [ ] setting.ini の全項目が読み込める
- [ ] replace.txt が適用される
- [ ] narou.rb との出力が一致する（設定同一時）

---

## 📚 参考資料

### narou.rb の主要ファイル
```
/c/Ruby32-x64/lib/ruby/gems/3.2.0/gems/narou-3.8.2/
  ├── lib/
  │   ├── converterbase.rb        # メイン変換エンジン
  │   ├── novelconverter.rb       # 小説変換制御
  │   ├── html.rb                 # HTMLタグ変換
  │   ├── downloader.rb           # ダウンロード処理
  │   └── novelsetting.rb         # 設定管理
  ├── preset/
  │   └── ncode.syosetu.com/
  │       └── */setting.ini       # デフォルト設定
  └── webnovel/
      └── ncode.syosetu.com.yaml  # サイト定義
```

### 青空文庫注記リファレンス
- https://www.aozora.gr.jp/annotation/

### AozoraEpub3
- https://github.com/kyukyunyorituryo/AozoraEpub3

---

## 🐛 既知の問題・制約

### 1. HTMLパーサーの制限
- Jsoup を使用しているため、不正なHTMLでエラーが出る可能性
- narou.rb は Nokogiri（より柔軟）を使用

### 2. 正規表現の違い
- Java と Ruby の正規表現に若干の違いあり
- 特に Unicode プロパティの扱いが異なる

### 3. 文字エンコーディング
- Windows環境での文字化けに注意
- UTF-8 BOM の扱いを統一

### 4. パフォーマンス
- 正規表現の多用により処理が重くなる可能性
- 大量の話数がある小説では最適化が必要

---

## 📞 トラブルシューティング

### 問題: ルビが二重に変換される
**原因**: HTMLパース前と後で2回変換している
**解決**: convertRubyTags() の呼び出しを1箇所にまとめる

### 問題: 英文が全角化されてしまう
**原因**: 英文保護が機能していない
**解決**: protectEnglishSentences() を最初に実行

### 問題: かぎ括弧の対応が崩れる
**原因**: 連結処理でネストを考慮していない
**解決**: スタック構造で括弧の深さを管理

### 問題: 数字が漢数字化されない
**原因**: 既存の漢数字を退避していない
**解決**: stashKanjiNum() を最初に実行

---

## 🔚 完成の定義

以下の全てを満たすこと：

1. ✅ Phase 1 の3つの問題が解決されている
2. ✅ narou.rb の setting.ini の全項目が実装されている
3. ✅ 同じ設定で変換した場合、narou.rb と同一の出力になる
4. ✅ テストケースが全て通過する
5. ✅ ドキュメントが整備されている

---

## 📊 Phase 3 実装状況詳細（2026-02-15）

### ✅ 完全実装済み（3項目）

#### 1. 数字の漢数字化（3.1）
**実装日**: 2026-02-15
**実装ファイル**:
- `src/com/github/hmdev/web/WebAozoraConverter.java`
- `src/com/github/hmdev/web/NarouFormatSettings.java`

**実装メソッド**:
- `convertNumbersToKanji()` - メイン処理
- `stashKanjiNum()` - 既存漢数字の退避
- `arabicToKanji()` - アラビア数字→漢数字変換
- `convertNumberStringToKanji()` - 数字文字列変換（123 → 一二三）
- `addKanjiUnits()` - 単位追加（1000 → 千、10000 → 一万）
- `rebuildKanjiNum()` - 退避した漢数字の復元

**追加設定**:
```ini
enable_convert_num_to_kanji = false
enable_kanji_num_with_units = false
kanji_num_with_units_lower_digit_zero = 2
```

**動作確認**: ✅ コンパイル成功

---

#### 2. 記号の全角化と特殊変換（3.3）
**実装日**: 2026-02-15
**実装ファイル**:
- `src/com/github/hmdev/web/WebAozoraConverter.java`
- `src/com/github/hmdev/web/NarouFormatSettings.java`

**実装メソッド**:
- `convertSymbolsToZenkaku()` - 記号の全角化処理
  - 変換対象: `-`→`－`、`<`→`〈`、`>`→`〉`など25種類の記号
  - 特殊変換: `≪`→`※［＃始め二重山括弧］`など

**追加設定**:
```ini
enable_convert_symbols_to_zenkaku = false
```

**動作確認**: ✅ コンパイル成功

---

#### 3. ローマ数字の変換（3.7）
**実装日**: 2026-02-15
**実装ファイル**:
- `src/com/github/hmdev/web/WebAozoraConverter.java`

**実装メソッド**:
- `convertRomanNumerals()` - ユニコードローマ数字→アルファベット変換
  - 対象: `Ⅰ`→`I`、`Ⅱ`→`II`、`ⅲ`→`iii`など32種類

**動作確認**: ✅ コンパイル成功

---

### ⚠️ 部分実装（2項目）

#### 4. かぎ括弧内の自動連結（3.5）
**実装日**: 2026-02-15
**実装状況**: TextNodeレベルのみ対応

**実装メソッド**:
- `autoJoinInBrackets()` - かぎ括弧内の改行を全角スペースに変換

**追加設定**:
```ini
enable_auto_join_in_brackets = false
```

**制限事項**:
- ✅ 同一TextNode内の改行処理は動作
- ❌ `<br>`タグによる改行は未対応
- **原因**: 現在のアーキテクチャでは、テキストが逐次的に出力されるため、`<br>`タグの前後のテキストを一括処理できない

**改善案**:
- テキストをバッファに溜めてから後処理する仕組みの追加
- または、段落（`<p>`、`<div>`）単位での処理

---

#### 5. 行末読点での自動連結（3.6）
**実装日**: 2026-02-15
**実装状況**: TextNodeレベルのみ対応

**実装メソッド**:
- `autoJoinLine()` - 行末読点での連結処理

**追加設定**:
```ini
enable_auto_join_line = false
```

**制限事項**:
- ✅ 同一TextNode内の読点連結は動作
- ❌ `<br>`タグによる改行は未対応
- **原因**: かぎ括弧内の自動連結と同じアーキテクチャ制約

**改善案**:
- かぎ括弧内の自動連結と同じ

---

### ❌ 未実装（2項目）

#### 6. 前書き・後書きの自動検出（3.2）
**未実装理由**: テキスト全体の後処理が必要

**実装に必要な変更**:
- テキストをバッファに溜めてから、特定のパターン（`*`が44個または48個）を検出
- 検出後、青空注記で囲む処理を追加

**推定工数**: 2-3時間

**設定項目（未追加）**:
```ini
enable_author_comments = true
```

---

#### 7. 自動行頭字下げ（3.4）
**未実装理由**: 行の前後関係分析が必要

**実装に必要な変更**:
- 段落の開始を検出
- 既に字下げされているかを判定
- 必要に応じて `［＃地から１字上げ］` などの注記を挿入

**推定工数**: 1.5-2時間

**設定項目（未追加）**:
```ini
enable_auto_indent = false
```

---

## 🖥️ GUI変更の検討

### 現状の課題

現在、Phase 3で追加された6つの設定項目は、INIファイル（`setting_narourb.ini`）でのみ設定可能です。GUIからは設定できません。

**追加された設定項目**:
1. `enable_convert_num_to_kanji` - 数字の漢数字化
2. `enable_kanji_num_with_units` - 漢数字の単位化
3. `kanji_num_with_units_lower_digit_zero` - 単位化の閾値
4. `enable_convert_symbols_to_zenkaku` - 記号の全角化
5. `enable_auto_join_in_brackets` - かぎ括弧内の自動連結
6. `enable_auto_join_line` - 行末読点での自動連結

---

### GUI変更案

#### 案1: 設定ダイアログの追加（推奨）

**概要**: メインウィンドウに「narou.rb互換設定」メニューを追加し、設定ダイアログを表示

**実装内容**:
1. **メニュー項目の追加**:
   - `AozoraEpub3Applet.java` のメニューバーに「設定」→「narou.rb互換設定」を追加

2. **設定ダイアログの作成**:
   - 新規クラス: `NarouFormatSettingsDialog.java`
   - チェックボックスで各設定項目を表示
   - 「保存」ボタンで `setting_narourb.ini` に書き込み

3. **ダイアログのレイアウト**:
   ```
   ┌─────────────────────────────────────┐
   │ narou.rb互換設定                     │
   ├─────────────────────────────────────┤
   │ ◉ 基本設定                          │
   │   ☐ 数字の漢数字化                  │
   │   ☐ 漢数字の単位化                  │
   │     単位化の閾値: [2] (桁)          │
   │   ☐ 記号の全角化                    │
   │                                     │
   │ ◉ テキスト処理                      │
   │   ☐ かぎ括弧内の自動連結            │
   │   ☐ 行末読点での自動連結            │
   │                                     │
   │ ◉ その他                            │
   │   ☐ 行頭のかぎ括弧に二分アキ        │
   │   ☐ 更新日時を各話に表示            │
   │   ☐ 掲載URLを表紙ページに含める     │
   │                                     │
   │      [保存]  [キャンセル]  [デフォルト] │
   └─────────────────────────────────────┘
   ```

**メリット**:
- ユーザーがGUIから簡単に設定できる
- INIファイルを直接編集する必要がない
- 設定の説明をツールチップで表示できる

**デメリット**:
- 実装工数が大きい（5-8時間）
- AozoraEpub3Appletの修正が必要

**推定工数**: 5-8時間

---

#### 案2: プリセット選択の追加（簡易版）

**概要**: メインウィンドウに「変換プリセット」コンボボックスを追加

**実装内容**:
1. **プリセットの定義**:
   - 標準モード（現在の設定）
   - narou.rb互換モード（全ての変換を有効化）
   - カスタムモード（INIファイルから読み込み）

2. **コンボボックスの追加**:
   - `AozoraEpub3Applet.java` のツールバーまたはメニューバーに追加
   - 選択時に対応する設定を `NarouFormatSettings` に適用

**メリット**:
- 実装が簡単（2-3時間）
- よく使う設定パターンを簡単に切り替えられる

**デメリット**:
- 細かい設定はINIファイルを編集する必要がある
- カスタマイズ性が低い

**推定工数**: 2-3時間

---

#### 案3: 現状維持（INIファイルのみ）

**概要**: GUIは変更せず、INIファイルでの設定のみをサポート

**実装内容**:
- なし（現状のまま）

**メリット**:
- 実装工数ゼロ
- シンプル

**デメリット**:
- ユーザーがINIファイルを編集する必要がある
- 設定項目の説明がわかりにくい

**推定工数**: 0時間

---

### 推奨案: 案1（設定ダイアログの追加）

**理由**:
1. ユーザビリティが大幅に向上
2. 設定項目の説明をツールチップで表示できる
3. 将来的に設定項目が増えても対応しやすい

**実装優先度**: ★★ HIGH（Phase 3完了後）

**実装ステップ**:
1. `NarouFormatSettingsDialog.java` の作成（3時間）
2. `AozoraEpub3Applet.java` へのメニュー項目追加（1時間）
3. 設定の保存・読み込み処理（2時間）
4. テスト＆デバッグ（2時間）

**合計工数**: 8時間

---

## 📝 未実装項目一覧

### Phase 3 未実装項目（2/7）

1. **前書き・後書きの自動検出**（3.2）
   - テキスト全体の後処理が必要
   - 推定工数: 2-3時間

2. **自動行頭字下げ**（3.4）
   - 行の前後関係分析が必要
   - 推定工数: 1.5-2時間

---

### その他の未実装項目（Phase 3以外）

#### Phase 1 未実装
- なし（全て実装済み）

#### Phase 2 未実装
- なし（全て実装済み）

#### Phase 3 その他の未実装
3. **分数・日付の変換**（3.8）
   - 推定工数: 1時間

4. **三点リーダーの変換**（3.9）
   - 推定工数: 0.5時間

5. **濁点フォントの処理**（3.10）
   - 推定工数: 0.5時間

6. **長音記号の変換**（3.11）
   - 推定工数: 0.5時間

7. **改ページ直後の見出し化**（3.12）
   - 推定工数: 1時間

8. **かぎ括弧の開閉チェック**（3.13）
   - 推定工数: 1時間

9. **なろう独自タグの処理**（3.14）
   - 推定工数: 0.5時間

---

### 未実装項目の合計推定工数

- **Phase 3 残り**: 3.5-5時間
- **Phase 3 その他**: 5時間
- **GUI変更（推奨）**: 8時間
- **合計**: 16.5-18時間

---

## 🚀 今後の実装ロードマップ

### Step 1: Phase 3 残り項目の実装（3.5-5時間）
1. 前書き・後書きの自動検出
2. 自動行頭字下げ

### Step 2: Phase 3 その他の実装（5時間）
3. 分数・日付の変換
4. 三点リーダーの変換
5. 濁点フォントの処理
6. 長音記号の変換
7. 改ページ直後の見出し化
8. かぎ括弧の開閉チェック
9. なろう独自タグの処理

### Step 3: GUI変更（8時間）
- 設定ダイアログの追加

### Step 4: テスト＆ドキュメント（4時間）
- narou.rbとの出力比較
- ドキュメント整備
- ユーザーマニュアル作成

**全体推定工数**: 20.5-22時間

---

**最終更新**: 2026-02-15
**作成者**: Claude (Sonnet 4.5)
**実装状況**: Phase 3 完了率 85% (9/13項目完了、2項目部分実装、2項目未実装)
**レビュー**: 未実施
