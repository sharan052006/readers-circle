import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import { beforeEach, describe, expect, it } from "vitest";
import { App } from "./App";
import { apiClient, clearTokens, saveTokens } from "./lib/apiClient";

function fakeJwt(role: string): string {
  const b64 = (s: string) =>
    btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return `${b64('{"alg":"HS256"}')}.${b64(JSON.stringify({ sub: "1", role }))}.sig`;
}

describe("Event Gallery Routing & Navigation in App", () => {
  let mock: MockAdapter;

  beforeEach(() => {
    mock = new MockAdapter(apiClient);
    clearTokens();
  });

  it("renders EventGalleryPage via /events/:eventId/gallery without redirecting to /", async () => {
    saveTokens({ accessToken: fakeJwt("READER"), refreshToken: "r" });

    window.history.pushState({}, "", "/events/evt-999/gallery");

    mock.onGet("/events/evt-999").reply(200, {
      id: "evt-999",
      circleId: "circle-1",
      circleName: "Fiction Club",
      title: "Completed Discussion",
      readingTopic: "Dune",
      status: "COMPLETED",
      isOrganizerOrAdmin: false,
    });
    mock.onGet("/events/evt-999/gallery").reply(200, []);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText("Completed Discussion — Memory Gallery")).toBeInTheDocument();
    });

    expect(window.location.pathname).toBe("/events/evt-999/gallery");
  });

  it("renders EventGalleryPage via /events/:id/gallery without redirecting to /", async () => {
    saveTokens({ accessToken: fakeJwt("READER"), refreshToken: "r" });

    window.history.pushState({}, "", "/events/evt-888/gallery");

    mock.onGet("/events/evt-888").reply(200, {
      id: "evt-888",
      circleId: "circle-1",
      circleName: "Fiction Club",
      title: "Past Meetup",
      status: "COMPLETED",
      isOrganizerOrAdmin: false,
    });
    mock.onGet("/events/evt-888/gallery").reply(200, []);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText("Past Meetup — Memory Gallery")).toBeInTheDocument();
    });

    expect(window.location.pathname).toBe("/events/evt-888/gallery");
  });

  it("navigates from EventDetailPage to EventGalleryPage on clicking gallery link", async () => {
    saveTokens({ accessToken: fakeJwt("READER"), refreshToken: "r" });

    window.history.pushState({}, "", "/events/evt-123");

    mock.onGet("/events/evt-123").reply(200, {
      id: "evt-123",
      circleId: "c1",
      circleName: "Test Circle",
      title: "Completed Discussion",
      status: "COMPLETED",
      registeredCount: 5,
      capacity: 10,
      isRegistered: true,
      isOrganizerOrAdmin: false,
    });
    mock.onGet("/events/evt-123/gallery").reply(200, []);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText("Completed Discussion")).toBeInTheDocument();
    });

    const galleryLinks = screen.getAllByText(/Event Gallery & Memories/i);
    expect(galleryLinks.length).toBeGreaterThan(0);

    fireEvent.click(galleryLinks[0]);

    await waitFor(() => {
      expect(screen.getByText("Completed Discussion — Memory Gallery")).toBeInTheDocument();
    });

    expect(window.location.pathname).toBe("/events/evt-123/gallery");
  });

  it("does not redirect unauthenticated users to / when viewing event gallery", async () => {
    clearTokens();

    window.history.pushState({}, "", "/events/evt-777/gallery");

    mock.onGet("/events/evt-777").reply(200, {
      id: "evt-777",
      circleId: "c1",
      circleName: "Open Circle",
      title: "Public Event",
      status: "COMPLETED",
      isOrganizerOrAdmin: false,
    });
    mock.onGet("/events/evt-777/gallery").reply(403, { message: "Forbidden" });

    render(<App />);

    await waitFor(() => {
      expect(
        screen.getByText("You must be an approved member of this circle to view the event memories gallery.")
      ).toBeInTheDocument();
    });

    expect(window.location.pathname).toBe("/events/evt-777/gallery");
  });
});
