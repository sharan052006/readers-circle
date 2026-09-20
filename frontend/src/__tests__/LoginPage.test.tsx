import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import LoginPage from '../pages/LoginPage';
import { AuthProvider } from '../lib/authContext';

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

describe('LoginPage', () => {
  it('renders form', () => {
    render(
      <AuthProvider>
        <MemoryRouter>
          <LoginPage />
        </MemoryRouter>
      </AuthProvider>,
    );
    expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /login/i })).toBeInTheDocument();
  });
});
