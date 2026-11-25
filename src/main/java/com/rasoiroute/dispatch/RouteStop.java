package com.rasoiroute.dispatch;

import java.util.List;

/**
 * One stop on the delivery route.
 *
 * @param locality  name of the place (the first stop is always the kitchen)
 * @param latitude  for drawing on the map
 * @param longitude for drawing on the map
 * @param legKm     distance from the previous stop (0 for the kitchen at the start)
 * @param orderIds  the orders to hand over here, earliest due first
 */
public record RouteStop(String locality, double latitude, double longitude, double legKm, List<String> orderIds) {
}
