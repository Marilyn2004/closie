export async function api(path, body, signal) {
  const response = await fetch(`/api${path}`, {signal, ...(body === undefined ? {} : {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})});
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message || 'Closie couldn’t load this. Please try again.');
  }
  return response.json();
}
export const money = (value,currency='USD') => new Intl.NumberFormat('en-US',{style:'currency',currency}).format(value);
export const safeUrl = value => { try { const u=new URL(value); return u.protocol==='https:' ? u.href : null; } catch { return null; } };
export function readLocal(key, fallback) { try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; } }
export function writeLocal(key,value) { try { localStorage.setItem(key,JSON.stringify(value)); } catch { /* Private browsing/storage limits do not block shopping. */ } }
