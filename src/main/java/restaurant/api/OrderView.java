package restaurant.api;

import java.util.List;
import restaurant.order.OrderStatus;
import restaurant.order.ServiceType;
import restaurant.pricing.PriceBreakdown;

public record OrderView(String id, String customer, ServiceType serviceType, Integer tableNumber,
                        String comment, List<ItemView> items, PriceBreakdown price, OrderStatus status) {
    public OrderView {
        items = List.copyOf(items);
    }
}
