import { Link } from 'react-router-dom'
import { NotificationList } from '../../components/notifications/NotificationCenter'

export default function NotificationsPage() {
  return <section className="mx-auto max-w-2xl rounded-xl border bg-white p-5">
    <Link to="/" className="text-sm text-orange-600">← Quay lại cửa hàng</Link>
    <h1 className="my-4 text-2xl font-bold">Thông báo</h1>
    <NotificationList />
  </section>
}
