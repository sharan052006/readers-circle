import test from 'node:test';
import assert from 'node:assert/strict';
import {
  extractRoleFromToken,
  ApiError,
  NetworkTimeoutError,
  NetworkConnectionError,
  fetchWithTimeout,
} from './api.ts';

// Helper to construct a mock JWT token with base64Url payload
function createMockJwt(payload) {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
  const signature = 'mock_signature';
  return `${header}.${body}.${signature}`;
}

test('extractRoleFromToken correctly parses role from JWT payload', () => {
  const adminToken = createMockJwt({ sub: 'user-1', role: 'ADMIN', exp: 9999999999 });
  assert.equal(extractRoleFromToken(adminToken), 'ADMIN');

  const readerToken = createMockJwt({ sub: 'user-2', role: 'READER', exp: 9999999999 });
  assert.equal(extractRoleFromToken(readerToken), 'READER');

  const organizerToken = createMockJwt({ sub: 'user-3', role: 'ORGANIZER', exp: 9999999999 });
  assert.equal(extractRoleFromToken(organizerToken), 'ORGANIZER');
});

test('extractRoleFromToken returns null for invalid or missing tokens', () => {
  assert.equal(extractRoleFromToken(null), null);
  assert.equal(extractRoleFromToken(undefined), null);
  assert.equal(extractRoleFromToken(''), null);
  assert.equal(extractRoleFromToken('not-a-token'), null);
  assert.equal(extractRoleFromToken('header.invalid-base64.sig'), null);
  assert.equal(extractRoleFromToken(createMockJwt({ sub: 'no-role' })), null);
});

test('Error classes are properly typed and hold messages', () => {
  const apiErr = new ApiError(401, 'Invalid credentials');
  assert.equal(apiErr.status, 401);
  assert.equal(apiErr.message, 'Invalid credentials');
  assert.equal(apiErr.name, 'ApiError');

  const timeoutErr = new NetworkTimeoutError('Timed out');
  assert.equal(timeoutErr.name, 'NetworkTimeoutError');
  assert.equal(timeoutErr.message, 'Timed out');

  const connErr = new NetworkConnectionError('Connection refused');
  assert.equal(connErr.name, 'NetworkConnectionError');
  assert.equal(connErr.message, 'Connection refused');
});

test('fetchWithTimeout aborts and throws NetworkTimeoutError on slow response', async () => {
  // Mock global fetch that delays longer than timeout
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (_url, { signal }) => {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        resolve(new Response('OK', { status: 200 }));
      }, 500);
      if (signal) {
        signal.addEventListener('abort', () => {
          clearTimeout(timer);
          const err = new Error('The operation was aborted');
          err.name = 'AbortError';
          reject(err);
        });
      }
    });
  };

  try {
    await assert.rejects(
      async () => {
        await fetchWithTimeout('https://example.com/slow', {}, 50); // 50ms timeout
      },
      (err) => {
        assert(err instanceof NetworkTimeoutError);
        assert(err.message.includes('timed out'));
        return true;
      }
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('fetchWithTimeout passes successful requests through', async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({ success: true }), { status: 200 });

  try {
    const res = await fetchWithTimeout('https://example.com/fast', {}, 2000);
    assert.equal(res.status, 200);
    const data = await res.json();
    assert.equal(data.success, true);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
