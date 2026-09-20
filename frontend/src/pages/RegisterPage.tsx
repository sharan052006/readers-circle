import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AxiosError } from "axios";
import { apiClient, saveTokens, TokenPair } from "../lib/apiClient";

export function RegisterPage() {
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) {
      setError("Name is required.");
      return;
    }
    if (!email.trim()) {
      setError("Email is required.");
      return;
    }
    if (password.length < 8) {
      setError("Password must be at least 8 characters.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const res = await apiClient.post<TokenPair>("/auth/register", {
        name: name.trim(),
        email: email.trim(),
        password,
      });
      saveTokens(res.data);
      navigate("/");
    } catch (err) {
      setError(
        err instanceof AxiosError
          ? ((err.response?.data as { message?: string } | undefined)?.message ??
            "Registration failed.")
          : "Registration failed.",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <main>
      <h1>Create your Reader&apos;s Circle account</h1>
      <form onSubmit={onSubmit}>
        <label>
          Name
          <input
            type="text"
            autoComplete="name"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </label>
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
          Password (min 8 characters)
          <input
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        {error && <p role="alert">{error}</p>}
        <button type="submit" disabled={busy}>
          {busy ? "Registering…" : "Register"}
        </button>
      </form>
      <p>
        Have an account? <Link to="/login">Log in</Link>
      </p>
    </main>
  );
}
