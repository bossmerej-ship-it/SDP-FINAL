import { $, request, money, names, message, readStorage, writeStorage } from './shared.js';
import { orderKey, clearOrderKey } from './submission.js';

export async function startMenu() {
  const menu = await request('/api/menu');
  const prices = Object.fromEntries(menu.meals.map(meal => [meal.code, Number(meal.price)]));
  const extras = Object.fromEntries(menu.toppings.map(topping => [topping.code, Number(topping.price)]));
  const form = $('#checkout-form');
  const submit = $('#checkout-button');
  const saved = readStorage('restaurant-draft', {});
  let cart = Array.isArray(saved.cart) ? saved.cart.filter(item =>
    item && Object.hasOwn(names, item.meal) && ['cheese', 'sauce', 'quantity'].every(key => Number.isInteger(item[key])) &&
    item.quantity >= 1 && item.quantity <= 99 && item.cheese >= 0 && item.cheese <= 20 &&
    item.sauce >= 0 && item.sauce <= 20).slice(0, 30) : [];
  let creating = false, quoting = false, quoteRevision = 0;
  if (typeof saved.customer === 'string') form.elements.customer.value = saved.customer.slice(0, 100);
  if (typeof saved.comment === 'string') form.elements.comment.value = saved.comment.slice(0, 500);
  form.elements.serviceType.value = saved.serviceType === 'DINE_IN' ? 'DINE_IN' : 'TAKEAWAY';
  if (Number.isInteger(saved.tableNumber)) form.elements.tableNumber.value = saved.tableNumber;
  form.elements.discount.checked = saved.discount === 'student';

  function save() {
    writeStorage('restaurant-draft', {
      cart, customer: form.elements.customer.value, comment: form.elements.comment.value,
      serviceType: form.elements.serviceType.value,
      tableNumber: Number(form.elements.tableNumber.value) || null,
      discount: form.elements.discount.checked ? 'student' : 'regular'
    });
  }
  function controls() {
    submit.disabled = creating || quoting || !cart.length || Boolean($('#cart-items input:invalid'));
    for (const control of document.querySelectorAll('#cart-items button, #cart-items input')) control.disabled = creating;
    for (const card of document.querySelectorAll('[data-meal]')) {
      $('fieldset', card).disabled = creating;
      $('.add-button', card).disabled = creating || !card.checkValidity();
    }
  }
  function service() {
    const dineIn = form.elements.serviceType.value === 'DINE_IN';
    $('#table-field').hidden = !dineIn;
    form.elements.tableNumber.disabled = !dineIn;
    form.elements.tableNumber.required = dineIn;
  }
  function body(preview = false) {
    const data = new URLSearchParams();
    data.set('customer', preview ? 'Предпросмотр' : form.elements.customer.value.trim());
    data.set('serviceType', preview ? 'TAKEAWAY' : form.elements.serviceType.value);
    if (!preview && form.elements.serviceType.value === 'DINE_IN') data.set('tableNumber', form.elements.tableNumber.value);
    data.set('comment', preview ? '' : form.elements.comment.value.trim());
    data.set('discount', form.elements.discount.checked ? 'student' : 'regular');
    for (const item of cart) for (const key of ['meal', 'quantity', 'cheese', 'sauce']) data.append(key, item[key]);
    return data;
  }
  function setTotals(price) {
    $('#subtotal').textContent = money(price.subtotal);
    $('#discount-total').textContent = (Number(price.discount) ? '−' : '') + money(price.discount);
    $('#total').textContent = money(price.total);
  }
  async function quote() {
    const revision = ++quoteRevision;
    message('#checkout-error');
    if (!cart.length) { quoting = false; setTotals({ subtotal: 0, discount: 0, total: 0 }); controls(); return; }
    quoting = true; controls();
    $('.totals').setAttribute('aria-busy', 'true');
    try {
      const price = await request('/api/orders/quote', body(true));
      if (revision !== quoteRevision) return;
      setTotals(price); quoting = false;
    } catch (error) {
      if (revision !== quoteRevision) return;
      message('#checkout-error', error.message + ' Измени заказ или обнови страницу для перерасчёта.');
      // Submission stays disabled until a fresh server quote succeeds.
      quoting = true;
    } finally {
      if (revision === quoteRevision) { $('.totals').setAttribute('aria-busy', 'false'); controls(); }
    }
  }
  function renderCart() {
    const focus = document.activeElement?.dataset.focus;
    const fragment = document.createDocumentFragment();
    cart.forEach((item, index) => {
      const row = $('#cart-item-template').content.cloneNode(true);
      $('h3', row).textContent = names[item.meal];
      $('.item-toppings', row).textContent = [item.cheese ? `Сыр ×${item.cheese}` : '', item.sauce ? `Соус ×${item.sauce}` : ''].filter(Boolean).join(', ') || 'Без добавок';
      $('.line-price', row).textContent = money((prices[item.meal] + item.cheese * extras.cheese + item.sauce * extras.sauce) * item.quantity);
      const input = $('input', row);
      input.value = item.quantity; input.dataset.focus = `quantity-${index}`;
      input.setAttribute('aria-label', `Количество: ${names[item.meal]}, позиция ${index + 1}`);
      input.addEventListener('change', () => {
        if (!input.reportValidity()) { controls(); return; }
        cart[index].quantity = Number(input.value); save(); renderCart(); quote();
      });
      const buttons = row.querySelectorAll('[data-step]');
      buttons[0].dataset.focus = `decrease-${index}`;
      buttons[1].dataset.focus = `increase-${index}`;
      buttons[0].setAttribute('aria-label', `Уменьшить количество: позиция ${index + 1}`);
      buttons[1].setAttribute('aria-label', `Увеличить количество: позиция ${index + 1}`);
      $('.remove-item', row).setAttribute('aria-label', `Убрать ${names[item.meal]}, позиция ${index + 1}`);
      $('.remove-item', row).addEventListener('click', () => {
        cart.splice(index, 1); save(); renderCart(); quote();
        $('#cart-title').focus({ preventScroll: true });
        $('#cart-announcement').textContent = 'Позиция удалена из заказа.';
      });
      fragment.append(row);
    });
    if (!cart.length) {
      const empty = document.createElement('div'); empty.className = 'empty-cart';
      const title = document.createElement('h3'); title.textContent = 'Здесь будет что-то вкусное';
      const text = document.createElement('p'); text.textContent = 'Добавь блюдо из меню, а затем оформи заказ.';
      empty.append(title, text); fragment.append(empty);
    }
    $('#cart-items').replaceChildren(fragment);
    const count = cart.reduce((sum, item) => sum + item.quantity, 0);
    $('#cart-count').textContent = count ? `Блюд в заказе: ${count}` : 'Пока пусто';
    $('#mobile-count').textContent = count;
    if (focus) $(`[data-focus="${focus}"]`)?.focus({ preventScroll: true });
    controls();
  }

  for (const card of document.querySelectorAll('[data-meal]')) {
    const meal = card.dataset.meal;
    $('[data-base-price]', card).textContent = money(prices[meal]);
    for (const topping of card.querySelectorAll('[data-topping-price]')) topping.textContent = '+' + money(extras[topping.dataset.toppingPrice]);
    function cardPrice() {
      const valid = card.checkValidity();
      $('[data-add-price]', card).textContent = money(prices[meal] + Number(card.elements.cheese.value) * extras.cheese + Number(card.elements.sauce.value) * extras.sauce);
      $('.add-button', card).disabled = !valid;
    }
    card.addEventListener('change', cardPrice);
    card.addEventListener('submit', event => {
      event.preventDefault();
      if (!card.reportValidity()) return;
      const cheese = Number(card.elements.cheese.value), sauce = Number(card.elements.sauce.value);
      const same = cart.find(item => item.meal === meal && item.cheese === cheese && item.sauce === sauce);
      if (same && same.quantity === 99 || !same && cart.length === 30) {
        message('#page-error', 'Этот заказ уже большой. Максимум 30 позиций и 99 блюд в каждой.'); return;
      }
      if (same) same.quantity++;
      else cart.push({ meal, cheese, sauce, quantity: 1 });
      message('#page-error'); save(); renderCart(); quote();
      $('#cart-announcement').textContent = `В заказ добавлено: ${names[meal]}. Блюд в заказе: ${cart.reduce((sum, item) => sum + item.quantity, 0)}.`;
    });
    cardPrice();
  }
  form.addEventListener('input', () => { form.elements.customer.setCustomValidity(''); save(); });
  form.addEventListener('change', event => { service(); save(); if (event.target.name === 'discount') quote(); });
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (creating || quoting || !cart.length) return;
    if (!form.elements.customer.value.trim()) {
      form.elements.customer.setCustomValidity('Введи имя.'); form.reportValidity(); return;
    }
    if (!form.reportValidity()) return;
    creating = true; controls(); message('#checkout-error');
    $('span', submit).textContent = 'Отправляем…';
    const data = body(), serialized = data.toString();
    const key = orderKey(serialized);
    try {
      const order = await request('/api/orders', data, key);
      cart = []; save(); clearOrderKey();
      window.location.assign('/orders?placed=' + encodeURIComponent(order.id));
    } catch (error) {
      message('#checkout-error', error.message); creating = false; controls();
      $('span', submit).textContent = 'Оформить заказ';
    }
  });
  service(); renderCart(); await quote();
  $('#loading').hidden = true; $('#menu-grid').hidden = false;
}
