import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { z } from 'zod'
import { getApiError } from '../../api/apiError'
import { changePassword, updateMe } from '../../api/auth'
import { useAuth } from '../../auth/useAuth'

// Quy tac giong backend (UpdateProfileRequest, ChangePasswordRequest) va giong trang dang ky
const profileSchema = z.object({
  fullName: z.string().trim().min(1, 'Vui lòng nhập họ tên').max(100, 'Họ tên tối đa 100 ký tự'),
  email: z.union([z.literal(''), z.email('Email không hợp lệ').max(150, 'Email tối đa 150 ký tự')]),
})

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Vui lòng nhập mật khẩu hiện tại'),
    newPassword: z.string().min(8, 'Mật khẩu tối thiểu 8 ký tự').max(72, 'Mật khẩu tối đa 72 ký tự'),
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    message: 'Mật khẩu nhập lại không khớp',
    path: ['confirmPassword'],
  })

type ProfileValues = z.infer<typeof profileSchema>
type PasswordValues = z.infer<typeof passwordSchema>

const fieldClass = 'mt-1 w-full rounded border px-3 py-2 focus:border-orange-500 focus:outline-none'

export default function Profile() {
  const { user } = useAuth() // ProtectedRoute da bao dam co user
  const queryClient = useQueryClient()

  const profileForm = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { fullName: user?.fullName ?? '', email: user?.email ?? '' },
  })
  const passwordForm = useForm<PasswordValues>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  })

  const profileMutation = useMutation({
    mutationFn: updateMe,
    onSuccess: (updated) => {
      // Ghi de thong tin dang nhap dang luu: ten tren thanh dau trang doi theo ngay
      queryClient.setQueryData(['me'], updated)
      profileForm.reset({ fullName: updated.fullName, email: updated.email ?? '' })
    },
    onError: (error) => {
      // Loi tung o tu server (vd email sai dinh dang) hien ngay duoi o do
      for (const [field, message] of Object.entries(getApiError(error).fieldErrors)) {
        if (field === 'fullName' || field === 'email') profileForm.setError(field, { message })
      }
    },
  })

  const passwordMutation = useMutation({
    mutationFn: changePassword,
    onSuccess: () => passwordForm.reset(),
    onError: (error) => {
      const { fieldErrors } = getApiError(error)
      if (fieldErrors.newPassword) passwordForm.setError('newPassword', { message: fieldErrors.newPassword })
    },
  })

  // Loi chung (khong gan voi o nao), vd "Email đã được đăng ký", "Mật khẩu hiện tại không đúng"
  function generalError(error: unknown) {
    const apiError = getApiError(error)
    return Object.keys(apiError.fieldErrors).length === 0 ? apiError.message : null
  }

  const profileErrors = profileForm.formState.errors
  const passwordErrors = passwordForm.formState.errors

  return (
    <div className="mx-auto max-w-xl space-y-4">
      <div className="flex items-center justify-between gap-2">
        <h1 className="text-2xl font-bold text-gray-900">Hồ sơ của tôi</h1>
        <Link to="/profile/addresses" className="text-sm text-orange-600 hover:underline">
          Sổ địa chỉ giao hàng →
        </Link>
      </div>

      <form
        onSubmit={profileForm.handleSubmit((values) => profileMutation.mutate(values))}
        className="space-y-3 rounded-xl border border-gray-200 bg-white p-4"
      >
        <p className="font-medium text-gray-800">Thông tin tài khoản</p>
        <div>
          <label className="block text-sm text-gray-700">Số điện thoại</label>
          <input value={user?.phone ?? ''} disabled className={`${fieldClass} bg-gray-100 text-gray-500`} />
          <p className="mt-1 text-xs text-gray-500">Dùng để đăng nhập nên không đổi được.</p>
        </div>
        <div>
          <label className="block text-sm text-gray-700">Họ tên</label>
          <input {...profileForm.register('fullName')} className={fieldClass} autoComplete="name" />
          {profileErrors.fullName && <p className="mt-1 text-sm text-red-600">{profileErrors.fullName.message}</p>}
        </div>
        <div>
          <label className="block text-sm text-gray-700">Email (không bắt buộc)</label>
          <input type="email" {...profileForm.register('email')} className={fieldClass} autoComplete="email" />
          {profileErrors.email && <p className="mt-1 text-sm text-red-600">{profileErrors.email.message}</p>}
        </div>
        {profileMutation.isError && generalError(profileMutation.error) && (
          <p className="text-sm text-red-600">{generalError(profileMutation.error)}</p>
        )}
        {profileMutation.isSuccess && !profileForm.formState.isDirty && (
          <p className="text-sm text-green-700">✓ Đã lưu thông tin</p>
        )}
        <button
          type="submit"
          disabled={profileMutation.isPending}
          className="rounded-lg bg-orange-600 px-4 py-2 text-sm font-medium text-white hover:bg-orange-700 disabled:opacity-50"
        >
          {profileMutation.isPending ? 'Đang lưu...' : 'Lưu thông tin'}
        </button>
      </form>

      <form
        onSubmit={passwordForm.handleSubmit((values) =>
          passwordMutation.mutate({ currentPassword: values.currentPassword, newPassword: values.newPassword }),
        )}
        className="space-y-3 rounded-xl border border-gray-200 bg-white p-4"
      >
        <p className="font-medium text-gray-800">Đổi mật khẩu</p>
        <div>
          <label className="block text-sm text-gray-700">Mật khẩu hiện tại</label>
          <input
            type="password"
            {...passwordForm.register('currentPassword')}
            className={fieldClass}
            autoComplete="current-password"
          />
          {passwordErrors.currentPassword && (
            <p className="mt-1 text-sm text-red-600">{passwordErrors.currentPassword.message}</p>
          )}
        </div>
        <div>
          <label className="block text-sm text-gray-700">Mật khẩu mới</label>
          <input
            type="password"
            {...passwordForm.register('newPassword')}
            className={fieldClass}
            autoComplete="new-password"
          />
          {passwordErrors.newPassword && (
            <p className="mt-1 text-sm text-red-600">{passwordErrors.newPassword.message}</p>
          )}
        </div>
        <div>
          <label className="block text-sm text-gray-700">Nhập lại mật khẩu mới</label>
          <input
            type="password"
            {...passwordForm.register('confirmPassword')}
            className={fieldClass}
            autoComplete="new-password"
          />
          {passwordErrors.confirmPassword && (
            <p className="mt-1 text-sm text-red-600">{passwordErrors.confirmPassword.message}</p>
          )}
        </div>
        {passwordMutation.isError && generalError(passwordMutation.error) && (
          <p className="text-sm text-red-600">{generalError(passwordMutation.error)}</p>
        )}
        {passwordMutation.isSuccess && <p className="text-sm text-green-700">✓ Đã đổi mật khẩu</p>}
        <button
          type="submit"
          disabled={passwordMutation.isPending}
          className="rounded-lg bg-orange-600 px-4 py-2 text-sm font-medium text-white hover:bg-orange-700 disabled:opacity-50"
        >
          {passwordMutation.isPending ? 'Đang đổi...' : 'Đổi mật khẩu'}
        </button>
      </form>
    </div>
  )
}
