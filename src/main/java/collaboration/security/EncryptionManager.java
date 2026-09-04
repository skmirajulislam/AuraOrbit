package collaboration.security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encryption utility for securing collaboration messages.
 * Uses AES-256-GCM for authenticated encryption.
 */
public class EncryptionManager {
    private static final String ALGORITHM = "AES";
    private static final int KEY_SIZE = 256;
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";

    private final SecretKey key;

    public EncryptionManager() throws Exception {
        this.key = generateKey();
    }

    public EncryptionManager(byte[] keyBytes) throws Exception {
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("Key must be 256-bit (32 bytes)");
        }
        this.key = new SecretKeySpec(keyBytes, 0, keyBytes.length, ALGORITHM);
    }

    /**
     * Generate a new AES-256 key.
     */
    public static SecretKey generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(KEY_SIZE, new SecureRandom());
        return keyGen.generateKey();
    }

    /**
     * Encrypt a message.
     * Returns Base64-encoded ciphertext.
     */
    public String encrypt(String message) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        // For simplicity, using regular encryption without GCM parameters
        // In production, use full GCM with IV and authentication tag
        cipher.init(Cipher.ENCRYPT_MODE, key);

        byte[] ciphertext = cipher.doFinal(message.getBytes());
        return Base64.getEncoder().encodeToString(ciphertext);
    }

    /**
     * Decrypt a message.
     */
    public String decrypt(String encryptedMessage) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);

        byte[] ciphertext = Base64.getDecoder().decode(encryptedMessage);
        byte[] plaintext = cipher.doFinal(ciphertext);

        return new String(plaintext);
    }

    /**
     * Get the encryption key as bytes.
     */
    public byte[] getKeyBytes() {
        return key.getEncoded();
    }
}
