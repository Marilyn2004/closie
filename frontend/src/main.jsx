import React from "react";
import { createRoot } from "react-dom/client";
import StudioApp from "./studio/StudioApp";
async function start() {
  let studio = false,
    publicDemo = false;
  try {
    const r = await fetch("/api/studio/status");
    if (r.ok) {
      const status = await r.json();
      studio = status.enabled;
      publicDemo = status.publicDemo;
    }
  } catch {}
  const App =
    studio &&
    (publicDemo || !new URLSearchParams(location.search).has("retail"))
      ? StudioApp
      : (await import("./RetailApp")).default;
  createRoot(document.getElementById("root")).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  );
}
start();
