import React from "react";
import { createRoot } from "react-dom/client";
import StudioApp from "./studio/StudioApp";
async function start() {
  let studio = false;
  try {
    const r = await fetch("/api/studio/status");
    studio = r.ok && (await r.json()).enabled;
  } catch {}
  const App =
    studio && !new URLSearchParams(location.search).has("retail")
      ? StudioApp
      : (await import("./RetailApp")).default;
  createRoot(document.getElementById("root")).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  );
}
start();
