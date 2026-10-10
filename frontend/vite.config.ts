import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// In development (pnpm dev) the /api calls go to the backend on port 8081.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { "/api": "http://localhost:8081" },
  },
});
