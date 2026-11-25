package com.rasoiroute.dispatch;

import com.rasoiroute.model.Order;
import com.rasoiroute.urgency.ScoredOrder;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the kitchen needs for one day.
 *
 * @param day            the day being planned
 * @param ordersDue      how many orders are due that day
 * @param deliveryCount  how many of them are deliveries
 * @param pickupCount    how many are customer pickups
 * @param lateCount      how many are already past their due time
 * @param topUrgent      the most urgent orders, most urgent first
 * @param pickups        the pickup orders, earliest due first (they need no route)
 * @param route          the delivery route
 * @param problems       orders that could not be planned, each with a plain reason
 */
public record DispatchPlan(
        LocalDate day,
        int ordersDue,
        int deliveryCount,
        int pickupCount,
        int lateCount,
        List<ScoredOrder> topUrgent,
        List<Order> pickups,
        RouteSummary route,
        List<String> problems) {
}
