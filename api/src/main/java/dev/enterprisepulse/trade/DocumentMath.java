package dev.enterprisepulse.trade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Line and document totals. Each line part is rounded to kuruş first and the document sums the rounded parts,
 * so the printed lines always add up to the printed totals.
 */
public final class DocumentMath {
    private DocumentMath() {
    }

    public record LineInput(BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountRate, BigDecimal vatRate) {
    }

    public record LineAmounts(BigDecimal gross, BigDecimal discount, BigDecimal net, BigDecimal vat, BigDecimal total) {
    }

    public record DocumentTotals(BigDecimal subtotal, BigDecimal discountTotal, BigDecimal vatTotal, BigDecimal grandTotal) {
    }

    public static BigDecimal round2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal round4(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    public static LineAmounts line(LineInput line) {
        BigDecimal gross = round2(line.quantity().multiply(line.unitPrice()));
        BigDecimal discount = round2(gross.multiply(line.discountRate()).movePointLeft(2));
        BigDecimal net = gross.subtract(discount);
        BigDecimal vat = round2(net.multiply(line.vatRate()).movePointLeft(2));
        return new LineAmounts(gross, discount, net, vat, net.add(vat));
    }

    public static DocumentTotals document(List<LineInput> lines) {
        BigDecimal sub = BigDecimal.ZERO;
        BigDecimal disc = BigDecimal.ZERO;
        BigDecimal vat = BigDecimal.ZERO;
        for (LineInput line : lines) {
            LineAmounts amounts = line(line);
            sub = sub.add(amounts.gross());
            disc = disc.add(amounts.discount());
            vat = vat.add(amounts.vat());
        }
        return new DocumentTotals(sub, disc, vat, sub.subtract(disc).add(vat));
    }

    public static BigDecimal toBase(BigDecimal amount, BigDecimal rate) {
        return round2(amount.multiply(rate));
    }

    /** Discounted unit price in the base currency: the cost a receipt books into stock. */
    public static BigDecimal netUnitCostBase(BigDecimal unitPrice, BigDecimal discountRate, BigDecimal rate) {
        return round4(unitPrice.multiply(BigDecimal.valueOf(100).subtract(discountRate)).movePointLeft(2).multiply(rate));
    }
}
