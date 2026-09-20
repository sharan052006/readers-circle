import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { apiClient, loadTokens } from "../lib/apiClient";

export type MembershipState = "PENDING" | "APPROVED" | "REJECTED" | null;

interface JoinButtonProps {
  circleId: string;
  initialStatus?: MembershipState;
  onStatusChange?: (newStatus: "PENDING" | "APPROVED" | "REJECTED") => void;
}

export function JoinButton({ circleId, initialStatus = null, onStatusChange }: JoinButtonProps) {
  const navigate = useNavigate();
  const [status, setStatus] = useState<MembershipState>(initialStatus);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleJoin() {
    const tokens = loadTokens();
    if (!tokens) {
      navigate("/login");
      return;
    }

    setBusy(true);
    setError(null);
    try {
      const res = await apiClient.post<{ status: "PENDING" | "APPROVED" | "REJECTED" }>(
        `/circles/${circleId}/join-requests`,
      );
      setStatus(res.data.status);
      onStatusChange?.(res.data.status);
    } catch {
      setError("Could not submit join request.");
    } finally {
      setBusy(false);
    }
  }

  if (status === "APPROVED") {
    return (
      <span className="rc-badge rc-badge-active" style={{ fontSize: "0.85rem", padding: "0.4rem 0.8rem" }}>
        ✓ Member
      </span>
    );
  }

  if (status === "PENDING") {
    return (
      <span className="rc-badge rc-badge-organizer" style={{ fontSize: "0.85rem", padding: "0.4rem 0.8rem" }}>
        ⏳ Awaiting organizer approval
      </span>
    );
  }

  return (
    <div style={{ display: "inline-flex", flexDirection: "column", gap: "0.25rem" }}>
      {status === "REJECTED" ? (
        <button
          type="button"
          className="rc-btn rc-btn-secondary rc-btn-sm"
          onClick={handleJoin}
          disabled={busy}
        >
          {busy ? "Submitting…" : "Request again"}
        </button>
      ) : (
        <button
          type="button"
          className="rc-btn rc-btn-primary rc-btn-sm"
          onClick={handleJoin}
          disabled={busy}
        >
          {busy ? "Joining…" : "Request to Join"}
        </button>
      )}
      {error && <small role="alert" style={{ color: "#fda4af" }}>{error}</small>}
    </div>
  );
}
