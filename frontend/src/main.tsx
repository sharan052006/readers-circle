import React from 'react';
import './styles.css';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route, Navigate, Link } from 'react-router-dom';
import { AuthProvider, useAuth } from './lib/authContext';
import { RequireAuth, RequireRole } from './lib/guards';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import AdminUsersPage from './pages/AdminUsersPage';

function Home() {
  const { user, isAdmin, logout } = useAuth();
  return (
    <main>
      <h1>Reader&apos;s Circle</h1>
      {user ? (
        <>
          <p>
            {user.name} ({user.role})
          </p>
          <button onClick={logout}>Logout</button>
          {isAdmin && (
            <p>
              <Link to="/admin/users">Manage users</Link>
            </p>
          )}
        </>
      ) : (
        <p>
          <Link to="/login">Login</Link> · <Link to="/register">Register</Link>
        </p>
      )}
    </main>
  );
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route
            path="/"
            element={
              <RequireAuth>
                <Home />
              </RequireAuth>
            }
          />
          <Route
            path="/admin/users"
            element={
              <RequireRole roles={['ADMIN']}>
                <AdminUsersPage />
              </RequireRole>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  </React.StrictMode>,
);
