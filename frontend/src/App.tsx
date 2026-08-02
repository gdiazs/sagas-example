import { useEffect, useMemo, useState, type ChangeEvent } from 'react'
import { Badge } from 'primereact/badge'
import { Button } from 'primereact/button'
import { Card } from 'primereact/card'
import { Divider } from 'primereact/divider'
import { InputNumber } from 'primereact/inputnumber'
import { InputText } from 'primereact/inputtext'
import { Message } from 'primereact/message'
import { ProgressSpinner } from 'primereact/progressspinner'
import { Tag } from 'primereact/tag'
import {
  addItem,
  createOrder,
  getEvents,
  getOrder,
  getPaymentFailMode,
  getPayments,
  getProducts,
  setPaymentFailMode,
  submitOrder,
  type CartItem,
  type Order,
  type Payment,
  type Product,
  type SagaEvent,
} from './api'
import './App.css'

type LoadState = 'idle' | 'loading' | 'ready' | 'error'

const currency = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
})

function formatDate(value?: string) {
  return value ? new Date(value).toLocaleString() : 'N/A'
}

function getOrderSeverity(status?: string) {
  switch (status) {
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'PROCESSING':
      return 'warning'
    case 'CREATED':
    default:
      return 'info'
  }
}

function getPaymentSeverity(status?: string) {
  switch (status) {
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'PENDING':
    default:
      return 'warning'
  }
}

function getInvoiceSeverity(status?: string) {
  switch (status) {
    case 'PAID':
      return 'success'
    case 'VOID':
      return 'danger'
    case 'PENDING':
    default:
      return 'warning'
  }
}

function App() {
  const [products, setProducts] = useState<Product[]>([])
  const [cart, setCart] = useState<CartItem[]>([])
  const [customerName, setCustomerName] = useState('Ava Johnson')
  const [order, setOrder] = useState<Order | null>(null)
  const [payments, setPayments] = useState<Payment[]>([])
  const [events, setEvents] = useState<SagaEvent[]>([])
  const [failMode, setFailMode] = useState<'always' | 'never'>('never')
  const [catalogState, setCatalogState] = useState<LoadState>('loading')
  const [submissionState, setSubmissionState] = useState<LoadState>('idle')
  const [syncError, setSyncError] = useState<string | null>(null)

  useEffect(() => {
    let active = true

    async function loadInitialData() {
      try {
        setCatalogState('loading')
        const [catalog, currentFailMode] = await Promise.all([
          getProducts(),
          getPaymentFailMode(),
        ])

        if (!active) return

        setProducts(catalog)
        setFailMode(currentFailMode === 'always' ? 'always' : 'never')
        setCatalogState('ready')
      } catch (error) {
        if (!active) return
        setCatalogState('error')
        setSyncError(error instanceof Error ? error.message : 'Failed to load catalog')
      }
    }

    void loadInitialData()

    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    const orderId = order?.id

    if (typeof orderId !== 'number') {
      return
    }

    const currentOrderId = orderId as number

    let active = true

    async function loadOrderDetails() {
      try {
        const [freshOrder, freshPayments, freshEvents] = await Promise.all([
          getOrder(currentOrderId),
          getPayments(currentOrderId),
          getEvents(currentOrderId),
        ])

        if (!active) return

        setOrder(freshOrder)
        setPayments(freshPayments)
        setEvents(freshEvents)
      } catch (error) {
        if (!active) return
        setSyncError(error instanceof Error ? error.message : 'Failed to load order details')
      }
    }

    void loadOrderDetails()

    return () => {
      active = false
    }
  }, [order?.id])

  const cartTotal = useMemo(
    () => cart.reduce((sum, item) => sum + item.price * item.quantity, 0),
    [cart],
  )

  const orderTotal = order?.invoice?.total ?? cartTotal
  const latestPayment = payments[0]

  function addToCart(product: Product) {
    setCart((current) => {
      const existing = current.find((item) => item.productId === product.id)
      if (existing) {
        return current.map((item) =>
          item.productId === product.id
            ? { ...item, quantity: Math.min(item.quantity + 1, item.stock) }
            : item,
        )
      }

      return [
        ...current,
        {
          productId: product.id,
          productName: product.name,
          price: product.price,
          quantity: 1,
          stock: product.stock,
        },
      ]
    })
  }

  function updateQuantity(productId: number, quantity: number | null) {
    setCart((current) =>
      current
        .map((item) =>
          item.productId === productId
            ? { ...item, quantity: Math.max(1, Math.min(quantity ?? 1, item.stock)) }
            : item,
        )
        .filter((item) => item.quantity > 0),
    )
  }

  function removeItem(productId: number) {
    setCart((current) => current.filter((item) => item.productId !== productId))
  }

  async function handleSetFailMode(mode: 'always' | 'never') {
    try {
      setSyncError(null)
      await setPaymentFailMode(mode)
      setFailMode(mode)
    } catch (error) {
      setSyncError(error instanceof Error ? error.message : 'Failed to change fail mode')
    }
  }

  async function handleSubmitOrder() {
    if (!customerName.trim() || cart.length === 0 || submissionState === 'loading') {
      return
    }

    setSubmissionState('loading')
    setSyncError(null)

    try {
      const created = await createOrder(customerName.trim())
      let latestOrder = created

      for (const item of cart) {
        latestOrder = await addItem(created.id, item.productId, item.quantity)
      }

      latestOrder = await submitOrder(created.id)
      setOrder(latestOrder)
      setCart([])

      const [freshPayments, freshEvents] = await Promise.all([
        getPayments(latestOrder.id),
        getEvents(latestOrder.id),
      ])
      setPayments(freshPayments)
      setEvents(freshEvents)
    } catch (error) {
      setSyncError(error instanceof Error ? error.message : 'Order submission failed')
    } finally {
      setSubmissionState('idle')
    }
  }

  const eventTypes = events.map((event) => event.type)

  return (
    <main className="app-shell">
      <section className="dashboard">
        <header className="workspace-head">
          <div>
            <p className="eyebrow">Saga commerce workspace</p>
            <h1>Cart and order processing</h1>
          </div>
          <div className="workspace-metrics">
            <Card className="metric-card">
              <div className="metric-value">{products.length}</div>
              <div className="metric-label">catalog products</div>
            </Card>
            <Card className="metric-card">
              <div className="metric-value">{cart.length}</div>
              <div className="metric-label">items in cart</div>
            </Card>
            <Card className="metric-card">
              <div className="metric-value">{order?.status ?? 'draft'}</div>
              <div className="metric-label">current order state</div>
            </Card>
          </div>
        </header>

        {syncError ? <Message severity="error" text={syncError} /> : null}

        <div className="workspace-grid">
          <section className="panel">
            <div className="panel-head">
              <div>
                <p className="panel-kicker">Catalog</p>
                <h2>Available products</h2>
              </div>
              {catalogState === 'loading' ? (
                <ProgressSpinner style={{ width: '1.5rem', height: '1.5rem' }} strokeWidth="4" />
              ) : null}
            </div>

            <div className="catalog-grid">
              {products.map((product) => {
                const cartItem = cart.find((item) => item.productId === product.id)
                return (
                  <Card key={product.id} className="product-card">
                    <div className="product-name">{product.name}</div>
                    <div className="product-meta">
                      <span>{currency.format(product.price)}</span>
                      <Tag
                        severity={product.stock > 0 ? 'success' : 'danger'}
                        value={`${product.stock} in stock`}
                      />
                    </div>
                    <div className="product-actions">
                      <Button
                        label={cartItem ? 'Add another' : 'Add to cart'}
                        icon="pi pi-shopping-cart"
                        onClick={() => addToCart(product)}
                        disabled={product.stock <= 0}
                      />
                    </div>
                  </Card>
                )
              })}
            </div>
          </section>

          <aside className="panel">
            <div className="panel-head">
              <div>
                <p className="panel-kicker">Draft cart</p>
                <h2>Customer and cart</h2>
              </div>
              <Badge value={cart.length} severity="info" />
            </div>

            <div className="form-stack">
              <label className="field-label" htmlFor="customer-name">
                Customer name
              </label>
              <InputText
                id="customer-name"
                value={customerName}
                onChange={(event: ChangeEvent<HTMLInputElement>) =>
                  setCustomerName(event.currentTarget.value)
                }
                className="app-input"
                placeholder="Enter customer name"
              />

              <Divider />

              <div className="cart-list">
                {cart.length === 0 ? (
                  <div className="empty-state">
                    Your cart is empty. Pick products from the catalog to start.
                  </div>
                ) : (
                  cart.map((item) => (
                    <div key={item.productId} className="cart-row">
                      <div className="cart-info">
                        <div className="cart-title">{item.productName}</div>
                        <div className="cart-subtitle">{currency.format(item.price)} each</div>
                      </div>
                      <InputNumber
                        value={item.quantity}
                        min={1}
                        max={item.stock}
                        showButtons
                        buttonLayout="horizontal"
                        incrementButtonIcon="pi pi-plus"
                        decrementButtonIcon="pi pi-minus"
                        inputClassName="qty-input"
                        onValueChange={(event) =>
                          updateQuantity(item.productId, event.value ?? null)
                        }
                      />
                      <Button
                        icon="pi pi-trash"
                        rounded
                        text
                        severity="danger"
                        onClick={() => removeItem(item.productId)}
                      />
                    </div>
                  ))
                )}
              </div>

              <div className="totals">
                <div>
                  <span>Subtotal</span>
                  <strong>{currency.format(cartTotal)}</strong>
                </div>
                <div>
                  <span>Order id</span>
                  <strong>{order?.id ?? 'Not submitted yet'}</strong>
                </div>
              </div>

              <Button
                label={submissionState === 'loading' ? 'Submitting...' : 'Submit order'}
                icon="pi pi-send"
                className="submit-button"
                onClick={() => void handleSubmitOrder()}
                disabled={cart.length === 0 || !customerName.trim() || submissionState === 'loading'}
              />
            </div>
          </aside>
        </div>

        <div className="workspace-grid lower-grid">
          <section className="panel">
            <div className="panel-head">
              <div>
                <p className="panel-kicker">Order</p>
                <h2>Status and invoice</h2>
              </div>
              {order ? <Tag severity={getOrderSeverity(order.status)} value={order.status} /> : null}
            </div>

            {order ? (
              <div className="status-stack">
                <div className="status-row">
                  <span>Customer</span>
                  <strong>{order.customer}</strong>
                </div>
                <div className="status-row">
                  <span>Created</span>
                  <strong>{formatDate(order.createdAt)}</strong>
                </div>
                <div className="status-row">
                  <span>Items</span>
                  <strong>{order.items.length}</strong>
                </div>
                <div className="status-row">
                  <span>Invoice total</span>
                  <strong>{currency.format(order.invoice?.total ?? 0)}</strong>
                </div>
                <div className="status-row">
                  <span>Invoice</span>
                  <Tag
                    severity={getInvoiceSeverity(order.invoice?.status)}
                    value={order.invoice?.status ?? 'N/A'}
                  />
                </div>

                <Divider />

                <div className="order-items">
                  {order.items.map((item) => (
                    <div key={item.id} className="order-item">
                      <div>
                        <div className="cart-title">{item.productName}</div>
                        <div className="cart-subtitle">
                          {item.quantity} x {currency.format(item.price)}
                        </div>
                      </div>
                      <strong>{currency.format(item.subtotal)}</strong>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <div className="empty-state">Submit an order to see its live status here.</div>
            )}
          </section>

          <section className="panel">
            <div className="panel-head">
              <div>
                <p className="panel-kicker">Payments</p>
                <h2>Fail mode control</h2>
              </div>
              <Tag severity={failMode === 'always' ? 'danger' : 'success'} value={failMode} />
            </div>

            <div className="toggle-row">
              <Button
                label="Never fail"
                icon="pi pi-check"
                severity={failMode === 'never' ? 'success' : 'secondary'}
                outlined={failMode !== 'never'}
                onClick={() => void handleSetFailMode('never')}
              />
              <Button
                label="Always fail"
                icon="pi pi-times"
                severity={failMode === 'always' ? 'danger' : 'secondary'}
                outlined={failMode !== 'always'}
                onClick={() => void handleSetFailMode('always')}
              />
            </div>

            <Divider />

            <div className="payment-summary">
              <div className="status-row">
                <span>Latest payment</span>
                <Tag
                  severity={getPaymentSeverity(latestPayment?.status)}
                  value={latestPayment?.status ?? 'N/A'}
                />
              </div>
              <div className="status-row">
                <span>Payment amount</span>
                <strong>{currency.format(latestPayment?.amount ?? orderTotal)}</strong>
              </div>
              <div className="status-row">
                <span>Payment id</span>
                <strong>{latestPayment?.id ?? 'N/A'}</strong>
              </div>
            </div>
          </section>
        </div>

        <div className="workspace-grid lower-grid">
          <section className="panel">
            <div className="panel-head">
              <div>
                <p className="panel-kicker">Event trail</p>
                <h2>Saga timeline</h2>
              </div>
              <Tag value={`${events.length} events`} severity="info" />
            </div>

            {events.length > 0 ? (
              <div className="timeline">
                {events.map((event) => (
                  <div key={event.id} className="timeline-item">
                    <div className="timeline-dot" />
                    <div className="timeline-body">
                      <div className="timeline-top">
                        <strong>{event.type}</strong>
                        <span>{formatDate(event.createdAt)}</span>
                      </div>
                      <div className="timeline-meta">
                        <Tag value={event.service} severity="secondary" />
                      </div>
                      <pre className="timeline-payload">{event.payload}</pre>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="empty-state">The event trail will appear after submission.</div>
            )}
          </section>

          <section className="panel">
            <div className="panel-head">
              <div>
                <p className="panel-kicker">Quick view</p>
                <h2>Current state</h2>
              </div>
            </div>

            <div className="summary-list">
              <div className="summary-row">
                <span>Order status</span>
                <Tag severity={getOrderSeverity(order?.status)} value={order?.status ?? 'DRAFT'} />
              </div>
              <div className="summary-row">
                <span>Invoice status</span>
                <Tag
                  severity={getInvoiceSeverity(order?.invoice?.status)}
                  value={order?.invoice?.status ?? 'PENDING'}
                />
              </div>
              <div className="summary-row">
                <span>Payment status</span>
                <Tag
                  severity={getPaymentSeverity(latestPayment?.status)}
                  value={latestPayment?.status ?? 'PENDING'}
                />
              </div>
              <div className="summary-row">
                <span>Last event</span>
                <strong>{eventTypes[0] ?? 'No events yet'}</strong>
              </div>
            </div>
          </section>
        </div>
      </section>
    </main>
  )
}

export default App
