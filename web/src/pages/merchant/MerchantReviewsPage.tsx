import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getMerchantReviews, replyReview } from '../../api/reviews'
import type { Review } from '../../api/reviews'
import { useBranch } from './MerchantBranchContext'

function StarDisplay({ rating }: { rating: number }) {
  return (
    <span>
      {'★'.repeat(rating)}<span className="text-gray-300">{'★'.repeat(5 - rating)}</span>
    </span>
  )
}

function ReplyModal({
  review,
  onClose,
}: {
  review: Review
  onClose: () => void
}) {
  const qc = useQueryClient()
  const [content, setContent] = useState('')

  const replyMut = useMutation({
    mutationFn: () => replyReview(review.id, content.trim()),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['merchant-reviews'] })
    },
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-bold text-gray-900">Trả lời đánh giá</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl">✕</button>
        </div>

        {/* Hiển thị review gốc */}
        <div className="mb-4 rounded-lg bg-gray-50 p-3 text-sm text-gray-700">
          <div className="flex items-center gap-2 mb-1">
            <span className="font-medium">{review.customerName}</span>
            <span className="text-yellow-500"><StarDisplay rating={review.restaurantRating} /></span>
          </div>
          {review.comment && <p className="italic text-gray-600">"{review.comment}"</p>}
        </div>

        {replyMut.isSuccess ? (
          <div className="text-center py-4 space-y-3">
            <p className="text-4xl">✅</p>
            <p className="font-semibold text-gray-800">Đã gửi phản hồi!</p>
            <button onClick={onClose}
              className="rounded-lg bg-orange-500 px-6 py-2 text-sm font-medium text-white hover:bg-orange-600">
              Đóng
            </button>
          </div>
        ) : (
          <div className="space-y-3">
            <textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              rows={4}
              maxLength={1000}
              placeholder="Nhập phản hồi của bạn..."
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-orange-400"
            />
            {replyMut.isError && (
              <p className="text-sm text-red-500">Có lỗi xảy ra. Vui lòng thử lại.</p>
            )}
            <div className="flex gap-3">
              <button
                onClick={() => replyMut.mutate()}
                disabled={!content.trim() || replyMut.isPending}
                className="flex-1 rounded-lg bg-orange-500 py-2.5 text-sm font-semibold text-white hover:bg-orange-600 disabled:opacity-50"
              >
                {replyMut.isPending ? 'Đang gửi...' : 'Gửi phản hồi'}
              </button>
              <button onClick={onClose}
                className="rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50">
                Hủy
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

export default function MerchantReviewsPage() {
  const { selectedBranch } = useBranch()
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [replyTarget, setReplyTarget] = useState<Review | null>(null)

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

  const { data, isLoading, isError } = useQuery({
    queryKey: ['merchant-reviews', selectedBranch.id, page],
    queryFn: () => getMerchantReviews(selectedBranch.id, page, 10),
  })

  const reviews = data?.items ?? []
  const totalPages = data?.totalPages ?? 0

  // Thống kê
  const avgRating = reviews.length > 0
    ? (reviews.reduce((sum, r) => sum + r.restaurantRating, 0) / reviews.length).toFixed(1)
    : '—'
  const replied = reviews.filter((r) => r.reply !== null).length
  const unReplied = reviews.length - replied

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">Đánh giá</h1>
          <p className="mt-0.5 text-sm text-gray-500">🏪 {selectedBranch.name}</p>
        </div>
        <button onClick={() => navigate('/merchant/branch')}
          className="rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50">
          Đổi chi nhánh
        </button>
      </div>

      {/* Stats */}
      {!isLoading && reviews.length > 0 && (
        <div className="grid grid-cols-3 gap-4">
          {[
            { label: 'Rating trung bình', value: `⭐ ${avgRating}`, color: 'text-yellow-600' },
            { label: 'Đã trả lời', value: `✅ ${replied}`, color: 'text-green-600' },
            { label: 'Chưa trả lời', value: `💬 ${unReplied}`, color: 'text-orange-600' },
          ].map((s) => (
            <div key={s.label} className="rounded-xl border border-gray-200 bg-white p-4 text-center shadow-sm">
              <p className={`text-2xl font-bold ${s.color}`}>{s.value}</p>
              <p className="mt-1 text-xs text-gray-500">{s.label}</p>
            </div>
          ))}
        </div>
      )}

      {/* Review list */}
      {isLoading && (
        <div className="space-y-3">
          {[...Array(3)].map((_, i) => (
            <div key={i} className="h-24 animate-pulse rounded-xl bg-gray-200" />
          ))}
        </div>
      )}

      {isError && (
        <div className="rounded-lg bg-red-50 p-4 text-center text-red-600 text-sm">
          Không tải được đánh giá. Vui lòng thử lại.
        </div>
      )}

      {!isLoading && !isError && reviews.length === 0 && (
        <div className="rounded-xl border border-dashed border-gray-300 bg-white py-16 text-center text-gray-400">
          <p className="text-4xl">⭐</p>
          <p className="mt-2 font-medium">Chưa có đánh giá nào</p>
        </div>
      )}

      <div className="space-y-3">
        {reviews.map((r) => (
          <div key={r.id} className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
            {/* Review info */}
            <div className="flex items-start justify-between gap-3">
              <div className="flex-1">
                <div className="flex items-center gap-2">
                  <div className="flex h-8 w-8 items-center justify-center rounded-full bg-orange-100 text-sm font-bold text-orange-600">
                    {r.customerName?.charAt(0) ?? '?'}
                  </div>
                  <div>
                    <p className="font-medium text-gray-800">{r.customerName}</p>
                    <p className="text-xs text-gray-400">
                      {new Date(r.createdAt).toLocaleDateString('vi-VN')}
                    </p>
                  </div>
                </div>

                <div className="mt-2 flex items-center gap-3 text-sm">
                  <span className="text-yellow-500 text-base"><StarDisplay rating={r.restaurantRating} /></span>
                  {r.driverRating && (
                    <span className="text-gray-500">🛵 <StarDisplay rating={r.driverRating} /></span>
                  )}
                </div>

                {r.comment && (
                  <p className="mt-2 text-sm text-gray-600 italic">"{r.comment}"</p>
                )}
              </div>

              {/* Nút trả lời */}
              {!r.reply && (
                <button
                  onClick={() => setReplyTarget(r)}
                  className="flex-shrink-0 rounded-lg border border-orange-400 bg-orange-50 px-3 py-1.5 text-xs font-medium text-orange-600 hover:bg-orange-100 transition"
                >
                  💬 Trả lời
                </button>
              )}
            </div>

            {/* Phản hồi đã có */}
            {r.reply && (
              <div className="mt-3 rounded-lg bg-gray-50 p-3 text-sm border-l-4 border-orange-300">
                <p className="font-medium text-gray-700">💬 Phản hồi từ quán:</p>
                <p className="mt-1 text-gray-600">{r.reply.content}</p>
                <p className="mt-1 text-xs text-gray-400">
                  {new Date(r.reply.createdAt).toLocaleDateString('vi-VN')}
                </p>
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 text-sm">
          <button onClick={() => setPage(page - 1)} disabled={page === 0}
            className="rounded-lg border border-gray-300 bg-white px-4 py-2 hover:bg-gray-50 disabled:opacity-40">
            ← Trước
          </button>
          <span className="text-gray-600">Trang {page + 1} / {totalPages}</span>
          <button onClick={() => setPage(page + 1)} disabled={page + 1 >= totalPages}
            className="rounded-lg border border-gray-300 bg-white px-4 py-2 hover:bg-gray-50 disabled:opacity-40">
            Sau →
          </button>
        </div>
      )}

      {/* Reply modal */}
      {replyTarget && (
        <ReplyModal review={replyTarget} onClose={() => setReplyTarget(null)} />
      )}
    </div>
  )
}
