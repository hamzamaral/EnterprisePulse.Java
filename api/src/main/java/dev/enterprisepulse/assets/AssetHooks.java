package dev.enterprisepulse.assets;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Asset;
import dev.enterprisepulse.data.generated.AssetCategory;
import dev.enterprisepulse.data.generated.AssetLocation;
import dev.enterprisepulse.data.generated.AssetStatus;
import dev.enterprisepulse.resource.ResourceHook;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Component;

public final class AssetHooks {
    private AssetHooks() {}

    @Component
    public static class CategoriesHook implements ResourceHook {
        private final Db db;

        public CategoriesHook(Db db) {
            this.db = db;
        }

        @Override
        public String resource() {
            return "asset-categories";
        }

        @Override
        public void beforeSave(Object entity, boolean isNew) {
            AssetCategory c = (AssetCategory) entity;
            if (c.code != null) c.code = c.code.trim().toUpperCase(Locale.ROOT);
            Guard.that(c.usefulLifeMonths >= 1 && c.usefulLifeMonths <= 600, "Faydalı ömür 1-600 ay arasında olmalı");
        }

        @Override
        public void beforeDelete(Object entity) {
            AssetCategory c = (AssetCategory) entity;
            Guard.state(db.nativeRows("select id, 1 from assets where category_id = ?1 limit 1", c.id).isEmpty(),
                    "Kategoride demirbaş var; silmek yerine pasifleştirin");
        }
    }

    @Component
    public static class LocationsHook implements ResourceHook {
        private final Db db;

        public LocationsHook(Db db) {
            this.db = db;
        }

        @Override
        public String resource() {
            return "asset-locations";
        }

        @Override
        public void beforeSave(Object entity, boolean isNew) {
            AssetLocation l = (AssetLocation) entity;
            if (l.code != null) l.code = l.code.trim().toUpperCase(Locale.ROOT);
        }

        @Override
        public void beforeDelete(Object entity) {
            AssetLocation l = (AssetLocation) entity;
            boolean used = !db.nativeRows("select id, 1 from assets where location_id = ?1 limit 1", l.id).isEmpty()
                    || db.exists("select a.id from AssetAssignment a where a.locationId = ?1", l.id);
            Guard.state(!used, "Lokasyon demirbaş veya zimmet kayıtlarında kullanılıyor; silmek yerine pasifleştirin");
        }
    }

    /** Status changes go through assign / return / maintenance / retire so the history stays consistent. */
    @Component
    public static class AssetsHook implements ResourceHook {
        private final Db db;

        public AssetsHook(Db db) {
            this.db = db;
        }

        @Override
        public String resource() {
            return "assets";
        }

        @Override
        public void beforeSave(Object entity, boolean isNew) {
            Asset a = (Asset) entity;
            if (isNew) {
                a.status = AssetStatus.IN_STOCK;
                a.retiredAt = null;
                a.retireReason = null;
            } else {
                AssetStatus original = db.original(a, "status");
                Guard.state(!AssetService.OUT_OF_SERVICE.contains(original), "Hizmet dışı demirbaş düzenlenemez");
                a.status = original;
                a.retiredAt = db.<Instant>original(a, "retiredAt");
                a.retireReason = db.original(a, "retireReason");
            }
            Guard.that((a.purchaseCost == null || a.purchaseCost.signum() >= 0) && (a.salvageValue == null || a.salvageValue.signum() >= 0),
                    "Tutarlar negatif olamaz");
            Guard.that(a.purchaseCost == null || a.salvageValue == null || a.salvageValue.compareTo(a.purchaseCost) <= 0, "Hurda değeri alış bedelini aşamaz");
            Guard.that(a.usefulLifeMonths == null || (a.usefulLifeMonths >= 1 && a.usefulLifeMonths <= 600), "Faydalı ömür 1-600 ay arasında olmalı");
            Guard.that(a.warrantyEndDate == null || a.purchaseDate == null || !a.warrantyEndDate.isBefore(a.purchaseDate),
                    "Garanti bitişi alış tarihinden önce olamaz");
        }

        @Override
        public void beforeDelete(Object entity) {
            Asset a = (Asset) entity;
            Guard.state(!db.exists("select x.id from AssetAssignment x where x.assetId = ?1", a.id),
                    "Demirbaşın zimmet geçmişi var; silmek yerine hizmet dışı bırakın");
        }
    }

    /** Zimmet, maintenance and count records change only through their workflows (assign, complete, scan...). */
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
    public static class AssignmentsHook extends ManagedRecordHook {
        public AssignmentsHook() {
            super("asset-assignments", "Zimmetler demirbaş kartındaki Zimmetle / İade al / Devret işlemleriyle yönetilir");
        }
    }

    @Component
    public static class MaintenancesHook extends ManagedRecordHook {
        public MaintenancesHook() {
            super("asset-maintenances", "Bakım kayıtları demirbaş kartının Bakım sekmesinden yönetilir");
        }
    }

    @Component
    public static class CountsHook extends ManagedRecordHook {
        public CountsHook() {
            super("asset-counts", "Sayımlar Demirbaş Sayımı ekranından yönetilir");
        }
    }

    @Component
    public static class CountLinesHook extends ManagedRecordHook {
        public CountLinesHook() {
            super("asset-count-lines", "Sayım satırları sayım ekranında okutularak güncellenir");
        }
    }
}
