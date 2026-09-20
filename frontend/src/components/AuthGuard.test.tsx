import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthGuard } from "./AuthGuard";
import { clearTokens, saveTokens } from "../lib/apiClient";

function fakeJwt(role: string): string {
  const b64 = (s: string) =>
    btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return `${b64('{"alg":"HS256"}')}.${b64(JSON.stringify({ sub: "1", role }))}.sig`;
}

function renderGuard() {
  return render(
    <MemoryRouter initialEntries={["/admin"]}>
      <Routes>
        <Route path="/login" element={<p>Login page</p>} />
        <Route
          path="/admin"
          element={
            <AuthGuard requiredRole="ADMIN">
              <p>Secret admin content</p>
            </AuthGuard>
          }
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe("AuthGuard", () => {
  beforeEach(() => clearTokens());

  it("redirects to login without a session", () => {
    renderGuard();
    expect(screen.getByText("Login page")).toBeInTheDocument();
  });

  it("shows 403 for the wrong role", () => {
    saveTokens({ accessToken: fakeJwt("READER"), refreshToken: "r" });
    renderGuard();
    expect(screen.getByText("403 — Forbidden")).toBeInTheDocument();
    expect(screen.queryByText("Secret admin content")).not.toBeInTheDocument();
  });

  it("renders children for ADMIN", () => {
    saveTokens({ accessToken: fakeJwt("ADMIN"), refreshToken: "r" });
    renderGuard();
    expect(screen.getByText("Secret admin content")).toBeInTheDocument();
  });
});
