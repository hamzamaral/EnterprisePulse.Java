package dev.enterprisepulse.accounting;

import dev.enterprisepulse.data.generated.LedgerAccountType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Tekdüzen Hesap Planı: the standard chart, account classes and statement layouts. */
public final class AccountingChart {
    private AccountingChart() {}

    /** System accounts used by automatic postings. */
    public static final class Acc {
        private Acc() {}

        public static final String CASH = "100";
        public static final String CHEQUES_RECEIVED = "101";
        public static final String BANKS = "102";
        public static final String CHEQUES_ISSUED = "103";
        public static final String RECEIVABLES = "120";
        public static final String NOTES_RECEIVABLE = "121";
        public static final String MERCHANDISE = "153";
        public static final String INPUT_VAT = "191";
        public static final String PAYABLES = "320";
        public static final String NOTES_PAYABLE = "321";
        public static final String OTHER_TRADE_PAYABLES = "329";
        public static final String DUE_TO_EMPLOYEES = "335";
        public static final String OTHER_PAYABLES = "336";
        public static final String TAXES_PAYABLE = "360";
        public static final String SOCIAL_SECURITY_PAYABLE = "361";
        public static final String OUTPUT_VAT = "391";
        public static final String CAPITAL = "500";
        public static final String SALES = "600";
        public static final String SALES_RETURNS = "610";
        public static final String COST_OF_GOODS_SOLD = "621";
        public static final String OTHER_EXTRAORDINARY_INCOME = "679";
        public static final String OTHER_EXTRAORDINARY_EXPENSE = "689";
        public static final String DIRECT_MATERIALS = "710";
        public static final String DIRECT_MATERIALS_APPLIED = "711";
        public static final String DIRECT_LABOR_APPLIED = "721";
        public static final String OVERHEAD_APPLIED = "731";
        public static final String GENERAL_ADMIN_EXPENSES = "770";

        public static final Set<String> SYSTEM_CODES = Set.of(
                CASH, CHEQUES_RECEIVED, BANKS, CHEQUES_ISSUED, RECEIVABLES, NOTES_RECEIVABLE, MERCHANDISE, INPUT_VAT, PAYABLES, NOTES_PAYABLE,
                OTHER_TRADE_PAYABLES, DUE_TO_EMPLOYEES, OTHER_PAYABLES, TAXES_PAYABLE, SOCIAL_SECURITY_PAYABLE, OUTPUT_VAT, CAPITAL, SALES, SALES_RETURNS,
                COST_OF_GOODS_SOLD, OTHER_EXTRAORDINARY_INCOME, OTHER_EXTRAORDINARY_EXPENSE, DIRECT_MATERIALS, DIRECT_MATERIALS_APPLIED, DIRECT_LABOR_APPLIED,
                OVERHEAD_APPLIED, GENERAL_ADMIN_EXPENSES);
    }

    public record DefaultAccount(String code, String name, LedgerAccountType type, boolean isSystem) {}

    public record IncomeSectionDef(String key, String label, List<String> prefixes) {}

    private static final String[][] ACCOUNTS = {
            {"100", "Kasa"}, {"101", "Alınan Çekler"}, {"102", "Bankalar"}, {"103", "Verilen Çekler ve Ödeme Emirleri (-)"}, {"108", "Diğer Hazır Değerler"},
            {"120", "Alıcılar"}, {"121", "Alacak Senetleri"}, {"126", "Verilen Depozito ve Teminatlar"}, {"127", "Diğer Ticari Alacaklar"},
            {"128", "Şüpheli Ticari Alacaklar"}, {"135", "Personelden Alacaklar"}, {"136", "Diğer Çeşitli Alacaklar"}, {"150", "İlk Madde ve Malzeme"},
            {"153", "Ticari Mallar"}, {"159", "Verilen Sipariş Avansları"}, {"180", "Gelecek Aylara Ait Giderler"}, {"190", "Devreden KDV"},
            {"191", "İndirilecek KDV"}, {"193", "Peşin Ödenen Vergiler ve Fonlar"}, {"195", "İş Avansları"}, {"196", "Personel Avansları"},
            {"253", "Tesis, Makine ve Cihazlar"}, {"254", "Taşıtlar"}, {"255", "Demirbaşlar"}, {"257", "Birikmiş Amortismanlar (-)"}, {"260", "Haklar"},
            {"300", "Banka Kredileri"}, {"320", "Satıcılar"}, {"321", "Borç Senetleri"}, {"326", "Alınan Depozito ve Teminatlar"},
            {"329", "Diğer Ticari Borçlar"}, {"335", "Personele Borçlar"}, {"336", "Diğer Çeşitli Borçlar"}, {"340", "Alınan Sipariş Avansları"},
            {"360", "Ödenecek Vergi ve Fonlar"}, {"361", "Ödenecek Sosyal Güvenlik Kesintileri"},
            {"370", "Dönem Kârı Vergi ve Diğer Yasal Yükümlülük Karşılıkları"}, {"380", "Gelecek Aylara Ait Gelirler"}, {"391", "Hesaplanan KDV"},
            {"400", "Banka Kredileri (Uzun Vadeli)"}, {"500", "Sermaye"}, {"540", "Yasal Yedekler"}, {"570", "Geçmiş Yıllar Kârları"},
            {"580", "Geçmiş Yıllar Zararları (-)"}, {"590", "Dönem Net Kârı"}, {"591", "Dönem Net Zararı (-)"}, {"600", "Yurt İçi Satışlar"},
            {"601", "Yurt Dışı Satışlar"}, {"602", "Diğer Gelirler"}, {"610", "Satıştan İadeler (-)"}, {"611", "Satış İskontoları (-)"},
            {"620", "Satılan Mamuller Maliyeti (-)"}, {"621", "Satılan Ticari Mallar Maliyeti (-)"}, {"622", "Satılan Hizmet Maliyeti (-)"},
            {"630", "Araştırma ve Geliştirme Giderleri (-)"}, {"631", "Pazarlama, Satış ve Dağıtım Giderleri (-)"}, {"632", "Genel Yönetim Giderleri (-)"},
            {"642", "Faiz Gelirleri"}, {"646", "Kambiyo Kârları"}, {"649", "Diğer Olağan Gelir ve Kârlar"}, {"653", "Komisyon Giderleri (-)"},
            {"656", "Kambiyo Zararları (-)"}, {"659", "Diğer Olağan Gider ve Zararlar (-)"}, {"660", "Kısa Vadeli Borçlanma Giderleri (-)"},
            {"679", "Diğer Olağandışı Gelir ve Kârlar"}, {"689", "Diğer Olağandışı Gider ve Zararlar (-)"},
            {"691", "Dönem Kârı Vergi ve Diğer Yasal Yükümlülük Karşılıkları (-)"}, {"710", "Direkt İlk Madde ve Malzeme Giderleri"},
            {"711", "Direkt İlk Madde ve Malzeme Yansıtma Hesabı"}, {"720", "Direkt İşçilik Giderleri"}, {"721", "Direkt İşçilik Giderleri Yansıtma Hesabı"},
            {"730", "Genel Üretim Giderleri"}, {"731", "Genel Üretim Giderleri Yansıtma Hesabı"}, {"760", "Pazarlama, Satış ve Dağıtım Giderleri"},
            {"770", "Genel Yönetim Giderleri"}, {"780", "Finansman Giderleri"},
    };

    /** Account type from the first digits of the code (class 6 mixes income and expense). */
    public static LedgerAccountType typeOf(String code) {
        return switch (code.charAt(0)) {
            case '1', '2' -> LedgerAccountType.ASSET;
            case '3', '4' -> LedgerAccountType.LIABILITY;
            case '5' -> LedgerAccountType.EQUITY;
            case '6' -> {
                String head = code.substring(0, 2);
                yield head.equals("60") || head.equals("64") || head.equals("67") ? LedgerAccountType.INCOME : LedgerAccountType.EXPENSE;
            }
            default -> LedgerAccountType.EXPENSE;
        };
    }

    public static List<DefaultAccount> defaults() {
        return java.util.Arrays.stream(ACCOUNTS).map(a -> new DefaultAccount(a[0], a[1], typeOf(a[0]), Acc.SYSTEM_CODES.contains(a[0]))).toList();
    }

    private static final Pattern VALID_CODE = Pattern.compile("^[1-7]\\d{2}(\\.\\d{1,4})*$");

    public static boolean isValidCode(String code) {
        return code != null && VALID_CODE.matcher(code).matches();
    }

    /** Three-digit main account of any code. */
    public static String mainCode(String code) {
        return code.substring(0, 3);
    }

    public static final Map<String, String> BALANCE_CLASSES = new LinkedHashMap<>();
    public static final Map<String, String> GROUPS = new LinkedHashMap<>();

    static {
        BALANCE_CLASSES.put("1", "Dönen Varlıklar");
        BALANCE_CLASSES.put("2", "Duran Varlıklar");
        BALANCE_CLASSES.put("3", "Kısa Vadeli Yabancı Kaynaklar");
        BALANCE_CLASSES.put("4", "Uzun Vadeli Yabancı Kaynaklar");
        BALANCE_CLASSES.put("5", "Özkaynaklar");
        String[][] groups = {
                {"10", "Hazır Değerler"}, {"11", "Menkul Kıymetler"}, {"12", "Ticari Alacaklar"}, {"13", "Diğer Alacaklar"}, {"15", "Stoklar"},
                {"17", "Yıllara Yaygın İnşaat ve Onarım Maliyetleri"}, {"18", "Gelecek Aylara Ait Giderler ve Gelir Tahakkukları"}, {"19", "Diğer Dönen Varlıklar"},
                {"22", "Ticari Alacaklar"}, {"23", "Diğer Alacaklar"}, {"24", "Mali Duran Varlıklar"}, {"25", "Maddi Duran Varlıklar"},
                {"26", "Maddi Olmayan Duran Varlıklar"}, {"27", "Özel Tükenmeye Tabi Varlıklar"}, {"28", "Gelecek Yıllara Ait Giderler ve Gelir Tahakkukları"},
                {"29", "Diğer Duran Varlıklar"}, {"30", "Mali Borçlar"}, {"32", "Ticari Borçlar"}, {"33", "Diğer Borçlar"}, {"34", "Alınan Avanslar"},
                {"35", "Yıllara Yaygın İnşaat ve Onarım Hakedişleri"}, {"36", "Ödenecek Vergi ve Diğer Yükümlülükler"}, {"37", "Borç ve Gider Karşılıkları"},
                {"38", "Gelecek Aylara Ait Gelirler ve Gider Tahakkukları"}, {"39", "Diğer Kısa Vadeli Yabancı Kaynaklar"}, {"40", "Mali Borçlar"},
                {"42", "Ticari Borçlar"}, {"43", "Diğer Borçlar"}, {"44", "Alınan Avanslar"}, {"47", "Borç ve Gider Karşılıkları"},
                {"48", "Gelecek Yıllara Ait Gelirler ve Gider Tahakkukları"}, {"49", "Diğer Uzun Vadeli Yabancı Kaynaklar"}, {"50", "Ödenmiş Sermaye"},
                {"52", "Sermaye Yedekleri"}, {"54", "Kâr Yedekleri"}, {"57", "Geçmiş Yıllar Kârları"}, {"58", "Geçmiş Yıllar Zararları (-)"},
                {"59", "Dönem Net Kârı (Zararı)"},
        };
        for (String[] g : groups) GROUPS.put(g[0], g[1]);
    }

    /** Gelir tablosu sections in statement order. */
    public static final List<IncomeSectionDef> INCOME_SECTIONS = List.of(
            new IncomeSectionDef("grossSales", "A. Brüt Satışlar", List.of("60")),
            new IncomeSectionDef("salesDeductions", "B. Satış İndirimleri (-)", List.of("61")),
            new IncomeSectionDef("costOfSales", "D. Satışların Maliyeti (-)", List.of("62")),
            new IncomeSectionDef("operatingExpenses", "E. Faaliyet Giderleri (-)", List.of("63", "71", "72", "73", "74", "75", "76", "77", "79")),
            new IncomeSectionDef("otherIncome", "F. Diğer Faaliyetlerden Olağan Gelir ve Kârlar", List.of("64")),
            new IncomeSectionDef("otherExpenses", "G. Diğer Faaliyetlerden Olağan Gider ve Zararlar (-)", List.of("65")),
            new IncomeSectionDef("financeExpenses", "H. Finansman Giderleri (-)", List.of("66", "78")),
            new IncomeSectionDef("extraordinaryIncome", "I. Olağandışı Gelir ve Kârlar", List.of("67")),
            new IncomeSectionDef("extraordinaryExpenses", "J. Olağandışı Gider ve Zararlar (-)", List.of("68")),
            new IncomeSectionDef("taxProvision", "K. Dönem Kârı Vergi ve Diğer Yasal Yükümlülük Karşılıkları (-)", List.of("69")));

    public static String incomeSection(String code) {
        String head = code.substring(0, 2);
        return INCOME_SECTIONS.stream().filter(s -> s.prefixes().contains(head)).map(IncomeSectionDef::key).findFirst().orElse(null);
    }

    /** Classes 6 and 7 make up the result of the period. */
    public static boolean isResultAccount(String code) {
        return code.charAt(0) == '6' || code.charAt(0) == '7';
    }
}
