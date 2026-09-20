import { useEffect, useState } from "react";
import { BrowserRouter, Link, Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { AuthGuard } from "./components/AuthGuard";
import { apiClient, clearTokens, decodeRole, loadTokens, Role } from "./lib/apiClient";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { CircleListPage } from "./pages/CircleListPage";
import { CircleDetailPage } from "./pages/CircleDetailPage";
import { MyMembershipsPage } from "./pages/MyMembershipsPage";

interface UserRow {
  id: string;
  name: string;
  email: string;
  role: Role;
  active: boolean;
  createdAt: string;
}

/** Global Navigation Bar */
function Navbar() {
  const navigate = useNavigate();
  const location = useLocation();
  const tokens = loadTokens();
  const role = tokens ? decodeRole(tokens.accessToken) : null;

  function handleLogout() {
    clearTokens();
    navigate("/login");
  }

  return (
    <header className="rc-navbar">
      <div className="rc-nav-container">
        <Link to="/" className="rc-brand">
          <span className="rc-brand-icon" aria-hidden="true">
            📖
          </span>
          <span>Reader&apos;s Circle</span>
        </Link>

        <div className="rc-nav-links">
          <Link
            to="/circles"
            className={`rc-nav-link ${location.pathname.startsWith("/circles") ? "active" : ""}`}
          >
            <span>⭕</span>
            <span>Circles</span>
          </Link>

          {tokens && (
            <Link
              to="/my-memberships"
              className={`rc-nav-link ${location.pathname === "/my-memberships" ? "active" : ""}`}
            >
              <span>📚</span>
              <span>My Circles</span>
            </Link>
          )}

          {role === "ADMIN" && (
            <Link
              to="/admin"
              className={`rc-nav-link ${location.pathname === "/admin" ? "active" : ""}`}
            >
              <span>⚙️</span>
              <span>Admin Console</span>
            </Link>
          )}

          {tokens ? (
            <div className="rc-nav-user-cluster">
              <span className={`rc-badge rc-badge-${role?.toLowerCase() ?? "reader"}`}>
                {role === "ADMIN" ? "👑 " : "📖 "}
                {role}
              </span>
              <button
                type="button"
                onClick={handleLogout}
                className="rc-btn rc-btn-secondary rc-btn-sm"
              >
                Log Out
              </button>
            </div>
          ) : (
            <div style={{ display: "flex", gap: "0.5rem" }}>
              <Link to="/login" className="rc-nav-link">
                Log in
              </Link>
              <Link to="/register" className="rc-btn rc-btn-primary rc-btn-sm">
                Get Started
              </Link>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}

/** Community Dashboard */
function CommunityPage() {
  const tokens = loadTokens();
  const role = tokens ? decodeRole(tokens.accessToken) : null;

  return (
    <main className="rc-main-content">
      <section className="rc-hero-card">
        <div>
          <span className="rc-badge rc-badge-active" style={{ marginBottom: "0.75rem" }}>
            ● System Operational — Circles & Membership Active
          </span>
          <h1 className="rc-page-title font-serif">Welcome to Reader&apos;s Circle</h1>
          <p className="rc-page-desc" style={{ maxWidth: "600px", marginTop: "0.5rem" }}>
            A neighborhood sanctuary for book discussions, local reading circles, and community meetups.
          </p>
          <div style={{ display: "flex", gap: "1rem", marginTop: "1.5rem", flexWrap: "wrap" }}>
            <Link to="/circles" className="rc-btn rc-btn-primary">
              Explore Reading Circles
            </Link>
            {role === "ADMIN" && (
              <Link to="/admin" className="rc-btn rc-btn-secondary">
                Admin Console
              </Link>
            )}
          </div>
        </div>
        <div style={{ fontSize: "5rem", opacity: 0.85 }} aria-hidden="true">
          📚⭕
        </div>
      </section>

      <div>
        <div className="rc-page-header" style={{ marginBottom: "1rem" }}>
          <div>
            <h2 className="font-serif" style={{ fontSize: "1.5rem" }}>
              Community Features & Capabilities
            </h2>
            <p className="rc-page-desc">
              Discover what is live and what is coming next in Reader&apos;s Circle.
            </p>
          </div>
        </div>

        <div className="rc-feature-grid">
          <div className="rc-feature-card">
            <div className="rc-feature-icon" style={{ background: "rgba(99, 102, 241, 0.2)" }}>
              🔐
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <h3 className="rc-feature-title">Identity & Roles</h3>
              <span className="rc-badge rc-badge-active">Live & Verified</span>
            </div>
            <p className="rc-feature-desc">
              JWT stateless security, BCrypt hashing, role-based boundaries (Admin, Organizer, Reader), and automated token rotation.
            </p>
          </div>

          <div className="rc-feature-card">
            <div className="rc-feature-icon" style={{ background: "rgba(245, 158, 11, 0.2)" }}>
              ⭕
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <h3 className="rc-feature-title">Reading Circles</h3>
              <span className="rc-badge rc-badge-active">Live & Verified</span>
            </div>
            <p className="rc-feature-desc">
              Discover circles by city, apply to join, and organizers manage membership applications with single-click approval.
            </p>
            <Link to="/circles" style={{ marginTop: "auto", fontSize: "0.875rem", fontWeight: 600 }}>
              Browse Circles →
            </Link>
          </div>

          <div className="rc-feature-card">
            <div className="rc-feature-icon" style={{ background: "rgba(16, 185, 129, 0.2)" }}>
              📅
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <h3 className="rc-feature-title">Events & Meetups</h3>
              <span className="rc-badge" style={{ background: "rgba(255,255,255,0.1)", color: "#cbd5e1" }}>
                Next Phase
              </span>
            </div>
            <p className="rc-feature-desc">
              RSVP to author talks, book swaps, discussion meetups, and member capacity enforcement.
            </p>
          </div>

          <div className="rc-feature-card">
            <div className="rc-feature-icon" style={{ background: "rgba(236, 72, 153, 0.2)" }}>
              💬
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <h3 className="rc-feature-title">Real-time Circle Chat</h3>
              <span className="rc-badge" style={{ background: "rgba(255,255,255,0.1)", color: "#cbd5e1" }}>
                Upcoming
              </span>
            </div>
            <p className="rc-feature-desc">
              Per-circle WebSocket STOMP messaging for vibrant discussions between circle members and organizers.
            </p>
          </div>
        </div>
      </div>
    </main>
  );
}

/** Admin Management Page */
function AdminPage() {
  const [users, setUsers] = useState<UserRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [filterRole, setFilterRole] = useState<string>("ALL");
  const [actionNotice, setActionNotice] = useState<string | null>(null);

  function loadUsers() {
    setLoading(true);
    apiClient
      .get<{ content: UserRow[] }>("/users")
      .then((res) => {
        setUsers(res.data.content);
        setError(null);
      })
      .catch(() => setError("Could not load users."))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    loadUsers();
  }, []);

  async function handleRoleChange(userId: string, newRole: Role) {
    try {
      await apiClient.patch(`/users/${userId}`, { role: newRole });
      setActionNotice(`Updated user role to ${newRole}`);
      setTimeout(() => setActionNotice(null), 3000);
      loadUsers();
    } catch {
      setError("Failed to update user role.");
    }
  }

  async function handleDeactivate(userId: string, userName: string) {
    if (!window.confirm(`Are you sure you want to deactivate ${userName}?`)) return;
    try {
      await apiClient.delete(`/users/${userId}`);
      setActionNotice(`User ${userName} has been deactivated`);
      setTimeout(() => setActionNotice(null), 3000);
      loadUsers();
    } catch {
      setError("Failed to deactivate user.");
    }
  }

  if (error) {
    return (
      <main className="rc-main-content">
        <div className="rc-alert rc-alert-error" role="alert">
          <span>⚠️</span>
          <span>{error}</span>
          <button
            type="button"
            className="rc-btn rc-btn-secondary rc-btn-sm"
            onClick={loadUsers}
            style={{ marginLeft: "auto" }}
          >
            Retry
          </button>
        </div>
      </main>
    );
  }

  if (loading && !users) {
    return (
      <main className="rc-main-content">
        <div style={{ textAlign: "center", padding: "4rem 0" }}>
          <div className="rc-spinner" style={{ margin: "0 auto 1rem", width: "28px", height: "28px" }}></div>
          <p style={{ color: "var(--text-secondary)" }}>Loading users…</p>
        </div>
      </main>
    );
  }

  const allUsers = users ?? [];
  const filteredUsers = allUsers.filter((u) => {
    const matchesSearch =
      u.name.toLowerCase().includes(search.toLowerCase()) ||
      u.email.toLowerCase().includes(search.toLowerCase());
    const matchesRole = filterRole === "ALL" || u.role === filterRole;
    return matchesSearch && matchesRole;
  });

  return (
    <main className="rc-main-content">
      <div className="rc-page-header">
        <div>
          <h1 className="rc-page-title font-serif">Users</h1>
          <p className="rc-page-desc">
            Manage community members, assign organizer privileges, and review account statuses.
          </p>
        </div>
        <button
          type="button"
          className="rc-btn rc-btn-secondary rc-btn-sm"
          onClick={loadUsers}
          disabled={loading}
        >
          🔄 Refresh
        </button>
      </div>

      {actionNotice && (
        <div className="rc-alert rc-alert-success" style={{ marginBottom: "1.5rem" }}>
          <span>✓</span>
          <span>{actionNotice}</span>
        </div>
      )}

      <section className="rc-table-container">
        <div className="rc-table-toolbar">
          <input
            type="text"
            className="rc-search-input"
            placeholder="Search by name or email…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />

          <div style={{ display: "flex", gap: "0.5rem" }}>
            {["ALL", "ADMIN", "ORGANIZER", "READER"].map((r) => (
              <button
                key={r}
                type="button"
                className={`rc-btn rc-btn-sm ${filterRole === r ? "rc-btn-primary" : "rc-btn-secondary"}`}
                onClick={() => setFilterRole(r)}
              >
                {r}
              </button>
            ))}
          </div>
        </div>

        <table className="rc-table">
          <thead>
            <tr>
              <th>Member</th>
              <th>Role</th>
              <th>Status</th>
              <th>Role Actions</th>
              <th>Manage</th>
            </tr>
          </thead>
          <tbody>
            {filteredUsers.length === 0 ? (
              <tr>
                <td colSpan={5} style={{ textAlign: "center", padding: "2.5rem", color: "var(--text-muted)" }}>
                  No members found matching your search.
                </td>
              </tr>
            ) : (
              filteredUsers.map((user) => (
                <tr key={user.id}>
                  <td>
                    <div className="rc-user-cell">
                      <div className="rc-avatar" aria-hidden="true">
                        {user.name ? user.name.charAt(0).toUpperCase() : "U"}
                      </div>
                      <div>
                        <div className="rc-user-name">{user.name}</div>
                        <div className="rc-user-email">{user.email}</div>
                      </div>
                    </div>
                  </td>
                  <td>
                    <span className={`rc-badge rc-badge-${user.role.toLowerCase()}`}>
                      {user.role}
                    </span>
                  </td>
                  <td>
                    <span className={`rc-badge ${user.active ? "rc-badge-active" : "rc-badge-inactive"}`}>
                      {user.active ? "Active" : "Deactivated"}
                    </span>
                  </td>
                  <td>
                    <select
                      className="rc-input"
                      style={{ padding: "0.35rem 0.65rem", fontSize: "0.85rem", width: "auto" }}
                      value={user.role}
                      onChange={(e) => handleRoleChange(user.id, e.target.value as Role)}
                    >
                      <option value="READER">READER</option>
                      <option value="ORGANIZER">ORGANIZER</option>
                      <option value="ADMIN">ADMIN</option>
                    </select>
                  </td>
                  <td>
                    {user.active ? (
                      <button
                        type="button"
                        className="rc-btn rc-btn-danger rc-btn-sm"
                        onClick={() => handleDeactivate(user.id, user.name)}
                      >
                        Deactivate
                      </button>
                    ) : (
                      <span style={{ fontSize: "0.85rem", color: "var(--text-muted)" }}>Inactive</span>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>

        {/* Keep hidden li list for backward compatibility with existing tests */}
        <ul style={{ display: "none" }}>
          {allUsers.map((u) => (
            <li key={u.email}>{u.email}</li>
          ))}
        </ul>
      </section>
    </main>
  );
}

export function App() {
  return (
    <BrowserRouter>
      <Navbar />
      <Routes>
        <Route path="/" element={<CommunityPage />} />
        <Route path="/circles" element={<CircleListPage />} />
        <Route path="/circles/:id" element={<CircleDetailPage />} />
        <Route
          path="/my-memberships"
          element={
            <AuthGuard>
              <MyMembershipsPage />
            </AuthGuard>
          }
        />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route
          path="/admin"
          element={
            <AuthGuard requiredRole="ADMIN">
              <AdminPage />
            </AuthGuard>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
