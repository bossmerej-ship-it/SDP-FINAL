let csrfToken = '';
const numberFormat = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 });
export const money = value => numberFormat.format(Number(value)) + ' ₸';
export const names = { burger: 'Бургер', pizza: 'Пицца' };
export const statuses = { NEW: 'Принят', PREPARING: 'Готовится', READY: 'Готов' };
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
    if (!response.ok) throw new Error(data.error || 'Не удалось выполнить действие. Попробуй ещё раз.');
    return data;
  } catch (error) {
    if (error instanceof TypeError || error.name === 'AbortError') {
      throw new Error('Нет ответа от ресторана. Проверь соединение и попробуй ещё раз.');
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

export function describe(text) {
  return text.replaceAll('Burger', 'Бургер').replaceAll('Pizza', 'Пицца')
    .replaceAll('cheese', 'сыр').replaceAll('sauce', 'соус');
}
