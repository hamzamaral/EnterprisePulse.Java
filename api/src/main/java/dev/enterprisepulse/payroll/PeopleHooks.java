package dev.enterprisepulse.payroll;

import dev.enterprisepulse.sales.SalesHooks;
import org.springframework.stereotype.Component;

public final class PeopleHooks {
    private PeopleHooks() {}

    @Component public static class Periods extends SalesHooks.Managed { public Periods() { super("payroll-periods", "Bordro dönemleri bordro ekranından yönetilir"); } }
    @Component public static class Payslips extends SalesHooks.Managed { public Payslips() { super("payslips", "Pusulalar bordro hesabıyla oluşur"); } }
    @Component public static class PayslipItems extends SalesHooks.Managed { public PayslipItems() { super("payslip-items", "Pusula kalemleri bordro ekranından eklenir"); } }
    @Component public static class Postings extends SalesHooks.Managed { public Postings() { super("job-postings", "İlanlar işe alım ekranından yönetilir"); } }
    @Component public static class Applications extends SalesHooks.Managed { public Applications() { super("job-applications", "Başvurular işe alım ekranından yönetilir"); } }
    @Component public static class Offers extends SalesHooks.Managed { public Offers() { super("job-offers", "Teklifler başvuru ekranından verilir"); } }
    @Component public static class Interviews extends SalesHooks.Managed { public Interviews() { super("interviews", "Mülakatlar başvuru ekranından planlanır"); } }
    @Component public static class Cycles extends SalesHooks.Managed { public Cycles() { super("review-cycles", "Değerlendirme dönemleri performans ekranından yönetilir"); } }
    @Component public static class Reviews extends SalesHooks.Managed { public Reviews() { super("performance-reviews", "Değerlendirmeler dönem başlayınca oluşur"); } }
    @Component public static class Sessions extends SalesHooks.Managed { public Sessions() { super("training-sessions", "Oturumlar eğitim ekranından yönetilir"); } }
    @Component public static class Enrollments extends SalesHooks.Managed { public Enrollments() { super("training-enrollments", "Katılımlar oturum ekranından yazılır"); } }
}
