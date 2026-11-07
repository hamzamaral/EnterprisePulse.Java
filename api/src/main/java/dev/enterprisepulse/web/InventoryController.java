package dev.enterprisepulse.web;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Paged;
import dev.enterprisepulse.data.generated.SerialStatus;
import dev.enterprisepulse.inventory.InventoryService;
import dev.enterprisepulse.inventory.StockCountService;
import dev.enterprisepulse.inventory.StockMovementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock")
@Tag(name = "Inventory")
public class InventoryController {
    private final InventoryService stock;
    private final StockMovementService movements;
    private final StockCountService counts;

    public InventoryController(InventoryService stock, StockMovementService movements, StockCountService counts) {
        this.stock = stock;
        this.movements = movements;
        this.counts = counts;
    }

    private static void require(String code) {
        CurrentUser.get().require(code);
    }

    private static void requireAny(String... codes) {
        if (!CurrentUser.get().hasAny(codes)) throw dev.enterprisepulse.common.AppException.forbidden("Missing permission: " + codes[0]);
    }

    @GetMapping("/levels")
    public Paged<InventoryService.Level> levels(@RequestParam(required = false) UUID warehouseId, @RequestParam(required = false) UUID categoryId,
                                                 @RequestParam(required = false) Boolean lowStock, @RequestParam(required = false) String search,
                                                 @RequestParam(required = false) String sort, @RequestParam(required = false) String order,
                                                 @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        require("inventory:read");
        return stock.levels(warehouseId, categoryId, Boolean.TRUE.equals(lowStock), search, sort, "desc".equalsIgnoreCase(order), page == null ? 1 : page, pageSize == null ? 50 : pageSize);
    }

    @GetMapping("/summary")
    public InventoryService.Summary summary() {
        require("inventory:read");
        return stock.summary();
    }

    @GetMapping("/warehouses")
    public List<InventoryService.WarehouseSummary> warehouses() {
        require("inventory:read");
        return stock.warehouses();
    }

    @GetMapping("/items")
    public List<InventoryService.Item> items(@RequestParam(required = false) String search, @RequestParam(required = false) UUID warehouseId,
                                              @RequestParam(required = false) Boolean inStock, @RequestParam(required = false) Boolean includeServices,
                                              @RequestParam(required = false) UUID id) {
        requireAny("inventory:read", "purchasing:read", "sales:read");
        return stock.items(search, warehouseId, Boolean.TRUE.equals(inStock), Boolean.TRUE.equals(includeServices), id);
    }

    @GetMapping("/lots")
    public List<InventoryService.LotStock> lots(@RequestParam(required = false) UUID variantId, @RequestParam(required = false) UUID warehouseId) {
        requireAny("inventory:read", "purchasing:read", "sales:read");
        return stock.lots(variantId, warehouseId);
    }

    @GetMapping("/serials")
    public List<InventoryService.SerialRow> serials(@RequestParam(required = false) UUID variantId, @RequestParam(required = false) UUID warehouseId,
                                                     @RequestParam(required = false) SerialStatus status) {
        requireAny("inventory:read", "purchasing:read", "sales:read");
        return stock.serials(variantId, warehouseId, status);
    }

    @GetMapping("/ledger")
    public Paged<InventoryService.LedgerRow> ledger(@RequestParam(required = false) UUID productId, @RequestParam(required = false) UUID variantId,
                                                     @RequestParam(required = false) UUID warehouseId, @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer pageSize) {
        requireAny("inventory:read", "purchasing:read", "sales:read");
        return stock.ledger(productId, variantId, warehouseId, page == null ? 1 : page, pageSize == null ? 50 : pageSize);
    }

    @GetMapping("/products/{id}")
    public InventoryService.ProductStock product(@PathVariable UUID id) {
        require("inventory:read");
        return stock.product(id);
    }

    @PostMapping("/products/{id}/variants")
    public InventoryService.ProductStock addVariant(@PathVariable UUID id, @RequestBody InventoryService.VariantInput body) {
        require("inventory:update");
        return stock.addVariant(id, body);
    }

    @PatchMapping("/products/{id}/variants/{variantId}")
    public InventoryService.ProductStock updateVariant(@PathVariable UUID id, @PathVariable UUID variantId, @RequestBody InventoryService.VariantInput body) {
        require("inventory:update");
        return stock.updateVariant(id, variantId, body);
    }

    @DeleteMapping("/products/{id}/variants/{variantId}")
    public InventoryService.ProductStock removeVariant(@PathVariable UUID id, @PathVariable UUID variantId) {
        require("inventory:update");
        return stock.removeVariant(id, variantId);
    }

    @PostMapping("/movements")
    public StockMovementService.DetailDto createMovement(@RequestBody StockMovementService.CreateRequest body) {
        require("inventory:move");
        return movements.create(body);
    }

    @GetMapping("/movements/{id}")
    public StockMovementService.DetailDto movement(@PathVariable UUID id) {
        require("inventory:read");
        return movements.detail(id);
    }

    @PostMapping("/counts")
    public StockCountService.Detail createCount(@RequestBody StockCountService.CreateRequest body) {
        require("inventory:count");
        return counts.create(body);
    }

    @GetMapping("/counts/{id}")
    public StockCountService.Detail count(@PathVariable UUID id) {
        require("inventory:read");
        return counts.detail(id);
    }

    @PutMapping("/counts/{id}/lines")
    public StockCountService.Detail updateLines(@PathVariable UUID id, @RequestBody List<StockCountService.LineUpdate> body) {
        require("inventory:count");
        return counts.updateLines(id, body);
    }

    @PostMapping("/counts/{id}/lines")
    public StockCountService.Detail addLine(@PathVariable UUID id, @RequestBody StockCountService.AddLine body) {
        require("inventory:count");
        return counts.addLine(id, body);
    }

    @PostMapping("/counts/{id}/complete")
    public StockCountService.Detail complete(@PathVariable UUID id) {
        require("inventory:count");
        return counts.complete(id);
    }

    @PostMapping("/counts/{id}/cancel")
    public StockCountService.Detail cancel(@PathVariable UUID id) {
        require("inventory:count");
        return counts.cancel(id);
    }
}
