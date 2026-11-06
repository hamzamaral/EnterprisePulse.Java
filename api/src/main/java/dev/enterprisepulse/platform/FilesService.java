package dev.enterprisepulse.platform;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.AppProperties;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.FileObject;
import java.net.URI;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@Transactional
public class FilesService {
    public static final long MAX_BYTES = 20L * 1024 * 1024;

    private static final Set<String> ALLOWED = Set.of(
            "application/pdf", "image/png", "image/jpeg", "image/webp", "image/gif", "text/plain", "text/csv",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword", "application/vnd.ms-excel", "application/zip", "application/xml", "text/xml");

    public record Download(FileObject file, byte[] content) {}

    private final Db db;
    private final Storage storage;

    public FilesService(Db db, Storage storage) {
        this.db = db;
        this.storage = storage;
    }

    public FileObject upload(byte[] content, String fileName, String contentType, String entityType, String entityId) {
        Guard.that(content.length > 0 && content.length <= MAX_BYTES, "Dosya boyutu 20 MB'ı geçemez");
        Guard.that(ALLOWED.contains(contentType), "Desteklenmeyen dosya türü: " + contentType);
        CurrentUser ctx = CurrentUser.get();
        UUID companyId = ctx.requireCompanyId();
        String name = fileName == null ? "dosya" : fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot) : "";
        String key = companyId + "/" + ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy/MM")) + "/"
                + UUID.randomUUID().toString().replace("-", "") + ext;
        storage.put(key, content, contentType);
        FileObject file = new FileObject();
        file.storageKey = key;
        file.originalName = name;
        file.mimeType = contentType;
        file.size = content.length;
        file.entityType = entityType;
        file.entityId = entityId;
        file.uploadedById = ctx.userId;
        db.add(file);
        db.flush();
        return file;
    }

    @Transactional(readOnly = true)
    public List<FileObject> forEntity(String entityType, String entityId) {
        return db.list("select f from FileObject f where f.entityType = ?1 and f.entityId = ?2 order by f.createdAt desc",
                FileObject.class, entityType, entityId);
    }

    @Transactional(readOnly = true)
    public Download download(UUID id) {
        FileObject file = db.first("select f from FileObject f where f.id = ?1", FileObject.class, id)
                .orElseThrow(() -> AppException.notFound("Dosya bulunamadı"));
        return new Download(file, storage.get(file.storageKey));
    }

    public void delete(UUID id) {
        CurrentUser ctx = CurrentUser.get();
        FileObject file = db.first("select f from FileObject f where f.id = ?1", FileObject.class, id)
                .orElseThrow(() -> AppException.notFound("Dosya bulunamadı"));
        Guard.state((ctx.userId != null && ctx.userId.equals(file.uploadedById)) || ctx.has("files:delete"), "Bu dosyayı silme yetkiniz yok");
        db.remove(file);
        db.flush();
    }

    /** S3-compatible object storage (RustFS locally), path-style addressing. */
    @Component
    public static class Storage {
        private final AppProperties props;
        private volatile S3Client client;
        private volatile boolean bucketReady;

        public Storage(AppProperties props) {
            this.props = props;
        }

        private S3Client client() {
            if (client == null) {
                synchronized (this) {
                    if (client == null) {
                        client = S3Client.builder()
                                .endpointOverride(URI.create(props.s3Endpoint))
                                .region(Region.US_EAST_1)
                                .forcePathStyle(true)
                                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(props.s3AccessKey, props.s3SecretKey)))
                                .build();
                    }
                }
            }
            return client;
        }

        public void put(String key, byte[] content, String contentType) {
            if (!bucketReady) {
                try {
                    client().createBucket(b -> b.bucket(props.s3Bucket));
                } catch (S3Exception ignored) {
                    // already exists
                }
                bucketReady = true;
            }
            client().putObject(b -> b.bucket(props.s3Bucket).key(key).contentType(contentType), RequestBody.fromBytes(content));
        }

        public byte[] get(String key) {
            return client().getObjectAsBytes(b -> b.bucket(props.s3Bucket).key(key)).asByteArray();
        }

        public void delete(String key) {
            client().deleteObject(b -> b.bucket(props.s3Bucket).key(key));
        }
    }
}
