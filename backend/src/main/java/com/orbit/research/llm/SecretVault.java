package com.orbit.research.llm;

import com.orbit.research.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

/** Only ciphertext goes in SQL. Back up the key separately with the database; never return it to clients. */
@Component
public class SecretVault {
    private final String configuredKey; private final Path keyPath;
    private final SecureRandom random = new SecureRandom(); private byte[] cachedKey;
    public SecretVault(@Value("${orbit.config-encryption-key:}") String key,
                       @Value("${orbit.config-key-path:./data/config.key}") String path) {
        configuredKey = key; keyPath = Path.of(path).toAbsolutePath().normalize();
    }
    private synchronized byte[] key() throws Exception {
        if (cachedKey != null) return cachedKey;
        if (!configuredKey.isBlank()) cachedKey = Base64.getDecoder().decode(configuredKey);
        else {
            Files.createDirectories(keyPath.getParent());
            if (!Files.exists(keyPath)) {
                byte[] generated = new byte[32]; random.nextBytes(generated);
                // A complete key becomes visible atomically. Never replace an existing installation's key.
                Path temp = Files.createTempFile(keyPath.getParent(), "orbit-key-", ".tmp");
                try {
                    if (Files.getFileStore(temp).supportsFileAttributeView("posix"))
                        Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"));
                    Files.writeString(temp, Base64.getEncoder().encodeToString(generated), StandardOpenOption.TRUNCATE_EXISTING);
                    Files.move(temp, keyPath, StandardCopyOption.ATOMIC_MOVE);
                } finally { Files.deleteIfExists(temp); }
            }
            cachedKey = Base64.getDecoder().decode(Files.readString(keyPath).trim());
        }
        if (cachedKey.length != 32) { cachedKey = null; throw new IllegalArgumentException(); }
        return cachedKey;
    }
    public String encrypt(String owner, String value) { return crypt(owner, value, true); }
    public String decrypt(String owner, String value) { return crypt(owner, value, false); }
    private String crypt(String owner, String value, boolean encrypt) {
        if (value == null || value.isBlank()) return "";
        try {
            byte[] iv = new byte[12], bytes;
            if (encrypt) { random.nextBytes(iv); bytes = value.getBytes(StandardCharsets.UTF_8); }
            else { var parts = value.split(":", -1); if (parts.length != 3 || !parts[0].equals("v1")) throw new IllegalArgumentException(); iv = Base64.getDecoder().decode(parts[1]); bytes = Base64.getDecoder().decode(parts[2]); if (iv.length != 12) throw new IllegalArgumentException(); }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(owner.getBytes(StandardCharsets.UTF_8));
            byte[] result = cipher.doFinal(bytes);
            return encrypt ? "v1:" + Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(result) : new String(result, StandardCharsets.UTF_8);
        } catch (Exception error) { throw new ApiException(503, "CONFIG_KEY_UNAVAILABLE", "配置加密密钥不可用，请检查后端 config.key 或恢复对应备份。"); }
    }
}
