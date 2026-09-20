import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";
import { apiClient } from "../lib/apiClient";
import { EventGalleryPage } from "./EventGalleryPage";

describe("EventGalleryPage", () => {
  let mock: MockAdapter;

  beforeEach(() => {
    mock = new MockAdapter(apiClient);
  });

  it("renders photo with img tag and video with video tag", async () => {
    mock.onGet("/events/event-1").reply(200, {
      id: "event-1",
      circleId: "circle-1",
      circleName: "Classic Books",
      title: "Completed Discussion",
      readingTopic: "Philosophy",
      status: "COMPLETED",
      isOrganizerOrAdmin: true,
    });

    mock.onGet("/events/event-1/gallery").reply(200, [
      {
        id: "item-1",
        eventId: "event-1",
        uploadedBy: "user-1",
        uploaderName: "Organizer",
        mediaType: "PHOTO",
        mediaUrl: "https://example.com/photo.jpg",
        caption: "Great tea discussion",
        uploadedAt: "2026-09-15T12:00:00Z",
      },
      {
        id: "item-2",
        eventId: "event-1",
        uploadedBy: "user-1",
        uploaderName: "Organizer",
        mediaType: "VIDEO",
        mediaUrl: "https://example.com/video.mp4",
        caption: "Opening remarks",
        uploadedAt: "2026-09-15T12:30:00Z",
      },
    ]);

    render(
      <MemoryRouter initialEntries={["/events/event-1/gallery"]}>
        <Routes>
          <Route path="/events/:id/gallery" element={<EventGalleryPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("Completed Discussion — Memory Gallery")).toBeInTheDocument();
      expect(screen.getByText("Great tea discussion")).toBeInTheDocument();
      expect(screen.getByText("Opening remarks")).toBeInTheDocument();
    });

    // Check img element
    const img = screen.getByAltText("Great tea discussion");
    expect(img).toBeInTheDocument();
    expect(img.tagName.toLowerCase()).toBe("img");

    // Check video element
    const video = document.querySelector("video");
    expect(video).toBeInTheDocument();
    expect(video).toHaveAttribute("src", "https://example.com/video.mp4");

    // Click on photo to open lightbox
    fireEvent.click(img);
    expect(screen.getByText(/close lightbox/i)).toBeInTheDocument();
  });

  it("shows empty state and upload button for organizer", async () => {
    mock.onGet("/events/event-1").reply(200, {
      id: "event-1",
      circleId: "circle-1",
      circleName: "Classic Books",
      title: "Completed Discussion",
      status: "COMPLETED",
      isOrganizerOrAdmin: true,
    });
    mock.onGet("/events/event-1/gallery").reply(200, []);

    render(
      <MemoryRouter initialEntries={["/events/event-1/gallery"]}>
        <Routes>
          <Route path="/events/:id/gallery" element={<EventGalleryPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText(/no gallery memories yet/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /upload first memory/i })).toBeInTheDocument();
    });
  });

  it("organizer can open modal, select file, and upload to gallery", async () => {
    mock.onGet("/events/event-1").reply(200, {
      id: "event-1",
      circleId: "circle-1",
      circleName: "Classic Books",
      title: "Completed Discussion",
      status: "COMPLETED",
      isOrganizerOrAdmin: true,
    });
    mock.onGet("/events/event-1/gallery").reply(200, []);
    mock.onPost("/events/event-1/gallery").reply(201, {
      id: "item-new",
      eventId: "event-1",
      uploadedBy: "org-1",
      uploaderName: "Organizer",
      mediaType: "PHOTO",
      mediaUrl: "/api/media/uploaded.jpg",
      caption: "Snapshot of meeting",
      uploadedAt: "2026-09-20T12:00:00Z",
    });

    render(
      <MemoryRouter initialEntries={["/events/event-1/gallery"]}>
        <Routes>
          <Route path="/events/:id/gallery" element={<EventGalleryPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /upload first memory/i })).toBeInTheDocument();
    });

    // Open modal
    fireEvent.click(screen.getByRole("button", { name: /upload first memory/i }));
    expect(screen.getByText("Add Event Memory")).toBeInTheDocument();

    // Select file
    const file = new File(["dummy"], "photo.jpg", { type: "image/jpeg" });
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(fileInput, { target: { files: [file] } });

    // Fill caption
    const captionInput = screen.getByLabelText(/caption \/ memory note/i);
    fireEvent.change(captionInput, { target: { value: "Snapshot of meeting" } });

    // Submit
    fireEvent.click(screen.getByRole("button", { name: /add to gallery/i }));

    await waitFor(() => {
      expect(mock.history.post.length).toBe(1);
      expect(mock.history.post[0].url).toBe("/events/event-1/gallery");
    });
  });
});
