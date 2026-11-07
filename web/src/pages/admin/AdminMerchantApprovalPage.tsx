import React, { useEffect, useState, useCallback } from 'react'
import {
  Card,
  Tabs,
  Table,
  Tag,
  Button,
  Space,
  Input,
  Select,
  Modal,
  Typography,
  Descriptions,
  message,
  Tooltip,
  Badge,
  Alert,
} from 'antd'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  EyeOutlined,
  ReloadOutlined,
  SearchOutlined,
  ShopOutlined,
  ClockCircleOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  fetchAdminMerchants,
  updateAdminMerchantStatus,
  fetchAdminRestaurants,
  updateAdminRestaurantStatus,
  type AdminMerchant,
  type AdminRestaurant,
  type ApprovalStatus,
} from '../../api/adminApproval'
import { getApiError } from '../../api/apiError'

const { Title, Text } = Typography
const { Option } = Select

export const AdminMerchantApprovalPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'merchants' | 'restaurants'>('merchants')

  // State Chủ quán
  const [merchants, setMerchants] = useState<AdminMerchant[]>([])
  const [merchantLoading, setMerchantLoading] = useState(false)
  const [merchantKeyword, setMerchantKeyword] = useState('')
  const [merchantStatus, setMerchantStatus] = useState<string | undefined>(undefined)
  const [merchantPage, setMerchantPage] = useState(1)
  const [merchantPageSize, setMerchantPageSize] = useState(10)
  const [merchantTotal, setMerchantTotal] = useState(0)
  const [selectedMerchant, setSelectedMerchant] = useState<AdminMerchant | null>(null)
  const [isMerchantDetailOpen, setIsMerchantDetailOpen] = useState(false)

  // State Nhà hàng
  const [restaurants, setRestaurants] = useState<AdminRestaurant[]>([])
  const [restaurantLoading, setRestaurantLoading] = useState(false)
  const [restaurantKeyword, setRestaurantKeyword] = useState('')
  const [restaurantStatus, setRestaurantStatus] = useState<string | undefined>(undefined)
  const [restaurantPage, setRestaurantPage] = useState(1)
  const [restaurantPageSize, setRestaurantPageSize] = useState(10)
  const [restaurantTotal, setRestaurantTotal] = useState(0)
  const [selectedRestaurant, setSelectedRestaurant] = useState<AdminRestaurant | null>(null)
  const [isRestaurantDetailOpen, setIsRestaurantDetailOpen] = useState(false)

  // Action Confirm Modal
  const [actionModal, setActionModal] = useState<{
    open: boolean
    type: 'merchant' | 'restaurant'
    targetId: number
    targetName: string
    newStatus: ApprovalStatus
    loading: boolean
    reason: string
  }>({
    open: false,
    type: 'merchant',
    targetId: 0,
    targetName: '',
    newStatus: 'APPROVED',
    loading: false,
    reason: '',
  })

  // ================= LOAD DATA =================
  const loadMerchants = useCallback(async () => {
    setMerchantLoading(true)
    try {
      const data = await fetchAdminMerchants({
        keyword: merchantKeyword || undefined,
        status: merchantStatus || undefined,
        page: merchantPage - 1,
        size: merchantPageSize,
      })
      setMerchants(data.items)
      setMerchantTotal(data.totalItems)
    } catch (err) {
      message.error(getApiError(err).message)
    } finally {
      setMerchantLoading(false)
    }
  }, [merchantKeyword, merchantStatus, merchantPage, merchantPageSize])

  const loadRestaurants = useCallback(async () => {
    setRestaurantLoading(true)
    try {
      const data = await fetchAdminRestaurants({
        keyword: restaurantKeyword || undefined,
        status: restaurantStatus || undefined,
        page: restaurantPage - 1,
        size: restaurantPageSize,
      })
      setRestaurants(data.items)
      setRestaurantTotal(data.totalItems)
    } catch (err) {
      message.error(getApiError(err).message)
    } finally {
      setRestaurantLoading(false)
    }
  }, [restaurantKeyword, restaurantStatus, restaurantPage, restaurantPageSize])

  useEffect(() => {
    if (activeTab === 'merchants') {
      loadMerchants()
    } else {
      loadRestaurants()
    }
  }, [activeTab, loadMerchants, loadRestaurants])

  // Helper render status tag
  const renderStatusTag = (status: ApprovalStatus) => {
    switch (status) {
      case 'APPROVED':
        return (
          <Tag color="success" icon={<CheckCircleOutlined />}>
            Đã duyệt
          </Tag>
        )
      case 'REJECTED':
        return (
          <Tag color="error" icon={<CloseCircleOutlined />}>
            Từ chối
          </Tag>
        )
      case 'PENDING':
      default:
        return (
          <Tag color="warning" icon={<ClockCircleOutlined />}>
            Chờ duyệt
          </Tag>
        )
    }
  }

  // ================= CONFIRM & EXECUTE ACTION =================
  const promptStatusChange = (
    type: 'merchant' | 'restaurant',
    targetId: number,
    targetName: string,
    newStatus: ApprovalStatus
  ) => {
    setActionModal({
      open: true,
      type,
      targetId,
      targetName,
      newStatus,
      loading: false,
      reason: '',
    })
  }

  const handleConfirmStatusChange = async () => {
    setActionModal((prev) => ({ ...prev, loading: true }))
    try {
      if (actionModal.type === 'merchant') {
        await updateAdminMerchantStatus(
          actionModal.targetId,
          actionModal.newStatus,
          actionModal.reason || undefined
        )
        message.success(
          `Đã ${actionModal.newStatus === 'APPROVED' ? 'phê duyệt' : 'từ chối'} chủ quán ${actionModal.targetName}`
        )
        loadMerchants()
      } else {
        await updateAdminRestaurantStatus(
          actionModal.targetId,
          actionModal.newStatus,
          actionModal.reason || undefined
        )
        message.success(
          `Đã ${actionModal.newStatus === 'APPROVED' ? 'phê duyệt' : 'từ chối'} nhà hàng ${actionModal.targetName}`
        )
        loadRestaurants()
      }
      setActionModal((prev) => ({ ...prev, open: false }))
    } catch (err) {
      message.error(getApiError(err).message)
    } finally {
      setActionModal((prev) => ({ ...prev, loading: false }))
    }
  }

  // ================= COLUMNS: MERCHANTS =================
  const merchantColumns: ColumnsType<AdminMerchant> = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 70,
      render: (id) => <Text code>#{id}</Text>,
    },
    {
      title: 'Tên cơ sở kinh doanh',
      dataIndex: 'businessName',
      key: 'businessName',
      render: (name, record) => (
        <div>
          <Text strong>{name}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: 12 }}>
            MST: {record.taxCode || 'Chưa cập nhật'}
          </Text>
        </div>
      ),
    },
    {
      title: 'Chủ sở hữu',
      dataIndex: 'ownerFullName',
      key: 'ownerFullName',
      render: (name, record) => (
        <div>
          <Text>{name}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: 12 }}>
            SĐT: {record.ownerPhone}
          </Text>
        </div>
      ),
    },
    {
      title: 'Số quán',
      dataIndex: 'totalRestaurants',
      key: 'totalRestaurants',
      align: 'center',
      render: (count) => <Badge count={count} showZero color={count > 0 ? '#108ee9' : '#d9d9d9'} />,
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      align: 'center',
      render: (status) => renderStatusTag(status),
    },
    {
      title: 'Ngày đăng ký',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date) => (date ? new Date(date).toLocaleString('vi-VN') : '-'),
    },
    {
      title: 'Thao tác',
      key: 'action',
      align: 'center',
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="Xem chi tiết">
            <Button
              size="small"
              icon={<EyeOutlined />}
              onClick={() => {
                setSelectedMerchant(record)
                setIsMerchantDetailOpen(true)
              }}
            />
          </Tooltip>
          {record.status !== 'APPROVED' && (
            <Tooltip title="Phê duyệt">
              <Button
                type="primary"
                size="small"
                icon={<CheckCircleOutlined />}
                onClick={() =>
                  promptStatusChange('merchant', record.id, record.businessName, 'APPROVED')
                }
              >
                Duyệt
              </Button>
            </Tooltip>
          )}
          {record.status !== 'REJECTED' && (
            <Tooltip title="Từ chối">
              <Button
                danger
                size="small"
                icon={<CloseCircleOutlined />}
                onClick={() =>
                  promptStatusChange('merchant', record.id, record.businessName, 'REJECTED')
                }
              >
                Từ chối
              </Button>
            </Tooltip>
          )}
        </Space>
      ),
    },
  ]

  // ================= COLUMNS: RESTAURANTS =================
  const restaurantColumns: ColumnsType<AdminRestaurant> = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 70,
      render: (id) => <Text code>#{id}</Text>,
    },
    {
      title: 'Tên nhà hàng',
      dataIndex: 'name',
      key: 'name',
      render: (name, record) => (
        <div>
          <Text strong>{name}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.address}
          </Text>
        </div>
      ),
    },
    {
      title: 'Chủ sở hữu / Quán mẹ',
      dataIndex: 'merchantBusinessName',
      key: 'merchantBusinessName',
      render: (mName, record) => (
        <div>
          <Text>{mName}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: 12 }}>
            Chủ: {record.merchantOwnerName} ({record.merchantOwnerPhone})
          </Text>
          <br />
          <span style={{ fontSize: 12 }}>
            Trạng thái chủ quán: {renderStatusTag(record.merchantStatus)}
          </span>
        </div>
      ),
    },
    {
      title: 'SĐT quán',
      dataIndex: 'phone',
      key: 'phone',
      render: (phone) => phone || '-',
    },
    {
      title: 'Đánh giá',
      dataIndex: 'avgRating',
      key: 'avgRating',
      align: 'center',
      render: (rating, record) => (
        <span>⭐ {rating?.toFixed(1) ?? '0.0'} ({record.ratingCount})</span>
      ),
    },
    {
      title: 'Trạng thái duyệt',
      dataIndex: 'status',
      key: 'status',
      align: 'center',
      render: (status) => renderStatusTag(status),
    },
    {
      title: 'Thao tác',
      key: 'action',
      align: 'center',
      render: (_, record) => {
        const canApprove = record.status !== 'APPROVED'
        const merchantIsApproved = record.merchantStatus === 'APPROVED'

        return (
          <Space size="small">
            <Tooltip title="Xem chi tiết">
              <Button
                size="small"
                icon={<EyeOutlined />}
                onClick={() => {
                  setSelectedRestaurant(record)
                  setIsRestaurantDetailOpen(true)
                }}
              />
            </Tooltip>
            {canApprove && (
              <Tooltip
                title={
                  !merchantIsApproved
                    ? 'Chủ quán chưa được duyệt (APPROVED), không thể duyệt nhà hàng'
                    : 'Phê duyệt'
                }
              >
                <Button
                  type="primary"
                  size="small"
                  disabled={!merchantIsApproved}
                  icon={<CheckCircleOutlined />}
                  onClick={() =>
                    promptStatusChange('restaurant', record.id, record.name, 'APPROVED')
                  }
                >
                  Duyệt
                </Button>
              </Tooltip>
            )}
            {record.status !== 'REJECTED' && (
              <Tooltip title="Từ chối">
                <Button
                  danger
                  size="small"
                  icon={<CloseCircleOutlined />}
                  onClick={() =>
                    promptStatusChange('restaurant', record.id, record.name, 'REJECTED')
                  }
                >
                  Từ chối
                </Button>
              </Tooltip>
            )}
          </Space>
        )
      },
    },
  ]

  return (
    <div style={{ padding: '4px 0' }}>
      <div style={{ marginBottom: 16 }}>
        <Title level={3} style={{ marginBottom: 4 }}>
          <ShopOutlined style={{ marginRight: 8, color: '#1677ff' }} />
          Quản lý & Duyệt Chủ quán / Nhà hàng
        </Title>
        <Text type="secondary">
          Kiểm tra thông tin pháp lý, hồ sơ chủ quán và xét duyệt nhà hàng mở bán trên Food Express
        </Text>
      </div>

      <Tabs
        activeKey={activeTab}
        onChange={(key) => setActiveTab(key as 'merchants' | 'restaurants')}
        type="card"
        items={[
          {
            key: 'merchants',
            label: 'Danh sách Chủ quán (Merchants)',
            children: (
              <Card>
                {/* Bộ lọc Chủ quán */}
                <Space wrap style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }}>
                  <Space wrap>
                    <Input
                      placeholder="Tìm tên cơ sở, chủ quán, SĐT..."
                      prefix={<SearchOutlined />}
                      value={merchantKeyword}
                      onChange={(e) => setMerchantKeyword(e.target.value)}
                      onPressEnter={() => {
                        setMerchantPage(1)
                        loadMerchants()
                      }}
                      style={{ width: 280 }}
                      allowClear
                    />
                    <Select
                      placeholder="Trạng thái duyệt"
                      value={merchantStatus}
                      onChange={(val) => {
                        setMerchantStatus(val)
                        setMerchantPage(1)
                      }}
                      style={{ width: 160 }}
                      allowClear
                    >
                      <Option value="PENDING">Chờ duyệt (PENDING)</Option>
                      <Option value="APPROVED">Đã duyệt (APPROVED)</Option>
                      <Option value="REJECTED">Từ chối (REJECTED)</Option>
                    </Select>
                    <Button type="primary" icon={<SearchOutlined />} onClick={() => loadMerchants()}>
                      Tìm kiếm
                    </Button>
                  </Space>
                  <Button icon={<ReloadOutlined />} onClick={() => loadMerchants()}>
                    Làm mới
                  </Button>
                </Space>

                {/* Bảng Chủ quán */}
                <Table
                  columns={merchantColumns}
                  dataSource={merchants}
                  rowKey="id"
                  loading={merchantLoading}
                  pagination={{
                    current: merchantPage,
                    pageSize: merchantPageSize,
                    total: merchantTotal,
                    showSizeChanger: true,
                    showTotal: (total) => `Tổng cộng ${total} chủ quán`,
                    onChange: (p, s) => {
                      setMerchantPage(p)
                      setMerchantPageSize(s)
                    },
                  }}
                />
              </Card>
            ),
          },
          {
            key: 'restaurants',
            label: 'Danh sách Nhà hàng (Restaurants)',
            children: (
              <Card>
                <Alert
                  type="info"
                  showIcon
                  message="Quy tắc nghiệp vụ duyệt nhà hàng"
                  description="Nhà hàng chỉ có thể được duyệt APPROVED khi chủ quán sở hữu (Merchant) đã ở trạng thái APPROVED. Nhà hàng chỉ hiển thị công khai cho khách hàng khi đã được APPROVED."
                  style={{ marginBottom: 16 }}
                />

                {/* Bộ lọc Nhà hàng */}
                <Space wrap style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }}>
                  <Space wrap>
                    <Input
                      placeholder="Tìm tên nhà hàng, địa chỉ, chủ sở hữu..."
                      prefix={<SearchOutlined />}
                      value={restaurantKeyword}
                      onChange={(e) => setRestaurantKeyword(e.target.value)}
                      onPressEnter={() => {
                        setRestaurantPage(1)
                        loadRestaurants()
                      }}
                      style={{ width: 300 }}
                      allowClear
                    />
                    <Select
                      placeholder="Trạng thái duyệt"
                      value={restaurantStatus}
                      onChange={(val) => {
                        setRestaurantStatus(val)
                        setRestaurantPage(1)
                      }}
                      style={{ width: 160 }}
                      allowClear
                    >
                      <Option value="PENDING">Chờ duyệt (PENDING)</Option>
                      <Option value="APPROVED">Đã duyệt (APPROVED)</Option>
                      <Option value="REJECTED">Từ chối (REJECTED)</Option>
                    </Select>
                    <Button type="primary" icon={<SearchOutlined />} onClick={() => loadRestaurants()}>
                      Tìm kiếm
                    </Button>
                  </Space>
                  <Button icon={<ReloadOutlined />} onClick={() => loadRestaurants()}>
                    Làm mới
                  </Button>
                </Space>

                {/* Bảng Nhà hàng */}
                <Table
                  columns={restaurantColumns}
                  dataSource={restaurants}
                  rowKey="id"
                  loading={restaurantLoading}
                  pagination={{
                    current: restaurantPage,
                    pageSize: restaurantPageSize,
                    total: restaurantTotal,
                    showSizeChanger: true,
                    showTotal: (total) => `Tổng cộng ${total} nhà hàng`,
                    onChange: (p, s) => {
                      setRestaurantPage(p)
                      setRestaurantPageSize(s)
                    },
                  }}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* MODAL CHI TIẾT CHỦ QUÁN */}
      <Modal
        title={`Chi tiết Chủ quán: ${selectedMerchant?.businessName ?? ''}`}
        open={isMerchantDetailOpen}
        onCancel={() => setIsMerchantDetailOpen(false)}
        footer={[
          <Button key="close" onClick={() => setIsMerchantDetailOpen(false)}>
            Đóng
          </Button>,
        ]}
        width={650}
      >
        {selectedMerchant && (
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="Mã chủ quán">#{selectedMerchant.id}</Descriptions.Item>
            <Descriptions.Item label="Tên cơ sở kinh doanh">
              <Text strong>{selectedMerchant.businessName}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Họ tên người đại diện">
              {selectedMerchant.ownerFullName}
            </Descriptions.Item>
            <Descriptions.Item label="Số điện thoại liên hệ">
              {selectedMerchant.contactPhone || selectedMerchant.ownerPhone}
            </Descriptions.Item>
            <Descriptions.Item label="Email">
              {selectedMerchant.ownerEmail || 'Chưa cung cấp'}
            </Descriptions.Item>
            <Descriptions.Item label="Mã số thuế">
              {selectedMerchant.taxCode || 'Chưa cung cấp'}
            </Descriptions.Item>
            <Descriptions.Item label="Số lượng chi nhánh nhà hàng">
              {selectedMerchant.totalRestaurants} nhà hàng
            </Descriptions.Item>
            <Descriptions.Item label="Trạng thái hiện tại">
              {renderStatusTag(selectedMerchant.status)}
            </Descriptions.Item>
            <Descriptions.Item label="Ngày đăng ký">
              {selectedMerchant.createdAt
                ? new Date(selectedMerchant.createdAt).toLocaleString('vi-VN')
                : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="Cập nhật lần cuối">
              {selectedMerchant.updatedAt
                ? new Date(selectedMerchant.updatedAt).toLocaleString('vi-VN')
                : '-'}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>

      {/* MODAL CHI TIẾT NHÀ HÀNG */}
      <Modal
        title={`Chi tiết Nhà hàng: ${selectedRestaurant?.name ?? ''}`}
        open={isRestaurantDetailOpen}
        onCancel={() => setIsRestaurantDetailOpen(false)}
        footer={[
          <Button key="close" onClick={() => setIsRestaurantDetailOpen(false)}>
            Đóng
          </Button>,
        ]}
        width={700}
      >
        {selectedRestaurant && (
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="Mã nhà hàng">#{selectedRestaurant.id}</Descriptions.Item>
            <Descriptions.Item label="Tên nhà hàng">
              <Text strong>{selectedRestaurant.name}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Mô tả">
              {selectedRestaurant.description || 'Chưa có mô tả'}
            </Descriptions.Item>
            <Descriptions.Item label="Địa chỉ">
              {selectedRestaurant.address}
            </Descriptions.Item>
            <Descriptions.Item label="Chủ quán / Merchant">
              {selectedRestaurant.merchantBusinessName} (Chủ: {selectedRestaurant.merchantOwnerName} - {selectedRestaurant.merchantOwnerPhone})
            </Descriptions.Item>
            <Descriptions.Item label="Trạng thái chủ quán">
              {renderStatusTag(selectedRestaurant.merchantStatus)}
            </Descriptions.Item>
            <Descriptions.Item label="Số điện thoại quán">
              {selectedRestaurant.phone || 'Chưa có'}
            </Descriptions.Item>
            <Descriptions.Item label="Trạng thái mở cửa">
              <Tag color={selectedRestaurant.openStatus === 'OPEN' ? 'green' : 'default'}>
                {selectedRestaurant.openStatus}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Trạng thái duyệt">
              {renderStatusTag(selectedRestaurant.status)}
            </Descriptions.Item>
            <Descriptions.Item label="Đơn tối thiểu">
              {selectedRestaurant.minOrderAmount?.toLocaleString('vi-VN')} đ
            </Descriptions.Item>
            <Descriptions.Item label="Thời gian chuẩn bị">
              {selectedRestaurant.prepTimeMinutes} phút
            </Descriptions.Item>
            <Descriptions.Item label="Đánh giá trung bình">
              ⭐ {selectedRestaurant.avgRating?.toFixed(1) ?? '0.0'} ({selectedRestaurant.ratingCount} lượt)
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>

      {/* MODAL XÁC NHẬN PHÊ DUYỆT / TỪ CHỐI */}
      <Modal
        title={
          <Space>
            <ExclamationCircleOutlined
              style={{
                color: actionModal.newStatus === 'APPROVED' ? '#52c41a' : '#ff4d4f',
              }}
            />
            {actionModal.newStatus === 'APPROVED' ? 'Xác nhận Phê duyệt' : 'Xác nhận Từ chối'}
          </Space>
        }
        open={actionModal.open}
        onCancel={() => setActionModal((prev) => ({ ...prev, open: false }))}
        onOk={handleConfirmStatusChange}
        confirmLoading={actionModal.loading}
        okText={actionModal.newStatus === 'APPROVED' ? 'Phê duyệt' : 'Từ chối'}
        okButtonProps={{
          danger: actionModal.newStatus === 'REJECTED',
          type: 'primary',
        }}
      >
        <p>
          Bạn có chắc chắn muốn {actionModal.newStatus === 'APPROVED' ? 'phê duyệt' : 'từ chối'}{' '}
          {actionModal.type === 'merchant' ? 'chủ quán' : 'nhà hàng'}{' '}
          <Text strong>"{actionModal.targetName}"</Text>?
        </p>
        <Input.TextArea
          rows={3}
          placeholder="Nhập ghi chú hoặc lý do (tùy chọn)..."
          value={actionModal.reason}
          onChange={(e) => setActionModal((prev) => ({ ...prev, reason: e.target.value }))}
        />
      </Modal>
    </div>
  )
}

export default AdminMerchantApprovalPage
