import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from '../lib/authContext';
import { RequireAuth } from '../lib/guards';

vi.mock('../lib/apiClient', async (importOriginal) => {
  const mod = await importOriginal<typeof import('../lib/apiClient')>();
  return {
    ...mod,
    api: {
      get: vi.fn().mockRejectedValue({ response: { status: 401 } }),
      post: vi.fn().mockRejectedValue({ response: { status: 401 } }),
    },
  };
});

describe('RequireAuth', () => {
  it('redirects to login when anonymous', async () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route
              path="/"
              element={
                <RequireAuth>
                  <p>secret</p>
                </RequireAuth>
              }
            />
            <Route path="/login" element={<p>login page</p>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );
    expect(await screen.findByText(/login page/i)).toBeInTheDocument();
  });
});
