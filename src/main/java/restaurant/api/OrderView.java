package restaurant.api;

import java.util.List;
import restaurant.order.OrderStatus;
import restaurant.order.ServiceType;
import restaurant.pricing.PriceBreakdown;

// Immutable snapshot. Clients cannot change the service's stored state.
public record OrderView(String id, String customer, ServiceType serviceType, Integer tableNumber,
                        String comment, List<ItemView> items, PriceBreakdown price, OrderStatus status) {
    public OrderView {
        items = List.copyOf(items);
    }
}
