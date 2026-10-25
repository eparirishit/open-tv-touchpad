package dev.opentvtouchpad.hidprobe;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import javax.security.auth.x500.X500Principal;

final class TvIdentity {
    private TvIdentity() {}

    static synchronized KeyStore.PrivateKeyEntry create(String alias)
            throws GeneralSecurityException, IOException {
        KeyStore store = store();
        if (store.containsAlias(alias)) throw new GeneralSecurityException("Identity already exists");
        long now = System.currentTimeMillis();
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setKeySize(2048)
                // Conscrypt hashes/pads TLS signatures before delegating raw RSA with SIGN purpose.
                .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                    KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setCertificateSubject(new X500Principal("CN=Nord TV Remote"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(new Date(now - 86400000L))
                .setCertificateNotAfter(new Date(now + 3650L * 86400000L))
                .build());
            generator.generateKeyPair();
            return load(alias);
        } catch (GeneralSecurityException | IOException | RuntimeException failure) {
            try { store.deleteEntry(alias); }
            catch (GeneralSecurityException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    static synchronized KeyStore.PrivateKeyEntry load(String alias)
            throws GeneralSecurityException, IOException {
        KeyStore.Entry stored = store().getEntry(alias, null);
        if (!(stored instanceof KeyStore.PrivateKeyEntry))
            throw new GeneralSecurityException("Identity unavailable");
        KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) stored;
        KeyInfo info = KeyFactory.getInstance("RSA", "AndroidKeyStore")
            .getKeySpec(entry.getPrivateKey(), KeyInfo.class);
        if (info.getKeySize() != 2048 || (info.getPurposes() & KeyProperties.PURPOSE_SIGN) == 0)
            throw new GeneralSecurityException("Unsupported native identity");
        X509Certificate certificate = (X509Certificate) entry.getCertificate();
        if (!(certificate.getPublicKey() instanceof RSAPublicKey)
                || ((RSAPublicKey) certificate.getPublicKey()).getModulus().bitLength() != 2048
                || !certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal()))
            throw new GeneralSecurityException("Unsupported identity certificate");
        certificate.checkValidity();
        certificate.verify(certificate.getPublicKey());
        return entry;
    }

    static synchronized void delete(String alias) throws GeneralSecurityException, IOException {
        store().deleteEntry(alias);
    }

    private static KeyStore store() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }
}
