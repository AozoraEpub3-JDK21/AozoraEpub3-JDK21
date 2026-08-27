#!/usr/bin/env python
"""PreToolUse(Bash) hook: git 管理下のファイルへの in-place 置換 (sed -i / perl -pi 等) を止める。

Why:
  .md / .ini / .txt は .gitattributes の対象外で作業ツリーが CRLF。sed -i は CR を落とす。
  コミット時に git が正規化するので `git diff` には出ないが、`gradlew dist` は作業ツリーから
  読むため LF の README.md が配布 ZIP に混入する（2026-08-27 の v1.6.1 リリースで実際に発生）。
  本文の編集は Edit / Write ツールを使う（CLAUDE.md §1「仕組みで対応する」）。

スクラッチパッド等 git 管理外のファイルは対象外（追跡されていないパスは許可する）。
判定できないときは常に許可側に倒す（フックが作業を止めないことを優先）。
"""
import json
import os
import re
import shlex
import subprocess
import sys

# sed -i / sed -ni / sed --in-place / perl -pi / perl -i.bak など。
# コマンド位置 (行頭 or シェル演算子の直後) に限定する。単なる空白の後まで拾うと、
# コミットメッセージ本文の「sed -i は…」のような散文まで誤検知する。
INPLACE = re.compile(
    r"(?:^|[\n;&|(])\s*(?:sed|perl)\s+[^;&|\n]*-(?:[a-zA-Z]*i\b|-in-place)"
)

# heredoc 本文 (git commit -F- <<'EOF' ... EOF) は実行されるコマンドではないので判定から外す
HEREDOC = re.compile(r"<<-?\s*(['\"]?)([A-Za-z_][A-Za-z0-9_]*)\1")


def strip_heredocs(cmd):
    """heredoc の本文を取り除いた文字列を返す。"""
    out = []
    lines = cmd.split("\n")
    i = 0
    while i < len(lines):
        line = lines[i]
        out.append(line)
        m = HEREDOC.search(line)
        i += 1
        if not m:
            continue
        delim = m.group(2)
        while i < len(lines) and lines[i].strip() != delim:
            i += 1
        i += 1  # 終端行も捨てる
    return "\n".join(out)


def main():
    try:
        payload = json.load(sys.stdin)
    except Exception:
        return 0
    cmd = (payload.get("tool_input") or {}).get("command") or ""
    if not cmd:
        return 0
    cmd = strip_heredocs(cmd)
    if not INPLACE.search(cmd):
        return 0

    repo = os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
    try:
        repo = subprocess.run(
            ["git", "-C", repo, "rev-parse", "--show-toplevel"],
            capture_output=True, text=True, timeout=10,
        ).stdout.strip()
    except Exception:
        return 0
    if not repo:
        return 0

    try:
        tokens = shlex.split(cmd, posix=True)
    except ValueError:
        tokens = cmd.split()

    tracked = []
    for tok in tokens:
        t = tok.replace("\\", "/").strip()
        if not t or t.startswith("-") or "=" in t:
            continue
        try:
            r = subprocess.run(
                ["git", "-C", repo, "ls-files", "--error-unmatch", "--", t],
                capture_output=True, text=True, timeout=10,
            )
        except Exception:
            continue
        if r.returncode == 0 and t not in tracked:
            tracked.append(t)

    if not tracked:
        return 0

    reason = (
        "git 管理下のファイルへの in-place 置換をブロックしました: "
        + " ".join(tracked)
        + "\nsed -i / perl -pi は行末コード (CRLF) を壊します。git diff には出ませんが、"
        "gradlew dist は作業ツリーから読むので配布物に LF のファイルが混入します"
        " (docs/release-procedure.md §2.3)。\n"
        "Edit ツールで編集してください。すでに壊した場合は "
        "rm <file> && git checkout HEAD -- <file> で戻せます。"
    )
    json.dump(
        {
            "hookSpecificOutput": {
                "hookEventName": "PreToolUse",
                "permissionDecision": "deny",
                "permissionDecisionReason": reason,
            }
        },
        sys.stdout,
        ensure_ascii=False,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
