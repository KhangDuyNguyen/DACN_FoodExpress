#!/bin/bash
# Script tạo nhánh mới cho tuần mới theo format team

set -e

if [ -z "$1" ]; then
    echo "❌ Thiếu số tuần!"
    echo "Cách dùng: ./new-week.sh 7 [mô-tả]"
    echo "Ví dụ: ./new-week.sh 7 cart-payment"
    exit 1
fi

WEEK=$1
DESCRIPTION=${2:-feature}

# Format: feature/duong-tuan<X>-<mô-tả>
BRANCH_NAME="feature/duong-tuan$(printf "%02d" $WEEK)-$DESCRIPTION"

echo "🚀 Tạo nhánh mới: $BRANCH_NAME"

# Đảm bảo main cập nhật
echo "📥 Cập nhật main..."
git checkout main
git pull origin main

# Kiểm tra nhánh đã tồn tại chưa
if git show-ref --verify --quiet refs/heads/$BRANCH_NAME; then
    echo "⚠️  Nhánh $BRANCH_NAME đã tồn tại!"
    read -p "Bạn muốn [U]pdate hay [C]ancel? (u/c): " choice
    if [ "$choice" == "u" ] || [ "$choice" == "U" ]; then
        git checkout $BRANCH_NAME
        git merge main --no-edit
        echo "✅ Đã cập nhật $BRANCH_NAME với main mới!"
    else
        echo "❌ Hủy."
        exit 1
    fi
else
    # Tạo nhánh mới từ main
    git checkout -b $BRANCH_NAME
    echo "✅ Đã tạo nhánh $BRANCH_NAME từ main!"
fi

echo ""
echo "📋 Bước tiếp theo:"
echo "   1. Code thoải mái trên nhánh $BRANCH_NAME"
echo "   2. Mỗi sáng chạy: ./sync-main.sh"
echo "   3. Sau khi xong: git push -u origin $BRANCH_NAME"
echo "   4. Tạo PR: gh pr create --base main --head $BRANCH_NAME"
