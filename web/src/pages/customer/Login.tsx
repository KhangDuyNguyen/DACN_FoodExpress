import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { z } from 'zod'
import { login } from '../../api/auth'
import { getApiError } from '../../api/apiError'
import { homePathForRole, safeRedirect } from '../../auth/homePath'
import { useAuth } from '../../auth/useAuth'

const schema = z.object({
  identifier: z.string().trim().min(1, 'Vui lòng nhập số điện thoại hoặc email'),
  password: z.string().min(1, 'Vui lòng nhập mật khẩu'),
})

type FormValues = z.infer<typeof schema>

export default function Login() {
  const auth = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (res) => {
      auth.login(res)
      const target = safeRedirect(searchParams.get('redirect')) ?? homePathForRole(res.user.role)
      navigate(target, { replace: true })
    },
  })

  return (
    <div className="mx-auto mt-10 w-full max-w-md rounded-lg bg-white p-8 shadow">
      <h1 className="text-center text-2xl font-semibold">Đăng nhập</h1>

      <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="mt-6 space-y-4" noValidate>
        <div>
          <label className="block text-sm font-medium text-gray-700">Số điện thoại hoặc email</label>
          <input
            {...register('identifier')}
            className="mt-1 w-full rounded border px-3 py-2 focus:border-orange-500 focus:outline-none"
            autoComplete="username"
          />
          {errors.identifier && <p className="mt-1 text-sm text-red-600">{errors.identifier.message}</p>}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700">Mật khẩu</label>
          <input
            type="password"
            {...register('password')}
            className="mt-1 w-full rounded border px-3 py-2 focus:border-orange-500 focus:outline-none"
            autoComplete="current-password"
          />
          {errors.password && <p className="mt-1 text-sm text-red-600">{errors.password.message}</p>}
        </div>

        {mutation.isError && (
          <p className="rounded bg-red-50 p-2 text-sm text-red-700">{getApiError(mutation.error).message}</p>
        )}

        <button
          type="submit"
          disabled={mutation.isPending}
          className="w-full rounded bg-orange-600 py-2 font-medium text-white hover:bg-orange-700 disabled:opacity-60"
        >
          {mutation.isPending ? 'Đang đăng nhập...' : 'Đăng nhập'}
        </button>
      </form>

      <p className="mt-4 text-center text-sm text-gray-600">
        Chưa có tài khoản?{' '}
        <Link to="/register" className="text-orange-600 hover:underline">
          Đăng ký
        </Link>
      </p>
    </div>
  )
}
