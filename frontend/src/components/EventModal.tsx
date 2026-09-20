import React, { useState, useEffect } from 'react';
import { EventSummary } from './EventCard';

interface EventModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (formData: any) => Promise<void>;
  eventToEdit?: EventSummary | null;
  circleId: string;
}

export const EventModal: React.FC<EventModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  eventToEdit,
}) => {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [readingTopic, setReadingTopic] = useState('');
  const [eventDate, setEventDate] = useState('');
  const [eventTime, setEventTime] = useState('');
  const [venue, setVenue] = useState('');
  const [capacity, setCapacity] = useState<number | ''>('');
  const [registrationDeadline, setRegistrationDeadline] = useState('');
  const [coverImageUrl, setCoverImageUrl] = useState('');
  const [status, setStatus] = useState<'DRAFT' | 'PUBLISHED' | 'COMPLETED' | 'CANCELLED'>('DRAFT');
  const [publishNow, setPublishNow] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (eventToEdit) {
      setTitle(eventToEdit.title || '');
      setReadingTopic(eventToEdit.readingTopic || '');
      setEventDate(eventToEdit.eventDate || '');
      setEventTime(eventToEdit.eventTime ? eventToEdit.eventTime.slice(0, 5) : '');
      setVenue(eventToEdit.venue || '');
      setCapacity(eventToEdit.capacity !== null && eventToEdit.capacity !== undefined ? eventToEdit.capacity : '');
      if (eventToEdit.registrationDeadline) {
        try {
          const d = new Date(eventToEdit.registrationDeadline);
          setRegistrationDeadline(d.toISOString().slice(0, 16));
        } catch {
          setRegistrationDeadline('');
        }
      } else {
        setRegistrationDeadline('');
      }
      setCoverImageUrl(eventToEdit.coverImageUrl || '');
      setStatus(eventToEdit.status);
    } else {
      // Default to next week
      const nextWeek = new Date();
      nextWeek.setDate(nextWeek.getDate() + 7);
      const defaultDate = nextWeek.toISOString().slice(0, 10);
      setEventDate(defaultDate);
      setEventTime('18:00');

      const nextWeekDeadline = new Date(nextWeek);
      nextWeekDeadline.setDate(nextWeekDeadline.getDate() - 1);
      setRegistrationDeadline(nextWeekDeadline.toISOString().slice(0, 16));

      setTitle('');
      setDescription('');
      setReadingTopic('');
      setVenue('');
      setCapacity(15);
      setCoverImageUrl('');
      setStatus('DRAFT');
      setPublishNow(true);
    }
    setError('');
  }, [eventToEdit, isOpen]);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!title.trim()) {
      setError('Event title is required');
      return;
    }

    try {
      setLoading(true);
      const payload: any = {
        title: title.trim(),
        description: description.trim() || undefined,
        readingTopic: readingTopic.trim() || undefined,
        eventDate: eventDate || undefined,
        eventTime: eventTime ? (eventTime.length === 5 ? `${eventTime}:00` : eventTime) : undefined,
        venue: venue.trim() || undefined,
        capacity: capacity === '' ? null : Number(capacity),
        registrationDeadline: registrationDeadline
          ? new Date(registrationDeadline).toISOString()
          : undefined,
        coverImageUrl: coverImageUrl.trim() || undefined,
      };

      if (!eventToEdit) {
        payload.publishNow = publishNow;
      } else {
        payload.status = status;
      }

      await onSubmit(payload);
      onClose();
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Failed to save event');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.75)',
        backdropFilter: 'blur(6px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1000,
        padding: '1rem',
      }}
    >
      <div
        className="glass-card"
        style={{
          width: '100%',
          maxWidth: '620px',
          maxHeight: '90vh',
          overflowY: 'auto',
          backgroundColor: '#0f172a',
          border: '1px solid rgba(255, 255, 255, 0.12)',
          borderRadius: '1.25rem',
          padding: '2rem',
          color: '#f8fafc',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.5)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
          <h2 style={{ fontSize: '1.4rem', fontWeight: 700, margin: 0 }}>
            {eventToEdit ? 'Edit Event' : 'Schedule New Event'}
          </h2>
          <button
            type="button"
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              color: '#94a3b8',
              fontSize: '1.5rem',
              cursor: 'pointer',
              lineHeight: 1,
            }}
          >
            &times;
          </button>
        </div>

        {error && (
          <div
            style={{
              backgroundColor: 'rgba(239, 68, 68, 0.15)',
              border: '1px solid rgba(239, 68, 68, 0.3)',
              color: '#f87171',
              padding: '0.75rem 1rem',
              borderRadius: '0.5rem',
              marginBottom: '1.25rem',
              fontSize: '0.875rem',
            }}
          >
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div>
            <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.35rem' }}>
              Event Title *
            </label>
            <input
              type="text"
              required
              className="form-input"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Discussing 'Pride and Prejudice'"
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div>
              <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.35rem' }}>
                Reading Topic / Book
              </label>
              <input
                type="text"
                className="form-input"
                value={readingTopic}
                onChange={(e) => setReadingTopic(e.target.value)}
                placeholder="e.g. Classic Romance / Jane Austen"
              />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.35rem' }}>
                Capacity (Seats) *
              </label>
              <input
                type="number"
                min="1"
                required
                className="form-input"
                value={capacity}
                onChange={(e) => setCapacity(e.target.value === '' ? '' : Number(e.target.value))}
                placeholder="e.g. 15"
              />
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div>
              <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.35rem' }}>
                Event Date *
              </label>
              <input
                type="date"
                required
                className="form-input"
                value={eventDate}
                onChange={(e) => setEventDate(e.target.value)}
              />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.35rem' }}>
                Event Time *
              </label>
              <input
                type="time"
                required
                className="form-input"
                value={eventTime}
                onChange={(e) => setEventTime(e.target.value)}
              />
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div>
              <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.35rem' }}>
                Venue / Location *
              </label>
              <input
                type="text"
                required
                className="form-input"
                value={venue}
                onChange={(e) => setVenue(e.target.value)}
                placeholder="e.g. City Library Room B or Google Meet"
              />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.35rem' }}>
                RSVP Deadline *
              </label>
              <input
                type="datetime-local"
                required
                className="form-input"
                value={registrationDeadline}
                onChange={(e) => setRegistrationDeadline(e.target.value)}
              />
            </div>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.35rem' }}>
              Description & Discussion Guide
            </label>
            <textarea
              rows={3}
              className="form-input"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Provide agenda, chapter discussion points, or preparation details..."
            />
          </div>

          {eventToEdit ? (
            <div>
              <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.35rem' }}>
                Status
              </label>
              <select
                className="form-input"
                value={status}
                onChange={(e) => setStatus(e.target.value as any)}
              >
                <option value="DRAFT">DRAFT</option>
                <option value="PUBLISHED">PUBLISHED</option>
                <option value="COMPLETED">COMPLETED</option>
                <option value="CANCELLED">CANCELLED</option>
              </select>
            </div>
          ) : (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginTop: '0.25rem' }}>
              <input
                type="checkbox"
                id="publishNow"
                checked={publishNow}
                onChange={(e) => setPublishNow(e.target.checked)}
                style={{ width: '1.1rem', height: '1.1rem', accentColor: 'var(--accent-indigo, #6366f1)' }}
              />
              <label htmlFor="publishNow" style={{ fontSize: '0.9rem', cursor: 'pointer' }}>
                Publish immediately (members can RSVP right away)
              </label>
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.25rem' }}>
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              className="btn btn-secondary"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading}
              className="btn btn-primary"
            >
              {loading ? 'Saving...' : eventToEdit ? 'Save Changes' : publishNow ? 'Publish Event' : 'Save Draft'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
