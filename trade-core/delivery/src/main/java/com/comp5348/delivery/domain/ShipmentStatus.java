package com.comp5348.delivery.domain;

public enum ShipmentStatus {
    REQUESTED, PICKUP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, LOST;

    public boolean isTerminal() {
        return this == DELIVERED || this == LOST;
    }

    public ShipmentStatus next() {
        return switch (this) {
            case REQUESTED -> PICKUP;
            case PICKUP -> IN_TRANSIT;
            case IN_TRANSIT -> OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> DELIVERED;
            default -> this;
        };
    }
}
