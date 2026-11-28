import type { QueryClient } from '@tanstack/react-query'

export type MerchantOrderScope = readonly ['merchant-orders', number, string]

export const merchantOrderKeys = {
  scope: (userId: number, sessionId: string): MerchantOrderScope => ['merchant-orders', userId, sessionId],
  restaurants: (scope: MerchantOrderScope) => [...scope, 'restaurants'] as const,
  list: (scope: MerchantOrderScope, restaurantId: number | undefined, tab: string, page: number) =>
    [...scope, 'list', restaurantId, tab, page] as const,
  detail: (scope: MerchantOrderScope, id: number | null) => [...scope, 'detail', id] as const,
}

export function invalidateMerchantOrders(client: QueryClient, scope: MerchantOrderScope) {
  return client.invalidateQueries({
    queryKey: scope,
    predicate: query => query.queryKey[3] === 'list' || query.queryKey[3] === 'detail',
  }, { cancelRefetch: false })
}
