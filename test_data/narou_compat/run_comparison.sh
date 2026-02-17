#!/bin/bash
#
# narou.rb と AozoraEpub3 の出力比較テスト実行スクリプト
#
# 使い方:
#   bash test_data/narou_compat/run_comparison.sh
#
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "========================================="
echo " narou.rb 出力比較テスト"
echo "========================================="
echo ""

# Step 1: Ruby側の期待出力を生成
echo "[Step 1] narou.rb で期待出力を生成..."
ruby "$SCRIPT_DIR/generate_expected.rb"
echo ""

# Step 2: Java側のテストを実行
echo "[Step 2] Java側の比較テストを実行..."
cd "$PROJECT_DIR"
./gradlew test --tests NarouRbOutputComparisonTest --info 2>&1 | tail -30
echo ""

# Step 3: 結果確認
echo "[Step 3] 結果ファイル:"
echo "  期待出力(narou.rb): $SCRIPT_DIR/expected_narou.txt"
echo "  実出力(Java):       $SCRIPT_DIR/actual_java.txt"
echo "  差異レポート:       $SCRIPT_DIR/diff_report.txt"
echo ""

if [ -f "$SCRIPT_DIR/diff_report.txt" ]; then
    echo "--- diff_report.txt ---"
    cat "$SCRIPT_DIR/diff_report.txt"
fi
