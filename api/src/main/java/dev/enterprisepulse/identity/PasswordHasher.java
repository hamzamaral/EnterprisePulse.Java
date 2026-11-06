package dev.enterprisepulse.identity;

import dev.enterprisepulse.common.AppException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/** Argon2id hashes in the PHC string format ($argon2id$v=19$m=...,t=...,p=...$salt$hash). */
public final class PasswordHasher {
    private static final int MEMORY_KB = 19456;
    private static final int ITERATIONS = 2;
    private static final int PARALLELISM = 1;
    private static final Pattern PHC = Pattern.compile("^\\$argon2id\\$v=19\\$m=(\\d+),t=(\\d+),p=(\\d+)\\$([^$]+)\\$([^$]+)$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {}

    public static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = compute(password, salt, MEMORY_KB, ITERATIONS, PARALLELISM, 32);
        return "$argon2id$v=19$m=" + MEMORY_KB + ",t=" + ITERATIONS + ",p=" + PARALLELISM + "$" + b64(salt) + "$" + b64(hash);
    }

    public static boolean verify(String password, String encoded) {
        if (password == null || encoded == null) return false;
        Matcher m = PHC.matcher(encoded);
        if (!m.matches()) return false;
        byte[] salt = Base64.getDecoder().decode(m.group(4));
        byte[] expected = Base64.getDecoder().decode(m.group(5));
        byte[] actual = compute(password, salt, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), expected.length);
        return MessageDigest.isEqual(actual, expected);
    }

    /** At least 8 characters with an upper case letter, a lower case letter and a digit. */
    public static void assertStrong(String password) {
        boolean ok = password != null && password.length() >= 8
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit);
        if (!ok) throw AppException.badRequest("Şifre en az 8 karakter olmalı; büyük harf, küçük harf ve rakam içermeli");
    }

    private static byte[] compute(String password, byte[] salt, int memory, int iterations, int parallelism, int length) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(memory)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);
        byte[] out = new byte[length];
        generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), out);
        return out;
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().withoutPadding().encodeToString(bytes);
    }
}
