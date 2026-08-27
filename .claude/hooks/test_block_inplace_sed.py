import json
import subprocess
import sys

HOOK = r"D:\git\AozoraEpub3\AozoraEpub3\.claude\hooks\block_inplace_sed.py"

CASES = [
    ("追跡ファイルへの sed -i", "sed -i 's/a/b/' docs/index.md", True),
    ("追跡ファイル (複合コマンド)", "cd /d/git/AozoraEpub3/AozoraEpub3 && sed -i s/a/b/ README.md", True),
    ("追跡ファイル (perl -pi)", "perl -pi -e 's/a/b/' README.md", True),
    ("スクラッチパッド (変数パス)", "sed -i 's/a/b/' \"$SP/analyze.py\"", False),
    ("scratchpad の絶対パス", "sed -i 's/a/b/' C:/Users/x/Temp/foo.py", False),
    ("sed -i 以外", "grep -n foo docs/index.md", False),
    ("パイプの sed (in-place でない)", "cat README.md | sed 's/a/b/' > /tmp/out", False),
    (
        "コミットメッセージ本文に sed -i を含む heredoc",
        "git commit -F- <<'EOF'\nchore: sed -i をブロックする\n\nsed -i は README.md の CRLF を壊す。\nsed -i 's/a/b/' docs/index.md のような使い方をやめる。\nEOF\ngit push origin master",
        False,
    ),
    (
        "heredoc の後に本物の sed -i",
        "git commit -F- <<'EOF'\nmsg\nEOF\nsed -i s/a/b/ README.md",
        True,
    ),
]

fail = 0
for name, cmd, expect_deny in CASES:
    p = subprocess.run(
        [sys.executable, HOOK],
        input=json.dumps({"tool_name": "Bash", "tool_input": {"command": cmd}}),
        capture_output=True, text=True,
    )
    denied = "permissionDecision" in p.stdout
    ok = denied == expect_deny
    fail += 0 if ok else 1
    print("%s  %-40s expect=%s got=%s" % ("OK  " if ok else "FAIL", name, "deny" if expect_deny else "allow", "deny" if denied else "allow"))
print("\n%d 件中 %d 件 NG" % (len(CASES), fail))
sys.exit(1 if fail else 0)
