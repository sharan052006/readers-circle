import React, { useEffect, useState } from 'react';
import { api } from '../lib/apiClient';

interface Attendee {
  readerId: string;
  fullName: string;
  email: string;
  registeredAt: string;
}

interface AttendeeModalProps {
  isOpen: boolean;
  onClose: () => void;
  eventId: string;
  eventTitle: string;
}

export const AttendeeModal: React.FC<AttendeeModalProps> = ({
  isOpen,
  onClose,
  eventId,
  eventTitle,
}) => {
  const [attendees, setAttendees] = useState<Attendee[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!isOpen || !eventId) return;

    const fetchAttendees = async () => {
      try {
        setLoading(true);
        setError('');
        const res = await api.get(`/events/${eventId}/registrations`);
        setAttendees(res.data);
      } catch (err: any) {
        setError(err?.response?.data?.message || 'Failed to load attendee roster');
      } finally {
        setLoading(false);
      }
    };

    fetchAttendees();
  }, [isOpen, eventId]);

  if (!isOpen) return null;

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
          maxWidth: '560px',
          maxHeight: '80vh',
          display: 'flex',
          flexDirection: 'column',
          backgroundColor: '#0f172a',
          border: '1px solid rgba(255, 255, 255, 0.12)',
          borderRadius: '1.25rem',
          padding: '1.75rem',
          color: '#f8fafc',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.5)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <div>
            <h2 style={{ fontSize: '1.3rem', fontWeight: 700, margin: 0 }}>Attendee Roster</h2>
            <p style={{ margin: '0.2rem 0 0 0', fontSize: '0.85rem', color: '#94a3b8' }}>
              {eventTitle} ({attendees.length} registered)
            </p>
          </div>
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
              marginBottom: '1rem',
              fontSize: '0.875rem',
            }}
          >
            {error}
          </div>
        )}

        <div style={{ flex: 1, overflowY: 'auto', margin: '0.5rem 0' }}>
          {loading ? (
            <div style={{ textAlign: 'center', padding: '2rem', color: '#94a3b8' }}>
              Loading attendees...
            </div>
          ) : attendees.length === 0 ? (
            <div
              style={{
                textAlign: 'center',
                padding: '2.5rem 1rem',
                color: '#94a3b8',
                backgroundColor: 'rgba(255, 255, 255, 0.02)',
                borderRadius: '0.75rem',
                border: '1px dashed rgba(255, 255, 255, 0.1)',
              }}
            >
              No readers have registered for this event yet.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
              {attendees.map((a, idx) => (
                <div
                  key={a.readerId}
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    padding: '0.75rem 1rem',
                    backgroundColor: 'rgba(255, 255, 255, 0.04)',
                    borderRadius: '0.75rem',
                    border: '1px solid rgba(255, 255, 255, 0.05)',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                    <div
                      style={{
                        width: '32px',
                        height: '32px',
                        borderRadius: '50%',
                        backgroundColor: 'var(--accent-indigo, #6366f1)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontWeight: 600,
                        fontSize: '0.85rem',
                      }}
                    >
                      {idx + 1}
                    </div>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: '0.95rem' }}>{a.fullName}</div>
                      {a.email && (
                        <div style={{ fontSize: '0.8rem', color: '#94a3b8' }}>{a.email}</div>
                      )}
                    </div>
                  </div>
                  <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
                    {new Date(a.registeredAt).toLocaleDateString(undefined, {
                      month: 'short',
                      day: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '1rem' }}>
          <button onClick={onClose} className="btn btn-secondary">
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
