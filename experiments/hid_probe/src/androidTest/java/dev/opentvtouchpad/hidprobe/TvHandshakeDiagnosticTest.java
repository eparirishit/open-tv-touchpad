package dev.opentvtouchpad.hidprobe;

import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.Locale;
import javax.net.ssl.SSLSocket;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

/** Explicit live TLS diagnosis only: no pairing frames, input, identity creation or store writes. */
@RunWith(AndroidJUnit4.class)
public class TvHandshakeDiagnosticTest {
    @Test public void inspectExistingIdentityHandshakeOnlyWhenExplicitlyRequested() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        assumeTrue("TLS_ONLY".equals(args.getString("liveTvDiagnostic")));
        String address = args.getString("projectorAddress");
        assertTrue("A canonical private projector address is required", TvEnrollment.validAddress(address));
        Bundle result = new Bundle();
        String stage = "IDENTITY";
        try {
            var identity = TvIdentity.load(TvEnrollment.KEY_ALIAS);
            String oid = ((X509Certificate) identity.getCertificate()).getSigAlgOID();
            result.putString("client_signature", "1.2.840.113549.1.1.10".equals(oid) ? "PSS"
                : "1.2.840.113549.1.1.11".equals(oid) ? "PKCS1_SHA256" : "OTHER");
            stage = "SOCKET";
            try (SSLSocket socket = TvTls.pairingSocket(identity)) {
                String[] parts = address.split("\\.");
                byte[] bytes = new byte[4];
                for (int i = 0; i < 4; i++) bytes[i] = (byte) Integer.parseInt(parts[i]);
                stage = "CONNECT";
                socket.connect(new InetSocketAddress(InetAddress.getByAddress(bytes), 6467), 5000);
                socket.setSoTimeout(5000);
                stage = "TLS";
                socket.startHandshake();
                result.putString("handshake", "COMPLETE");
            }
        } catch (Exception failure) {
            result.putString("handshake", "FAILED_" + stage);
            int depth = 0;
            for (Throwable cause = failure; cause != null && depth < 8; cause = cause.getCause(), depth++) {
                String category = cause instanceof java.security.cert.CertificateException ? "CERTIFICATE"
                    : cause instanceof javax.net.ssl.SSLHandshakeException ? "HANDSHAKE"
                    : cause instanceof java.net.SocketTimeoutException ? "TIMEOUT"
                    : cause instanceof java.security.InvalidKeyException ? "KEY"
                    : cause instanceof java.security.SignatureException ? "SIGNATURE"
                    : cause instanceof java.security.KeyStoreException ? "KEYSTORE" : "OTHER";
                result.putString("cause_" + depth, category);
                String message = String.valueOf(cause.getMessage()).toUpperCase(Locale.ROOT);
                String[] reasons = {"UNSUPPORTED PEER SIGNATURE", "INVALID PEER SIGNATURE",
                    "UNSUPPORTED PEER KEY", "INVALID PEER CHAIN", "EXPIRED", "NOT YET VALID",
                    "CERTIFICATE_VERIFY_FAILED", "NO_SUITABLE_SIGNATURE_ALGORITHM",
                    "NO_COMMON_SIGNATURE_ALGORITHMS", "SSLV3_ALERT_HANDSHAKE_FAILURE",
                    "TLSV1_ALERT_INTERNAL_ERROR", "TLSV1_ALERT_UNKNOWN_CA", "BAD_SIGNATURE",
                    "KEY_USAGE_BIT_INCORRECT", "INCOMPATIBLE_DIGEST", "INCOMPATIBLE_PADDING"};
                for (int i = 0; i < reasons.length; i++)
                    if (message.contains(reasons[i])) result.putBoolean("reason_" + i, true);
            }
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, result);
    }
}
