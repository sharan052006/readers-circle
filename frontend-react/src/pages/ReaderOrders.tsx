import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import ReaderLayout from '../components/ReaderLayout';
import { api, fmtDate, fmtTime, resolveMedia } from '../lib/api';
import { BOOK_PLACEHOLDER as FALLBACK } from '../lib/placeholders';
import { rupee } from './ReaderBooks';
import './ReaderOrders.css';

type OrderLine = {
  bookId?: string | null;
  title?: string;
  author?: string;
  coverImageUrl?: string | null;
  price?: number;
  qty?: number;
  lineTotal?: number;
};

type TimelineEntry = {
  stage?: string;
  title?: string;
  detail?: string;
  at?: string | null;
  done?: boolean;
};

type Order = {
  id: string;
  orderNumber?: string;
  status?: string;
  subtotal?: number;
  shipping?: number;
  total?: number;
  paymentMethod?: string;
  paymentStatus?: string;
  transactionId?: string | null;
  paidAt?: string | null;
  reader?: { name?: string; email?: string };
  address?: {
    fullName?: string;
    phone?: string;
    line1?: string;
    line2?: string | null;
    city?: string;
    state?: string;
    pincode?: string;
  };
  items?: OrderLine[];
  timeline?: TimelineEntry[];
  note?: string | null;
  trackingId?: string | null;
  orderDate?: string | null;
  createdAt?: string | null;
};

type TabFilter = 'ALL' | 'ACTIVE' | 'DELIVERED' | 'CANCELLED';

function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

function statusBadgeClass(s?: string): string {
  const v = (s || '').toUpperCase();
  if (v === 'DELIVERED') return 'ro-status delivered';
  if (v === 'SHIPPED') return 'ro-status shipped';
  if (v === 'CONFIRMED' || v === 'PAID') return 'ro-status confirmed';
  if (v === 'CANCELLED') return 'ro-status cancelled';
  return 'ro-status pending';
}

function prettyStatus(s?: string): string {
  const v = (s || '').toUpperCase();
  if (v === 'PENDING') return '● Order Placed';
  if (v === 'CONFIRMED') return '● Confirmed';
  if (v === 'SHIPPED') return '● Out for Delivery';
  if (v === 'DELIVERED') return '✔ Delivered';
  if (v === 'CANCELLED') return '✕ Cancelled';
  return '● ' + (v.charAt(0) + v.slice(1).toLowerCase());
}

export default function ReaderOrders() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState('');
  const [tab, setTab] = useState<TabFilter>('ALL');
  const [q, setQ] = useState('');
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);
  const [busyCancel, setBusyCancel] = useState(false);

  async function loadOrders() {
    setLoading(true);
    setErr('');
    try {
      const data = await api<Order[]>('/api/orders');
      const list = Array.isArray(data) ? data : [];
      setOrders(list);

      // Check if deep-linked to a specific orderId from checkout
      const targetId = searchParams.get('orderId') || searchParams.get('id');
      if (targetId) {
        const found = list.find((o) => o.id === targetId || o.orderNumber === targetId);
        if (found) {
          setSelectedOrder(found);
        } else {
          // Attempt direct fetch
          try {
            const single = await api<Order>(`/api/orders/${targetId}`);
            if (single) setSelectedOrder(single);
          } catch {
            /* ignore */
          }
        }
      }
    } catch (e) {
      setErr(errMsg(e));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadOrders();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function cancelOrder(orderId: string) {
    if (!window.confirm('Are you sure you want to cancel this order?')) return;
    setBusyCancel(true);
    try {
      const updated = await api<Order>(`/api/orders/${orderId}/cancel`, { method: 'POST' });
      setOrders((prev) => prev.map((o) => (o.id === orderId ? updated : o)));
      if (selectedOrder?.id === orderId) {
        setSelectedOrder(updated);
      }
    } catch (e) {
      alert(errMsg(e));
    } finally {
      setBusyCancel(false);
    }
  }

  const counts = useMemo(() => {
    const total = orders.length;
    const active = orders.filter((o) => {
      const s = (o.status || '').toUpperCase();
      return s === 'PENDING' || s === 'CONFIRMED' || s === 'SHIPPED';
    }).length;
    const delivered = orders.filter((o) => (o.status || '').toUpperCase() === 'DELIVERED').length;
    const cancelled = orders.filter((o) => (o.status || '').toUpperCase() === 'CANCELLED').length;
    return { total, active, delivered, cancelled };
  }, [orders]);

  const filteredOrders = useMemo(() => {
    let res = orders;
    if (tab === 'ACTIVE') {
      res = res.filter((o) => {
        const s = (o.status || '').toUpperCase();
        return s === 'PENDING' || s === 'CONFIRMED' || s === 'SHIPPED';
      });
    } else if (tab === 'DELIVERED') {
      res = res.filter((o) => (o.status || '').toUpperCase() === 'DELIVERED');
    } else if (tab === 'CANCELLED') {
      res = res.filter((o) => (o.status || '').toUpperCase() === 'CANCELLED');
    }

    const t = q.trim().toLowerCase();
    if (t) {
      res = res.filter((o) => {
        const num = (o.orderNumber || '').toLowerCase();
        const matchItem = (o.items || []).some(
          (item) => (item.title || '').toLowerCase().includes(t) || (item.author || '').toLowerCase().includes(t)
        );
        return num.includes(t) || matchItem;
      });
    }
    return res;
  }, [orders, tab, q]);

  function closeModal() {
    setSelectedOrder(null);
    if (searchParams.has('orderId')) {
      const next = new URLSearchParams(searchParams);
      next.delete('orderId');
      setSearchParams(next, { replace: true });
    }
  }

  return (
    <ReaderLayout active="orders">
      <div className="ro-wrap">
        <div className="ro-head">
          <h1 className="ro-title">📦 My Orders</h1>
          <p className="ro-sub">Track delivery status, view digital receipts, and manage your book orders.</p>
        </div>

        <div className="ro-toolbar">
          <div className="ro-tabs" role="tablist">
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'ALL'}
              className={'ro-tab' + (tab === 'ALL' ? ' on' : '')}
              onClick={() => setTab('ALL')}
            >
              All Orders <span className="ro-badge">{counts.total}</span>
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'ACTIVE'}
              className={'ro-tab' + (tab === 'ACTIVE' ? ' on' : '')}
              onClick={() => setTab('ACTIVE')}
            >
              Active <span className="ro-badge">{counts.active}</span>
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'DELIVERED'}
              className={'ro-tab' + (tab === 'DELIVERED' ? ' on' : '')}
              onClick={() => setTab('DELIVERED')}
            >
              Delivered <span className="ro-badge">{counts.delivered}</span>
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'CANCELLED'}
              className={'ro-tab' + (tab === 'CANCELLED' ? ' on' : '')}
              onClick={() => setTab('CANCELLED')}
            >
              Cancelled <span className="ro-badge">{counts.cancelled}</span>
            </button>
          </div>

          <div className="ro-search">
            <span className="lens" aria-hidden="true">🔍</span>
            <input
              type="search"
              placeholder="Search by order #, book or author…"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              aria-label="Search orders"
            />
          </div>
        </div>

        {err && <div className="sr-err" style={{ marginBottom: 16 }}>{err}</div>}

        {loading ? (
          <div className="sr-notice">Loading your orders…</div>
        ) : filteredOrders.length === 0 ? (
          <div className="ro-empty">
            <span className="ro-empty-icon" aria-hidden="true">📦</span>
            <h3>{orders.length === 0 ? 'No orders placed yet' : 'No matching orders'}</h3>
            <p>
              {orders.length === 0
                ? "Browse our curated book collection and place your first order."
                : 'Try adjusting your search query or switching tabs.'}
            </p>
            {orders.length === 0 && (
              <Link to="/reader-books" className="ro-btn" style={{ display: 'inline-block', textDecoration: 'none' }}>
                Discover Books →
              </Link>
            )}
          </div>
        ) : (
          <div className="ro-list">
            {filteredOrders.map((o) => {
              const status = (o.status || 'PENDING').toUpperCase();
              const canCancel = status === 'PENDING' || status === 'CONFIRMED';
              const items = o.items || [];
              const dateStr = fmtDate(o.orderDate || o.createdAt);
              const timeStr = fmtTime(o.orderDate || o.createdAt);

              return (
                <article className="ro-card" key={o.id}>
                  <div className="ro-card-head">
                    <div className="ro-card-meta">
                      <span className="ro-num">Order #{o.orderNumber || o.id.slice(0, 8)}</span>
                      <span className="ro-date">Placed on {dateStr} {timeStr ? `at ${timeStr}` : ''}</span>
                    </div>
                    <span className={statusBadgeClass(o.status)}>
                      {prettyStatus(o.status)}
                    </span>
                  </div>

                  <div className="ro-card-body">
                    <div className="ro-items-row">
                      {items.map((item, idx) => (
                        <div className="ro-item" key={item.bookId || idx}>
                          <img
                            src={resolveMedia(item.coverImageUrl) || FALLBACK}
                            alt=""
                            className="ro-item-img"
                            onError={(e) => {
                              e.currentTarget.onerror = null;
                              e.currentTarget.src = FALLBACK;
                            }}
                          />
                          <div className="ro-item-info">
                            <h4 className="ro-item-title">{item.title || 'Untitled Book'}</h4>
                            <p className="ro-item-author">{item.author ? `by ${item.author}` : ''}</p>
                            <span className="ro-item-price">
                              Qty: <b>{item.qty || 1}</b> · {rupee(item.lineTotal || (item.price || 0) * (item.qty || 1))}
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>

                    <div className="ro-card-foot">
                      <div className="ro-dest">
                        <span>📍 Deliver to: <b>{o.address?.fullName || o.reader?.name || 'You'}</b></span>
                        {o.address?.city && <span>· {o.address.city} ({o.address.pincode})</span>}
                      </div>

                      <div className="ro-card-actions">
                        <span style={{ fontSize: 14, fontWeight: 700, color: '#241a12', marginRight: 4 }}>
                          Total: <span style={{ color: '#6b2a1f' }}>{rupee(o.total)}</span>
                        </span>
                        {canCancel && (
                          <button
                            type="button"
                            className="ro-btn danger-ghost"
                            disabled={busyCancel}
                            onClick={() => void cancelOrder(o.id)}
                          >
                            Cancel
                          </button>
                        )}
                        <button
                          type="button"
                          className="ro-btn ghost"
                          onClick={() => setSelectedOrder(o)}
                        >
                          View Details →
                        </button>
                      </div>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}

        {/* Full Order Details Modal */}
        {selectedOrder && (
          <div className="ro-overlay" onClick={closeModal}>
            <div className="ro-modal" onClick={(e) => e.stopPropagation()}>
              <div className="ro-modal-head">
                <div>
                  <h2>Order #{selectedOrder.orderNumber || selectedOrder.id.slice(0, 8)}</h2>
                  <p>
                    Placed on {fmtDate(selectedOrder.orderDate || selectedOrder.createdAt)} ·{' '}
                    <span className={statusBadgeClass(selectedOrder.status)}>
                      {prettyStatus(selectedOrder.status)}
                    </span>
                  </p>
                </div>
                <button type="button" className="ro-close" onClick={closeModal} aria-label="Close modal">
                  ✕
                </button>
              </div>

              <div className="ro-modal-body">
                {/* Visual Order Timeline */}
                <div className="ro-timeline" aria-label="Order Tracking Timeline">
                  {(() => {
                    const st = (selectedOrder.status || '').toUpperCase();
                    const isCancelled = st === 'CANCELLED';
                    if (isCancelled) {
                      return (
                        <div style={{ width: '100%', textAlign: 'center', color: '#a8271e', fontWeight: 600, fontSize: 13.5 }}>
                          ✕ This order has been cancelled.
                        </div>
                      );
                    }
                    const stages = [
                      { key: 'PENDING', label: 'Placed' },
                      { key: 'CONFIRMED', label: 'Confirmed' },
                      { key: 'SHIPPED', label: 'Shipped' },
                      { key: 'DELIVERED', label: 'Delivered' },
                    ];
                    const orderLevel =
                      st === 'DELIVERED' ? 4 : st === 'SHIPPED' ? 3 : st === 'CONFIRMED' ? 2 : 1;

                    return stages.map((s, idx) => {
                      const stepNum = idx + 1;
                      const done = stepNum < orderLevel;
                      const active = stepNum === orderLevel;
                      return (
                        <div
                          key={s.key}
                          className={'ro-step-node' + (done ? ' done' : active ? ' active' : '')}
                        >
                          <span className="ro-dot">{done ? '✓' : stepNum}</span>
                          <span className="ro-step-title">{s.label}</span>
                        </div>
                      );
                    });
                  })()}
                </div>

                {/* Delivery Address & Payment Summary */}
                <div className="ro-grid2">
                  <div className="ro-sec">
                    <h4>📍 Delivery Address</h4>
                    {selectedOrder.address ? (
                      <div className="ro-kv">
                        <b>{selectedOrder.address.fullName}</b>
                        <span style={{ textTransform: 'none', color: '#4a4238' }}>
                          {selectedOrder.address.line1}
                          {selectedOrder.address.line2 ? ', ' + selectedOrder.address.line2 : ''}
                        </span>
                        <span style={{ textTransform: 'none', color: '#4a4238' }}>
                          {selectedOrder.address.city}, {selectedOrder.address.state} - {selectedOrder.address.pincode}
                        </span>
                        <span style={{ marginTop: 4, textTransform: 'none', color: '#241a12', fontWeight: 600 }}>
                          📞 +91 {selectedOrder.address.phone}
                        </span>
                      </div>
                    ) : (
                      <p style={{ margin: 0, color: '#8a8175', fontSize: 13 }}>Standard delivery address</p>
                    )}
                  </div>

                  <div className="ro-sec">
                    <h4>💳 Payment Details</h4>
                    <div className="ro-kv">
                      <span>Method</span>
                      <b>{selectedOrder.paymentMethod === 'UPI' ? 'Online (UPI / Cards / Net Banking)' : selectedOrder.paymentMethod || 'Online Payment'}</b>
                    </div>
                    <div className="ro-kv" style={{ marginTop: 8 }}>
                      <span>Payment Status</span>
                      <b>
                        <span className={'ro-status ' + (selectedOrder.paymentStatus === 'PAID' ? 'confirmed' : 'pending')} style={{ padding: '2px 8px' }}>
                          {selectedOrder.paymentStatus || 'PAID'}
                        </span>
                      </b>
                    </div>
                    {selectedOrder.transactionId && (
                      <div className="ro-kv" style={{ marginTop: 8 }}>
                        <span>Transaction ID</span>
                        <b style={{ fontFamily: 'monospace', fontSize: 12 }}>{selectedOrder.transactionId}</b>
                      </div>
                    )}
                  </div>
                </div>

                {/* Ordered Items */}
                <div className="ro-sec">
                  <h4>📚 Items Ordered ({(selectedOrder.items || []).length})</h4>
                  <table className="ro-items-table">
                    <tbody>
                      {(selectedOrder.items || []).map((item, i) => (
                        <tr key={item.bookId || i}>
                          <td style={{ width: 44, paddingRight: 12 }}>
                            <img
                              src={resolveMedia(item.coverImageUrl) || FALLBACK}
                              alt=""
                              style={{ width: 40, height: 54, objectFit: 'cover', borderRadius: 6, display: 'block' }}
                              onError={(e) => {
                                e.currentTarget.onerror = null;
                                e.currentTarget.src = FALLBACK;
                              }}
                            />
                          </td>
                          <td>
                            <b style={{ fontSize: 13.5, color: '#241a12', display: 'block' }}>{item.title}</b>
                            <span style={{ fontSize: 12, color: '#6b6257' }}>{item.author}</span>
                          </td>
                          <td style={{ textAlign: 'center', width: 70, color: '#4a4238', fontSize: 13 }}>
                            × {item.qty || 1}
                          </td>
                          <td style={{ textAlign: 'right', width: 90, fontWeight: 700, color: '#241a12', fontSize: 13.5 }}>
                            {rupee(item.lineTotal || (item.price || 0) * (item.qty || 1))}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>

                  <div className="ro-total-box" style={{ marginTop: 12 }}>
                    <div className="ro-total-row">
                      <span>Items Subtotal</span>
                      <b>{rupee(selectedOrder.subtotal || selectedOrder.total)}</b>
                    </div>
                    <div className="ro-total-row">
                      <span>Shipping Charges</span>
                      <b style={{ color: '#2c7a3f' }}>{selectedOrder.shipping ? rupee(selectedOrder.shipping) : 'FREE'}</b>
                    </div>
                    <div className="ro-total-row grand">
                      <span>Total Amount Paid</span>
                      <b>{rupee(selectedOrder.total)}</b>
                    </div>
                  </div>
                </div>
              </div>

              <div className="ro-modal-foot">
                {(() => {
                  const st = (selectedOrder.status || '').toUpperCase();
                  const canCancel = st === 'PENDING' || st === 'CONFIRMED';
                  return canCancel ? (
                    <button
                      type="button"
                      className="ro-btn danger-ghost"
                      disabled={busyCancel}
                      onClick={() => void cancelOrder(selectedOrder.id)}
                    >
                      {busyCancel ? 'Cancelling…' : 'Cancel This Order'}
                    </button>
                  ) : (
                    <span style={{ fontSize: 12.5, color: '#8a8175' }}>
                      {st === 'DELIVERED' ? '✔ Order successfully delivered' : ''}
                    </span>
                  );
                })()}

                <button type="button" className="ro-btn" onClick={closeModal} style={{ marginLeft: 'auto' }}>
                  Close
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </ReaderLayout>
  );
}
