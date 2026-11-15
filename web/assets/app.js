import { $, connect, message } from './shared.js';
import { startMenu } from './menu.js';
import { startOrders } from './orders.js';

document.addEventListener('click', event => {
  const button = event.target.closest('[data-step]');
  if (!button || button.disabled) return;
  const input = $('input', button.closest('.stepper'));
  if (!input.value || !input.validity.valid) input.value = input.min || '0';
  Number(button.dataset.step) > 0 ? input.stepUp() : input.stepDown();
  input.dispatchEvent(new Event('change', { bubbles: true }));
});

try {
  await connect();
  if (document.body.dataset.page === 'menu') await startMenu();
  else await startOrders(document.body.dataset.page === 'kitchen');
} catch (error) {
  $('#loading').hidden = true;
  message('#page-error', error.message + ' Refresh the page to try again.');
}
