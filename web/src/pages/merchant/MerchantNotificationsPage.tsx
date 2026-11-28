import { Link } from 'react-router-dom'
import { NotificationList } from '../../components/notifications/NotificationCenter'

export default function MerchantNotificationsPage() {
  return <section className="merchant-notification mx-auto max-w-2xl rounded-xl border border-gray-200 bg-white p-4 sm:p-5">
    <Link className="text-sm text-blue-700" to="/merchant">← Quay lại tổng quan quán</Link>
    <h1 className="my-4 text-2xl font-bold">Thông báo</h1>
    <NotificationList />
  </section>
}
