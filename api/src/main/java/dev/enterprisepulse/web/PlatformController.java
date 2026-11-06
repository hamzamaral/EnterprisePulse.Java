package dev.enterprisepulse.web;

import com.fasterxml.jackson.databind.JsonNode;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.common.Paged;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Company;
import dev.enterprisepulse.data.generated.FileObject;
import dev.enterprisepulse.data.generated.Notification;
import dev.enterprisepulse.data.generated.User;
import dev.enterprisepulse.data.generated.WorkflowStatus;
import dev.enterprisepulse.identity.UsersService;
import dev.enterprisepulse.meta.MetadataStore;
import dev.enterprisepulse.platform.Extensibility;
import dev.enterprisepulse.platform.FilesService;
import dev.enterprisepulse.platform.NotificationService;
import dev.enterprisepulse.platform.OrganizationService;
import dev.enterprisepulse.workflow.WorkflowService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "Platform")
public class PlatformController {
    public record CommentRequest(String comment) {}

    public record SendNotificationRequest(List<UUID> userIds, String title, String body, String link, boolean email) {}

    private final MetadataStore meta;
    private final List<Extensibility.DashboardProvider> dashboards;
    private final UsersService users;
    private final WorkflowService workflow;
    private final NotificationService notifications;
    private final FilesService files;
    private final OrganizationService org;
    private final Db db;

    public PlatformController(MetadataStore meta, List<Extensibility.DashboardProvider> dashboards, UsersService users, WorkflowService workflow,
                              NotificationService notifications, FilesService files, OrganizationService org, Db db) {
        this.meta = meta;
        this.dashboards = dashboards;
        this.users = users;
        this.workflow = workflow;
        this.notifications = notifications;
        this.files = files;
        this.org = org;
        this.db = db;
    }

    private static CurrentUser ctx() {
        return CurrentUser.get();
    }

    private static ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "time", Instant.now());
    }

    @GetMapping(value = "/api/meta", produces = MediaType.APPLICATION_JSON_VALUE)
    public String meta() {
        return meta.rawJson();
    }

    @GetMapping("/api/dashboard")
    public List<Extensibility.DashboardSection> dashboard() {
        List<Extensibility.DashboardSection> sections = new ArrayList<>();
        for (var p : dashboards.stream().sorted(Comparator.comparingInt(Extensibility.DashboardProvider::order)).toList()) {
            var section = p.section();
            if (section != null && !section.cards().isEmpty()) sections.add(section);
        }
        return sections;
    }

    // ---------- users & roles ----------

    @PostMapping("/api/users")
    @Tag(name = "Users & Roles")
    public User createUser(@RequestBody UsersService.CreateUserRequest body) {
        ctx().require("users:create");
        return users.create(body);
    }

    @GetMapping("/api/users/{id}/roles")
    public List<UUID> userRoles(@PathVariable UUID id) {
        ctx().require("users:read");
        return users.roleIds(id);
    }

    @PutMapping("/api/users/{id}/roles")
    public ResponseEntity<Void> setUserRoles(@PathVariable UUID id, @RequestBody UsersService.UserRolesRequest body) {
        ctx().require("users:update");
        users.updateRoles(id, body);
        return noContent();
    }

    @DeleteMapping("/api/users/{id}/membership")
    public ResponseEntity<Void> removeMembership(@PathVariable UUID id) {
        ctx().require("users:delete");
        users.removeFromCompany(id);
        return noContent();
    }

    @GetMapping("/api/roles/details")
    public List<UsersService.RoleDetail> roles() {
        ctx().require("roles:read");
        return users.roles();
    }

    @PostMapping("/api/roles/details")
    public UsersService.RoleDetail createRole(@RequestBody UsersService.RoleRequest body) {
        return users.saveRole(null, body);
    }

    @PutMapping("/api/roles/details/{id}")
    public UsersService.RoleDetail updateRole(@PathVariable UUID id, @RequestBody UsersService.RoleRequest body) {
        return users.saveRole(id, body);
    }

    @DeleteMapping("/api/roles/details/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
        users.deleteRole(id);
        return noContent();
    }

    // ---------- workflow ----------

    @GetMapping("/api/workflow/inbox")
    public Paged<WorkflowService.InstanceDto> inbox(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "25") int pageSize) {
        return workflow.inbox(page, pageSize);
    }

    @GetMapping("/api/workflow/inbox/count")
    public Map<String, Integer> inboxCount() {
        return Map.of("count", workflow.inboxCount());
    }

    @GetMapping("/api/workflow/instances")
    public Paged<WorkflowService.InstanceDto> instances(@RequestParam(required = false) WorkflowStatus status, @RequestParam(required = false) String entityType,
                                                        @RequestParam(defaultValue = "false") boolean mine, @RequestParam(required = false) String search,
                                                        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "25") int pageSize) {
        return workflow.list(status, entityType, mine, search, page, pageSize);
    }

    @GetMapping("/api/workflow/instances/{id}")
    public WorkflowService.InstanceDto instance(@PathVariable UUID id) {
        return workflow.get(id);
    }

    @GetMapping("/api/workflow/entity/{entityType}/{entityId}")
    public ResponseEntity<WorkflowService.InstanceDto> forEntity(@PathVariable String entityType, @PathVariable String entityId) {
        var dto = workflow.forEntity(entityType, entityId);
        return dto == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(dto);
    }

    @PostMapping("/api/workflow/start")
    public WorkflowService.InstanceDto start(@RequestBody WorkflowService.StartRequest body) {
        ctx().require("workflow:start");
        return workflow.start(body);
    }

    @PostMapping("/api/workflow/instances/{id}/approve")
    public WorkflowService.InstanceDto approve(@PathVariable UUID id, @RequestBody(required = false) CommentRequest body) {
        return workflow.approve(id, body == null ? null : body.comment());
    }

    @PostMapping("/api/workflow/instances/{id}/reject")
    public WorkflowService.InstanceDto reject(@PathVariable UUID id, @RequestBody(required = false) CommentRequest body) {
        return workflow.reject(id, body == null || body.comment() == null ? "" : body.comment());
    }

    @PostMapping("/api/workflow/instances/{id}/cancel")
    public WorkflowService.InstanceDto cancel(@PathVariable UUID id, @RequestBody(required = false) CommentRequest body) {
        return workflow.cancel(id, body == null ? null : body.comment());
    }

    // ---------- notifications ----------

    private static final String MINE = " from Notification n where n.userId = ?1 and (n.companyId is null or n.companyId = ?2)";

    @GetMapping("/api/notifications")
    @Transactional(readOnly = true)
    public Paged<Notification> notifications(@RequestParam(required = false) Boolean unread, @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int pageSize) {
        int p = Math.max(1, page), size = Math.max(1, Math.min(100, pageSize));
        String where = MINE + (Boolean.TRUE.equals(unread) ? " and n.readAt is null" : "");
        UUID user = ctx().requireUserId();
        long total = db.count("select count(n)" + where, user, ctx().companyId);
        List<Notification> rows = db.query("select n" + where + " order by n.createdAt desc", Notification.class, user, ctx().companyId)
                .setFirstResult((p - 1) * size).setMaxResults(size).getResultList();
        return Paged.of(rows, total, p, size);
    }

    @GetMapping("/api/notifications/unread-count")
    @Transactional(readOnly = true)
    public Map<String, Long> unread() {
        return Map.of("count", db.count("select count(n)" + MINE + " and n.readAt is null", ctx().requireUserId(), ctx().companyId));
    }

    @PostMapping("/api/notifications/{id}/read")
    @Transactional
    public ResponseEntity<Void> read(@PathVariable UUID id) {
        db.execute("update Notification n set n.readAt = ?3 where n.id = ?4 and n.readAt is null and n.userId = ?1 and (n.companyId is null or n.companyId = ?2)",
                ctx().requireUserId(), ctx().companyId, Instant.now(), id);
        return noContent();
    }

    @PostMapping("/api/notifications/read-all")
    @Transactional
    public ResponseEntity<Void> readAll() {
        db.execute("update Notification n set n.readAt = ?3 where n.readAt is null and n.userId = ?1 and (n.companyId is null or n.companyId = ?2)",
                ctx().requireUserId(), ctx().companyId, Instant.now());
        return noContent();
    }

    @PostMapping("/api/notifications/send")
    public ResponseEntity<Void> send(@RequestBody SendNotificationRequest body) {
        ctx().require("notifications:send");
        Guard.that(body.userIds() != null && !body.userIds().isEmpty() && !Guard.blank(body.title()), "Alıcı ve başlık zorunludur");
        notifications.notify(new NotificationService.NotifyRequest(body.userIds(), ctx().companyId, "announcement", body.title(),
                body.body() == null ? "" : body.body(), body.link(), body.email()));
        return noContent();
    }

    // ---------- files ----------

    @PostMapping(value = "/api/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileObject upload(@RequestPart("file") MultipartFile file, @RequestParam(required = false) String entityType,
                             @RequestParam(required = false) String entityId) throws IOException {
        ctx().require("files:create");
        Guard.that(!file.isEmpty(), "Dosya seçilmedi");
        String type = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        return files.upload(file.getBytes(), file.getOriginalFilename(), type, Guard.blank(entityType) ? null : entityType, Guard.blank(entityId) ? null : entityId);
    }

    @GetMapping("/api/files")
    public List<FileObject> filesFor(@RequestParam String entityType, @RequestParam String entityId) {
        ctx().require("files:read");
        return files.forEntity(entityType, entityId);
    }

    @GetMapping("/api/files/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        ctx().require("files:read");
        var d = files.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.file().mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.file().originalName, java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(d.content());
    }

    @DeleteMapping("/api/files/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable UUID id) {
        files.delete(id);
        return noContent();
    }

    // ---------- settings & companies ----------

    @GetMapping("/api/settings/{key}")
    public JsonNode setting(@PathVariable String key) {
        ctx().require("settings:read");
        return org.getSetting(key);
    }

    @PutMapping("/api/settings/{key}")
    public JsonNode saveSetting(@PathVariable String key, @RequestBody JsonNode body) {
        ctx().require("settings:update");
        return org.saveSetting(key, body);
    }

    @PostMapping("/api/companies")
    public Company createCompany(@RequestBody OrganizationService.CreateCompanyRequest body) {
        ctx().require("companies:create");
        return org.createCompany(body);
    }
}
