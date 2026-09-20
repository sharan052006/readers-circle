import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { EventCard, EventSummary } from "./EventCard";

const baseEvent: EventSummary = {
  id: "e-1",
  circleId: "c-1",
  title: "Book Club Gathering",
  readingTopic: "Dystopian Fiction",
  eventDate: "2026-10-15",
  eventTime: "18:30:00",
  venue: "City Central Library",
  capacity: 10,
  registeredCount: 3,
  registrationDeadline: new Date(Date.now() + 86400000).toISOString(),
  status: "PUBLISHED",
  isRegistered: false,
  createdAt: "2026-09-01T00:00:00Z",
};

describe("EventCard", () => {
  it("renders event details and RSVP button", () => {
    const onRegister = vi.fn();
    render(
      <EventCard event={baseEvent} onRegister={onRegister} />
    );

    expect(screen.getByText("Book Club Gathering")).toBeInTheDocument();
    expect(screen.getByText(/Dystopian Fiction/i)).toBeInTheDocument();
    expect(screen.getByText(/City Central Library/i)).toBeInTheDocument();

    const rsvpBtn = screen.getByRole("button", { name: /register \/ rsvp/i });
    expect(rsvpBtn).toBeInTheDocument();
    fireEvent.click(rsvpBtn);
    expect(onRegister).toHaveBeenCalledWith("e-1");
  });

  it("shows registered status and cancel button when user is registered", () => {
    const onCancel = vi.fn();
    const registeredEvent: EventSummary = {
      ...baseEvent,
      isRegistered: true,
    };

    render(
      <EventCard event={registeredEvent} onCancel={onCancel} />
    );

    expect(screen.getByText(/✓ Registered/i)).toBeInTheDocument();
    const cancelBtn = screen.getByRole("button", { name: /cancel rsvp/i });
    expect(cancelBtn).toBeInTheDocument();
    fireEvent.click(cancelBtn);
    expect(onCancel).toHaveBeenCalledWith("e-1");
  });

  it("disables registration when event is full", () => {
    const fullEvent: EventSummary = {
      ...baseEvent,
      registeredCount: 10,
    };

    render(<EventCard event={fullEvent} />);
    const btn = screen.getByRole("button", { name: /event full/i });
    expect(btn).toBeDisabled();
  });
});
