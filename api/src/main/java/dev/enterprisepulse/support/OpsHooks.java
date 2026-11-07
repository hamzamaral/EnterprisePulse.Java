package dev.enterprisepulse.support;

import dev.enterprisepulse.sales.SalesHooks;
import org.springframework.stereotype.Component;

/** Generic resource writes that must go through the Support, Contract, Budget and Fleet screens instead. */
public final class OpsHooks {
    private OpsHooks() {}

    @Component public static class Tickets extends SalesHooks.Managed { public Tickets() { super("tickets", "Destek talepleri destek ekranından yönetilir"); } }
    @Component public static class TicketComments extends SalesHooks.Managed { public TicketComments() { super("ticket-comments", "Yorumlar talep ekranından yazılır"); } }
    @Component public static class Contracts extends SalesHooks.Managed { public Contracts() { super("contracts", "Sözleşmeler sözleşme ekranından yönetilir"); } }
    @Component public static class Budgets extends SalesHooks.Managed { public Budgets() { super("budgets", "Bütçeler bütçe ekranından yönetilir"); } }
    @Component public static class BudgetLines extends SalesHooks.Managed { public BudgetLines() { super("budget-lines", "Plan satırları bütçe ekranından yazılır"); } }
    @Component public static class Vehicles extends SalesHooks.Managed { public Vehicles() { super("vehicles", "Araçlar filo ekranından yönetilir"); } }
    @Component public static class VehicleAssignments extends SalesHooks.Managed { public VehicleAssignments() { super("vehicle-assignments", "Zimmetler araç ekranından yazılır"); } }
    @Component public static class VehicleFuel extends SalesHooks.Managed { public VehicleFuel() { super("vehicle-fuel-entries", "Yakıt kayıtları araç ekranından yazılır"); } }
}
