import { useCallback, useEffect, useState } from 'react';
import ReaderLayout from '../components/ReaderLayout';
import { api } from '../lib/api';
import './StoreReader.css';

export type ShopBook = {
  id: string;
  title?: string;
  author?: string;
  description?: string | null;
  coverImageUrl?: string | null;
  price?: number;
  stockQty?: number;
  status?: string;
  lowStock?: boolean;
  addedOn?: string | null;
  createdAt?: string | null;
};

type BookPage = {
  content: ShopBook[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
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

export function rupee(n?: number | null): string {
  return '₹' + Number(n ?? 0).toLocaleString('en-IN');
}

const PAGE_SIZE = 10;

const SORTS = [
  { value: 'latest', label: 'Latest' },
  { value: 'price_asc', label: 'Price: Low to High' },
  { value: 'price_desc', label: 'Price: High to Low' },
  { value: 'title_asc', label: 'Title: A to Z' },
];

export default function ReaderBooks() {
  const [rows, setRows] = useState<ShopBook[]>([]);
  const [total, setTotal] = useState(0);
  const [pages, setPages] = useState(1);
  const [page, setPage] = useState(0);
  const [q, setQ] = useState('');
  const [appliedQ, setAppliedQ] = useState('');
  const [inStock, setInStock] = useState(false);
  const [outStock, setOutStock] = useState(false);
  const [minP, setMinP] = useState('');
  const [maxP, setMaxP] = useState('');
  const [sort, setSort] = useState('latest');
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState('');
  const [sync, setSync] = useState('Connecting...');
  const [adding, setAdding] = useState('');
  const [added, setAdded] = useState<Record<string, boolean>>({});

  const availability =
    inStock && !outStock ? 'in_stock' : !inStock && outStock ? 'out_of_stock' : '';

  const load = useCallback(async () => {
    try {
      setErr('');
      const params =
        '?query=' + encodeURIComponent(appliedQ) +
        (availability ? '&availability=' + availability : '') +
        (minP !== '' ? '&minPrice=' + encodeURIComponent(minP) : '') +
        (maxP !== '' ? '&maxPrice=' + encodeURIComponent(maxP) : '') +
        '&sort=' + encodeURIComponent(sort) +
        '&page=' + page + '&size=' + PAGE_SIZE;
      const data = await api<BookPage>('/api/books' + params);
      setRows(Array.isArray(data.content) ? data.content : []);
      setTotal(data.totalElements ?? 0);
      setPages(Math.max(1, data.totalPages ?? 1));
      setSync('Synced with admin just now');
    } catch (e) {
      setErr(errMsg(e));
      setSync('Offline');
    } finally {
      setLoading(false);
    }
  }, [appliedQ, availability, minP, maxP, sort, page]);

  useEffect(() => {
    setSync('Syncing with admin data...');
    setLoading(true);
    void load();
  }, [load]);

  useEffect(() => {
    const t = window.setTimeout(() => {
      setAppliedQ(q.trim());
      setPage(0);
    }, 400);
    return () => window.clearTimeout(t);
  }, [q]);

  function applyFilters() {
    setAppliedQ(q.trim());
    setPage(0);
    setLoading(true);
    void load();
  }

  async function addToCart(b: ShopBook) {
    setAdding(b.id);
    try {
      await api('/api/cart/items', {
        method: 'POST',
        body: JSON.stringify({ bookId: b.id, qty: 1 }),
      });
      setAdded((p) => ({ ...p, [b.id]: true }));
      window.dispatchEvent(new Event('cart-changed'));
      window.setTimeout(() => setAdded((p) => ({ ...p, [b.id]: false })), 1600);
    } catch (e) {
      window.alert(errMsg(e));
    } finally {
      setAdding('');
    }
  }

  return (
    <ReaderLayout
      active="books"
      syncNote={sync}
      search={
        <>
          <span className="rd-lens" aria-hidden="true">🔍</span>
          <input
            type="search"
            placeholder="Search books, authors, or titles..."
            aria-label="Search books, authors, or titles"
            autoComplete="off"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
        </>
      }
    >
      <div className="sr-wrap">
        <section className="sr-hero">
          <div>
            <span className="sr-eyebrow">Books</span>
            <h1>Discover Books</h1>
            <p>Explore books from Reader's Circle. Read, learn and build a stronger community.</p>
          </div>
          <span className="sr-heroart" aria-hidden="true">📚</span>
        </section>

        <div className="sr-cols">
          <main className="sr-main">
            <div className="sr-listhead">
              <b>{total} Books</b>
              <label>
                Sort by:{' '}
                <select value={sort} onChange={(e) => { setSort(e.target.value); setPage(0); }} aria-label="Sort books">
                  {SORTS.map((s) => (
                    <option key={s.value} value={s.value}>{s.label}</option>
                  ))}
                </select>
              </label>
            </div>

            {err ? (
              <div className="sr-err">{err}</div>
            ) : loading ? (
              <div className="sr-notice">Loading books...</div>
            ) : rows.length === 0 ? (
              <div className="sr-notice">No books match these filters.</div>
            ) : (
              <div className="sr-grid">
                {rows.map((b) => {
                  const out = (b.stockQty ?? 0) <= 0;
                  return (
                    <article className="sr-book" key={b.id}>
                      <img src={b.coverImageUrl || FALLBACK} alt={'Cover of ' + (b.title || 'book')} onError={imgErr} />
                      <b className="sr-title">{b.title}</b>
                      <span className="sr-author">Author: {b.author}</span>
                      <span className="sr-price">{rupee(b.price)}</span>
                      <span className={'sr-stock' + (out ? ' out' : b.lowStock ? ' low' : '')}>
                        {out ? '● Out of Stock' : b.lowStock ? '● Low Stock (' + b.stockQty + ')' : '● In Stock (' + b.stockQty + ')'}
                      </span>
                      <button
                        className="sr-add"
                        disabled={out || adding === b.id}
                        onClick={() => void addToCart(b)}
                      >
                        🛒 {added[b.id] ? 'Added ✓' : out ? 'Out of Stock' : 'Add to Cart'}
                      </button>
                    </article>
                  );
                })}
              </div>
            )}

            {pages > 1 && (
              <div className="sr-pager">
                <button disabled={page <= 0} onClick={() => setPage((p) => p - 1)} aria-label="Previous page">‹</button>
                {Array.from({ length: pages }, (_, i) => (
                  <button key={i} className={i === page ? 'on' : ''} onClick={() => setPage(i)}>
                    {i + 1}
                  </button>
                ))}
                <button disabled={page >= pages - 1} onClick={() => setPage((p) => p + 1)} aria-label="Next page">›</button>
              </div>
            )}
          </main>

          <aside className="sr-rail" aria-label="Search and filter">
            <section className="sr-panel">
              <h3>Search &amp; Filter</h3>
              <span className="sr-search">
                <span aria-hidden="true">🔍</span>
                <input
                  type="search"
                  placeholder="Search books..."
                  aria-label="Search books"
                  value={q}
                  onChange={(e) => setQ(e.target.value)}
                />
              </span>
              <b className="sr-sub">Availability</b>
              <label className="sr-check">
                <input type="checkbox" checked={inStock} onChange={(e) => setInStock(e.target.checked)} /> In Stock
              </label>
              <label className="sr-check">
                <input type="checkbox" checked={outStock} onChange={(e) => setOutStock(e.target.checked)} /> Out of Stock
              </label>
              <b className="sr-sub">Price Range</b>
              <div className="sr-pricerow">
                <input
                  inputMode="numeric"
                  placeholder="Min (₹)"
                  aria-label="Minimum price"
                  value={minP}
                  onChange={(e) => setMinP(e.target.value.replace(/[^0-9]/g, ''))}
                />
                <input
                  inputMode="numeric"
                  placeholder="Max (₹)"
                  aria-label="Maximum price"
                  value={maxP}
                  onChange={(e) => setMaxP(e.target.value.replace(/[^0-9]/g, ''))}
                />
              </div>
              <button className="sr-apply" onClick={applyFilters}>
                Apply Filters
              </button>
              <button className="sr-clear" onClick={() => { setQ(''); setInStock(false); setOutStock(false); setMinP(''); setMaxP(''); setSort('latest'); setPage(0); }}>
                Clear
              </button>
            </section>
          </aside>
        </div>
      </div>
    </ReaderLayout>
  );
}
