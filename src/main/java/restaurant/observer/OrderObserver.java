package restaurant.observer;

import restaurant.api.OrderView;

public interface OrderObserver {
    void onOrderChanged(OrderView order);
}
