import { useQuery } from '@tanstack/react-query'
import { fetchHealth } from '../../api/health'

export default function CustomerHome() {
  const { data, isLoading, isError } = useQuery({ queryKey: ['health'], queryFn: fetchHealth })

  return (
    <div>
      <h1 className="text-2xl font-semibold">Web khách hàng</h1>
      <p className="mt-2 text-gray-600">
        Trang chủ sẽ hiển thị danh sách quán (Dưỡng làm). Đây chỉ là trang mẫu để kiểm tra kết nối backend.
      </p>
      <p className="mt-4 text-sm">
        Backend:{' '}
        {isLoading ? 'đang kiểm tra...' : isError ? 'không kết nối được (đã chạy backend chưa?)' : `OK (${data?.status})`}
      </p>
    </div>
  )
}
