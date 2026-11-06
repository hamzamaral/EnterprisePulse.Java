# EnterprisePulse Java

EnterprisePulse ERP'nin Spring Boot 3.5 API + Vaadin 24 web ile yeniden yazımı (Java 17).

## Çalıştırma

```bash
docker compose up -d                 # Postgres 5452, RustFS 9020/9021, Mailpit 1045/8045
./mvnw -pl api spring-boot:run       # http://localhost:8180 (Swagger: /swagger-ui.html)
./mvnw -pl web spring-boot:run       # http://localhost:8181
```

API açılışta Flyway migration'larını uygular ve demo verisini yükler.

| Kullanıcı | Şifre |
| --- | --- |
| admin@enterprisepulse.dev | Admin123! |
| manager@enterprisepulse.dev | Manager123! |
| employee@enterprisepulse.dev | Employee123! |

## Yapı

- `api`: JPA varlıkları (üretilmiş), kimlik, yetki, çok şirketlilik, iş akışı, dosya, bildirim (STOMP), genel CRUD, modül servisleri
- `web`: Vaadin arayüzü; metadata'dan üretilen liste/form/detay ekranları ve modül sayfaları
- `tools/codegen`: Prisma şemasından varlık, enum ve `metadata.json` üretimi (`node tools/codegen/emit-java.mjs`)
- Testler: `./mvnw -pl api test` (Postgres 5452 gerekir; her koşu geçici bir veritabanı açar)
