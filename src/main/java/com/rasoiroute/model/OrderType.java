package com.rasoiroute.model;

/**
 * The three kinds of order the kitchen takes.
 * Each type carries "type points" used later in the urgency score.
 * The numbers are documented assumptions and can be changed in this one place.
 */
public enum OrderType {
    CATERING(60),
    BAKERY(40),
    DAILY_FOOD(20);

    private final int typePoints;

    OrderType(int typePoints) {
        this.typePoints = typePoints;
    }

    public int getTypePoints() {
        return typePoints;
    }
}
