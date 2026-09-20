import { FormEvent, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { apiClient, decodeRole, loadTokens } from "../lib/apiClient";

export interface CircleSummary {
  id: string;
  name: string;
  city: string;
  description: string;
  organizerId: string;
  status: "ACTIVE" | "INACTIVE";
  createdAt: string;
  memberCount: number;
}

export function CircleListPage() {
  const [circles, setCircles] = useState<CircleSummary[]>([]);
  const [cityFilter, setCityFilter] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Admin circle creation state
  const tokens = loadTokens();
  const isAdmin = tokens ? decodeRole(tokens.accessToken) === "ADMIN" : false;
  const [showCreate, setShowCreate] = useState(false);
  const [newName, setNewName] = useState("");
  const [newCity, setNewCity] = useState("");
  const [newDesc, setNewDesc] = useState("");
  const [newOrgId, setNewOrgId] = useState("");
  const [createBusy, setCreateBusy] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  function fetchCircles(city?: string) {
    setLoading(true);
    setError(null);
    const query = city && city.trim() ? `?city=${encodeURIComponent(city.trim())}` : "";
    apiClient
      .get<CircleSummary[]>(`/circles${query}`)
      .then((res) => setCircles(res.data))
      .catch(() => setError("Failed to load reading circles."))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    fetchCircles(cityFilter);
  }, []);

  function handleCitySearch(e: FormEvent) {
    e.preventDefault();
    fetchCircles(cityFilter);
  }

  function selectCity(city: string) {
    setCityFilter(city);
    fetchCircles(city);
  }

  async function handleCreateCircle(e: FormEvent) {
    e.preventDefault();
    if (!newName.trim() || !newCity.trim() || !newDesc.trim() || !newOrgId.trim()) {
      setCreateError("All fields are required.");
      return;
    }
    setCreateBusy(true);
    setCreateError(null);
    try {
      await apiClient.post("/circles", {
        name: newName.trim(),
        city: newCity.trim(),
        description: newDesc.trim(),
        organizerId: newOrgId.trim(),
      });
      setShowCreate(false);
      setNewName("");
      setNewCity("");
      setNewDesc("");
      setNewOrgId("");
      fetchCircles(cityFilter);
    } catch {
      setCreateError("Failed to create circle. Ensure Organizer UUID is valid.");
    } finally {
      setCreateBusy(false);
    }
  }

  return (
    <main className="rc-main-content">
      <div className="rc-page-header">
        <div>
          <h1 className="rc-page-title font-serif">Explore Reading Circles</h1>
          <p className="rc-page-desc">
            Discover local book clubs by city, connect with book lovers, and join the circle.
          </p>
        </div>
        {isAdmin && (
          <button
            type="button"
            className="rc-btn rc-btn-primary rc-btn-sm"
            onClick={() => setShowCreate(!showCreate)}
          >
            {showCreate ? "✕ Close Form" : "+ Create Circle (Admin)"}
          </button>
        )}
      </div>

      {/* Admin Circle Creation Panel */}
      {showCreate && (
        <section
          style={{
            background: "var(--bg-surface-card)",
            border: "1px solid var(--border-active)",
            borderRadius: "var(--radius-lg)",
            padding: "1.5rem",
            marginBottom: "2rem",
          }}
        >
          <h2 style={{ fontSize: "1.2rem", marginBottom: "1rem" }}>Create New Reading Circle</h2>
          <form className="rc-form" onSubmit={handleCreateCircle}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem" }}>
              <label className="rc-label">
                Circle Name
                <input
                  className="rc-input"
                  placeholder="e.g. Dhaka Classics Circle"
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                />
              </label>
              <label className="rc-label">
                City
                <input
                  className="rc-input"
                  placeholder="e.g. Dhaka"
                  value={newCity}
                  onChange={(e) => setNewCity(e.target.value)}
                />
              </label>
            </div>
            <div className="rc-form-group">
              <label className="rc-label">
                Organizer User ID (UUID)
                <input
                  className="rc-input"
                  placeholder="UUID of organizer user"
                  value={newOrgId}
                  onChange={(e) => setNewOrgId(e.target.value)}
                />
              </label>
              <div style={{ display: "flex", gap: "0.5rem", marginTop: "0.25rem", flexWrap: "wrap" }}>
                <button
                  type="button"
                  className="rc-btn rc-btn-secondary rc-btn-sm"
                  style={{ fontSize: "0.75rem", padding: "0.2rem 0.5rem" }}
                  onClick={() => setNewOrgId("e4ac3233-4e1b-4241-b7e3-2ad7c0138b39")}
                >
                  ⚡ Use Jane Organizer
                </button>
                <button
                  type="button"
                  className="rc-btn rc-btn-secondary rc-btn-sm"
                  style={{ fontSize: "0.75rem", padding: "0.2rem 0.5rem" }}
                  onClick={() => setNewOrgId("283a7ecb-35c9-4068-b1dc-3eb80e400053")}
                >
                  ⚡ Use Admin as Organizer
                </button>
              </div>
            </div>
            <label className="rc-label">
              Description
              <textarea
                className="rc-input"
                rows={3}
                placeholder="What books do you discuss? Meeting frequency, etc."
                value={newDesc}
                onChange={(e) => setNewDesc(e.target.value)}
              />
            </label>
            {createError && (
              <p role="alert" style={{ color: "#fda4af", fontSize: "0.9rem" }}>
                {createError}
              </p>
            )}
            <button type="submit" className="rc-btn rc-btn-primary" disabled={createBusy}>
              {createBusy ? "Creating…" : "Save Circle"}
            </button>
          </form>
        </section>
      )}

      {/* City Filter Toolbar */}
      <section style={{ marginBottom: "2rem" }}>
        <form
          onSubmit={handleCitySearch}
          style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap", marginBottom: "1rem" }}
        >
          <input
            type="text"
            className="rc-search-input"
            style={{ flex: 1, minWidth: "260px" }}
            placeholder="Filter by city (e.g. Dhaka, Chittagong, Sylhet)..."
            value={cityFilter}
            onChange={(e) => setCityFilter(e.target.value)}
          />
          <button type="submit" className="rc-btn rc-btn-primary rc-btn-sm">
            Search
          </button>
          {cityFilter && (
            <button
              type="button"
              className="rc-btn rc-btn-secondary rc-btn-sm"
              onClick={() => selectCity("")}
            >
              Clear
            </button>
          )}
        </form>

        <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap", alignItems: "center" }}>
          <span style={{ fontSize: "0.85rem", color: "var(--text-muted)", marginRight: "0.5rem" }}>
            Popular:
          </span>
          {["Dhaka", "Chittagong", "Sylhet", "Rajshahi", "Khulna"].map((c) => (
            <button
              key={c}
              type="button"
              className={`rc-btn rc-btn-sm ${cityFilter.toLowerCase() === c.toLowerCase() ? "rc-btn-primary" : "rc-btn-secondary"}`}
              onClick={() => selectCity(c)}
            >
              📍 {c}
            </button>
          ))}
        </div>
      </section>

      {error && (
        <div className="rc-alert rc-alert-error" role="alert" style={{ marginBottom: "1.5rem" }}>
          <span>⚠️</span>
          <span>{error}</span>
          <button
            type="button"
            className="rc-btn rc-btn-secondary rc-btn-sm"
            onClick={() => fetchCircles(cityFilter)}
            style={{ marginLeft: "auto" }}
          >
            Retry
          </button>
        </div>
      )}

      {loading ? (
        <div style={{ textAlign: "center", padding: "4rem 0" }}>
          <div className="rc-spinner" style={{ margin: "0 auto 1rem", width: "28px", height: "28px" }}></div>
          <p style={{ color: "var(--text-secondary)" }}>Loading reading circles…</p>
        </div>
      ) : circles.length === 0 ? (
        <div
          style={{
            textAlign: "center",
            padding: "4rem 1rem",
            background: "var(--bg-surface-card)",
            borderRadius: "var(--radius-lg)",
            border: "1px solid var(--border-subtle)",
          }}
        >
          <div style={{ fontSize: "2.5rem", marginBottom: "0.75rem" }}>🔍</div>
          <h3 className="font-serif" style={{ marginBottom: "0.5rem" }}>No reading circles found</h3>
          <p style={{ color: "var(--text-secondary)", maxWidth: "450px", margin: "0 auto" }}>
            No circles match your current city filter. Try clearing the filter or search for another city.
          </p>
        </div>
      ) : (
        <div className="rc-feature-grid" style={{ marginTop: 0 }}>
          {circles.map((circle) => (
            <article key={circle.id} className="rc-feature-card">
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: "0.5rem" }}>
                <span className="rc-badge rc-badge-reader">📍 {circle.city}</span>
                <span style={{ fontSize: "0.825rem", color: "var(--text-muted)" }}>
                  👥 {circle.memberCount} {circle.memberCount === 1 ? "member" : "members"}
                </span>
              </div>
              <h2 className="rc-feature-title" style={{ fontSize: "1.25rem" }}>
                <Link to={`/circles/${circle.id}`} style={{ color: "inherit" }}>
                  {circle.name}
                </Link>
              </h2>
              <p className="rc-feature-desc" style={{ flex: 1 }}>
                {circle.description.length > 120
                  ? `${circle.description.substring(0, 120)}…`
                  : circle.description}
              </p>
              <div style={{ borderTop: "1px solid var(--border-subtle)", paddingTop: "0.85rem", marginTop: "auto" }}>
                <Link to={`/circles/${circle.id}`} className="rc-btn rc-btn-secondary rc-btn-sm" style={{ width: "100%" }}>
                  View Circle & Join →
                </Link>
              </div>
            </article>
          ))}
        </div>
      )}
    </main>
  );
}
