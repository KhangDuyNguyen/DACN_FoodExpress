import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Card, Input, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useState } from 'react'
import { getApiError } from '../../api/apiError'
import { changeUserStatus, fetchAdminUsers, type AdminUser, type UserStatus } from '../../api/adminUsers'
import type { Role } from '../../api/auth'

// Trang admin quan ly nguoi dung (Khang, tuan 6): loc, tim, khoa / mo khoa tai khoan.

const ROLE_LABEL: Record<Role, string> = {
  CUSTOMER: 'Khách hàng',
  MERCHANT: 'Chủ quán',
  DRIVER: 'Tài xế',
  ADMIN: 'Quản trị',
}

const STATUS_TAG: Record<UserStatus, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: 'Hoạt động' },
  LOCKED: { color: 'red', label: 'Bị khóa' },
  PENDING: { color: 'orange', label: 'Chờ duyệt' },
  DELETED: { color: 'default', label: 'Đã xóa' },
}

const PAGE_SIZE = 20

function formatTime(value: string | null) {
  return value ? new Date(value).toLocaleString('vi-VN') : '—'
}

export default function AdminUsersPage() {
  const queryClient = useQueryClient()
  const [role, setRole] = useState<Role | undefined>()
  const [status, setStatus] = useState<UserStatus | undefined>()
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(0)

  const users = useQuery({
    queryKey: ['admin-users', role, status, keyword, page],
    queryFn: () => fetchAdminUsers({ role, status, q: keyword || undefined, page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData, // giu bang cu trong luc tai trang moi, khong bi nhay
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, next }: { id: number; next: 'ACTIVE' | 'LOCKED' }) => changeUserStatus(id, next),
    onSuccess: (user) => {
      message.success(`${user.fullName}: ${STATUS_TAG[user.status].label}`)
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
    },
    onError: (error) => message.error(getApiError(error).message),
  })

  // Doi bo loc thi quay ve trang dau
  function changeFilter(apply: () => void) {
    apply()
    setPage(0)
  }

  const columns: ColumnsType<AdminUser> = [
    { title: 'Họ tên', dataIndex: 'fullName' },
    { title: 'Số điện thoại', dataIndex: 'phone' },
    { title: 'Email', dataIndex: 'email', render: (email: string | null) => email ?? '—' },
    { title: 'Vai trò', dataIndex: 'role', render: (r: Role) => <Tag>{ROLE_LABEL[r]}</Tag> },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      render: (s: UserStatus) => <Tag color={STATUS_TAG[s].color}>{STATUS_TAG[s].label}</Tag>,
    },
    { title: 'Ngày tạo', dataIndex: 'createdAt', render: formatTime },
    { title: 'Đăng nhập gần nhất', dataIndex: 'lastLoginAt', render: formatTime },
    {
      title: 'Thao tác',
      key: 'action',
      render: (_, user) => {
        // Khong khoa tai khoan quan tri (backend cung chan); chi doi giua Hoat dong va Bi khoa
        if (user.role === 'ADMIN' || (user.status !== 'ACTIVE' && user.status !== 'LOCKED')) return null
        const locking = user.status === 'ACTIVE'
        return (
          <Popconfirm
            title={locking ? `Khóa tài khoản ${user.fullName}?` : `Mở khóa tài khoản ${user.fullName}?`}
            description={locking ? 'Người này bị đăng xuất ngay và không đăng nhập lại được.' : undefined}
            okText={locking ? 'Khóa' : 'Mở khóa'}
            cancelText="Hủy"
            onConfirm={() => statusMutation.mutate({ id: user.id, next: locking ? 'LOCKED' : 'ACTIVE' })}
          >
            <Button size="small" danger={locking}>
              {locking ? 'Khóa' : 'Mở khóa'}
            </Button>
          </Popconfirm>
        )
      },
    },
  ]

  return (
    <Card>
      <Typography.Title level={4}>Quản lý người dùng</Typography.Title>
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          placeholder="Vai trò"
          style={{ width: 160 }}
          value={role}
          onChange={(value) => changeFilter(() => setRole(value))}
          options={(Object.keys(ROLE_LABEL) as Role[]).map((r) => ({ value: r, label: ROLE_LABEL[r] }))}
        />
        <Select
          allowClear
          placeholder="Trạng thái"
          style={{ width: 160 }}
          value={status}
          onChange={(value) => changeFilter(() => setStatus(value))}
          options={[
            { value: 'ACTIVE', label: 'Hoạt động' },
            { value: 'LOCKED', label: 'Bị khóa' },
          ]}
        />
        <Input.Search
          allowClear
          placeholder="Tìm họ tên, số điện thoại, email"
          style={{ width: 320 }}
          onSearch={(value) => changeFilter(() => setKeyword(value.trim()))}
        />
      </Space>
      {users.isError && <Typography.Text type="danger">{getApiError(users.error).message}</Typography.Text>}
      <Table
        rowKey="id"
        columns={columns}
        dataSource={users.data?.items ?? []}
        loading={users.isFetching}
        pagination={{
          current: page + 1,
          pageSize: PAGE_SIZE,
          total: users.data?.totalItems ?? 0,
          showSizeChanger: false,
          onChange: (next) => setPage(next - 1),
        }}
      />
    </Card>
  )
}
