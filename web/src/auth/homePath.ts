import type { Role } from '../api/auth'

// Trang chuyen den sau khi dang nhap, theo vai tro. Tai xe dung app nen ve trang chu.
export function homePathForRole(role: Role): string {
  if (role === 'ADMIN') return '/admin'
  if (role === 'MERCHANT') return '/merchant'
  return '/'
}

// Chi chap nhan duong dan noi bo (bat dau bang "/", khong phai "//"),
// de tranh bi lua chuyen sang trang web khac qua ?redirect=
export function safeRedirect(redirect: string | null): string | null {
  if (redirect && redirect.startsWith('/') && !redirect.startsWith('//')) return redirect
  return null
}
