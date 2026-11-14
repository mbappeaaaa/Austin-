package com.java3y.austin.billing.payment.config;

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

/** Database backups never contain the encryption key. A shared key is required for multi-node deployments. */
@Component
public class PaymentConfigCipher {
    private final String configuredKey;
    private final Path keyFile;
    private byte[] key;
    public PaymentConfigCipher(
            @Value("${austin.billing.payment.config.encryption-key:}") String configuredKey,
            @Value("${austin.billing.payment.config.key-file:${user.home}/.austin/payment-config.key}") String keyFile) {
        this.configuredKey = configuredKey;
        this.keyFile = Paths.get(keyFile);
    }

    private synchronized byte[] key(boolean create) throws Exception {
        if (key != null) return key;
        byte[] loaded;
        if (!configuredKey.trim().isEmpty()) {
            loaded = Base64.getDecoder().decode(configuredKey);
        } else {
            if (!Files.exists(keyFile) && create) {
                try {
                    Files.createDirectories(keyFile.getParent(),
                            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                } catch (UnsupportedOperationException windows) {
                    // Windows lacks POSIX permissions; a plain directory is fine.
                    Files.createDirectories(keyFile.getParent());
                }
                byte[] generated = new byte[32];
                new SecureRandom().nextBytes(generated);
                try {
                    try {
                        Files.createFile(keyFile,
                                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                    } catch (UnsupportedOperationException windows) {
                        Files.createFile(keyFile);
                    }
                    Files.write(keyFile, Base64.getEncoder().encodeToString(generated).getBytes(StandardCharsets.UTF_8));
                } catch (FileAlreadyExistsException concurrentCreation) {
                    // Another process created the key; use that persistent key instead.
                }
            }
            loaded = Base64.getDecoder().decode(new String(Files.readAllBytes(keyFile), StandardCharsets.UTF_8).trim());
        }
        if (loaded.length != 32) throw new IllegalStateException("支付配置加密主密钥必须为32字节Base64");
        return key = loaded;
    }

    public String encrypt(String provider, String plaintext) {
        try {
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(true), "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(provider.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(nonce) + ":"
                    + Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("支付配置加密失败，请检查服务端主密钥配置");
        }
    }

    public String decrypt(String provider, String encrypted) {
        try {
            String[] parts = encrypted.split(":", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(false), "AES"),
                    new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            cipher.updateAAD(provider.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("支付配置解密失败，请恢复正确的服务端主密钥");
        }
    }
}
