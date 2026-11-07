package dev.enterprisepulse.finance;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.Cheque;
import dev.enterprisepulse.data.generated.ChequeKind;
import dev.enterprisepulse.data.generated.InvoiceStatus;
import dev.enterprisepulse.data.generated.LedgerDocumentType;
import dev.enterprisepulse.data.generated.PartnerPayment;
import dev.enterprisepulse.data.generated.PaymentAllocation;
import dev.enterprisepulse.data.generated.PaymentDirection;
import dev.enterprisepulse.data.generated.PaymentMethod;
import dev.enterprisepulse.data.generated.PaymentRecordStatus;
import dev.enterprisepulse.data.generated.PurchaseInvoice;
import dev.enterprisepulse.data.generated.SalesInvoice;
import dev.enterprisepulse.data.generated.TreasuryAccount;
import dev.enterprisepulse.data.generated.TreasuryAccountType;
import dev.enterprisepulse.data.generated.TreasuryDocumentType;
import dev.enterprisepulse.partners.PartnerLedgerService;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.trade.DocumentMath;
import dev.enterprisepulse.trade.TradeService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentService {
    public record AllocationIn(UUID salesInvoiceId, UUID purchaseInvoiceId, BigDecimal amount) {}

    public record Create(PaymentDirection direction, PaymentMethod method, UUID partnerId, LocalDate paymentDate, UUID accountId, BigDecimal amount,
                          String currencyCode, BigDecimal exchangeRate, String description, String chequeSerial, String bankName, String branchName, String drawer,
                          LocalDate dueDate, UUID endorseChequeId, List<AllocationIn> allocations) {}

    public record AllocationDto(UUID salesInvoiceId, String salesInvoiceNumber, UUID purchaseInvoiceId, String purchaseInvoiceNumber, BigDecimal amount) {}

    public record Detail(UUID id, String number, PaymentDirection direction, PaymentMethod method, UUID partnerId, String partnerTitle, LocalDate paymentDate,
                          UUID accountId, String accountName, BigDecimal amount, String currencyCode, BigDecimal exchangeRate, BigDecimal amountBase, String description,
                          PaymentRecordStatus status, UUID chequeId, String chequeNumber, List<AllocationDto> allocations) {}

    private final Db db;
    private final NumberingService numbering;
    private final TradeService trade;
    private final PartnerLedgerService ledger;
    private final TreasuryService treasury;
    private final ChequeService cheques;

    public PaymentService(Db db, NumberingService numbering, TradeService trade, PartnerLedgerService ledger, TreasuryService treasury, ChequeService cheques) {
        this.db = db;
        this.numbering = numbering;
        this.trade = trade;
        this.ledger = ledger;
        this.treasury = treasury;
        this.cheques = cheques;
    }

    public Detail create(Create body) {
        Guard.that(body.amount() != null && body.amount().signum() > 0, "Tutar sıfırdan büyük olmalı");
        BusinessPartner partner = ledger.forDocument(body.partnerId(), body.direction() == PaymentDirection.IN ? PartnerLedgerService.CUSTOMERS : PartnerLedgerService.SUPPLIERS);
        LocalDate date = body.paymentDate() == null ? LocalDate.now() : body.paymentDate();
        var currency = trade.resolveCurrency(body.currencyCode() == null ? partner.currencyCode : body.currencyCode(), body.exchangeRate(), date);
        TreasuryAccount account = null;
        if (body.method() == PaymentMethod.CASH || body.method() == PaymentMethod.BANK) {
            account = Guard.found(db.first("select a from TreasuryAccount a where a.id = ?1 and a.isActive = true", TreasuryAccount.class, body.accountId()), "Kasa veya banka hesabı seçilmeli");
            Guard.that(body.method() == PaymentMethod.CASH ? account.type == TreasuryAccountType.CASH : account.type == TreasuryAccountType.BANK, "Hesap türü ödeme yöntemiyle uyuşmuyor");
            Guard.that(account.currencyCode.equals(currency.currencyCode()), "Hesabın para birimi belgenin para birimiyle aynı olmalı");
        } else {
            Guard.that(body.endorseChequeId() != null || (body.chequeSerial() != null && !body.chequeSerial().isBlank()), "Çek seri numarası gerekli");
            if (body.endorseChequeId() == null) Guard.that(body.dueDate() != null, "Çek vadesi gerekli");
        }
        PartnerPayment payment = new PartnerPayment();
        payment.number = numbering.next(body.direction() == PaymentDirection.IN ? "COLLECTION" : "PAYMENT");
        payment.direction = body.direction();
        payment.method = body.method();
        payment.partnerId = partner.id;
        payment.paymentDate = date;
        payment.accountId = account == null ? null : account.id;
        payment.amount = DocumentMath.round2(body.amount());
        payment.currencyCode = currency.currencyCode();
        payment.exchangeRate = currency.exchangeRate();
        payment.amountBase = DocumentMath.toBase(body.amount(), currency.exchangeRate());
        payment.description = blank(body.description());
        payment.createdById = CurrentUser.get().requireUserId();
        db.add(payment);
        db.flush();
        allocate(payment, body.allocations() == null ? List.of() : body.allocations());
        if (account != null) {
            boolean inbound = body.direction() == PaymentDirection.IN;
            treasury.post(account.id, date, inbound ? TreasuryDocumentType.COLLECTION : TreasuryDocumentType.PAYMENT, payment.id, payment.number, payment.description,
                    inbound ? payment.amount : BigDecimal.ZERO, inbound ? BigDecimal.ZERO : payment.amount);
        } else if (body.endorseChequeId() != null) {
            Guard.that(body.direction() == PaymentDirection.OUT && body.method() == PaymentMethod.CHEQUE, "Ciro yalnızca çek ile yapılan ödemede kullanılır");
            cheques.endorse(body.endorseChequeId(), payment);
        } else {
            ChequeKind kind = body.method() == PaymentMethod.NOTE ? ChequeKind.NOTE : ChequeKind.CHEQUE;
            cheques.open(payment, kind, body.chequeSerial(), body.bankName(), body.branchName(), body.drawer(), body.dueDate(), numbering.next(kind == ChequeKind.NOTE ? "PROMISSORY_NOTE" : "CHEQUE"));
        }
        boolean incoming = body.direction() == PaymentDirection.IN;
        ledger.post(new PartnerLedgerService.Posting(partner.id, date, body.dueDate(), incoming ? LedgerDocumentType.COLLECTION : LedgerDocumentType.PAYMENT, payment.id,
                payment.number, payment.description == null ? (incoming ? "Tahsilat" : "Ödeme") : payment.description, incoming ? BigDecimal.ZERO : payment.amount,
                incoming ? payment.amount : BigDecimal.ZERO, currency.currencyCode(), currency.exchangeRate()));
        return get(payment.id);
    }

    public Detail cancel(UUID id, String reason) {
        db.nativeRows("SELECT id FROM partner_payments WHERE id = ?1 FOR UPDATE", id);
        PartnerPayment payment = Guard.found(db.first("select p from PartnerPayment p where p.id = ?1", PartnerPayment.class, id), "Tahsilat veya ödeme bulunamadı");
        Guard.state(payment.status == PaymentRecordStatus.POSTED, "Belge zaten iptal edilmiş");
        LocalDate date = LocalDate.now();
        for (PaymentAllocation allocation : db.list("select a from PaymentAllocation a where a.paymentId = ?1", PaymentAllocation.class, id)) {
            if (allocation.salesInvoiceId != null) InvoicePayments.apply(db.get(SalesInvoice.class, allocation.salesInvoiceId), allocation.amount.negate());
            if (allocation.purchaseInvoiceId != null) InvoicePayments.apply(db.get(PurchaseInvoice.class, allocation.purchaseInvoiceId), allocation.amount.negate());
        }
        if (payment.method == PaymentMethod.CASH || payment.method == PaymentMethod.BANK) {
            treasury.reverse(payment.direction == PaymentDirection.IN ? TreasuryDocumentType.COLLECTION : TreasuryDocumentType.PAYMENT, payment.id, date, "İptal: " + payment.number);
        } else cheques.cancelWithPayment(payment, date);
        ledger.reverse(payment.direction == PaymentDirection.IN ? LedgerDocumentType.COLLECTION : LedgerDocumentType.PAYMENT, payment.id, date,
                "İptal: " + payment.number + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()));
        payment.status = PaymentRecordStatus.CANCELLED;
        payment.cancelledAt = Instant.now();
        return get(id);
    }

    public Detail get(UUID id) {
        PartnerPayment payment = Guard.found(db.first("select p from PartnerPayment p where p.id = ?1", PartnerPayment.class, id), "Tahsilat veya ödeme bulunamadı");
        BusinessPartner partner = db.get(BusinessPartner.class, payment.partnerId);
        TreasuryAccount account = payment.accountId == null ? null : db.find(TreasuryAccount.class, payment.accountId);
        Cheque cheque = db.first("select c from Cheque c where c.originPaymentId = ?1", Cheque.class, payment.id).orElse(null);
        List<AllocationDto> allocations = new ArrayList<>();
        for (PaymentAllocation allocation : db.list("select a from PaymentAllocation a where a.paymentId = ?1", PaymentAllocation.class, id)) {
            SalesInvoice sales = allocation.salesInvoiceId == null ? null : db.find(SalesInvoice.class, allocation.salesInvoiceId);
            PurchaseInvoice purchase = allocation.purchaseInvoiceId == null ? null : db.find(PurchaseInvoice.class, allocation.purchaseInvoiceId);
            allocations.add(new AllocationDto(allocation.salesInvoiceId, sales == null ? null : sales.number, allocation.purchaseInvoiceId, purchase == null ? null : purchase.number, allocation.amount));
        }
        return new Detail(payment.id, payment.number, payment.direction, payment.method, payment.partnerId, partner.title, payment.paymentDate, payment.accountId,
                account == null ? null : account.name, payment.amount, payment.currencyCode, payment.exchangeRate, payment.amountBase, payment.description, payment.status,
                cheque == null ? null : cheque.id, cheque == null ? null : cheque.number, allocations);
    }

    private void allocate(PartnerPayment payment, List<AllocationIn> allocations) {
        BigDecimal allocated = BigDecimal.ZERO;
        for (AllocationIn row : allocations) {
            Guard.that(row.amount() != null && row.amount().signum() > 0, "Mahsup tutarı sıfırdan büyük olmalı");
            Guard.that((row.salesInvoiceId() != null) != (row.purchaseInvoiceId() != null), "Mahsup tek bir faturaya yazılır");
            if (row.salesInvoiceId() != null) {
                Guard.that(payment.direction == PaymentDirection.IN, "Satış faturası yalnızca tahsilata mahsup edilir");
                db.nativeRows("SELECT id FROM sales_invoices WHERE id = ?1 FOR UPDATE", row.salesInvoiceId());
                SalesInvoice invoice = Guard.found(db.first("select i from SalesInvoice i where i.id = ?1", SalesInvoice.class, row.salesInvoiceId()), "Satış faturası bulunamadı");
                Guard.state(invoice.status == InvoiceStatus.ISSUED && invoice.partnerId.equals(payment.partnerId), "Fatura bu cariye ait ve açık olmalı");
                Guard.that(invoice.currencyCode.equals(payment.currencyCode), "Fatura para birimi belgenin para birimiyle aynı olmalı");
                BigDecimal open = invoice.grandTotal.subtract(invoice.paidAmount);
                Guard.that(row.amount().compareTo(open.add(new BigDecimal("0.004"))) <= 0, invoice.number + " açık tutarı aşılamaz");
                InvoicePayments.apply(invoice, row.amount());
                add(payment, row.salesInvoiceId(), null, row.amount());
            } else {
                Guard.that(payment.direction == PaymentDirection.OUT, "Alış faturası yalnızca ödemeye mahsup edilir");
                db.nativeRows("SELECT id FROM purchase_invoices WHERE id = ?1 FOR UPDATE", row.purchaseInvoiceId());
                PurchaseInvoice invoice = Guard.found(db.first("select i from PurchaseInvoice i where i.id = ?1", PurchaseInvoice.class, row.purchaseInvoiceId()), "Alış faturası bulunamadı");
                Guard.state(invoice.status == InvoiceStatus.ISSUED && invoice.partnerId.equals(payment.partnerId), "Fatura bu cariye ait ve açık olmalı");
                Guard.that(invoice.currencyCode.equals(payment.currencyCode), "Fatura para birimi belgenin para birimiyle aynı olmalı");
                BigDecimal open = invoice.grandTotal.subtract(invoice.paidAmount);
                Guard.that(row.amount().compareTo(open.add(new BigDecimal("0.004"))) <= 0, invoice.number + " açık tutarı aşılamaz");
                InvoicePayments.apply(invoice, row.amount());
                add(payment, null, row.purchaseInvoiceId(), row.amount());
            }
            allocated = allocated.add(row.amount());
        }
        Guard.that(allocated.compareTo(payment.amount.add(new BigDecimal("0.004"))) <= 0, "Mahsuplar belge tutarını aşıyor");
    }

    private void add(PartnerPayment payment, UUID salesId, UUID purchaseId, BigDecimal amount) {
        PaymentAllocation row = new PaymentAllocation();
        row.paymentId = payment.id;
        row.salesInvoiceId = salesId;
        row.purchaseInvoiceId = purchaseId;
        row.amount = DocumentMath.round2(amount);
        db.add(row);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
