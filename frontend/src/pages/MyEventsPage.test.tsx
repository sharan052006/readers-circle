import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";
import { apiClient } from "../lib/apiClient";
import { MyEventsPage } from "./MyEventsPage";

describe("MyEventsPage", () => {
  let mock: MockAdapter;

  beforeEach(() => {
    mock = new MockAdapter(apiClient);
  });

  it("renders registrations and handles cancellation", async () => {
    mock.onGet("/users/me/registrations").reply(200, [
      {
        id: "reg-1",
        eventId: "event-1",
        eventTitle: "Dune Discussion",
        circleId: "circle-1",
        readerId: "user-1",
        status: "REGISTERED",
        registeredAt: "2026-09-10T12:00:00Z",
      },
    ]);
    mock.onDelete("/events/event-1/register").reply(200, {});

    render(
      <MemoryRouter>
        <MyEventsPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("Dune Discussion")).toBeInTheDocument();
      expect(screen.getByText("REGISTERED")).toBeInTheDocument();
    });

    const cancelBtn = screen.getByRole("button", { name: /cancel rsvp/i });
    fireEvent.click(cancelBtn);

    await waitFor(() => {
      expect(mock.history.delete.length).toBe(1);
    });
  });

  it("shows empty state when user has no registrations", async () => {
    mock.onGet("/users/me/registrations").reply(200, []);

    render(
      <MemoryRouter>
        <MyEventsPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText(/no event rsvps yet/i)).toBeInTheDocument();
    });
  });
});
