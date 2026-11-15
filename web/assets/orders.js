import { $, request, money, statuses, message } from './shared.js';

export async function startOrders(kitchen) {
  let loading = false, mutating = false, previous = '';
  const endpoint = kitchen ? '/api/kitchen' : '/api/orders';
  const shortId = id => '#' + id.slice(0, 8).toUpperCase();
  const service = order => order.serviceType === 'DINE_IN' ? `Dine in · Table ${order.tableNumber}` : 'Takeaway';
  function fill(card, order) {
    $('.order-number', card).textContent = 'ORDER ' + shortId(order.id);
    $('.order-customer', card).textContent = order.customer;
    $('.order-service', card).textContent = service(order);
    for (const item of order.items) {
      const row = document.createElement('li');
      const title = document.createElement('span'), price = document.createElement('span');
      title.textContent = `${item.quantity} × ${item.description}`;
      price.textContent = money(item.lineTotal); row.append(title, price);
      $('.order-meals', card).append(row);
    }
    if (order.comment) { $('.order-comment', card).textContent = order.comment; $('.order-comment', card).hidden = false; }
  }
  function renderOrders(orders) {
    const fragment = document.createDocumentFragment();
    if (!orders.length) {
      const box = document.createElement('div'); box.className = 'empty-state';
      const title = document.createElement('h2'); title.textContent = 'No orders yet';
      const text = document.createElement('p'); text.textContent = 'Place an order from the menu to track it here.';
      const link = document.createElement('a'); link.href = '/'; link.className = 'button primary'; link.textContent = 'View menu →';
      box.append(title, text, link); fragment.append(box);
    }
    for (const order of orders.toReversed ? orders.toReversed() : [...orders].reverse()) {
      const card = $('#order-template').content.cloneNode(true); fill(card, order);
      const badge = $('.status-badge', card); badge.textContent = statuses[order.status]; badge.classList.add(order.status.toLowerCase());
      const stage = ['NEW', 'PREPARING', 'READY'].indexOf(order.status);
      card.querySelectorAll('.order-progress li').forEach((step, index) => {
        step.classList.toggle('reached', index <= stage);
        if (index === stage) step.setAttribute('aria-current', 'step');
      });
      $('.order-price strong', card).textContent = money(order.price.total);
      if (Number(order.price.discount)) { $('.order-discount', card).textContent = `Discount: −${money(order.price.discount)}`; $('.order-discount', card).hidden = false; }
      if (order.id === new URLSearchParams(location.search).get('placed')) $('.order-card', card).classList.add('recent-order');
      fragment.append(card);
    }
    $('#orders-list').replaceChildren(fragment);
  }
  function renderKitchen(orders) {
    for (const column of document.querySelectorAll('.kitchen-column')) {
      const group = orders.filter(order => order.status === column.dataset.status);
      $('.column-count', column).textContent = group.length;
      const fragment = document.createDocumentFragment();
      for (const order of group) {
        const card = $('#kitchen-order-template').content.cloneNode(true); fill(card, order);
        const button = $('.status-button', card);
        if (order.status === 'READY') {
          button.remove();
          const done = document.createElement('p'); done.className = 'ready-label'; done.textContent = '✓ Ready to serve';
          $('.order-card', card).append(done);
        } else {
          const next = order.status === 'NEW' ? 'PREPARING' : 'READY';
          button.textContent = next === 'PREPARING' ? 'Start cooking →' : 'Mark ready ✓';
          button.setAttribute('aria-label', `${button.textContent}, order ${shortId(order.id)}`);
          button.addEventListener('click', async () => {
            if (mutating) return;
            mutating = true; button.disabled = true;
            try {
              await request(`/api/orders/${encodeURIComponent(order.id)}/status`, new URLSearchParams({ status: next }));
              previous = ''; await refresh(true);
              $('#kitchen-announcement').textContent = `Order ${shortId(order.id)}: ${statuses[next]}.`;
              const heading = $(`[data-status="${next}"] h2`); heading.tabIndex = -1; heading.focus({ preventScroll: true });
            } catch (error) { message('#page-error', error.message); button.disabled = false; }
            finally { mutating = false; }
          });
        }
        fragment.append(card);
      }
      if (!group.length) { const empty = document.createElement('p'); empty.className = 'column-empty'; empty.textContent = 'No orders'; fragment.append(empty); }
      $('.column-orders', column).replaceChildren(fragment);
    }
    $('#kitchen-board').hidden = false;
  }
  async function refresh(force = false) {
    if (loading || mutating && !force) return;
    loading = true;
    const button = $('#refresh-button'); button.disabled = true;
    try {
      const orders = await request(endpoint);
      const snapshot = JSON.stringify(orders);
      if (snapshot !== previous) { kitchen ? renderKitchen(orders) : renderOrders(orders); previous = snapshot; }
      message('#page-error');
    } catch (error) { message('#page-error', error.message + ' Select Refresh to try again.'); }
    finally { loading = false; button.disabled = false; $('#loading').hidden = true; }
  }
  $('#refresh-button').addEventListener('click', () => refresh());
  document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
  await refresh();
  setInterval(() => { if (!document.hidden && !document.activeElement.closest('.column-orders')) refresh(); }, 5000);
}
