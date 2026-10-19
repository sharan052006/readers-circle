import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../../lib/api';
import { fmtAddedOn, rupee } from './AdminBooks';
import './StoreAdmin.css';

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

type OrderPage = {
  content: Order[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

type OrderStats = {
  totalOrders?: number;
  pendingOrders?: number;
  shippedOrders?: number;
  deliveredOrders?: number;
};

import { BOOK_PLACEHOLDER as FALLBACK } from '../../lib/placeholders';

const STATUSES = ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'] as const;

const DOT: Record<string, string> = {
  PENDING: '#D9A21B',
  CONFIRMED: '#2B6CB0',
  SHIPPED: '#6B46C1',
  DELIVERED: '#2F7A44',
  CANCELLED: '#8A8175',
};

const PAGE_SIZE = 8;

function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

function imgErr(e: React.SyntheticEvent<HTMLImageElement>) {
  const img = e.currentTarget;
  img.onerror = null;
  img.src = FALLBACK;
}

function prettyStatus(s?: string): string {
  const v = (s || '').toUpperCase();
  return v.charAt(0) + v.slice(1).toLowerCase();
}

function fmtDateTime(iso?: string | null): [string, string] {
  if (!iso) return ['–', ''];
  const d = new Date(iso);
  if (isNaN(d.getTime())) return ['–', ''];
  const date = d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  let h = d.getHours();
  const ap = h >= 12 ? 'PM' : 'AM';
  h = h % 12 || 12;
  const time = h + ':' + String(d.getMinutes()).padStart(2, '0') + ' ' + ap;
  return [date, time];
}

function fullAddress(a: Order['address']): string {
  if (!a) return '';
  return [a.line1, a.city, a.state + ' - ' + a.pincode].filter(Boolean).join(', ');
}

export default function AdminOrders() {
  const [stats, setStats] = useState<OrderStats>({});
  const [rows, setRows] = useState<Order[]>([]);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [q, setQ] = useState('');
  const [debouncedQ, setDebouncedQ] = useState('');
  const [status, setStatus] = useState('all');
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState('');
  const [weekRows, setWeekRows] = useState<Order[]>([]);
  const [detail, setDetail] = useState<Order | null>(null);
  const [updating, setUpdating] = useState<Order | null>(null);
  const [newStatus, setNewStatus] = useState('PENDING');
  const [note, setNote] = useState('');
  const [tracking, setTracking] = useState('');
  const [statusOpen, setStatusOpen] = useState(false);
  const [saveErr, setSaveErr] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const t = window.setTimeout(() => {
      setDebouncedQ(q.trim());
      setPage(0);
    }, 350);
    return () => window.clearTimeout(t);
  }, [q]);

  const load = useCallback(async () => {
    try {
      setErr('');
      const params =
        '?query=' + encodeURIComponent(debouncedQ) +
        '&status=' + encodeURIComponent(status === 'all' ? 'all' : status) +
        '&sort=latest&page=' + page + '&size=' + PAGE_SIZE;
      const [data, st, wide] = await Promise.all([
        api<OrderPage>('/api/admin/orders' + params),
        api<OrderStats>('/api/admin/orders/stats'),
        api<OrderPage>('/api/admin/orders?sort=latest&page=0&size=50'),
      ]);
      setRows(Array.isArray(data.content) ? data.content : []);
      setTotalElements(data.totalElements ?? 0);
      setTotalPages(Math.max(1, data.totalPages ?? 1));
      setStats(st);
      setWeekRows(Array.isArray(wide.content) ? wide.content : []);
    } catch (e) {
      setErr(errMsg(e));
    } finally {
      setLoading(false);
    }
  }, [debouncedQ, status, page]);

  useEffect(() => {
    setLoading(true);
    void load();
  }, [load]);

  const deltas = useMemo(() => {
    const week = Date.now() - 7 * 86400000;
    const fresh = weekRows.filter((o) => {
      const t = o.createdAt ? new Date(o.createdAt).getTime() : NaN;
      return !isNaN(t) && t >= week;
    });
    const by = (s: string) => fresh.filter((o) => o.status === s).length;
    return { total: fresh.length, pending: by('PENDING'), shipped: by('SHIPPED'), delivered: by('DELIVERED') };
  }, [weekRows]);

  function openUpdate(o: Order) {
    setUpdating(o);
    setNewStatus(o.status || 'PENDING');
    setNote('');
    setTracking(o.trackingId || '');
    setSaveErr('');
    setStatusOpen(false);
  }

  async function saveStatus() {
    if (!updating) return;
    setSaveErr('');
    setSaving(true);
    try {
      const updated = await api<Order>('/api/admin/orders/' + updating.id + '/status', {
        method: 'PATCH',
        body: JSON.stringify({ status: newStatus, note: note.trim() || null, trackingId: tracking.trim() || null }),
      });
      setUpdating(null);
      if (detail && detail.id === updated.id) setDetail(updated);
      await load();
    } catch (e) {
      setSaveErr(errMsg(e));
    } finally {
      setSaving(false);
    }
  }

  async function openDetail(id: string) {
    try {
      const d = await api<Order>('/api/admin/orders/' + id);
      setDetail(d);
    } catch (e) {
      window.alert(errMsg(e));
    }
  }

  const from = totalElements === 0 ? 0 : page * PAGE_SIZE + 1;
  const to = Math.min(totalElements, page * PAGE_SIZE + rows.length);

  return (
    <div className="st-wrap">
      <section className="st-hero">
        <div>
          <span className="st-eyebrow">Orders Management</span>
          <h1>
            Manage Orders <span aria-hidden="true">📦</span>
          </h1>
          <p>View, track and manage book orders from readers.</p>
        </div>
      </section>

      <section className="st-stats">
        <div className="st-stat">
          <span className="st-staticon" style={{ background: '#FBF0DC' }} aria-hidden="true">🛒</span>
          <span className="st-statnum"><b>{stats.totalOrders ?? 0}</b><span>Total Orders</span></span>
          <span className="st-delta">+{deltas.total}</span>
        </div>
        <div className="st-stat">
          <span className="st-staticon" style={{ background: '#E6EFFF' }} aria-hidden="true">🕐</span>
          <span className="st-statnum"><b>{stats.pendingOrders ?? 0}</b><span>Pending Orders</span></span>
          <span className="st-delta">+{deltas.pending}</span>
        </div>
        <div className="st-stat">
          <span className="st-staticon" style={{ background: '#E3F2E6' }} aria-hidden="true">🚚</span>
          <span className="st-statnum"><b>{stats.shippedOrders ?? 0}</b><span>Shipped Orders</span></span>
          <span className="st-delta">+{deltas.shipped}</span>
        </div>
        <div className="st-stat">
          <span className="st-staticon" style={{ background: '#E3F2E6' }} aria-hidden="true">✔</span>
          <span className="st-statnum"><b>{stats.deliveredOrders ?? 0}</b><span>Delivered Orders</span></span>
          <span className="st-delta">+{deltas.delivered}</span>
        </div>
      </section>

      <section className="st-card">
        <div className="st-cardhead">
          <div>
            <h2>Order List</h2>
            <p>View, track and manage all book orders.</p>
          </div>
          <div className="st-tools">
            <span className="st-search">
              <span aria-hidden="true">🔍</span>
              <input
                type="search"
                placeholder="Search by order ID, reader name, or book title..."
                aria-label="Search orders"
                value={q}
                onChange={(e) => setQ(e.target.value)}
              />
            </span>
            <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} aria-label="Filter by status">
              <option value="all">All Status</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>{prettyStatus(s)}</option>
              ))}
            </select>
          </div>
        </div>

        {err ? (
          <div className="st-err">{err}</div>
        ) : loading ? (
          <div className="st-empty">Loading orders...</div>
        ) : rows.length === 0 ? (
          <div className="st-empty">No orders yet — they appear here when readers check out.</div>
        ) : (
          <div className="st-tablewrap">
            <table className="st-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Order ID</th>
                  <th>Reader</th>
                  <th>Books</th>
                  <th>Qty</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Order Date</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((o, i) => {
                  const first = (o.items || [])[0];
                  const extra = Math.max(0, (o.items || []).length - 1);
                  const qty = (o.items || []).reduce((n, it) => n + (it.qty ?? 0), 0);
                  const [d, t] = fmtDateTime(o.orderDate || o.createdAt);
                  return (
                    <tr key={o.id}>
                      <td className="num">{page * PAGE_SIZE + i + 1}</td>
                      <td>{o.orderNumber}</td>
                      <td>
                        <b>{o.reader?.name}</b>
                        <br />
                        <span className="muted">{o.reader?.email}</span>
                      </td>
                      <td>
                        <span className="st-bookcell">
                          {first && <img src={first.coverImageUrl || FALLBACK} alt={'Cover of ' + (first.title || 'book')} onError={imgErr} />}
                          <b>
                            {first?.title}
                            {extra > 0 && <span className="muted"> +{extra} more</span>}
                          </b>
                        </span>
                      </td>
                      <td className="num">{qty}</td>
                      <td className="num">{rupee(o.total)}</td>
                      <td>
                        <span className={'st-pill ' + pillClass(o.status)}>
                          ● {prettyStatus(o.status)}
                        </span>
                      </td>
                      <td className="muted">
                        {d}
                        <br />
                        {t}
                      </td>
                      <td>
                        <span className="st-actions">
                          <button className="st-iconbtn" aria-label={'View ' + o.orderNumber} onClick={() => void openDetail(o.id)}>
                            👁
                          </button>
                          <button className="st-solid" style={{ padding: '8px 18px' }} onClick={() => openUpdate(o)}>
                            Update
                          </button>
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        <div className="st-foot">
          <span>
            Showing {from} to {to} of {totalElements} orders
          </span>
          <span className="st-pages">
            <button disabled={page <= 0} onClick={() => setPage((p) => p - 1)} aria-label="Previous page">‹</button>
            {Array.from({ length: totalPages }, (_, i) => (
              <button key={i} className={i === page ? 'on' : ''} onClick={() => setPage(i)}>
                {i + 1}
              </button>
            ))}
            <button disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)} aria-label="Next page">›</button>
          </span>
        </div>
      </section>

      {updating && (
        <div className="st-overlay" onClick={() => setUpdating(null)}>
          <div className="st-modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
            <div className="st-modalhead">
              <div>
                <h2>Update Order Status</h2>
                <p>{updating.orderNumber}</p>
              </div>
              <button className="st-x" onClick={() => setUpdating(null)} aria-label="Close">✕</button>
            </div>
            <div className="st-cols">
              <div>
                <div className="st-sec">Order Details</div>
                <OrderBookLine order={updating} />
                <div className="st-sec">Reader Details</div>
                <PersonLines
                  name={updating.reader?.name}
                  email={updating.reader?.email}
                  phone={updating.address?.phone}
                  address={fullAddress(updating.address)}
                />
                <div className="st-sec">Order Information</div>
                <OrderInfoLines order={updating} />
              </div>
              <div>
                <div className="st-sec">Update Status</div>
                <label className="st-label">
                  Select New Status <i>*</i>
                </label>
                <div style={{ position: 'relative' }}>
                  <button
                    className="st-ghost"
                    style={{ width: '100%', display: 'flex', alignItems: 'center', gap: 10, boxSizing: 'border-box' }}
                    onClick={() => setStatusOpen((v) => !v)}
                    aria-haspopup="listbox"
                    aria-expanded={statusOpen}
                  >
                    <span className="st-dot" style={{ background: DOT[newStatus] || '#D9A21B' }} />
                    {prettyStatus(newStatus)}
                    <span style={{ marginLeft: 'auto' }}>⌄</span>
                  </button>
                  {statusOpen && (
                    <div
                      role="listbox"
                      style={{
                        position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 5,
                        background: '#fff', border: '1px solid #e5ded2', borderRadius: 12,
                        padding: 6, marginTop: 6, boxShadow: '0 14px 34px rgba(36,26,18,.14)',
                      }}
                    >
                      {['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED'].map((s) => (
                        <div
                          key={s}
                          role="option"
                          aria-selected={newStatus === s}
                          className="st-statusopt"
                          style={newStatus === s ? { background: '#faf7f0' } : undefined}
                          onClick={() => { setNewStatus(s); setStatusOpen(false); }}
                        >
                          <span className="st-dot" style={{ background: DOT[s] }} />
                          {prettyStatus(s)}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
                <label className="st-label">Tracking ID (optional)</label>
                <input
                  value={tracking}
                  onChange={(e) => setTracking(e.target.value)}
                  placeholder="e.g. IP123456789IN"
                  style={{ width: '100%', boxSizing: 'border-box', border: '1px solid #e5ded2', borderRadius: 10, padding: '10px 12px', font: 'inherit', fontSize: 13.5 }}
                />
                <label className="st-label">Add Note (Optional)</label>
                <div className="st-tawrap">
                  <textarea
                    value={note}
                    maxLength={200}
                    onChange={(e) => setNote(e.target.value)}
                    placeholder="Add a note about this order (e.g., Tracking ID, packing details, etc.)..."
                  />
                  <span>{note.length}/200</span>
                </div>
              </div>
            </div>
            {saveErr && <div className="st-err" style={{ marginTop: 12 }}>{saveErr}</div>}
            <div className="st-modalfoot">
              <button className="st-ghost" onClick={() => setUpdating(null)}>Cancel</button>
              <button className="st-solid" disabled={saving} onClick={() => void saveStatus()}>
                Update Status
              </button>
            </div>
          </div>
        </div>
      )}

      {detail && (
        <div className="st-overlay" onClick={() => setDetail(null)}>
          <div className="st-modal wide" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
            <div className="st-modalhead">
              <div>
                <h2>Order Details</h2>
                <p>{detail.orderNumber}</p>
              </div>
              <button className="st-x" onClick={() => setDetail(null)} aria-label="Close">✕</button>
            </div>
            <OrderTracker timeline={detail.timeline || []} />
            <div className="st-cols">
              <div>
                <div className="st-sec">Books in this Order</div>
                {(detail.items || []).map((it, i) => (
                  <div className="st-line" key={i}>
                    <img src={it.coverImageUrl || FALLBACK} alt={'Cover of ' + (it.title || 'book')} onError={imgErr} />
                    <div>
                      <b>{it.title}</b>
                      <span className="muted">Author: {it.author}</span>
                      <br />
                      <span className="muted">Price: {rupee(it.price)}</span>
                      <br />
                      <span className="muted">Quantity: {it.qty}</span>
                    </div>
                    <b>{rupee(it.lineTotal)}</b>
                  </div>
                ))}
                <div className="st-totals">
                  <div><span>Subtotal</span><span>{rupee(detail.subtotal)}</span></div>
                  <div><span>Shipping Charges</span><span>{rupee(detail.shipping)}</span></div>
                  <div><span>Total Amount</span><span>{rupee(detail.total)}</span></div>
                </div>
                <div className="st-sec">Order Timeline</div>
                <div className="st-timeline">
                  {(detail.timeline || []).map((s, i) => {
                    const [d, t] = fmtDateTime(s.at);
                    return (
                      <div className="st-tlrow" key={i}>
                        <span className="ball" style={{ background: s.done ? DOT[s.stage || ''] || '#8A5A12' : '#cfc6b4' }}>
                          {s.done ? '✓' : ''}
                        </span>
                        <div>
                          <b>{s.title}</b>
                          <p>{s.done ? s.detail : '–'}</p>
                        </div>
                        <time>{s.done ? d + ', ' + t : ''}</time>
                      </div>
                    );
                  })}
                </div>
              </div>
              <div>
                <div className="st-sec">Reader Details</div>
                <PersonLines
                  name={detail.reader?.name}
                  email={detail.reader?.email}
                  phone={detail.address?.phone}
                  address={fullAddress(detail.address)}
                />
                <div className="st-sec">Shipping Address</div>
                <PersonLines
                  name={detail.address?.fullName}
                  address={fullAddress(detail.address)}
                  hideContact
                />
                <div className="st-sec">Payment Information</div>
                <div className="st-kv"><span>Payment Method</span><span>{detail.paymentMethod === 'UPI' ? 'Online (UPI)' : detail.paymentMethod}</span></div>
                <div className="st-kv">
                  <span>Payment Status</span>
                  <span>{detail.paymentStatus === 'PAID' ? '🟢 Paid' : detail.paymentStatus}</span>
                </div>
                <div className="st-kv"><span>Transaction ID</span><span>{detail.transactionId || '–'}</span></div>
                <div className="st-kv"><span>Payment Date</span><span>{fmtAddedOn(detail.paidAt)}{detail.paidAt ? ', ' + fmtDateTime(detail.paidAt)[1] : ''}</span></div>
                <div className="st-sec">Order Information</div>
                <OrderInfoLines order={detail} />
              </div>
            </div>
            <div className="st-modalfoot">
              <button className="st-ghost" onClick={() => setDetail(null)}>Close</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function OrderBookLine({ order }: { order: Order }) {
  const first = (order.items || [])[0];
  if (!first) return null;
  return (
    <div className="st-orderbook">
      <img src={first.coverImageUrl || FALLBACK} alt={'Cover of ' + (first.title || 'book')} onError={imgErr} />
      <div>
        <b>{first.title}</b>
        <span>Author: {first.author}</span>
        <span>Price: {rupee(first.price)}</span>
        <span>Quantity: {first.qty}</span>
        <span>Total Amount: {rupee(first.lineTotal)}</span>
      </div>
    </div>
  );
}

function PersonLines({
  name,
  email,
  phone,
  address,
  hideContact,
}: {
  name?: string;
  email?: string;
  phone?: string;
  address?: string;
  hideContact?: boolean;
}) {
  return (
    <div>
      {name && <div className="st-kv"><span>👤</span><b>{name}</b></div>}
      {!hideContact && email && <div className="st-kv"><span>✉</span><span>{email}</span></div>}
      {!hideContact && phone && <div className="st-kv"><span>📞</span><span>{phone}</span></div>}
      {address && <div className="st-kv"><span>📍</span><span>{address}</span></div>}
    </div>
  );
}

function OrderInfoLines({ order }: { order: Order }) {
  const [d, t] = fmtDateTime(order.orderDate || order.createdAt);
  return (
    <div>
      <div className="st-kv"><span>📅</span><span>Order Date<br />{d}, {t}</span></div>
      <div className="st-kv"><span>🧾</span><span>Order ID<br />{order.orderNumber}</span></div>
      {order.status && (
        <div className="st-kv">
          <span>Current Status</span>
          <span className={'st-pill ' + pillClass(order.status)}>● {prettyStatus(order.status)}</span>
        </div>
      )}
      {order.trackingId && (
        <div className="st-kv"><span>Tracking ID</span><span>{order.trackingId}</span></div>
      )}
    </div>
  );
}

function pillClass(status?: string): string {
  switch ((status || '').toUpperCase()) {
    case 'CONFIRMED':
      return 'blue';
    case 'SHIPPED':
      return 'purple';
    case 'DELIVERED':
      return 'green';
    case 'CANCELLED':
      return 'grey';
    default:
      return 'amber';
  }
}

function OrderTracker({ timeline }: { timeline: TimelineEntry[] }) {
  const stages = timeline.filter((s) => ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED'].includes(s.stage || ''));
  const colors = ['#D9A21B', '#2B6CB0', '#6B46C1', '#2F7A44'];
  return (
    <div className="st-track">
      {stages.map((s, i) => {
        const [d, t] = fmtDateTime(s.at);
        return (
          <div key={i} className={'st-stop' + (s.done ? ' done' : '')}>
            <span className="ball" style={s.done ? { background: colors[i % colors.length] } : undefined}>
              {s.done ? '✓' : ''}
            </span>
            <b>{s.title}</b>
            <span>{s.done ? d + ' ' + t : s.title}</span>
          </div>
        );
      })}
    </div>
  );
}
