package restaurant.order;

public enum OrderStatus {
    NEW, PREPARING, READY;

    public boolean canChangeTo(OrderStatus next) {
        return (this == NEW && next == PREPARING)
                || (this == PREPARING && next == READY);
    }
}
