import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../lib/apiClient';

interface MyRegistration {
  id: string;
  eventId: string;
  eventTitle: string;
  circleId: string;
  readerId: string;
  status: 'REGISTERED' | 'CANCELLED';
  registeredAt: string;
  cancelledAt?: string;
}

export const MyEventsPage: React.FC = () => {
  const [registrations, setRegistrations] = useState<MyRegistration[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [cancellingId, setCancellingId] = useState<string | null>(null);
  const navigate = useNavigate();

  const fetchRegistrations = async () => {
    try {
      setLoading(true);
      setError('');
      const res = await api.get('/users/me/registrations');
      setRegistrations(res.data);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to load your event registrations');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRegistrations();
  }, []);

  const handleCancel = async (eventId: string) => {
    try {
      setCancellingId(eventId);
      setError('');
      await api.delete(`/events/${eventId}/register`);
      await fetchRegistrations();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to cancel RSVP');
    } finally {
      setCancellingId(null);
    }
  };

  return (
    <div className="container" style={{ padding: '2.5rem 1rem', maxWidth: '850px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.75rem', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 style={{ fontSize: '2rem', fontWeight: 800, margin: 0, color: '#f8fafc' }}>
            My Event Registrations
          </h1>
          <p style={{ margin: '0.35rem 0 0 0', color: '#94a3b8' }}>
            Keep track of all book club gatherings you've signed up to attend.
          </p>
        </div>
        <Link to="/circles" className="btn btn-secondary">
          Explore Circles & Events
        </Link>
      </div>

      {error && (
        <div className="alert alert-danger" style={{ marginBottom: '1.5rem' }}>
          {error}
        </div>
      )}

      {loading ? (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#94a3b8' }}>
          Loading registrations...
        </div>
      ) : registrations.length === 0 ? (
        <div
          className="glass-card"
          style={{
            textAlign: 'center',
            padding: '3.5rem 1.5rem',
            borderRadius: '1rem',
            background: 'rgba(30, 41, 59, 0.3)',
            border: '1px dashed rgba(255, 255, 255, 0.1)',
          }}
        >
          <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>🎟️</div>
          <h3 style={{ fontSize: '1.25rem', fontWeight: 700, margin: '0 0 0.5rem 0' }}>
            No event RSVPs yet
          </h3>
          <p style={{ color: '#94a3b8', maxWidth: '450px', margin: '0 auto 1.5rem auto' }}>
            Join a reading circle and register for upcoming book discussions, author talks, and group readings.
          </p>
          <Link to="/circles" className="btn btn-primary">
            Find an Event
          </Link>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {registrations.map((reg) => (
            <div
              key={reg.id}
              className="glass-card"
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                padding: '1.5rem',
                borderRadius: '1rem',
                background: 'rgba(30, 41, 59, 0.45)',
                border: '1px solid rgba(255, 255, 255, 0.08)',
                flexWrap: 'wrap',
                gap: '1rem',
              }}
            >
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '0.35rem' }}>
                  <span
                    style={{
                      padding: '0.15rem 0.6rem',
                      borderRadius: '9999px',
                      fontSize: '0.75rem',
                      fontWeight: 600,
                      backgroundColor:
                        reg.status === 'REGISTERED' ? 'rgba(52, 211, 153, 0.15)' : 'rgba(239, 68, 68, 0.15)',
                      color: reg.status === 'REGISTERED' ? '#34d399' : '#f87171',
                    }}
                  >
                    {reg.status}
                  </span>
                  <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                    Registered on{' '}
                    {new Date(reg.registeredAt).toLocaleDateString(undefined, {
                      month: 'short',
                      day: 'numeric',
                      year: 'numeric',
                    })}
                  </span>
                </div>
                <h3
                  onClick={() => navigate(`/events/${reg.eventId}`)}
                  style={{
                    fontSize: '1.2rem',
                    fontWeight: 700,
                    margin: '0.2rem 0',
                    color: '#f8fafc',
                    cursor: 'pointer',
                  }}
                >
                  {reg.eventTitle}
                </h3>
              </div>

              <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
                <Link
                  to={`/events/${reg.eventId}`}
                  className="btn btn-secondary"
                  style={{ fontSize: '0.85rem' }}
                >
                  View Event
                </Link>
                {reg.status === 'REGISTERED' && (
                  <button
                    onClick={() => handleCancel(reg.eventId)}
                    disabled={cancellingId === reg.eventId}
                    className="btn btn-danger"
                    style={{ fontSize: '0.85rem' }}
                  >
                    {cancellingId === reg.eventId ? 'Cancelling...' : 'Cancel RSVP'}
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
