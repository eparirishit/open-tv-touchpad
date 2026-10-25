package dev.opentvtouchpad.hidprobe;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class TvEnrollmentTest {
    private static final String ADDRESS = "192.168.50.20";
    private static final String ALIAS = "open-tv-remote-client-v1";
    private static final byte[] FINGERPRINT = new byte[32];
    // Public certificate only; the independent native TLS fixture uses the same test CA.
    private static final byte[] CERTIFICATE = Base64.getDecoder().decode("MIIDATCCAemgAwIBAgIJAICTzJVaFFhTMA0GCSqGSIb3DQEBCwUAMB0xGzAZBgNVBAMTElB1YmxpYyBUTFMgVGVzdCBDQTAgFw0yMDAxMDEwNTAwMDBaGA8yMTAwMDEwMTA1MDAwMFowHTEbMBkGA1UEAxMSUHVibGljIFRMUyBUZXN0IENBMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApPXCujvvdcnW6WE52ZmsXU7AucC1wgtshX3bjbUCdbyzMUM3x39vSid2B12U9G01X2VBrfuCHzgcDwpzfsyaeo6C0A5Az5TDYSG9HRXLRlBJM/ovMwSWZNzGB/4CPMQzeQUoN5QJoFwpI2sNnRr+72BG02yDuIQhS28Ln5kTXFVfJvMOPx+m5rNvFv8BoADUxB42/R05Gj8y8YLUsKtzweUesvyu+g8M8zCDvn65UYHoPcjIOCsz1W5E8qUQsk2IhU/pemsvZEQt/vIBLdd31RRCzgA9yErzYKnqUNqWv6/tzHaEVc9z9gsRHZWa6jNHjoKsIopqEgc84RHXvaKEMwIDAQABo0IwQDAdBgNVHQ4EFgQUj05MqCbwInxCJz1/wXzGpOCY7JMwDgYDVR0PAQH/BAQDAgIEMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEBAELReTAdgsXZMpc++KUgJUXfzz4jN9mP8Ww60m6wDY+0rbZO5j7CXvrlS3C1y9n+7i26hPk/I6LkfW9+OaSe8Ma4iA8JRCNa2Zlvt8ozmS8Fmt3g9s2WJE0ALoIgLZeDR1hKXCVIKcnQsHCCvmpGKb3eOM1F3bXS64Qz2RtJ3uZvHXR2p4TScsvpnh+EQDCKcWu9dUOU7HeXGBYZKRfsZ1nYkWv0ZMv0fwiasvha5YTMbet15VzAHupAAhqVPqFCDG66rVt1jJpWilv5BDjH4Imf9M0A0GxKtE3AnQ5Dm5puRC0fzv921dkx3DyoRtvWCV6QDs4PDWIQ7fRYBj2YXFo=");

    @Test public void verifiedEmptyAllowsAbsentModeWithoutAdoptingLeftoverKey() {
        Fixture f = new Fixture();
        TvEnrollment store = f.open();
        assertEquals(TvEnrollment.State.ABSENT, store.state());
        assertNull(store.record());
        assertEquals(0, f.identity.lookups);
        assertEquals(0, f.identity.deletes);
        assertFalse(store.changeLocator(ADDRESS));
        assertFalse(store.save(null));
        assertEquals(0, f.disk.writes);
    }

    @Test public void saveAndReopenRetainOnlyTheExactIdentityAndLocator() throws Exception {
        Fixture f = new Fixture();
        TvEnrollment store = f.open();
        assertTrue(store.save(record()));
        assertArrayEquals(encoded(1, ADDRESS, ALIAS, FINGERPRINT, CERTIFICATE), f.disk.bytes);
        assertEquals(TvEnrollment.State.VALID, store.state());
        assertRecord(f.open().record(), ADDRESS);
        assertEquals(0, f.identity.deletes);
    }

    @Test public void recordDefensivelyCopiesInputsAndOutputs() {
        byte[] fingerprint = FINGERPRINT.clone();
        byte[] certificate = CERTIFICATE.clone();
        TvEnrollment.Record record = new TvEnrollment.Record(ADDRESS, fingerprint, certificate);
        fingerprint[0] ^= 1;
        certificate[0] ^= 1;
        assertRecord(record, ADDRESS);
        record.clientFingerprint()[0] ^= 1;
        record.peerCertificate()[0] ^= 1;
        assertRecord(record, ADDRESS);
    }

    @Test public void addressValidationAcceptsOnlyCanonicalLiteralRfc1918() {
        for (String address : new String[]{"10.0.0.0", "10.255.255.255", "172.16.0.0",
                "172.31.255.255", "192.168.0.0", "192.168.255.255"})
            assertTrue(address, TvEnrollment.validAddress(address));
        for (String address : invalidAddresses()) {
            assertFalse(address, TvEnrollment.validAddress(address));
            assertThrows(IllegalArgumentException.class,
                () -> new TvEnrollment.Record(address, FINGERPRINT, CERTIFICATE));
        }
    }

    @Test public void recordRejectsBadFingerprintAndNonCanonicalCertificate() {
        for (byte[] fingerprint : new byte[][]{null, new byte[0], new byte[31], new byte[33]})
            assertThrows(IllegalArgumentException.class,
                () -> new TvEnrollment.Record(ADDRESS, fingerprint, CERTIFICATE));
        for (byte[] certificate : new byte[][]{null, new byte[0], new byte[]{1, 2},
                Arrays.copyOf(CERTIFICATE, CERTIFICATE.length + 1), new byte[16385],
                concat(CERTIFICATE, CERTIFICATE)})
            assertThrows(IllegalArgumentException.class,
                () -> new TvEnrollment.Record(ADDRESS, FINGERPRINT, certificate));
    }

    @Test public void malformedSchemaNeverSelectsAbsentOrOverwritesDisk() throws Exception {
        List<byte[]> malformed = new ArrayList<>();
        malformed.add(null);
        malformed.add(new byte[]{1});
        malformed.add(new byte[16384]);
        malformed.add(new byte[16385]);
        for (int version : new int[]{0, 2, -1, Integer.MAX_VALUE})
            malformed.add(encoded(version, ADDRESS, ALIAS, FINGERPRINT, CERTIFICATE));
        malformed.add(encoded(1, ADDRESS, "other-alias", FINGERPRINT, CERTIFICATE));
        for (String address : invalidAddresses()) if (address != null)
            malformed.add(encoded(1, address, ALIAS, FINGERPRINT, CERTIFICATE));
        for (int size : new int[]{0, 31, 33})
            malformed.add(encoded(1, ADDRESS, ALIAS, new byte[size], CERTIFICATE));
        malformed.add(encoded(1, ADDRESS, ALIAS, FINGERPRINT, new byte[0]));
        malformed.add(encoded(1, ADDRESS, ALIAS, FINGERPRINT, new byte[]{1, 2}));
        malformed.add(encoded(1, ADDRESS, ALIAS, FINGERPRINT, concat(CERTIFICATE, CERTIFICATE)));
        byte[] valid = encoded(1, ADDRESS, ALIAS, FINGERPRINT, CERTIFICATE);
        malformed.add(Arrays.copyOf(valid, valid.length + 1));
        int fingerprintLengthOffset = 4 + 2 + ADDRESS.length() + 2 + ALIAS.length();
        int certificateLengthOffset = fingerprintLengthOffset + 4 + 32;
        for (int offset : new int[]{4, 6 + ADDRESS.length()}) {
            byte[] changed = valid.clone();
            ByteBuffer.wrap(changed).putShort(offset, (short) 65535);
            malformed.add(changed);
        }
        for (int length : new int[]{-1, 0, 16384, Integer.MAX_VALUE}) {
            byte[] changed = valid.clone();
            ByteBuffer.wrap(changed).putInt(certificateLengthOffset, length);
            malformed.add(changed);
        }
        byte[] hugeFingerprint = valid.clone();
        ByteBuffer.wrap(hugeFingerprint).putInt(fingerprintLengthOffset, Integer.MAX_VALUE);
        malformed.add(hugeFingerprint);
        // Modified UTF accepts overlong ASCII; the on-disk format must not.
        byte[] nonCanonicalUtf = new byte[valid.length + 1];
        System.arraycopy(valid, 0, nonCanonicalUtf, 0, 6);
        nonCanonicalUtf[5]++;
        nonCanonicalUtf[6] = (byte) 0xc0;
        nonCanonicalUtf[7] = (byte) 0xb1;
        System.arraycopy(valid, 7, nonCanonicalUtf, 8, valid.length - 7);
        malformed.add(nonCanonicalUtf);
        for (byte[] bytes : malformed) assertUnusableLoad(bytes);
    }

    @Test public void everyTruncationFailsClosedWithoutAllocatingDeclaredLengths() throws Exception {
        byte[] valid = encoded(1, ADDRESS, ALIAS, FINGERPRINT, CERTIFICATE);
        for (int length = 1; length < valid.length; length++)
            assertUnusableLoad(Arrays.copyOf(valid, length));
    }

    @Test public void missingMismatchedOrFailedIdentityNeverBecomesAbsent() throws Exception {
        for (int mode = 0; mode < 4; mode++) {
            Fixture f = new Fixture();
            f.disk.bytes = encoded(1, ADDRESS, ALIAS, FINGERPRINT, CERTIFICATE);
            breakIdentity(f.identity, mode);
            assertBlocked(f.open());
            assertEquals(0, f.disk.writes);
        }
    }

    @Test public void identityFailureAtSaveBlocksUntilForget() {
        for (int mode = 0; mode < 4; mode++) {
            Fixture f = new Fixture();
            TvEnrollment store = f.open();
            breakIdentity(f.identity, mode);
            assertFalse(store.save(record()));
            assertBlocked(store);
            assertEquals(0, f.disk.writes);
        }
    }

    @Test public void failedInitialReadCannotFallBackToAbsent() {
        Fixture f = new Fixture();
        f.disk.failRead = true;
        TvEnrollment store = f.open();
        f.disk.failRead = false;
        assertBlocked(store);
        assertTrue(store.forget());
        assertEquals(TvEnrollment.State.ABSENT, store.state());
    }

    @Test public void failedSaveWriteReadbackOrLostWriteLatchesForProcess() {
        for (int mode = 0; mode < 4; mode++) {
            Fixture f = new Fixture();
            TvEnrollment store = f.open();
            f.disk.failWrite = mode == 0;
            f.disk.failRead = mode == 1;
            f.disk.loseWrite = mode == 2;
            f.disk.runtimeWriteFailure = mode == 3;
            assertFalse(store.save(record()));
            f.disk.clearFailures();
            assertBlocked(store);
            assertTrue(store.forget());
            assertEquals(TvEnrollment.State.ABSENT, store.state());
            f.identity.fingerprint = FINGERPRINT.clone();
            assertTrue(store.save(record()));
        }
    }

    @Test public void identityChangeDuringReadbackCannotPublishValidRecord() {
        Fixture f = new Fixture();
        TvEnrollment store = f.open();
        f.disk.afterWrite = () -> f.identity.fingerprint = null;
        assertFalse(store.save(record()));
        assertBlocked(store);
    }

    @Test public void existingEnrollmentCannotBeReplacedButLocatorPreservesPin() {
        Fixture f = new Fixture();
        TvEnrollment store = f.open();
        assertTrue(store.save(record()));
        byte[] saved = f.disk.bytes.clone();
        assertFalse(store.save(new TvEnrollment.Record("10.0.0.2", FINGERPRINT, CERTIFICATE)));
        assertArrayEquals(saved, f.disk.bytes);
        assertFalse(store.changeLocator("example.test"));
        assertArrayEquals(saved, f.disk.bytes);
        assertTrue(store.changeLocator("172.16.1.2"));
        assertRecord(store.record(), "172.16.1.2");
        assertRecord(f.open().record(), "172.16.1.2");
        assertEquals(0, f.identity.deletes);
    }

    @Test public void locatorFailureDoesNotRetainUsableInMemoryPin() {
        Fixture f = new Fixture();
        TvEnrollment store = f.open();
        assertTrue(store.save(record()));
        f.disk.loseWrite = true;
        assertFalse(store.changeLocator("10.1.1.1"));
        assertBlocked(store);
        assertRecord(f.open().record(), ADDRESS);
    }

    @Test public void locatorChangeCannotSilentlyReplaceMissingIdentity() {
        Fixture f = new Fixture();
        TvEnrollment store = f.open();
        assertTrue(store.save(record()));
        f.identity.fingerprint = null;
        assertFalse(store.changeLocator("10.1.1.1"));
        assertBlocked(store);
        assertEquals(1, f.disk.writes);
    }

    @Test public void forgetVerifiesEmptyBeforeDeletingFixedAliasAndAllowsAbsentMode() {
        Fixture f = new Fixture();
        TvEnrollment store = f.open();
        assertTrue(store.save(record()));
        f.events.clear();
        assertTrue(store.forget());
        assertEquals(Arrays.asList("write", "read", "delete"), f.events);
        assertArrayEquals(new byte[0], f.disk.bytes);
        assertEquals(TvEnrollment.State.ABSENT, store.state());
        assertNull(store.record());
        assertEquals(TvEnrollment.State.ABSENT, f.open().state());
    }

    @Test public void failedForgetNeverDeletesKeyOrSelectsAbsentMode() {
        for (int mode = 0; mode < 3; mode++) {
            Fixture f = new Fixture();
            TvEnrollment store = f.open();
            assertTrue(store.save(record()));
            f.disk.failWrite = mode == 0;
            f.disk.failRead = mode == 1;
            f.disk.loseWrite = mode == 2;
            assertFalse(store.forget());
            assertEquals(0, f.identity.deletes);
            f.disk.clearFailures();
            assertBlocked(store);
            if (mode != 1) assertEquals(TvEnrollment.State.VALID, f.open().state());
            assertTrue(store.forget());
            assertEquals(TvEnrollment.State.ABSENT, store.state());
        }
    }

    @Test public void deletionFailureKeepsTombstoneButRequiresExplicitForgetRetry() {
        Fixture f = new Fixture();
        TvEnrollment store = f.open();
        assertTrue(store.save(record()));
        f.identity.failDelete = true;
        assertFalse(store.forget());
        assertArrayEquals(new byte[0], f.disk.bytes);
        assertBlocked(store);
        // Restart after the verified tombstone cannot resurrect the enrollment.
        assertEquals(TvEnrollment.State.ABSENT, f.open().state());
        f.identity.failDelete = false;
        assertBlocked(store);
        assertTrue(store.forget());
        assertEquals(TvEnrollment.State.ABSENT, store.state());
    }

    private static String[] invalidAddresses() {
        return new String[]{null, "", "localhost", "example.test", "127.0.0.1", "0.0.0.0",
            "8.8.8.8", "169.254.1.1", "172.15.255.255", "172.32.0.0", "192.169.0.1",
            "::1", "::ffff:192.168.1.1", "192.168.01.1", "10.1.1", "10.1.1.1.",
            "10.1.1.256", "10.1.1.-1", "10.1.1.+1", " 10.1.1.1", "10.1.1.1\n",
            "10.1.1.1:6466", "http://10.1.1.1", "１０.1.1.1", "10.1.1.000"};
    }

    private static TvEnrollment.Record record() {
        return new TvEnrollment.Record(ADDRESS, FINGERPRINT, CERTIFICATE);
    }

    private static void assertRecord(TvEnrollment.Record record, String address) {
        assertNotNull(record);
        assertEquals(address, record.address());
        assertArrayEquals(FINGERPRINT, record.clientFingerprint());
        assertArrayEquals(CERTIFICATE, record.peerCertificate());
    }

    private static void assertBlocked(TvEnrollment store) {
        assertEquals(TvEnrollment.State.UNUSABLE, store.state());
        assertNull(store.record());
        assertFalse(store.save(record()));
        assertFalse(store.changeLocator("10.0.0.2"));
        assertEquals(TvEnrollment.State.UNUSABLE, store.state());
    }

    private static void assertUnusableLoad(byte[] bytes) {
        Fixture f = new Fixture();
        f.disk.bytes = bytes;
        TvEnrollment store = f.open();
        assertBlocked(store);
        assertArrayEquals(bytes, f.disk.bytes);
        assertEquals(0, f.disk.writes);
        assertEquals(0, f.identity.deletes);
    }

    private static void breakIdentity(FakeIdentity identity, int mode) {
        if (mode == 0) identity.fingerprint = null;
        if (mode == 1) identity.fingerprint[0] = 1;
        if (mode == 2) identity.fingerprint = new byte[31];
        if (mode == 3) identity.failLookup = true;
    }

    private static byte[] encoded(int version, String address, String alias,
            byte[] fingerprint, byte[] certificate) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(version);
        out.writeUTF(address);
        out.writeUTF(alias);
        out.writeInt(fingerprint.length);
        out.write(fingerprint);
        out.writeInt(certificate.length);
        out.write(certificate);
        return bytes.toByteArray();
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static final class Fixture {
        final List<String> events = new ArrayList<>();
        final FakeDisk disk = new FakeDisk(events);
        final FakeIdentity identity = new FakeIdentity(events);
        TvEnrollment open() { return new TvEnrollment(disk, identity); }
    }

    private static final class FakeDisk implements TvEnrollment.Disk {
        final List<String> events;
        byte[] bytes = new byte[0];
        boolean failRead, failWrite, loseWrite, runtimeWriteFailure;
        int writes;
        Runnable afterWrite;
        FakeDisk(List<String> events) { this.events = events; }
        public byte[] read() throws IOException {
            events.add("read");
            if (failRead) throw new IOException("Synthetic read failure");
            return bytes == null ? null : bytes.clone();
        }
        public void write(byte[] record) throws IOException {
            events.add("write");
            writes++;
            if (failWrite) throw new IOException("Synthetic write failure");
            if (runtimeWriteFailure) throw new IllegalStateException("Synthetic write failure");
            if (!loseWrite) bytes = record.clone();
            if (afterWrite != null) afterWrite.run();
        }
        void clearFailures() { failRead = failWrite = loseWrite = runtimeWriteFailure = false; }
    }

    private static final class FakeIdentity implements TvEnrollment.Identity {
        final List<String> events;
        byte[] fingerprint = FINGERPRINT.clone();
        boolean failLookup, failDelete;
        int lookups, deletes;
        FakeIdentity(List<String> events) { this.events = events; }
        public byte[] fingerprint(String alias) throws Exception {
            assertEquals(ALIAS, alias);
            lookups++;
            if (failLookup) throw new IOException("Synthetic identity failure");
            return fingerprint == null ? null : fingerprint.clone();
        }
        public void delete(String alias) throws Exception {
            assertEquals(ALIAS, alias);
            events.add("delete");
            deletes++;
            if (failDelete) throw new IOException("Synthetic deletion failure");
            fingerprint = null;
        }
    }
}
