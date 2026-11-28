import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  confirmedNewNotifications, notificationOrderPath, notificationSocketUrl,
  readNotificationPage, readNotificationSignal, uniqueNotificationItems,
} from './notificationRules.ts'

test('accepts only valid realtime identifiers and event types', () => {
  assert.equal(readNotificationSignal('{"notificationId":105,"eventType":"ORDER_DELIVERED"}'), 105)
  for (const input of ['', '{}', '{"notificationId":0,"eventType":"ORDER_PLACED"}',
    '{"notificationId":"1","eventType":"ORDER_PLACED"}', '{"notificationId":1,"eventType":"bad"}',
    '{"notificationId":9007199254740992,"eventType":"ORDER_PLACED"}']) {
    assert.equal(readNotificationSignal(input), null)
  }
})

test('only positive safe order IDs produce an internal order route', () => {
  assert.equal(notificationOrderPath(11), '/orders/11')
  for (const id of [null, -1, 0, 1.5, '11', Number.MAX_SAFE_INTEGER + 1]) {
    assert.equal(notificationOrderPath(id), null)
  }
})

const item = (id: number) => ({
  id, type: 'ORDER', eventType: 'ORDER_DELIVERED', title: 'Giao hàng thành công', body: 'Đơn hàng đã được giao.',
  isRead: false, createdAt: '2026-11-26T08:00:00Z', data: { orderId: 11 },
})

test('preserves server counts and opaque cursors independently of row count', () => {
  assert.deepEqual(readNotificationPage({ items: [item(42)], unreadCount: 86, nextCursor: 'Y3Vyc29y' }), {
    items: [item(42)], unreadCount: 86, nextCursor: 'Y3Vyc29y',
  })
})

test('rejects invalid envelopes and counts', () => {
  const valid = { items: [], unreadCount: 0, nextCursor: null }
  for (const bad of [null, [], {}, { ...valid, items: {} }, { ...valid, unreadCount: -1 },
    { ...valid, unreadCount: '30' }, { ...valid, unreadCount: Infinity }, { ...valid, nextCursor: '' }]) {
    assert.throws(() => readNotificationPage(bad), /Dữ liệu thông báo không hợp lệ/)
  }
})

test('drops malformed rows while preserving valid legacy rows and trusted metadata', () => {
  const legacy = { ...item(7), eventType: null, data: null }
  const bad = [null, { ...item(8), title: {} }, { ...item(9), isRead: 0 },
    { ...item(10), createdAt: '2026-11-26T08:00:00' }, { ...item(11), id: -1 }]
  assert.deepEqual(readNotificationPage({
    items: [...bad, legacy, { ...item(6), data: { orderId: '11', url: '/admin' } }],
    unreadCount: 12, nextCursor: null,
  }).items, [{ ...legacy, data: {} }, { ...item(6), data: {} }])
})

test('deduplicates overlapping cursor pages and keeps the first snapshot of each ID', () => {
  const first = item(5)
  assert.deepEqual(uniqueNotificationItems([
    { items: [first, item(4)], unreadCount: 24, nextCursor: '4' },
    { items: [{ ...first, isRead: true }, item(3)], unreadCount: 24, nextCursor: null },
  ]), [first, item(4), item(3)])
})

test('only a new signal confirmed by REST can produce a toast including after reconnect', () => {
  const seen = new Set([4])
  const page = { items: [item(7), item(6), { ...item(5), isRead: true }, item(4)], unreadCount: 6, nextCursor: null }
  assert.deepEqual(confirmedNewNotifications(page, new Set(), seen), [])
  assert.deepEqual(confirmedNewNotifications(page, new Set([4, 6, 9]), seen), [])
  assert.deepEqual(confirmedNewNotifications({ ...page, items: [item(10), item(9), item(8), ...page.items] },
    new Set([10, 9, 7, 5]), seen), [item(10)])
  assert.deepEqual(confirmedNewNotifications({ ...page, items: [item(10)] }, new Set([10]), seen), [])
})

test('uses the Vite API origin and prefix for the native STOMP URL', () => {
  assert.equal(notificationSocketUrl('/api', 'http://127.0.0.1:5173'), 'ws://127.0.0.1:5173/ws')
  assert.equal(notificationSocketUrl('/gateway/api/', 'https://food.example'), 'wss://food.example/gateway/ws')
  assert.equal(notificationSocketUrl('https://api.example/api?discard#me', 'http://food.example'), 'wss://api.example/ws')
  assert.throws(() => notificationSocketUrl('file:///api', 'https://food.example'))
})
