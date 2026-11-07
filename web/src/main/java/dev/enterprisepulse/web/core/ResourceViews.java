package dev.enterprisepulse.web.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.icon.VaadinIcon;
import dev.enterprisepulse.web.core.Meta.ResourceMeta;
import dev.enterprisepulse.web.views.assets.AssetDialogs;
import dev.enterprisepulse.web.views.assets.AssetTabs;
import dev.enterprisepulse.web.views.inventory.InventoryTabs;
import dev.enterprisepulse.web.views.inventory.StockCountView;
import dev.enterprisepulse.web.views.purchasing.PurchasingTabs;
import dev.enterprisepulse.web.views.purchasing.QuoteFormView;
import dev.enterprisepulse.web.views.hr.LeaveRequestDialog;
import dev.enterprisepulse.web.views.hr.TerminateDialog;
import dev.enterprisepulse.web.views.platform.CompanyCreateDialog;
import dev.enterprisepulse.web.views.platform.UserCreateDialog;
import dev.enterprisepulse.web.views.platform.UserRolesTab;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Per-resource tweaks over the generic metadata screens; business phases register theirs here. */
public final class ResourceViews {
    private ResourceViews() {}

    public record ActionContext(ObjectNode record, Runnable reload) {
        public String id() {
            return record.path("id").asText();
        }
    }

    public record TabContext(ObjectNode record, ResourceMeta meta, Runnable onChanged) {}

    public record Tab(String title, String titleEn, String permission, Function<TabContext, Component> factory) {}

    /** A button in the list header that opens a related page. */
    public record HeaderLink(String label, String labelEn, String href, VaadinIcon icon, String permission) {
        public HeaderLink(String label, String labelEn, String href, VaadinIcon icon) {
            this(label, labelEn, href, icon, null);
        }
    }

    /** A button on a record (submit, approve, post, print...). Default run: POST endpoint with {comment}. */
    public static final class Action {
        public final String label;
        public final String labelEn;
        public VaadinIcon icon = VaadinIcon.PLAY;
        public String theme = "primary";
        public String permission;
        public Predicate<ObjectNode> visible = r -> true;
        public String endpoint;
        public String prompt;
        public boolean promptRequired;
        public String confirm;
        public boolean download;
        public Consumer<ActionContext> run;

        public Action(String label, String labelEn) {
            this.label = label;
            this.labelEn = labelEn;
        }

        public String text(String locale) {
            return "en".equals(locale) && labelEn != null && !labelEn.isEmpty() ? labelEn : label;
        }

        public Action icon(VaadinIcon v) { icon = v; return this; }
        public Action theme(String v) { theme = v; return this; }
        public Action permission(String v) { permission = v; return this; }
        public Action visible(Predicate<ObjectNode> v) { visible = v; return this; }
        public Action endpoint(String v) { endpoint = v; return this; }
        public Action prompt(String v, boolean required) { prompt = v; promptRequired = required; return this; }
        public Action confirm(String v) { confirm = v; return this; }
        public Action download() { download = true; return this; }
        public Action run(Consumer<ActionContext> v) { run = v; return this; }

        public static Predicate<ObjectNode> statusIn(String... statuses) {
            Set<String> set = Set.of(statuses);
            return r -> set.contains(r.path("status").asText());
        }
    }

    public static final class View {
        public List<String> columns;
        public Set<String> hiddenFields = Set.of();
        public Set<String> readOnlyFields = Set.of();
        public List<String> filterFields;
        public boolean hideCreate;
        public boolean hideEdit;
        public boolean hideDelete;
        public String createHref;
        /** Custom record page such as "assets/counts/{id}" instead of the generic detail view. */
        public String detailHref;
        /** Opens a custom create dialog; the argument is called after a successful save. */
        public Consumer<Runnable> createDialog;
        public final List<Action> actions = new ArrayList<>();
        public final List<Tab> tabs = new ArrayList<>();
        public final List<HeaderLink> links = new ArrayList<>();
        public Set<String> hiddenChildren = Set.of();
        public String workflowEntity;
    }

    private static final Map<String, View> MAP = new HashMap<>();
    private static final View EMPTY = new View();

    public static View of(String resource) {
        return MAP.getOrDefault(resource, EMPTY);
    }

    public static View configure(String resource, Consumer<View> configure) {
        View view = MAP.computeIfAbsent(resource, k -> new View());
        configure.accept(view);
        return view;
    }

    static {
        platform();
        hr();
        assets();
        inventory();
    }

    private static void inventory() {
        configure("products", v -> {
            v.columns = List.of("code", "name", "categoryId", "unitId", "type", "trackingType", "salePrice", "isActive");
            v.filterFields = List.of("type", "trackingType", "categoryId", "isActive");
            v.links.add(new HeaderLink("Stok durumu", "Stock levels", "inventory", VaadinIcon.STORAGE));
            v.tabs.add(new Tab("Varyantlar", "Variants", null, InventoryTabs::variants));
            v.tabs.add(new Tab("Stok", "Stock", null, InventoryTabs::stock));
            v.tabs.add(new Tab("Stok kartı", "Stock card", null, InventoryTabs::ledger));
        });
        configure("product-categories", v -> {
            v.columns = List.of("code", "name", "parentId", "isActive");
            v.links.add(new HeaderLink("Depolar", "Warehouses", "r/warehouses", VaadinIcon.STORAGE));
        });
        configure("warehouses", v -> {
            v.columns = List.of("code", "name", "branchId", "address", "isActive");
            v.links.add(new HeaderLink("Kategoriler", "Categories", "r/product-categories", VaadinIcon.CUBES));
            v.tabs.add(new Tab("Stok değeri", "Stock value", null, InventoryTabs::warehouse));
        });
        configure("stock-movements", v -> {
            v.columns = List.of("number", "type", "movementDate", "sourceWarehouseId", "targetWarehouseId", "reference", "createdAt");
            v.filterFields = List.of("type", "movementDate", "sourceWarehouseId", "targetWarehouseId");
            v.createHref = "inventory/movements/new";
            v.detailHref = "inventory/movements/{id}";
            v.hideEdit = true;
            v.hideDelete = true;
        });
        configure("stock-counts", v -> {
            v.columns = List.of("number", "warehouseId", "countDate", "status", "createdById", "completedAt");
            v.filterFields = List.of("status", "warehouseId", "countDate");
            v.createDialog = StockCountView::open;
            v.detailHref = "inventory/counts/{id}";
            v.hideEdit = true;
            v.hideDelete = true;
        });
        configure("business-partners", v -> {
            v.columns = List.of("code", "title", "type", "city", "phone", "paymentTermDays", "currencyCode", "isActive");
            v.filterFields = List.of("type", "city", "isActive");
            v.tabs.add(new Tab("Ekstre", "Statement", null, PurchasingTabs::statement));
        });
        configure("purchase-requests", v -> {
            v.columns = List.of("number", "title", "requestDate", "neededBy", "status", "requestedById");
            v.filterFields = List.of("status", "requestDate", "departmentId");
            v.createHref = "purchasing/requests/new";
            v.hideEdit = true;
            v.hideDelete = true;
            v.hiddenChildren = Set.of("purchase-request-lines");
            v.workflowEntity = "PurchaseRequest";
            v.tabs.add(new Tab("Satırlar", "Lines", null, PurchasingTabs::requestLines));
            v.tabs.add(new Tab("Teklif karşılaştırma", "Compare quotes", "purchasing:read", PurchasingTabs::compare));
        });
        configure("supplier-quotes", v -> {
            v.columns = List.of("number", "partnerId", "requestId", "quoteDate", "validUntil", "currencyCode", "grandTotal", "status");
            v.filterFields = List.of("status", "partnerId", "quoteDate");
            v.createHref = "purchasing/quotes/new";
            v.hideEdit = true;
            v.hideDelete = true;
            v.hiddenChildren = Set.of("supplier-quote-lines");
            v.tabs.add(new Tab("Satırlar", "Lines", null, PurchasingTabs::quoteLines));
            v.actions.add(new Action("Kabul et", "Accept").icon(VaadinIcon.CHECK).permission("purchasing:create")
                    .visible(Action.statusIn("OPEN")).run(QuoteFormView.accept()));
            v.actions.add(new Action("Reddet", "Reject").icon(VaadinIcon.CLOSE).theme("error").permission("purchasing:update")
                    .visible(Action.statusIn("OPEN")).endpoint("/api/purchasing/quotes/{id}/reject").confirm("Teklif reddedilecek."));
        });
        configure("purchase-orders", v -> {
            v.columns = List.of("number", "partnerId", "orderDate", "expectedDate", "warehouseId", "currencyCode", "grandTotal", "status");
            v.filterFields = List.of("status", "partnerId", "warehouseId", "orderDate");
            v.createHref = "purchasing/orders/new";
            v.detailHref = "purchasing/orders/{id}";
            v.hideEdit = true;
            v.hideDelete = true;
        });
        configure("goods-receipts", v -> {
            v.columns = List.of("number", "orderId", "partnerId", "warehouseId", "receiptDate", "deliveryNoteNo");
            v.filterFields = List.of("partnerId", "warehouseId", "receiptDate");
            v.createHref = "purchasing/receipts/new";
            v.detailHref = "purchasing/receipts/{id}";
            v.hideEdit = true;
            v.hideDelete = true;
        });
        configure("purchase-invoices", v -> {
            v.columns = List.of("number", "supplierInvoiceNo", "partnerId", "invoiceDate", "dueDate", "grandTotal", "paymentStatus", "status");
            v.filterFields = List.of("status", "paymentStatus", "partnerId", "invoiceDate");
            v.createHref = "purchasing/invoices/new";
            v.detailHref = "purchasing/invoices/{id}";
            v.hideEdit = true;
            v.hideDelete = true;
        });
    }

    private static void assets() {
        configure("assets", v -> {
            v.columns = List.of("assetTag", "name", "categoryId", "brand", "model", "status", "condition", "locationId");
            v.filterFields = List.of("status", "condition", "categoryId", "locationId", "purchaseDate", "warrantyEndDate");
            v.readOnlyFields = Set.of("status", "retiredAt", "retireReason");
            v.actions.add(new Action("Zimmetle", "Assign").icon(VaadinIcon.USER_CHECK).permission("assets:assign")
                    .visible(Action.statusIn("IN_STOCK")).run(AssetDialogs.assign(false)));
            v.actions.add(new Action("Devret", "Transfer").icon(VaadinIcon.EXCHANGE).permission("assets:assign")
                    .visible(Action.statusIn("ASSIGNED")).run(AssetDialogs.assign(true)));
            v.actions.add(new Action("İade al", "Take back").icon(VaadinIcon.ARROW_BACKWARD).permission("assets:assign")
                    .visible(Action.statusIn("ASSIGNED")).run(AssetDialogs::returnAsset));
            v.actions.add(new Action("Hizmet dışı bırak", "Retire").icon(VaadinIcon.BAN).theme("error").permission("assets:update")
                    .visible(Action.statusIn("IN_STOCK")).run(AssetDialogs::retire));
            v.actions.add(new Action("QR etiketi", "QR label").icon(VaadinIcon.QRCODE).theme("contrast").endpoint("/api/assets/{id}/label").download());
            v.tabs.add(new Tab("Amortisman ve etiket", "Depreciation and label", null, AssetTabs::overview));
            v.tabs.add(new Tab("Zimmet geçmişi", "Assignment history", null, AssetTabs::assignments));
            v.tabs.add(new Tab("Bakım", "Maintenance", null, AssetTabs::maintenance));
        });
        configure("asset-assignments", v -> {
            v.columns = List.of("number", "assetId", "assigneeType", "employeeId", "departmentId", "locationId", "assignedAt", "returnedAt", "acknowledgedAt", "status");
            v.filterFields = List.of("status", "assigneeType", "assetId", "employeeId", "departmentId", "assignedAt");
            v.hideCreate = true;
            v.hideEdit = true;
            v.hideDelete = true;
            v.detailHref = "assets/assignments/{id}";
        });
        configure("asset-counts", v -> {
            v.columns = List.of("number", "countDate", "locationId", "categoryId", "status", "createdById", "completedAt");
            v.filterFields = List.of("status", "locationId", "categoryId", "countDate");
            v.createDialog = AssetDialogs::createCount;
            v.hideEdit = true;
            v.hideDelete = true;
            v.detailHref = "assets/counts/{id}";
        });
        configure("asset-categories", v -> {
            v.columns = List.of("code", "name", "usefulLifeMonths", "isActive");
            v.links.add(new HeaderLink("Konumlar", "Locations", "r/asset-locations", VaadinIcon.MAP_MARKER));
        });
        configure("asset-locations", v -> {
            v.columns = List.of("code", "name", "branchId", "description", "isActive");
            v.links.add(new HeaderLink("Kategoriler", "Categories", "r/asset-categories", VaadinIcon.CUBES));
        });
        configure("asset-maintenances", v -> {
            v.hideCreate = true;
            v.hideEdit = true;
            v.hideDelete = true;
        });
    }

    private static void hr() {
        configure("employees", v -> {
            v.columns = List.of("employeeNo", "firstName", "lastName", "departmentId", "positionId", "managerId", "hireDate", "status");
            v.filterFields = List.of("status", "departmentId", "positionId", "branchId", "employmentType", "managerId");
            v.readOnlyFields = Set.of("status", "terminationDate", "terminationReason");
            v.hiddenChildren = Set.of("shift-assignments", "attendance-records", "salary-records");
            v.links.add(new HeaderLink("Organizasyon şeması", "Org chart", "hr/org-chart", VaadinIcon.SITEMAP));
            v.actions.add(new Action("İşten çıkış", "Terminate").icon(VaadinIcon.USER_CLOCK).theme("error").permission("employees:update")
                    .visible(Action.statusIn("ACTIVE")).run(TerminateDialog::open));
        });
        configure("departments", v -> v.columns = List.of("code", "name", "parentId", "managerId", "isActive"));
        configure("positions", v -> v.columns = List.of("code", "name", "departmentId", "isActive"));
        configure("leave-requests", v -> {
            v.columns = List.of("number", "employeeId", "leaveTypeId", "startDate", "endDate", "days", "status", "createdAt");
            v.filterFields = List.of("status", "employeeId", "leaveTypeId", "startDate");
            v.createDialog = done -> LeaveRequestDialog.open(null, done);
            v.hideEdit = true;
            v.hideDelete = true;
            v.workflowEntity = "LeaveRequest";
            v.actions.add(new Action("İptal et", "Cancel").icon(VaadinIcon.CLOSE_CIRCLE).theme("error")
                    .visible(Action.statusIn("PENDING", "APPROVED")).endpoint("/api/hr/leave-requests/{id}/cancel")
                    .prompt("Açıklama", false).confirm("Talep iptal edilecek; onaylanmış izinlerde günler bakiyeye iade edilir."));
        });
        configure("leave-types", v -> {
            v.columns = List.of("code", "name", "defaultDays", "isPaid", "requiresBalance", "color", "isActive");
            v.links.add(new HeaderLink("İzin bakiyeleri", "Balances", "hr/leave-balances", VaadinIcon.WALLET));
            v.links.add(new HeaderLink("Resmi tatiller", "Public holidays", "r/holidays", VaadinIcon.CALENDAR_O));
        });
        configure("holidays", v -> v.columns = List.of("date", "name"));
        configure("leave-balances", v -> v.columns = List.of("employeeId", "leaveTypeId", "year", "entitledDays", "usedDays"));
        configure("shifts", v -> {
            v.columns = List.of("code", "name", "startTime", "endTime", "breakMinutes", "color", "isActive");
            v.links.add(new HeaderLink("Haftalık plan", "Weekly plan", "hr/shift-plan", VaadinIcon.CALENDAR));
            v.links.add(new HeaderLink("Puantaj", "Timesheet", "hr/timesheet", VaadinIcon.TABLE));
        });
    }

    private static void platform() {
        configure("users", v -> {
            v.columns = List.of("firstName", "lastName", "email", "phone", "isActive", "isSuperAdmin", "lastLoginAt");
            v.createDialog = UserCreateDialog::open;
            v.hiddenFields = Set.of("failedLoginCount", "lockedUntil", "twoFactorEnabled", "avatarUrl");
            v.tabs.add(new Tab("Roller", "Roles", "users:update", UserRolesTab::new));
            v.hiddenChildren = Set.of("user-roles");
        });
        configure("roles", v -> v.createHref = "roles");
        configure("companies", v -> v.createDialog = CompanyCreateDialog::open);
        configure("audit-logs", v -> {
            v.columns = List.of("createdAt", "userEmail", "action", "entityType", "entityId", "ip");
            v.filterFields = List.of("action", "entityType", "userEmail");
        });
        configure("workflow-instances", v -> {
            v.columns = List.of("title", "definitionId", "entityType", "status", "currentStepOrder", "startedById", "startedAt");
            v.actions.add(new Action("Süreci aç", "Open").icon(VaadinIcon.EXTERNAL_LINK)
                    .run(c -> UI.getCurrent().navigate("workflow/instances/" + c.id())));
        });
        configure("number-sequences", v -> {
            v.hideCreate = true;
            v.hideDelete = true;
        });
    }
}
