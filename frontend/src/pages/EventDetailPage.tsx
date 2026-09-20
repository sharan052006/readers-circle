import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { api } from '../lib/apiClient';
import { CapacityBar } from '../components/CapacityBar';
import { EventModal } from '../components/EventModal';
import { AttendeeModal } from '../components/AttendeeModal';

interface EventDetail {
  id: string;
  circleId: string;
  circleName: string;
  title: string;
  description?: string;
  readingTopic?: string;
  eventDate?: string;
  eventTime?: string;
  venue?: string;
  capacity: number | null;
  registeredCount: number;
  registrationDeadline?: string;
  coverImageUrl?: string;
  status: 'DRAFT' | 'PUBLISHED' | 'COMPLETED' | 'CANCELLED';
  isRegistered: boolean;
  isOrganizerOrAdmin: boolean;
  createdAt: string;
}

export const EventDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [event, setEvent] = useState<EventDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [isAttendeeModalOpen, setIsAttendeeModalOpen] = useState(false);

  const fetchEvent = async () => {
    if (!id) return;
    try {
      setLoading(true);
      setError('');
      const res = await api.get(`/events/${id}`);
      setEvent(res.data);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to load event details');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEvent();
  }, [id]);

  const handleRegister = async () => {
    if (!event) return;
    try {
      setActionLoading(true);
      setError('');
      await api.post(`/events/${event.id}/register`);
      await fetchEvent();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to register for event');
    } finally {
      setActionLoading(false);
    }
  };

  const handleCancelRegistration = async () => {
    if (!event) return;
    try {
      setActionLoading(true);
      setError('');
      await api.delete(`/events/${event.id}/register`);
      await fetchEvent();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to cancel registration');
    } finally {
      setActionLoading(false);
    }
  };

  const handleUpdateEvent = async (payload: any) => {
    if (!event) return;
    await api.patch(`/events/${event.id}`, payload);
    await fetchEvent();
  };

  const handleStatusChange = async (newStatus: 'COMPLETED' | 'CANCELLED') => {
    if (!event) return;
    try {
      setActionLoading(true);
      setError('');
      await api.patch(`/events/${event.id}`, { status: newStatus });
      await fetchEvent();
    } catch (err: any) {
      setError(err?.response?.data?.message || `Failed to transition event to ${newStatus}`);
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="container" style={{ padding: '3rem 1rem', textAlign: 'center' }}>
        <p style={{ color: '#94a3b8' }}>Loading event details...</p>
      </div>
    );
  }

  if (error && !event) {
    return (
      <div className="container" style={{ padding: '3rem 1rem', textAlign: 'center' }}>
        <div className="alert alert-danger" style={{ maxWidth: '600px', margin: '0 auto' }}>
          <p>{error}</p>
          <button onClick={() => navigate(-1)} className="btn btn-secondary" style={{ marginTop: '1rem' }}>
            Go Back
          </button>
        </div>
      </div>
    );
  }

  if (!event) return null;

  const isCapped = event.capacity !== null && event.capacity !== undefined && event.capacity > 0;
  const isFull = isCapped && event.registeredCount >= (event.capacity || 0);

  let deadlinePassed = false;
  if (event.registrationDeadline) {
    try {
      deadlinePassed = new Date(event.registrationDeadline).getTime() < Date.now();
    } catch {
      // ignore
    }
  }

  const formattedDate = event.eventDate
    ? new Date(event.eventDate + 'T00:00:00').toLocaleDateString(undefined, {
        weekday: 'long',
        month: 'long',
        day: 'numeric',
        year: 'numeric',
      })
    : 'Date TBD';

  return (
    <div className="container" style={{ padding: '2rem 1rem', maxWidth: '850px' }}>
      <button
        onClick={() => navigate(`/circles/${event.circleId}`)}
        className="btn btn-secondary"
        style={{ marginBottom: '1.5rem', display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}
      >
        ← Back to {event.circleName}
      </button>

      {error && (
        <div className="alert alert-danger" style={{ marginBottom: '1.5rem' }}>
          {error}
        </div>
      )}

      <div
        className="card glass-card"
        style={{
          padding: '2.5rem',
          borderRadius: '1.25rem',
          background: 'rgba(30, 41, 59, 0.45)',
          backdropFilter: 'blur(16px)',
          border: '1px solid rgba(255, 255, 255, 0.08)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <div style={{ display: 'flex', gap: '0.6rem', alignItems: 'center', marginBottom: '0.75rem' }}>
              <span className={`badge badge-${event.status.toLowerCase()}`}>
                {event.status}
              </span>
              {event.readingTopic && (
                <span
                  style={{
                    fontSize: '0.8rem',
                    padding: '0.2rem 0.7rem',
                    borderRadius: '9999px',
                    backgroundColor: 'rgba(99, 102, 241, 0.15)',
                    color: '#a5b4fc',
                    border: '1px solid rgba(99, 102, 241, 0.25)',
                  }}
                >
                  📖 {event.readingTopic}
                </span>
              )}
            </div>
            <h1 style={{ fontSize: '2rem', fontWeight: 800, margin: '0 0 0.5rem 0', color: '#f8fafc' }}>
              {event.title}
            </h1>
            <p style={{ margin: 0, fontSize: '0.95rem', color: '#94a3b8' }}>
              Part of <Link to={`/circles/${event.circleId}`} style={{ color: 'var(--accent-indigo, #6366f1)', fontWeight: 600 }}>{event.circleName}</Link>
            </p>
          </div>

          {event.isOrganizerOrAdmin && (
            <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
              <button
                onClick={() => setIsAttendeeModalOpen(true)}
                className="btn btn-secondary"
                style={{ fontSize: '0.85rem' }}
              >
                👥 Attendees ({event.registeredCount})
              </button>
              {event.status !== 'COMPLETED' && event.status !== 'CANCELLED' && (
                <>
                  <button
                    onClick={() => setIsEditModalOpen(true)}
                    className="btn btn-secondary"
                    style={{ fontSize: '0.85rem' }}
                  >
                    ✏️ Edit
                  </button>
                  {event.status === 'PUBLISHED' && (
                    <button
                      onClick={() => handleStatusChange('COMPLETED')}
                      disabled={actionLoading}
                      className="btn btn-secondary"
                      style={{ fontSize: '0.85rem', color: '#34d399' }}
                    >
                      ✓ Mark Completed
                    </button>
                  )}
                  <button
                    onClick={() => handleStatusChange('CANCELLED')}
                    disabled={actionLoading}
                    className="btn btn-danger"
                    style={{ fontSize: '0.85rem' }}
                  >
                    Cancel Event
                  </button>
                </>
              )}
            </div>
          )}
        </div>

        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '1.25rem',
            margin: '2rem 0',
            padding: '1.25rem',
            borderRadius: '0.75rem',
            backgroundColor: 'rgba(255, 255, 255, 0.03)',
            border: '1px solid rgba(255, 255, 255, 0.05)',
          }}
        >
          <div>
            <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 700 }}>
              Date & Time
            </div>
            <div style={{ fontSize: '0.95rem', fontWeight: 600, marginTop: '0.25rem' }}>
              📅 {formattedDate}
            </div>
            {event.eventTime && (
              <div style={{ fontSize: '0.85rem', color: '#94a3b8', marginTop: '0.15rem' }}>
                ⏰ {event.eventTime.slice(0, 5)}
              </div>
            )}
          </div>

          <div>
            <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 700 }}>
              Venue / Location
            </div>
            <div style={{ fontSize: '0.95rem', fontWeight: 600, marginTop: '0.25rem' }}>
              📍 {event.venue || 'To be determined'}
            </div>
          </div>

          <div>
            <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 700 }}>
              Registration
            </div>
            <div style={{ fontSize: '0.95rem', fontWeight: 600, marginTop: '0.25rem' }}>
              {event.isRegistered ? (
                <span style={{ color: '#34d399' }}>✓ You are attending</span>
              ) : isFull ? (
                <span style={{ color: '#f43f5e' }}>Event is Full</span>
              ) : deadlinePassed ? (
                <span style={{ color: '#ef4444' }}>RSVP Closed</span>
              ) : (
                <span style={{ color: '#a5b4fc' }}>Open for RSVP</span>
              )}
            </div>
          </div>
        </div>

        <CapacityBar
          registeredCount={event.registeredCount}
          capacity={event.capacity}
          registrationDeadline={event.registrationDeadline}
          status={event.status}
        />

        <div style={{ margin: '2rem 0' }}>
          <h3 style={{ fontSize: '1.15rem', fontWeight: 700, marginBottom: '0.75rem', color: '#f8fafc' }}>
            About this Gathering
          </h3>
          <p style={{ lineHeight: 1.7, color: '#cbd5e1', whiteSpace: 'pre-line' }}>
            {event.description || 'No detailed discussion agenda provided yet.'}
          </p>
        </div>

        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginTop: '2.5rem',
            paddingTop: '1.5rem',
            borderTop: '1px solid rgba(255, 255, 255, 0.08)',
            flexWrap: 'wrap',
            gap: '1rem',
          }}
        >
          <div>
            {event.isRegistered ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <span
                  style={{
                    backgroundColor: 'rgba(52, 211, 153, 0.15)',
                    color: '#34d399',
                    border: '1px solid rgba(52, 211, 153, 0.3)',
                    padding: '0.4rem 0.8rem',
                    borderRadius: '0.5rem',
                    fontWeight: 600,
                    fontSize: '0.9rem',
                  }}
                >
                  ✓ You're Registered!
                </span>
                {event.status === 'PUBLISHED' && !deadlinePassed && (
                  <button
                    onClick={handleCancelRegistration}
                    disabled={actionLoading}
                    className="btn btn-danger"
                    style={{ fontSize: '0.85rem' }}
                  >
                    Cancel RSVP
                  </button>
                )}
              </div>
            ) : event.status === 'PUBLISHED' ? (
              <button
                onClick={handleRegister}
                disabled={actionLoading || isFull || deadlinePassed}
                className="btn btn-primary"
                style={{ fontSize: '1rem', padding: '0.75rem 2rem' }}
              >
                {actionLoading
                  ? 'Securing your seat...'
                  : isFull
                  ? 'Event Full'
                  : deadlinePassed
                  ? 'Registration Closed'
                  : 'Register / RSVP Now'}
              </button>
            ) : (
              <span style={{ color: '#64748b', fontSize: '0.95rem' }}>
                {event.status === 'DRAFT'
                  ? 'This event is a draft and not open for registration.'
                  : `This event is ${event.status.toLowerCase()}.`}
              </span>
            )}
          </div>
        </div>
      </div>

      <EventModal
        isOpen={isEditModalOpen}
        onClose={() => setIsEditModalOpen(false)}
        onSubmit={handleUpdateEvent}
        eventToEdit={event}
        circleId={event.circleId}
      />

      <AttendeeModal
        isOpen={isAttendeeModalOpen}
        onClose={() => setIsAttendeeModalOpen(false)}
        eventId={event.id}
        eventTitle={event.title}
      />
    </div>
  );
};
