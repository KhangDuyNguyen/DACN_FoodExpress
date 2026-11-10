import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import {
  fetchCategories, createCategory, updateCategory, deleteCategory,
  fetchItems, createItem, updateItem, deleteItem, patchAvailability,
} from '../../api/merchant'
import type { CategoryDTO, CategoryRequest, MenuItemRequest, MenuItemRow } from '../../api/merchant'
import { useBranch } from './MerchantBranchContext'

export default function MenuManagement() {
  const { selectedBranch } = useBranch()
  const navigate = useNavigate()
  const qc = useQueryClient()

  // Nếu chưa chọn chi nhánh, redirect về trang chọn
  if (!selectedBranch) {
    return (
      <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4 text-gray-500">
        <p className="text-4xl">🏪</p>
        <p className="font-medium">Chưa chọn chi nhánh</p>
        <button
          onClick={() => navigate('/merchant/branch')}
          className="rounded-lg bg-orange-500 px-5 py-2 text-sm font-medium text-white hover:bg-orange-600"
        >
          Chọn chi nhánh
        </button>
      </div>
    )
  }

  const rid = selectedBranch.id

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">Quản lý Menu</h1>
          <p className="mt-0.5 text-sm text-gray-500">🏪 {selectedBranch.name}</p>
        </div>
        <button
          onClick={() => navigate('/merchant/branch')}
          className="rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50"
        >
          Đổi chi nhánh
        </button>
      </div>

      <CategoriesSection rid={rid} qc={qc} />
      <ItemsSection rid={rid} qc={qc} />
    </div>
  )
}

// ── Quản lý nhóm món ─────────────────────────────────────────────────────────

function CategoriesSection({ rid, qc }: { rid: number; qc: ReturnType<typeof useQueryClient> }) {
  const [showForm, setShowForm] = useState(false)
  const [editTarget, setEditTarget] = useState<CategoryDTO | null>(null)
  const [formName, setFormName] = useState('')
  const [formSort, setFormSort] = useState(0)

  const { data: cats = [], isLoading } = useQuery({
    queryKey: ['merchantCategories', rid],
    queryFn: () => fetchCategories(rid),
  })

  const createMut = useMutation({
    mutationFn: (data: CategoryRequest) => createCategory(rid, data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['merchantCategories', rid] }); resetForm() },
  })

  const updateMut = useMutation({
    mutationFn: ({ id, data }: { id: number; data: CategoryRequest }) => updateCategory(rid, id, data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['merchantCategories', rid] }); resetForm() },
  })

  const deleteMut = useMutation({
    mutationFn: (id: number) => deleteCategory(rid, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['merchantCategories', rid] }),
  })

  const resetForm = () => { setShowForm(false); setEditTarget(null); setFormName(''); setFormSort(0) }

  const handleEdit = (cat: CategoryDTO) => {
    setEditTarget(cat); setFormName(cat.name); setFormSort(cat.sortOrder); setShowForm(true)
  }

  const handleSubmit = () => {
    if (!formName.trim()) return
    if (editTarget) updateMut.mutate({ id: editTarget.id, data: { name: formName, sortOrder: formSort } })
    else createMut.mutate({ name: formName, sortOrder: formSort })
  }

  return (
    <section>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-lg font-semibold text-gray-800">Nhóm món</h2>
        <button
          onClick={() => { setEditTarget(null); setFormName(''); setFormSort(cats.length); setShowForm(true) }}
          className="rounded-lg bg-orange-500 px-4 py-1.5 text-sm font-medium text-white hover:bg-orange-600"
        >
          + Thêm nhóm
        </button>
      </div>

      {/* Form thêm/sửa */}
      {showForm && (
        <div className="mb-4 rounded-xl border border-orange-200 bg-orange-50 p-4">
          <p className="mb-3 font-medium text-gray-700">{editTarget ? 'Sửa nhóm món' : 'Thêm nhóm mới'}</p>
          <div className="flex gap-3">
            <input
              value={formName} onChange={e => setFormName(e.target.value)}
              placeholder="Tên nhóm (VD: Món chính)"
              className="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-orange-400"
            />
            <input
              type="number" value={formSort} onChange={e => setFormSort(Number(e.target.value))}
              placeholder="Thứ tự" className="w-24 rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-orange-400"
            />
            <button onClick={handleSubmit} disabled={createMut.isPending || updateMut.isPending}
              className="rounded-lg bg-orange-500 px-4 py-2 text-sm font-medium text-white hover:bg-orange-600 disabled:opacity-50">
              Lưu
            </button>
            <button onClick={resetForm} className="rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50">
              Hủy
            </button>
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="space-y-2">{[...Array(3)].map((_, i) => <div key={i} className="h-12 animate-pulse rounded-lg bg-gray-200" />)}</div>
      ) : cats.length === 0 ? (
        <p className="text-sm text-gray-400">Chưa có nhóm món nào</p>
      ) : (
        <div className="space-y-2">
          {cats.map(cat => (
            <div key={cat.id} className="flex items-center justify-between rounded-lg border border-gray-200 bg-white px-4 py-3">
              <div>
                <span className="font-medium text-gray-800">{cat.name}</span>
                <span className="ml-2 text-xs text-gray-400">thứ tự: {cat.sortOrder}</span>
              </div>
              <div className="flex gap-2">
                <button onClick={() => handleEdit(cat)}
                  className="rounded px-3 py-1 text-sm text-blue-600 hover:bg-blue-50">Sửa</button>
                <button onClick={() => confirm(`Xóa nhóm "${cat.name}"? Tất cả món trong nhóm cũng bị xóa.`) && deleteMut.mutate(cat.id)}
                  className="rounded px-3 py-1 text-sm text-red-500 hover:bg-red-50">Xóa</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

// ── Quản lý món ăn ───────────────────────────────────────────────────────────

function ItemsSection({ rid, qc }: { rid: number; qc: ReturnType<typeof useQueryClient> }) {
  const [showForm, setShowForm] = useState(false)
  const [editTarget, setEditTarget] = useState<MenuItemRow | null>(null)

  const { data: cats = [] } = useQuery({ queryKey: ['merchantCategories', rid], queryFn: () => fetchCategories(rid) })
  const { data: items = [], isLoading } = useQuery({ queryKey: ['merchantItems', rid], queryFn: () => fetchItems(rid) })

  const createMut = useMutation({
    mutationFn: (data: MenuItemRequest) => createItem(rid, data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['merchantItems', rid] }); setShowForm(false) },
  })
  const updateMut = useMutation({
    mutationFn: ({ id, data }: { id: number; data: MenuItemRequest }) => updateItem(rid, id, data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['merchantItems', rid] }); setShowForm(false); setEditTarget(null) },
  })
  const deleteMut = useMutation({
    mutationFn: (id: number) => deleteItem(rid, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['merchantItems', rid] }),
  })
  const availMut = useMutation({
    mutationFn: ({ id, avail }: { id: number; avail: boolean }) => patchAvailability(id, avail),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['merchantItems', rid] }),
  })

  const handleEdit = (item: MenuItemRow) => { setEditTarget(item); setShowForm(true) }

  return (
    <section>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-lg font-semibold text-gray-800">Món ăn</h2>
        <button onClick={() => { setEditTarget(null); setShowForm(true) }}
          className="rounded-lg bg-orange-500 px-4 py-1.5 text-sm font-medium text-white hover:bg-orange-600">
          + Thêm món
        </button>
      </div>

      {showForm && (
        <ItemForm
          categories={cats}
          initial={editTarget}
          onSubmit={(data) => {
            if (editTarget) updateMut.mutate({ id: editTarget.id, data })
            else createMut.mutate(data)
          }}
          onCancel={() => { setShowForm(false); setEditTarget(null) }}
          loading={createMut.isPending || updateMut.isPending}
        />
      )}

      {isLoading ? (
        <div className="space-y-2">{[...Array(4)].map((_, i) => <div key={i} className="h-16 animate-pulse rounded-lg bg-gray-200" />)}</div>
      ) : items.length === 0 ? (
        <p className="text-sm text-gray-400">Chưa có món ăn nào</p>
      ) : (
        <div className="overflow-hidden rounded-xl border border-gray-200 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-left text-xs text-gray-500">
              <tr>
                <th className="px-4 py-3">Tên món</th>
                <th className="px-4 py-3">Nhóm</th>
                <th className="px-4 py-3 text-right">Giá</th>
                <th className="px-4 py-3 text-center">Còn món</th>
                <th className="px-4 py-3 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {items.map(item => {
                const avail = item.is_available === true || item.is_available === 1
                return (
                  <tr key={item.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        {item.image_url && (
                          <img src={item.image_url} alt="" className="h-10 w-10 rounded-lg object-cover" />
                        )}
                        <div>
                          <p className="font-medium text-gray-800">{item.name}</p>
                          {item.description && <p className="text-xs text-gray-400 line-clamp-1">{item.description}</p>}
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-gray-500">{item.category_name}</td>
                    <td className="px-4 py-3 text-right font-semibold text-orange-600">
                      {item.price.toLocaleString('vi-VN')}đ
                    </td>
                    <td className="px-4 py-3 text-center">
                      <button
                        onClick={() => availMut.mutate({ id: item.id, avail: !avail })}
                        className={`relative inline-flex h-6 w-11 rounded-full transition-colors ${avail ? 'bg-green-500' : 'bg-gray-300'}`}
                      >
                        <span className={`inline-block h-4 w-4 translate-y-1 rounded-full bg-white shadow transition-transform ${avail ? 'translate-x-6' : 'translate-x-1'}`} />
                      </button>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <button onClick={() => handleEdit(item)} className="mr-2 text-blue-600 hover:underline">Sửa</button>
                      <button onClick={() => confirm(`Xóa món "${item.name}"?`) && deleteMut.mutate(item.id)}
                        className="text-red-500 hover:underline">Xóa</button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

// ── Form thêm/sửa món ────────────────────────────────────────────────────────

function ItemForm({
  categories, initial, onSubmit, onCancel, loading,
}: {
  categories: CategoryDTO[]
  initial: MenuItemRow | null
  onSubmit: (data: MenuItemRequest) => void
  onCancel: () => void
  loading: boolean
}) {
  const [form, setForm] = useState<MenuItemRequest>({
    categoryId: initial?.category_id ?? categories[0]?.id ?? 0,
    name: initial?.name ?? '',
    description: initial?.description ?? '',
    price: initial?.price ?? 0,
    imageUrl: initial?.image_url ?? '',
    isAvailable: initial ? (initial.is_available === true || initial.is_available === 1) : true,
    sortOrder: initial?.sort_order ?? 0,
  })

  const set = (k: keyof MenuItemRequest, v: unknown) => setForm(f => ({ ...f, [k]: v }))

  return (
    <div className="mb-4 rounded-xl border border-orange-200 bg-orange-50 p-5">
      <p className="mb-4 font-semibold text-gray-700">{initial ? 'Sửa món ăn' : 'Thêm món mới'}</p>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div>
          <label className="mb-1 block text-xs font-medium text-gray-600">Tên món *</label>
          <input value={form.name} onChange={e => set('name', e.target.value)}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-orange-400" />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-gray-600">Nhóm món *</label>
          <select value={form.categoryId} onChange={e => set('categoryId', Number(e.target.value))}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-orange-400">
            {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-gray-600">Giá (₫) *</label>
          <input type="number" min={0} value={form.price} onChange={e => set('price', Number(e.target.value))}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-orange-400" />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-gray-600">URL ảnh</label>
          <input value={form.imageUrl ?? ''} onChange={e => set('imageUrl', e.target.value)}
            placeholder="https://..."
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-orange-400" />
        </div>
        <div className="sm:col-span-2">
          <label className="mb-1 block text-xs font-medium text-gray-600">Mô tả</label>
          <textarea value={form.description ?? ''} onChange={e => set('description', e.target.value)} rows={2}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-orange-400" />
        </div>
        <div className="flex items-center gap-3">
          <label className="text-sm text-gray-600">Còn món:</label>
          <button type="button" onClick={() => set('isAvailable', !form.isAvailable)}
            className={`relative inline-flex h-6 w-11 rounded-full transition-colors ${form.isAvailable ? 'bg-green-500' : 'bg-gray-300'}`}>
            <span className={`inline-block h-4 w-4 translate-y-1 rounded-full bg-white shadow transition-transform ${form.isAvailable ? 'translate-x-6' : 'translate-x-1'}`} />
          </button>
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-gray-600">Thứ tự</label>
          <input type="number" min={0} value={form.sortOrder} onChange={e => set('sortOrder', Number(e.target.value))}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-orange-400" />
        </div>
      </div>
      <div className="mt-4 flex gap-3">
        <button onClick={() => onSubmit(form)} disabled={loading || !form.name.trim() || !form.categoryId}
          className="rounded-lg bg-orange-500 px-5 py-2 text-sm font-medium text-white hover:bg-orange-600 disabled:opacity-50">
          {loading ? 'Đang lưu...' : 'Lưu'}
        </button>
        <button onClick={onCancel}
          className="rounded-lg border border-gray-300 px-5 py-2 text-sm text-gray-600 hover:bg-gray-50">
          Hủy
        </button>
      </div>
    </div>
  )
}
