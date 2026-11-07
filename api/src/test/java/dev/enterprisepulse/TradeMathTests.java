package dev.enterprisepulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.enterprisepulse.inventory.StockMath;
import dev.enterprisepulse.trade.DocumentMath;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradeMathTests {
    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    @Test
    void weightedAverageReplacesCostWhenOnHandIsNotPositive() {
        assertEquals(n("12.3457"), StockMath.weightedAverageCost(n("0"), n("99"), n("4"), n("12.34567")));
        assertEquals(n("8.0000"), StockMath.weightedAverageCost(n("-2"), n("3"), n("5"), n("8")));
    }

    @Test
    void weightedAverageBlendsExistingStock() {
        assertEquals(n("11.5000"), StockMath.weightedAverageCost(n("10"), n("10"), n("10"), n("13")));
        assertEquals(n("10.3333"), StockMath.weightedAverageCost(n("2"), n("10"), n("1"), n("11")));
    }

    @Test
    void minimumIsCrossedOnlyDownward() {
        assertTrue(StockMath.crossedBelowMinimum(n("5"), n("4.999"), n("5")));
        assertFalse(StockMath.crossedBelowMinimum(n("4"), n("3"), n("5")));
        assertFalse(StockMath.crossedBelowMinimum(n("6"), n("5"), n("5")));
        assertFalse(StockMath.crossedBelowMinimum(n("6"), n("1"), n("0")));
    }

    @Test
    void documentRoundsEachLineBeforeSumming() {
        DocumentMath.LineAmounts line = DocumentMath.line(new DocumentMath.LineInput(n("3"), n("10.005"), n("10"), n("20")));
        assertEquals(n("30.02"), line.gross());
        assertEquals(n("3.00"), line.discount());
        assertEquals(n("27.02"), line.net());
        assertEquals(n("5.40"), line.vat());
        assertEquals(n("32.42"), line.total());

        DocumentMath.DocumentTotals doc = DocumentMath.document(List.of(
                new DocumentMath.LineInput(n("1"), n("10.004"), n("0"), n("20")),
                new DocumentMath.LineInput(n("1"), n("10.004"), n("0"), n("20"))));
        assertEquals(n("20.00"), doc.subtotal());
        assertEquals(n("4.00"), doc.vatTotal());
        assertEquals(n("24.00"), doc.grandTotal());
    }

    @Test
    void netUnitCostIsTheDiscountedBasePrice() {
        assertEquals(n("436.3200"), DocumentMath.netUnitCostBase(n("10"), n("10"), n("48.48")));
        assertEquals(n("216.00"), DocumentMath.toBase(n("4.5"), n("48")));
    }
}
