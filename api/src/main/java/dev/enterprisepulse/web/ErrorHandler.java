package dev.enterprisepulse.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Every error leaves the API as {statusCode, message, requestId}. */
@RestControllerAdvice
public class ErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    public static void write(HttpServletResponse response, ObjectMapper json, int status, String message, String requestId) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(response.getOutputStream(), SecurityConfig.body(status, message, requestId));
    }

    private static ResponseEntity<Map<String, Object>> of(int status, String message) {
        return ResponseEntity.status(status).body(SecurityConfig.body(status, message, CurrentUser.get().requestId));
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, Object>> app(AppException e) {
        return of(e.status(), e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception e) {
        return of(400, "Geçersiz istek");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tooLarge(Exception e) {
        return of(400, "Dosya boyutu 20 MB'ı geçemez");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(Exception e) {
        return of(404, "Bulunamadı");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> method(Exception e) {
        return of(405, "Desteklenmeyen istek yöntemi");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> other(Exception e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof AppException app) return of(app.status(), app.getMessage());
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                switch (sql.getSQLState()) {
                    case "23505": return of(409, "Bu değer zaten kullanılıyor (tekil alan)");
                    case "23503": return of(409, "Kayıt başka kayıtlarda kullanıldığı için bu işlem yapılamaz");
                    case "23502": return of(400, "Zorunlu alan eksik");
                    case "22001": return of(400, "Değer çok uzun");
                    default: break;
                }
            }
        }
        log.error("Unhandled error", e);
        return of(500, "Beklenmeyen bir hata oluştu");
    }
}
