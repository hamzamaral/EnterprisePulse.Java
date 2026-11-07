package dev.enterprisepulse.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Quantities carry 3 decimals, unit costs 4. */
public final class StockMath {
    private StockMath() {
    }

    /** A non-positive on-hand quantity carries no value, so the receipt cost becomes the new average. */
    public static BigDecimal weightedAverageCost(BigDecimal onHand, BigDecimal average, BigDecimal received, BigDecimal cost) {
        if (onHand.signum() <= 0) return cost.setScale(4, RoundingMode.HALF_UP);
        BigDecimal total = onHand.add(received);
        if (total.signum() <= 0) return average;
        return onHand.multiply(average).add(received.multiply(cost)).divide(total, 4, RoundingMode.HALF_UP);
    }

    public static boolean crossedBelowMinimum(BigDecimal before, BigDecimal after, BigDecimal minimum) {
        return minimum.signum() > 0 && before.compareTo(minimum) >= 0 && after.compareTo(minimum) < 0;
    }

    public static boolean isWhole(BigDecimal value) {
        return value.stripTrailingZeros().scale() <= 0;
    }
}
