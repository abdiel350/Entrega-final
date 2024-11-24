import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class CryptoUtils {
    // Generar una clave secreta AES de 128 bits
    public static SecretKey generateAESKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128); // Tamaño de clave de 128 bits para mayor seguridad
        return keyGenerator.generateKey();
    }

    // Cifrar un mensaje con AES
    public static String encryptAES(String message, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(message.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }

    // Descifrar un mensaje con AES
    public static String decryptAES(String encryptedMessage, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedMessage));
        return new String(decrypted);
    }

    // Convertir una cadena a una clave AES (por ejemplo, de una contraseña)
    public static SecretKey getAESKeyFromString(String keyStr) {
        return new SecretKeySpec(keyStr.getBytes(), "AES");
    }
}
