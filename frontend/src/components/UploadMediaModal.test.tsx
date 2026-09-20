import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { UploadMediaModal } from "./UploadMediaModal";

// Mock URL.createObjectURL and URL.revokeObjectURL
window.URL.createObjectURL = vi.fn((file: File) => `blob:http://localhost/${file.name}`);
window.URL.revokeObjectURL = vi.fn();

describe("UploadMediaModal", () => {
  it("renders file upload UI and does NOT show URL inputs", () => {
    render(
      <UploadMediaModal
        isOpen={true}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
        eventTitle="Dune Gathering"
      />
    );

    expect(screen.getByText("Add Event Memory")).toBeInTheDocument();
    expect(screen.getByText("For Dune Gathering")).toBeInTheDocument();

    // Check media format toggle
    expect(screen.getByRole("button", { name: /photo \/ image/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /video clip/i })).toBeInTheDocument();

    // Check file picker section
    expect(screen.getByText("Select Media")).toBeInTheDocument();
    expect(screen.getByText(/choose a file from your device/i)).toBeInTheDocument();

    // Ensure NO URL input fields exist
    expect(screen.queryByText(/photo url/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/video url/i)).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText(/https:\/\//i)).not.toBeInTheDocument();

    // Check caption input
    expect(screen.getByLabelText(/caption \/ memory note/i)).toBeInTheDocument();

    // Check buttons
    expect(screen.getByRole("button", { name: /cancel/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /add to gallery/i })).toBeInTheDocument();
  });

  it("shows error when submitting without selecting a file", async () => {
    const onSubmit = vi.fn();
    render(
      <UploadMediaModal
        isOpen={true}
        onClose={vi.fn()}
        onSubmit={onSubmit}
        eventTitle="Dune Gathering"
      />
    );

    // Click submit without file
    fireEvent.click(screen.getByRole("button", { name: /add to gallery/i }));

    expect(await screen.findByText(/please choose a file to upload/i)).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("selects image file, shows image preview, and submits successfully", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const onClose = vi.fn();

    render(
      <UploadMediaModal
        isOpen={true}
        onClose={onClose}
        onSubmit={onSubmit}
        eventTitle="Dune Gathering"
      />
    );

    const file = new File(["dummy-image-content"], "gathering.jpg", { type: "image/jpeg" });
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;

    fireEvent.change(fileInput, { target: { files: [file] } });

    // Verify preview renders
    expect(await screen.findByAltText("Upload Preview")).toBeInTheDocument();
    expect(screen.getByText(/gathering.jpg/i)).toBeInTheDocument();

    // Add caption
    const captionInput = screen.getByLabelText(/caption \/ memory note/i);
    fireEvent.change(captionInput, { target: { value: "Great book discussion!" } });

    // Submit
    fireEvent.click(screen.getByRole("button", { name: /add to gallery/i }));

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith({
        mediaType: "PHOTO",
        file,
        caption: "Great book discussion!",
      });
      expect(onClose).toHaveBeenCalled();
    });
  });

  it("selects video file and shows video preview", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);

    render(
      <UploadMediaModal
        isOpen={true}
        onClose={vi.fn()}
        onSubmit={onSubmit}
        eventTitle="Dune Gathering"
      />
    );

    // Switch to Video format
    fireEvent.click(screen.getByRole("button", { name: /video clip/i }));

    const videoFile = new File(["dummy-video-content"], "clip.mp4", { type: "video/mp4" });
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;

    fireEvent.change(fileInput, { target: { files: [videoFile] } });

    // Check video element in preview
    const video = await waitFor(() => {
      const v = document.querySelector("video");
      expect(v).toBeInTheDocument();
      return v;
    });

    expect(video).toHaveAttribute("src", "blob:http://localhost/clip.mp4");
    expect(screen.getByText(/clip.mp4/i)).toBeInTheDocument();

    // Submit
    fireEvent.click(screen.getByRole("button", { name: /add to gallery/i }));

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith({
        mediaType: "VIDEO",
        file: videoFile,
        caption: undefined,
      });
    });
  });

  it("rejects file exceeding size limit", async () => {
    render(
      <UploadMediaModal
        isOpen={true}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
        eventTitle="Dune Gathering"
      />
    );

    // 55MB file
    const oversizedFile = new File(["x"], "huge.png", { type: "image/png" });
    Object.defineProperty(oversizedFile, "size", { value: 55 * 1024 * 1024 });

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(fileInput, { target: { files: [oversizedFile] } });

    expect(await screen.findByText(/exceeds the 50MB limit/i)).toBeInTheDocument();
  });
});
