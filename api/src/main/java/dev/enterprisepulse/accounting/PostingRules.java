package dev.enterprisepulse.accounting;

import static dev.enterprisepulse.accounting.AccountingChart.Acc;
import static dev.enterprisepulse.accounting.AccountingMath.DraftLine;

import dev.enterprisepulse.data.generated.ChequeKind;
import dev.enterprisepulse.data.generated.PaymentMethod;
import dev.enterprisepulse.data.generated.StockMovementType;
import dev.enterprisepulse.data.generated.TreasuryAccountType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Posting rules (muhasebeleştirme kuralları). Every amount is already in the base currency. */
public final class PostingRules {
    private PostingRules() {}

    public static String treasuryCode(TreasuryAccountType type) {
        return type == TreasuryAccountType.CASH ? Acc.CASH : Acc.BANKS;
    }

    public static List<DraftLine> salesInvoice(UUID partnerId, BigDecimal total, BigDecimal vat) {
        return List.of(
                DraftLine.debit(Acc.RECEIVABLES, total, partnerId, null, null),
                DraftLine.credit(Acc.SALES, total.subtract(vat)),
                DraftLine.credit(Acc.OUTPUT_VAT, vat));
    }

    public static List<DraftLine> salesReturn(UUID partnerId, BigDecimal total, BigDecimal vat, BigDecimal cost) {
        return List.of(
                DraftLine.debit(Acc.SALES_RETURNS, total.subtract(vat)),
                DraftLine.debit(Acc.OUTPUT_VAT, vat),
                DraftLine.credit(Acc.RECEIVABLES, total, partnerId, null, null),
                DraftLine.debit(Acc.MERCHANDISE, cost),
                DraftLine.credit(Acc.COST_OF_GOODS_SOLD, cost));
    }

    public static List<DraftLine> purchaseInvoice(UUID partnerId, BigDecimal total, BigDecimal vat, BigDecimal goods) {
        BigDecimal net = total.subtract(vat);
        BigDecimal stocked = goods.min(net);
        return List.of(
                DraftLine.debit(Acc.MERCHANDISE, stocked),
                DraftLine.debit(Acc.GENERAL_ADMIN_EXPENSES, net.subtract(stocked)),
                DraftLine.debit(Acc.INPUT_VAT, vat),
                DraftLine.credit(Acc.PAYABLES, total, partnerId, null, null));
    }

    public static List<DraftLine> deliveryCost(BigDecimal cost) {
        return List.of(DraftLine.debit(Acc.COST_OF_GOODS_SOLD, cost), DraftLine.credit(Acc.MERCHANDISE, cost));
    }

    public static List<DraftLine> stockMovement(StockMovementType type, BigDecimal increase, BigDecimal decrease) {
        return switch (type) {
            case RECEIPT -> List.of(DraftLine.debit(Acc.MERCHANDISE, increase), DraftLine.credit(Acc.OTHER_TRADE_PAYABLES, increase));
            case ISSUE -> List.of(DraftLine.debit(Acc.GENERAL_ADMIN_EXPENSES, decrease), DraftLine.credit(Acc.MERCHANDISE, decrease));
            case ADJUSTMENT -> List.of(
                    DraftLine.debit(Acc.MERCHANDISE, increase), DraftLine.credit(Acc.OTHER_EXTRAORDINARY_INCOME, increase),
                    DraftLine.debit(Acc.OTHER_EXTRAORDINARY_EXPENSE, decrease), DraftLine.credit(Acc.MERCHANDISE, decrease));
            default -> List.of();
        };
    }

    public static List<DraftLine> production(BigDecimal material, BigDecimal labor, BigDecimal overhead) {
        return List.of(
                DraftLine.debit(Acc.DIRECT_MATERIALS, material),
                DraftLine.credit(Acc.MERCHANDISE, material),
                DraftLine.debit(Acc.MERCHANDISE, material.add(labor).add(overhead)),
                DraftLine.credit(Acc.DIRECT_MATERIALS_APPLIED, material),
                DraftLine.credit(Acc.DIRECT_LABOR_APPLIED, labor),
                DraftLine.credit(Acc.OVERHEAD_APPLIED, overhead));
    }

    private static String methodCode(PaymentMethod method, boolean incoming, boolean endorsed) {
        return switch (method) {
            case CASH -> Acc.CASH;
            case BANK -> Acc.BANKS;
            case CHEQUE -> incoming || endorsed ? Acc.CHEQUES_RECEIVED : Acc.CHEQUES_ISSUED;
            default -> incoming || endorsed ? Acc.NOTES_RECEIVABLE : Acc.NOTES_PAYABLE;
        };
    }

    public static List<DraftLine> collection(UUID partnerId, PaymentMethod method, BigDecimal amount) {
        return List.of(DraftLine.debit(methodCode(method, true, false), amount), DraftLine.credit(Acc.RECEIVABLES, amount, partnerId, null, null));
    }

    public static List<DraftLine> payment(UUID partnerId, PaymentMethod method, BigDecimal amount, boolean endorsed) {
        return List.of(DraftLine.debit(Acc.PAYABLES, amount, partnerId, null, null), DraftLine.credit(methodCode(method, false, endorsed), amount));
    }

    public static List<DraftLine> chequeCollection(ChequeKind kind, TreasuryAccountType treasury, BigDecimal amount, UUID partnerId) {
        String contra = kind == ChequeKind.CHEQUE ? Acc.CHEQUES_RECEIVED : Acc.NOTES_RECEIVABLE;
        return List.of(DraftLine.debit(treasuryCode(treasury), amount), DraftLine.credit(contra, amount, partnerId, null, null));
    }

    public static List<DraftLine> chequePayment(ChequeKind kind, TreasuryAccountType treasury, BigDecimal amount, UUID partnerId) {
        String debit = kind == ChequeKind.CHEQUE ? Acc.CHEQUES_ISSUED : Acc.NOTES_PAYABLE;
        return List.of(DraftLine.debit(debit, amount, partnerId, null, null), DraftLine.credit(treasuryCode(treasury), amount));
    }

    public static List<DraftLine> treasuryOpening(TreasuryAccountType treasury, BigDecimal amount) {
        return List.of(DraftLine.debit(treasuryCode(treasury), amount), DraftLine.credit(Acc.CAPITAL, amount));
    }

    public static List<DraftLine> partnerOpening(UUID partnerId, BigDecimal debit, BigDecimal credit) {
        return List.of(
                DraftLine.debit(Acc.RECEIVABLES, debit, partnerId, null, null),
                DraftLine.credit(Acc.CAPITAL, debit),
                DraftLine.debit(Acc.CAPITAL, credit),
                DraftLine.credit(Acc.PAYABLES, credit, partnerId, null, null));
    }

    public static List<DraftLine> expenseReport(UUID employeeId, BigDecimal total, BigDecimal vat) {
        return List.of(
                DraftLine.debit(Acc.GENERAL_ADMIN_EXPENSES, total.subtract(vat)),
                DraftLine.debit(Acc.INPUT_VAT, vat),
                DraftLine.credit(Acc.DUE_TO_EMPLOYEES, total, null, employeeId, null));
    }

    public static List<DraftLine> employeePayment(TreasuryAccountType treasury, BigDecimal amount, UUID employeeId) {
        return List.of(DraftLine.debit(Acc.DUE_TO_EMPLOYEES, amount, null, employeeId, null), DraftLine.credit(treasuryCode(treasury), amount));
    }

    public static List<DraftLine> payroll(BigDecimal net, BigDecimal tax, BigDecimal sgk, BigDecimal deductions) {
        return List.of(
                DraftLine.debit(Acc.GENERAL_ADMIN_EXPENSES, net.add(tax).add(sgk).add(deductions)),
                DraftLine.credit(Acc.DUE_TO_EMPLOYEES, net),
                DraftLine.credit(Acc.TAXES_PAYABLE, tax),
                DraftLine.credit(Acc.SOCIAL_SECURITY_PAYABLE, sgk),
                DraftLine.credit(Acc.OTHER_PAYABLES, deductions));
    }
}
