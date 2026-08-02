export type Product = {
  id: number
  name: string
  price: number
  stock: number
}

export type CartItem = {
  productId: number
  productName: string
  price: number
  quantity: number
  stock: number
}

export type OrderItem = {
  id: number
  productId: number
  productName: string
  quantity: number
  price: number
  subtotal: number
}

export type OrderInvoice = {
  id: number
  total: number
  status: string
}

export type Order = {
  id: number
  customer: string
  status: string
  createdAt: string
  items: OrderItem[]
  invoice: OrderInvoice
}

export type Payment = {
  id: number
  orderId: number
  invoiceId: number
  amount: number
  status: string
  failMode: string
  createdAt: string
}

export type SagaEvent = {
  id: number
  orderId: number
  service: string
  type: string
  payload: string
  createdAt: string
}

type FailModeResponse = {
  mode?: string
  failMode?: string
  value?: string
}

const ORDERS_BASE = '/api/orders'
const PAYMENTS_BASE = '/api/payments'
const EVENTS_BASE = '/api/events'

async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
    ...init,
  })

  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || `${response.status} ${response.statusText}`)
  }

  return (await response.json()) as T
}

export async function getProducts() {
  return requestJson<Product[]>(`${ORDERS_BASE}/products`)
}

export async function createOrder(customerName: string) {
  return requestJson<Order>(`${ORDERS_BASE}/orders`, {
    method: 'POST',
    body: JSON.stringify({ customerName }),
  })
}

export async function addItem(orderId: number, productId: number, quantity: number) {
  return requestJson<Order>(`${ORDERS_BASE}/orders/${orderId}/items`, {
    method: 'POST',
    body: JSON.stringify({ productId, quantity }),
  })
}

export async function submitOrder(orderId: number) {
  return requestJson<Order>(`${ORDERS_BASE}/orders/${orderId}/submit`, {
    method: 'POST',
  })
}

export async function getOrder(orderId: number) {
  return requestJson<Order>(`${ORDERS_BASE}/orders/${orderId}`)
}

export async function getPayments(orderId?: number) {
  const query = typeof orderId === 'number' ? `?orderId=${orderId}` : ''
  return requestJson<Payment[]>(`${PAYMENTS_BASE}/payments${query}`)
}

export async function getPaymentFailMode() {
  const response = await requestJson<FailModeResponse | string>(`${PAYMENTS_BASE}/payments/fail-mode`)
  if (typeof response === 'string') {
    return response
  }

  return response.mode ?? response.failMode ?? response.value ?? 'never'
}

export async function setPaymentFailMode(mode: 'always' | 'never') {
  return requestJson<Record<string, string>>(`${PAYMENTS_BASE}/payments/fail-mode`, {
    method: 'POST',
    body: JSON.stringify({ mode }),
  })
}

export async function getEvents(orderId?: number) {
  const query = typeof orderId === 'number' ? `?orderId=${orderId}` : ''
  return requestJson<SagaEvent[]>(`${EVENTS_BASE}/events${query}`)
}
