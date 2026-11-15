let csrfToken = '';
const numberFormat = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 });
export const money = value => numberFormat.format(Number(value)) + ' ₸';
export const names = { burger: 'Burger', pizza: 'Pizza' };
export const statuses = { NEW: 'Accepted', PREPARING: 'Preparing', READY: 'Ready' };
export const $ = (selector, root = document) => root.querySelector(selector);

export async function request(path, form, idempotencyKey) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 12000);
  const headers = {};
  if (form) {
    headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
    headers['X-CSRF-Token'] = csrfToken;
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
  }
  try {
    const response = await fetch(path, {
      method: form ? 'POST' : 'GET', body: form, headers,
      credentials: 'same-origin', cache: 'no-store', signal: controller.signal
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || 'Something went wrong. Try again.');
    return data;
  } catch (error) {
    if (error instanceof TypeError || error.name === 'AbortError') {
      throw new Error('The server did not respond. Check your connection and try again.');
    }
    throw error;
  } finally { clearTimeout(timeout); }
}

export async function connect() {
  csrfToken = (await request('/api/session')).csrfToken;
}

export function message(selector, text = '') {
  const element = $(selector);
  element.textContent = text;
  element.hidden = !text;
}

export function readStorage(key, fallback) {
  try { return JSON.parse(sessionStorage.getItem(key)) ?? fallback; }
  catch { return fallback; }
}

export function writeStorage(key, value) {
  try { sessionStorage.setItem(key, JSON.stringify(value)); }
  catch { /* The ordering flow still works when browser storage is unavailable. */ }
}
