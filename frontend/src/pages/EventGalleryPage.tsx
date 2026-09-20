import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { apiClient } from '../lib/apiClient';
import { UploadMediaModal } from '../components/UploadMediaModal';

interface GalleryItem {
  id: string;
  eventId: string;
  uploadedBy: string;
  uploaderName: string;
  mediaType: 'PHOTO' | 'VIDEO';
  mediaUrl: string;
  caption?: string;
  uploadedAt: string;
}

interface EventDetail {
  id: string;
  circleId: string;
  circleName: string;
  title: string;
  readingTopic?: string;
  eventDate?: string;
  status: 'DRAFT' | 'PUBLISHED' | 'COMPLETED' | 'CANCELLED';
  isOrganizerOrAdmin: boolean;
}

export const EventGalleryPage: React.FC = () => {
  const { id, eventId } = useParams<{ id?: string; eventId?: string }>();
  const effectiveEventId = eventId || id;
  const navigate = useNavigate();

  const [event, setEvent] = useState<EventDetail | null>(null);
  const [items, setItems] = useState<GalleryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionNotice, setActionNotice] = useState<string | null>(null);

  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
  const [selectedPhoto, setSelectedPhoto] = useState<GalleryItem | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const fetchEventAndGallery = async () => {
    if (!effectiveEventId) return;
    try {
      setLoading(true);
      setError('');
      const [eventRes, galleryRes] = await Promise.all([
        apiClient.get<EventDetail>(`/events/${effectiveEventId}`),
        apiClient.get<GalleryItem[]>(`/events/${effectiveEventId}/gallery`),
      ]);
      setEvent(eventRes.data);
      setItems(galleryRes.data);
    } catch (err: any) {
      if (err?.response?.status === 403) {
        setError("You must be an approved member of this circle to view the event memories gallery.");
      } else {
        setError(err?.response?.data?.message || 'Failed to load event gallery');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEventAndGallery();
  }, [effectiveEventId]);

  const handleUpload = async (data: { mediaType: 'PHOTO' | 'VIDEO'; file: File; caption?: string }) => {
    if (!effectiveEventId) return;
    const formData = new FormData();
    formData.append('file', data.file);
    formData.append('mediaType', data.mediaType);
    if (data.caption) {
      formData.append('caption', data.caption);
    }
    await apiClient.post(`/events/${effectiveEventId}/gallery`, formData);
    setActionNotice('Photo / Video added to gallery!');
    setTimeout(() => setActionNotice(null), 3000);
    const res = await apiClient.get<GalleryItem[]>(`/events/${effectiveEventId}/gallery`);
    setItems(res.data);
  };

  const handleDelete = async (itemId: string) => {
    if (!window.confirm('Are you sure you want to remove this memory from the gallery?')) return;
    try {
      setDeletingId(itemId);
      await apiClient.delete(`/gallery/${itemId}`);
      setActionNotice('Item removed from gallery.');
      setTimeout(() => setActionNotice(null), 3000);
      setItems((prev) => prev.filter((it) => it.id !== itemId));
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to delete gallery item');
      setTimeout(() => setError(''), 4000);
    } finally {
      setDeletingId(null);
    }
  };

  if (loading) {
    return (
      <div className="container" style={{ padding: '3.5rem 1rem', textAlign: 'center' }}>
        <p style={{ color: '#94a3b8' }}>Loading gallery memories...</p>
      </div>
    );
  }

  if (error && !event) {
    return (
      <div className="container" style={{ padding: '3.5rem 1rem', maxWidth: '650px' }}>
        <div className="alert alert-danger">
          <p>{error}</p>
          <button onClick={() => navigate(-1)} className="btn btn-secondary" style={{ marginTop: '1rem' }}>
            Go Back
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="container" style={{ padding: '2rem 1rem', maxWidth: '1100px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', flexWrap: 'wrap', gap: '1rem' }}>
        <button
          onClick={() => navigate(`/events/${effectiveEventId}`)}
          className="btn btn-secondary"
          style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}
        >
          ← Back to Event Details
        </button>

        {event?.isOrganizerOrAdmin && (
          <button
            onClick={() => setIsUploadModalOpen(true)}
            className="btn btn-primary"
            style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}
          >
            📸 + Add Photo / Video
          </button>
        )}
      </div>

      {actionNotice && (
        <div className="alert alert-success" style={{ marginBottom: '1.5rem' }}>
          ✓ {actionNotice}
        </div>
      )}

      {error && (
        <div className="alert alert-danger" style={{ marginBottom: '1.5rem' }}>
          ⚠️ {error}
        </div>
      )}

      {event && (
        <div
          className="card glass-card"
          style={{
            padding: '1.75rem',
            borderRadius: '1.25rem',
            background: 'rgba(30, 41, 59, 0.45)',
            border: '1px solid rgba(255, 255, 255, 0.08)',
            marginBottom: '2rem',
          }}
        >
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginBottom: '0.4rem' }}>
            <span className="badge badge-completed">COMPLETED EVENT</span>
            {event.readingTopic && (
              <span
                style={{
                  fontSize: '0.75rem',
                  padding: '0.2rem 0.6rem',
                  borderRadius: '9999px',
                  backgroundColor: 'rgba(99, 102, 241, 0.15)',
                  color: '#a5b4fc',
                }}
              >
                📖 {event.readingTopic}
              </span>
            )}
          </div>
          <h1 style={{ fontSize: '1.85rem', fontWeight: 800, margin: '0.2rem 0 0.5rem 0', color: '#f8fafc' }}>
            {event.title} — Memory Gallery
          </h1>
          <p style={{ margin: 0, color: '#94a3b8', fontSize: '0.9rem' }}>
            Preserved moments from <Link to={`/circles/${event.circleId}`} style={{ color: 'var(--accent-indigo, #6366f1)' }}>{event.circleName}</Link> • {items.length} {items.length === 1 ? 'memory' : 'memories'} shared
          </p>
        </div>
      )}

      {items.length === 0 ? (
        <div
          className="glass-card"
          style={{
            textAlign: 'center',
            padding: '4rem 1.5rem',
            borderRadius: '1.25rem',
            background: 'rgba(30, 41, 59, 0.3)',
            border: '1px dashed rgba(255, 255, 255, 0.1)',
          }}
        >
          <div style={{ fontSize: '3.5rem', marginBottom: '1rem' }}>📷</div>
          <h3 style={{ fontSize: '1.3rem', fontWeight: 700, margin: '0 0 0.5rem 0', color: '#f8fafc' }}>
            No gallery memories yet
          </h3>
          <p style={{ color: '#94a3b8', maxWidth: '480px', margin: '0 auto 1.5rem auto', lineHeight: 1.6 }}>
            {event?.isOrganizerOrAdmin
              ? 'Upload snapshots, group photos, and video clips from this gathering to relive the discussion with your circle.'
              : 'The organizer has not posted any photos or video highlights from this event yet.'}
          </p>
          {event?.isOrganizerOrAdmin && (
            <button
              onClick={() => setIsUploadModalOpen(true)}
              className="btn btn-primary"
            >
              Upload First Memory
            </button>
          )}
        </div>
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
            gap: '1.5rem',
          }}
        >
          {items.map((item) => (
            <div
              key={item.id}
              className="card glass-card"
              style={{
                borderRadius: '1rem',
                overflow: 'hidden',
                background: 'rgba(15, 23, 42, 0.65)',
                border: '1px solid rgba(255, 255, 255, 0.08)',
                display: 'flex',
                flexDirection: 'column',
              }}
            >
              <div style={{ position: 'relative', width: '100%', height: '220px', backgroundColor: '#020617' }}>
                {item.mediaType === 'PHOTO' ? (
                  <img
                    src={item.mediaUrl}
                    alt={item.caption || 'Event photo'}
                    onClick={() => setSelectedPhoto(item)}
                    style={{
                      width: '100%',
                      height: '100%',
                      objectFit: 'cover',
                      cursor: 'pointer',
                      transition: 'transform 0.3s ease',
                    }}
                    onError={(e) => {
                      // Fallback placeholder if image fails to load
                      (e.target as HTMLElement).style.display = 'none';
                    }}
                  />
                ) : (
                  <video
                    src={item.mediaUrl}
                    controls
                    preload="metadata"
                    playsInline
                    style={{
                      width: '100%',
                      height: '100%',
                      objectFit: 'cover',
                      backgroundColor: '#000',
                    }}
                  />
                )}

                <span
                  style={{
                    position: 'absolute',
                    top: '0.75rem',
                    left: '0.75rem',
                    padding: '0.2rem 0.5rem',
                    borderRadius: '0.4rem',
                    backgroundColor: 'rgba(0, 0, 0, 0.65)',
                    color: '#f8fafc',
                    fontSize: '0.75rem',
                    fontWeight: 600,
                    backdropFilter: 'blur(4px)',
                  }}
                >
                  {item.mediaType === 'PHOTO' ? '📷 Photo' : '🎬 Video'}
                </span>
              </div>

              <div style={{ padding: '1rem', flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                {item.caption && (
                  <p style={{ margin: '0 0 0.75rem 0', fontSize: '0.95rem', color: '#f1f5f9', lineHeight: 1.5 }}>
                    {item.caption}
                  </p>
                )}

                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    marginTop: 'auto',
                    paddingTop: '0.5rem',
                    borderTop: '1px solid rgba(255, 255, 255, 0.05)',
                    fontSize: '0.8rem',
                    color: '#64748b',
                  }}
                >
                  <span>Shared by {item.uploaderName}</span>
                  {event?.isOrganizerOrAdmin && (
                    <button
                      onClick={() => handleDelete(item.id)}
                      disabled={deletingId === item.id}
                      style={{
                        background: 'none',
                        border: 'none',
                        color: '#f87171',
                        cursor: 'pointer',
                        fontSize: '0.8rem',
                        fontWeight: 600,
                        padding: '0.2rem 0.4rem',
                      }}
                    >
                      {deletingId === item.id ? 'Deleting...' : 'Delete'}
                    </button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Lightbox Modal for Photos */}
      {selectedPhoto && (
        <div
          onClick={() => setSelectedPhoto(null)}
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.9)',
            backdropFilter: 'blur(8px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1100,
            padding: '2rem',
          }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              maxWidth: '90vw',
              maxHeight: '90vh',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
            }}
          >
            <img
              src={selectedPhoto.mediaUrl}
              alt={selectedPhoto.caption || 'Event memory'}
              style={{
                maxWidth: '100%',
                maxHeight: '75vh',
                borderRadius: '0.75rem',
                boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.8)',
              }}
            />
            {selectedPhoto.caption && (
              <p style={{ color: '#f8fafc', fontSize: '1.1rem', marginTop: '1rem', textAlign: 'center' }}>
                {selectedPhoto.caption}
              </p>
            )}
            <button
              onClick={() => setSelectedPhoto(null)}
              className="btn btn-secondary"
              style={{ marginTop: '0.75rem', fontSize: '0.85rem' }}
            >
              Close Lightbox
            </button>
          </div>
        </div>
      )}

      {event && (
        <UploadMediaModal
          isOpen={isUploadModalOpen}
          onClose={() => setIsUploadModalOpen(false)}
          onSubmit={handleUpload}
          eventTitle={event.title}
        />
      )}
    </div>
  );
};
