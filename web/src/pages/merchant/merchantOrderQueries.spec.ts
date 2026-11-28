import assert from 'node:assert/strict'
import test from 'node:test'
import { QueryClient } from '@tanstack/react-query'
import { invalidateMerchantOrders, merchantOrderKeys } from './merchantOrderQueries.ts'

test('Merchant keys isolate account, generation, restaurant, tab and page', () => {
  const a = merchantOrderKeys.scope(30, 'session-a')
  const b = merchantOrderKeys.scope(31, 'session-b')
  assert.notDeepEqual(merchantOrderKeys.list(a, 18, 'ALL', 0), merchantOrderKeys.list(b, 18, 'ALL', 0))
  assert.notDeepEqual(merchantOrderKeys.detail(a, 890), merchantOrderKeys.detail(merchantOrderKeys.scope(30, 'new-session'), 890))
  assert.notDeepEqual(merchantOrderKeys.list(a, 18, 'ALL', 0), merchantOrderKeys.list(a, 19, 'ALL', 0))
  assert.notDeepEqual(merchantOrderKeys.list(a, 18, 'ALL', 0), merchantOrderKeys.list(a, 18, 'DELIVERED', 1))
})

test('A lifecycle signal stales every list/detail in its session and preserves other caches', async () => {
  const client = new QueryClient()
  const a = merchantOrderKeys.scope(30, 'session-a')
  const b = merchantOrderKeys.scope(31, 'session-b')
  const changed = [merchantOrderKeys.list(a, undefined, 'ALL', 0), merchantOrderKeys.list(a, 18, 'PREPARING', 1), merchantOrderKeys.detail(a, 890)]
  const preserved = [merchantOrderKeys.restaurants(a), merchantOrderKeys.list(b, 18, 'ALL', 0), ['notifications', 'CUSTOMER', 30]]
  for (const key of [...changed, ...preserved]) client.setQueryData(key, { fixture: true })
  await invalidateMerchantOrders(client, a)
  for (const key of changed) assert.equal(client.getQueryState(key)?.isInvalidated, true)
  for (const key of preserved) assert.equal(client.getQueryState(key)?.isInvalidated, false)
  client.clear()
})
