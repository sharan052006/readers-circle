import { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { decodeRole, loadTokens, Role } from "../lib/apiClient";

interface Props {
  children: ReactNode;
  /** When set, only this role passes (e.g. "ADMIN" for user management). */
  requiredRole?: Role;
}

export function AuthGuard({ children, requiredRole }: Props) {
  const tokens = loadTokens();
  const location = useLocation();

  if (!tokens?.accessToken) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (requiredRole && decodeRole(tokens.accessToken) !== requiredRole) {
    return (
      <main>
        <h1>403 — Forbidden</h1>
        <p>This page requires the {requiredRole} role.</p>
      </main>
    );
  }
  return <>{children}</>;
}
