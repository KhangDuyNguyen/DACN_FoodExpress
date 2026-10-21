import React from 'react'
import PlaceholderPage from '../../components/common/PlaceholderPage'

export const AdminSupportPage: React.FC = () => {
  return (
    <PlaceholderPage
      title="Hỗ trợ khách hàng & Xử lý ticket"
      subtitle="Khu vực quản lý các yêu cầu hỗ trợ, khiếu nại về đơn hàng hoặc sự cố thanh toán từ phía Khách hàng và Cửa hàng."
      targetWeek="Tuần 6 (16/11 - 21/11/2026)"
      assignee="Sầm Xuân Điệp"
      backPath="/admin"
      plannedFeatures={[
        'Tiếp nhận ticket từ API POST /api/support/tickets',
        'Phân loại sự cố: Đơn trễ giờ, sai món, hoàn tiền (REFUND), sự cố ứng dụng',
        'Cập nhật tiến độ giải quyết (OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED)',
        'Gửi tin nhắn phản hồi trao đổi trực tiếp với khách hàng',
        'Thống kê thời gian xử lý khiếu nại trung bình (SLA) của đội ngũ hỗ trợ',
      ]}
    />
  )
}

export default AdminSupportPage
