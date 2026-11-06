package dev.enterprisepulse.web;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;
import dev.enterprisepulse.web.core.ApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.ApplicationRunner;

@Push
@SpringBootApplication
public class WebApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }

    @Override
    public void configurePage(AppShellSettings settings) {
        settings.setPageTitle("EnterprisePulse");
        settings.addLink("stylesheet", "styles/app.css");
        settings.addFavIcon("icon", "icons/favicon.svg", "any");
    }

    @Bean
    ApplicationRunner configureApi(@Value("${app.api-url}") String apiUrl) {
        return args -> ApiClient.configure(apiUrl);
    }
}
