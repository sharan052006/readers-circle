import React from 'react';

interface CapacityBarProps {
  registeredCount: number;
  capacity: number | null;
  registrationDeadline?: string | null;
  status?: string;
  compact?: boolean;
}

export const CapacityBar: React.FC<CapacityBarProps> = ({
  registeredCount,
  capacity,
  registrationDeadline,
  compact = false,
}) => {
  const isCapped = capacity !== null && capacity !== undefined && capacity > 0;
  const percentage = isCapped ? Math.min(100, Math.round((registeredCount / capacity) * 100)) : 0;
  const isFull = isCapped && registeredCount >= capacity;

  let deadlinePassed = false;
  let formattedDeadline = '';
  if (registrationDeadline) {
    try {
      const d = new Date(registrationDeadline);
      deadlinePassed = d.getTime() < Date.now();
      formattedDeadline = d.toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      // ignore
    }
  }

  const barColor = isFull
    ? 'var(--accent-red, #f43f5e)'
    : percentage >= 80
    ? 'var(--accent-amber, #f59e0b)'
    : 'var(--accent-indigo, #6366f1)';

  return (
    <div className="capacity-bar-wrapper" style={{ margin: compact ? '0.25rem 0' : '0.75rem 0' }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          fontSize: '0.82rem',
          color: 'var(--text-secondary, #94a3b8)',
          marginBottom: '0.35rem',
        }}
      >
        <span>
          <strong style={{ color: isFull ? '#f43f5e' : 'var(--text-primary, #f8fafc)' }}>
            {registeredCount}
          </strong>{' '}
          {isCapped ? `/ ${capacity} seats filled` : 'attendees registered'}
        </span>
        {isFull ? (
          <span
            style={{
              padding: '0.15rem 0.5rem',
              borderRadius: '9999px',
              backgroundColor: 'rgba(244, 63, 94, 0.15)',
              color: '#f43f5e',
              fontWeight: 600,
              fontSize: '0.75rem',
            }}
          >
            Full
          </span>
        ) : deadlinePassed ? (
          <span
            style={{
              padding: '0.15rem 0.5rem',
              borderRadius: '9999px',
              backgroundColor: 'rgba(239, 68, 68, 0.15)',
              color: '#ef4444',
              fontWeight: 600,
              fontSize: '0.75rem',
            }}
          >
            RSVP Closed
          </span>
        ) : isCapped ? (
          <span>{capacity - registeredCount} spots left</span>
        ) : null}
      </div>

      {isCapped && (
        <div
          style={{
            height: '6px',
            backgroundColor: 'rgba(255, 255, 255, 0.08)',
            borderRadius: '9999px',
            overflow: 'hidden',
          }}
        >
          <div
            role="progressbar"
            aria-valuenow={registeredCount}
            aria-valuemin={0}
            aria-valuemax={capacity}
            style={{
              width: `${percentage}%`,
              height: '100%',
              backgroundColor: barColor,
              borderRadius: '9999px',
              transition: 'width 0.4s ease',
            }}
          />
        </div>
      )}

      {formattedDeadline && !compact && (
        <div
          style={{
            marginTop: '0.35rem',
            fontSize: '0.75rem',
            color: deadlinePassed ? '#f87171' : 'var(--text-muted, #64748b)',
          }}
        >
          {deadlinePassed ? '⚠️ RSVP closed on ' : '⏳ RSVP closes '}
          {formattedDeadline}
        </div>
      )}
    </div>
  );
};
