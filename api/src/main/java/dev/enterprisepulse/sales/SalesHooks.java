package dev.enterprisepulse.sales;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.resource.ResourceHook;
import org.springframework.stereotype.Component;

public final class SalesHooks {
    private SalesHooks() {}

    public abstract static class Managed implements ResourceHook {
        private final String resource;
        private final String message;

        protected Managed(String resource, String message) {
            this.resource = resource;
            this.message = message;
        }

        @Override public String resource() { return resource; }
        @Override public void beforeSave(Object entity, boolean isNew) { throw AppException.conflict(message); }
        @Override public void beforeDelete(Object entity) { throw AppException.conflict(message); }
    }

    @Component public static class Quotes extends Managed { public Quotes() { super("sales-quotes", "Teklifler Satış Teklifi ekranından yönetilir"); } }
    @Component public static class QuoteLines extends Managed { public QuoteLines() { super("sales-quote-lines", "Teklif satırları teklif formundan yönetilir"); } }
    @Component public static class Orders extends Managed { public Orders() { super("sales-orders", "Siparişler Satış Siparişi ekranından yönetilir"); } }
    @Component public static class OrderLines extends Managed { public OrderLines() { super("sales-order-lines", "Sipariş satırları sipariş formundan yönetilir"); } }
    @Component public static class Reservations extends Managed { public Reservations() { super("stock-reservations", "Stok rezervasyonu sipariş onayıyla oluşur"); } }
    @Component public static class Deliveries extends Managed { public Deliveries() { super("delivery-notes", "İrsaliyeler sevkiyat ekranından kesilir"); } }
    @Component public static class DeliveryLines extends Managed { public DeliveryLines() { super("delivery-note-lines", "İrsaliye satırları sevkiyat ekranından yönetilir"); } }
    @Component public static class Invoices extends Managed { public Invoices() { super("sales-invoices", "Satış faturaları fatura ekranından kesilir"); } }
    @Component public static class InvoiceLines extends Managed { public InvoiceLines() { super("sales-invoice-lines", "Fatura satırları fatura formundan yönetilir"); } }
    @Component public static class Returns extends Managed { public Returns() { super("sales-returns", "İadeler satış iadesi ekranından kaydedilir"); } }
    @Component public static class ReturnLines extends Managed { public ReturnLines() { super("sales-return-lines", "İade satırları iade formundan yönetilir"); } }
}
