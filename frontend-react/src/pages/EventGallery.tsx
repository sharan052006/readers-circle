import { useCallback, useEffect, useRef, useState, type ChangeEvent } from 'react';
import { ApiError, api, apiBase, getAccess, resolveMedia } from '../lib/api';

export type GalleryItem = {
  id: string;
  eventId?: string;
  uploadedBy?: string;
  uploaderName?: string;
  mediaType?: string;
  mediaUrl?: string;
  caption?: string;
  uploadedAt?: string;
};

function errMsg(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  if (e instanceof Error) return e.message;
  return 'Request failed';
}

export async function listEventGallery(eventId: string): Promise<GalleryItem[]> {
  const d = await api<GalleryItem[]>('/api/events/' + eventId + '/gallery');
  return Array.isArray(d) ? d : [];
}

/**
 * Event photo/video gallery for COMPLETED events.
 * Organisers/admins (canUpload) can add and remove media; approved members
 * who took part can view. Backend enforces the gates; this is presentational.
 */
export default function EventGallery({
  eventId,
  canUpload,
}: {
  eventId: string;
  canUpload?: boolean;
}) {
  const [items, setItems] = useState<GalleryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const [lightbox, setLightbox] = useState<GalleryItem | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await listEventGallery(eventId));
      setErr('');
    } catch (e) {
      setErr(errMsg(e));
    } finally {
      setLoading(false);
    }
  }, [eventId]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!lightbox) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setLightbox(null);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [lightbox]);

  async function onPick(e: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files || []);
    if (!files.length) return;
    setBusy(true);
    setErr('');
    try {
      for (const f of files) {
        const mediaType = f.type.startsWith('video') ? 'VIDEO' : 'PHOTO';
        const fd = new FormData();
        fd.append('file', f);
        fd.append('mediaType', mediaType);
        const headers: Record<string, string> = {};
        const t = getAccess();
        if (t) headers.Authorization = 'Bearer ' + t;
        const r = await fetch(apiBase() + '/api/events/' + eventId + '/gallery', {
          method: 'POST',
          headers,
          body: fd,
        });
        if (!r.ok) {
          const d = (await r.json().catch(() => ({}))) as { message?: string; error?: string };
          throw new ApiError(r.status, String(d.message || d.error || 'Upload failed: ' + r.status));
        }
      }
      await load();
    } catch (e2) {
      setErr(errMsg(e2));
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  }

  async function remove(item: GalleryItem) {
    if (!window.confirm('Remove this item from the gallery?')) return;
    try {
      await api('/api/gallery/' + item.id, { method: 'DELETE' });
      setItems((prev) => prev.filter((x) => x.id !== item.id));
    } catch (e) {
      setErr(errMsg(e));
    }
  }

  async function downloadMedia(url?: string, caption?: string) {
    if (!url) return;
    try {
      const resolved = resolveMedia(url);
      const downloadUrl = resolved.includes('/api/media/')
        ? resolved + (resolved.includes('?') ? '&' : '?') + 'download=true'
        : resolved;
      const res = await fetch(downloadUrl);
      const blob = await res.blob();
      const blobUrl = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = blobUrl;
      const cleanName = (caption || 'event-photo')
        .toLowerCase()
        .replace(/[^a-z0-9_-]/g, '_')
        .slice(0, 30);
      const ext = url.toLowerCase().includes('.mp4') ? '.mp4' : '.jpg';
      a.download = cleanName + ext;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(blobUrl), 1000);
    } catch {
      const a = document.createElement('a');
      a.href = resolveMedia(url);
      a.download = (caption || 'event-photo') + '.jpg';
      a.target = '_blank';
      document.body.appendChild(a);
      a.click();
      a.remove();
    }
  }

  async function downloadAll() {
    if (!items.length) return;
    setBusy(true);
    try {
      for (let i = 0; i < items.length; i++) {
        const it = items[i];
        await downloadMedia(it.mediaUrl, it.caption || `event-photo-${i + 1}`);
        if (i < items.length - 1) {
          await new Promise((r) => setTimeout(r, 250));
        }
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="evgallery">
      <div className="eg-head">
        <h3>
          🖼 Event Gallery <span className="countpill">{items.length}</span>
        </h3>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
          {items.length > 0 ? (
            <button
              className="oabtn"
              type="button"
              style={{
                background: '#FAF5EE',
                border: '1px solid #DFD2BF',
                color: 'var(--ink)',
                cursor: 'pointer',
                fontWeight: 600,
              }}
              disabled={busy}
              onClick={() => void downloadAll()}
            >
              📥 Download All ({items.length})
            </button>
          ) : null}
          {canUpload ? (
            <>
              <input
                ref={fileRef}
                type="file"
                accept="image/jpeg,image/png,image/webp,video/mp4,video/webm,video/quicktime"
                multiple
                hidden
                onChange={(e) => void onPick(e)}
              />
              <button className="oabtn primary" disabled={busy} onClick={() => fileRef.current?.click()}>
                {busy ? 'Uploading…' : '📷 Upload Photos'}
              </button>
            </>
          ) : null}
        </div>
      </div>

      {err ? <div className="err">{err}</div> : null}

      {loading ? (
        <div className="empty">Loading gallery…</div>
      ) : !items.length ? (
        <div className="eg-empty">
          {canUpload
            ? 'No photos yet — share the first memories from this event.'
            : 'The organiser hasn’t shared any photos from this event yet.'}
        </div>
      ) : (
        <div className="eg-grid">
          {items.map((it) => {
            const url = resolveMedia(it.mediaUrl);
            const isVideo = it.mediaType === 'VIDEO';
            return (
              <figure className="eg-tile" key={it.id}>
                <button className="eg-thumb" type="button" onClick={() => setLightbox(it)}>
                  {isVideo ? (
                    <video src={url} muted playsInline preload="metadata" />
                  ) : (
                    <img src={url} alt={it.caption || 'Event photo'} loading="lazy" />
                  )}
                  {isVideo ? <span className="eg-video-badge">▶</span> : null}
                </button>
                <button
                  className="eg-download"
                  type="button"
                  title="Download"
                  aria-label="Download image"
                  onClick={(e) => {
                    e.stopPropagation();
                    void downloadMedia(it.mediaUrl, it.caption);
                  }}
                >
                  📥
                </button>
                <figcaption>
                  {it.caption || it.uploaderName ? (
                    <div className="eg-info">
                      {it.caption ? <span className="eg-cap">{it.caption}</span> : null}
                      {it.uploaderName ? <span className="eg-by">by {it.uploaderName}</span> : null}
                    </div>
                  ) : null}
                  <button
                    className="eg-card-download"
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      void downloadMedia(it.mediaUrl, it.caption);
                    }}
                  >
                    📥 Download
                  </button>
                </figcaption>
                {canUpload ? (
                  <button className="eg-del" title="Remove" aria-label="Remove" onClick={() => void remove(it)}>
                    ✕
                  </button>
                ) : null}
              </figure>
            );
          })}
        </div>
      )}

      {lightbox ? (
        <div className="eg-lightbox" onClick={() => setLightbox(null)}>
          <div className="eg-lb-topbar" onClick={(e) => e.stopPropagation()}>
            <button
              className="eg-lb-download"
              title="Download full size"
              onClick={() => void downloadMedia(lightbox.mediaUrl, lightbox.caption)}
            >
              📥 Download
            </button>
            <button className="eg-lb-close" aria-label="Close" onClick={() => setLightbox(null)}>
              ✕
            </button>
          </div>
          {lightbox.mediaType === 'VIDEO' ? (
            <video
              className="eg-lb-media"
              src={resolveMedia(lightbox.mediaUrl)}
              controls
              autoPlay
              onClick={(e) => e.stopPropagation()}
            />
          ) : (
            <img
              className="eg-lb-media"
              src={resolveMedia(lightbox.mediaUrl)}
              alt={lightbox.caption || 'Event photo'}
              onClick={(e) => e.stopPropagation()}
            />
          )}
          {lightbox.uploaderName || lightbox.caption ? (
            <div className="eg-lb-cap" onClick={(e) => e.stopPropagation()}>
              {lightbox.caption ? <strong>{lightbox.caption} </strong> : null}
              {lightbox.uploaderName ? <span>by {lightbox.uploaderName}</span> : null}
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
