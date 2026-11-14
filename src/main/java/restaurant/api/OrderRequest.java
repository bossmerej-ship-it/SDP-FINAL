package restaurant.api;

import java.util.List;
import restaurant.order.ServiceType;

public record OrderRequest(String customer, ServiceType serviceType, Integer tableNumber,
                           String comment, String discount, List<ItemRequest> items) {
    public OrderRequest {
        if (customer == null || customer.isBlank() || serviceType == null
                || discount == null || items == null || items.isEmpty()
                || items.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Customer, service type, discount and order items are required.");
        }
        items = List.copyOf(items);
    }
}
