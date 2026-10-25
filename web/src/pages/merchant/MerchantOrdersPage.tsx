import React from 'react'
import PlaceholderPage from '../../components/common/PlaceholderPage'

export const MerchantOrdersPage: React.FC = () => {
  return (
    <PlaceholderPage
      title="Quản lý Đơn hàng (Đơn đang đến Realtime)"
      subtitle="Khu vực nhận đơn hàng trực tiếp từ khách hàng theo thời gian thực. Đã chuẩn bị kiến trúc sẵn sàng để tích hợp WebSocket STOMP ở Tuần 5."
      targetWeek="Tuần 5 (09/11 - 14/11/2026)"
      assignee="Sầm Xuân Điệp"
      backPath="/merchant"
      plannedFeatures={[
        'Kết nối WebSocket STOMP kênh /topic/restaurants/{id}/orders để nhận thông báo âm thanh và popup đơn mới trong vòng 2 giây',
        'Nút "Xác nhận nhận đơn" (chuyển trạng thái sang CONFIRMED / PREPARING)',
        'Nút "Từ chối đơn" kèm lý do (hết món, quá tải)',
        'Cập nhật tiến độ: Đang nấu -> Đã nấu xong (sẵn sàng chờ tài xế đến lấy)',
        'Bộ lọc đơn theo trạng thái: Mới, Đang nấu, Đang giao, Đã giao, Đã hủy',
      ]}
    />
  )
}

export default MerchantOrdersPage
