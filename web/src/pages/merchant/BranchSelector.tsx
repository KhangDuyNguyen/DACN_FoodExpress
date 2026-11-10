import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { fetchMyRestaurants, createRestaurant } from '../../api/merchant'
import type { MerchantRestaurantDTO, CreateRestaurantRequest } from '../../api/merchant'
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

const EMPTY_FORM: CreateRestaurantRequest = {
  name: '', description: '', address: '',
  lat: 10.7769, lng: 106.7009, // mặc định Hồ Chí Minh
  phone: '', logoUrl: '', coverUrl: '',
  minOrderAmount: 30000, prepTimeMinutes: 20,
}

export default function BranchSelector() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { setSelectedBranch } = useBranch()
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState<CreateRestaurantRequest>(EMPTY_FORM)

  const { data: branches = [], isLoading, isError } = useQuery({
    queryKey: ['myRestaurants'],
    queryFn: fetchMyRestaurants,
  })

  const createMut = useMutation({
    mutationFn: createRestaurant,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['myRestaurants'] })
      setShowForm(false)
      setForm(EMPTY_FORM)
      alert('✅ Tạo chi nhánh thành công! Chờ admin duyệt trước khi hiển thị cho khách.')
    },
    onError: (err: Error) => {
      alert('❌ ' + (err.message ?? 'Lỗi tạo chi nhánh'))
    }
  })

  const handleSelect = (branch: MerchantRestaurantDTO) => {
    setSelectedBranch(branch)
    navigate('/merchant/menu')
  }

  const set = (k: keyof CreateRestaurantRequest, v: string | number) =>
    setForm(f => ({ ...f, [k]: v }))

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

        {/* Header */}
        <div className="mb-6 flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Chọn chi nhánh</h1>
            <p className="mt-1 text-gray-500">Chọn quán bạn muốn quản lý</p>
          </div>
          <button
            onClick={() => setShowForm(v => !v)}
            className="rounded-lg bg-orange-500 px-4 py-2 text-sm font-medium text-white hover:bg-orange-600"
          >
            + Tạo chi nhánh
          </button>
        </div>

        {/* Form tạo chi nhánh mới */}
        {showForm && (
          <div className="mb-6 rounded-xl border border-orange-200 bg-orange-50 p-5">
            <h2 className="mb-4 font-semibold text-gray-800">Tạo chi nhánh mới</h2>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {[
                { label: 'Tên chi nhánh *', key: 'name', placeholder: 'VD: Cơm Tấm - CN Quận 1' },
                { label: 'Số điện thoại', key: 'phone', placeholder: '028...' },
                { label: 'Địa chỉ *', key: 'address', placeholder: '5 Nguyễn Trãi, Quận 1...' },
                { label: 'URL logo', key: 'logoUrl', placeholder: 'https://...' },
                { label: 'Vĩ độ (lat) *', key: 'lat', placeholder: '10.7769' },
                { label: 'Kinh độ (lng) *', key: 'lng', placeholder: '106.7009' },
                { label: 'Đặt hàng tối thiểu (₫)', key: 'minOrderAmount', placeholder: '30000' },
                { label: 'Thời gian chuẩn bị (phút)', key: 'prepTimeMinutes', placeholder: '20' },
              ].map(({ label, key, placeholder }) => (
                <div key={key} className={key === 'address' ? 'sm:col-span-2' : ''}>
                  <label className="mb-1 block text-xs font-medium text-gray-600">{label}</label>
                  <input
                    value={String(form[key as keyof CreateRestaurantRequest] ?? '')}
                    onChange={e => set(
                      key as keyof CreateRestaurantRequest,
                      ['lat', 'lng', 'minOrderAmount', 'prepTimeMinutes'].includes(key)
                        ? Number(e.target.value)
                        : e.target.value
                    )}
                    placeholder={placeholder}
                    className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-orange-400"
                  />
                </div>
              ))}
              <div className="sm:col-span-2">
                <label className="mb-1 block text-xs font-medium text-gray-600">Mô tả</label>
                <textarea
                  value={form.description ?? ''}
                  onChange={e => set('description', e.target.value)}
                  rows={2}
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-orange-400"
                />
              </div>
            </div>

            <div className="mt-2 rounded-lg bg-yellow-50 px-3 py-2 text-xs text-yellow-700">
              ⚠️ Chi nhánh mới sẽ có trạng thái <strong>Chờ duyệt</strong> — cần admin phê duyệt trước khi khách nhìn thấy.
            </div>

            <div className="mt-4 flex gap-3">
              <button
                onClick={() => createMut.mutate(form)}
                disabled={createMut.isPending || !form.name.trim() || !form.address.trim()}
                className="rounded-lg bg-orange-500 px-5 py-2 text-sm font-medium text-white hover:bg-orange-600 disabled:opacity-50"
              >
                {createMut.isPending ? 'Đang tạo...' : 'Tạo chi nhánh'}
              </button>
              <button
                onClick={() => { setShowForm(false); setForm(EMPTY_FORM) }}
                className="rounded-lg border border-gray-300 px-5 py-2 text-sm text-gray-600 hover:bg-gray-50"
              >
                Hủy
              </button>
            </div>
          </div>
        )}

        {/* Danh sách chi nhánh */}
        {branches.length === 0 ? (
          <div className="rounded-xl border border-dashed border-gray-300 bg-white p-12 text-center text-gray-400">
            <p className="text-4xl">🏪</p>
            <p className="mt-2 font-medium">Bạn chưa có chi nhánh nào</p>
            <p className="text-sm">Bấm "+ Tạo chi nhánh" để thêm mới</p>
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
