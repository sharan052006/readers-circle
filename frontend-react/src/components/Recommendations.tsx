import { useCallback, useEffect, useState, type ChangeEvent } from 'react';
import { api } from '../lib/api';
import './Recommendations.css';

export type RecComment = { author?: string; content?: string };
export type Rec = {
  id: string;
  bookTitle?: string;
  author?: string;
  genre?: string;
  rating?: number;
  review?: string;
  reviewText?: string;
  coverImageUrl?: string;
  coverUrl?: string;
  recommenderName?: string;
  upvotesCount?: number;
  userUpvoted?: boolean;
  comments?: RecComment[];
};
export type TopRecommender = { name?: string; recommendationsCount?: number; count?: number };

function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

/* ------------------------------------------------------------------ */
/* Book recommendations — same functionality everywhere (admin,        */
/* reader, organiser): browse + sort + genre filter, post, comment,    */
/* like/upvote, save, trending books, top recommenders.                */
/* ------------------------------------------------------------------ */

export default function RecommendationsView() {
  const [recSort, setRecSort] = useState('mostLiked');
  const [recGenre, setRecGenre] = useState('');
  const [recs, setRecs] = useState<Rec[]>([]);
  const [topRecs, setTopRecs] = useState<TopRecommender[]>([]);
  const [recModal, setRecModal] = useState(false);
  const [recFormErr, setRecFormErr] = useState('');
  const [pageErr, setPageErr] = useState('');
  const [recTitle, setRecTitle] = useState('');
  const [recAuthor, setRecAuthor] = useState('');
  const [recMGenre, setRecMGenre] = useState('');
  const [recReview, setRecReview] = useState('');
  const [recSpoiler, setRecSpoiler] = useState(false);
  const [recTagInput, setRecTagInput] = useState('');
  const [recTags, setRecTags] = useState<string[]>([]);
  const [recRating, setRecRating] = useState(5);
  const [recCover, setRecCover] = useState('');
  const [savedMarks, setSavedMarks] = useState<Record<string, boolean>>({});

  const loadRecs = useCallback(async () => {
    try {
      setPageErr('');
      const data = await api<Rec[]>(
        '/api/recommendations?sort=' + encodeURIComponent(recSort) + (recGenre ? '&genre=' + encodeURIComponent(recGenre) : ''),
      );
      setRecs(Array.isArray(data) ? data : []);
      try {
        const top = await api<TopRecommender[]>('/api/recommendations/top-recommenders');
        setTopRecs(Array.isArray(top) ? top : []);
      } catch {
        setTopRecs([]);
      }
    } catch (e) {
      setPageErr(errMsg(e));
    }
  }, [recSort, recGenre]);

  useEffect(() => {
    void loadRecs();
  }, [loadRecs]);

  function onRecCoverFile(e: ChangeEvent<HTMLInputElement>) {
    const f = e.target.files && e.target.files[0];
    if (!f) return;
    const r = new FileReader();
    r.onload = () => {
      if (typeof r.result === 'string') setRecCover(r.result);
    };
    r.readAsDataURL(f);
  }

  async function postRec() {
    setRecFormErr('');
    const bookTitle = recTitle.trim();
    const author = recAuthor.trim();
    if (!bookTitle || !author) {
      setRecFormErr('Book title and author are required.');
      return;
    }
    try {
      await api('/api/recommendations', {
        method: 'POST',
        body: JSON.stringify({
          bookTitle,
          author,
          genre: recMGenre || 'General',
          rating: recRating,
          review: recReview.trim() || null,
          spoiler: recSpoiler,
          tags: recTags,
          coverImageUrl: recCover || null,
        }),
      });
      setRecTitle('');
      setRecAuthor('');
      setRecMGenre('');
      setRecReview('');
      setRecSpoiler(false);
      setRecTags([]);
      setRecCover('');
      setRecRating(5);
      setRecModal(false);
      await loadRecs();
    } catch (e) {
      setRecFormErr(errMsg(e));
    }
  }

  async function upvoteRec(id: string) {
    try {
      await api('/api/recommendations/' + id + '/upvote', { method: 'POST' });
      await loadRecs();
    } catch (e) {
      setPageErr(errMsg(e));
    }
  }

  const recTrending = [...recs].sort((a, b) => (b.upvotesCount || 0) - (a.upvotesCount || 0)).slice(0, 5);

  return (
    <div className="rec-scope">
      <div className="rec-banner">
        <div className="rec-banner-left">
          <span className="rec-banner-ic">📖</span>
          <div>
            <h2>BOOK RECOMMENDATIONS</h2>
            <p>Discover your next great read from the community</p>
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span style={{ fontSize: 40 }}>📚☕</span>
          <button className="btn" onClick={() => setRecModal(true)}>
            ➤ Recommend a Book
          </button>
        </div>
      </div>
      {pageErr && <div className="err">{pageErr}</div>}
      <div className="rec-filters">
        <select value={recSort} onChange={(e) => setRecSort(e.target.value)} aria-label="Sort recommendations">
          <option value="mostLiked">Sort by: Most Liked</option>
          <option value="recent">Sort by: Most Recent</option>
          <option value="rating">Sort by: Top Rated</option>
        </select>
        <select value={recGenre} onChange={(e) => setRecGenre(e.target.value)} aria-label="Filter by genre">
          <option value="">Filter by Genre</option>
          <option>General</option>
          <option>Literature</option>
          <option>Science Fiction</option>
          <option>Classic</option>
          <option>Mystery</option>
          <option>Fiction</option>
          <option>Tamil Literature</option>
          <option>Philosophy</option>
        </select>
      </div>
      <div className="rec-grid">
        <div>
          {recs.length ? (
            recs.map((d) => (
              <div className="rec-card" key={d.id}>
                <div className="rec-top">
                  {((d.coverImageUrl && d.coverImageUrl.trim()) || (d.coverUrl && d.coverUrl.trim())) ? (
                    <img
                      className="rec-cover"
                      alt={'Cover of ' + (d.bookTitle || 'book')}
                      src={(d.coverImageUrl && d.coverImageUrl.trim()) || (d.coverUrl && d.coverUrl.trim()) || ''}
                      onError={(e) => {
                        e.currentTarget.style.display = 'none';
                      }}
                    />
                  ) : (
                    <span className="rec-cover" aria-hidden="true">
                      📖<br />
                      {(d.bookTitle || '')[0] || ''}
                    </span>
                  )}
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <h3>{(d.bookTitle || '').toUpperCase()}</h3>
                    <div className="rec-author">{d.author || ''}</div>
                    <div className="rec-byline">
                      <span className="rec-mini-av">{((d.recommenderName || '?')[0] || '?').toUpperCase()}</span>
                      <span>
                        Recommended by
                        <br />
                        <b>{d.recommenderName || ''}</b>
                      </span>
                    </div>
                    <span className="rec-stars">
                      {[1, 2, 3, 4, 5].map((i) => (
                        <span key={i} className={i <= (d.rating || 0) ? '' : 'off'}>
                          ★
                        </span>
                      ))}
                    </span>
                    <p className="rec-review">{d.review || d.reviewText || ''}</p>
                    <span className="bd-tagpill">{d.genre || ''}</span>
                  </div>
                </div>
                      <div className="rec-actions">
                        <button className={'rec-act' + (d.userUpvoted ? ' liked' : '')} onClick={() => void upvoteRec(d.id)}>
                          👍 Like/Upvote (count {d.upvotesCount || 0})
                        </button>
                        <button
                          className="rec-save"
                          title="Save"
                          onClick={() => setSavedMarks((prev) => ({ ...prev, [d.id]: !prev[d.id] }))}
                        >
                          {savedMarks[d.id] ? '📌' : '🔖'}
                        </button>
                      </div>
              </div>
            ))
          ) : (
            <div className="rec-card">
              <p className="bd-empty">No recommendations yet — share the first book.</p>
            </div>
          )}
        </div>
        <div>
          <div className="rec-side">
            <h3>TRENDING BOOKS</h3>
            <div>
              {recTrending.length ? (
                recTrending.map((d, i) => (
                  <div className="rec-trow" key={d.id}>
                    <span className="rec-rank">{i + 1}</span>
                    <span className="rec-mini-av" style={{ width: 30, height: 38, borderRadius: 6 }}>
                      {((d.bookTitle || '?')[0] || '?').toUpperCase()}
                    </span>
                    <div>
                      <b>{d.bookTitle || ''}</b>
                      <br />
                      <span style={{ color: 'var(--muted)', fontSize: 11.5 }}>{d.author || ''}</span>
                    </div>
                  </div>
                ))
              ) : (
                <p className="sub">No trending books yet.</p>
              )}
            </div>
          </div>
          <div className="rec-side">
            <h3>TOP RECOMMENDERS</h3>
            <div>
              {topRecs.length ? (
                topRecs.map((t, i) => (
                  <div className="rec-trow" key={i}>
                    <span className="rec-mini-av">{((t.name || '?')[0] || '?').toUpperCase()}</span>
                    <div>
                      <b>
                        {t.name || ''} ({t.recommendationsCount || t.count || 0})
                      </b>
                    </div>
                    <span style={{ marginLeft: 'auto', fontSize: 11.5, color: 'var(--muted)' }}>{t.recommendationsCount || t.count || 0} Rec</span>
                  </div>
                ))
              ) : (
                <p className="sub">No recommenders yet.</p>
              )}
            </div>
          </div>
        </div>
      </div>
      {!recModal ? null : (
        <div
          className="rec-modal-veil"
          onClick={(e) => {
            if (e.target === e.currentTarget) setRecModal(false);
          }}
        >
          <div className="rec-modal" role="dialog" aria-label="Share a book recommendation">
            <h2>SHARE A BOOK RECOMMENDATION</h2>
            {recFormErr && <div className="err">{recFormErr}</div>}
            <div className="rec-coverrow">
              <div>
                <div className="rec-coverprev">
                  {recCover ? <img alt="" src={recCover} /> : '🖼️'}
                </div>
                <label className="btn ghost" style={{ display: 'block', textAlign: 'center', marginTop: 8, padding: '8px 10px', cursor: 'pointer' }}>
                  Change Cover
                  <input type="file" accept="image/*" hidden onChange={onRecCoverFile} />
                </label>
              </div>
              <div style={{ flex: 1 }}>
                <label htmlFor="recTitle">Book Title</label>
                <input type="text" id="recTitle" value={recTitle} onChange={(e) => setRecTitle(e.target.value)} placeholder="Enter title..." />
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                  <div>
                    <label htmlFor="recAuthor">Author</label>
                    <input type="text" id="recAuthor" value={recAuthor} onChange={(e) => setRecAuthor(e.target.value)} placeholder="Enter author..." />
                  </div>
                  <div>
                    <label htmlFor="recMGenre">Genre</label>
                    <select id="recMGenre" value={recMGenre} onChange={(e) => setRecMGenre(e.target.value)}>
                      <option value="">Select Genre...</option>
                      <option>General</option>
                      <option>Literature</option>
                      <option>Science Fiction</option>
                      <option>Classic</option>
                      <option>Mystery</option>
                      <option>Fiction</option>
                      <option>Tamil Literature</option>
                      <option>Philosophy</option>
                    </select>
                  </div>
                </div>
                <label>Rating</label>
                <div className="rec-rate" role="radiogroup" aria-label="Rating">
                  {[1, 2, 3, 4, 5].map((i) => (
                    <button key={i} type="button" data-n={i} className={i <= recRating ? 'lit' : ''} aria-label={i + ' stars'} onClick={() => setRecRating(i)}>
                      ★
                    </button>
                  ))}
                </div>
              </div>
            </div>
            <label htmlFor="recReview">Your Review</label>
            <div style={{ border: '1px solid var(--border)', borderRadius: 10, overflow: 'hidden' }}>
              <div style={{ display: 'flex', gap: 12, padding: '6px 12px', borderBottom: '1px solid var(--border)', fontSize: 12.5, color: '#6D625C' }}>
                <b>B</b>
                <i>I</i>
                <u>U</u>
                <span>•≡</span>
                <span>1≡</span>
              </div>
              <textarea id="recReview" style={{ border: 0 }} value={recReview} onChange={(e) => setRecReview(e.target.value)} placeholder="I absolutely loved this book! The characters are well-developed and the plot is..." />
            </div>
            <label style={{ fontWeight: 400 }}>
              <input type="checkbox" checked={recSpoiler} onChange={(e) => setRecSpoiler(e.target.checked)} /> This review contains spoilers
            </label>
            <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
              <input
                type="text"
                value={recTagInput}
                onChange={(e) => setRecTagInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    const v = recTagInput.trim();
                    if (v) {
                      setRecTags((prev) => [...prev, v]);
                      setRecTagInput('');
                    }
                  }
                }}
                placeholder="Add tag (e.g. Sci-Fi, Hard Science) and press Enter"
                style={{ flex: 1 }}
              />
              <button
                className="btn ghost"
                type="button"
                onClick={() => {
                  const v = recTagInput.trim();
                  if (!v) return;
                  setRecTags((prev) => [...prev, v]);
                  setRecTagInput('');
                }}
              >
                + Add Tag
              </button>
            </div>
            <div>
              {recTags.map((t, i) => (
                <span className="rec-tagchip" key={i}>
                  {t}{' '}
                  <a
                    href="#"
                    style={{ color: 'inherit' }}
                    onClick={(e) => {
                      e.preventDefault();
                      setRecTags((prev) => prev.filter((_, j) => j !== i));
                    }}
                  >
                    ×
                  </a>
                </span>
              ))}
            </div>
            <div style={{ display: 'flex', gap: 10, marginTop: 16 }}>
              <button className="btn" type="button" style={{ flex: 1 }} onClick={() => void postRec()}>
                Post Recommendation
              </button>
              <button className="btn ghost" type="button" onClick={() => setRecModal(false)}>
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
