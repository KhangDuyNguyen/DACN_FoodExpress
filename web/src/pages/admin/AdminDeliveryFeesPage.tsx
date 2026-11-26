import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Card, Form, Input, InputNumber, Switch, Table, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { getApiError } from '../../api/apiError'
import {
  createDeliveryFeeRule,
  deliveryFeeRulesQueryKey,
  fetchDeliveryFeeRules,
  setDeliveryFeeRuleActive,
  type DeliveryFeeRule,
  type DeliveryFeeRuleBody,
} from '../../api/deliveryFees'

// Trang admin phi ship (Khang, tuan 6). Dat hang dung quy tac DANG BAT MOI NHAT; don da dat khong doi phi.

function vnd(amount: number) {
  return `${amount.toLocaleString('vi-VN')}đ`
}

export default function AdminDeliveryFeesPage() {
  const queryClient = useQueryClient()
  const [form] = Form.useForm<DeliveryFeeRuleBody>()

  const rules = useQuery({ queryKey: deliveryFeeRulesQueryKey, queryFn: fetchDeliveryFeeRules })

  const createMutation = useMutation({
    mutationFn: createDeliveryFeeRule,
    onSuccess: (rule) => {
      message.success(`Đã tạo "${rule.name}", đặt hàng từ giờ dùng quy tắc này`)
      form.resetFields()
      queryClient.invalidateQueries({ queryKey: deliveryFeeRulesQueryKey })
    },
    onError: (error) => message.error(getApiError(error).message),
  })

  const activeMutation = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) => setDeliveryFeeRuleActive(id, active),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: deliveryFeeRulesQueryKey }),
    // Vd tat quy tac dang bat cuoi cung -> 409 "Phải còn ít nhất 1 quy tắc phí giao đang bật"
    onError: (error) => message.error(getApiError(error).message),
  })

  const columns: ColumnsType<DeliveryFeeRule> = [
    {
      title: 'Tên quy tắc',
      dataIndex: 'name',
      render: (name: string, rule) => (
        <span>
          {name} {rule.applied && <Tag color="green">Đang áp dụng</Tag>}
        </span>
      ),
    },
    { title: 'Phí cơ bản', dataIndex: 'baseFee', align: 'right', render: vnd },
    { title: 'Trong (km)', dataIndex: 'baseDistanceKm', align: 'right' },
    { title: 'Mỗi km thêm', dataIndex: 'perKmFee', align: 'right', render: vnd },
    {
      title: 'Bật',
      dataIndex: 'active',
      render: (active: boolean, rule) => (
        <Switch
          checked={active}
          loading={activeMutation.isPending && activeMutation.variables?.id === rule.id}
          onChange={(checked) => activeMutation.mutate({ id: rule.id, active: checked })}
        />
      ),
    },
    {
      title: 'Ngày tạo',
      dataIndex: 'createdAt',
      render: (value: string) => new Date(value).toLocaleString('vi-VN'),
    },
  ]

  return (
    <Card>
      <Typography.Title level={4}>Phí giao hàng</Typography.Title>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        title="Đặt hàng dùng quy tắc đang bật mới nhất (gắn nhãn Đang áp dụng). Phí = phí cơ bản trong số km cơ bản, sau đó cộng phí mỗi km. Đơn đã đặt không đổi phí."
      />
      {rules.isError && <Typography.Text type="danger">{getApiError(rules.error).message}</Typography.Text>}
      <Table rowKey="id" columns={columns} dataSource={rules.data ?? []} loading={rules.isFetching} pagination={false} />

      <Typography.Title level={5} style={{ marginTop: 24 }}>
        Tạo quy tắc mới
      </Typography.Title>
      <Form
        form={form}
        layout="inline"
        initialValues={{ baseDistanceKm: 2 }}
        onFinish={(values) => createMutation.mutate({ ...values, name: values.name.trim() })}
      >
        <Form.Item name="name" rules={[{ required: true, whitespace: true, message: 'Nhập tên quy tắc' }]}>
          <Input placeholder="Tên, vd Giờ cao điểm" maxLength={100} style={{ width: 200 }} />
        </Form.Item>
        <Form.Item name="baseFee" label="Phí cơ bản (đ)" rules={[{ required: true, message: 'Nhập phí cơ bản' }]}>
          <InputNumber min={0} step={1000} precision={0} style={{ width: 130 }} />
        </Form.Item>
        <Form.Item name="baseDistanceKm" label="Trong (km)" rules={[{ required: true, message: 'Nhập số km' }]}>
          <InputNumber min={0} max={50} step={0.5} precision={2} style={{ width: 100 }} />
        </Form.Item>
        <Form.Item name="perKmFee" label="Mỗi km thêm (đ)" rules={[{ required: true, message: 'Nhập phí mỗi km' }]}>
          <InputNumber min={0} step={500} precision={0} style={{ width: 130 }} />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit" loading={createMutation.isPending}>
            Tạo và áp dụng
          </Button>
        </Form.Item>
      </Form>
    </Card>
  )
}
