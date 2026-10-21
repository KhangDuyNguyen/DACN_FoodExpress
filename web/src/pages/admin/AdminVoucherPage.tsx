import React from 'react'
import PlaceholderPage from '../../components/common/PlaceholderPage'

export const AdminVoucherPage: React.FC = () => {
  return (
    <PlaceholderPage
      title="Quản lý Voucher toàn sàn"
      subtitle="Khu vực dành cho Quản trị viên khởi tạo và giám sát các chương trình mã giảm giá trên toàn hệ thống FoodExpress."
      targetWeek="Tuần 3 (26/10 - 31/10/2026)"
      assignee="Sầm Xuân Điệp"
      backPath="/admin"
      plannedFeatures={[
        'Tạo mới voucher với 3 loại giảm: PERCENT (giảm %), FIXED (giảm số tiền cố định), FREE_SHIP (miễn phí giao hàng)',
        'Cấu hình giá trị đơn hàng tối thiểu (min_order) và mức giảm tối đa (max_discount)',
        'Thiết lập giới hạn số lượt sử dụng toàn sàn (usage_limit) và giới hạn mỗi tài khoản (per_user_limit)',
        'Quản lý thời hạn hiệu lực (start_at, end_at) và kích hoạt / tạm dừng (ACTIVE / PAUSED / EXPIRED)',
        'Bảng thống kê số lượt đã dùng (used_count) theo thời gian thực',
      ]}
    />
  )
}

export default AdminVoucherPage
