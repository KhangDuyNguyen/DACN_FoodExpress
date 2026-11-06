import React, { useEffect, useState, useCallback } from 'react'
import {
  Card,
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
  CarOutlined,
  ClockCircleOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  fetchAdminDrivers,
  updateAdminDriverStatus,
  type AdminDriver,
  type ApprovalStatus,
} from '../../api/adminApproval'
import { getApiError } from '../../api/apiError'

const { Title, Text } = Typography
const { Option } = Select

export const AdminDriverApprovalPage: React.FC = () => {
  const [drivers, setDrivers] = useState<AdminDriver[]>([])
  const [loading, setLoading] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string | undefined>(undefined)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [total, setTotal] = useState(0)

  const [selectedDriver, setSelectedDriver] = useState<AdminDriver | null>(null)
  const [isDetailOpen, setIsDetailOpen] = useState(false)

  const [actionModal, setActionModal] = useState<{
    open: boolean
    driverId: number
    driverName: string
    newStatus: ApprovalStatus
    loading: boolean
    reason: string
  }>({
    open: false,
    driverId: 0,
    driverName: '',
    newStatus: 'APPROVED',
    loading: false,
    reason: '',
  })

  // Load danh sách tài xế
  const loadDrivers = useCallback(async () => {
    setLoading(true)
    try {
      const data = await fetchAdminDrivers({
        keyword: keyword || undefined,
        status: status || undefined,
        page: page - 1,
        size: pageSize,
      })
      setDrivers(data.items)
      setTotal(data.totalItems)
    } catch (err) {
      message.error(getApiError(err).message)
    } finally {
      setLoading(false)
    }
  }, [keyword, status, page, pageSize])

  useEffect(() => {
    loadDrivers()
  }, [loadDrivers])

  // Helper render status tag
  const renderStatusTag = (st: ApprovalStatus) => {
    switch (st) {
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

  const renderVehicleTag = (type: string) => {
    switch (type) {
      case 'MOTORBIKE':
        return <Tag color="blue">Xe máy</Tag>
      case 'CAR':
        return <Tag color="purple">Ô tô</Tag>
      case 'BICYCLE':
        return <Tag color="cyan">Xe đạp</Tag>
      default:
        return <Tag>{type}</Tag>
    }
  }

  // Thao tác đổi trạng thái
  const promptStatusChange = (driverId: number, driverName: string, newStatus: ApprovalStatus) => {
    setActionModal({
      open: true,
      driverId,
      driverName,
      newStatus,
      loading: false,
      reason: '',
    })
  }

  const handleConfirmStatusChange = async () => {
    setActionModal((prev) => ({ ...prev, loading: true }))
    try {
      await updateAdminDriverStatus(
        actionModal.driverId,
        actionModal.newStatus,
        actionModal.reason || undefined
      )
      message.success(
        `Đã ${actionModal.newStatus === 'APPROVED' ? 'phê duyệt' : 'từ chối'} tài xế ${actionModal.driverName}`
      )
      loadDrivers()
      setActionModal((prev) => ({ ...prev, open: false }))
    } catch (err) {
      message.error(getApiError(err).message)
    } finally {
      setActionModal((prev) => ({ ...prev, loading: false }))
    }
  }

  const columns: ColumnsType<AdminDriver> = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 70,
      render: (id) => <Text code>#{id}</Text>,
    },
    {
      title: 'Họ và tên',
      dataIndex: 'fullName',
      key: 'fullName',
      render: (name, record) => (
        <div>
          <Text strong>{name}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: 12 }}>
            SĐT: {record.phone}
          </Text>
        </div>
      ),
    },
    {
      title: 'Phương tiện',
      dataIndex: 'vehicleType',
      key: 'vehicleType',
      render: (vType, record) => (
        <div>
          {renderVehicleTag(vType)}
          <br />
          <Text code style={{ fontSize: 12 }}>
            {record.licensePlate || 'Không biển số'}
          </Text>
        </div>
      ),
    },
    {
      title: 'Trực tuyến',
      dataIndex: 'isOnline',
      key: 'isOnline',
      align: 'center',
      render: (isOnline) => (
        <Badge
          status={isOnline ? 'success' : 'default'}
          text={isOnline ? 'Online' : 'Offline'}
        />
      ),
    },
    {
      title: 'Đánh giá',
      dataIndex: 'avgRating',
      key: 'avgRating',
      align: 'center',
      render: (rating) => <span>⭐ {rating?.toFixed(1) ?? '0.0'}</span>,
    },
    {
      title: 'Trạng thái duyệt',
      dataIndex: 'status',
      key: 'status',
      align: 'center',
      render: (st) => renderStatusTag(st),
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
                setSelectedDriver(record)
                setIsDetailOpen(true)
              }}
            />
          </Tooltip>
          {record.status !== 'APPROVED' && (
            <Tooltip title="Phê duyệt">
              <Button
                type="primary"
                size="small"
                icon={<CheckCircleOutlined />}
                onClick={() => promptStatusChange(record.id, record.fullName, 'APPROVED')}
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
                onClick={() => promptStatusChange(record.id, record.fullName, 'REJECTED')}
              >
                Từ chối
              </Button>
            </Tooltip>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: '4px 0' }}>
      <div style={{ marginBottom: 16 }}>
        <Title level={3} style={{ marginBottom: 4 }}>
          <CarOutlined style={{ marginRight: 8, color: '#1677ff' }} />
          Quản lý & Duyệt Hồ sơ Tài xế
        </Title>
        <Text type="secondary">
          Thẩm định hồ sơ đối tác vận chuyển trước khi cho phép tài xế bật chế độ trực tuyến và nhận cuốc giao hàng
        </Text>
      </div>

      <Alert
        type="info"
        showIcon
        message="Điều kiện bắt buộc tài xế nhận đơn"
        description="Tài xế bắt buộc phải ở trạng thái APPROVED thì mới có thể bật chế độ nhận đơn (Online). Nếu bị REJECTED hoặc PENDING, tài xế sẽ bị ngắt kết nối nhận đơn ngay lập tức."
        style={{ marginBottom: 16 }}
      />

      <Card>
        {/* Bộ lọc */}
        <Space wrap style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }}>
          <Space wrap>
            <Input
              placeholder="Tìm theo họ tên, SĐT, biển số xe..."
              prefix={<SearchOutlined />}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onPressEnter={() => {
                setPage(1)
                loadDrivers()
              }}
              style={{ width: 280 }}
              allowClear
            />
            <Select
              placeholder="Trạng thái duyệt"
              value={status}
              onChange={(val) => {
                setStatus(val)
                setPage(1)
              }}
              style={{ width: 160 }}
              allowClear
            >
              <Option value="PENDING">Chờ duyệt (PENDING)</Option>
              <Option value="APPROVED">Đã duyệt (APPROVED)</Option>
              <Option value="REJECTED">Từ chối (REJECTED)</Option>
            </Select>
            <Button type="primary" icon={<SearchOutlined />} onClick={() => loadDrivers()}>
              Tìm kiếm
            </Button>
          </Space>
          <Button icon={<ReloadOutlined />} onClick={() => loadDrivers()}>
            Làm mới
          </Button>
        </Space>

        {/* Bảng danh sách */}
        <Table
          columns={columns}
          dataSource={drivers}
          rowKey="id"
          loading={loading}
          pagination={{
            current: page,
            pageSize: pageSize,
            total: total,
            showSizeChanger: true,
            showTotal: (totalCount) => `Tổng cộng ${totalCount} tài xế`,
            onChange: (p, s) => {
              setPage(p)
              setPageSize(s)
            },
          }}
        />
      </Card>

      {/* MODAL CHI TIẾT */}
      <Modal
        title={`Chi tiết Hồ sơ Tài xế: ${selectedDriver?.fullName ?? ''}`}
        open={isDetailOpen}
        onCancel={() => setIsDetailOpen(false)}
        footer={[
          <Button key="close" onClick={() => setIsDetailOpen(false)}>
            Đóng
          </Button>,
        ]}
        width={650}
      >
        {selectedDriver && (
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="Mã tài xế">#{selectedDriver.id}</Descriptions.Item>
            <Descriptions.Item label="Mã tài khoản (User ID)">
              #{selectedDriver.userId}
            </Descriptions.Item>
            <Descriptions.Item label="Họ và tên">
              <Text strong>{selectedDriver.fullName}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Số điện thoại">{selectedDriver.phone}</Descriptions.Item>
            <Descriptions.Item label="Email">
              {selectedDriver.email || 'Chưa cung cấp'}
            </Descriptions.Item>
            <Descriptions.Item label="Loại phương tiện">
              {renderVehicleTag(selectedDriver.vehicleType)}
            </Descriptions.Item>
            <Descriptions.Item label="Biển số xe">
              <Text code>{selectedDriver.licensePlate || 'Không có'}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Trạng thái phê duyệt">
              {renderStatusTag(selectedDriver.status)}
            </Descriptions.Item>
            <Descriptions.Item label="Trạng thái trực tuyến">
              <Badge
                status={selectedDriver.isOnline ? 'success' : 'default'}
                text={selectedDriver.isOnline ? 'Đang trực tuyến (Online)' : 'Ngoại tuyến (Offline)'}
              />
            </Descriptions.Item>
            <Descriptions.Item label="Đánh giá trung bình">
              ⭐ {selectedDriver.avgRating?.toFixed(1) ?? '0.0'}
            </Descriptions.Item>
            <Descriptions.Item label="Ngày đăng ký">
              {selectedDriver.createdAt
                ? new Date(selectedDriver.createdAt).toLocaleString('vi-VN')
                : '-'}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>

      {/* MODAL XÁC NHẬN DUYỆT / TỪ CHỐI */}
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
          Bạn có chắc chắn muốn {actionModal.newStatus === 'APPROVED' ? 'phê duyệt' : 'từ chối'} hồ sơ tài xế{' '}
          <Text strong>"{actionModal.driverName}"</Text>?
        </p>
        {actionModal.newStatus === 'REJECTED' && (
          <p style={{ color: '#ff4d4f', fontSize: 13 }}>
            * Lưu ý: Tài xế bị từ chối sẽ không thể bật chế độ nhận đơn (Online) và không thể nhận chuyến.
          </p>
        )}
        <Input.TextArea
          rows={3}
          placeholder="Nhập lý do hoặc hướng dẫn bổ sung giấy tờ (tùy chọn)..."
          value={actionModal.reason}
          onChange={(e) => setActionModal((prev) => ({ ...prev, reason: e.target.value }))}
        />
      </Modal>
    </div>
  )
}

export default AdminDriverApprovalPage
