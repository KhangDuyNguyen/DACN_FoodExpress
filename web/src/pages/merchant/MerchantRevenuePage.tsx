import React from 'react'
import PlaceholderPage from '../../components/common/PlaceholderPage'

export const MerchantRevenuePage: React.FC = () => {
  return (
    <PlaceholderPage
      title="Thống kê Doanh thu & Dự báo AI"
      subtitle="Khu vực trực quan hóa dữ liệu kinh doanh của quán qua biểu đồ (Recharts) và mô hình dự báo nhu cầu bán hàng (AI Service Holt-Winters)."
      targetWeek="Tuần 6 (16/11 - 21/11/2026)"
      assignee="Sầm Xuân Điệp"
      backPath="/merchant"
      plannedFeatures={[
        'Biểu đồ cột / đường thể hiện doanh thu theo Ngày, Tuần, Tháng (tích hợp Recharts)',
        'Biểu đồ phân tích khung giờ vàng bán chạy (sáng, trưa, tối) từ 3000 đơn mô phỏng',
        'Tỷ lệ hoàn thành đơn, đơn hủy và lý do hủy đơn',
        'Kết nối AI Service FastAPI để hiển thị đường dự báo doanh thu 7 ngày tới (bảng demand_forecasts)',
        'Xuất báo cáo tài chính định dạng Excel / CSV',
      ]}
    />
  )
}

export default MerchantRevenuePage
