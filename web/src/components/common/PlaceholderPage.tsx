import React from 'react'
import { Card, Tag, Typography, List, Button, Result, Space } from 'antd'
import { ToolOutlined, ArrowLeftOutlined, CheckCircleOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'

const { Title, Paragraph, Text } = Typography

interface PlaceholderPageProps {
  title: string
  subtitle: string
  targetWeek: string
  assignee?: string
  plannedFeatures: string[]
  backPath?: string
  badgeText?: string
}

export const PlaceholderPage: React.FC<PlaceholderPageProps> = ({
  title,
  subtitle,
  targetWeek,
  assignee = 'Sầm Xuân Điệp',
  plannedFeatures,
  backPath,
  badgeText = 'Đang phát triển theo kế hoạch'
}) => {
  const navigate = useNavigate()

  return (
    <div style={{ maxWidth: 900, margin: '0 auto', padding: '12px 0' }}>
      <Card
        style={{
          borderRadius: 12,
          boxShadow: '0 2px 10px rgba(0, 0, 0, 0.05)',
        }}
      >
        <Result
          icon={<ToolOutlined style={{ color: '#fa8c16' }} />}
          title={
            <Space direction="vertical" align="center" size={4}>
              <Title level={3} style={{ margin: 0, textAlign: 'center' }}>
                {title}
              </Title>
              <Space>
                <Tag color="orange">{badgeText}</Tag>
                <Tag color="blue">{targetWeek}</Tag>
                <Tag color="cyan">Phụ trách: {assignee}</Tag>
              </Space>
            </Space>
          }
          subTitle={
            <Paragraph style={{ color: '#595959', maxWidth: 600, margin: '8px auto 0' }}>
              {subtitle}
            </Paragraph>
          }
          extra={
            backPath ? (
              <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(backPath)}>
                Quay lại Tổng quan
              </Button>
            ) : undefined
          }
        >
          <div style={{ background: '#fafafa', padding: 20, borderRadius: 8, marginTop: 12 }}>
            <Text strong style={{ fontSize: 15, display: 'block', marginBottom: 12 }}>
              Các tính năng dự kiến triển khai:
            </Text>
            <List
              size="small"
              dataSource={plannedFeatures}
              renderItem={(item) => (
                <List.Item>
                  <Space align="start">
                    <CheckCircleOutlined style={{ color: '#52c41a', marginTop: 4 }} />
                    <Text>{item}</Text>
                  </Space>
                </List.Item>
              )}
            />
          </div>
        </Result>
      </Card>
    </div>
  )
}

export default PlaceholderPage
