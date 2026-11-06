package dev.enterprisepulse.web.core;

import java.util.HashMap;
import java.util.Map;

/** UI strings that are not field labels (those come from the API metadata). */
public final class Texts {
    private Texts() {}

    private static final Map<String, String[]> ALL = new HashMap<>();

    private static void t(String key, String tr, String en) {
        ALL.put(key, new String[] {tr, en});
    }

    static {
        t("app", "EnterprisePulse", "EnterprisePulse");
        t("login", "Giriş yap", "Sign in");
        t("logout", "Çıkış yap", "Sign out");
        t("email", "E-posta", "E-mail");
        t("password", "Şifre", "Password");
        t("code2fa", "Doğrulama kodu", "Verification code");
        t("forgot", "Şifremi unuttum", "Forgot password");
        t("sendLink", "Bağlantı gönder", "Send link");
        t("resetPassword", "Şifreyi sıfırla", "Reset password");
        t("newPassword", "Yeni şifre", "New password");
        t("currentPassword", "Mevcut şifre", "Current password");
        t("backToLogin", "Girişe dön", "Back to sign in");
        t("demoAccounts", "Demo hesaplar", "Demo accounts");
        t("search", "Ara", "Search");
        t("new", "Yeni", "New");
        t("save", "Kaydet", "Save");
        t("cancel", "Vazgeç", "Cancel");
        t("delete", "Sil", "Delete");
        t("edit", "Düzenle", "Edit");
        t("close", "Kapat", "Close");
        t("back", "Geri", "Back");
        t("refresh", "Yenile", "Refresh");
        t("export", "Dışa aktar", "Export");
        t("filters", "Filtreler", "Filters");
        t("clear", "Temizle", "Clear");
        t("all", "Tümü", "All");
        t("yes", "Evet", "Yes");
        t("no", "Hayır", "No");
        t("saved", "Kaydedildi", "Saved");
        t("deleted", "Silindi", "Deleted");
        t("confirmDelete", "Bu kaydı silmek istediğinize emin misiniz?", "Are you sure you want to delete this record?");
        t("noRecords", "Kayıt bulunamadı", "No records found");
        t("loading", "Yükleniyor...", "Loading...");
        t("details", "Detaylar", "Details");
        t("history", "Geçmiş", "History");
        t("files", "Dosyalar", "Files");
        t("upload", "Dosya yükle", "Upload file");
        t("notifications", "Bildirimler", "Notifications");
        t("markAllRead", "Tümünü okundu yap", "Mark all as read");
        t("profile", "Profil", "Profile");
        t("settings", "Ayarlar", "Settings");
        t("company", "Şirket", "Company");
        t("branch", "Şube", "Branch");
        t("allBranches", "Tüm şubeler", "All branches");
        t("dashboard", "Gösterge Paneli", "Dashboard");
        t("approve", "Onayla", "Approve");
        t("reject", "Reddet", "Reject");
        t("comment", "Açıklama", "Comment");
        t("rejectReason", "Ret gerekçesi", "Rejection reason");
        t("withdraw", "Geri çek", "Withdraw");
        t("inbox", "Onay Kutum", "My approvals");
        t("myRequests", "Taleplerim", "My requests");
        t("approvalFlow", "Onay süreci", "Approval flow");
        t("step", "Adım", "Step");
        t("startedBy", "Başlatan", "Started by");
        t("startedAt", "Başlangıç", "Started");
        t("status", "Durum", "Status");
        t("actions", "İşlemler", "Actions");
        t("permissions", "İzinler", "Permissions");
        t("roles", "Roller", "Roles");
        t("users", "Kullanıcılar", "Users");
        t("twoFactor", "İki adımlı doğrulama", "Two-factor authentication");
        t("enable", "Etkinleştir", "Enable");
        t("disable", "Devre dışı bırak", "Disable");
        t("language", "Dil", "Language");
        t("theme", "Tema", "Theme");
        t("notFound", "Sayfa bulunamadı", "Page not found");
        t("forbidden", "Bu sayfa için yetkiniz yok", "You are not allowed to view this page");
        t("required", "Zorunlu alan", "Required");
        t("system", "Sistem", "System");
        t("total", "Toplam", "Total");
        t("send", "Gönder", "Send");
        t("submit", "Onaya gönder", "Submit for approval");
        t("add", "Ekle", "Add");
        t("lines", "Satırlar", "Lines");
        t("print", "Yazdır", "Print");
        t("confirm", "Onay", "Confirm");
    }

    public static String get(String key, String locale) {
        String[] t = ALL.get(key);
        return t == null ? key : "en".equals(locale) ? t[1] : t[0];
    }
}
