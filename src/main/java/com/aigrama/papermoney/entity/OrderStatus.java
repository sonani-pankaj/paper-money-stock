package com.aigrama.papermoney.entity;

/**
 * Processing status of an order.
 */
public enum OrderStatus {
    NEW,
    PARTIALLY_FILLED,
    FILLED,
    CANCELED,
    REJECTED
}
