import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { z } from 'zod'
import {
  addressesQueryKey,
  createAddress,
  deleteAddress,
  fetchMyAddresses,
  setDefaultAddress,
  updateAddress,
  type Address,
  type AddressBody,
} from '../../api/addresses'
import { getApiError } from '../../api/apiError'
import { safeRedirect } from '../../auth/homePath'
import { useAuth } from '../../auth/useAuth'
import LocationPicker, { type LatLng } from '../../components/map/LocationPicker'

// Quy tac giong backend (AddressRequest.java). Toa do lay tu ban do nen kiem tra rieng.
const schema = z.object({
  label: z.string().trim().max(50, 'Tên gợi nhớ tối đa 50 ký tự'),
  receiverName: z.string().trim().min(1, 'Vui lòng nhập tên người nhận').max(100, 'Tên người nhận tối đa 100 ký tự'),
  receiverPhone: z.string().trim().regex(/^0\d{9}$/, 'Số điện thoại phải gồm 10 chữ số, bắt đầu bằng 0'),
  addressLine: z.string().trim().min(1, 'Vui lòng nhập địa chỉ').max(255, 'Địa chỉ tối đa 255 ký tự'),
  isDefault: z.boolean(),
})

type FormValues = z.infer<typeof schema>

const fieldClass = 'mt-1 w-full rounded border px-3 py-2 focus:border-orange-500 focus:outline-none'

export default function Addresses() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  // Vao tu trang thanh toan (?redirect=/checkout) thi luu xong quay lai do
  const redirect = safeRedirect(searchParams.get('redirect'))
  const queryClient = useQueryClient()
  const { data: addresses = [], isLoading } = useQuery({ queryKey: addressesQueryKey, queryFn: fetchMyAddresses })

  // null = dang dong form, 'new' = them dia chi moi, so = id dia chi dang sua
  const [editing, setEditing] = useState<number | 'new' | null>(null)
  const [point, setPoint] = useState<LatLng | null>(null)
  const [pointError, setPointError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { label: '', receiverName: '', receiverPhone: '', addressLine: '', isDefault: false },
  })

  function openNew() {
    reset({
      label: '',
      receiverName: user?.fullName ?? '',
      receiverPhone: user?.phone ?? '',
      addressLine: '',
      isDefault: addresses.length === 0,
    })
    setPoint(null)
    setPointError(null)
    setEditing('new')
  }

  function openEdit(address: Address) {
    reset({
      label: address.label ?? '',
      receiverName: address.receiverName,
      receiverPhone: address.receiverPhone,
      addressLine: address.addressLine,
      isDefault: address.isDefault,
    })
    setPoint({ lat: address.lat, lng: address.lng })
    setPointError(null)
    setEditing(address.id)
  }

  const refresh = () => queryClient.invalidateQueries({ queryKey: addressesQueryKey })

  const saveMutation = useMutation({
    mutationFn: (body: AddressBody) => (typeof editing === 'number' ? updateAddress(editing, body) : createAddress(body)),
    onSuccess: () => {
      refresh()
      setEditing(null)
      if (redirect) navigate(redirect)
    },
    onError: (error) => {
      // Loi 400 tung o tu backend: hien duoi dung o nhap
      const { fieldErrors } = getApiError(error)
      for (const [field, message] of Object.entries(fieldErrors)) {
        if (field === 'lat' || field === 'lng') setPointError(message)
        else if (field in schema.shape) setError(field as keyof FormValues, { message })
      }
    },
  })
  const defaultMutation = useMutation({ mutationFn: setDefaultAddress, onSuccess: refresh })
  const deleteMutation = useMutation({ mutationFn: deleteAddress, onSuccess: refresh })
  const listError = defaultMutation.error ?? deleteMutation.error

  function onSubmit(values: FormValues) {
    if (!point) {
      setPointError('Vui lòng chọn vị trí trên bản đồ')
      return
    }
    saveMutation.mutate({ ...values, label: values.label || undefined, lat: point.lat, lng: point.lng })
  }

  function handleDelete(address: Address) {
    if (window.confirm(`Xóa địa chỉ "${address.label ?? address.addressLine}"?`)) {
      deleteMutation.mutate(address.id)
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="flex items-center justify-between gap-2">
        <h1 className="text-2xl font-bold text-gray-900">Sổ địa chỉ</h1>
        {editing === null && (
          <button onClick={openNew} className="rounded-lg bg-orange-600 px-4 py-2 text-sm font-medium text-white hover:bg-orange-700">
            + Thêm địa chỉ
          </button>
        )}
      </div>
      {redirect && <p className="text-sm text-gray-600">Lưu địa chỉ xong sẽ quay lại trang thanh toán.</p>}

      {isLoading && <p className="text-gray-500">Đang tải...</p>}
      {!isLoading && addresses.length === 0 && editing === null && (
        <p className="rounded-xl border border-dashed p-6 text-center text-gray-500">Bạn chưa có địa chỉ giao hàng nào.</p>
      )}

      <ul className="space-y-3">
        {addresses.map((a) => (
          <li key={a.id} className="rounded-xl border border-gray-200 bg-white p-4">
            <div className="flex items-start justify-between gap-2">
              <div>
                <p className="font-medium text-gray-900">
                  {a.label ?? 'Địa chỉ'}
                  {a.isDefault && (
                    <span className="ml-2 rounded-full bg-orange-100 px-2 py-0.5 text-xs font-semibold text-orange-700">Mặc định</span>
                  )}
                </p>
                <p className="text-sm text-gray-600">
                  {a.receiverName} · {a.receiverPhone}
                </p>
                <p className="text-sm text-gray-500">{a.addressLine}</p>
              </div>
              <div className="flex flex-shrink-0 flex-col items-end gap-1 text-sm">
                <button onClick={() => openEdit(a)} className="text-orange-600 hover:underline">
                  Sửa
                </button>
                {!a.isDefault && (
                  <button onClick={() => defaultMutation.mutate(a.id)} className="text-gray-600 hover:underline">
                    Đặt mặc định
                  </button>
                )}
                <button onClick={() => handleDelete(a)} className="text-red-600 hover:underline">
                  Xóa
                </button>
              </div>
            </div>
          </li>
        ))}
      </ul>
      {listError && <p className="text-sm text-red-600">{getApiError(listError).message}</p>}

      {editing !== null && (
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-3 rounded-xl border border-gray-200 bg-white p-4">
          <p className="font-medium text-gray-800">{editing === 'new' ? 'Thêm địa chỉ' : 'Sửa địa chỉ'}</p>

          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <label className="block text-sm text-gray-700">Tên gợi nhớ (không bắt buộc)</label>
              <input {...register('label')} className={fieldClass} placeholder="Nhà riêng, Công ty..." />
              {errors.label && <p className="mt-1 text-sm text-red-600">{errors.label.message}</p>}
            </div>
            <div>
              <label className="block text-sm text-gray-700">Người nhận</label>
              <input {...register('receiverName')} className={fieldClass} autoComplete="name" />
              {errors.receiverName && <p className="mt-1 text-sm text-red-600">{errors.receiverName.message}</p>}
            </div>
            <div>
              <label className="block text-sm text-gray-700">Số điện thoại</label>
              <input {...register('receiverPhone')} className={fieldClass} autoComplete="tel" inputMode="numeric" />
              {errors.receiverPhone && <p className="mt-1 text-sm text-red-600">{errors.receiverPhone.message}</p>}
            </div>
            <div>
              <label className="block text-sm text-gray-700">Địa chỉ (số nhà, đường, phường, quận)</label>
              <input {...register('addressLine')} className={fieldClass} autoComplete="street-address" />
              {errors.addressLine && <p className="mt-1 text-sm text-red-600">{errors.addressLine.message}</p>}
            </div>
          </div>

          <div>
            <label className="mb-1 block text-sm text-gray-700">Vị trí giao hàng trên bản đồ (dùng để tính phí ship)</label>
            <LocationPicker
              value={point}
              onChange={(p) => {
                setPoint(p)
                setPointError(null)
              }}
            />
            {pointError && <p className="mt-1 text-sm text-red-600">{pointError}</p>}
          </div>

          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input type="checkbox" {...register('isDefault')} />
            Đặt làm địa chỉ mặc định
          </label>

          {saveMutation.isError && Object.keys(getApiError(saveMutation.error).fieldErrors).length === 0 && (
            <p className="rounded bg-red-50 p-2 text-sm text-red-700">{getApiError(saveMutation.error).message}</p>
          )}

          <div className="flex gap-2">
            <button
              type="submit"
              disabled={saveMutation.isPending}
              className="rounded-lg bg-orange-600 px-4 py-2 text-sm font-medium text-white hover:bg-orange-700 disabled:opacity-50"
            >
              {saveMutation.isPending ? 'Đang lưu...' : 'Lưu địa chỉ'}
            </button>
            <button type="button" onClick={() => setEditing(null)} className="rounded-lg border px-4 py-2 text-sm hover:bg-gray-50">
              Hủy
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
