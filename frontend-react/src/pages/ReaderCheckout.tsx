import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import ReaderLayout from '../components/ReaderLayout';
import { api } from '../lib/api';
import { rupee } from './ReaderBooks';
import type { Cart } from './ReaderCart';
import './StoreReader.css';

type Address = {
  id: string;
  label?: string;
  fullName?: string;
  phone?: string;
  line1?: string;
  line2?: string | null;
  city?: string;
  state?: string;
  pincode?: string;
  isDefault?: boolean;
};

type PlacedOrder = {
  orderNumber?: string;
  total?: number;
  status?: string;
};

const EMPTY_ADDR = {
  label: 'Home',
  fullName: '',
  phone: '',
  line1: '',
  line2: '',
  city: '',
  state: '',
  pincode: '',
  isDefault: false,
};

function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

export default function ReaderCheckout() {
  const navigate = useNavigate();
  const [cart, setCart] = useState<Cart>({ items: [], subtotal: 0, count: 0 });
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [selected, setSelected] = useState('');
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState('');
  const [sync, setSync] = useState('Connecting...');
  const [busy, setBusy] = useState(false);
  const [reviewOpen, setReviewOpen] = useState(false);
  const [placed, setPlaced] = useState<PlacedOrder | null>(null);
  const [addrModal, setAddrModal] = useState<null | { mode: 'add' } | { mode: 'edit'; addr: Address }>(null);
  const [form, setForm] = useState(EMPTY_ADDR);
  const [formErr, setFormErr] = useState('');

  const load = useCallback(async () => {
    try {
      setErr('');
      const [c, a] = await Promise.all([api<Cart>('/api/cart'), api<Address[]>('/api/addresses')]);
      setCart(c);
      const list = Array.isArray(a) ? a : [];
      setAddresses(list);
      const def = list.find((x) => x.isDefault) || list[0];
      if (def) setSelected((s) => s || def.id);
      setSync('Synced with admin just now');
    } catch (e) {
      setErr(errMsg(e));
      setSync('Offline');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    setSync('Syncing with admin data...');
    void load();
  }, [load]);

  async function setQty(itemId: string, qty: number) {
    if (qty < 1) return;
    try {
      const c = await api<Cart>('/api/cart/items/' + itemId, {
        method: 'PATCH',
        body: JSON.stringify({ qty }),
      });
      setCart(c);
      window.dispatchEvent(new Event('cart-changed'));
    } catch (e) {
      window.alert(errMsg(e));
    }
  }

  function openAdd() {
    setForm(EMPTY_ADDR);
    setFormErr('');
    setAddrModal({ mode: 'add' });
  }

  function openEdit(a: Address) {
    setForm({
      label: a.label || 'Home',
      fullName: a.fullName || '',
      phone: a.phone || '',
      line1: a.line1 || '',
      line2: a.line2 || '',
      city: a.city || '',
      state: a.state || '',
      pincode: a.pincode || '',
      isDefault: !!a.isDefault,
    });
    setFormErr('');
    setAddrModal({ mode: 'edit', addr: a });
  }

  async function saveAddress() {
    if (!addrModal) return;
    setFormErr('');
    if (!form.fullName.trim() || !form.phone.trim() || !form.line1.trim() || !form.city.trim() || !form.state.trim() || !form.pincode.trim()) {
      setFormErr('Name, phone, street, city, state and pincode are required.');
      return;
    }
    try {
      const payload = {
        label: form.label.trim() || 'Home',
        fullName: form.fullName.trim(),
        phone: form.phone.trim(),
        line1: form.line1.trim(),
        line2: form.line2.trim() || null,
        city: form.city.trim(),
        state: form.state.trim(),
        pincode: form.pincode.trim(),
        isDefault: form.isDefault,
      };
      let saved: Address;
      if (addrModal.mode === 'add') {
        saved = await api<Address>('/api/addresses', { method: 'POST', body: JSON.stringify(payload) });
      } else {
        saved = await api<Address>('/api/addresses/' + addrModal.addr.id, {
          method: 'PATCH',
          body: JSON.stringify(payload),
        });
      }
      setAddrModal(null);
      const list = await api<Address[]>('/api/addresses');
      setAddresses(Array.isArray(list) ? list : []);
      setSelected(saved.id);
    } catch (e) {
      setFormErr(errMsg(e));
    }
  }

  async function removeAddress(a: Address) {
    if (!window.confirm('Delete the "' + (a.label || 'address') + '" address?')) return;
    try {
      await api('/api/addresses/' + a.id, { method: 'DELETE' });
      const list = await api<Address[]>('/api/addresses');
      const arr = Array.isArray(list) ? list : [];
      setAddresses(arr);
      if (selected === a.id) {
        const def = arr.find((x) => x.isDefault) || arr[0];
        setSelected(def ? def.id : '');
      }
    } catch (e) {
      window.alert(errMsg(e));
    }
  }

  async function placeOrder() {
    if (!selected) {
      window.alert('Choose a delivery address first.');
      return;
    }
    setBusy(true);
    try {
      const o = await api<PlacedOrder>('/api/orders', {
        method: 'POST',
        body: JSON.stringify({ addressId: selected, paymentMethod: 'UPI' }),
      });
      setPlaced(o);
      window.dispatchEvent(new Event('cart-changed'));
    } catch (e) {
      window.alert(errMsg(e));
    } finally {
      setBusy(false);
    }
  }

  const items = cart.items ?? [];
  const subtotal = cart.subtotal ?? 0;
  const chosen = addresses.find((a) => a.id === selected);

  return (
    <ReaderLayout active="books" syncNote={sync}>
      <div className="sr-wrap">
        {placed ? (
          <div className="sr-success">
            <span aria-hidden="true">✔</span>
            <h1>Order placed!</h1>
            <p>
              Order <b>{placed.orderNumber}</b> for {rupee(placed.total)} is confirmed.
              You’ll receive updates as it ships.
            </p>
            <button className="sr-checkout" style={{ width: 'auto', padding: '12px 34px' }} onClick={() => navigate('/reader-books')}>
              Continue Shopping
            </button>
          </div>
        ) : (
          <>
            <Link className="sr-back" to="/reader-cart">← Back to Cart</Link>
            <h1 className="sr-title">Checkout</h1>
            <p className="sr-sub">Review your details and complete your order</p>

            {err ? (
              <div className="sr-err">{err}</div>
            ) : loading ? (
              <div className="sr-notice">Loading checkout...</div>
            ) : items.length === 0 ? (
              <div className="sr-notice">
                Your cart is empty. <Link to="/reader-books">Discover books</Link> first.
              </div>
            ) : (
              <div className="sr-cols">
                <main className="sr-steps">
                  <section className="sr-step">
                    <div className="sr-stephead">
                      <h2>📍 1. Delivery Address</h2>
                      <button className="sr-outline" onClick={openAdd}>＋ Add New Address</button>
                    </div>
                    {addresses.length === 0 ? (
                      <div className="sr-notice">No addresses yet — add one to check out.</div>
                    ) : (
                      addresses.map((a) => (
                        <label key={a.id} className={'sr-addr' + (selected === a.id ? ' on' : '')}>
                          <input
                            type="radio"
                            name="addr"
                            checked={selected === a.id}
                            onChange={() => setSelected(a.id)}
                          />
                          <span className="sr-addrbody">
                            <b>
                              {a.label} {a.isDefault && <i>Default</i>}
                            </b>
                            <span>{a.fullName}</span>
                            <span>{a.line1}{a.line2 ? ', ' + a.line2 : ''}</span>
                            <span>{a.city}, {a.state} - {a.pincode}</span>
                            <span>+91 {a.phone}</span>
                          </span>
                          <span className="sr-addractions">
                            <button type="button" onClick={(e) => { e.preventDefault(); openEdit(a); }}>✎ Edit</button>
                            <button type="button" onClick={(e) => { e.preventDefault(); void removeAddress(a); }}>🗑 Delete</button>
                          </span>
                        </label>
                      ))
                    )}
                  </section>

                  <section className="sr-step">
                    <h2>💳 2. Payment Method</h2>
                    <label className="sr-pay">
                      <input type="radio" checked readOnly />
                      <span>
                        <b>UPI / Online Payment</b>
                        <span>Pay securely using UPI, UPI Apps, Net Banking or Cards</span>
                      </span>
                      <span className="sr-brands" aria-hidden="true">
                        <i>UPI</i><i>GPay</i><i>Pe</i><i>paytm</i><i>💳</i><i>🏦</i>
                      </span>
                    </label>
                  </section>

                  <section className="sr-step">
                    <button className="sr-reviewhead" onClick={() => setReviewOpen((v) => !v)} aria-expanded={reviewOpen}>
                      <span>🛡 3. Order Review</span>
                      <span>{reviewOpen ? '⌃' : '⌄'}</span>
                    </button>
                    {!reviewOpen && <p className="sr-sub">Please review your order details before placing the order.</p>}
                    {reviewOpen && (
                      <div className="sr-reviewbody">
                        <div>Deliver to: <b>{chosen ? chosen.fullName + ', ' + chosen.line1 + ', ' + chosen.city : '–'}</b></div>
                        <div>Payment: <b>UPI / Online Payment</b></div>
                        <div>Items: <b>{items.length} · {rupee(subtotal)}</b></div>
                      </div>
                    )}
                  </section>
                </main>

                <aside className="sr-side">
                  <section className="sr-panel">
                    <h3>🧾 Order Summary</h3>
                    {items.map((l) => (
                      <div className="sr-sumline" key={l.id}>
                        <span className="sr-sumbook"><b>{l.title}</b><span>{l.author}</span></span>
                        <span className="sr-stepper sm">
                          <button onClick={() => void setQty(l.id, (l.qty ?? 1) - 1)} aria-label="Decrease">−</button>
                          <b>{l.qty}</b>
                          <button onClick={() => void setQty(l.id, (l.qty ?? 1) + 1)} aria-label="Increase">＋</button>
                        </span>
                        <b>{rupee(l.lineTotal)}</b>
                      </div>
                    ))}
                    <div className="sr-kv"><span>Subtotal ({items.length} items)</span><b>{rupee(subtotal)}</b></div>
                    <div className="sr-kv"><span>Shipping Charges</span><b>₹0</b></div>
                    <div className="sr-total"><span>Total Amount</span><b>{rupee(subtotal)}</b></div>
                    <button className="sr-checkout" disabled={busy || !selected} onClick={() => void placeOrder()}>
                      🔒 Pay {rupee(subtotal)} →
                    </button>
                    <p className="sr-secure">🔒 Secure and encrypted payment</p>
                  </section>
                </aside>
              </div>
            )}
          </>
        )}

        {addrModal && (
          <div className="sr-overlay" onClick={() => setAddrModal(null)}>
            <div className="sr-modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
              <h2>{addrModal.mode === 'add' ? 'Add New Address' : 'Edit Address'}</h2>
              <label>Label<input value={form.label} onChange={(e) => setForm((p) => ({ ...p, label: e.target.value }))} placeholder="Home / College" /></label>
              <label>Full Name *<input value={form.fullName} onChange={(e) => setForm((p) => ({ ...p, fullName: e.target.value }))} /></label>
              <label>Phone *<input value={form.phone} onChange={(e) => setForm((p) => ({ ...p, phone: e.target.value }))} placeholder="98765 43210" /></label>
              <label>Street *<input value={form.line1} onChange={(e) => setForm((p) => ({ ...p, line1: e.target.value }))} /></label>
              <label>Area / Landmark<input value={form.line2} onChange={(e) => setForm((p) => ({ ...p, line2: e.target.value }))} /></label>
              <div className="sr-3col">
                <label>City *<input value={form.city} onChange={(e) => setForm((p) => ({ ...p, city: e.target.value }))} /></label>
                <label>State *<input value={form.state} onChange={(e) => setForm((p) => ({ ...p, state: e.target.value }))} /></label>
                <label>Pincode *<input value={form.pincode} onChange={(e) => setForm((p) => ({ ...p, pincode: e.target.value }))} /></label>
              </div>
              <label className="sr-check"><input type="checkbox" checked={form.isDefault} onChange={(e) => setForm((p) => ({ ...p, isDefault: e.target.checked }))} /> Set as default address</label>
              {formErr && <div className="sr-err">{formErr}</div>}
              <div className="sr-modalfoot">
                <button className="sr-ghost" onClick={() => setAddrModal(null)}>Cancel</button>
                <button className="sr-checkout" style={{ width: 'auto', padding: '11px 30px' }} onClick={() => void saveAddress()}>
                  Save Address
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </ReaderLayout>
  );
}
