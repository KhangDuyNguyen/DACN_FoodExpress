import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { fetchMyRestaurantDetail } from '../../api/merchant'
import { updateRestaurant, updateHours, updateOpenStatus } from '../../api/merchant'
import type { HourEntry } from '../../api/merchant'
import { useBranch } from './MerchantBranchContext'

const DAY_NAMES = ['', 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'Chủ nhật']

const DEFAULT_HOURS: HourEntry[] = [1,2,3,4,5,6,7].map(d => ({
  dayOfWeek: d, openTime: '08:00', closeTime: '22:00',
}))

export default function RestaurantSettings() {
  const { selectedBranch, setSelectedBranch } = useBranch()
  const navigate = useNavigate()
  const qc = useQueryClient()

  if (!selectedBranch) {
    return (
      <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4 text-gray-500">
        <p className="text-4xl">🏪</p>
        <p className="font-medium">Chưa chọn chi nhánh</p>
        <button onClick={() => navigate('/merchant/branch')}
          className="rounded-lg bg-orange-500 px-5 py-2 text-sm font-medium text-white hover:bg-orange-600">
          Chọn chi nhánh
        </button>
      </div>
    )
  }

  const rid = selectedBranch.id

  const { data: restaurant, isLoading } = useQuery({
    queryKey: ['restaurantDetail', rid],
    queryFn: () => fetchMyRestaurantDetail(rid),
  })

  // ── Form thông tin quán ──
  const [info, setInfo] = useState({ name: '', description: '', address: '', phone: '', logoUrl: '', coverUrl: '', minOrderAmount: 0, prepTimeMinutes: 0 })
  const [hours, setHours] = useState<HourEntry[]>(DEFAULT_HOURS)

  useEffect(() => {
    if (!restaurant) return
    setInfo({
      name: restaurant.name,
      description: restaurant.description ?? '',
      address: restaurant.address,
      phone: restaurant.phone ?? '',
      logoUrl: restaurant.logoUrl ?? '',
      coverUrl: restaurant.coverUrl ?? '',
      minOrderAmount: restaurant.minOrderAmount,
      prepTimeMinutes: restaurant.prepTimeMinutes,
    })
    if (restaurant.hours.length > 0) {
      setHours(restaurant.hours.map(h => ({
        dayOfWeek: h.dayOfWeek,
        openTime: h.openTime.slice(0, 5),
        closeTime: h.closeTime.slice(0, 5),
      })))
    }
  }, [restaurant])

  const infoMut = useMutation({
    mutationFn: () => updateRestaurant(rid, { ...info, minOrderAmount: Number(info.minOrderAmount), prepTimeMinutes: Number(info.prepTimeMinutes) }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['restaurantDetail', rid] })
      alert('✅ Đã lưu thông tin quán!')
    },
  })

  const hoursMut = useMutation({
    mutationFn: () => updateHours(rid, hours),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['restaurantDetail', rid] })
      alert('✅ Đã lưu giờ mở cửa!')
    },
  })

  const statusMut = useMutation({
    mutationFn: (status: string) => updateOpenStatus(rid, status),
    onSuccess: (_, status) => {
      qc.invalidateQueries({ queryKey: ['restaurantDetail', rid] })
      // Cập nhật branch context
      setSelectedBranch({ ...selectedBranch, openStatus: status as 'OPEN' | 'CLOSED' | 'TEMP_CLOSED' })
    },
  })

  const setHour = (day: number, field: 'openTime' | 'closeTime', val: string) => {
    setHours(hs => hs.map(h => h.dayOfWeek === day ? { ...h, [field]: val } : h))
  }

  if (isLoading) return (
    <div className="space-y-4">
      {[...Array(4)].map((_, i) => <div key={i} className="h-12 animate-pulse rounded-lg bg-gray-200" />)}
    </div>
  )

  const isOpen = restaurant?.openStatus === 'OPEN'

  return (
    <div className="mx-auto max-w-2xl space-y-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">Thông tin quán</h1>
          <p className="mt-0.5 text-sm text-gray-500">🏪 {selectedBranch.name}</p>
        </div>
        <button onClick={() => navigate('/merchant/branch')}
          className="rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50">
          Đổi chi nhánh
        </button>
      </div>

      {/* Toggle mở/đóng quán */}
      <div className="flex items-center justify-between rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
        <div>
          <p className="font-semibold text-gray-800">Trạng thái quán</p>
          <p className={`mt-1 text-sm font-medium ${isOpen ? 'text-green-600' : 'text-red-500'}`}>
            {isOpen ? '🟢 Đang mở cửa' : '🔴 Đang đóng cửa'}
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => statusMut.mutate('OPEN')}
            disabled={isOpen || statusMut.isPending}
            className="rounded-lg bg-green-500 px-4 py-2 text-sm font-medium text-white hover:bg-green-600 disabled:opacity-40"
          >
            Mở cửa
          </button>
          <button
            onClick={() => statusMut.mutate('CLOSED')}
            disabled={!isOpen || statusMut.isPending}
            className="rounded-lg bg-red-500 px-4 py-2 text-sm font-medium text-white hover:bg-red-600 disabled:opacity-40"
          >
            Đóng cửa
          </button>
          <button
            onClick={() => statusMut.mutate('TEMP_CLOSED')}
            disabled={statusMut.isPending}
            className="rounded-lg bg-yellow-500 px-4 py-2 text-sm font-medium text-white hover:bg-yellow-600 disabled:opacity-40"
          >
            Tạm đóng
          </button>
        </div>
      </div>

      {/* Form thông tin quán */}
      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
        <h2 className="mb-4 font-semibold text-gray-800">Thông tin cơ bản</h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {[
            { label: 'Tên quán *', key: 'name' },
            { label: 'Số điện thoại', key: 'phone' },
            { label: 'Địa chỉ *', key: 'address' },
            { label: 'URL logo', key: 'logoUrl' },
            { label: 'URL ảnh bìa', key: 'coverUrl' },
          ].map(({ label, key }) => (
            <div key={key} className={key === 'address' ? 'sm:col-span-2' : ''}>
              <label className="mb-1 block text-xs font-medium text-gray-600">{label}</label>
              <input
                value={(info as Record<string, string | number>)[key] as string}
                onChange={e => setInfo(i => ({ ...i, [key]: e.target.value }))}
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-orange-400"
              />
            </div>
          ))}
          <div>
            <label className="mb-1 block text-xs font-medium text-gray-600">Đặt hàng tối thiểu (₫)</label>
            <input type="number" min={0} value={info.minOrderAmount}
              onChange={e => setInfo(i => ({ ...i, minOrderAmount: Number(e.target.value) }))}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-orange-400" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-gray-600">Thời gian chuẩn bị (phút)</label>
            <input type="number" min={0} value={info.prepTimeMinutes}
              onChange={e => setInfo(i => ({ ...i, prepTimeMinutes: Number(e.target.value) }))}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-orange-400" />
          </div>
          <div className="sm:col-span-2">
            <label className="mb-1 block text-xs font-medium text-gray-600">Mô tả</label>
            <textarea value={info.description} onChange={e => setInfo(i => ({ ...i, description: e.target.value }))}
              rows={3} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-orange-400" />
          </div>
        </div>
        <button onClick={() => infoMut.mutate()} disabled={infoMut.isPending || !info.name.trim()}
          className="mt-4 rounded-lg bg-orange-500 px-5 py-2 text-sm font-medium text-white hover:bg-orange-600 disabled:opacity-50">
          {infoMut.isPending ? 'Đang lưu...' : 'Lưu thông tin'}
        </button>
      </div>

      {/* Form giờ mở cửa */}
      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
        <h2 className="mb-4 font-semibold text-gray-800">Giờ mở cửa</h2>
        <div className="space-y-2">
          {hours.map(h => (
            <div key={h.dayOfWeek} className="flex items-center gap-4">
              <span className="w-20 text-sm font-medium text-gray-600">{DAY_NAMES[h.dayOfWeek]}</span>
              <input type="time" value={h.openTime} onChange={e => setHour(h.dayOfWeek, 'openTime', e.target.value)}
                className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm outline-none focus:border-orange-400" />
              <span className="text-gray-400">–</span>
              <input type="time" value={h.closeTime} onChange={e => setHour(h.dayOfWeek, 'closeTime', e.target.value)}
                className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm outline-none focus:border-orange-400" />
            </div>
          ))}
        </div>
        <button onClick={() => hoursMut.mutate()} disabled={hoursMut.isPending}
          className="mt-4 rounded-lg bg-orange-500 px-5 py-2 text-sm font-medium text-white hover:bg-orange-600 disabled:opacity-50">
          {hoursMut.isPending ? 'Đang lưu...' : 'Lưu giờ mở cửa'}
        </button>
      </div>
    </div>
  )
}
