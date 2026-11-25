package com.rasoiroute.urgency;

import com.rasoiroute.model.Order;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Gives every order an urgency score. A bigger number means "send this sooner".
 *
 *   urgency score = type points + time points
 *
 *   type points : catering 60, bakery 40, daily food 20 (kept in OrderType, one place to change)
 *   time points : 120 minus the minutes left, never below 0 and never above 120
 *
 * So an order due in 2 hours or more gets 0 time points, an order due in 30 minutes gets 90,
 * and an order that is already late gets the full 120.
 * These numbers are documented assumptions, not laws of nature.
 */
public final class UrgencyScorer {

    /** Orders due further away than this many minutes get no time points yet. */
    public static final int TIME_WINDOW_MINUTES = 120;

    private UrgencyScorer() {
        // static helper only
    }

    /** Minutes from 'now' until the order is due. Negative means it is already overdue. */
    public static long minutesLeft(Order order, LocalDateTime now) {
        requireInputs(order, now);
        return Duration.between(now, order.dueAt()).toMinutes();
    }

    /** True only when the due time has already passed. */
    public static boolean isLate(Order order, LocalDateTime now) {
        requireInputs(order, now);
        return order.dueAt().isBefore(now);
    }

    /** Time points for a given number of minutes left: 120 - minutes, kept between 0 and 120. */
    public static int timePoints(long minutesLeft) {
        long points = TIME_WINDOW_MINUTES - minutesLeft;
        if (points < 0) {
            return 0;
        }
        if (points > TIME_WINDOW_MINUTES) {
            return TIME_WINDOW_MINUTES;
        }
        return (int) points;
    }

    /** The urgency score: type points + time points. */
    public static int score(Order order, LocalDateTime now) {
        requireInputs(order, now);
        return order.type().getTypePoints() + timePoints(minutesLeft(order, now));
    }

    private static void requireInputs(Order order, LocalDateTime now) {
        if (order == null || now == null) {
            throw new IllegalArgumentException("order and current time are required");
        }
    }
}
