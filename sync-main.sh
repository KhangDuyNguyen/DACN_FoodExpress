#!/bin/bash
# Script tự động sync main vào nhánh hiện tại - tránh conflict

set -e

echo "🔄 Đang cập nhật main..."

# Lưu nhánh hiện tại
CURRENT_BRANCH=$(git branch --show-current)

if [ "$CURRENT_BRANCH" == "main" ]; then
    echo "⚠️  Bạn đang ở main. Hãy checkout sang nhánh làm việc!"
    exit 1
fi

echo "📍 Nhánh hiện tại: $CURRENT_BRANCH"

# Fetch main mới nhất
echo "📥 Fetch origin/main..."
git fetch origin main

# Kiểm tra có commit mới không
BEHIND=$(git rev-list --count HEAD..origin/main 2>/dev/null || echo 0)

if [ "$BEHIND" -eq 0 ]; then
    echo "✅ Nhánh của bạn đã cập nhật!"
    exit 0
fi

echo "📊 Main có $BEHIND commits mới. Đang merge..."

# Merge main vào nhánh hiện tại
if git merge origin/main --no-edit; then
    echo "✅ Merge thành công!"
    echo "💡 Nhớ test lại code trước khi push:"
    echo "   - Backend: cd backend && ./mvnw compile"
    echo "   - Web: cd web && npm run build"
    echo "   - Android: cd android && ./gradlew assembleDebug"
else
    echo "❌ Có conflict! Hãy giải quyết theo docs/GitWorkflow.md"
    echo "   1. Mở files conflict"
    echo "   2. Sửa thủ công"
    echo "   3. git add <files>"
    echo "   4. git commit"
    exit 1
fi
