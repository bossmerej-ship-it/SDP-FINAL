import test from 'node:test';
import assert from 'node:assert/strict';
import { orderKey, clearOrderKey } from '../web/assets/submission.js';

test('an ambiguous request retry keeps its key even when browser storage is blocked', () => {
  Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, get() { throw new Error('Storage blocked'); } });
  clearOrderKey();
  const first = orderKey('customer=Aida&meal=burger');
  assert.equal(orderKey('customer=Aida&meal=burger'), first);
  assert.notEqual(orderKey('customer=Aida&meal=pizza'), first);
  clearOrderKey();
  assert.notEqual(orderKey('customer=Aida&meal=burger'), first);
});
