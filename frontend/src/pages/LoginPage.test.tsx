import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { apiClient, clearTokens } from "../lib/apiClient";
import { LoginPage } from "./LoginPage";

const { navigate } = vi.hoisted(() => ({ navigate: vi.fn() }));
vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => navigate };
});

describe("LoginPage", () => {
  beforeEach(() => {
    clearTokens();
    navigate.mockClear();
  });

  function renderPage() {
    return render(
      <MemoryRouter initialEntries={["/login"]}>
        <Routes>
          <Route path="/" element={<p>Home</p>} />
          <Route path="*" element={<LoginPage />} />
        </Routes>
      </MemoryRouter>,
    );
  }

  it("validates before submitting", async () => {
    renderPage();
    fireEvent.click(screen.getByRole("button", { name: /log in/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/required/i);
    expect(navigate).not.toHaveBeenCalled();
  });

  it("stores the pair and navigates on success", async () => {
    const mock = new MockAdapter(apiClient);
    try {
      mock
        .onPost("/auth/login", { email: "a@x.com", password: "password123" })
        .reply(200, { accessToken: "a", refreshToken: "r" });
      renderPage();

      fireEvent.change(screen.getByLabelText(/email/i), { target: { value: "a@x.com" } });
      fireEvent.change(screen.getByLabelText(/password/i), {
        target: { value: "password123" },
      });
      fireEvent.click(screen.getByRole("button", { name: /log in/i }));

      await waitFor(() => expect(navigate).toHaveBeenCalledWith("/"));
      expect(sessionStorage.getItem("rc.accessToken")).toBe("a");
      expect(sessionStorage.getItem("rc.refreshToken")).toBe("r");
    } finally {
      mock.restore();
    }
  });

  it("shows the backend message on 401", async () => {
    const mock = new MockAdapter(apiClient);
    try {
      mock.onPost("/auth/login").reply(401, { message: "invalid credentials" });
      renderPage();

      fireEvent.change(screen.getByLabelText(/email/i), { target: { value: "a@x.com" } });
      fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "wrongpass1" } });
      fireEvent.click(screen.getByRole("button", { name: /log in/i }));

      expect(await screen.findByRole("alert")).toHaveTextContent("invalid credentials");
      expect(navigate).not.toHaveBeenCalled();
    } finally {
      mock.restore();
    }
  });
});
