package dev.enterprisepulse.web.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.server.VaadinSession;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.StreamSupport;

/** Login state of one browser session; lives in the VaadinSession so it survives page reloads. */
public final class Session {
    public String accessToken;
    public String refreshToken;
    public JsonNode me;
    public String companyId;
    public String branchId;
    public String locale = "tr";
    public boolean dark;
    public Set<String> permissions = Set.of();

    private final ApiClient api = new ApiClient(this);
    private final Realtime realtime = new Realtime(this);

    public static Session current() {
        VaadinSession vs = VaadinSession.getCurrent();
        Session s = vs.getAttribute(Session.class);
        if (s == null) {
            s = new Session();
            vs.setAttribute(Session.class, s);
        }
        return s;
    }

    public ApiClient api() {
        return api;
    }

    public Realtime realtime() {
        return realtime;
    }

    public boolean isAuthenticated() {
        return me != null && accessToken != null;
    }

    public JsonNode user() {
        return me == null ? null : me.get("user");
    }

    public String userId() {
        return me == null ? null : user().path("id").asText();
    }

    public boolean isSuperAdmin() {
        return me != null && user().path("isSuperAdmin").asBoolean();
    }

    public boolean has(String code) {
        return isSuperAdmin() || permissions.contains(code);
    }

    public boolean hasAny(Collection<String> codes) {
        return codes == null || codes.isEmpty() || codes.stream().anyMatch(this::has);
    }

    public JsonNode company() {
        if (me == null) return null;
        for (JsonNode c : me.path("companies")) {
            if (c.path("id").asText().equals(companyId)) return c;
        }
        return null;
    }

    public String branchName() {
        JsonNode c = company();
        if (c == null || branchId == null) return null;
        for (JsonNode b : c.path("branches")) {
            if (b.path("id").asText().equals(branchId)) return b.path("name").asText();
        }
        return null;
    }

    public void signIn(String access, String refresh) {
        accessToken = access;
        refreshToken = refresh;
        loadMe();
    }

    public void loadMe() {
        try {
            me = api.get("/api/auth/me");
        } catch (ApiException ex) {
            if (companyId == null || ex.status == 401) throw ex;
            companyId = null;
            branchId = null;
            me = api.get("/api/auth/me");
        }
        companyId = me.path("activeCompanyId").asText(null);
        branchId = me.hasNonNull("activeBranchId") ? me.get("activeBranchId").asText() : null;
        Set<String> perms = new HashSet<>();
        StreamSupport.stream(me.path("permissions").spliterator(), false).forEach(p -> perms.add(p.asText()));
        permissions = perms;
    }

    public void switchCompany(String company, String branch) {
        companyId = company;
        branchId = branch;
        loadMe();
    }

    public void signOut() {
        if (refreshToken != null) {
            try {
                ObjectNode body = ApiClient.obj("refreshToken", refreshToken);
                api.post("/api/auth/logout", body);
            } catch (ApiException ignored) {
            }
        }
        clear();
    }

    void clear() {
        realtime.stop();
        accessToken = null;
        refreshToken = null;
        me = null;
        permissions = Set.of();
    }
}
