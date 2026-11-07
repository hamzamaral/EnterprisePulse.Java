package dev.enterprisepulse.finance;

import dev.enterprisepulse.data.generated.InvoiceStatus;
import dev.enterprisepulse.data.generated.PaymentStatus;
import dev.enterprisepulse.data.generated.PurchaseInvoice;
import dev.enterprisepulse.data.generated.SalesInvoice;
import dev.enterprisepulse.trade.DocumentMath;
import java.math.BigDecimal;

/** paidAmount covers collections and sales returns. */
public final class InvoicePayments {
    private InvoicePayments() {}

    public static void apply(SalesInvoice invoice, BigDecimal delta) {
        invoice.paidAmount = DocumentMath.round2(invoice.paidAmount.add(delta));
        refresh(invoice.status, invoice.paidAmount, invoice.grandTotal, invoice);
    }

    public static void apply(PurchaseInvoice invoice, BigDecimal delta) {
        invoice.paidAmount = DocumentMath.round2(invoice.paidAmount.add(delta));
        if (invoice.status != InvoiceStatus.ISSUED) return;
        if (invoice.paidAmount.signum() <= 0) {
            invoice.paidAmount = BigDecimal.ZERO;
            invoice.paymentStatus = PaymentStatus.UNPAID;
        } else if (invoice.paidAmount.add(new BigDecimal("0.004")).compareTo(invoice.grandTotal) >= 0) invoice.paymentStatus = PaymentStatus.PAID;
        else invoice.paymentStatus = PaymentStatus.PARTIALLY_PAID;
    }

    private static void refresh(InvoiceStatus status, BigDecimal paid, BigDecimal grand, SalesInvoice invoice) {
        if (status != InvoiceStatus.ISSUED) return;
        if (paid.signum() <= 0) {
            invoice.paidAmount = BigDecimal.ZERO;
            invoice.paymentStatus = PaymentStatus.UNPAID;
        } else if (paid.add(new BigDecimal("0.004")).compareTo(grand) >= 0) invoice.paymentStatus = PaymentStatus.PAID;
        else invoice.paymentStatus = PaymentStatus.PARTIALLY_PAID;
    }
}
