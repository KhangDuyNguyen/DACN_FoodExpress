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
  Empty,
  Alert,
} from 'antd'
import {
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  EditOutlined,
  CheckCircleOutlined,
  StopOutlined,
  ShopOutlined,
  CopyOutlined,
  TagOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  fetchMerchantVouchers,
  fetchMerchantRestaurants,
  createMerchantVoucher,
  updateMerchantVoucher,
  updateMerchantVoucherStatus,
  type Voucher,
  type DiscountType,
  type VoucherStatus,
  type MerchantVoucherRequest,
  type MerchantRestaurantOption,
} from '../../api/voucher'
import { getApiError } from '../../api/apiError'

const { Title, Text, Paragraph } = Typography
const { RangePicker } = DatePicker

export const MerchantVouchersPage: React.FC = () => {
  const [vouchers, setVouchers] = useState<Voucher[]>([])
  const [myRestaurants, setMyRestaurants] = useState<MerchantRestaurantOption[]>([])
  const [loading, setLoading] = useState<boolean>(false)
  const [totalItems, setTotalItems] = useState<number>(0)
  const [page, setPage] = useState<number>(0)
  const [pageSize, setPageSize] = useState<number>(10)

  // Bộ lọc
  const [keyword, setKeyword] = useState<string>('')
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined)

  // Modal Thêm / Sửa
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false)
  const [editingVoucher, setEditingVoucher] = useState<Voucher | null>(null)
  const [submitting, setSubmitting] = useState<boolean>(false)
  const [form] = Form.useForm()

  // Tải danh sách quán ăn của chủ quán
  const loadRestaurants = async () => {
    try {
      const res = await fetchMerchantRestaurants()
      setMyRestaurants(res)
    } catch {
      // Bỏ qua hoặc báo lỗi nhẹ
    }
  }

  // Tải danh sách voucher
  const loadVouchers = useCallback(async () => {
    setLoading(true)
    try {
      const res = await fetchMerchantVouchers({
        keyword: keyword || undefined,
        status: statusFilter || undefined,
        page,
        size: pageSize,
      })
      setVouchers(res.items)
      setTotalItems(res.totalItems)
    } catch (err) {
      const apiErr = getApiError(err)
      message.error(apiErr.message || 'Lỗi khi tải danh sách voucher quán')
    } finally {
      setLoading(false)
    }
  }, [keyword, statusFilter, page, pageSize])

  useEffect(() => {
    loadRestaurants()
  }, [])

  useEffect(() => {
    loadVouchers()
  }, [loadVouchers])

  // Mở modal tạo voucher
  const handleOpenCreateModal = () => {
    if (myRestaurants.length === 0) {
      message.warning('Bạn chưa có nhà hàng nào được phê duyệt để tạo voucher.')
      return
    }
    setEditingVoucher(null)
    form.resetFields()
    form.setFieldsValue({
      restaurantId: myRestaurants[0]?.id,
      discountType: 'PERCENT',
      minOrder: 0,
      perUserLimit: 1,
      status: 'ACTIVE',
      dateRange: [dayjs(), dayjs().add(7, 'day')],
    })
    setIsModalOpen(true)
  }

  // Mở modal chỉnh sửa
  const handleOpenEditModal = (record: Voucher) => {
    setEditingVoucher(record)
    form.setFieldsValue({
      restaurantId: record.restaurantId,
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

  // Submit form
  const handleFormSubmit = async () => {
    try {
      const values = await form.validateFields()
      setSubmitting(true)

      const [startAt, endAt] = values.dateRange

      const payload: MerchantVoucherRequest = {
        restaurantId: values.restaurantId,
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
        await updateMerchantVoucher(editingVoucher.id, payload)
        message.success(`Đã cập nhật voucher ${payload.code}`)
      } else {
        await createMerchantVoucher(payload)
        message.success(`Đã tạo thành công voucher ${payload.code} cho quán của bạn`)
      }

      setIsModalOpen(false)
      loadVouchers()
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'errorFields' in err) {
        return
      }
      const apiErr = getApiError(err)
      message.error(apiErr.message || 'Lỗi lưu voucher quán')
    } finally {
      setSubmitting(false)
    }
  }

  // Cập nhật trạng thái
  const handleToggleStatus = async (record: Voucher) => {
    const nextStatus: VoucherStatus = record.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE'
    try {
      await updateMerchantVoucherStatus(record.id, nextStatus)
      message.success(`Voucher ${record.code} đã đổi trạng thái sang ${nextStatus}`)
      loadVouchers()
    } catch (err) {
      const apiErr = getApiError(err)
      message.error(apiErr.message || 'Không thể cập nhật trạng thái voucher')
    }
  }

  const handleCopyCode = (code: string) => {
    navigator.clipboard.writeText(code)
    message.success(`Đã sao chép mã ${code}`)
  }

  const columns: ColumnsType<Voucher> = [
    {
      title: 'Mã Voucher',
      dataIndex: 'code',
      key: 'code',
      width: 150,
      render: (code: string) => (
        <Space size="small">
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
      title: 'Chương trình & Quán áp dụng',
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
          <div style={{ marginTop: 4 }}>
            <Tag color="blue" icon={<ShopOutlined />}>
              {r.restaurantName || `Quán #${r.restaurantId}`}
            </Tag>
          </div>
        </div>
      ),
    },
    {
      title: 'Loại giảm',
      dataIndex: 'discountType',
      key: 'discountType',
      width: 130,
      render: (type: DiscountType) => {
        if (type === 'PERCENT') return <Tag color="green">Giảm %</Tag>
        if (type === 'FIXED') return <Tag color="blue">Giảm tiền mặt</Tag>
        return <Tag color="orange">Miễn phí ship</Tag>
      },
    },
    {
      title: 'Mức giảm',
      key: 'discountValue',
      width: 150,
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
      title: 'Thời hạn',
      key: 'dateRange',
      width: 180,
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
      title: 'Đã dùng / Giới hạn',
      key: 'usage',
      width: 140,
      render: (_, r) => (
        <div>
          <Text strong>{r.usedCount}</Text>
          <Text type="secondary"> / {r.usageLimit != null ? r.usageLimit : 'Không giới hạn'}</Text>
          <div style={{ fontSize: 11, color: '#8c8c8c' }}>
            Mỗi khách: {r.perUserLimit} lần
          </div>
        </div>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (st: VoucherStatus) => {
        if (st === 'ACTIVE') return <Tag color="success">Kích hoạt</Tag>
        if (st === 'PAUSED') return <Tag color="warning">Tạm dừng</Tag>
        return <Tag color="default">Hết hạn</Tag>
      },
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 130,
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
          { title: 'Dashboard Cửa Hàng' },
          { title: 'Tiếp thị & Khuyến mãi' },
          { title: 'Voucher của quán' },
        ]}
      />

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <div>
          <Title level={3} style={{ margin: 0 }}>
            Quản lý Voucher Khuyến Mãi Của Quán
          </Title>
          <Paragraph type="secondary" style={{ margin: '4px 0 0 0' }}>
            Tự tạo mã ưu đãi để tri ân khách quen, kích cầu đơn hàng và tăng doanh thu cho quán ăn của bạn.
          </Paragraph>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          size="large"
          onClick={handleOpenCreateModal}
          style={{ background: '#ff4d4f', borderColor: '#ff4d4f' }}
        >
          Tạo mã giảm giá mới
        </Button>
      </div>

      {myRestaurants.length === 0 && !loading && (
        <Alert
          type="info"
          showIcon
          message="Thông báo tài khoản"
          description="Bạn chưa có nhà hàng nào được liên kết hoặc đã duyệt hoạt động. Vui lòng liên hệ Admin để đăng ký quán trước khi tạo voucher."
          style={{ marginBottom: 16 }}
        />
      )}

      {/* Thống kê nhanh */}
      <Row gutter={16} style={{ marginBottom: 20 }}>
        <Col span={8}>
          <Card size="small" variant="borderless" style={{ background: '#fff2f0', borderColor: '#ffccc7' }}>
            <Statistic
              title="Tổng Voucher Của Quán"
              value={totalItems}
              prefix={<TagOutlined style={{ color: '#ff4d4f' }} />}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" variant="borderless" style={{ background: '#f6ffed', borderColor: '#b7eb8f' }}>
            <Statistic
              title="Đang Chạy Chương Trình"
              value={vouchers.filter((v) => v.status === 'ACTIVE').length}
              prefix={<ThunderboltOutlined style={{ color: '#52c41a' }} />}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" variant="borderless" style={{ background: '#e6f4ff', borderColor: '#91caff' }}>
            <Statistic
              title="Tổng Lượt Khách Đã Áp Dụng"
              value={vouchers.reduce((acc, curr) => acc + (curr.usedCount || 0), 0)}
              valueStyle={{ color: '#1677ff' }}
            />
          </Card>
        </Col>
      </Row>

      {/* Bộ lọc và Tìm kiếm */}
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} sm={10} md={10}>
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
          <Col xs={12} sm={6} md={6}>
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
          <Col xs={12} sm={8} md={8} style={{ textAlign: 'right' }}>
            <Button icon={<ReloadOutlined />} onClick={loadVouchers} loading={loading}>
              Làm mới
            </Button>
          </Col>
        </Row>
      </Card>

      {/* Bảng voucher của quán */}
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
            showTotal: (total) => `Tổng số ${total} voucher của bạn`,
          }}
          locale={{ emptyText: <Empty description="Quán của bạn chưa có voucher khuyến mãi nào" /> }}
        />
      </Card>

      {/* Modal Thêm mới / Cập nhật Voucher Quán */}
      <Modal
        title={editingVoucher ? `Chỉnh sửa voucher: ${editingVoucher.code}` : 'Tạo mới Voucher cho quán của bạn'}
        open={isModalOpen}
        onOk={handleFormSubmit}
        onCancel={() => setIsModalOpen(false)}
        confirmLoading={submitting}
        okText={editingVoucher ? 'Lưu thay đổi' : 'Tạo mã voucher'}
        cancelText="Hủy"
        width={680}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="restaurantId"
            label="Chọn quán ăn áp dụng mã"
            rules={[{ required: true, message: 'Vui lòng chọn quán ăn của bạn' }]}
          >
            <Select
              placeholder="Chọn quán của bạn"
              disabled={!!editingVoucher}
              options={myRestaurants.map((r) => ({
                label: `${r.name} - ${r.address}`,
                value: r.id,
              }))}
            />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="code"
                label="Mã Voucher"
                tooltip="Mẹo: Đặt mã gắn với tên món hoặc tên quán để khách dễ nhớ"
                rules={[
                  { required: true, message: 'Vui lòng nhập mã voucher' },
                  { max: 30, message: 'Tối đa 30 ký tự' },
                  { pattern: /^[A-Z0-9_-]+$/, message: 'Mã gồm chữ hoa, số, gạch ngang/dưới' },
                ]}
              >
                <Input
                  placeholder="VÍ DỤ: COMTAM20K, BUNCHALE"
                  disabled={!!editingVoucher}
                  style={{ textTransform: 'uppercase' }}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="title"
                label="Tiêu đề hiển thị"
                rules={[{ required: true, message: 'Vui lòng nhập tiêu đề' }]}
              >
                <Input placeholder="Ví dụ: Giảm 20k cho món cơm tấm sườn nướng" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="description" label="Ghi chú điều kiện">
            <Input.TextArea rows={2} placeholder="Ví dụ: Chỉ áp dụng cho đơn từ 2 phần cơm trở lên..." />
          </Form.Item>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="discountType"
                label="Hình thức giảm giá"
                rules={[{ required: true, message: 'Chọn hình thức giảm' }]}
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
                        ? 'Mức ship hỗ trợ (0 = full)'
                        : 'Số tiền giảm (VND)'
                  const max = type === 'PERCENT' ? 100 : undefined
                  return (
                    <Form.Item
                      name="discountValue"
                      label={label}
                      rules={[
                        { required: true, message: 'Nhập giá trị' },
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
              <Form.Item name="maxDiscount" label="Mức giảm tối đa (VND)">
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
                label="Ngân sách lượt dùng"
                tooltip="Giới hạn số lượng mã phát ra để kiểm soát chi phí của quán"
              >
                <InputNumber min={1} style={{ width: '100%' }} placeholder="Không giới hạn" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="perUserLimit"
                label="Lượt dùng / Mỗi khách"
                rules={[{ required: true, message: 'Nhập lượt/khách' }]}
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

export default MerchantVouchersPage
