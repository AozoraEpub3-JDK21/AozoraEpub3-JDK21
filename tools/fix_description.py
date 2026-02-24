#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""WebAozoraConverter.java の DESCRIPTION script要素対応ブロックを正しく修正するスクリプト"""

import sys

filepath = 'src/com/github/hmdev/web/WebAozoraConverter.java'

with open(filepath, 'rb') as f:
    content = f.read()

# 間違ったブロック全体を検索して置換
# if ("script"...) { ... } else { printNode... } の全体を置換
old_block_start = b'if ("script".equals(description.tagName())) {'
old_block_end = b'\t\t\t\t\t\t} else {\r\n\t\t\t\t\t\t\tprintNode(bw, description, true);\r\n\t\t\t\t\t\t}'

idx_start = content.find(old_block_start)
idx_end = content.find(old_block_end)
if idx_start < 0 or idx_end < 0:
    print(f"ERROR: Block not found. start={idx_start}, end={idx_end}")
    sys.exit(1)

idx_end += len(old_block_end)
old_block = content[idx_start:idx_end]
print(f"Old block found at {idx_start}-{idx_end}, length={len(old_block)}")

# 正しい Java ソースコードを構築（UTF-8 バイト列）
# 重要: Javaソース内の文字列リテラルは次のとおり
#   "\\r\\n"  → Java が \r\n (4文字: \, r, \, n) にマッチ   → ファイルには \\r\\n として書く (バックスラッシュ2つ+r+バックスラッシュ2つ+n)
#   "\n"      → Java の改行文字                              → ファイルには \n として書く (バックスラッシュ+n)
#   '\n'      → Java のchar型改行                            → ファイルには \n として書く
T7 = b'\t\t\t\t\t\t\t'   # 7 tabs
T8 = b'\t\t\t\t\t\t\t\t'  # 8 tabs
T9 = b'\t\t\t\t\t\t\t\t\t'  # 9 tabs
CRLF = b'\r\n'

# コメント行 (UTF-8)
comment1 = (
    T7 +
    "// script要素は正規表現でテキスト抽出後、JSON \\n エスケープを改行に変換して出力".encode('utf-8') +
    CRLF
)
comment2 = (
    T7 +
    "// (カクヨムなど __NEXT_DATA__ JSON ベースの説明取得に対応)".encode('utf-8') +
    CRLF
)

# Java コード: descText = descText.replace("\\r\\n", "\n").replace("\\r", "\n").replace("\\n", "\n");
# ファイル上は:
#   "\\r\\n"  → 0x22 0x5c 0x5c 0x72 0x5c 0x5c 0x6e 0x22
#   "\n"      → 0x22 0x5c 0x6e 0x22
dq_bs_r_bs_n = bytes([0x22, 0x5c, 0x5c, 0x72, 0x5c, 0x5c, 0x6e, 0x22])  # "\\r\\n"
dq_bs_r      = bytes([0x22, 0x5c, 0x5c, 0x72, 0x22])                     # "\\r"
dq_bs_n      = bytes([0x22, 0x5c, 0x5c, 0x6e, 0x22])                     # "\\n"
dq_nl        = bytes([0x22, 0x5c, 0x6e, 0x22])                           # "\n"
sq_nl        = bytes([0x27, 0x5c, 0x6e, 0x27])                           # '\n'

replace_line = (
    T8 +
    b'descText = descText.replace(' + dq_bs_r_bs_n + b', ' + dq_nl + b')' +
    b'.replace(' + dq_bs_r + b', ' + dq_nl + b')' +
    b'.replace(' + dq_bs_n + b', ' + dq_nl + b');' +
    CRLF
)

for_line = (
    T8 +
    b'for (String line : descText.split(' + dq_nl + b', -1)) {' +
    CRLF
)

append_n_line = (
    T9 +
    b'bw.append(' + sq_nl + b');' +
    CRLF
)

new_block = (
    b'if ("script".equals(description.tagName())) {' + CRLF +
    comment1 +
    comment2 +
    T7 + b'String descText = getExtractText(doc, this.queryMap.get(ExtractId.DESCRIPTION));' + CRLF +
    T7 + b'if (descText != null && !descText.isEmpty()) {' + CRLF +
    replace_line +
    for_line +
    T9 + b'printText(bw, line);' + CRLF +
    append_n_line +
    T8 + b'}' + CRLF +
    T7 + b'}' + CRLF +
    b'\t\t\t\t\t\t} else {' + CRLF +
    b'\t\t\t\t\t\t\tprintNode(bw, description, true);' + CRLF +
    b'\t\t\t\t\t\t}'
)

new_content = content[:idx_start] + new_block + content[idx_end:]

with open(filepath, 'wb') as f:
    f.write(new_content)

print("Done! Verifying...")

# 検証
with open(filepath, 'rb') as f:
    verify = f.read()

idx = verify.find(b'replace(' + dq_bs_n + b', ' + dq_nl + b')')
if idx > 0:
    print(f"Verification OK: replace call found at {idx}")
    print("Context:", repr(verify[idx-30:idx+60]))
else:
    print("WARNING: replace call not found in expected form")
