import React from 'react';
import { CapacityBar } from './CapacityBar';

export interface EventSummary {
  id: string;
  circleId: string;
  title: string;
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
  createdAt: string;
}

interface EventCardProps {
  event: EventSummary;
  onRegister?: (eventId: string) => void;
  onCancel?: (eventId: string) => void;
  onViewDetails?: (eventId: string) => void;
  onEdit?: (event: EventSummary) => void;
  onViewAttendees?: (eventId: string) => void;
  isOrganizerOrAdmin?: boolean;
  actionLoading?: boolean;
}

export const EventCard: React.FC<EventCardProps> = ({
  event,
  onRegister,
  onCancel,
  onViewDetails,
  onEdit,
  onViewAttendees,
  isOrganizerOrAdmin,
  actionLoading,
}) => {
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

  const getStatusBadge = () => {
    switch (event.status) {
      case 'DRAFT':
        return <span className="badge badge-draft">Draft</span>;
      case 'PUBLISHED':
        return <span className="badge badge-published">Published</span>;
      case 'COMPLETED':
        return <span className="badge badge-completed">Completed</span>;
      case 'CANCELLED':
        return <span className="badge badge-cancelled">Cancelled</span>;
      default:
        return null;
    }
  };

  const formattedDate = event.eventDate
    ? new Date(event.eventDate + 'T00:00:00').toLocaleDateString(undefined, {
        weekday: 'short',
        month: 'short',
        day: 'numeric',
        year: 'numeric',
      })
    : 'Date TBD';

  return (
    <div
      className="card glass-card"
      style={{
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        padding: '1.5rem',
        borderRadius: '1rem',
        border: '1px solid rgba(255, 255, 255, 0.08)',
        background: 'rgba(30, 41, 59, 0.45)',
        backdropFilter: 'blur(12px)',
        position: 'relative',
        overflow: 'hidden',
        transition: 'transform 0.2s ease, border-color 0.2s ease',
      }}
    >
      <div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '0.75rem' }}>
          <div>
            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginBottom: '0.5rem' }}>
              {getStatusBadge()}
              {event.readingTopic && (
                <span
                  style={{
                    fontSize: '0.75rem',
                    padding: '0.2rem 0.6rem',
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
            <h3
              onClick={() => onViewDetails?.(event.id)}
              style={{
                fontSize: '1.25rem',
                fontWeight: 700,
                color: 'var(--text-primary, #f8fafc)',
                cursor: onViewDetails ? 'pointer' : 'default',
                margin: '0.25rem 0 0.5rem 0',
              }}
            >
              {event.title}
            </h3>
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem', margin: '0.75rem 0', fontSize: '0.875rem', color: 'var(--text-secondary, #94a3b8)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <span>📅</span>
            <span>{formattedDate} {event.eventTime ? `at ${event.eventTime.slice(0, 5)}` : ''}</span>
          </div>
          {event.venue && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span>📍</span>
              <span>{event.venue}</span>
            </div>
          )}
        </div>

        <CapacityBar
          registeredCount={event.registeredCount}
          capacity={event.capacity}
          registrationDeadline={event.registrationDeadline}
          status={event.status}
        />
      </div>

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: '0.5rem',
          marginTop: '1.25rem',
          paddingTop: '1rem',
          borderTop: '1px solid rgba(255, 255, 255, 0.06)',
          flexWrap: 'wrap',
        }}
      >
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          {onViewDetails && (
            <button
              onClick={() => onViewDetails(event.id)}
              className="btn btn-secondary"
              style={{ fontSize: '0.85rem', padding: '0.4rem 0.8rem' }}
            >
              Details
            </button>
          )}

          {isOrganizerOrAdmin && onViewAttendees && (
            <button
              onClick={() => onViewAttendees(event.id)}
              className="btn btn-secondary"
              style={{ fontSize: '0.85rem', padding: '0.4rem 0.8rem' }}
            >
              👥 Attendees ({event.registeredCount})
            </button>
          )}

          {isOrganizerOrAdmin && onEdit && (
            <button
              onClick={() => onEdit(event)}
              className="btn btn-secondary"
              style={{ fontSize: '0.85rem', padding: '0.4rem 0.8rem' }}
            >
              ✏️ Edit
            </button>
          )}
        </div>

        <div>
          {event.isRegistered ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '0.3rem',
                  fontSize: '0.85rem',
                  color: '#34d399',
                  fontWeight: 600,
                }}
              >
                ✓ Registered
              </span>
              {onCancel && event.status === 'PUBLISHED' && !deadlinePassed && (
                <button
                  onClick={() => onCancel(event.id)}
                  disabled={actionLoading}
                  className="btn btn-danger"
                  style={{ fontSize: '0.8rem', padding: '0.35rem 0.75rem' }}
                >
                  Cancel RSVP
                </button>
              )}
            </div>
          ) : event.status === 'PUBLISHED' ? (
            <button
              onClick={() => onRegister?.(event.id)}
              disabled={actionLoading || isFull || deadlinePassed}
              className="btn btn-primary"
              style={{ fontSize: '0.85rem', padding: '0.45rem 1rem' }}
            >
              {isFull ? 'Event Full' : deadlinePassed ? 'RSVP Closed' : 'Register / RSVP'}
            </button>
          ) : (
            <span style={{ fontSize: '0.85rem', color: 'var(--text-muted, #64748b)' }}>
              {event.status === 'DRAFT' ? 'Not Published' : 'Event Concluded'}
            </span>
          )}
        </div>
      </div>
    </div>
  );
};
