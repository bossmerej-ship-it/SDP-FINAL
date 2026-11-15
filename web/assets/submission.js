import { readStorage, writeStorage } from './shared.js';

// Keep uncertain submissions in memory too: storage may be blocked by the browser.
let pending = readStorage('restaurant-pending', {});

export function orderKey(body) {
  if (pending?.body !== body || typeof pending?.key !== 'string') {
    pending = { body, key: crypto.randomUUID() };
  }
  writeStorage('restaurant-pending', pending);
  return pending.key;
}

export function clearOrderKey() {
  pending = {};
  writeStorage('restaurant-pending', pending);
}
