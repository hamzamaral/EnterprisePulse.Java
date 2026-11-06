package dev.enterprisepulse;

import static org.assertj.core.api.Assertions.assertThat;

import dev.enterprisepulse.data.generated.NumberSequence;
import dev.enterprisepulse.identity.AuthService;
import dev.enterprisepulse.identity.PasswordHasher;
import dev.enterprisepulse.meta.MetadataStore;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.resource.ResourceService;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import java.time.Instant;
import java.time.Year;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlatformUnitTests {

    @Test
    void passwordHashRoundTrip() {
        String hash = PasswordHasher.hash("Secret123!");
        assertThat(hash).startsWith("$argon2id$");
        assertThat(PasswordHasher.verify("Secret123!", hash)).isTrue();
        assertThat(PasswordHasher.verify("secret123!", hash)).isFalse();
        assertThat(PasswordHasher.hash("Secret123!")).isNotEqualTo(hash);
    }

    @Test
    void numberFormat() {
        assertThat(NumberingService.format("ZMT", 6, true, 2026, 42)).isEqualTo("ZMT-2026-000042");
        assertThat(NumberingService.format("SO", 4, false, 2026, 7)).isEqualTo("SO-0007");
    }

    @Test
    void numberPreviewRestartsOnNewYear() {
        NumberSequence s = new NumberSequence();
        s.prefix = "PR";
        s.padding = 3;
        s.includeYear = true;
        s.resetYearly = true;
        s.currentYear = Year.now().getValue() - 1;
        s.nextValue = 57;
        assertThat(NumberingService.preview(s)).isEqualTo("PR-" + Year.now().getValue() + "-001");
        s.currentYear = Year.now().getValue();
        assertThat(NumberingService.preview(s)).isEqualTo("PR-" + Year.now().getValue() + "-057");
    }

    @Test
    void totpCodeVerifies() throws Exception {
        String secret = new DefaultSecretGenerator().generate();
        String code = new DefaultCodeGenerator().generate(secret, Instant.now().getEpochSecond() / 30);
        assertThat(AuthService.verifyTotp(secret, code)).isTrue();
        assertThat(AuthService.verifyTotp(secret, "000000".equals(code) ? "111111" : "000000")).isFalse();
    }

    @Test
    void metadataCoversAllModels() {
        MetadataStore meta = new MetadataStore();
        assertThat(meta.has("users")).isTrue();
        assertThat(meta.has("employees")).isTrue();
        assertThat(meta.get("users").fields).isNotEmpty();
        assertThat(meta.allPermissionCodes()).contains("users:read", "workflow:start", "sales:approve");
        assertThat(meta.registry()).containsKeys("employees", "workflow");
    }

    @Test
    void convertsQueryValues() {
        UUID id = UUID.randomUUID();
        assertThat(ResourceService.convert(id.toString(), UUID.class)).isEqualTo(id);
        assertThat(ResourceService.convert("true", Boolean.class)).isEqualTo(true);
        assertThat(ResourceService.convert("42", Integer.class)).isEqualTo(42);
    }
}
