import { fireEvent, render, screen } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import { beforeEach, describe, expect, it } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { apiClient, clearTokens, saveTokens } from "../lib/apiClient";
import { JoinButton } from "./JoinButton";

describe("JoinButton", () => {
  beforeEach(() => {
    clearTokens();
  });

  it("shows 'Request to Join' when not a member", () => {
    render(
      <MemoryRouter>
        <JoinButton circleId="c-1" initialStatus={null} />
      </MemoryRouter>,
    );
    expect(screen.getByRole("button", { name: /request to join/i })).toBeInTheDocument();
  });

  it("shows 'Awaiting organizer approval' when PENDING", () => {
    render(
      <MemoryRouter>
        <JoinButton circleId="c-1" initialStatus="PENDING" />
      </MemoryRouter>,
    );
    expect(screen.getByText(/awaiting organizer approval/i)).toBeInTheDocument();
  });

  it("shows 'Member' when APPROVED", () => {
    render(
      <MemoryRouter>
        <JoinButton circleId="c-1" initialStatus="APPROVED" />
      </MemoryRouter>,
    );
    expect(screen.getByText(/member/i)).toBeInTheDocument();
  });

  it("shows 'Request again' when REJECTED", () => {
    render(
      <MemoryRouter>
        <JoinButton circleId="c-1" initialStatus="REJECTED" />
      </MemoryRouter>,
    );
    expect(screen.getByRole("button", { name: /request again/i })).toBeInTheDocument();
  });

  it("submits join request when clicked", async () => {
    saveTokens({ accessToken: "test-token", refreshToken: "ref-token" });
    const mock = new MockAdapter(apiClient);
    try {
      mock.onPost("/circles/c-1/join-requests").reply(200, { status: "PENDING" });
      render(
        <MemoryRouter>
          <JoinButton circleId="c-1" initialStatus={null} />
        </MemoryRouter>,
      );

      fireEvent.click(screen.getByRole("button", { name: /request to join/i }));
      expect(await screen.findByText(/awaiting organizer approval/i)).toBeInTheDocument();
    } finally {
      mock.restore();
    }
  });
});
