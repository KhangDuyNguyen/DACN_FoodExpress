import React from 'react'
import PlaceholderPage from '../../components/common/PlaceholderPage'

export const MerchantTopDishesPage: React.FC = () => {
  return (
    <PlaceholderPage
      title="Thống kê Món bán chạy (Top Items)"
      subtitle="Báo cáo phân tích món ăn được yêu thích nhất tại quán dựa trên số lượng đơn hàng và đánh giá từ khách hàng."
      targetWeek="Tuần 6 (16/11 - 21/11/2026)"
      assignee="Sầm Xuân Điệp"
      backPath="/merchant"
      plannedFeatures={[
        'Xếp hạng top 10 món bán chạy nhất theo số lượng phần ăn và doanh thu đóng góp',
        'Biểu đồ tròn tỷ trọng đóng góp của từng nhóm thực đơn (Cơm, Đồ uống, Món thêm)',
        'Gợi ý tối ưu thực đơn: Món ít khách gọi để xem xét điều chỉnh giá hoặc cải thiện công thức',
        'Cung cấp dữ liệu co-occurrence cho AI Service gợi ý món ăn kèm (recommendation engine)',
      ]}
    />
  )
}

export default MerchantTopDishesPage
