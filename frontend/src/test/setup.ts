import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

afterEach(() => {
  cleanup();
});

if (!window.URL.createObjectURL) {
  window.URL.createObjectURL = (file: any) => `blob:http://localhost/${file?.name || "blob"}`;
}
if (!window.URL.revokeObjectURL) {
  window.URL.revokeObjectURL = () => {};
}

