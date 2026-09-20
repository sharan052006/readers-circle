import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CapacityBar } from "./CapacityBar";

describe("CapacityBar", () => {
  it("renders seat counts and progressbar correctly", () => {
    render(
      <CapacityBar registeredCount={3} capacity={10} />
    );
    expect(screen.getByText(/3/)).toBeInTheDocument();
    expect(screen.getByText(/\/ 10 seats filled/)).toBeInTheDocument();
    expect(screen.getByText(/7 spots left/)).toBeInTheDocument();
    const bar = screen.getByRole("progressbar");
    expect(bar).toHaveAttribute("aria-valuenow", "3");
    expect(bar).toHaveAttribute("aria-valuemax", "10");
  });

  it("shows 'Full' badge when capacity is reached", () => {
    render(
      <CapacityBar registeredCount={10} capacity={10} />
    );
    expect(screen.getByText(/Full/i)).toBeInTheDocument();
  });

  it("shows 'RSVP Closed' badge when deadline has passed", () => {
    const pastDeadline = new Date(Date.now() - 3600000).toISOString();
    render(
      <CapacityBar
        registeredCount={2}
        capacity={10}
        registrationDeadline={pastDeadline}
      />
    );
    expect(screen.getAllByText(/RSVP Closed/i).length).toBeGreaterThanOrEqual(1);
  });
});
