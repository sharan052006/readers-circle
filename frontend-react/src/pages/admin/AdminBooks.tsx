import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../../lib/api';
import './StoreAdmin.css';

export type StoreBook = {
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
  content: StoreBook[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

type BookStats = {
  totalBooks?: number;
  available?: number;
  outOfStock?: number;
  totalOrders?: number;
};

import { BOOK_PLACEHOLDER as FALLBACK } from '../../lib/placeholders';

function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

function imgErr(e: React.SyntheticEvent<HTMLImageElement>) {
  const img = e.currentTarget;
  img.onerror = null;
  img.src = FALLBACK;
}

export function fmtAddedOn(iso?: string | null): string {
  if (!iso) return '–';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '–';
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}

function inLast7Days(iso?: string | null): boolean {
  if (!iso) return false;
  const t = new Date(iso).getTime();
  if (isNaN(t)) return false;
  return Date.now() - t < 7 * 86400000;
}

export function rupee(n?: number | null): string {
  return '₹' + Number(n ?? 0).toLocaleString('en-IN');
}

const PAGE_SIZE = 7;

const EMPTY_FORM = {
  title: '',
  author: '',
  description: '',
  cover: '',
  price: '',
  stock: '',
  status: 'AVAILABLE',
};

export default function AdminBooks() {
  const [stats, setStats] = useState<BookStats>({});
  const [rows, setRows] = useState<StoreBook[]>([]);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [q, setQ] = useState('');
  const [debouncedQ, setDebouncedQ] = useState('');
  const [status, setStatus] = useState('all');
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState('');
  const [modal, setModal] = useState<null | { mode: 'add' } | { mode: 'edit'; book: StoreBook }>(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [formErr, setFormErr] = useState('');
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
      const [data, st] = await Promise.all([
        api<BookPage>('/api/admin/books' + params),
        api<BookStats>('/api/admin/books/stats'),
      ]);
      setRows(Array.isArray(data.content) ? data.content : []);
      setTotalElements(data.totalElements ?? 0);
      setTotalPages(Math.max(1, data.totalPages ?? 1));
      setStats(st);
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
    const fresh = rows.filter((b) => inLast7Days(b.createdAt));
    return {
      total: fresh.length,
      avail: fresh.filter((b) => b.status === 'AVAILABLE').length,
      oos: fresh.filter((b) => b.status === 'OUT_OF_STOCK').length,
    };
  }, [rows]);

  function openAdd() {
    setForm(EMPTY_FORM);
    setFormErr('');
    setModal({ mode: 'add' });
  }

  function openEdit(b: StoreBook) {
    setForm({
      title: b.title || '',
      author: b.author || '',
      description: b.description || '',
      cover: b.coverImageUrl || '',
      price: String(b.price ?? ''),
      stock: String(b.stockQty ?? ''),
      status: b.status || 'AVAILABLE',
    });
    setFormErr('');
    setModal({ mode: 'edit', book: b });
  }

  function onCoverFile(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files && e.target.files[0];
    if (!f) return;
    if (f.size > 5 * 1024 * 1024) {
      setFormErr('Cover image must be under 5MB.');
      return;
    }
    const r = new FileReader();
    r.onload = () => {
      if (typeof r.result === 'string') setForm((p) => ({ ...p, cover: r.result as string }));
    };
    r.readAsDataURL(f);
  }

  async function submit() {
    if (!modal) return;
    setFormErr('');
    const title = form.title.trim();
    const author = form.author.trim();
    if (!title || !author) {
      setFormErr('Book title and author are required.');
      return;
    }
    const price = form.price === '' ? 0 : parseInt(form.price, 10);
    const stock = form.stock === '' ? 0 : parseInt(form.stock, 10);
    if (isNaN(price) || price < 0 || isNaN(stock) || stock < 0) {
      setFormErr('Price and stock must be zero or more.');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        title,
        author,
        description: form.description.trim() || null,
        coverImageUrl: form.cover.trim() || null,
        price,
        stockQty: stock,
        status: form.status,
      };
      if (modal.mode === 'add') {
        await api('/api/admin/books', { method: 'POST', body: JSON.stringify(payload) });
      } else {
        await api('/api/admin/books/' + modal.book.id, { method: 'PATCH', body: JSON.stringify(payload) });
      }
      setModal(null);
      await load();
    } catch (e) {
      setFormErr(errMsg(e));
    } finally {
      setSaving(false);
    }
  }

  async function remove(b: StoreBook) {
    if (!window.confirm('Delete "' + (b.title || 'book') + '" from the store?')) return;
    try {
      await api('/api/admin/books/' + b.id, { method: 'DELETE' });
      await load();
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
          <span className="st-eyebrow">Books Management</span>
          <h1>
            Manage Books <span aria-hidden="true">📚</span>
          </h1>
          <p>Add and manage books for the Reader's Circle store.</p>
        </div>
        <button className="st-addbtn" onClick={openAdd}>
          <span aria-hidden="true">＋</span> Add Book
        </button>
      </section>

      <section className="st-stats">
        <StatCard icon="📖" iconBg="#F3E8F7" value={stats.totalBooks ?? 0} label="Total Books" delta={deltas.total} />
        <StatCard icon="✔" iconBg="#E3F2E6" value={stats.available ?? 0} label="Available" delta={deltas.avail} />
        <StatCard icon="⊖" iconBg="#FBE3E0" value={stats.outOfStock ?? 0} label="Out of Stock" delta={deltas.oos} zero />
        <StatCard icon="🛒" iconBg="#FBF0DC" value={stats.totalOrders ?? 0} label="Total Orders" delta={0} />
      </section>

      <section className="st-card">
        <div className="st-cardhead">
          <div>
            <h2>Book List</h2>
            <p>Manage book details, stock and availability.</p>
          </div>
          <div className="st-tools">
            <span className="st-search">
              <span aria-hidden="true">🔍</span>
              <input
                type="search"
                placeholder="Search books by title, author..."
                aria-label="Search books"
                value={q}
                onChange={(e) => setQ(e.target.value)}
              />
            </span>
            <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} aria-label="Filter by status">
              <option value="all">All Status</option>
              <option value="AVAILABLE">Available</option>
              <option value="OUT_OF_STOCK">Out of Stock</option>
            </select>
          </div>
        </div>

        {err ? (
          <div className="st-err">{err}</div>
        ) : loading ? (
          <div className="st-empty">Loading books...</div>
        ) : rows.length === 0 ? (
          <div className="st-empty">No books yet — click Add Book to stock the store.</div>
        ) : (
          <div className="st-tablewrap">
            <table className="st-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Book</th>
                  <th>Author</th>
                  <th>Price</th>
                  <th>Stock</th>
                  <th>Status</th>
                  <th>Added On</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((b, i) => (
                  <tr key={b.id}>
                    <td className="num">{page * PAGE_SIZE + i + 1}</td>
                    <td>
                      <span className="st-bookcell">
                        <img src={b.coverImageUrl || FALLBACK} alt={'Cover of ' + (b.title || 'book')} onError={imgErr} />
                        <b>{b.title}</b>
                      </span>
                    </td>
                    <td className="muted">{b.author}</td>
                    <td className="num">{rupee(b.price)}</td>
                    <td className="num">{b.stockQty}</td>
                    <td>
                      {b.status === 'OUT_OF_STOCK' ? (
                        <span className="st-pill red">● Out of Stock</span>
                      ) : (
                        <span className="st-pill green">● Available</span>
                      )}
                    </td>
                    <td className="muted">{fmtAddedOn(b.addedOn || b.createdAt)}</td>
                    <td>
                      <span className="st-actions">
                        <button className="st-iconbtn" aria-label={'Edit ' + b.title} onClick={() => openEdit(b)}>
                          ✎
                        </button>
                        <button className="st-iconbtn danger" aria-label={'Delete ' + b.title} onClick={() => void remove(b)}>
                          🗑
                        </button>
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="st-foot">
          <span>
            Showing {from} to {to} of {totalElements} books
          </span>
          <span className="st-pages">
            <button disabled={page <= 0} onClick={() => setPage((p) => p - 1)} aria-label="Previous page">
              ‹
            </button>
            {Array.from({ length: totalPages }, (_, i) => (
              <button key={i} className={i === page ? 'on' : ''} onClick={() => setPage(i)}>
                {i + 1}
              </button>
            ))}
            <button disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)} aria-label="Next page">
              ›
            </button>
          </span>
        </div>
      </section>

      {modal && (
        <div className="st-overlay" onClick={() => setModal(null)}>
          <div className="st-modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
            <div className="st-modalhead">
              <div>
                <h2>{modal.mode === 'add' ? 'Add Book' : 'Edit Book'}</h2>
                <p>{modal.mode === 'add' ? 'Add a new book to the store.' : 'Update the book details below.'}</p>
              </div>
              <button className="st-x" onClick={() => setModal(null)} aria-label="Close">
                ✕
              </button>
            </div>
            <div className="st-formgrid">
              <div>
                <label className="st-label">
                  Book Cover{modal.mode === 'add' ? <i>*</i> : null}
                </label>
                {form.cover ? (
                  <div className="st-coverprev">
                    <img src={form.cover} alt="Book cover preview" />
                    <button
                      className="st-coverrm"
                      aria-label="Remove cover"
                      onClick={() => setForm((p) => ({ ...p, cover: '' }))}
                    >
                      🗑
                    </button>
                  </div>
                ) : (
                  <div className="st-upload">
                    <span aria-hidden="true">📖</span>
                    <b>Upload Book Cover</b>
                    <span className="muted">JPG, PNG (Max 5MB)</span>
                    <label className="st-choose">
                      ⬆ Choose Image
                      <input type="file" accept="image/*" hidden onChange={onCoverFile} />
                    </label>
                  </div>
                )}
                {form.cover ? (
                  <label className="st-change">
                    ⬆ {modal.mode === 'add' ? 'Choose Image' : 'Change Cover Image'}
                    <span className="muted">JPG, PNG (Max 5MB)</span>
                    <input type="file" accept="image/*" hidden onChange={onCoverFile} />
                  </label>
                ) : null}
              </div>
              <div className="st-fields">
                <label className="st-label">
                  Book Title <i>*</i>
                </label>
                <input value={form.title} onChange={(e) => setForm((p) => ({ ...p, title: e.target.value }))} placeholder="Enter book title" />
                <label className="st-label">
                  Author <i>*</i>
                </label>
                <input value={form.author} onChange={(e) => setForm((p) => ({ ...p, author: e.target.value }))} placeholder="Enter author name" />
                <label className="st-label">
                  Description <i>*</i>
                </label>
                <div className="st-tawrap">
                  <textarea
                    value={form.description}
                    maxLength={500}
                    onChange={(e) => setForm((p) => ({ ...p, description: e.target.value }))}
                    placeholder="Enter a brief description about the book..."
                  />
                  <span>{form.description.length}/500</span>
                </div>
              </div>
            </div>
            <div className="st-formrow">
              <div>
                <label className="st-label">
                  Price (₹) <i>*</i>
                </label>
                <span className="st-inwrap">
                  <span aria-hidden="true">₹</span>
                  <input
                    inputMode="numeric"
                    value={form.price}
                    onChange={(e) => setForm((p) => ({ ...p, price: e.target.value.replace(/[^0-9]/g, '') }))}
                    placeholder="Enter price"
                  />
                </span>
              </div>
              <div>
                <label className="st-label">
                  Stock Quantity <i>*</i>
                </label>
                <input
                  inputMode="numeric"
                  value={form.stock}
                  onChange={(e) => setForm((p) => ({ ...p, stock: e.target.value.replace(/[^0-9]/g, '') }))}
                  placeholder="Enter stock quantity"
                />
              </div>
            </div>
            <label className="st-label">
              Status <i>*</i>
            </label>
            <select value={form.status} onChange={(e) => setForm((p) => ({ ...p, status: e.target.value }))}>
              <option value="AVAILABLE">● Available</option>
              <option value="OUT_OF_STOCK">● Out of Stock</option>
            </select>
            {formErr && <div className="st-err">{formErr}</div>}
            <div className="st-modalfoot">
              <button className="st-ghost" onClick={() => setModal(null)}>
                Cancel
              </button>
              <button className="st-solid" disabled={saving} onClick={() => void submit()}>
                {modal.mode === 'add' ? 'Add Book' : 'Save Changes'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function StatCard({
  icon,
  iconBg,
  value,
  label,
  delta,
  zero,
}: {
  icon: string;
  iconBg: string;
  value: number;
  label: string;
  delta: number;
  zero?: boolean;
}) {
  return (
    <div className="st-stat">
      <span className="st-staticon" style={{ background: iconBg }} aria-hidden="true">
        {icon}
      </span>
      <span className="st-statnum">
        <b>{value}</b>
        <span>{label}</span>
      </span>
      <span className={'st-delta' + (zero ? ' zero' : '')}>{zero && delta === 0 ? '0' : '+' + delta}</span>
    </div>
  );
}
