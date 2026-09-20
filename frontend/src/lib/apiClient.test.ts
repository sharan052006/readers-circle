import MockAdapter from "axios-mock-adapter";
import { beforeEach, describe, expect, it } from "vitest";
import {
  clearTokens,
  createApi,
  decodeRole,
  saveTokens,
  TokenPair,
} from "./apiClient";

const OLD_PAIR: TokenPair = { accessToken: "old-access", refreshToken: "old-refresh" };
const NEW_PAIR: TokenPair = { accessToken: "new-access", refreshToken: "new-refresh" };

function fakeJwt(role: string): string {
  const b64 = (s: string) =>
    btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return `${b64('{"alg":"HS256"}')}.${b64(JSON.stringify({ sub: "1", role }))}.sig`;
}

describe("apiClient", () => {
  beforeEach(() => clearTokens());

  it("attaches Bearer from sessionStorage", async () => {
    const api = createApi("http://test");
    const mock = new MockAdapter(api);
    saveTokens(OLD_PAIR);
    mock.onGet("/me").reply((config) => {
      expect(config.headers?.Authorization).toBe("Bearer old-access");
      return [200, {}];
    });

    await api.get("/me");
  });

  it("sends no Authorization header without a session", async () => {
    const api = createApi("http://test");
    const mock = new MockAdapter(api);
    mock.onGet("/me").reply((config) => {
      expect(config.headers?.Authorization).toBeUndefined();
      return [200, {}];
    });

    await api.get("/me");
  });

  it("401 refreshes once then retries with the new token", async () => {
    const api = createApi("http://test");
    const mock = new MockAdapter(api);
    saveTokens(OLD_PAIR);
    mock.onGet("/me").replyOnce(401);
    mock.onGet("/me").reply((config) => {
      expect(config.headers?.Authorization).toBe("Bearer new-access");
      return [200, { ok: true }];
    });
    mock.onPost("/auth/refresh", { refreshToken: "old-refresh" }).reply(200, NEW_PAIR);

    const res = await api.get("/me");

    expect(res.data).toEqual({ ok: true });
    expect(mock.history.post.filter((p) => p.url === "/auth/refresh")).toHaveLength(1);
    expect(sessionStorage.getItem("rc.accessToken")).toBe("new-access");
  });

  it("failed refresh clears the session and rejects", async () => {
    const api = createApi("http://test");
    const mock = new MockAdapter(api);
    saveTokens(OLD_PAIR);
    mock.onGet("/me").reply(401);
    mock.onPost("/auth/refresh").reply(401);

    await expect(api.get("/me")).rejects.toMatchObject({ response: { status: 401 } });
    expect(sessionStorage.getItem("rc.accessToken")).toBeNull();
    expect(sessionStorage.getItem("rc.refreshToken")).toBeNull();
  });

  it("concurrent 401s share a single refresh POST", async () => {
    const api = createApi("http://test");
    const mock = new MockAdapter(api);
    saveTokens(OLD_PAIR);
    mock.onGet("/me").replyOnce(401);
    mock.onGet("/me").replyOnce(401);
    mock.onGet("/me").reply(200, { ok: true });
    mock.onPost("/auth/refresh").reply(200, NEW_PAIR);

    const [a, b] = await Promise.all([api.get("/me"), api.get("/me")]);

    expect(a.data).toEqual({ ok: true });
    expect(b.data).toEqual({ ok: true });
    expect(mock.history.post.filter((p) => p.url === "/auth/refresh")).toHaveLength(1);
  });

  it("decodeRole reads the claim and rejects junk", () => {
    expect(decodeRole(fakeJwt("ADMIN"))).toBe("ADMIN");
    expect(decodeRole(fakeJwt("READER"))).toBe("READER");
    expect(decodeRole("not-a-jwt")).toBeNull();
    expect(decodeRole(fakeJwt("SUPERUSER"))).toBeNull();
  });
});
