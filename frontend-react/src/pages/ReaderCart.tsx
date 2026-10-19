import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import ReaderLayout from '../components/ReaderLayout';
import { api } from '../lib/api';
import { rupee } from './ReaderBooks';
import './StoreReader.css';

export type CartLine = {
  id: string;
  bookId?: string;
  title?: string;
  author?: string;
  coverImageUrl?: string | null;
  price?: number;
  stockQty?: number;
  status?: string;
  qty?: number;
  lineTotal?: number;
};

export type Cart = {
  items?: CartLine[];
  subtotal?: number;
  count?: number;
};

import { BOOK_PLACEHOLDER as FALLBACK } from '../lib/placeholders';

function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

function imgErr(e: React.SyntheticEvent<HTMLImageElement>) {
  const img = e.currentTarget;
  img.onerror = null;
  img.src = FALLBACK;
}

export default function ReaderCart() {
  const navigate = useNavigate();
  const [cart, setCart] = useState<Cart>({ items: [], subtotal: 0, count: 0 });
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState('');
  const [sync, setSync] = useState('Connecting...');
  const [busy, setBusy] = useState('');

  const load = useCallback(async () => {
    try {
      setErr('');
      const c = await api<Cart>('/api/cart');
      setCart(c);
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

  function refreshBadge() {
    window.dispatchEvent(new Event('cart-changed'));
  }

  async function setQty(line: CartLine, qty: number) {
    if (qty < 1) return;
    setBusy(line.id);
    try {
      const c = await api<Cart>('/api/cart/items/' + line.id, {
        method: 'PATCH',
        body: JSON.stringify({ qty }),
      });
      setCart(c);
      refreshBadge();
    } catch (e) {
      window.alert(errMsg(e));
    } finally {
      setBusy('');
    }
  }

  async function remove(line: CartLine) {
    setBusy(line.id);
    try {
      const c = await api<Cart>('/api/cart/items/' + line.id, { method: 'DELETE' });
      setCart(c);
      refreshBadge();
    } catch (e) {
      window.alert(errMsg(e));
    } finally {
      setBusy('');
    }
  }

  const items = cart.items ?? [];
  const subtotal = cart.subtotal ?? 0;

  return (
    <ReaderLayout active="books" syncNote={sync}>
      <div className="sr-wrap">
        <div className="sr-carthead">
          <span className="sr-carticon" aria-hidden="true">🛒</span>
          <div>
            <h1>Your Cart</h1>
            <p>{items.length} item{items.length === 1 ? '' : 's'} in your cart</p>
          </div>
          <Link className="sr-cont" to="/reader-books">
            ← Continue Shopping
          </Link>
        </div>

        {err ? (
          <div className="sr-err">{err}</div>
        ) : loading ? (
          <div className="sr-notice">Loading your cart...</div>
        ) : items.length === 0 ? (
          <div className="sr-notice">
            Your cart is empty. <Link to="/reader-books">Discover books</Link> to add some.
          </div>
        ) : (
          <div className="sr-cols">
            <main className="sr-lines">
              {items.map((l) => {
                const out = (l.stockQty ?? 0) <= 0;
                const low = !out && (l.stockQty ?? 0) <= 5;
                return (
                  <article className="sr-line" key={l.id}>
                    <img src={l.coverImageUrl || FALLBACK} alt={'Cover of ' + (l.title || 'book')} onError={imgErr} />
                    <div className="sr-lineinfo">
                      <b>{l.title}</b>
                      <span>Author: {l.author}</span>
                      <span className="sr-price">{rupee(l.price)}</span>
                      <span className={'sr-stock' + (out ? ' out' : low ? ' low' : '')}>
                        {out ? '● Out of Stock' : low ? '● Low Stock (' + l.stockQty + ')' : '● In Stock'}
                      </span>
                    </div>
                    <div className="sr-qtycol">
                      <span className="sr-stepper">
                        <button disabled={!!busy} onClick={() => void setQty(l, (l.qty ?? 1) - 1)} aria-label="Decrease quantity">−</button>
                        <b>{l.qty}</b>
                        <button disabled={busy === l.id} onClick={() => void setQty(l, (l.qty ?? 1) + 1)} aria-label="Increase quantity">＋</button>
                      </span>
                      <button className="sr-remove" disabled={busy === l.id} onClick={() => void remove(l)}>
                        🗑 Remove
                      </button>
                    </div>
                    <b className="sr-linetotal">{rupee(l.lineTotal)}</b>
                  </article>
                );
              })}
            </main>
            <aside className="sr-side">
              <section className="sr-panel">
                <h3>🧾 Order Summary</h3>
                <div className="sr-kv"><span>Subtotal ({items.length} items)</span><b>{rupee(subtotal)}</b></div>
                <div className="sr-kv"><span>Shipping Charges</span><b>₹0</b></div>
                <div className="sr-total"><span>Total</span><b>{rupee(subtotal)}</b></div>
                <button className="sr-checkout" onClick={() => navigate('/reader-checkout')}>
                  Proceed to Checkout <span aria-hidden="true">→</span>
                </button>
              </section>
              <section className="sr-panel sr-perks">
                <div><span aria-hidden="true">🚚</span> Free shipping on all orders</div>
                <div><span aria-hidden="true">🛡</span> Secure payment (UPI / Online)</div>
                <div><span aria-hidden="true">📦</span> Books are carefully packed</div>
                <div><span aria-hidden="true">🕐</span> You'll receive order updates</div>
              </section>
            </aside>
          </div>
        )}
      </div>
    </ReaderLayout>
  );
}
