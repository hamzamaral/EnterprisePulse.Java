package dev.enterprisepulse.finance;

import dev.enterprisepulse.sales.SalesHooks;
import org.springframework.stereotype.Component;

public final class FinanceHooks {
    private FinanceHooks() {}

    @Component public static class Transactions extends SalesHooks.Managed { public Transactions() { super("treasury-transactions", "Kasa hareketleri tahsilat, ödeme ve çek işlemleriyle oluşur"); } }
    @Component public static class Payments extends SalesHooks.Managed { public Payments() { super("partner-payments", "Tahsilat ve ödemeler finans ekranından kaydedilir"); } }
    @Component public static class Allocations extends SalesHooks.Managed { public Allocations() { super("payment-allocations", "Mahsuplar tahsilat veya ödeme belgesiyle yazılır"); } }
    @Component public static class Cheques extends SalesHooks.Managed { public Cheques() { super("cheques", "Çek ve senetler tahsilat veya ödeme sırasında oluşur"); } }
    @Component public static class Events extends SalesHooks.Managed { public Events() { super("cheque-events", "Çek hareketleri portföy ekranından yazılır"); } }
}
