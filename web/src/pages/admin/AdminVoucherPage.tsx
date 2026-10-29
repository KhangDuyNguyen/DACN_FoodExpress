import React, { useState, useEffect, useCallback } from 'react'
import {
  Table,
  Card,
  Button,
  Input,
  Select,
  Tag,
  Space,
  Modal,
  Form,
  InputNumber,
  DatePicker,
  Typography,
  message,
  Tooltip,
  Popconfirm,
  Row,
  Col,
  Statistic,
  Breadcrumb,
} from 'antd'
import {
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  EditOutlined,
  CheckCircleOutlined,
  StopOutlined,
  TagOutlined,
  ThunderboltOutlined,
  CopyOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  fetchAdminVouchers,
  createAdminVoucher,
  updateAdminVoucher,
  updateAdminVoucherStatus,
  type Voucher,
  type DiscountType,
  type VoucherStatus,
  type AdminVoucherRequest,
} from '../../api/voucher'
import { getApiError } from '../../api/apiError'

const { Title, Text, Paragraph } = Typography
const { RangePicker } = DatePicker

export const AdminVoucherPage: React.FC = () => {
  const [vouchers, setVouchers] = useState<Voucher[]>([])
  const [loading, setLoading] = useState<boolean>(false)
  const [totalItems, setTotalItems] = useState<number>(0)
  const [page, setPage] = useState<number>(0)
  const [pageSize, setPageSize] = useState<number>(10)

  // Bộ lọc
  const [keyword, setKeyword] = useState<string>('')
  const [discountTypeFilter, setDiscountTypeFilter] = useState<string | undefined>(undefined)
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined)

  // Modal Thêm / Sửa
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false)
  const [editingVoucher, setEditingVoucher] = useState<Voucher | null>(null)
  const [submitting, setSubmitting] = useState<boolean>(false)
  const [form] = Form.useForm()

  const loadVouchers = useCallback(async () => {
    setLoading(true)
    try {
      const res = await fetchAdminVouchers({
        keyword: keyword || undefined,
        discountType: discountTypeFilter || undefined,
        status: statusFilter || undefined,
        page,
        size: pageSize,
      })
      setVouchers(res.items)
      setTotalItems(res.totalItems)
    } catch (err) {
      const apiErr = getApiError(err)
      message.error(apiErr.message || 'Lỗi khi tải danh sách voucher toàn sàn')
    } finally {
      setLoading(false)
    }
  }, [keyword, discountTypeFilter, statusFilter, page, pageSize])

  useEffect(() => {
    loadVouchers()
  }, [loadVouchers])

  // Xử lý mở modal tạo mới
  const handleOpenCreateModal = () => {
    setEditingVoucher(null)
    form.resetFields()
    form.setFieldsValue({
      discountType: 'PERCENT',
      minOrder: 0,
      perUserLimit: 1,
      status: 'ACTIVE',
      dateRange: [dayjs(), dayjs().add(7, 'day')],
    })
    setIsModalOpen(true)
  }

  // Xử lý mở modal chỉnh sửa
  const handleOpenEditModal = (record: Voucher) => {
    setEditingVoucher(record)
    form.setFieldsValue({
      code: record.code,
      title: record.title,
      description: record.description,
      discountType: record.discountType,
      discountValue: record.discountValue,
      minOrder: record.minOrder,
      maxDiscount: record.maxDiscount,
      perUserLimit: record.perUserLimit,
      usageLimit: record.usageLimit,
      status: record.status,
      dateRange: [dayjs(record.startAt), dayjs(record.endAt)],
    })
    setIsModalOpen(true)
  }

  // Submit form (Tạo hoặc cập nhật)
  const handleFormSubmit = async () => {
    try {
      const values = await form.validateFields()
      setSubmitting(true)

      const [startAt, endAt] = values.dateRange

      const payload: AdminVoucherRequest = {
        code: values.code.trim().toUpperCase(),
        title: values.title.trim(),
        description: values.description?.trim() || undefined,
        discountType: values.discountType,
        discountValue: values.discountValue,
        minOrder: values.minOrder || 0,
        maxDiscount: values.maxDiscount || undefined,
        startAt: startAt.format('YYYY-MM-DDTHH:mm:ss'),
        endAt: endAt.format('YYYY-MM-DDTHH:mm:ss'),
        usageLimit: values.usageLimit || undefined,
        perUserLimit: values.perUserLimit || 1,
        status: values.status || 'ACTIVE',
      }

      if (editingVoucher) {
        await updateAdminVoucher(editingVoucher.id, payload)
        message.success(`Đã cập nhật thành công voucher ${payload.code}`)
      } else {
        await createAdminVoucher(payload)
        message.success(`Đã tạo thành công voucher toàn sàn ${payload.code}`)
      }

      setIsModalOpen(false)
      loadVouchers()
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'errorFields' in err) {
        return // Validate lỗi trên form ant design
      }
      const apiErr = getApiError(err)
      message.error(apiErr.message || 'Lỗi lưu voucher')
    } finally {
      setSubmitting(false)
    }
  }

  // Cập nhật trạng thái voucher (ACTIVE <-> PAUSED)
  const handleToggleStatus = async (record: Voucher) => {
    const nextStatus: VoucherStatus = record.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE'
    try {
      await updateAdminVoucherStatus(record.id, nextStatus)
      message.success(`Voucher ${record.code} đã chuyển sang trạng thái ${nextStatus}`)
      loadVouchers()
    } catch (err) {
      const apiErr = getApiError(err)
      message.error(apiErr.message || 'Không thể cập nhật trạng thái voucher')
    }
  }

  // Sao chép mã code vào clipboard
  const handleCopyCode = (code: string) => {
    navigator.clipboard.writeText(code)
    message.success(`Đã sao chép mã ${code}`)
  }

  // Render tag loại voucher
  const renderDiscountTypeTag = (type: DiscountType) => {
    switch (type) {
      case 'PERCENT':
        return <Tag color="green">Giảm %</Tag>
      case 'FIXED':
        return <Tag color="blue">Giảm tiền mặt</Tag>
      case 'FREE_SHIP':
        return <Tag color="orange">Miễn phí ship</Tag>
    }
  }

  // Render tag trạng thái
  const renderStatusTag = (status: VoucherStatus) => {
    switch (status) {
      case 'ACTIVE':
        return <Tag color="success">Đang kích hoạt</Tag>
      case 'PAUSED':
        return <Tag color="warning">Tạm dừng</Tag>
      case 'EXPIRED':
        return <Tag color="default">Hết hạn</Tag>
    }
  }

  const columns: ColumnsType<Voucher> = [
    {
      title: 'Mã Voucher',
      dataIndex: 'code',
      key: 'code',
      width: 150,
      render: (code: string) => (
        <Space orientation="horizontal" size="small">
          <Text strong style={{ fontFamily: 'monospace', color: '#1677ff' }}>
            {code}
          </Text>
          <Tooltip title="Sao chép">
            <Button
              type="text"
              size="small"
              icon={<CopyOutlined style={{ fontSize: 12, color: '#8c8c8c' }} />}
              onClick={() => handleCopyCode(code)}
            />
          </Tooltip>
        </Space>
      ),
    },
    {
      title: 'Chương trình & Phạm vi',
      key: 'title',
      render: (_, r) => (
        <div>
          <Text strong>{r.title}</Text>
          {r.description && (
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {r.description}
              </Text>
            </div>
          )}
          <div>
            {r.restaurantId ? (
              <Tag color="cyan">Quán: {r.restaurantName || `#${r.restaurantId}`}</Tag>
            ) : (
              <Tag color="purple">Toàn sàn FoodExpress</Tag>
            )}
          </div>
        </div>
      ),
    },
    {
      title: 'Loại giảm giá',
      dataIndex: 'discountType',
      key: 'discountType',
      width: 140,
      render: (type: DiscountType) => renderDiscountTypeTag(type),
    },
    {
      title: 'Mức giảm',
      key: 'discountValue',
      width: 160,
      render: (_, r) => {
        if (r.discountType === 'PERCENT') {
          return (
            <div>
              <Text strong style={{ color: '#52c41a' }}>{r.discountValue}%</Text>
              {r.maxDiscount && (
                <div style={{ fontSize: 11, color: '#8c8c8c' }}>
                  Tối đa: {Number(r.maxDiscount).toLocaleString('vi-VN')} đ
                </div>
              )}
            </div>
          )
        }
        if (r.discountType === 'FIXED') {
          return (
            <Text strong style={{ color: '#1677ff' }}>
              {Number(r.discountValue).toLocaleString('vi-VN')} đ
            </Text>
          )
        }
        // FREE_SHIP
        return (
          <div>
            <Text strong style={{ color: '#fa8c16' }}>Freeship</Text>
            {r.discountValue > 0 && (
              <div style={{ fontSize: 11, color: '#8c8c8c' }}>
                Hỗ trợ: {Number(r.discountValue).toLocaleString('vi-VN')} đ
              </div>
            )}
          </div>
        )
      },
    },
    {
      title: 'Đơn tối thiểu',
      dataIndex: 'minOrder',
      key: 'minOrder',
      width: 130,
      render: (val: number) => `${Number(val).toLocaleString('vi-VN')} đ`,
    },
    {
      title: 'Thời gian hiệu lực',
      key: 'dateRange',
      width: 190,
      render: (_, r) => {
        const isExpired = dayjs().isAfter(dayjs(r.endAt))
        return (
          <div style={{ fontSize: 12 }}>
            <div>Từ: {dayjs(r.startAt).format('DD/MM/YYYY HH:mm')}</div>
            <div style={{ color: isExpired ? '#ff4d4f' : undefined }}>
              Đến: {dayjs(r.endAt).format('DD/MM/YYYY HH:mm')}
            </div>
          </div>
        )
      },
    },
    {
      title: 'Lượt sử dụng',
      key: 'usage',
      width: 140,
      render: (_, r) => (
        <div>
          <Text strong>{r.usedCount}</Text>
          <Text type="secondary"> / {r.usageLimit != null ? r.usageLimit : '∞'}</Text>
          <div style={{ fontSize: 11, color: '#8c8c8c' }}>
            Mỗi user: {r.perUserLimit} lần
          </div>
        </div>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 130,
      render: (st: VoucherStatus) => renderStatusTag(st),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 150,
      render: (_, r) => (
        <Space size="small">
          <Tooltip title="Chỉnh sửa">
            <Button
              type="text"
              icon={<EditOutlined style={{ color: '#1677ff' }} />}
              onClick={() => handleOpenEditModal(r)}
            />
          </Tooltip>

          <Popconfirm
            title={`Bạn có chắc muốn ${r.status === 'ACTIVE' ? 'tạm dừng' : 'kích hoạt'} voucher này?`}
            onConfirm={() => handleToggleStatus(r)}
            okText="Đồng ý"
            cancelText="Hủy"
          >
            <Tooltip title={r.status === 'ACTIVE' ? 'Tạm dừng voucher' : 'Kích hoạt voucher'}>
              <Button
                type="text"
                danger={r.status === 'ACTIVE'}
                icon={
                  r.status === 'ACTIVE' ? (
                    <StopOutlined />
                  ) : (
                    <CheckCircleOutlined style={{ color: '#52c41a' }} />
                  )
                }
              />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: '24px' }}>
      <Breadcrumb
        style={{ marginBottom: 16 }}
        items={[
          { title: 'Quản trị Admin' },
          { title: 'Khuyến mãi & Voucher' },
          { title: 'Voucher toàn sàn' },
        ]}
      />

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <div>
          <Title level={3} style={{ margin: 0 }}>
            Quản lý Voucher Khuyến Mãi Toàn Sàn
          </Title>
          <Paragraph type="secondary" style={{ margin: '4px 0 0 0' }}>
            Cấu hình mã giảm giá toàn sàn: hỗ trợ giảm %, tiền cố định và miễn phí vận chuyển.
          </Paragraph>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          size="large"
          onClick={handleOpenCreateModal}
          style={{ background: '#1677ff' }}
        >
          Tạo voucher mới
        </Button>
      </div>

      {/* Thống kê nhanh */}
      <Row gutter={16} style={{ marginBottom: 20 }}>
        <Col span={6}>
          <Card size="small" variant="borderless" style={{ background: '#f6ffed', borderColor: '#b7eb8f' }}>
            <Statistic
              title="Tổng Voucher Hệ Thống"
              value={totalItems}
              prefix={<TagOutlined style={{ color: '#52c41a' }} />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small" variant="borderless" style={{ background: '#e6f4ff', borderColor: '#91caff' }}>
            <Statistic
              title="Voucher Đang Kích Hoạt"
              value={vouchers.filter((v) => v.status === 'ACTIVE').length}
              prefix={<ThunderboltOutlined style={{ color: '#1677ff' }} />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small" variant="borderless" style={{ background: '#fff7e6', borderColor: '#ffd591' }}>
            <Statistic
              title="Voucher Miễn Phí Vận Chuyển"
              value={vouchers.filter((v) => v.discountType === 'FREE_SHIP').length}
              valueStyle={{ color: '#fa8c16' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small" variant="borderless" style={{ background: '#f9f0ff', borderColor: '#d3adf7' }}>
            <Statistic
              title="Tổng Lượt Đã Sử Dụng"
              value={vouchers.reduce((acc, curr) => acc + (curr.usedCount || 0), 0)}
              valueStyle={{ color: '#722ed1' }}
            />
          </Card>
        </Col>
      </Row>

      {/* Bộ lọc và Tìm kiếm */}
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} sm={8} md={8}>
            <Input
              placeholder="Tìm theo mã hoặc tên voucher..."
              prefix={<SearchOutlined />}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onPressEnter={() => {
                setPage(0)
                loadVouchers()
              }}
              allowClear
            />
          </Col>
          <Col xs={12} sm={6} md={5}>
            <Select
              style={{ width: '100%' }}
              placeholder="Loại giảm giá"
              value={discountTypeFilter}
              onChange={(val) => {
                setDiscountTypeFilter(val)
                setPage(0)
              }}
              allowClear
              options={[
                { label: 'Tất cả loại', value: '' },
                { label: 'Giảm theo phần trăm (%)', value: 'PERCENT' },
                { label: 'Giảm số tiền cố định', value: 'FIXED' },
                { label: 'Miễn phí vận chuyển', value: 'FREE_SHIP' },
              ]}
            />
          </Col>
          <Col xs={12} sm={6} md={5}>
            <Select
              style={{ width: '100%' }}
              placeholder="Trạng thái"
              value={statusFilter}
              onChange={(val) => {
                setStatusFilter(val)
                setPage(0)
              }}
              allowClear
              options={[
                { label: 'Tất cả trạng thái', value: '' },
                { label: 'Đang kích hoạt (ACTIVE)', value: 'ACTIVE' },
                { label: 'Tạm dừng (PAUSED)', value: 'PAUSED' },
                { label: 'Hết hạn (EXPIRED)', value: 'EXPIRED' },
              ]}
            />
          </Col>
          <Col xs={24} sm={4} md={6} style={{ textAlign: 'right' }}>
            <Space>
              <Button icon={<ReloadOutlined />} onClick={loadVouchers} loading={loading}>
                Làm mới
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      {/* Bảng dữ liệu voucher */}
      <Card>
        <Table<Voucher>
          rowKey="id"
          columns={columns}
          dataSource={vouchers}
          loading={loading}
          pagination={{
            current: page + 1,
            pageSize,
            total: totalItems,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50'],
            onChange: (p, ps) => {
              setPage(p - 1)
              setPageSize(ps)
            },
            showTotal: (total) => `Tổng số ${total} voucher`,
          }}
          locale={{ emptyText: 'Chưa có voucher nào phù hợp' }}
        />
      </Card>

      {/* Modal Thêm mới / Cập nhật Voucher */}
      <Modal
        title={editingVoucher ? `Chỉnh sửa voucher: ${editingVoucher.code}` : 'Tạo mới Voucher toàn sàn'}
        open={isModalOpen}
        onOk={handleFormSubmit}
        onCancel={() => setIsModalOpen(false)}
        confirmLoading={submitting}
        okText={editingVoucher ? 'Lưu thay đổi' : 'Tạo voucher'}
        cancelText="Hủy"
        width={680}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="code"
                label="Mã Voucher"
                rules={[
                  { required: true, message: 'Vui lòng nhập mã voucher' },
                  { max: 30, message: 'Tối đa 30 ký tự' },
                  { pattern: /^[A-Z0-9_-]+$/, message: 'Mã gồm chữ hoa, số, gạch ngang/dưới' },
                ]}
              >
                <Input
                  placeholder="VÍ DỤ: FOODEXPRESS10, FREESHIP"
                  disabled={!!editingVoucher}
                  style={{ textTransform: 'uppercase' }}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="title"
                label="Tiêu đề chương trình"
                rules={[{ required: true, message: 'Vui lòng nhập tiêu đề' }]}
              >
                <Input placeholder="Ví dụ: Giảm 20% đơn từ 100k cuối tuần" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="description" label="Mô tả chi tiết">
            <Input.TextArea rows={2} placeholder="Chi tiết điều khoản và thể lệ áp dụng của voucher..." />
          </Form.Item>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="discountType"
                label="Loại giảm giá"
                rules={[{ required: true, message: 'Chọn loại giảm giá' }]}
              >
                <Select
                  options={[
                    { label: 'Giảm phần trăm (%)', value: 'PERCENT' },
                    { label: 'Giảm số tiền cố định', value: 'FIXED' },
                    { label: 'Miễn phí vận chuyển', value: 'FREE_SHIP' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                noStyle
                shouldUpdate={(prev, curr) => prev.discountType !== curr.discountType}
              >
                {({ getFieldValue }) => {
                  const type: DiscountType = getFieldValue('discountType')
                  const label =
                    type === 'PERCENT'
                      ? 'Tỷ lệ giảm (%)'
                      : type === 'FREE_SHIP'
                        ? 'Mức ship tối đa hỗ trợ (0 = full)'
                        : 'Số tiền giảm (VND)'
                  const max = type === 'PERCENT' ? 100 : undefined
                  return (
                    <Form.Item
                      name="discountValue"
                      label={label}
                      rules={[
                        { required: true, message: 'Nhập giá trị giảm' },
                        { type: 'number', min: 0, max, message: `Giá trị từ 0${max ? ` đến ${max}` : ''}` },
                      ]}
                    >
                      <InputNumber
                        style={{ width: '100%' }}
                        formatter={(val) => `${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                        parser={(val) => (val ? Number(val.replace(/\$\s?|(,*)/g, '')) : 0)}
                      />
                    </Form.Item>
                  )
                }}
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="maxDiscount"
                label="Mức giảm tối đa (VND)"
                tooltip="Áp dụng cho giảm % hoặc giới hạn trợ giá phí ship"
              >
                <InputNumber
                  style={{ width: '100%' }}
                  placeholder="Không giới hạn"
                  formatter={(val) => `${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                  parser={(val) => (val ? Number(val.replace(/\$\s?|(,*)/g, '')) : 0)}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="minOrder"
                label="Đơn hàng tối thiểu (VND)"
                rules={[{ required: true, message: 'Nhập giá trị đơn tối thiểu' }]}
              >
                <InputNumber<number>
                  style={{ width: '100%' }}
                  formatter={(val) => `${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                  parser={(val) => (val ? Number(val.replace(/\$\s?|(,*)/g, '')) : 0)}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="dateRange"
                label="Thời hạn áp dụng"
                rules={[{ required: true, message: 'Vui lòng chọn thời hạn' }]}
              >
                <RangePicker showTime format="DD/MM/YYYY HH:mm" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="usageLimit"
                label="Tổng lượt phát hành"
                tooltip="Để trống = Không giới hạn số lượt toàn hệ thống"
              >
                <InputNumber min={1} style={{ width: '100%' }} placeholder="Không giới hạn" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="perUserLimit"
                label="Lượt dùng / Mỗi tài khoản"
                rules={[{ required: true, message: 'Nhập lượt/user' }]}
              >
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="status" label="Trạng thái ban đầu">
                <Select
                  options={[
                    { label: 'Đang hoạt động (ACTIVE)', value: 'ACTIVE' },
                    { label: 'Tạm dừng (PAUSED)', value: 'PAUSED' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  )
}

export default AdminVoucherPage
