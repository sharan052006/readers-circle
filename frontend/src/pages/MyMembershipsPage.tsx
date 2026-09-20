import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { apiClient } from "../lib/apiClient";

interface MembershipItem {
  id: string;
  circleId: string;
  circleName: string;
  circleCity: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  requestedAt: string;
  decidedAt?: string | null;
}

export function MyMembershipsPage() {
  const [memberships, setMemberships] = useState<MembershipItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  function load() {
    setLoading(true);
    setError(null);
    apiClient
      .get<MembershipItem[]>("/users/me/memberships")
      .then((res) => setMemberships(res.data))
      .catch(() => setError("Failed to load your memberships."))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    load();
  }, []);

  return (
    <main className="rc-main-content">
      <div className="rc-page-header">
        <div>
          <h1 className="rc-page-title font-serif">My Reading Circles</h1>
          <p className="rc-page-desc">
            Track your circle memberships, pending applications, and book club statuses.
          </p>
        </div>
        <Link to="/circles" className="rc-btn rc-btn-primary rc-btn-sm">
          + Explore More Circles
        </Link>
      </div>

      {error && (
        <div className="rc-alert rc-alert-error" role="alert" style={{ marginBottom: "1.5rem" }}>
          <span>⚠️</span>
          <span>{error}</span>
        </div>
      )}

      {loading ? (
        <div style={{ textAlign: "center", padding: "4rem 0" }}>
          <div className="rc-spinner" style={{ margin: "0 auto 1rem", width: "28px", height: "28px" }}></div>
          <p style={{ color: "var(--text-secondary)" }}>Loading memberships…</p>
        </div>
      ) : memberships.length === 0 ? (
        <div
          style={{
            textAlign: "center",
            padding: "4rem 1rem",
            background: "var(--bg-surface-card)",
            borderRadius: "var(--radius-lg)",
            border: "1px solid var(--border-subtle)",
          }}
        >
          <div style={{ fontSize: "2.5rem", marginBottom: "0.75rem" }}>📖</div>
          <h3 className="font-serif" style={{ marginBottom: "0.5rem" }}>No circle memberships yet</h3>
          <p style={{ color: "var(--text-secondary)", maxWidth: "450px", margin: "0 auto 1.5rem" }}>
            You haven't requested to join any reading circles yet. Explore local circles in your city to get started!
          </p>
          <Link to="/circles" className="rc-btn rc-btn-primary">
            Explore Reading Circles
          </Link>
        </div>
      ) : (
        <div className="rc-table-container">
          <table className="rc-table">
            <thead>
              <tr>
                <th>Circle</th>
                <th>City</th>
                <th>Status</th>
                <th>Requested On</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {memberships.map((m) => (
                <tr key={m.id}>
                  <td>
                    <span className="rc-user-name">{m.circleName}</span>
                  </td>
                  <td>
                    <span className="rc-badge rc-badge-reader">📍 {m.circleCity}</span>
                  </td>
                  <td>
                    {m.status === "APPROVED" && (
                      <span className="rc-badge rc-badge-active">✓ Approved</span>
                    )}
                    {m.status === "PENDING" && (
                      <span className="rc-badge rc-badge-organizer">⏳ Awaiting Approval</span>
                    )}
                    {m.status === "REJECTED" && (
                      <span className="rc-badge rc-badge-inactive">✕ Rejected</span>
                    )}
                  </td>
                  <td style={{ color: "var(--text-muted)", fontSize: "0.85rem" }}>
                    {new Date(m.requestedAt).toLocaleDateString()}
                  </td>
                  <td>
                    <Link to={`/circles/${m.circleId}`} className="rc-btn rc-btn-secondary rc-btn-sm">
                      View Circle →
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  );
}
