import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { fetchMyRestaurants } from '../../api/merchant'
import type { MerchantRestaurantDTO } from '../../api/merchant'
import { useBranch } from './MerchantBranchContext'

const STATUS_LABEL: Record<string, string> = {
  ACTIVE: 'Đang hoạt động', PENDING: 'Chờ duyệt',
  HIDDEN: 'Đang ẩn', SUSPENDED: 'Bị khóa',
}
const STATUS_COLOR: Record<string, string> = {
  ACTIVE: 'bg-green-100 text-green-700', PENDING: 'bg-yellow-100 text-yellow-700',
  HIDDEN: 'bg-gray-100 text-gray-600', SUSPENDED: 'bg-red-100 text-red-600',
}
const OPEN_LABEL: Record<string, string> = {
  OPEN: '🟢 Đang mở', CLOSED: '🔴 Đã đóng', TEMP_CLOSED: '🟡 Tạm đóng',
}

export default function BranchSelector() {
  const navigate = useNavigate()
  const { setSelectedBranch } = useBranch()

  const { data: branches = [], isLoading, isError } = useQuery({
    queryKey: ['myRestaurants'],
    queryFn: fetchMyRestaurants,
  })

  const handleSelect = (branch: MerchantRestaurantDTO) => {
    setSelectedBranch(branch)
    navigate('/merchant/menu')
  }

  if (isLoading) return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-orange-500 border-t-transparent" />
    </div>
  )

  if (isError) return (
    <div className="flex min-h-screen items-center justify-center">
      <p className="text-red-500">Không thể tải danh sách quán. Vui lòng thử lại.</p>
    </div>
  )

  return (
    <div className="min-h-screen bg-gray-50 px-4 py-12">
      <div className="mx-auto max-w-xl">
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-bold text-gray-900">Chọn chi nhánh</h1>
          <p className="mt-1 text-gray-500">Chọn quán bạn muốn quản lý</p>
        </div>

        {branches.length === 0 ? (
          <div className="rounded-xl border border-dashed border-gray-300 bg-white p-12 text-center text-gray-400">
            <p className="text-4xl">🏪</p>
            <p className="mt-2 font-medium">Bạn chưa có quán nào</p>
            <p className="text-sm">Liên hệ admin để được tạo quán</p>
          </div>
        ) : (
          <div className="space-y-3">
            {branches.map(branch => (
              <button
                key={branch.id}
                onClick={() => handleSelect(branch)}
                className="flex w-full items-center justify-between rounded-xl border border-gray-200 bg-white px-5 py-4 text-left shadow-sm transition hover:border-orange-400 hover:shadow-md active:scale-[0.99]"
              >
                <div>
                  <p className="font-semibold text-gray-900">{branch.name}</p>
                  <p className="mt-0.5 text-sm text-gray-500">📍 {branch.address}</p>
                  <p className="mt-1 text-sm">{OPEN_LABEL[branch.openStatus] ?? branch.openStatus}</p>
                </div>
                <div className="flex flex-col items-end gap-2">
                  <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_COLOR[branch.status] ?? 'bg-gray-100 text-gray-600'}`}>
                    {STATUS_LABEL[branch.status] ?? branch.status}
                  </span>
                  <span className="text-orange-500">→</span>
                </div>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
