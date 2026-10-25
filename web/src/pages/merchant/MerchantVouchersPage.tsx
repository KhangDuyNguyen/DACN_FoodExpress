import React from 'react'
import PlaceholderPage from '../../components/common/PlaceholderPage'

export const MerchantVouchersPage: React.FC = () => {
  return (
    <PlaceholderPage
      title="Voucher khuyến mãi riêng của quán"
      subtitle="Khu vực dành cho Chủ quán tự tạo mã giảm giá chiết khấu riêng cho khách hàng ăn tại quán của mình (restaurant_id = ID quán)."
      targetWeek="Tuần 3 (26/10 - 31/10/2026)"
      assignee="Sầm Xuân Điệp"
      backPath="/merchant"
      plannedFeatures={[
        'Tạo mã voucher với tiền tố riêng của quán (Ví dụ: COMTAM10, COMTAM20K)',
        'Cấu hình giảm % hoặc giảm số tiền cố định cho các món trong thực đơn quán',
        'Tự động kiểm tra giá trị đơn hàng tối thiểu (min_order) trước khi áp dụng',
        'Giới hạn số lượng phát hành để kiểm soát chi phí marketing của quán',
        'Báo cáo hiệu quả: Doanh thu tạo ra từ các đơn hàng có áp dụng mã của quán',
      ]}
    />
  )
}

export default MerchantVouchersPage
