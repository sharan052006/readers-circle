import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AdminUsersPage from '../pages/AdminUsersPage';
import { AuthProvider } from '../lib/authContext';
import { api } from '../lib/apiClient';

vi.mock('../lib/apiClient', async (importOriginal) => {
  const mod = await importOriginal<typeof import('../lib/apiClient')>();
  return { ...mod, api: { get: vi.fn(), patch: vi.fn(), delete: vi.fn(), post: vi.fn() } };
});

describe('AdminUsersPage', () => {
  beforeEach(() => {
    vi.mocked(api.post).mockRejectedValue({ response: { status: 401 } });
    vi.mocked(api.get).mockImplementation((url: string) => {
      if (url === '/users') {
        return Promise.resolve({
          data: [{ id: '1', name: 'Ann', email: 'ann@x.com', role: 'READER' }],
        });
      }
      return Promise.reject({ response: { status: 401 } });
    });
  });

  it('lists users for admin', async () => {
    render(
      <AuthProvider>
        <MemoryRouter>
          <AdminUsersPage />
        </MemoryRouter>
      </AuthProvider>,
    );
    expect(await screen.findByRole('heading', { name: /users/i })).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByText(/ann.*reader/i)).toBeInTheDocument(),
    );
  });
});
