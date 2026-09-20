import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import axios from "axios";
import { apiClient, saveTokens, TokenPair } from "../lib/apiClient";

export function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!email.trim() || !password) {
      setError("Email and password are required.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const res = await apiClient.post<TokenPair>("/auth/login", {
        email: email.trim(),
        password,
      });
      saveTokens(res.data);
      navigate("/");
    } catch (err) {
      setError(
        axios.isAxiosError(err)
          ? ((err.response?.data as { message?: string } | undefined)?.message ??
            "Login failed.")
          : "Login failed.",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <main>
      <h1>Log in to Reader&apos;s Circle</h1>
      <form onSubmit={onSubmit}>
        <label>
          Email
          <input
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </label>
        <label>
          Password
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        {error && <p role="alert">{error}</p>}
        <button type="submit" disabled={busy}>
          {busy ? "Logging in…" : "Log in"}
        </button>
      </form>
      <p>
        No account? <Link to="/register">Register</Link>
      </p>
    </main>
  );
}
