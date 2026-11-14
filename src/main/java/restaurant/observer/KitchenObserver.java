package restaurant.observer;

import java.util.ArrayList;
import java.util.List;
import restaurant.api.OrderView;
import restaurant.order.OrderStatus;

public final class KitchenObserver implements OrderObserver {
    private final List<String> messages = new ArrayList<>();

    @Override
    public synchronized void onOrderChanged(OrderView order) {
        if (order.status() == OrderStatus.NEW) {
            messages.add("Kitchen: new order for " + order.customer() + ", " + order.items().size() + " item(s).");
        }
    }

    public synchronized List<String> messages() { return List.copyOf(messages); }
}
