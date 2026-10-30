package dev.opentvtouchpad.hidprobe;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Local sockets only. Never accesses enrollment stores or app-owned identity aliases. */
@RunWith(AndroidJUnit4.class)
public class TvTlsTest {
    private static final String CLIENT = "hidprobe.test.tls.client";
    private static final String SERVER = "hidprobe.test.tls.server";
    private static final String OTHER = "hidprobe.test.tls.other";
    private static final String[] ALIASES = {CLIENT, SERVER, OTHER};
    private static final int TIMEOUT = 5000;
    private KeyStore.PrivateKeyEntry client;
    private KeyStore.PrivateKeyEntry server;

    @Before public void setUp() throws Exception {
        cleanAliases();
        client = TvIdentity.create(CLIENT);
        server = TvIdentity.create(SERVER);
    }

    @After public void cleanAliases() throws Exception {
        // Fixed names bound crash leftovers; never enumerate or remove unrelated aliases.
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        Exception failure = null;
        for (String alias : ALIASES) {
            try { store.deleteEntry(alias); }
            catch (Exception e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        }
        if (failure != null) throw failure;
    }

    @Test public void identityIsNativeStableSelfSignedAndNeverReplaced() throws Exception {
        X509Certificate cert = cert(client);
        assertEquals(2048, ((RSAPublicKey) cert.getPublicKey()).getModulus().bitLength());
        assertNull(client.getPrivateKey().getEncoded());
        android.security.keystore.KeyInfo info = java.security.KeyFactory.getInstance("RSA", "AndroidKeyStore")
            .getKeySpec(client.getPrivateKey(), android.security.keystore.KeyInfo.class);
        assertEquals(KeyProperties.PURPOSE_SIGN, info.getPurposes());
        cert.checkValidity();
        cert.verify(cert.getPublicKey());
        TvTls.pinnedTrustManager(cert).checkServerTrusted(new X509Certificate[]{cert}, "RSA");
        assertArrayEquals(cert.getEncoded(), cert(TvIdentity.load(CLIENT)).getEncoded());
        assertThrows(java.security.GeneralSecurityException.class, () -> TvIdentity.create(CLIENT));
        assertArrayEquals(cert.getEncoded(), cert(TvIdentity.load(CLIENT)).getEncoded());
        TvIdentity.delete(CLIENT);
        assertThrows(java.security.GeneralSecurityException.class, () -> TvIdentity.load(CLIENT));
    }

    @Test public void nativeTlsRsaAdapterCanSignPrehashedPkcs1() throws Exception {
        javax.crypto.Cipher signer = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding");
        signer.init(javax.crypto.Cipher.ENCRYPT_MODE, client.getPrivateKey());
        byte[] input = {1, 2, 3};
        byte[] signature = signer.doFinal(input);
        java.security.Signature verifier = java.security.Signature.getInstance("NONEwithRSA");
        verifier.initVerify(cert(client).getPublicKey());
        verifier.update(input);
        assertTrue(verifier.verify(signature));
    }

    @Test public void nativeTlsRsaAdapterCanSignPreencodedBlock() throws Exception {
        javax.crypto.Cipher signer = javax.crypto.Cipher.getInstance("RSA/ECB/NoPadding");
        signer.init(javax.crypto.Cipher.ENCRYPT_MODE, client.getPrivateKey());
        byte[] input = new byte[256];
        input[255] = 2;
        byte[] signed = signer.doFinal(input);
        javax.crypto.Cipher verifier = javax.crypto.Cipher.getInstance("RSA/ECB/NoPadding");
        verifier.init(javax.crypto.Cipher.DECRYPT_MODE, cert(client).getPublicKey());
        assertArrayEquals(input, verifier.doFinal(signed));
    }

    @Test public void pssParametersRejectWeakMismatchedAndMalformedProfiles() throws Exception {
        String[] digests = {"SHA-256", "SHA-384", "SHA-512"};
        int[] saltLengths = {32, 48, 64};
        for (int i = 0; i < digests.length; i++)
            TvTls.checkPssParameters(pssParameters(digests[i], digests[i], saltLengths[i], 1));
        byte[][] invalid = {
            pssParameters("SHA-1", "SHA-1", 20, 1),
            pssParameters("SHA-256", "SHA-1", 32, 1),
            pssParameters("SHA-256", "SHA-384", 32, 1),
            pssParameters("SHA-256", "SHA-256", 31, 1),
            pssParameters("SHA-256", "SHA-256", 33, 1),
            // JDK17 encoded trailer=2; its native decoder rejects "Unsupported trailerField value 2".
            Base64.getDecoder().decode("MDmgDzANBglghkgBZQMEAgEFAKEcMBoGCSqGSIb3DQEBCDANBglghkgBZQMEAgEFAKIDAgEgowMCAQI="),
            null, new byte[0], new byte[]{1, 2, 3}, new byte[16385]
        };
        for (int i = 0; i < invalid.length; i++) {
            byte[] encoded = invalid[i];
            assertThrows("Invalid PSS profile index " + i, CertificateException.class,
                () -> TvTls.checkPssParameters(encoded));
        }
    }

    private static byte[] pssParameters(String digest, String mgfDigest, int salt, int trailer) throws Exception {
        java.security.AlgorithmParameters parameters = java.security.AlgorithmParameters.getInstance("PSS");
        parameters.init(new java.security.spec.PSSParameterSpec(digest, "MGF1",
            new java.security.spec.MGF1ParameterSpec(mgfDigest), salt, trailer));
        return parameters.getEncoded();
    }

    @Test public void parserRejectsMalformedTruncatedOversizedAndTrailingInput() throws Exception {
        byte[] der = cert(server).getEncoded();
        assertArrayEquals(der, TvTls.certificate(der).getEncoded());
        assertThrows(CertificateException.class, () -> TvTls.certificate(null));
        assertThrows(CertificateException.class, () -> TvTls.certificate(new byte[0]));
        assertThrows(CertificateException.class, () -> TvTls.certificate(new byte[16385]));
        assertThrows(CertificateException.class, () -> TvTls.certificate(new byte[]{1, 2, 3}));
        assertThrows(CertificateException.class, () -> TvTls.certificate(Arrays.copyOf(der, der.length - 1)));
        assertThrows(CertificateException.class, () -> TvTls.certificate(Arrays.copyOf(der, der.length + 1)));
    }

    @Test public void tls12AuthenticatesActualKeystoreClientAndExchangesData() throws Exception {
        exchange(TvTls.clientSocket(client, cert(server)));
    }

    @Test public void invalidSelfSignatureAnchorPairsAndPinsWithItsPrivateKey() throws Exception {
        server = anchorFixture();
        exchange(TvTls.pairingSocket(client));
        exchange(TvTls.clientSocket(client, cert(server)));
    }

    @Test public void samePublicKeyWithDifferentDerStillFailsExactPin() throws Exception {
        server = anchorFixture();
        X509Certificate altered = TvTls.certificate(fixtureBytes("altered.der"));
        assertArrayEquals(cert(server).getPublicKey().getEncoded(), altered.getPublicKey().getEncoded());
        assertFalse(Arrays.equals(cert(server).getEncoded(), altered.getEncoded()));
        altered.checkValidity();
        altered.verify(TvTls.certificate(fixtureBytes("signer.der")).getPublicKey());
        assertThrows(java.security.GeneralSecurityException.class, () -> altered.verify(altered.getPublicKey()));
        reject(TvTls.clientSocket(client, altered), CertificateException.class, "Peer certificate changed");
    }

    @Test public void invalidSelfSignatureAnchorCannotHandshakeWithoutMatchingPrivateKey() throws Exception {
        server = anchorFixture();
        X509Certificate anchor = cert(server);
        // Establish policy acceptance first so a policy rejection cannot masquerade as key proof.
        TvTls.pinnedTrustManager(anchor).checkServerTrusted(new X509Certificate[]{anchor}, "RSA");
        KeyStore.PrivateKeyEntry wrong = TvIdentity.create(OTHER);
        assertFalse(Arrays.equals(anchor.getPublicKey().getEncoded(), cert(wrong).getPublicKey().getEncoded()));
        for (PrivateKey key : new PrivateKey[]{null, wrong.getPrivateKey()}) {
            for (boolean pairing : new boolean[]{true, false}) {
                // Construct a real context/socket with the fixture certificate and absent/wrong key.
                // Only startHandshake may satisfy the negative assertion, never construction failure.
                try (SSLSocket socket = pairing ? TvTls.pairingSocket(client) : TvTls.clientSocket(client, anchor);
                     Loopback peer = new Loopback(anchor, key)) {
                    peer.connect(socket);
                    peer.serve(s -> {
                        IOException failure = assertThrows(IOException.class, s::startHandshake);
                        assertFalse("Server must reject, not time out", failure instanceof java.net.SocketTimeoutException);
                    });
                    IOException failure = assertThrows(IOException.class, socket::startHandshake);
                    assertFalse("Client must reject, not time out", failure instanceof java.net.SocketTimeoutException);
                    peer.awaitServer();
                    assertTrue("Native handshake requested the RSA server identity", peer.socketRsaRequested);
                }
            }
        }
    }

    // Deliberately PUBLIC test private key, packaged only in the instrumentation APK.
    // OpenSSL generated two RSA keys with one DN, then signed the server CSR using the other.
    // See src/androidTest/fixtures/generate-anchor-fixture.py; no certificate wrapper or DER editing.
    private static KeyStore.PrivateKeyEntry anchorFixture() throws Exception {
        X509Certificate anchor = TvTls.certificate(fixtureBytes("anchor.der"));
        X509Certificate signer = TvTls.certificate(fixtureBytes("signer.der"));
        assertEquals(anchor.getSubjectX500Principal(), anchor.getIssuerX500Principal());
        assertEquals("1.2.840.113549.1.1.11", anchor.getSigAlgOID());
        assertEquals(2048, ((RSAPublicKey) anchor.getPublicKey()).getModulus().bitLength());
        anchor.checkValidity();
        anchor.verify(signer.getPublicKey());
        assertThrows(java.security.GeneralSecurityException.class, () -> anchor.verify(anchor.getPublicKey()));
        PrivateKey key = java.security.KeyFactory.getInstance("RSA").generatePrivate(
            new java.security.spec.PKCS8EncodedKeySpec(fixtureBytes("server.pk8")));
        // Independently establish intact key possession using native JCA before the TLS proof.
        java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
        byte[] challenge = {21, 34, 55};
        signature.initSign(key); signature.update(challenge);
        byte[] proof = signature.sign();
        signature.initVerify(anchor.getPublicKey()); signature.update(challenge);
        assertTrue(signature.verify(proof));
        return new KeyStore.PrivateKeyEntry(key, new java.security.cert.Certificate[]{anchor});
    }

    private static byte[] fixtureBytes(String name) throws IOException {
        try (java.io.InputStream input = InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().open("tls-anchor/" + name);
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int size;
            while ((size = input.read(buffer)) != -1) {
                if (output.size() + size > 16384) throw new IOException("Oversized public TLS fixture");
                output.write(buffer, 0, size);
            }
            return output.toByteArray();
        }
    }

    @Test public void wrongPinFailsDuringHandshakeBeforeApplicationData() throws Exception {
        X509Certificate other = cert(TvIdentity.create(OTHER));
        reject(TvTls.clientSocket(client, other), CertificateException.class, "Peer certificate changed");
    }

    @Test public void expiredExactPeerFailsDuringHandshake() throws Exception {
        TvIdentity.delete(SERVER);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
        generator.initialize(new KeyGenParameterSpec.Builder(SERVER, KeyProperties.PURPOSE_SIGN)
            .setKeySize(2048).setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                KeyProperties.SIGNATURE_PADDING_RSA_PSS)
            .setCertificateSubject(new X500Principal("CN=Expired TLS fixture"))
            .setCertificateNotBefore(new Date(946684800000L))
            .setCertificateNotAfter(new Date(978307200000L)).build());
        generator.generateKeyPair();
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        server = (KeyStore.PrivateKeyEntry) store.getEntry(SERVER, null);
        cert(server).verify(cert(server).getPublicKey());
        reject(TvTls.clientSocket(client, cert(server)), java.security.cert.CertificateExpiredException.class, null);
    }

    @Test public void trustedChildIsRejectedBecauseOnlyExactLeafIsAuthorized() throws Exception {
        X509Certificate ca = TvTls.certificate(Base64.getDecoder().decode(CA_DER));
        X509Certificate child = TvTls.certificate(Base64.getDecoder().decode(CHILD_DER));
        X509Certificate[] chain = {child, ca};
        child.verify(ca.getPublicKey());
        nativeTrust(ca).checkServerTrusted(chain, "RSA");
        assertThrows(CertificateException.class,
            () -> TvTls.pinnedTrustManager(ca).checkServerTrusted(chain, "RSA"));
    }

    @Test public void bootstrapDoesNotAuthorizeSeparatePinnedControlSocket() throws Exception {
        X509Certificate other = cert(TvIdentity.create(OTHER));
        try (SSLSocket control = TvTls.clientSocket(client, other)) {
            exchange(TvTls.pairingSocket(client));
            reject(control, CertificateException.class, "Peer certificate changed");
        }
        exchange(TvTls.clientSocket(client, cert(server)));
    }

    @Test public void closeUnblocksEstablishedTlsRead() throws Exception {
        CountDownLatch readEntered = new CountDownLatch(1);
        ExecutorService reader = Executors.newSingleThreadExecutor();
        try (SSLSocket socket = TvTls.clientSocket(client, cert(server)); Loopback peer = new Loopback()) {
            peer.connect(socket);
            peer.serve(s -> {
                s.startHandshake();
                assertClient(s);
                s.getOutputStream().write(7);
                try { assertEquals(-1, s.getInputStream().read()); }
                catch (java.net.SocketTimeoutException timeout) { throw timeout; }
                catch (IOException closed) { /* Either EOF or transport close ends the fixture. */ }
            });
            peer.startHandshake(socket);
            assertEquals(7, socket.getInputStream().read());
            socket.setSoTimeout(0); // The future can finish only by close, not a read timeout.
            Future<?> blocked = reader.submit(() -> {
                readEntered.countDown();
                try { assertEquals("Closed TLS read must end at EOF", -1, socket.getInputStream().read()); }
                catch (java.net.SocketTimeoutException timeout) { throw new AssertionError("TLS read timed out", timeout); }
                catch (IOException closed) { /* Native close may finish the blocked read with EOF or an exception. */ }
            });
            assertTrue(readEntered.await(TIMEOUT, TimeUnit.MILLISECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class,
                () -> blocked.get(100, TimeUnit.MILLISECONDS));
            socket.close();
            blocked.get(2, TimeUnit.SECONDS);
            peer.awaitServer();
        } finally {
            reader.shutdownNow();
            assertTrue(reader.awaitTermination(TIMEOUT, TimeUnit.MILLISECONDS));
        }
    }

    @Test public void closeUnblocksStalledTlsHandshake() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
             SSLSocket socket = TvTls.clientSocket(client, cert(server))) {
            listener.setSoTimeout(TIMEOUT);
            socket.connect(new InetSocketAddress("127.0.0.1", listener.getLocalPort()), TIMEOUT);
            try (Socket accepted = listener.accept()) {
                accepted.setSoTimeout(TIMEOUT);
                Future<?> blocked = worker.submit(() ->
                    assertThrows(IOException.class, socket::startHandshake));
                assertTrue(accepted.getInputStream().read() >= 0); // ClientHello reached the peer.
                assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> blocked.get(100, TimeUnit.MILLISECONDS));
                socket.close();
                blocked.get(2, TimeUnit.SECONDS);
            }
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(TIMEOUT, TimeUnit.MILLISECONDS));
        }
    }

    private void exchange(SSLSocket socket) throws Exception {
        try (socket; Loopback peer = new Loopback()) {
            peer.connect(socket);
            peer.serve(s -> {
                s.startHandshake();
                assertClient(s);
                assertEquals(42, s.getInputStream().read());
                s.getOutputStream().write(43);
            });
            peer.startHandshake(socket);
            assertEquals("TLSv1.2", socket.getSession().getProtocol());
            assertArrayEquals(cert(server).getEncoded(), socket.getSession().getPeerCertificates()[0].getEncoded());
            socket.getOutputStream().write(42);
            assertEquals(43, socket.getInputStream().read());
            peer.awaitServer();
        }
    }

    private void reject(SSLSocket socket, Class<? extends CertificateException> expectedType,
                        String expectedMessage) throws Exception {
        try (socket; Loopback peer = new Loopback()) {
            peer.connect(socket);
            peer.serve(s -> assertThrows(IOException.class, s::startHandshake));
            SSLHandshakeException failure = assertThrows(SSLHandshakeException.class, socket::startHandshake);
            peer.awaitServer();
            boolean certificateRejected = false;
            for (Throwable cause = failure; cause != null; cause = cause.getCause())
                certificateRejected |= expectedType.isInstance(cause)
                    && (expectedMessage == null || expectedMessage.equals(cause.getMessage()));
            assertTrue("Expected certificate-policy rejection; " + peer.callbackStatus(), certificateRejected);
        }
    }

    private void assertClient(SSLSocket socket) throws Exception {
        assertArrayEquals(cert(client).getEncoded(), socket.getSession().getPeerCertificates()[0].getEncoded());
    }

    private static X509Certificate cert(KeyStore.PrivateKeyEntry entry) {
        return (X509Certificate) entry.getCertificate();
    }

    private static X509TrustManager nativeTrust(X509Certificate trusted) throws Exception {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null);
        store.setCertificateEntry("fixture-peer", trusted);
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        for (javax.net.ssl.TrustManager manager : factory.getTrustManagers())
            if (manager instanceof X509TrustManager) return (X509TrustManager) manager;
        throw new AssertionError("No native X509 trust manager");
    }

    private interface SocketAction { void run(SSLSocket socket) throws Exception; }

    private final class Loopback implements AutoCloseable {
        final SSLServerSocket listener;
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        volatile SSLSocket accepted;
        volatile boolean socketRsaRequested;
        volatile boolean socketAliasSelected;
        Future<?> serverRun;

        Loopback() throws Exception {
            this(cert(server), server.getPrivateKey());
        }

        Loopback(X509Certificate certificate, PrivateKey privateKey) throws Exception {
            X509ExtendedKeyManager key = new X509ExtendedKeyManager() {
                public String[] getClientAliases(String t, Principal[] i) { return null; }
                public String chooseClientAlias(String[] t, Principal[] i, Socket s) { return null; }
                public String[] getServerAliases(String t, Principal[] i) {
                    return "RSA".equals(t) ? new String[]{"fixture"} : null;
                }
                public String chooseServerAlias(String t, Principal[] i, Socket s) {
                    socketRsaRequested |= "RSA".equals(t);
                    String alias = "RSA".equals(t) ? "fixture" : null;
                    socketAliasSelected |= alias != null;
                    return alias;
                }
                public X509Certificate[] getCertificateChain(String alias) {
                    return "fixture".equals(alias) ? new X509Certificate[]{certificate} : null;
                }
                public PrivateKey getPrivateKey(String alias) {
                    return "fixture".equals(alias) ? privateKey : null;
                }
            };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(new KeyManager[]{key}, new X509TrustManager[]{nativeTrust(cert(client))}, null);
            listener = (SSLServerSocket) context.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            listener.setEnabledProtocols(new String[]{"TLSv1.2"});
            listener.setNeedClientAuth(true);
            listener.setSoTimeout(TIMEOUT);
        }

        void connect(SSLSocket socket) throws Exception {
            assertFalse("Socket must be configured before connect", socket.isConnected());
            assertTrue(socket.getUseClientMode());
            assertEquals(2, socket.getEnabledProtocols().length);
            assertTrue(Arrays.asList(socket.getEnabledProtocols()).contains("TLSv1.2"));
            assertTrue(Arrays.asList(socket.getEnabledProtocols()).contains("TLSv1.3"));
            socket.setSoTimeout(TIMEOUT);
            socket.connect(new InetSocketAddress("127.0.0.1", listener.getLocalPort()), TIMEOUT);
        }

        void serve(SocketAction action) {
            serverRun = worker.submit(() -> {
                try (SSLSocket socket = (SSLSocket) listener.accept()) {
                    accepted = socket;
                    socket.setSoTimeout(TIMEOUT);
                    action.run(socket);
                }
                return null;
            });
        }

        void startHandshake(SSLSocket socket) throws Exception {
            try { socket.startHandshake(); }
            catch (IOException clientFailure) {
                try { awaitServer(); }
                catch (Exception | AssertionError serverFailure) { clientFailure.addSuppressed(serverFailure); }
                throw clientFailure;
            }
        }

        String callbackStatus() {
            return "socketRsaRequested=" + socketRsaRequested + "; socketAliasSelected=" + socketAliasSelected;
        }

        void awaitServer() throws Exception {
            if (serverRun == null) return;
            try { serverRun.get(TIMEOUT, TimeUnit.MILLISECONDS); }
            catch (java.util.concurrent.ExecutionException failure) {
                StringBuilder types = new StringBuilder();
                for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) {
                    if (types.length() != 0) types.append(" -> ");
                    types.append(cause.getClass().getSimpleName());
                }
                // Keep provider messages and credential objects out of persistent test diagnostics.
                throw new AssertionError("Fixture server failed: " + types + "; " + callbackStatus());
            }
        }

        public void close() throws Exception {
            try { listener.close(); }
            finally {
                try { if (accepted != null) accepted.close(); }
                finally {
                    worker.shutdownNow();
                    assertTrue("Fixture worker stopped", worker.awaitTermination(TIMEOUT + 1000, TimeUnit.MILLISECONDS));
                    awaitServer();
                }
            }
        }
    }

    // Public-only keytool CA/child fixtures, valid 2020–2100. Temporary signing keys were destroyed.
    private static final String CA_DER = "MIIDATCCAemgAwIBAgIJAICTzJVaFFhTMA0GCSqGSIb3DQEBCwUAMB0xGzAZBgNVBAMTElB1YmxpYyBUTFMgVGVzdCBDQTAgFw0yMDAxMDEwNTAwMDBaGA8yMTAwMDEwMTA1MDAwMFowHTEbMBkGA1UEAxMSUHVibGljIFRMUyBUZXN0IENBMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApPXCujvvdcnW6WE52ZmsXU7AucC1wgtshX3bjbUCdbyzMUM3x39vSid2B12U9G01X2VBrfuCHzgcDwpzfsyaeo6C0A5Az5TDYSG9HRXLRlBJM/ovMwSWZNzGB/4CPMQzeQUoN5QJoFwpI2sNnRr+72BG02yDuIQhS28Ln5kTXFVfJvMOPx+m5rNvFv8BoADUxB42/R05Gj8y8YLUsKtzweUesvyu+g8M8zCDvn65UYHoPcjIOCsz1W5E8qUQsk2IhU/pemsvZEQt/vIBLdd31RRCzgA9yErzYKnqUNqWv6/tzHaEVc9z9gsRHZWa6jNHjoKsIopqEgc84RHXvaKEMwIDAQABo0IwQDAdBgNVHQ4EFgQUj05MqCbwInxCJz1/wXzGpOCY7JMwDgYDVR0PAQH/BAQDAgIEMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEBAELReTAdgsXZMpc++KUgJUXfzz4jN9mP8Ww60m6wDY+0rbZO5j7CXvrlS3C1y9n+7i26hPk/I6LkfW9+OaSe8Ma4iA8JRCNa2Zlvt8ozmS8Fmt3g9s2WJE0ALoIgLZeDR1hKXCVIKcnQsHCCvmpGKb3eOM1F3bXS64Qz2RtJ3uZvHXR2p4TScsvpnh+EQDCKcWu9dUOU7HeXGBYZKRfsZ1nYkWv0ZMv0fwiasvha5YTMbet15VzAHupAAhqVPqFCDG66rVt1jJpWilv5BDjH4Imf9M0A0GxKtE3AnQ5Dm5puRC0fzv921dkx3DyoRtvWCV6QDs4PDWIQ7fRYBj2YXFo=";
    private static final String CHILD_DER = "MIIDKTCCAhGgAwIBAgIJAM7W+HNRqqu7MA0GCSqGSIb3DQEBCwUAMB0xGzAZBgNVBAMTElB1YmxpYyBUTFMgVGVzdCBDQTAgFw0yMDAxMDEwNTAwMDBaGA8yMTAwMDEwMTA1MDAwMFowIDEeMBwGA1UEAxMVUHVibGljIFRMUyBUZXN0IENoaWxkMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAj8C2lateuWrKepD2FKXE330HM8kwC7KMn9goMcvNGIJUrlkQTklxE8KhnU5Kr2jaSPstKuolIw55iNNgr5rHmzuUPd2Y7EMk48LxF7WTlRiy3Rhxcf3qgSVCe5DxGNcF9tiSEyVJ8fuFYajH+3skWkplO4XjaDFvaacPoa9S5/6hlfi3zh5q64D3/wLhr3F9IqBn+E2a/10V92zCfnxfm+qrWleUvGwp+7GQr7SoE1mWBkSzADuboRAWgpRe7HqN22fHZZdnGQ4kAFk87X22s478NWBK7t2saC59eqfsk8Aykw7Em05MtchEVkfiSwARV1LHoe4C0LJkWqyRWRG8twIDAQABo2cwZTAdBgNVHQ4EFgQUp1+RXQ9k/lSyjnGhwuc5BIHss7gwDgYDVR0PAQH/BAQDAgWgMB8GA1UdIwQYMBaAFI9OTKgm8CJ8Qic9f8F8xqTgmOyTMBMGA1UdJQQMMAoGCCsGAQUFBwMBMA0GCSqGSIb3DQEBCwUAA4IBAQCJjMn6+yUFJ8sxQbP3HPMRx3Ui89GQlqSka8A+ufpcesbRDY2GdsHGW9/WkYI3+oNaNeFpnLGAvNzqQIIkjPqDwzQrRzWJ5jRbZn2GGanjWi1itWVK1ruZ4emf3RPgzUJUOlqT59/PLqAVGJhTm5iOFxcQqjX5vgDa72pWpvYzCkwEW+9PHvNHS0IkkvjAD3tidRyuabwXB32yq0WVyvTm09pHcw/bREmMHJWbDfOix14rSe2eiXAiRubiX3rEKt2n6iEhXRgtIxBtUKHPqs3vBnUydGFkEVfpni5gXxvEjPNpBv2M1UM9ud6Jov8iX0wNA0dJma9SP0mWq48e9TVK";
}
