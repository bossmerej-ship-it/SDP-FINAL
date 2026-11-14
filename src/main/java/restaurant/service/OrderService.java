package restaurant.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import restaurant.api.ItemView;
import restaurant.api.OrderView;
import restaurant.observer.OrderObserver;
import restaurant.order.Order;
import restaurant.order.OrderItem;
import restaurant.order.OrderStatus;
import restaurant.pricing.PriceBreakdown;

// Observer Subject and owner of the order lifecycle.
// Synchronization keeps state and event order consistent across callers.
public final class OrderService {
    private static final System.Logger LOG = System.getLogger(OrderService.class.getName());
    private final Map<String, OrderView> orders = new LinkedHashMap<>();
    private final Set<OrderObserver> observers = new LinkedHashSet<>();
    private boolean notifying;

    public synchronized void subscribe(OrderObserver observer) {
        observers.add(java.util.Objects.requireNonNull(observer));
    }

    public synchronized void unsubscribe(OrderObserver observer) {
        observers.remove(observer);
    }

    public synchronized OrderView submit(Order order, PriceBreakdown price) {
        checkNotInCallback();
        List<ItemView> items = new ArrayList<>();
        for (OrderItem item : order.items()) {
            items.add(new ItemView(item.meal().getDescription(), item.quantity(),
                    item.meal().getPrice(), item.lineTotal()));
        }
        OrderView view = new OrderView(UUID.randomUUID().toString(), order.customer(),
                order.serviceType(), order.tableNumber(), order.comment(), items, price, OrderStatus.NEW);
        orders.put(view.id(), view);
        notifyObservers(view);
        return view;
    }

    public synchronized OrderView getOrder(String id) {
        OrderView order = orders.get(id);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + id);
        }
        return order;
    }

    public synchronized List<OrderView> listOrders() {
        return List.copyOf(orders.values());
    }

    public synchronized OrderView updateStatus(String id, OrderStatus next) {
        checkNotInCallback();
        OrderView current = getOrder(id);
        if (next == current.status()) {
            return current; // A repeated update produces no duplicate event.
        }
        if (!current.status().canChangeTo(next)) {
            throw new IllegalArgumentException("Cannot change " + current.status() + " to " + next + ".");
        }
        OrderView updated = new OrderView(current.id(), current.customer(), current.serviceType(),
                current.tableNumber(), current.comment(), current.items(), current.price(), next);
        orders.put(id, updated);
        notifyObservers(updated);
        return updated;
    }

    private void notifyObservers(OrderView order) {
        notifying = true;
        try {
            for (OrderObserver observer : List.copyOf(observers)) {
                try {
                    observer.onOrderChanged(order);
                } catch (RuntimeException error) {
                    // The order is already stored. Continue with the remaining subscribers.
                    LOG.log(System.Logger.Level.WARNING,
                            "Order observer failed: " + error.getMessage());
                }
            }
        } finally {
            notifying = false;
        }
    }

    private void checkNotInCallback() {
        if (notifying) {
            throw new IllegalStateException("Observers cannot create orders or change their status inside a callback.");
        }
    }
}
