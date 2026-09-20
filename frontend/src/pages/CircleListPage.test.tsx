import { fireEvent, render, screen } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import { beforeEach, describe, expect, it } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { apiClient, clearTokens } from "../lib/apiClient";
import { CircleListPage } from "./CircleListPage";

describe("CircleListPage", () => {
  beforeEach(() => {
    clearTokens();
  });

  it("fetches and renders circles", async () => {
    const mock = new MockAdapter(apiClient);
    try {
      mock.onGet("/circles").reply(200, [
        {
          id: "c1",
          name: "Dhaka Classics",
          city: "Dhaka",
          description: "A great book club",
          organizerId: "o1",
          status: "ACTIVE",
          createdAt: "2026-01-01T00:00:00Z",
          memberCount: 5,
        },
      ]);

      render(
        <MemoryRouter>
          <CircleListPage />
        </MemoryRouter>,
      );

      expect(await screen.findByText("Dhaka Classics")).toBeInTheDocument();
      expect(screen.getAllByText("📍 Dhaka").length).toBeGreaterThanOrEqual(1);
    } finally {
      mock.restore();
    }
  });

  it("filters circles when city search submitted", async () => {
    const mock = new MockAdapter(apiClient);
    try {
      mock.onGet("/circles").reply(200, []);
      mock.onGet("/circles?city=Sylhet").reply(200, [
        {
          id: "c2",
          name: "Sylhet Mystery",
          city: "Sylhet",
          description: "Mystery books in Sylhet",
          organizerId: "o2",
          status: "ACTIVE",
          createdAt: "2026-01-01T00:00:00Z",
          memberCount: 3,
        },
      ]);

      render(
        <MemoryRouter>
          <CircleListPage />
        </MemoryRouter>,
      );

      const searchInput = screen.getByPlaceholderText(/filter by city/i);
      fireEvent.change(searchInput, { target: { value: "Sylhet" } });
      fireEvent.click(screen.getByRole("button", { name: /search/i }));

      expect(await screen.findByText("Sylhet Mystery")).toBeInTheDocument();
    } finally {
      mock.restore();
    }
  });
});
