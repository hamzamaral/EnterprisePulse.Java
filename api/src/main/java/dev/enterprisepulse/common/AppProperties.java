package dev.enterprisepulse.common;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    public String jwtSecret = "dev-secret-change-me-dev-secret-change-me-0123456789";
    public String jwtIssuer = "enterprise-pulse";
    public int accessTokenMinutes = 15;
    public int refreshTokenDays = 7;
    public String webAppUrl = "http://localhost:8181";
    public int maxFailedLogins = 5;
    public int lockMinutes = 15;

    public String s3Endpoint = "http://localhost:9020";
    public String s3AccessKey = "erpadmin";
    public String s3SecretKey = "erpadmin123";
    public String s3Bucket = "enterprise-pulse";

    public String mailFrom = "EnterprisePulse <no-reply@enterprisepulse.dev>";

    public boolean seedOnStartup = true;
    public boolean rateLimit = true;
    public List<String> corsOrigins = List.of("http://localhost:8181");

    public void setJwtSecret(String v) { jwtSecret = v; }
    public void setJwtIssuer(String v) { jwtIssuer = v; }
    public void setAccessTokenMinutes(int v) { accessTokenMinutes = v; }
    public void setRefreshTokenDays(int v) { refreshTokenDays = v; }
    public void setWebAppUrl(String v) { webAppUrl = v; }
    public void setMaxFailedLogins(int v) { maxFailedLogins = v; }
    public void setLockMinutes(int v) { lockMinutes = v; }
    public void setS3Endpoint(String v) { s3Endpoint = v; }
    public void setS3AccessKey(String v) { s3AccessKey = v; }
    public void setS3SecretKey(String v) { s3SecretKey = v; }
    public void setS3Bucket(String v) { s3Bucket = v; }
    public void setMailFrom(String v) { mailFrom = v; }
    public void setSeedOnStartup(boolean v) { seedOnStartup = v; }
    public void setRateLimit(boolean v) { rateLimit = v; }
    public void setCorsOrigins(List<String> v) { corsOrigins = v; }
}
