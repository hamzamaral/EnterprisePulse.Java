package dev.enterprisepulse.crm;

import dev.enterprisepulse.sales.SalesHooks;
import org.springframework.stereotype.Component;

public final class WorkHooks {
    private WorkHooks() {}

    @Component public static class Leads extends SalesHooks.Managed { public Leads() { super("leads", "Potansiyel müşteriler CRM ekranından yönetilir"); } }
    @Component public static class Opportunities extends SalesHooks.Managed { public Opportunities() { super("opportunities", "Fırsatlar CRM ekranından yönetilir"); } }
    @Component public static class Activities extends SalesHooks.Managed { public Activities() { super("crm-activities", "Aktiviteler CRM ekranından yönetilir"); } }
    @Component public static class Reports extends SalesHooks.Managed { public Reports() { super("expense-reports", "Masraf formları masraf ekranından yönetilir"); } }
    @Component public static class Lines extends SalesHooks.Managed { public Lines() { super("expense-lines", "Masraf satırları formdan yönetilir"); } }
    @Component public static class Projects extends SalesHooks.Managed { public Projects() { super("projects", "Projeler proje ekranından yönetilir"); } }
    @Component public static class Members extends SalesHooks.Managed { public Members() { super("project-members", "Proje üyeleri proje ekranından yönetilir"); } }
    @Component public static class Tasks extends SalesHooks.Managed { public Tasks() { super("project-tasks", "Görevler proje ekranından yönetilir"); } }
    @Component public static class Time extends SalesHooks.Managed { public Time() { super("time-entries", "Süre kayıtları proje ekranından girilir"); } }
}
