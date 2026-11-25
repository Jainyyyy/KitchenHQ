package com.rasoiroute.urgency;

import com.rasoiroute.model.Order;

/**
 * An order together with the numbers the screen needs to show:
 * its urgency score, the minutes left, and whether it is already late.
 */
public record ScoredOrder(Order order, int score, long minutesLeft, boolean late) {
}
