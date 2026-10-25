import React from 'react'
import PlaceholderPage from '../../components/common/PlaceholderPage'

export const AdminMerchantApprovalPage: React.FC = () => {
  return (
    <PlaceholderPage
      title="Duyệt chủ quán và Quán ăn mới"
      subtitle="Khu vực kiểm tra hồ sơ pháp lý, thông tin chi nhánh quán và phê duyệt tài khoản Merchant trước khi cho phép mở bán trên ứng dụng."
      targetWeek="Tuần 4 (02/11 - 07/11/2026)"
      assignee="Sầm Xuân Điệp"
      backPath="/admin"
      plannedFeatures={[
        'Danh sách hồ sơ chủ quán đăng ký ở trạng thái chờ duyệt (PENDING)',
        'Xem chi tiết thông tin quán: Tên quán, địa chỉ, số điện thoại, ảnh đại diện, ảnh thực đơn',
        'Kiểm tra giấy chứng nhận vệ sinh an toàn thực phẩm / giấy phép kinh doanh',
        'Thao tác Phê duyệt (APPROVED) để quán bắt đầu hoạt động hoặc Từ chối (REJECTED) kèm lý do',
        'Gửi email thông báo tự động cho chủ quán khi hồ sơ được duyệt',
      ]}
    />
  )
}

export default AdminMerchantApprovalPage
