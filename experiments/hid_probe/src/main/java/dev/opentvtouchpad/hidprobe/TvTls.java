package dev.opentvtouchpad.hidprobe;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.AlgorithmParameters;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Arrays;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

final class TvTls {
    private static final int MAX_CERTIFICATE_BYTES = 16384;

    private TvTls() {}

    static X509Certificate certificate(byte[] der) throws CertificateException {
        if (der == null || der.length == 0 || der.length > MAX_CERTIFICATE_BYTES)
            throw new CertificateException("Invalid certificate size");
        ByteArrayInputStream input = new ByteArrayInputStream(der);
        X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
            .generateCertificate(input);
        if (input.available() != 0 || !Arrays.equals(der, certificate.getEncoded()))
            throw new CertificateException("Expected one DER certificate");
        return certificate;
    }

    static SSLSocket clientSocket(KeyStore.PrivateKeyEntry identity, X509Certificate peer)
            throws GeneralSecurityException, IOException {
        return socket(context(identity, pinnedTrustManager(peer)));
    }

    // Only the pairing transport may use this socket; it grants no control authorization.
    static SSLSocket pairingSocket(KeyStore.PrivateKeyEntry identity)
            throws GeneralSecurityException, IOException {
        return socket(context(identity, new X509TrustManager() {
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                checkPeer(chain, authType);
            }
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                throw new CertificateException("Client authentication not supported");
            }
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }));
    }

    static X509TrustManager pinnedTrustManager(X509Certificate peer)
            throws GeneralSecurityException, IOException {
        final byte[] pin = peer.getEncoded();
        certificate(pin);
        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
        trust.load(null);
        trust.setCertificateEntry("peer", peer);
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(trust);
        X509TrustManager nativeManager = null;
        for (TrustManager manager : factory.getTrustManagers())
            if (manager instanceof X509TrustManager) nativeManager = (X509TrustManager) manager;
        if (nativeManager == null) throw new GeneralSecurityException("Native X509 validation unavailable");
        final X509TrustManager validator = nativeManager;
        return new X509TrustManager() {
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                checkPeer(chain, authType);
                if (!MessageDigest.isEqual(pin, chain[0].getEncoded()))
                    throw new CertificateException("Peer certificate changed");
                validator.checkServerTrusted(chain, authType);
            }
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                throw new CertificateException("Client authentication not supported");
            }
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{peer}; }
        };
    }

    private static void checkPeer(X509Certificate[] chain, String authType) throws CertificateException {
        if (chain == null || chain.length == 0 || chain.length > 8 || authType == null || authType.isEmpty())
            throw new CertificateException("Invalid peer chain");
        for (X509Certificate cert : chain) {
            if (cert == null || cert.getEncoded().length > MAX_CERTIFICATE_BYTES)
                throw new CertificateException("Invalid peer certificate");
            cert.checkValidity();
        }
        X509Certificate leaf = chain[0];
        if (!(leaf.getPublicKey() instanceof RSAPublicKey)
                || ((RSAPublicKey) leaf.getPublicKey()).getModulus().bitLength() < 2048)
            throw new CertificateException("Unsupported peer key");
        // Native trust anchors can skip algorithm/validity checks; exact pins still require them.
        String algorithm = leaf.getSigAlgOID();
        if ("1.2.840.113549.1.1.10".equals(algorithm)) {
            checkPssParameters(leaf.getSigAlgParams());
        } else if (!"1.2.840.113549.1.1.11".equals(algorithm)
                && !"1.2.840.113549.1.1.12".equals(algorithm)
                && !"1.2.840.113549.1.1.13".equals(algorithm))
            throw new CertificateException("Unsupported peer signature");
        if (leaf.getSubjectX500Principal().equals(leaf.getIssuerX500Principal())) {
            try { leaf.verify(leaf.getPublicKey()); }
            catch (GeneralSecurityException e) { throw new CertificateException("Invalid peer signature", e); }
        }
    }

    static void checkPssParameters(byte[] encoded) throws CertificateException {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_CERTIFICATE_BYTES)
            throw new CertificateException("Invalid peer PSS parameters");
        PSSParameterSpec pss;
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("PSS");
            parameters.init(encoded);
            pss = parameters.getParameterSpec(PSSParameterSpec.class);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException failure) {
            throw new CertificateException("Invalid peer PSS parameters", failure);
        }
        String digest = pss.getDigestAlgorithm();
        int digestBytes = switch (digest) {
            case "SHA-256" -> 32;
            case "SHA-384" -> 48;
            case "SHA-512" -> 64;
            default -> throw new CertificateException("Unsupported peer PSS digest");
        };
        if (!"MGF1".equals(pss.getMGFAlgorithm())
                || !(pss.getMGFParameters() instanceof MGF1ParameterSpec)
                || !digest.equals(((MGF1ParameterSpec) pss.getMGFParameters()).getDigestAlgorithm())
                || pss.getSaltLength() != digestBytes || pss.getTrailerField() != 1)
            throw new CertificateException("Unsupported peer PSS parameters");
    }

    private static SSLSocket socket(SSLContext context) throws IOException {
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        try {
            socket.setUseClientMode(true);
            socket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
            return socket;
        } catch (RuntimeException failure) {
            try { socket.close(); }
            catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static SSLContext context(KeyStore.PrivateKeyEntry identity, X509TrustManager trust)
            throws GeneralSecurityException {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(new KeyManager[]{new IdentityKeyManager(identity)}, new TrustManager[]{trust}, null);
        return context;
    }

    private static final class IdentityKeyManager extends X509ExtendedKeyManager {
        private final KeyStore.PrivateKeyEntry identity;
        private static final String ALIAS = "identity";

        IdentityKeyManager(KeyStore.PrivateKeyEntry identity) { this.identity = identity; }

        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return "RSA".equals(keyType) ? new String[]{ALIAS} : null;
        }
        public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
            if (keyTypes != null) for (String keyType : keyTypes)
                if ("RSA".equals(keyType)) return ALIAS;
            return null;
        }
        public String chooseEngineClientAlias(String[] keyTypes, Principal[] issuers, SSLEngine engine) {
            return chooseClientAlias(keyTypes, issuers, null);
        }
        public String[] getServerAliases(String keyType, Principal[] issuers) { return null; }
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) { return null; }
        public X509Certificate[] getCertificateChain(String alias) {
            return ALIAS.equals(alias) ? new X509Certificate[]{(X509Certificate) identity.getCertificate()} : null;
        }
        public PrivateKey getPrivateKey(String alias) {
            return ALIAS.equals(alias) ? identity.getPrivateKey() : null;
        }
    }
}
