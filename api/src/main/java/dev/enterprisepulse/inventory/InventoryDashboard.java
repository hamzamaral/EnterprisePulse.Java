package dev.enterprisepulse.inventory;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.PurchaseOrderStatus;
import dev.enterprisepulse.data.generated.PurchaseRequestStatus;
import dev.enterprisepulse.platform.Extensibility;
import dev.enterprisepulse.platform.Extensibility.DashboardCard;
import dev.enterprisepulse.platform.Extensibility.DashboardSection;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class InventoryDashboard implements Extensibility.DashboardProvider {
    private final Db db;
    private final InventoryService stock;

    public InventoryDashboard(Db db, InventoryService stock) {
        this.db = db;
        this.stock = stock;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public DashboardSection section() {
        CurrentUser ctx = CurrentUser.get();
        List<DashboardCard> cards = new ArrayList<>();
        if (ctx.has("inventory:read")) {
            InventoryService.Summary summary = stock.summary();
            cards.add(new DashboardCard("stock-value", "Stok değeri", String.format(java.util.Locale.forLanguageTag("tr"), "%,.2f", summary.stockValue()),
                    "Inventory", summary.itemCount() + " kalem, " + summary.warehouseCount() + " depo", "/inventory", "primary"));
            if (summary.lowStockCount() > 0) {
                cards.add(new DashboardCard("low-stock", "Minimum altı stok", String.valueOf(summary.lowStockCount()), "WarningAmber", null, "/inventory?low=1", "warning"));
            }
        }
        if (ctx.has("purchasing:read")) {
            long pending = db.count("select count(r) from PurchaseRequest r where r.status = ?1", PurchaseRequestStatus.PENDING);
            long open = db.count("select count(o) from PurchaseOrder o where o.status = ?1 or o.status = ?2",
                    PurchaseOrderStatus.APPROVED, PurchaseOrderStatus.PARTIALLY_RECEIVED);
            if (pending > 0) {
                cards.add(new DashboardCard("purchase-requests", "Bekleyen satın alma talebi", String.valueOf(pending), "RequestQuote", null,
                        "/r/purchase-requests?status=PENDING", "warning"));
            }
            cards.add(new DashboardCard("open-orders", "Açık satın alma siparişi", String.valueOf(open), "LocalShipping", "/r/purchase-orders"));
        }
        return cards.isEmpty() ? null : new DashboardSection("inventory", "Stok ve Satın Alma", cards);
    }
}
