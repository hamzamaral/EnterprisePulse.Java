package dev.enterprisepulse.production;

import dev.enterprisepulse.data.generated.WorkOrderStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.function.Function;

/** BOM, work order and MRP arithmetic. Rounding follows the original (half up for costs, up for requirements). */
public final class ProductionMath {
    private ProductionMath() {}

    public record BomLineShape(UUID variantId, BigDecimal quantity, BigDecimal scrapRate) {}

    public record BomShape(BigDecimal quantity, BigDecimal laborCost, BigDecimal overheadCost, List<BomLineShape> lines, UUID id, UUID variantId) {
        public BomShape(BigDecimal quantity, BigDecimal laborCost, BigDecimal overheadCost, List<BomLineShape> lines) {
            this(quantity, laborCost, overheadCost, lines, null, null);
        }

        public BomShape withIds(UUID id, UUID variantId) {
            return new BomShape(quantity, laborCost, overheadCost, lines, id, variantId);
        }
    }

    public record RolledCost(BigDecimal material, BigDecimal labor, BigDecimal overhead, BigDecimal unitCost) {}

    public record ExplodedLine(int level, UUID variantId, UUID parentId, BigDecimal quantity, BigDecimal allocated, BigDecimal shortage, boolean made) {}

    public record BuildLine(BigDecimal quantityPer, BigDecimal available) {}

    public static final Set<WorkOrderStatus> OPEN_STATUSES = EnumSet.of(WorkOrderStatus.DRAFT, WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS);

    private static BigDecimal round(BigDecimal value, int places) {
        return value.setScale(places, RoundingMode.HALF_UP);
    }

    public static BigDecimal perUnit(BigDecimal quantity, BigDecimal scrapRate, BigDecimal batch) {
        return round(quantity.multiply(BigDecimal.ONE.add(scrapRate.movePointLeft(2))).divide(batch, 12, RoundingMode.HALF_UP), 6);
    }

    public static BigDecimal required(BigDecimal quantityPer, BigDecimal output) {
        BigDecimal raw = quantityPer.multiply(output);
        BigDecimal scaled = raw.abs().multiply(BigDecimal.valueOf(1000)).setScale(0, RoundingMode.CEILING).movePointLeft(3);
        return raw.signum() < 0 ? scaled.negate() : scaled;
    }

    public static BigDecimal costPerUnit(BigDecimal batchCost, BigDecimal batch) {
        return round(batchCost.divide(batch, 12, RoundingMode.HALF_UP), 4);
    }

    public static BigDecimal standardConsumption(BigDecimal quantityPer, BigDecimal requiredQty, BigDecimal consumedQty, BigDecimal output, boolean finishes) {
        BigDecimal remaining = requiredQty.subtract(consumedQty).max(BigDecimal.ZERO);
        return finishes ? remaining : required(quantityPer, output).min(remaining);
    }

    public static RolledCost rollUpCost(BomShape bom, Function<UUID, BigDecimal> costOf) {
        BigDecimal material = BigDecimal.ZERO;
        for (BomLineShape line : bom.lines()) {
            material = material.add(line.quantity().multiply(BigDecimal.ONE.add(line.scrapRate().movePointLeft(2))).multiply(costOf.apply(line.variantId())));
        }
        BigDecimal labor = round(bom.laborCost(), 2);
        BigDecimal overhead = round(bom.overheadCost(), 2);
        BigDecimal materialRounded = round(material, 2);
        return new RolledCost(materialRounded, labor, overhead, round(material.add(bom.laborCost()).add(bom.overheadCost()).divide(bom.quantity(), 12, RoundingMode.HALF_UP), 4));
    }

    public static boolean createsCycle(UUID output, Iterable<UUID> components, Function<UUID, Iterable<UUID>> componentsOf) {
        Set<UUID> seen = new HashSet<>();
        Stack<UUID> stack = new Stack<>();
        for (UUID component : components) stack.push(component);
        while (!stack.isEmpty()) {
            UUID current = stack.pop();
            if (current.equals(output)) return true;
            if (!seen.add(current)) continue;
            for (UUID next : componentsOf.apply(current)) stack.push(next);
        }
        return false;
    }

    public static List<ExplodedLine> explode(UUID variantId, BigDecimal quantity, Function<UUID, BomShape> bomOf, Map<UUID, BigDecimal> stock, int maxDepth) {
        Map<UUID, BigDecimal> left = new HashMap<>(stock);
        List<ExplodedLine> result = new ArrayList<>();
        walk(variantId, quantity, 1, bomOf, left, result, maxDepth);
        return result;
    }

    public static List<ExplodedLine> explode(UUID variantId, BigDecimal quantity, Function<UUID, BomShape> bomOf, Map<UUID, BigDecimal> stock) {
        return explode(variantId, quantity, bomOf, stock, 10);
    }

    private static void walk(UUID parentId, BigDecimal output, int level, Function<UUID, BomShape> bomOf, Map<UUID, BigDecimal> left, List<ExplodedLine> result, int maxDepth) {
        BomShape bom = bomOf.apply(parentId);
        if (bom == null || level > maxDepth) return;
        for (BomLineShape line : bom.lines()) {
            BigDecimal need = required(perUnit(line.quantity(), line.scrapRate(), bom.quantity()), output);
            BigDecimal free = left.getOrDefault(line.variantId(), BigDecimal.ZERO).max(BigDecimal.ZERO);
            BigDecimal allocated = free.min(need);
            left.put(line.variantId(), free.subtract(allocated));
            BigDecimal shortage = need.subtract(allocated);
            boolean made = bomOf.apply(line.variantId()) != null;
            result.add(new ExplodedLine(level, line.variantId(), parentId, need, allocated, shortage, made));
            if (made && shortage.signum() > 0) walk(line.variantId(), shortage, level + 1, bomOf, left, result, maxDepth);
        }
    }

    public static BigDecimal netRequirement(BigDecimal gross, BigDecimal onHand, BigDecimal incoming) {
        return gross.subtract(onHand.max(BigDecimal.ZERO)).subtract(incoming).max(BigDecimal.ZERO);
    }

    public static BigDecimal buildable(Iterable<BuildLine> lines) {
        BigDecimal max = null;
        for (BuildLine line : lines) {
            if (line.quantityPer().signum() <= 0) continue;
            BigDecimal units = line.available().max(BigDecimal.ZERO).divide(line.quantityPer(), 0, RoundingMode.FLOOR);
            max = max == null ? units : max.min(units);
        }
        return max == null ? BigDecimal.ZERO : max;
    }

    public static boolean canEdit(WorkOrderStatus status) { return status == WorkOrderStatus.DRAFT; }
    public static boolean canRelease(WorkOrderStatus status) { return status == WorkOrderStatus.DRAFT; }
    public static boolean canRecord(WorkOrderStatus status) { return status == WorkOrderStatus.RELEASED || status == WorkOrderStatus.IN_PROGRESS; }
    public static boolean canCancel(WorkOrderStatus status, BigDecimal produced) {
        return (status == WorkOrderStatus.DRAFT || status == WorkOrderStatus.RELEASED) && produced.signum() == 0;
    }
    public static boolean canComplete(WorkOrderStatus status) { return status == WorkOrderStatus.IN_PROGRESS; }
    public static boolean isLate(WorkOrderStatus status, LocalDate plannedEnd, LocalDate today) {
        return OPEN_STATUSES.contains(status) && plannedEnd.isBefore(today);
    }

    public static int progress(BigDecimal produced, BigDecimal quantity) {
        if (quantity.signum() <= 0) return 0;
        return round(produced.multiply(BigDecimal.valueOf(100)).divide(quantity, 12, RoundingMode.HALF_UP), 0).min(BigDecimal.valueOf(100)).intValue();
    }

    public static String availability(BigDecimal needed, BigDecimal onHand) {
        if (needed.signum() <= 0 || onHand.compareTo(needed) >= 0) return "OK";
        return onHand.signum() > 0 ? "PARTIAL" : "MISSING";
    }
}
