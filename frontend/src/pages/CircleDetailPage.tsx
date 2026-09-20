import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { JoinButton, MembershipState } from "../components/JoinButton";
import { apiClient, decodeRole, loadTokens } from "../lib/apiClient";

interface CircleDetail {
  id: string;
  name: string;
  city: string;
  description: string;
  organizerId: string;
  organizerName: string;
  status: "ACTIVE" | "INACTIVE";
  createdAt: string;
  memberCount: number;
}

interface MemberRow {
  membershipId: string;
  readerId: string;
  readerName: string;
  readerEmail?: string | null;
  status: string;
  requestedAt: string;
  decidedAt?: string | null;
}

interface MyMembership {
  circleId: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
}

export function CircleDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [circle, setCircle] = useState<CircleDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // User auth and membership state
  const tokens = loadTokens();
  const role = tokens ? decodeRole(tokens.accessToken) : null;
  const currentUserId = tokens ? extractUserIdFromJwt(tokens.accessToken) : null;
  const [myStatus, setMyStatus] = useState<MembershipState>(null);

  // Organizer management state
  const [activeTab, setActiveTab] = useState<"about" | "requests" | "members">("about");
  const [pendingRequests, setPendingRequests] = useState<MemberRow[]>([]);
  const [members, setMembers] = useState<MemberRow[]>([]);
  const [actionNotice, setActionNotice] = useState<string | null>(null);

  function extractUserIdFromJwt(token: string): string | null {
    try {
      const payload = JSON.parse(atob(token.split(".")[1]));
      return payload.sub ?? null;
    } catch {
      return null;
    }
  }

  function loadCircle() {
    if (!id) return;
    setLoading(true);
    apiClient
      .get<CircleDetail>(`/circles/${id}`)
      .then((res) => {
        setCircle(res.data);
        setError(null);
      })
      .catch(() => setError("Reading circle not found."))
      .finally(() => setLoading(false));

    if (tokens) {
      apiClient
        .get<MyMembership[]>("/users/me/memberships")
        .then((res) => {
          const found = res.data.find((m) => m.circleId === id);
          if (found) setMyStatus(found.status);
        })
        .catch(() => {});
    }
  }

  useEffect(() => {
    loadCircle();
  }, [id]);

  const isOrganizer = !!(circle && currentUserId && circle.organizerId === currentUserId);
  const isAdmin = role === "ADMIN";
  const canManage = isOrganizer || isAdmin;

  function loadPendingRequests() {
    if (!id || !canManage) return;
    apiClient
      .get<MemberRow[]>(`/circles/${id}/join-requests`)
      .then((res) => setPendingRequests(res.data))
      .catch(() => {});
  }

  function loadMembers() {
    if (!id) return;
    apiClient
      .get<MemberRow[]>(`/circles/${id}/members`)
      .then((res) => setMembers(res.data))
      .catch(() => {});
  }

  useEffect(() => {
    if (activeTab === "requests") loadPendingRequests();
    if (activeTab === "members") loadMembers();
  }, [activeTab, id, canManage]);

  async function handleDecision(membershipId: string, action: "APPROVE" | "REJECT") {
    try {
      await apiClient.patch(`/join-requests/${membershipId}`, { action });
      setActionNotice(`Request ${action === "APPROVE" ? "approved" : "rejected"}.`);
      setTimeout(() => setActionNotice(null), 3000);
      loadPendingRequests();
      loadCircle();
    } catch {
      setError("Failed to update join request.");
    }
  }

  if (loading) {
    return (
      <main className="rc-main-content">
        <div style={{ textAlign: "center", padding: "4rem 0" }}>
          <div className="rc-spinner" style={{ margin: "0 auto 1rem", width: "28px", height: "28px" }}></div>
          <p style={{ color: "var(--text-secondary)" }}>Loading circle details…</p>
        </div>
      </main>
    );
  }

  if (error || !circle) {
    return (
      <main className="rc-main-content">
        <div className="rc-alert rc-alert-error" role="alert">
          <span>⚠️</span>
          <span>{error ?? "Circle not found"}</span>
          <Link to="/circles" className="rc-btn rc-btn-secondary rc-btn-sm" style={{ marginLeft: "auto" }}>
            Back to Circles
          </Link>
        </div>
      </main>
    );
  }

  return (
    <main className="rc-main-content">
      <div style={{ marginBottom: "1.5rem" }}>
        <Link to="/circles" style={{ fontSize: "0.9rem" }}>
          ← Back to all circles
        </Link>
      </div>

      <section
        style={{
          background: "var(--bg-surface-card)",
          border: "1px solid var(--border-subtle)",
          borderRadius: "var(--radius-xl)",
          padding: "2rem",
          marginBottom: "2rem",
        }}
      >
        <div style={{ display: "flex", justifyContent: "space-between", flexWrap: "wrap", gap: "1rem", alignItems: "flex-start" }}>
          <div>
            <div style={{ display: "flex", gap: "0.5rem", alignItems: "center", marginBottom: "0.5rem" }}>
              <span className="rc-badge rc-badge-reader">📍 {circle.city}</span>
              <span className={`rc-badge ${circle.status === "ACTIVE" ? "rc-badge-active" : "rc-badge-inactive"}`}>
                {circle.status}
              </span>
            </div>
            <h1 className="rc-page-title font-serif">{circle.name}</h1>
            <p style={{ color: "var(--text-secondary)", fontSize: "0.95rem" }}>
              Organized by <strong style={{ color: "var(--text-primary)" }}>{circle.organizerName}</strong> • {circle.memberCount} {circle.memberCount === 1 ? "member" : "members"}
            </p>
          </div>

          <div>
            <JoinButton
              circleId={circle.id}
              initialStatus={myStatus}
              onStatusChange={(s) => setMyStatus(s)}
            />
          </div>
        </div>
      </section>

      {/* Tabs */}
      <div style={{ display: "flex", gap: "0.75rem", borderBottom: "1px solid var(--border-subtle)", marginBottom: "1.5rem" }}>
        <button
          type="button"
          className={`rc-btn rc-btn-sm ${activeTab === "about" ? "rc-btn-primary" : "rc-btn-secondary"}`}
          onClick={() => setActiveTab("about")}
        >
          About
        </button>
        <button
          type="button"
          className={`rc-btn rc-btn-sm ${activeTab === "members" ? "rc-btn-primary" : "rc-btn-secondary"}`}
          onClick={() => setActiveTab("members")}
        >
          Members ({circle.memberCount})
        </button>
        {canManage && (
          <button
            type="button"
            className={`rc-btn rc-btn-sm ${activeTab === "requests" ? "rc-btn-primary" : "rc-btn-secondary"}`}
            onClick={() => setActiveTab("requests")}
          >
            Pending Requests ({pendingRequests.length})
          </button>
        )}
      </div>

      {actionNotice && (
        <div className="rc-alert rc-alert-success" style={{ marginBottom: "1.5rem" }}>
          <span>✓</span>
          <span>{actionNotice}</span>
        </div>
      )}

      {/* Tab Content */}
      {activeTab === "about" && (
        <article
          style={{
            background: "var(--bg-surface-card)",
            border: "1px solid var(--border-subtle)",
            borderRadius: "var(--radius-lg)",
            padding: "2rem",
            lineHeight: "1.7",
          }}
        >
          <h2 style={{ fontSize: "1.25rem", marginBottom: "1rem" }}>About this Circle</h2>
          <p style={{ color: "var(--text-primary)", whiteSpace: "pre-wrap" }}>{circle.description}</p>
        </article>
      )}

      {activeTab === "members" && (
        <section className="rc-table-container">
          <table className="rc-table">
            <thead>
              <tr>
                <th>Member</th>
                {canManage && <th>Email</th>}
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {members.length === 0 ? (
                <tr>
                  <td colSpan={canManage ? 3 : 2} style={{ textAlign: "center", padding: "2rem", color: "var(--text-muted)" }}>
                    No members listed yet.
                  </td>
                </tr>
              ) : (
                members.map((m) => (
                  <tr key={m.membershipId}>
                    <td>
                      <div className="rc-user-cell">
                        <div className="rc-avatar" aria-hidden="true">
                          {m.readerName.charAt(0).toUpperCase()}
                        </div>
                        <span className="rc-user-name">{m.readerName}</span>
                      </div>
                    </td>
                    {canManage && <td style={{ color: "var(--text-secondary)" }}>{m.readerEmail ?? "—"}</td>}
                    <td>
                      <span className="rc-badge rc-badge-active">Approved</span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </section>
      )}

      {activeTab === "requests" && canManage && (
        <section className="rc-table-container">
          <table className="rc-table">
            <thead>
              <tr>
                <th>Applicant</th>
                <th>Email</th>
                <th>Requested Date</th>
                <th>Decision Actions</th>
              </tr>
            </thead>
            <tbody>
              {pendingRequests.length === 0 ? (
                <tr>
                  <td colSpan={4} style={{ textAlign: "center", padding: "2.5rem", color: "var(--text-muted)" }}>
                    No pending membership applications.
                  </td>
                </tr>
              ) : (
                pendingRequests.map((req) => (
                  <tr key={req.membershipId}>
                    <td>
                      <div className="rc-user-cell">
                        <div className="rc-avatar" aria-hidden="true">
                          {req.readerName.charAt(0).toUpperCase()}
                        </div>
                        <span className="rc-user-name">{req.readerName}</span>
                      </div>
                    </td>
                    <td style={{ color: "var(--text-secondary)" }}>{req.readerEmail ?? "—"}</td>
                    <td style={{ color: "var(--text-muted)", fontSize: "0.85rem" }}>
                      {new Date(req.requestedAt).toLocaleDateString()}
                    </td>
                    <td>
                      <div style={{ display: "flex", gap: "0.5rem" }}>
                        <button
                          type="button"
                          className="rc-btn rc-btn-primary rc-btn-sm"
                          onClick={() => handleDecision(req.membershipId, "APPROVE")}
                        >
                          ✓ Approve
                        </button>
                        <button
                          type="button"
                          className="rc-btn rc-btn-danger rc-btn-sm"
                          onClick={() => handleDecision(req.membershipId, "REJECT")}
                        >
                          ✕ Reject
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </section>
      )}
    </main>
  );
}
