package dev.enterprisepulse.web.views;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.theme.lumo.Lumo;
import dev.enterprisepulse.web.core.Features;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Meta;
import dev.enterprisepulse.web.core.Meta.NavGroup;
import dev.enterprisepulse.web.core.Meta.NavItem;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import java.util.List;
import java.util.function.Consumer;

/** Shell for signed-in pages: menu from metadata, company/branch switcher, language, theme, notifications. */
public class MainLayout extends AppLayout implements BeforeEnterObserver, AfterNavigationObserver {
    private boolean built;
    private final Span unreadBadge = new Span();
    private int unread;
    private Consumer<JsonNode> listener;

    public MainLayout() {
        setPrimarySection(Section.NAVBAR);
        addClassName("ep-shell");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Session s = Session.current();
        if (!s.isAuthenticated()) {
            String path = event.getLocation().getPathWithQueryParameters();
            event.rerouteTo("login", path.isEmpty() ? QueryParameters.empty() : QueryParameters.of("returnUrl", "/" + path));
            return;
        }
        if (!built) build();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        if (built) refreshUnread();
    }

    private void build() {
        Session s = Session.current();
        if (!Ui.run(() -> Meta.load(s.api()))) return;
        built = true;
        UI ui = UI.getCurrent();
        ui.setLocale(Fmt.locale(s.locale));
        if (s.dark) ui.getElement().getThemeList().add(Lumo.DARK);

        DrawerToggle toggle = new DrawerToggle();
        Div brand = new Div(VaadinIcon.CHART_TIMELINE.create(), new Span("EnterprisePulse"));
        brand.addClassName("ep-brand");
        brand.getElement().addEventListener("click", e -> ui.navigate(""));
        Div spacer = new Div();
        spacer.addClassName("ep-grow");

        Button lang = new Button(s.locale.toUpperCase(), e -> {
            s.locale = "tr".equals(s.locale) ? "en" : "tr";
            ui.getPage().reload();
        });
        lang.setTooltipText(Ui.L("language"));
        lang.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        lang.addClassName("ep-nav-btn");

        Button theme = new Button(new Icon(s.dark ? VaadinIcon.SUN_O : VaadinIcon.MOON_O));
        theme.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ICON);
        theme.addClassName("ep-nav-btn");
        theme.setTooltipText(Ui.L("theme"));
        theme.addClickListener(e -> {
            s.dark = !s.dark;
            if (s.dark) ui.getElement().getThemeList().add(Lumo.DARK);
            else ui.getElement().getThemeList().remove(Lumo.DARK);
            theme.setIcon(new Icon(s.dark ? VaadinIcon.SUN_O : VaadinIcon.MOON_O));
        });

        Button bell = new Button(new Icon(VaadinIcon.BELL), e -> ui.navigate("notifications"));
        bell.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ICON);
        bell.addClassName("ep-nav-btn");
        bell.setTooltipText(Ui.L("notifications"));
        unreadBadge.addClassName("ep-counter");
        Div bellBox = new Div(bell, unreadBadge);
        bellBox.addClassName("ep-bell");

        addToNavbar(toggle, brand, spacer, companyMenu(s), lang, theme, bellBox, userMenu(s));
        Scroller scroller = new Scroller(navigation(s));
        scroller.addClassName("ep-drawer");
        addToDrawer(scroller);
        refreshUnread();
    }

    private MenuBar companyMenu(Session s) {
        MenuBar bar = new MenuBar();
        bar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);
        bar.addClassName("ep-company-menu");
        JsonNode company = s.company();
        String branch = s.branchName();
        Div label = new Div(VaadinIcon.BUILDING.create(), new Span((company == null ? "" : company.path("name").asText()) + (branch != null ? " · " + branch : "")));
        label.addClassName("ep-row");
        MenuItem root = bar.addItem(label);
        SubMenu sub = root.getSubMenu();
        for (JsonNode c : s.me.path("companies")) {
            String id = c.path("id").asText();
            boolean active = id.equals(s.companyId);
            MenuItem item = sub.addItem((active ? "✓ " : "   ") + c.path("name").asText(), e -> switchCompany(id, null));
            item.addClassName("ep-menu-company");
            if (active) {
                MenuItem all = sub.addItem("    ↳ " + Ui.L("allBranches"), e -> switchCompany(id, null));
                if (s.branchId == null) all.addClassName("ep-menu-active");
                for (JsonNode b : c.path("branches")) {
                    String bid = b.path("id").asText();
                    MenuItem bi = sub.addItem("    ↳ " + b.path("name").asText(), e -> switchCompany(id, bid));
                    if (bid.equals(s.branchId)) bi.addClassName("ep-menu-active");
                }
            }
        }
        return bar;
    }

    private void switchCompany(String companyId, String branchId) {
        Session s = Session.current();
        if (Ui.run(() -> s.switchCompany(companyId, branchId))) UI.getCurrent().getPage().setLocation("/");
    }

    private MenuBar userMenu(Session s) {
        JsonNode u = s.user();
        String first = u.path("firstName").asText();
        String last = u.path("lastName").asText();
        Avatar avatar = new Avatar(first + " " + last);
        avatar.addClassName("ep-avatar");
        MenuBar bar = new MenuBar();
        bar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);
        MenuItem root = bar.addItem(avatar);
        SubMenu sub = root.getSubMenu();
        Div who = new Div(new Div(new Span(first + " " + last)), Ui.muted(u.path("email").asText()));
        who.addClassName("ep-user-card");
        sub.addItem(who).setEnabled(false);
        sub.addItem(new Div(VaadinIcon.USER.create(), new Span(" " + Ui.L("profile"))), e -> UI.getCurrent().navigate("profile"));
        sub.addItem(new Div(VaadinIcon.SIGN_OUT.create(), new Span(" " + Ui.L("logout"))), e -> {
            s.signOut();
            UI.getCurrent().getPage().setLocation("login");
        });
        return bar;
    }

    private Div navigation(Session s) {
        Div box = new Div();
        Meta.Document meta = Meta.get();
        List<NavGroup> groups = meta.navigation.stream().filter(Features::enabled).toList();
        for (int i = 0; i < groups.size(); i++) {
            NavGroup g = groups.get(i);
            List<NavItem> items = g.items().stream().filter(it -> Features.enabled(it) && s.hasAny(it.permission())).toList();
            if (items.isEmpty()) continue;
            SideNav nav = new SideNav();
            nav.setWidthFull();
            if (i == 0) {
                for (NavItem it : items) {
                    SideNavItem item = new SideNavItem(Ui.T(it.label()), it.href(), (it.href().isEmpty() ? VaadinIcon.DASHBOARD : VaadinIcon.CHECK_SQUARE_O).create());
                    nav.addItem(item);
                }
            } else {
                SideNavItem group = new SideNavItem(Ui.T(g.label()));
                group.setPrefixComponent(groupIcon(g.label().tr()).create());
                for (NavItem it : items) group.addItem(new SideNavItem(Ui.T(it.label()), it.href()));
                nav.addItem(group);
            }
            box.add(nav);
        }
        return box;
    }

    private void refreshUnread() {
        try {
            JsonNode r = Session.current().api().get("/api/notifications/unread-count");
            unread = r == null ? 0 : r.path("count").asInt();
        } catch (RuntimeException ignored) {
        }
        unreadBadge.setText(String.valueOf(unread));
        unreadBadge.setVisible(unread > 0);
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        Session s = Session.current();
        if (!s.isAuthenticated()) return;
        UI ui = event.getUI();
        listener = n -> ui.access(() -> {
            unread++;
            unreadBadge.setText(String.valueOf(unread));
            unreadBadge.setVisible(true);
            Notification note = new Notification(n.path("title").asText(), 5000, Notification.Position.BOTTOM_END);
            note.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
            String link = n.path("link").asText(null);
            if (link != null) note.getElement().addEventListener("click", e -> Ui.go(link));
            note.open();
        });
        s.realtime().subscribe(listener);
        s.realtime().start();
    }

    @Override
    protected void onDetach(DetachEvent event) {
        if (listener != null) Session.current().realtime().unsubscribe(listener);
        super.onDetach(event);
    }

    private static VaadinIcon groupIcon(String label) {
        return switch (label) {
            case "İnsan Kaynakları" -> VaadinIcon.USER_CARD;
            case "Masraf ve Harcırah" -> VaadinIcon.FILE_TEXT_O;
            case "Bordro" -> VaadinIcon.MONEY;
            case "İşe Alım" -> VaadinIcon.SEARCH;
            case "Performans ve Eğitim" -> VaadinIcon.ACADEMY_CAP;
            case "Destek" -> VaadinIcon.HEADSET;
            case "Proje Yönetimi" -> VaadinIcon.TASKS;
            case "Zimmet ve Demirbaş" -> VaadinIcon.PACKAGE;
            case "Stok ve Depo" -> VaadinIcon.STORAGE;
            case "Üretim" -> VaadinIcon.FACTORY;
            case "Satın Alma" -> VaadinIcon.CART;
            case "CRM" -> VaadinIcon.HANDSHAKE;
            case "Satış" -> VaadinIcon.CASH;
            case "Cari ve Finans" -> VaadinIcon.INSTITUTION;
            case "Muhasebe" -> VaadinIcon.CALC_BOOK;
            case "Sözleşmeler" -> VaadinIcon.GAVEL;
            case "Bütçe" -> VaadinIcon.PIE_CHART;
            case "Araç ve Filo" -> VaadinIcon.CAR;
            case "Raporlar" -> VaadinIcon.BAR_CHART;
            case "Onay Süreçleri" -> VaadinIcon.SITEMAP;
            case "Organizasyon" -> VaadinIcon.BUILDING;
            default -> VaadinIcon.COG;
        };
    }
}
