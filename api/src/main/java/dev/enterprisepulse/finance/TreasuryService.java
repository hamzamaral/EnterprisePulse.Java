package dev.enterprisepulse.finance;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.TreasuryAccount;
import dev.enterprisepulse.data.generated.TreasuryDocumentType;
import dev.enterprisepulse.data.generated.TreasuryTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TreasuryService {
    public record AccountDto(UUID id, String code, String name, dev.enterprisepulse.data.generated.TreasuryAccountType type, String currencyCode, BigDecimal balance, boolean isActive) {}

    public record TxDto(UUID id, LocalDate txDate, TreasuryDocumentType documentType, String documentNumber, String description, BigDecimal amountIn, BigDecimal amountOut) {}

    private final Db db;

    public TreasuryService(Db db) {
        this.db = db;
    }

    public BigDecimal balance(UUID accountId) {
        return db.first("select coalesce(sum(t.amountIn - t.amountOut), 0) from TreasuryTransaction t where t.accountId = ?1", BigDecimal.class, accountId).orElse(BigDecimal.ZERO);
    }

    public List<AccountDto> accounts() {
        List<AccountDto> rows = new ArrayList<>();
        for (TreasuryAccount account : db.list("select a from TreasuryAccount a order by a.code", TreasuryAccount.class)) {
            rows.add(new AccountDto(account.id, account.code, account.name, account.type, account.currencyCode, balance(account.id), account.isActive));
        }
        return rows;
    }

    public List<TxDto> transactions(UUID accountId) {
        List<TxDto> rows = new ArrayList<>();
        for (TreasuryTransaction tx : db.list("select t from TreasuryTransaction t where t.accountId = ?1 order by t.txDate desc, t.seq desc", TreasuryTransaction.class, accountId)) {
            rows.add(new TxDto(tx.id, tx.txDate, tx.documentType, tx.documentNumber, tx.description, tx.amountIn, tx.amountOut));
        }
        return rows;
    }

    public void post(UUID accountId, LocalDate date, TreasuryDocumentType type, UUID documentId, String number, String description, BigDecimal amountIn, BigDecimal amountOut) {
        Guard.that(amountIn.signum() >= 0 && amountOut.signum() >= 0 && amountIn.signum() > 0 != amountOut.signum() > 0, "Kasa hareketinin tek yönü olmalı");
        TreasuryAccount account = Guard.found(db.first("select a from TreasuryAccount a where a.id = ?1 and a.isActive = true", TreasuryAccount.class, accountId), "Kasa veya banka hesabı bulunamadı");
        if (amountOut.signum() > 0) {
            BigDecimal balance = balance(account.id);
            Guard.that(balance.add(new BigDecimal("0.004")).compareTo(amountOut) >= 0, account.name + " bakiyesi yetersiz");
        }
        TreasuryTransaction tx = new TreasuryTransaction();
        tx.accountId = account.id;
        tx.txDate = date;
        tx.documentType = type;
        tx.documentId = documentId;
        tx.documentNumber = number;
        tx.description = description == null || description.isBlank() ? null : description.trim();
        tx.amountIn = amountIn;
        tx.amountOut = amountOut;
        tx.createdById = CurrentUser.get().requireUserId();
        db.add(tx);
        db.flush();
    }

    public void reverse(TreasuryDocumentType type, UUID documentId, LocalDate date, String description) {
        List<TreasuryTransaction> entries = db.list("select t from TreasuryTransaction t where t.documentType = ?1 and t.documentId = ?2 and t.reversesId is null", TreasuryTransaction.class, type, documentId);
        for (TreasuryTransaction entry : entries) {
            if (db.exists("select r.id from TreasuryTransaction r where r.reversesId = ?1", entry.id)) continue;
            TreasuryTransaction reversal = new TreasuryTransaction();
            reversal.accountId = entry.accountId;
            reversal.txDate = date;
            reversal.documentType = type;
            reversal.documentId = documentId;
            reversal.documentNumber = entry.documentNumber;
            reversal.description = description;
            reversal.amountIn = entry.amountOut;
            reversal.amountOut = entry.amountIn;
            reversal.reversesId = entry.id;
            reversal.createdById = CurrentUser.get().requireUserId();
            db.add(reversal);
        }
    }
}
