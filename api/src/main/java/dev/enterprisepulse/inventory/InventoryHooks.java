package dev.enterprisepulse.inventory;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Product;
import dev.enterprisepulse.data.generated.ProductCategory;
import dev.enterprisepulse.data.generated.ProductType;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.TrackingType;
import dev.enterprisepulse.data.generated.Warehouse;
import dev.enterprisepulse.resource.ResourceHook;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

public final class InventoryHooks {
    private InventoryHooks() {}

    @Component
    public static class CategoriesHook implements ResourceHook {
        private final Db db;

        public CategoriesHook(Db db) {
            this.db = db;
        }

        @Override
        public String resource() {
            return "product-categories";
        }

        @Override
        public void beforeSave(Object entity, boolean isNew) {
            ProductCategory c = (ProductCategory) entity;
            c.code = c.code.trim().toUpperCase(Locale.ROOT);
            if (c.parentId == null || isNew) return;
            Map<UUID, UUID> parents = new HashMap<>();
            for (Object[] row : db.list("select c.id, c.parentId from ProductCategory c where c.id <> ?1", Object[].class, c.id)) {
                parents.put((UUID) row[0], (UUID) row[1]);
            }
            for (UUID cursor = c.parentId; cursor != null; cursor = parents.get(cursor)) {
                Guard.that(!cursor.equals(c.id), "Bir kategori kendi alt kategorisinin altına taşınamaz");
            }
        }

        @Override
        public void beforeDelete(Object entity) {
            ProductCategory c = (ProductCategory) entity;
            Guard.state(!db.exists("select x.id from ProductCategory x where x.parentId = ?1", c.id), "Kategorinin alt kategorileri var");
            Guard.state(!db.exists("select p.id from Product p where p.categoryId = ?1", c.id), "Kategoride ürün var; silmek yerine pasifleştirin");
        }
    }

    @Component
    public static class WarehousesHook implements ResourceHook {
        private final Db db;

        public WarehousesHook(Db db) {
            this.db = db;
        }

        @Override
        public String resource() {
            return "warehouses";
        }

        @Override
        public void beforeSave(Object entity, boolean isNew) {
            Warehouse w = (Warehouse) entity;
            w.code = w.code.trim().toUpperCase(Locale.ROOT);
            if (!isNew && !w.isActive && Boolean.TRUE.equals(db.original(w, "isActive"))) {
                Guard.state(!db.exists("select b.id from StockBalance b where b.warehouseId = ?1 and b.quantity <> 0", w.id),
                        "Depoda hâlâ stok var; önce transfer edin");
            }
        }

        @Override
        public void beforeDelete(Object entity) {
            Warehouse w = (Warehouse) entity;
            boolean used = db.exists("select t.id from StockTransaction t where t.warehouseId = ?1", w.id)
                    || db.exists("select c.id from StockCount c where c.warehouseId = ?1", w.id)
                    || db.exists("select o.id from PurchaseOrder o where o.warehouseId = ?1", w.id);
            Guard.state(!used, "Depo hareket görmüş; silmek yerine pasifleştirin");
            db.execute("delete from StockBalance b where b.warehouseId = ?1", w.id);
        }
    }

    @Component
    public static class ProductsHook implements ResourceHook {
        private final Db db;
        private final InventoryService inventory;

        public ProductsHook(Db db, InventoryService inventory) {
            this.db = db;
            this.inventory = inventory;
        }

        @Override
        public String resource() {
            return "products";
        }

        @Override
        public void beforeSave(Object entity, boolean isNew) {
            Product p = (Product) entity;
            p.code = p.code.trim();
            p.name = p.name.trim();
            Guard.that(p.type != ProductType.SERVICE || p.trackingType == TrackingType.NONE, "Hizmetler lot veya seri takipli olamaz");
            Guard.that((p.purchasePrice == null || p.purchasePrice.signum() >= 0) && (p.salePrice == null || p.salePrice.signum() >= 0), "Fiyatlar negatif olamaz");
            if (isNew) {
                Guard.state(!db.exists("select v.id from ProductVariant v where v.sku = ?1", p.code), p.code + " stok kodu başka bir üründe kullanılıyor");
                return;
            }
            ProductType originalType = db.original(p, "type");
            TrackingType originalTracking = db.original(p, "trackingType");
            if (originalType != p.type || originalTracking != p.trackingType) {
                List<UUID> ids = db.list("select v.id from ProductVariant v where v.productId = ?1", UUID.class, p.id);
                Guard.state(!inventory.hasHistory(ids), "Stok hareketi olan ürünün türü ve takip şekli değiştirilemez");
                Guard.state(p.type != ProductType.SERVICE || ids.size() <= 1, "Varyantlı ürün hizmete çevrilemez");
            }
        }

        @Override
        public void afterSave(Object entity, boolean isNew) {
            Product p = (Product) entity;
            if (!isNew || db.exists("select v.id from ProductVariant v where v.productId = ?1", p.id)) return;
            ProductVariant v = new ProductVariant();
            v.productId = p.id;
            v.sku = p.code;
            v.isDefault = true;
            v.minStock = BigDecimal.ZERO;
            v.averageCost = BigDecimal.ZERO;
            db.add(v);
            db.flush();
        }

        @Override
        public void beforeDelete(Object entity) {
            Product p = (Product) entity;
            List<UUID> ids = db.list("select v.id from ProductVariant v where v.productId = ?1", UUID.class, p.id);
            Guard.state(!inventory.hasHistory(ids), "Ürünün stok geçmişi var; silmek yerine pasifleştirin");
            boolean used = !ids.isEmpty() && (db.exists("select l.id from PurchaseRequestLine l where l.variantId in ?1", ids)
                    || db.exists("select l.id from SupplierQuoteLine l where l.variantId in ?1", ids)
                    || db.exists("select l.id from PurchaseOrderLine l where l.variantId in ?1", ids));
            Guard.state(!used, "Ürün belgelerde kullanılıyor; silmek yerine pasifleştirin");
            if (!ids.isEmpty()) db.execute("delete from StockBalance b where b.variantId in ?1", ids);
            db.execute("delete from ProductVariant v where v.productId = ?1", p.id);
            p.isActive = false;
        }
    }

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
    public static class VariantsHook extends ManagedRecordHook {
        public VariantsHook() {
            super("product-variants", "Varyantlar ürün kartının Varyantlar sekmesinden yönetilir");
        }
    }

    @Component
    public static class MovementsHook extends ManagedRecordHook {
        public MovementsHook() {
            super("stock-movements", "Stok hareketleri Yeni hareket ekranından işlenir ve sonradan değiştirilemez");
        }
    }

    @Component
    public static class MovementLinesHook extends ManagedRecordHook {
        public MovementLinesHook() {
            super("stock-movement-lines", "İşlenmiş hareket satırları değiştirilemez");
        }
    }

    @Component
    public static class MovementSerialsHook extends ManagedRecordHook {
        public MovementSerialsHook() {
            super("stock-movement-serials", "İşlenmiş hareket satırları değiştirilemez");
        }
    }

    @Component
    public static class CountsHook extends ManagedRecordHook {
        public CountsHook() {
            super("stock-counts", "Sayımlar Stok Sayımı ekranından yönetilir");
        }
    }

    @Component
    public static class CountLinesHook extends ManagedRecordHook {
        public CountLinesHook() {
            super("stock-count-lines", "Sayım satırları sayım ekranından güncellenir");
        }
    }

    @Component
    public static class LotsHook extends ManagedRecordHook {
        public LotsHook() {
            super("stock-lots", "Lotlar stok girişleriyle oluşur");
        }
    }

    @Component
    public static class SerialsHook extends ManagedRecordHook {
        public SerialsHook() {
            super("serial-numbers", "Seri numaraları stok hareketleriyle oluşur");
        }
    }
}
