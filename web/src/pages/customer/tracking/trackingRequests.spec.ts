import assert from 'node:assert/strict'
import { test } from 'node:test'
import axios, { AxiosError, type AxiosAdapter, type AxiosResponse } from 'axios'
import { createTrackingRequests, trackingAccessError } from './trackingRequests.ts'

test('a delayed 401 from the old tracking session cannot clear the new token or redirect', async (context) => {
  const originalAdapter = axios.defaults.adapter
  let activeToken = 'fixture-old-session'
  let redirects = 0
  const storage = { getItem: () => activeToken, removeItem: () => { activeToken = '' } }
  const location = { pathname: '/orders/11', set href(_value: string) { redirects += 1 } }
  context.mock.method(globalThis, 'fetch', () => { throw new Error('Unexpected network access') })
  const globals = ['localStorage', 'window'] as const
  const savedDescriptors = globals.map((key) => Object.getOwnPropertyDescriptor(globalThis, key))
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: storage })
  Object.defineProperty(globalThis, 'window', { configurable: true, value: { location } })
  const failOld: Array<() => void> = []
  const adapter: AxiosAdapter = (config) => {
    if (config.headers.get('Authorization') === 'Bearer fixture-new-session') {
      return Promise.resolve({ data: { id: 11 }, status: 200, statusText: 'OK', headers: {}, config })
    }
    assert.equal(config.headers.get('Authorization'), 'Bearer fixture-old-session')
    return new Promise((_, reject) => {
      const response: AxiosResponse = { data: null, status: 401, statusText: 'Unauthorized', headers: {}, config }
      failOld.push(() => reject(new AxiosError('Unauthorized', AxiosError.ERR_BAD_REQUEST, config, undefined, response)))
    })
  }
  axios.defaults.adapter = adapter

  try {
    const old = createTrackingRequests('/api', activeToken)
    const signal = new AbortController().signal
    const pending = [old.order(11, signal), old.snapshot(11, signal), old.cancel(11, 'fixture'), old.route(11, signal)]
    activeToken = 'fixture-new-session'
    const next = createTrackingRequests('/api', activeToken)
    assert.equal((await next.order(11, signal)).id, 11)
    assert.equal(failOld.length, 4)
    for (const respond of failOld) respond()
    await Promise.all(pending.map((request) => assert.rejects(request, (error) => trackingAccessError(error) === 401)))
    assert.equal(storage.getItem(), 'fixture-new-session')
    assert.equal(redirects, 0)
    assert.equal((await next.order(11, signal)).id, 11)
  } finally {
    axios.defaults.adapter = originalAdapter
    for (const [index, key] of globals.entries()) {
      const descriptor = savedDescriptors[index]
      if (descriptor) Object.defineProperty(globalThis, key, descriptor)
      else Reflect.deleteProperty(globalThis, key)
    }
  }
})
