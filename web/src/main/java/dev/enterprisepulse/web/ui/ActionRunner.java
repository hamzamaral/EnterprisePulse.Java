package dev.enterprisepulse.web.ui;

import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.ApiException;
import dev.enterprisepulse.web.core.ResourceViews.Action;
import dev.enterprisepulse.web.core.ResourceViews.ActionContext;

public final class ActionRunner {
    private ActionRunner() {}

    public static void run(Action action, ActionContext ctx) {
        String title = action.text(Ui.locale());
        if (action.prompt != null || action.confirm != null) {
            PromptDialog.ask(title, action.prompt, action.promptRequired, action.confirm, title, action.theme, comment -> execute(action, ctx, comment));
        } else {
            execute(action, ctx, null);
        }
    }

    private static void execute(Action action, ActionContext ctx, String comment) {
        try {
            if (action.run != null) {
                action.run.accept(ctx);
                return;
            }
            String url = action.endpoint.replace("{id}", ctx.id());
            if (action.download) {
                Ui.save(Ui.api().download(url));
                return;
            }
            Ui.api().post(url, ApiClient.obj("comment", comment == null || comment.isEmpty() ? null : comment));
            Ui.ok(Ui.L("saved"));
            ctx.reload().run();
        } catch (ApiException ex) {
            if (ex.status != 401) Ui.error(ex.getMessage());
        }
    }
}
