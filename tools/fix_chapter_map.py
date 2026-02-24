#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Phase 2-1: __NEXT_DATA__ 章構造マッピング対応を実装するスクリプト"""

import sys

filepath = 'src/com/github/hmdev/web/WebAozoraConverter.java'

with open(filepath, 'rb') as f:
    content = f.read()

CRLF = b'\r\n'

# ============================================================
# Step 1: フィールド追加 (bookTitle フィールドの後に追加)
# ============================================================
field_anchor = b'private String bookTitle = null;'
idx_field = content.find(field_anchor)
if idx_field < 0:
    print("ERROR: field anchor not found")
    sys.exit(1)

insert_field_pos = idx_field + len(field_anchor) + 2  # after the line ending \r\n

new_field = (
    b'\t/** __NEXT_DATA__ JSON \xe3\x81\x8b\xe3\x82\x89\xe6\xa7\x8b\xe7\xaf\x89\xe3\x81\x97\xe3\x81\x9f\xe3\x82\xa8\xe3\x83\x94\xe3\x82\xbd\xe3\x83\xbc\xe3\x83\x89URL\xe2\x86\x92\xe7\xab\xa0\xe3\x82\xbf\xe3\x82\xa4\xe3\x83\x88\xe3\x83\xab\xe3\x83\x9e\xe3\x83\x83\xe3\x83\x97 (\xe3\x82\xab\xe3\x82\xaf\xe3\x83\xa8\xe3\x83\xa0Phase 2-1) */' + CRLF +
    b'\tprivate HashMap<String, String> nextDataEpisodeChapterMap = null;' + CRLF
)

# Insert after bookTitle field (find the CRLF after the line)
end_of_bookTitle_line = idx_field + len(field_anchor)
# Find the CRLF
if content[end_of_bookTitle_line:end_of_bookTitle_line+2] == b'\r\n':
    end_of_bookTitle_line += 2
elif content[end_of_bookTitle_line] == ord('\n'):
    end_of_bookTitle_line += 1

content = content[:end_of_bookTitle_line] + new_field + content[end_of_bookTitle_line:]
print(f"Step 1: field added at {end_of_bookTitle_line}")

# ============================================================
# Step 2: extractEpisodesFromNextData() に章マップ構築を追加
# ============================================================
# Find: return result.isEmpty() ? null : result;
# and insert chapter map building before it

ep_return_line = b'return result.isEmpty() ? null : result;'
idx_return = content.find(ep_return_line, content.find(b'extractEpisodesFromNextData'))
if idx_return < 0:
    print("ERROR: return line not found in extractEpisodesFromNextData")
    sys.exit(1)

# Build the chapter map insertion code
# UTF-8 for Japanese comments
ch_map_code = (
    b'\t\t// \xe7\xab\xa0-\xe3\x82\xa8\xe3\x83\x94\xe3\x82\xbd\xe3\x83\xbc\xe3\x83\x89\xe3\x83\x9e\xe3\x83\x83\xe3\x83\x94\xe3\x83\xb3\xe3\x82\xb0\xe3\x82\x92\xe6\xa7\x8b\xe7\xaf\x89 (Phase 2-1)' + CRLF +
    b'\t\tHashMap<String, String> chapterIdMap = buildEpisodeChapterMapFromNextData(json, workId);' + CRLF +
    b'\t\tif (!chapterIdMap.isEmpty()) {' + CRLF +
    b'\t\t\tthis.nextDataEpisodeChapterMap = new HashMap<String, String>();' + CRLF +
    b'\t\t\tfor (HashMap.Entry<String, String> e : chapterIdMap.entrySet()) {' + CRLF +
    b'\t\t\t\tthis.nextDataEpisodeChapterMap.put(this.baseUri + "/works/" + workId + "/episodes/" + e.getKey(), e.getValue());' + CRLF +
    b'\t\t\t}' + CRLF +
    # UTF-8 for LogAppender message
    b'\t\t\tLogAppender.println("__NEXT_DATA__ JSON \xe3\x81\x8b\xe3\x82\x89\xe7\xab\xa0\xe3\x83\x9e\xe3\x83\x83\xe3\x83\x94\xe3\x83\xb3\xe3\x82\xb0\xe6\xa7\x8b\xe7\xaf\x89: " + this.nextDataEpisodeChapterMap.size() + "\xe3\x82\xa8\xe3\x83\x94\xe3\x82\xbd\xe3\x83\xbc\xe3\x83\x89");' + CRLF +
    b'\t\t}' + CRLF
)

# Find the start of the return line (including leading tabs)
line_start = content.rfind(b'\n', 0, idx_return) + 1  # start of the line
content = content[:line_start] + ch_map_code + content[line_start:]
print(f"Step 2: chapter map code inserted before return at {line_start}")

# ============================================================
# Step 3: エピソードループで章マップをフォールバックとして使用
# ============================================================
# Find: String chapterTitle = getExtractText(chapterDoc, this.queryMap.get(ExtractId.CONTENT_CHAPTER));
target_chapter_line = b'String chapterTitle = getExtractText(chapterDoc, this.queryMap.get(ExtractId.CONTENT_CHAPTER));'
idx_ch = content.find(target_chapter_line)
if idx_ch < 0:
    print("ERROR: chapterTitle line not found")
    sys.exit(1)

# Find end of the chapterTitle line
end_of_ch_line = idx_ch + len(target_chapter_line)
if content[end_of_ch_line:end_of_ch_line+2] == b'\r\n':
    end_of_ch_line += 2
elif content[end_of_ch_line] == ord('\n'):
    end_of_ch_line += 1

# Insert the fallback lookup after the chapterTitle line
fallback_code = (
    b'\t\t\t\t\t\t// nextDataEpisodeChapterMap \xe3\x82\x92\xe3\x83\x95\xe3\x82\xa9\xe3\x83\xbc\xe3\x83\xab\xe3\x83\x90\xe3\x83\x83\xe3\x82\xaf\xe3\x81\xa8\xe3\x81\x97\xe3\x81\xa6\xe4\xbd\xbf\xe7\x94\xa8 (Phase 2-1: \xe3\x82\xab\xe3\x82\xaf\xe3\x83\xa8\xe3\x83\xa0\xe7\xab\xa0\xe6\xa7\x8b\xe9\x80\xa0\xe5\xaf\xbe\xe5\xbf\x9c)' + CRLF +
    b'\t\t\t\t\t\tif (chapterTitle == null && this.nextDataEpisodeChapterMap != null) {' + CRLF +
    b'\t\t\t\t\t\t\tchapterTitle = this.nextDataEpisodeChapterMap.get(chapterHref);' + CRLF +
    b'\t\t\t\t\t\t}' + CRLF
)

content = content[:end_of_ch_line] + fallback_code + content[end_of_ch_line:]
print(f"Step 3: fallback lookup inserted after chapterTitle at {end_of_ch_line}")

# ============================================================
# Step 4: buildEpisodeChapterMapFromNextData() メソッドを追加
# ============================================================
# Add after extractEpisodesFromNextData() method

end_of_extract_method = content.find(b'return result.isEmpty() ? null : result;\r\n\t}')
if end_of_extract_method < 0:
    print("ERROR: end of extractEpisodesFromNextData not found")
    sys.exit(1)

end_pos = end_of_extract_method + len(b'return result.isEmpty() ? null : result;\r\n\t}') + 2  # +CRLF

# Build the new method
new_method = (
    b'\r\n\t/**' + CRLF +
    b'\t * __NEXT_DATA__ JSON \xe3\x81\x8b\xe3\x82\x89\xe7\xab\xa0-\xe3\x82\xa8\xe3\x83\x94\xe3\x82\xbd\xe3\x83\xbc\xe3\x83\x89\xe5\xaf\xbe\xe5\xbf\x9c\xe3\x83\x9e\xe3\x83\x83\xe3\x83\x97\xe3\x82\x92\xe6\xa7\x8b\xe7\xaf\x88\xe3\x81\x99\xe3\x82\x8b (Phase 2-1)' + CRLF +
    b'\t * "TableOfContentsChapter" \xe3\x82\xa8\xe3\x83\xb3\xe3\x83\x88\xe3\x83\xaa\xe3\x81\xa8\xe5\x90\x84\xe8\xa9\xb1\xe3\x81\xaeepId\xe3\x81\xae\xe5\xaf\xbe\xe5\xbf\x9c\xe3\x82\x92\xe8\xa7\xa3\xe6\x9e\x90\xe3\x81\x99\xe3\x82\x8b' + CRLF +
    b'\t * @return epId \xe2\x86\x92 chapterTitle \xe3\x83\x9e\xe3\x83\x83\xe3\x83\x97 (\xe7\xab\xa0\xe3\x81\xaa\xe3\x81\x97\xe4\xbd\x9c\xe5\x93\x81\xe3\x81\xa7\xe3\x81\xaf\xe7\xa9\xba\xe3\x83\x9e\xe3\x83\x83\xe3\x83\x97)' + CRLF +
    b'\t */' + CRLF +
    b'\tprivate HashMap<String, String> buildEpisodeChapterMapFromNextData(String json, String workId) {' + CRLF +
    b'\t\tHashMap<String, String> result = new HashMap<String, String>();' + CRLF +
    b'\t\t// "TableOfContentsChapter:ID": \xe3\x82\xa8\xe3\x83\xb3\xe3\x83\x88\xe3\x83\xaa\xe3\x82\x92\xe6\xa4\x9c\xe7\xb4\xa2' + CRLF +
    b'\t\tPattern chapterKeyPat = Pattern.compile("\\"TableOfContentsChapter:\\\\d+\\":");' + CRLF +
    b'\t\t// \xe3\x82\xad\xe3\x83\xa3\xe3\x83\x97\xe3\x83\x81\xe3\x83\xa3\xe3\x83\xbc\xe3\x82\xb0\xe3\x83\xab\xe3\x83\xbc\xe3\x83\x97\xe5\x86\x85\xe3\x81\xae\xe3\x82\xa8\xe3\x83\x94\xe3\x82\xbd\xe3\x83\xbc\xe3\x83\x89\xe5\x8f\x82\xe7\x85\xa7\xe3\x83\x91\xe3\x82\xbf\xe3\x83\xbc\xe3\x83\xb3' + CRLF +
    b'\t\tPattern epRefPat = Pattern.compile("\\"__ref\\":\\"Episode:(\\\\d+)\\"");' + CRLF +
    b'\t\t// \xe7\xab\xa0\xe3\x82\xbf\xe3\x82\xa4\xe3\x83\x88\xe3\x83\xab\xe3\x83\x91\xe3\x82\xbf\xe3\x83\xbc\xe3\x83\xb3 (\xe6\x8b\xa1\xe5\xbc\xb5\xe6\x96\x87\xe5\xad\x97\xe3\x82\x92\xe9\x99\xa4\xe3\x81\x84\xe3\x81\x9f\xe3\x82\xb7\xe3\x83\xb3\xe3\x83\x97\xe3\x83\xab\xe3\x81\xaa\xe3\x83\x9e\xe3\x83\x83\xe3\x83\x81)' + CRLF +
    b'\t\tPattern titlePat = Pattern.compile("\\"title\\":\\"([^\\"]*+)\\"");' + CRLF +
    b'\t\t// \xe5\x85\xa8\xe3\x83\x81\xe3\x83\xa3\xe3\x83\x97\xe3\x82\xbf\xe3\x83\xbc\xe3\x82\xad\xe3\x83\xbc\xe3\x81\xae\xe4\xbd\x8d\xe7\xbd\xae\xe3\x82\x92\xe5\x8f\x8e\xe9\x9b\x86' + CRLF +
    b'\t\tjava.util.List<Integer> positions = new java.util.ArrayList<Integer>();' + CRLF +
    b'\t\tMatcher chMatcher = chapterKeyPat.matcher(json);' + CRLF +
    b'\t\twhile (chMatcher.find()) { positions.add(chMatcher.start()); }' + CRLF +
    b'\t\tif (positions.isEmpty()) return result;' + CRLF +
    b'\t\tfor (int i = 0; i < positions.size(); i++) {' + CRLF +
    b'\t\t\tint start = positions.get(i);' + CRLF +
    b'\t\t\tint end = (i + 1 < positions.size()) ? positions.get(i + 1) : json.length();' + CRLF +
    b'\t\t\tString block = json.substring(start, end);' + CRLF +
    b'\t\t\t// \xe7\xab\xa0\xe3\x82\xbf\xe3\x82\xa4\xe3\x83\x88\xe3\x83\xab\xe6\x8a\xbd\xe5\x87\xba' + CRLF +
    b'\t\t\tMatcher titleM = titlePat.matcher(block);' + CRLF +
    b'\t\t\tif (!titleM.find()) continue;' + CRLF +
    b'\t\t\tString chTitle = titleM.group(1)' + CRLF +
    b'\t\t\t\t.replace("\\\\n", "\\n").replace("\\\\r", "\\r")' + CRLF +
    b'\t\t\t\t.replace("\\\\\\"", "\\"").replace("\\\\\\\\", "\\\\");' + CRLF +
    b'\t\t\t// \xe3\x82\xa8\xe3\x83\x94\xe3\x82\xbd\xe3\x83\xbc\xe3\x83\x89\xe5\x8f\x82\xe7\x85\xa7\xe3\x82\x92\xe5\x8f\x8e\xe9\x9b\x86' + CRLF +
    b'\t\t\tMatcher refM = epRefPat.matcher(block);' + CRLF +
    b'\t\t\twhile (refM.find()) {' + CRLF +
    b'\t\t\t\tString epId = refM.group(1);' + CRLF +
    b'\t\t\t\tif (!result.containsKey(epId)) result.put(epId, chTitle);' + CRLF +
    b'\t\t\t}' + CRLF +
    b'\t\t}' + CRLF +
    b'\t\treturn result;' + CRLF +
    b'\t}' + CRLF
)

content = content[:end_pos] + new_method + content[end_pos:]
print(f"Step 4: buildEpisodeChapterMapFromNextData method added at {end_pos}")

# ============================================================
# Write and verify
# ============================================================
with open(filepath, 'wb') as f:
    f.write(content)
print("File written.")

# Verification
with open(filepath, 'rb') as f:
    verify = f.read()

checks = [
    b'nextDataEpisodeChapterMap = null',
    b'buildEpisodeChapterMapFromNextData',
    b'chapterIdMap.isEmpty()',
    b'nextDataEpisodeChapterMap.get(chapterHref)',
    b'chapterKeyPat = Pattern.compile',
    b'titlePat = Pattern.compile',
    b'epRefPat = Pattern.compile',
]
for check in checks:
    idx = verify.find(check)
    print(f"  {check[:40].decode('ascii', errors='replace')}: {'OK' if idx >= 0 else 'MISSING'} at {idx}")
