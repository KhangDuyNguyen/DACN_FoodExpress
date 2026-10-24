import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { register as registerApi } from '../../api/auth'
import { getApiError } from '../../api/apiError'
import { homePathForRole } from '../../auth/homePath'
import { useAuth } from '../../auth/useAuth'

// Quy tac giong backend (RegisterRequest.java)
const schema = z.object({
  fullName: z.string().trim().min(1, 'Vui lòng nhập họ tên').max(100, 'Họ tên tối đa 100 ký tự'),
  phone: z.string().trim().regex(/^0\d{9}$/, 'Số điện thoại phải gồm 10 chữ số, bắt đầu bằng 0'),
  email: z.union([z.literal(''), z.email('Email không hợp lệ')]),
  password: z.string().min(8, 'Mật khẩu tối thiểu 8 ký tự').max(72, 'Mật khẩu tối đa 72 ký tự'),
  role: z.enum(['CUSTOMER', 'MERCHANT', 'DRIVER']),
})

type FormValues = z.infer<typeof schema>

const fieldClass = 'mt-1 w-full rounded border px-3 py-2 focus:border-orange-500 focus:outline-none'

export default function Register() {
  const auth = useAuth()
  const navigate = useNavigate()
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: '', phone: '', email: '', password: '', role: 'CUSTOMER' },
  })

  const mutation = useMutation({
    mutationFn: registerApi,
    onSuccess: (res) => {
      // Dang ky xong backend tra token luon, coi nhu da dang nhap
      auth.login(res)
      navigate(homePathForRole(res.user.role), { replace: true })
    },
    onError: (error) => {
      // Loi 400 tung o tu backend: hien duoi dung o nhap
      const { fieldErrors } = getApiError(error)
      for (const [field, message] of Object.entries(fieldErrors)) {
        setError(field as keyof FormValues, { message })
      }
    },
  })

  function onSubmit(values: FormValues) {
    mutation.mutate({ ...values, email: values.email || undefined })
  }

  return (
    <div className="mx-auto mt-10 w-full max-w-md rounded-lg bg-white p-8 shadow">
      <h1 className="text-center text-2xl font-semibold">Đăng ký</h1>

      <form onSubmit={handleSubmit(onSubmit)} className="mt-6 space-y-4" noValidate>
        <div>
          <label className="block text-sm font-medium text-gray-700">Họ tên</label>
          <input {...register('fullName')} className={fieldClass} autoComplete="name" />
          {errors.fullName && <p className="mt-1 text-sm text-red-600">{errors.fullName.message}</p>}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700">Số điện thoại</label>
          <input {...register('phone')} className={fieldClass} autoComplete="tel" inputMode="numeric" />
          {errors.phone && <p className="mt-1 text-sm text-red-600">{errors.phone.message}</p>}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700">Email (không bắt buộc)</label>
          <input type="email" {...register('email')} className={fieldClass} autoComplete="email" />
          {errors.email && <p className="mt-1 text-sm text-red-600">{errors.email.message}</p>}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700">Mật khẩu</label>
          <input type="password" {...register('password')} className={fieldClass} autoComplete="new-password" />
          {errors.password && <p className="mt-1 text-sm text-red-600">{errors.password.message}</p>}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700">Bạn là</label>
          <select {...register('role')} className={fieldClass}>
            <option value="CUSTOMER">Khách hàng</option>
            <option value="MERCHANT">Chủ quán</option>
            <option value="DRIVER">Tài xế</option>
          </select>
          {errors.role && <p className="mt-1 text-sm text-red-600">{errors.role.message}</p>}
        </div>

        {mutation.isError && (
          <p className="rounded bg-red-50 p-2 text-sm text-red-700">{getApiError(mutation.error).message}</p>
        )}

        <button
          type="submit"
          disabled={mutation.isPending}
          className="w-full rounded bg-orange-600 py-2 font-medium text-white hover:bg-orange-700 disabled:opacity-60"
        >
          {mutation.isPending ? 'Đang đăng ký...' : 'Đăng ký'}
        </button>
      </form>

      <p className="mt-4 text-center text-sm text-gray-600">
        Đã có tài khoản?{' '}
        <Link to="/login" className="text-orange-600 hover:underline">
          Đăng nhập
        </Link>
      </p>
    </div>
  )
}
