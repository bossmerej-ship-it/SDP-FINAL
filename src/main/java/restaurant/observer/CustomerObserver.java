package restaurant.observer;

import java.util.ArrayList;
import java.util.List;
import restaurant.api.OrderView;

public final class CustomerObserver implements OrderObserver {
    private final String customer;
    private final List<String> messages = new ArrayList<>();

    public CustomerObserver(String customer) {
        this.customer = java.util.Objects.requireNonNull(customer);
    }

    @Override
    public synchronized void onOrderChanged(OrderView order) {
        if (customer.equals(order.customer())) {
            messages.add(customer + ": order status is " + order.status() + ".");
        }
    }

    public synchronized List<String> messages() { return List.copyOf(messages); }
}
