package dev.enterprisepulse.purchasing;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.generated.PurchaseRequest;
import dev.enterprisepulse.resource.ResourceHook;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Component;

public final class PurchasingHooks {
    private PurchasingHooks() {}

    public abstract static class ManagedRecordHook implements ResourceHook {
        private final String resource;
        private final String message;

        protected ManagedRecordHook(String resource, String message) {
            this.resource = resource;
            this.message = message;
        }

        @Override
        public String resource() {
            return resource;
        }

        @Override
        public void beforeSave(Object entity, boolean isNew) {
            throw AppException.conflict(message);
        }

        @Override
        public void beforeDelete(Object entity) {
            throw AppException.conflict(message);
        }
    }

    @Component
    public static class RequestsHook extends ManagedRecordHook {
        public RequestsHook() {
            super("purchase-requests", "Talepler Satın Alma Talebi ekranından yönetilir");
        }

        @Override
        public Predicate scope(CriteriaBuilder cb, AbstractQuery<?> query, Root<?> root) {
            if (CurrentUser.get().has("purchasing:read") || !PurchaseRequest.class.equals(root.getJavaType())) return null;
            return cb.equal(root.get("requestedById"), CurrentUser.get().userId);
        }
    }

    @Component
    public static class RequestLinesHook extends ManagedRecordHook {
        public RequestLinesHook() {
            super("purchase-request-lines", "Talep satırları talep formundan yönetilir");
        }
    }

    @Component
    public static class QuotesHook extends ManagedRecordHook {
        public QuotesHook() {
            super("supplier-quotes", "Teklifler Tedarikçi Teklifi ekranından yönetilir");
        }
    }

    @Component
    public static class QuoteLinesHook extends ManagedRecordHook {
        public QuoteLinesHook() {
            super("supplier-quote-lines", "Teklif satırları teklif formundan yönetilir");
        }
    }

    @Component
    public static class OrdersHook extends ManagedRecordHook {
        public OrdersHook() {
            super("purchase-orders", "Siparişler Satın Alma Siparişi ekranından yönetilir");
        }
    }

    @Component
    public static class OrderLinesHook extends ManagedRecordHook {
        public OrderLinesHook() {
            super("purchase-order-lines", "Sipariş satırları sipariş formundan yönetilir");
        }
    }

    @Component
    public static class ReceiptsHook extends ManagedRecordHook {
        public ReceiptsHook() {
            super("goods-receipts", "Mal kabul, siparişin Mal kabul işlemiyle oluşur");
        }
    }

    @Component
    public static class ReceiptLinesHook extends ManagedRecordHook {
        public ReceiptLinesHook() {
            super("goods-receipt-lines", "Mal kabul satırları değiştirilemez");
        }
    }

    @Component
    public static class InvoicesHook extends ManagedRecordHook {
        public InvoicesHook() {
            super("purchase-invoices", "Alış faturaları fatura ekranından kesilir");
        }
    }

    @Component
    public static class InvoiceLinesHook extends ManagedRecordHook {
        public InvoiceLinesHook() {
            super("purchase-invoice-lines", "Fatura satırları fatura formundan yönetilir");
        }
    }
}
