import { useState } from 'react'
import type { MenuItemDTO, OptionGroupDTO } from '../../api/restaurants'

// Hộp chọn tùy chọn (size, topping...) trước khi thêm món vào giỏ (Khang, tuần 7).
// Chỉ giúp khách chọn cho đúng; server vẫn kiểm tra lại toàn bộ luật (OptionRules) và tự tính giá.

function vnd(amount: number) {
  return `${amount.toLocaleString('vi-VN')}đ`
}

// Giống server: nhóm bắt buộc chọn ít nhất 1 (hoặc minSelect nếu lớn hơn); nhóm không bắt buộc thì bỏ qua được
function minOf(group: OptionGroupDTO): number {
  if (!group.isRequired || group.options.length === 0) return 0
  return Math.max(group.minSelect, 1)
}

function hintOf(group: OptionGroupDTO): string {
  const min = minOf(group)
  if (min === group.maxSelect) return `Chọn ${min}`
  if (min === 0) return `Chọn tối đa ${group.maxSelect}`
  return `Chọn từ ${min} đến ${group.maxSelect}`
}

interface OptionPickerProps {
  item: MenuItemDTO
  busy: boolean // đang gửi lên server
  error: string | null // lỗi server trả về (vd tùy chọn vừa hết)
  onCancel: () => void
  onConfirm: (optionIds: number[]) => void
}

export default function OptionPicker({ item, busy, error, onCancel, onConfirm }: OptionPickerProps) {
  const [chosen, setChosen] = useState<number[]>([])

  const countIn = (group: OptionGroupDTO) => group.options.filter((o) => chosen.includes(o.id)).length

  function toggle(group: OptionGroupDTO, optionId: number) {
    const groupIds = group.options.map((o) => o.id)
    const inGroup = chosen.filter((id) => groupIds.includes(id))
    const others = chosen.filter((id) => !groupIds.includes(id))
    if (inGroup.includes(optionId)) {
      setChosen([...others, ...inGroup.filter((id) => id !== optionId)]) // bỏ chọn
    } else if (group.maxSelect === 1) {
      setChosen([...others, optionId]) // nhóm chỉ chọn 1: thay lựa chọn cũ
    } else if (inGroup.length < group.maxSelect) {
      setChosen([...chosen, optionId])
    }
  }

  // Nhóm còn thiếu (chưa đủ số mục tối thiểu) thì chưa cho thêm vào giỏ
  const missingGroup = item.optionGroups.find((g) => countIn(g) < minOf(g))
  const extra = item.optionGroups
    .flatMap((g) => g.options)
    .filter((o) => chosen.includes(o.id))
    .reduce((sum, o) => sum + o.extraPrice, 0)

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 sm:items-center" onClick={onCancel}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="option-picker-title"
        className="max-h-[85vh] w-full max-w-md overflow-y-auto rounded-t-2xl bg-white p-4 sm:rounded-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 id="option-picker-title" className="text-lg font-bold text-gray-900">
          {item.name}
        </h2>
        <p className="text-sm text-gray-500">Giá món {vnd(item.price)}</p>

        <div className="mt-4 space-y-4">
          {item.optionGroups.map((group) => {
            const full = countIn(group) >= group.maxSelect
            // Bắt buộc chọn đúng 1 thì dùng nút tròn (radio), còn lại dùng ô vuông (checkbox)
            const asRadio = group.maxSelect === 1 && minOf(group) === 1
            return (
              <fieldset key={group.id}>
                <legend className="text-sm font-medium text-gray-800">
                  {group.name}
                  {minOf(group) > 0 && <span className="ml-1 text-red-500">*</span>}
                  <span className="ml-2 text-xs font-normal text-gray-500">{hintOf(group)}</span>
                </legend>
                <div className="mt-2 space-y-1">
                  {group.options.map((option) => {
                    const checked = chosen.includes(option.id)
                    // Hết thì không chọn được; nhóm chọn nhiều đã đủ tối đa thì khóa các ô chưa chọn
                    const disabled = !option.isAvailable || (!checked && full && group.maxSelect > 1)
                    return (
                      <label
                        key={option.id}
                        className={`flex items-center gap-2 rounded-lg border px-3 py-2 text-sm ${
                          checked ? 'border-orange-500 bg-orange-50' : 'border-gray-200'
                        } ${disabled ? 'cursor-not-allowed text-gray-400' : 'cursor-pointer hover:border-orange-400'}`}
                      >
                        <input
                          type={asRadio ? 'radio' : 'checkbox'}
                          name={`option-group-${group.id}`}
                          checked={checked}
                          disabled={disabled}
                          onChange={() => toggle(group, option.id)}
                          className="accent-orange-600"
                        />
                        <span className="flex-1">
                          {option.name}
                          {!option.isAvailable && ' (hết)'}
                        </span>
                        {option.extraPrice > 0 && <span>+{vnd(option.extraPrice)}</span>}
                      </label>
                    )
                  })}
                </div>
              </fieldset>
            )
          })}
        </div>

        {error && <p className="mt-3 rounded bg-red-50 p-2 text-sm text-red-700">{error}</p>}
        {missingGroup && <p className="mt-3 text-sm text-orange-700">Vui lòng chọn {missingGroup.name}</p>}

        <div className="mt-4 flex gap-2">
          <button
            type="button"
            onClick={onCancel}
            className="flex-1 rounded-lg border border-gray-300 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Hủy
          </button>
          <button
            type="button"
            onClick={() => onConfirm(chosen)}
            disabled={busy || missingGroup !== undefined}
            className="flex-1 rounded-lg bg-orange-600 py-2 text-sm font-medium text-white hover:bg-orange-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {busy ? 'Đang thêm...' : `Thêm vào giỏ · ${vnd(item.price + extra)}`}
          </button>
        </div>
      </div>
    </div>
  )
}
