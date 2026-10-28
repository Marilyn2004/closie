let session = localStorage.getItem("closie.studio.session");
if (!/^[a-f0-9]{64}$/.test(session || "")) {
  session = Array.from(crypto.getRandomValues(new Uint8Array(32)), (n) =>
    n.toString(16).padStart(2, "0"),
  ).join("");
  localStorage.setItem("closie.studio.session", session);
}
export async function studioApi(path, body, method) {
  const multipart = body instanceof FormData;
  const r = await fetch("/api/studio" + path, {
    method: method || (body === undefined ? "GET" : "POST"),
    headers: {
      "X-Studio-Session": session,
      ...(!multipart && body !== undefined
        ? { "Content-Type": "application/json" }
        : {}),
    },
    body:
      body === undefined ? undefined : multipart ? body : JSON.stringify(body),
  });
  if (!r.ok) {
    const data = await r.json().catch(() => ({}));
    throw new Error(
      data.message || data.detail || `Request failed (${r.status}).`,
    );
  }
  if (r.status === 204 || r.headers.get("content-length") === "0") return null;
  return r.text().then((t) => (t ? JSON.parse(t) : null));
}
export async function privateImage(path) {
  if (!/^\/api\/studio\/assets\/[a-f0-9-]{36}$/.test(path)) return "";
  const r = await fetch(path, { headers: { "X-Studio-Session": session } });
  if (!r.ok) throw new Error("Photo unavailable.");
  return URL.createObjectURL(await r.blob());
}
export const last = (doc) => doc?.revisions?.at(-1);
export const slots = {
  TOP: "Tops & knits",
  BOTTOM: "Bottoms",
  ONE_PIECE: "Dresses",
  OUTERWEAR: "Layers",
  SHOES: "Shoes",
  BAG: "Bags",
  ACCESSORY: "Extras",
};
