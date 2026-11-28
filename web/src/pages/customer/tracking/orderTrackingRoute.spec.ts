import assert from 'node:assert/strict'
import { test } from 'node:test'
import axios, { AxiosError, type AxiosResponse } from 'axios'
import { readRouteFailure, requestOrderTrackingRoute } from './orderTrackingRoute.ts'

test('Route request uses the scoped client, order ID, timeout and AbortSignal only', async () => {
  const signal = new AbortController().signal
  const payload = { orderId: 501, geometry: { type: 'LineString' } }
  const client = axios.create({
    baseURL: '/api', headers: { Authorization: 'Bearer fixture-token' },
    adapter: async (config) => {
      assert.equal(config.baseURL, '/api')
      assert.equal(config.url, '/orders/501/tracking/route')
      assert.equal(config.method, 'get')
      assert.equal(config.signal, signal)
      assert.equal(config.timeout, 8_000)
      assert.equal(config.params, undefined)
      assert.equal(config.data, undefined)
      assert.equal(config.headers.get('Authorization'), 'Bearer fixture-token')
      return { data: payload, status: 200, statusText: 'OK', headers: {}, config }
    },
  })
  assert.equal(await requestOrderTrackingRoute(client, 501, signal), payload)
})

test('reads Route error status and machine code without surfacing other response fields', async () => {
  const client = axios.create({
    adapter: async (config) => {
      const response: AxiosResponse = {
        status: 422, statusText: 'Unavailable', headers: {}, config,
        data: { code: 'ROUTE_PROFILE_UNSUPPORTED', message: 'synthetic payload' },
      }
      throw new AxiosError('Route error', AxiosError.ERR_BAD_REQUEST, config, undefined, response)
    },
  })
  await assert.rejects(requestOrderTrackingRoute(client, 501, new AbortController().signal), (error) => {
    assert.deepEqual(readRouteFailure(error), { status: 422, code: 'ROUTE_PROFILE_UNSUPPORTED' })
    return true
  })
})

test('normalizes the Axios timeout and leaves ordinary connection errors to Planner', () => {
  assert.deepEqual(readRouteFailure(new AxiosError('timeout', AxiosError.ECONNABORTED)), { status: 504 })
  assert.deepEqual(readRouteFailure(new AxiosError('timeout', AxiosError.ETIMEDOUT)), { status: 504 })
  assert.deepEqual(readRouteFailure(new AxiosError('network', AxiosError.ERR_NETWORK)), {})
})
