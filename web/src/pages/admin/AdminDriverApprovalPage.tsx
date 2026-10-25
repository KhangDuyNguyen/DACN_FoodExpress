import React from 'react'
import PlaceholderPage from '../../components/common/PlaceholderPage'

export const AdminDriverApprovalPage: React.FC = () => {
  return (
    <PlaceholderPage
      title="Duyệt hồ sơ tài xế giao hàng"
      subtitle="Khu vực thẩm định hồ sơ tài xế (Driver) để đảm bảo an toàn vận chuyển trước khi kích hoạt tài khoản nhận cuốc xe."
      targetWeek="Tuần 4 (02/11 - 07/11/2026)"
      assignee="Sầm Xuân Điệp"
      backPath="/admin"
      plannedFeatures={[
        'Danh sách tài xế đăng ký mới với giấy tờ đính kèm',
        'Kiểm tra ảnh CCCD/CMND 2 mặt, Giấy phép lái xe (GPLX), Cà vẹt xe và Biển số xe',
        'Phê duyệt (ACTIVE) kích hoạt tài khoản tài xế có thể đăng nhập app Android và bật chế độ nhận đơn',
        'Từ chối hồ sơ kèm ghi chú hướng dẫn tài xế bổ sung giấy tờ hợp lệ',
        'Khóa hoặc tạm dừng tài khoản tài xế khi có phản ánh vi phạm',
      ]}
    />
  )
}

export default AdminDriverApprovalPage
