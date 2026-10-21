import React from 'react'
import PlaceholderPage from '../../components/common/PlaceholderPage'

export const MerchantSupportPage: React.FC = () => {
  return (
    <PlaceholderPage
      title="Hỗ trợ & Khiếu nại Cửa hàng"
      subtitle="Kênh tiếp nhận hỗ trợ dành riêng cho Đối tác Nhà hàng khi gặp sự cố về thiết bị, tài xế không đến lấy món hoặc đối soát thanh toán."
      targetWeek="Tuần 6 (16/11 - 21/11/2026)"
      assignee="Sầm Xuân Điệp"
      backPath="/merchant"
      plannedFeatures={[
        'Gửi ticket khiếu nại tới Ban quản trị hệ thống FoodExpress',
        'Báo cáo sự cố khẩn cấp: Tài xế không nhận món, khách hủy đơn khi đã chế biến xong',
        'Theo dõi tiến độ bồi hoàn thiệt hại đơn hàng',
        'Hướng dẫn vận hành và quy chuẩn đóng gói thực phẩm an toàn',
      ]}
    />
  )
}

export default MerchantSupportPage
