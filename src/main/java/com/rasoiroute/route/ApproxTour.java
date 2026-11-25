package com.rasoiroute.route;

/**
 * The answer of the 2-approximation: the route, plus the length of the spanning tree
 * it was made from. The tree length is what lets us CHECK the 2x promise on any input:
 * the best possible route is never shorter than the tree, so route <= 2 x tree
 * means route <= 2 x best.
 */
public record ApproxTour(Tour tour, double mstWeight) {

    /** A tiny allowance for rounding in decimal arithmetic. */
    private static final double ROUNDING = 1e-9;

    /** How many times longer the route is than the spanning tree (never above 2 for real map distances). */
    public double ratioToMst() {
        if (mstWeight == 0.0) {
            return 1.0;   // all stops are at the same place; nothing to compare
        }
        return tour.length() / mstWeight;
    }

    /** True when route length is at most twice the tree length. */
    public boolean withinGuarantee() {
        return tour.length() <= 2.0 * mstWeight * (1.0 + ROUNDING) + ROUNDING;
    }
}
