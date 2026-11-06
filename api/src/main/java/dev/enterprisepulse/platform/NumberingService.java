package dev.enterprisepulse.platform;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.NumberSequence;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NumberingService {
    private final Db db;

    public NumberingService(Db db) {
        this.db = db;
    }

    /** format("ZMT", 6, true, 2026, 42) == "ZMT-2026-000042" */
    public static String format(String prefix, int padding, boolean includeYear, int year, long value) {
        String sequence = String.format("%0" + padding + "d", value);
        return includeYear ? prefix + "-" + year + "-" + sequence : prefix + "-" + sequence;
    }

    public String next(String code) {
        return next(code, CurrentUser.get().requireCompanyId());
    }

    /**
     * Reserves the next number with a single atomic UPDATE ... RETURNING, so concurrent requests never
     * receive the same number. Runs inside the caller's transaction.
     */
    public String next(String code, UUID companyId) {
        int year = Year.now().getValue();
        List<Object[]> rows = db.nativeRows("""
                UPDATE number_sequences
                   SET next_value = CASE WHEN reset_yearly AND current_year <> ?1 THEN 2 ELSE next_value + 1 END,
                       current_year = ?1,
                       updated_at = now()
                 WHERE company_id = ?2 AND code = ?3
                RETURNING prefix, padding, include_year, next_value - 1
                """, year, companyId, code);
        if (rows.isEmpty()) throw AppException.notFound("Numara serisi tanımlı değil: " + code);
        Object[] r = rows.get(0);
        return format((String) r[0], ((Number) r[1]).intValue(), (Boolean) r[2], year, ((Number) r[3]).longValue());
    }

    public static String preview(NumberSequence s) {
        int year = Year.now().getValue();
        int next = s.resetYearly && s.currentYear != year ? 1 : s.nextValue;
        return format(s.prefix, s.padding, s.includeYear, year, next);
    }
}
