package dev.opentvtouchpad.hidprobe;

import dev.opentvtouchpad.hidprobe.wire.PoloProto.*;
import dev.opentvtouchpad.hidprobe.wire.RemoteProto.RemoteMessage;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import javax.net.ssl.*;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class TvRemoteClientTest {
    private final AtomicLong now = new AtomicLong(10000);
    private final List<TvRemoteClient> clients = new ArrayList<>();
    private final List<FakeSocket> sockets = new ArrayList<>();
    private final List<Native> natives = new ArrayList<>();
    private final List<Events> callbacks = new ArrayList<>();
    private final X509Certificate certificate = parse(CA_DER);
    private final X509Certificate other = parse(CHILD_DER);

    @After public void stopAll() throws Exception {
        for (TvRemoteClient client : clients) client.stop();
        for (Native io : natives) {
            if (io.identityGate != null) io.identityGate.countDown();
            if (io.socketGate != null) io.socketGate.countDown();
        }
        for (FakeSocket socket : sockets) socket.close();
        for (Events events : callbacks) await(() -> events.closed.get() == 1);
    }

    @Test public void controlRequiresHandshakeThenOnlyFixedCommandsAndOneAttempt() throws Exception {
        Native io = new Native(); Events events = new Events();
        FakeSocket socket = io.control();
        TvRemoteClient client = client(io, events);
        assertFalse(client.submit(TvCommand.HOME));
        client.connect(record());
        await(() -> socket.handshakes.get() == 1);
        assertFalse(client.ready()); assertEquals(0, events.connected.get());
        socket.handshakeMessages();
        await(client::ready);
        assertEquals(1, events.connected.get());
        assertEquals(6466, socket.port);
        assertArrayEquals(certificate.getEncoded(), io.pin.getEncoded());
        assertTrue(client.submit(TvCommand.HOME));
        await(() -> socket.messages().size() == 3);
        RemoteMessage message = TvControlProtocolTest.parse(socket.messages().get(2));
        assertEquals(3, message.getRemoteKeyInject().getKeyCodeValue());
        assertEquals(3, message.getRemoteKeyInject().getDirectionValue());
        client.connect(record()); client.beginPair("10.0.0.2");
        assertEquals(1, io.identities.get());
        client.stop(); await(() -> events.closed.get() == 1);
        assertFalse(client.ready()); assertFalse(client.submit(TvCommand.NETFLIX));
        assertEquals(0, events.failed.get());
    }

    @Test public void invalidLocatorAndMismatchedIdentityNeverOpenSocket() throws Exception {
        for (String address : new String[]{"127.0.0.1", "8.8.8.8", "tv.local", "10.00.0.2"}) {
            Native io = new Native(); Events events = new Events();
            client(io, events).beginPair(address);
            await(() -> events.closed.get() == 1);
            assertEquals(1, events.failed.get()); assertEquals(0, io.identities.get());
        }
        Native io = new Native(); Events events = new Events();
        client(io, events).connect(new TvEnrollment.Record("10.0.0.2", new byte[32], certificate.getEncoded()));
        await(() -> events.closed.get() == 1);
        assertEquals(1, events.failed.get()); assertEquals(0, io.created.get());
    }

    @Test public void stopBeforeStartAndDuringKeyGenerationPreventsLateEnrollment() throws Exception {
        Native io = new Native(); Events events = new Events();
        TvRemoteClient stopped = client(io, events);
        stopped.stop(); stopped.beginPair("10.0.0.2");
        await(() -> events.closed.get() == 1); assertEquals(0, io.identities.get());
        Native delayed = new Native(); Events later = new Events();
        delayed.identityGate = new CountDownLatch(1);
        TvRemoteClient client = client(delayed, later);
        client.beginPair("10.0.0.2");
        await(() -> delayed.identities.get() == 1);
        client.stop();
        assertEquals(0, later.closed.get());
        delayed.identityGate.countDown();
        await(() -> later.closed.get() == 1);
        assertEquals(0, delayed.created.get()); assertNull(later.record);
        assertEquals(0, later.needCode.get());
    }

    @Test public void stopDuringSocketCreationClosesLateSocketWithoutConnecting() throws Exception {
        Native io = new Native(); Events events = new Events(); FakeSocket socket = io.control();
        io.socketGate = new CountDownLatch(1);
        TvRemoteClient client = client(io, events); client.connect(record());
        await(() -> io.created.get() == 1); client.stop();
        assertEquals(0, events.closed.get());
        io.socketGate.countDown(); await(() -> events.closed.get() == 1);
        assertTrue(socket.isClosed()); assertEquals(0, socket.connects.get());
    }

    @Test public void absoluteConnectDeadlineClosesBlockedHandshake() throws Exception {
        Native io = new Native(); Events events = new Events(); FakeSocket socket = io.control();
        socket.blockHandshake = true;
        TvRemoteClient client = client(io, events); client.connect(record());
        await(() -> socket.handshakes.get() == 1);
        now.addAndGet(12000); await(() -> events.closed.get() == 1);
        assertTrue(socket.isClosed()); assertEquals(1, events.failed.get()); assertFalse(client.ready());
    }

    @Test public void stopUnblocksReaderAndWriterIndependently() throws Exception {
        Native io = new Native(); Events events = new Events(); FakeSocket socket = io.control();
        TvRemoteClient client = connected(io, events, socket);
        socket.blockWrites = true;
        assertTrue(client.submit(TvCommand.HOME)); await(() -> socket.writeBlocked.get());
        client.stop(); await(() -> events.closed.get() == 1);
        assertTrue(socket.isClosed()); assertEquals(2, socket.messages().size());
        assertEquals(0, events.failed.get());
    }

    @Test public void writeWatchdogClosesWithoutWriterProgressAndCannotAffectAnotherOwner() throws Exception {
        Native io = new Native(); Events events = new Events(); FakeSocket socket = io.control();
        TvRemoteClient client = connected(io, events, socket); socket.blockWrites = true;
        client.submit(TvCommand.HOME); await(() -> socket.writeBlocked.get());
        now.addAndGet(2000); await(() -> events.closed.get() == 1);
        assertEquals(1, events.failed.get());
        Native next = new Native(); Events later = new Events(); FakeSocket fresh = next.control();
        TvRemoteClient newOwner = connected(next, later, fresh);
        client.stop(); now.addAndGet(2000);
        assertTrue(newOwner.submit(TvCommand.BACK)); await(() -> fresh.messages().size() == 3);
        assertFalse(fresh.isClosed()); assertEquals(0, later.closed.get());
    }

    @Test public void queueIsOnePendingCommandAndTenPerSecondWithAgeCheck() throws Exception {
        Native io = new Native(); Events events = new Events(); FakeSocket socket = io.control();
        TvRemoteClient client = connected(io, events, socket); socket.blockWrites = true;
        assertTrue(client.submit(TvCommand.HOME)); await(() -> socket.writeBlocked.get());
        assertFalse(client.submit(TvCommand.BACK));
        now.addAndGet(100); assertTrue(client.submit(TvCommand.BACK));
        now.addAndGet(100); assertFalse(client.submit(TvCommand.NETFLIX));
        now.addAndGet(151); socket.releaseWrites();
        await(() -> socket.messages().size() == 3);
        now.addAndGet(100); await(() -> client.submit(TvCommand.UP));
        await(() -> socket.messages().size() == 4);
        RemoteMessage last = TvControlProtocolTest.parse(socket.messages().get(3));
        assertEquals(19, last.getRemoteKeyInject().getKeyCodeValue());
    }

    @Test public void eightRepliesBoundBacklogEvenWhenWriterIsBlocked() throws Exception {
        Native io = new Native(); Events events = new Events(); FakeSocket socket = io.control();
        TvRemoteClient client = connected(io, events, socket); socket.blockWrites = true;
        client.submit(TvCommand.HOME); await(() -> socket.writeBlocked.get());
        for (int i = 0; i < 9; i++) socket.feed(TvControlProtocolTest.ping(i));
        await(() -> events.closed.get() == 1);
        assertEquals(1, events.failed.get()); assertTrue(socket.isClosed());
    }

    @Test public void readBurstAndIdleDeadlinesBoundInformationalTraffic() throws Exception {
        Native io = new Native(); Events events = new Events(); FakeSocket socket = io.control();
        connected(io, events, socket);
        for (int i = 0; i < 201; i++) socket.feed(TvControlProtocolTest.info());
        await(() -> events.closed.get() == 1); assertEquals(1, events.failed.get());
        Native idle = new Native(); Events later = new Events(); FakeSocket quiet = idle.control();
        connected(idle, later, quiet); now.addAndGet(30000);
        await(() -> later.closed.get() == 1); assertEquals(1, later.failed.get());
    }

    @Test public void deadlineIsCheckedAtSubmissionAndDispatchEvenWithoutUiTimer() throws Exception {
        Native io = new Native(); Events events = new Events(); FakeSocket socket = io.control();
        TvRemoteClient client = new TvRemoteClient(now::get, now.get()+1000, events, io); clients.add(client); callbacks.add(events);
        client.connect(record()); socket.handshakeMessages(); await(client::ready);
        now.addAndGet(1000);
        assertFalse(client.ready()); assertFalse(client.submit(TvCommand.HOME));
        await(() -> events.closed.get() == 1); assertEquals(2, socket.messages().size());
    }

    @Test public void malformedReadAndMissingFeaturesFailWithoutRetry() throws Exception {
        for (byte[] input : new byte[][]{new byte[]{1}, TvControlProtocolTest.configure(2)}) {
            Native io = new Native(); Events events = new Events(); FakeSocket socket = io.control();
            TvRemoteClient client = client(io, events); client.connect(record()); socket.feed(input);
            await(() -> events.closed.get() == 1);
            assertEquals(1, events.failed.get()); assertEquals(1, io.created.get());
            assertEquals(0, events.connected.get());
        }
    }

    @Test public void pairingUsesSessionKeysSequentialPinnedControlAndNoInputOrPersistence() throws Exception {
        Native io = new Native(); Events events = new Events(); FakeSocket pairing = io.pair();
        FakeSocket control = io.control();
        TvRemoteClient client = client(io, events); client.beginPair("10.0.0.2");
        pairing.pairingMessages(); await(client::needsCode);
        assertEquals(1, events.needCode.get()); assertFalse(client.submit(TvCommand.HOME));
        client.finishPair(code(certificate, other));
        pairing.feed(polo().setSecretAck(SecretAck.newBuilder().setSecret(com.google.protobuf.ByteString.copyFrom(new byte[32]))).build().toByteArray());
        control.handshakeMessages(); await(() -> events.record != null);
        assertTrue(pairing.isClosed()); assertEquals(6467, pairing.port); assertEquals(6466, control.port);
        assertTrue(io.pairClosedBeforeControl); assertTrue(client.ready());
        assertFalse(client.submit(TvCommand.HOME)); assertEquals(0, events.connected.get());
        assertEquals(1, events.enrolled.get());
        assertArrayEquals(other.getEncoded(), events.record.peerCertificate());
        assertArrayEquals(fingerprint(), events.record.clientFingerprint());
        assertEquals(2, control.messages().size());
        client.stop(); await(() -> events.closed.get() == 1); assertEquals(1, io.identities.get());
    }

    @Test public void wrongCodeTimeoutAndCancelledCodeCannotEnroll() throws Exception {
        Native io = new Native(); Events events = new Events(); FakeSocket pair = io.pair();
        TvRemoteClient client = client(io, events); client.beginPair("10.0.0.2"); pair.pairingMessages();
        await(client::needsCode); client.finishPair("zzzzzz");
        await(() -> events.closed.get() == 1); assertNull(events.record); assertEquals(1, io.created.get());
        Native expired = new Native(); Events late = new Events(); FakeSocket otherPair = expired.pair();
        TvRemoteClient timeout = client(expired, late); timeout.beginPair("10.0.0.2"); otherPair.pairingMessages();
        await(timeout::needsCode); now.addAndGet(120000);
        await(() -> late.closed.get() == 1); timeout.finishPair(code(certificate, other));
        assertNull(late.record); assertFalse(timeout.needsCode()); assertEquals(1, expired.created.get());
    }

    @Test public void changedControlPinAndStopAfterSecretNeverProduceEnrollment() throws Exception {
        Native io = new Native(); Events events = new Events(); FakeSocket pairing = io.pair();
        io.controlFailure = true;
        TvRemoteClient client = client(io, events); client.beginPair("10.0.0.2"); pairing.pairingMessages();
        await(client::needsCode); client.finishPair(code(certificate, other));
        pairing.feed(polo().setSecretAck(SecretAck.newBuilder().setSecret(com.google.protobuf.ByteString.copyFrom(new byte[32]))).build().toByteArray());
        await(() -> events.closed.get() == 1);
        assertNull(events.record); assertEquals(1, events.failed.get()); assertTrue(pairing.isClosed());
        Native stopped = new Native(); Events later = new Events(); FakeSocket p = stopped.pair();
        TvRemoteClient cancelled = client(stopped, later); cancelled.beginPair("10.0.0.2"); p.pairingMessages();
        await(cancelled::needsCode); cancelled.finishPair(code(certificate, other)); cancelled.stop();
        await(() -> later.closed.get() == 1); assertNull(later.record); assertEquals(1, stopped.created.get());
    }

    @Test public void connectAndProtocolNegotiationCannotOutliveTwelveSeconds() throws Exception {
        for (boolean blockConnect : new boolean[]{true, false}) {
            Native io = new Native(); Events events = new Events(); FakeSocket socket = io.control();
            socket.blockConnect = blockConnect;
            TvRemoteClient client = client(io, events); client.connect(record());
            await(() -> blockConnect ? socket.connects.get() == 1 : socket.inputWaits.get() > 0);
            now.addAndGet(12000); await(() -> events.closed.get() == 1);
            assertEquals(1, events.failed.get()); assertEquals(0, events.connected.get());
            assertFalse(client.ready()); assertTrue(socket.isClosed());
        }
    }

    @Test public void pairingExchangeTimeoutAndUnexpectedMessageFailBeforeCode() throws Exception {
        for (boolean unexpected : new boolean[]{false, true}) {
            Native io = new Native(); Events events = new Events(); FakeSocket pair = io.pair();
            TvRemoteClient client = client(io, events); client.beginPair("10.0.0.2");
            await(() -> pair.inputWaits.get() > 0);
            if (unexpected) pair.feed(polo().setConfigurationAck(ConfigurationAck.getDefaultInstance()).build().toByteArray());
            else now.addAndGet(10000);
            await(() -> events.closed.get() == 1);
            assertEquals(1, events.failed.get()); assertEquals(0, events.needCode.get());
            assertEquals(1, io.created.get()); assertNull(events.record);
        }
    }

    @Test public void oversizedTruncatedAndStalledFramesTerminateActualReader() throws Exception {
        byte[][] frames = { {(byte)0x81, (byte)0x80, 4}, {4, 8}, {(byte)0x80} };
        for (int i = 0; i < frames.length; i++) {
            Native io = new Native(); Events events = new Events(); FakeSocket socket = io.control();
            TvRemoteClient client = client(io, events); client.connect(record());
            socket.raw(frames[i], i == 1);
            if (i == 2) { await(() -> socket.inputWaits.get() > 0); now.addAndGet(12000); }
            await(() -> events.closed.get() == 1);
            assertEquals(1, events.failed.get()); assertEquals(0, events.connected.get());
        }
    }

    @Test public void readBudgetRefillsAtOneFramePerTenMilliseconds() throws Exception {
        Native io = new Native(); Events events = new Events(); FakeSocket socket = io.control();
        TvRemoteClient client = connected(io, events, socket);
        // Three negotiation frames already consumed the initial burst.
        for (int i = 0; i < 197; i++) socket.feed(TvControlProtocolTest.info());
        drain(socket);
        for (int i = 0; i < 4; i++) {
            now.addAndGet(1000);
            for (int j = 0; j < 100; j++) socket.feed(TvControlProtocolTest.info());
            drain(socket);
            assertTrue(client.ready()); assertEquals(0, events.failed.get());
        }
        socket.feed(TvControlProtocolTest.info());
        await(() -> events.closed.get() == 1); assertEquals(1, events.failed.get());
    }

    @Test public void validHexWrongChecksumDoesNotSendSecretOrOpenControl() throws Exception {
        Native io = new Native(); Events events = new Events(); FakeSocket pair = io.pair();
        TvRemoteClient client = client(io, events); client.beginPair("10.0.0.2");
        pair.pairingMessages(); await(client::needsCode);
        String good = code(certificate, other);
        client.finishPair((good.charAt(0) == '0' ? "1" : "0") + good.substring(1));
        await(() -> events.closed.get() == 1);
        assertEquals(3, pair.messages().size()); assertEquals(1, io.created.get());
        assertEquals(1, events.failed.get()); assertNull(events.record);
    }

    @Test public void pairingSessionMustAuthenticateTheSelectedClientIdentity() throws Exception {
        Native io = new Native(); Events events = new Events();
        io.pairing = new FakeSocket(other, other); sockets.add(io.pairing);
        TvRemoteClient client = client(io, events); client.beginPair("10.0.0.2");
        await(() -> events.closed.get() == 1);
        assertEquals(0, io.pairing.messages().size()); assertEquals(0, events.needCode.get());
        assertEquals(1, events.failed.get()); assertNull(events.record);
    }

    @Test public void readinessWaitsForRepliesAndStopSuppressesPendingSuccess() throws Exception {
        Native io = new Native(); Events events = new Events(); FakeSocket socket = io.control();
        socket.blockWrites = true;
        TvRemoteClient client = client(io, events); client.connect(record()); socket.handshakeMessages();
        await(() -> socket.writeBlocked.get());
        assertFalse(client.ready()); assertFalse(client.submit(TvCommand.HOME));
        client.stop(); await(() -> events.closed.get() == 1);
        assertEquals(0, events.connected.get()); assertEquals(0, events.enrolled.get());
    }

    @Test public void failedFinalControlWriteCannotConnectWhileInputRemainsOpen() throws Exception {
        failedFinalReply(false, false);
    }

    @Test public void failedFinalControlFlushCannotConnectWhileInputRemainsOpen() throws Exception {
        failedFinalReply(false, true);
    }

    @Test public void failedPostPairControlWriteCannotEnrollWhileInputRemainsOpen() throws Exception {
        failedFinalReply(true, false);
    }

    @Test public void failedPostPairControlFlushCannotEnrollWhileInputRemainsOpen() throws Exception {
        failedFinalReply(true, true);
    }

    private void failedFinalReply(boolean pairing, boolean flush) throws Exception {
        // Repeated bounded attempts exercise the actual competing operation/writer threads.
        for (int attempt = 0; attempt < 64; attempt++) {
            Native io = new Native(); Events events = new Events();
            FakeSocket pair = pairing ? io.pair() : null;
            FakeSocket control = io.control();
            TvRemoteClient client = client(io, events);
            if (pairing) {
                client.beginPair("10.0.0.2"); pair.pairingMessages(); await(client::needsCode);
                client.finishPair(code(certificate, other));
                pair.feed(polo().setSecretAck(SecretAck.newBuilder()
                    .setSecret(com.google.protobuf.ByteString.copyFrom(new byte[32]))).build().toByteArray());
            } else client.connect(record());
            control.feed(TvControlProtocolTest.configure(579));
            await(() -> control.flushes.get() == 1);
            control.failWrite = !flush; control.failFlush = flush;
            control.feed(TvControlProtocolTest.active()); control.feed(TvControlProtocolTest.start());
            await(() -> events.closed.get() == 1);
            assertTrue("Injected output failure occurred while input was open", control.outputFailed.get());
            assertEquals(1, events.failed.get());
            assertEquals("Failed control exchange must not connect", 0, events.connected.get());
            assertEquals("Failed control exchange must not enroll", 0, events.enrolled.get());
            assertNull(events.record); assertFalse(client.ready());
            assertFalse(client.submit(TvCommand.HOME));
        }
    }

    private static void drain(FakeSocket socket) throws Exception {
        // The next blocking read is observed only after the last frame was processed.
        int before;
        synchronized (socket) { before = socket.inputWaits.get(); socket.notifyAll(); }
        await(() -> { synchronized (socket) { return socket.input.isEmpty() && socket.inputWaits.get() > before; } });
    }

    private TvRemoteClient client(Native io, Events events) {
        TvRemoteClient c = new TvRemoteClient(now::get, now.get()+300000, events, io); clients.add(c); callbacks.add(events); return c;
    }
    private TvRemoteClient connected(Native io, Events events, FakeSocket socket) throws Exception {
        TvRemoteClient c = client(io, events); c.connect(record()); socket.handshakeMessages(); await(c::ready); return c;
    }
    private byte[] fingerprint() throws Exception { return MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()); }
    private TvEnrollment.Record record() throws Exception { return new TvEnrollment.Record("10.0.0.2", fingerprint(), certificate.getEncoded()); }
    private static void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        do {
            if (condition.getAsBoolean()) return;
            Thread.sleep(1);
        } while (System.nanoTime() < end);
        fail("Worker condition completed");
    }
    private static OuterMessage.Builder polo() {
        return OuterMessage.newBuilder().setProtocolVersion(2).setStatus(OuterMessage.Status.STATUS_OK);
    }
    private static X509Certificate parse(String base64) {
        try { return TvTls.certificate(Base64.getDecoder().decode(base64)); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static String code(X509Certificate local, X509Certificate peer) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        for (X509Certificate c : new X509Certificate[]{local, peer}) {
            RSAPublicKey key=(RSAPublicKey)c.getPublicKey();
            for (java.math.BigInteger number : new java.math.BigInteger[]{key.getModulus(),key.getPublicExponent()}) {
                byte[] bytes=number.toByteArray(); digest.update(bytes, bytes[0]==0?1:0, bytes.length-(bytes[0]==0?1:0));
            }
        }
        digest.update(new byte[]{(byte)0xab,(byte)0xcd});
        return String.format(java.util.Locale.ROOT,"%02XABCD",digest.digest()[0]&255);
    }

    private static final class Events implements TvRemoteClient.Listener {
        final AtomicInteger needCode=new AtomicInteger(), connected=new AtomicInteger(), enrolled=new AtomicInteger(), failed=new AtomicInteger(), closed=new AtomicInteger();
        volatile TvEnrollment.Record record;
        public void needCode() { needCode.incrementAndGet(); }
        public void connected() { connected.incrementAndGet(); }
        public void enrolled(TvEnrollment.Record value) { enrolled.incrementAndGet(); record=value; }
        public void failed() { failed.incrementAndGet(); }
        public void closed() { closed.incrementAndGet(); }
    }
    private final class Native implements TvRemoteClient.NativeIo {
        final AtomicInteger identities=new AtomicInteger(), created=new AtomicInteger();
        volatile CountDownLatch identityGate, socketGate;
        Native() { natives.add(this); }
        FakeSocket pairing, control;
        volatile X509Certificate pin;
        volatile boolean pairClosedBeforeControl, controlFailure;
        FakeSocket pair() { pairing=new FakeSocket(certificate,other); sockets.add(pairing); return pairing; }
        FakeSocket control() { control=new FakeSocket(certificate,pairing==null?certificate:pairing.peer); sockets.add(control); return control; }
        public KeyStore.PrivateKeyEntry identity(boolean fresh) throws Exception {
            identities.incrementAndGet(); if(identityGate!=null)identityGate.await();
            PrivateKey key=new PrivateKey() {
                public String getAlgorithm(){return "RSA";} public String getFormat(){return null;} public byte[] getEncoded(){return null;}
            };
            return new KeyStore.PrivateKeyEntry(key,new Certificate[]{certificate});
        }
        public SSLSocket pairingSocket(KeyStore.PrivateKeyEntry identity) throws Exception {
            created.incrementAndGet(); if(socketGate!=null)socketGate.await(); return pairing;
        }
        public SSLSocket clientSocket(KeyStore.PrivateKeyEntry identity,X509Certificate peer) throws Exception {
            created.incrementAndGet(); pin=peer; pairClosedBeforeControl=pairing==null||pairing.isClosed();
            if(socketGate!=null)socketGate.await();
            if(controlFailure || !Arrays.equals(peer.getEncoded(),control.peer.getEncoded()))
                throw new SSLHandshakeException("Synthetic certificate mismatch");
            return control;
        }
    }

    private static final class FakeSocket extends SSLSocket {
        final X509Certificate local,peer;
        final AtomicInteger connects=new AtomicInteger(),handshakes=new AtomicInteger();
        final AtomicBoolean writeBlocked=new AtomicBoolean(), outputFailed=new AtomicBoolean();
        final AtomicInteger flushes=new AtomicInteger();
        volatile boolean failWrite, failFlush;
        final ArrayDeque<Integer> input=new ArrayDeque<>();
        final ByteArrayOutputStream output=new ByteArrayOutputStream();
        volatile boolean closed,blockConnect,blockHandshake,blockWrites,eof;
        final AtomicInteger inputWaits = new AtomicInteger();
        volatile int port;
        FakeSocket(X509Certificate local,X509Certificate peer){this.local=local;this.peer=peer;}
        public synchronized void connect(SocketAddress address,int timeout)throws IOException{
            connects.incrementAndGet();port=((InetSocketAddress)address).getPort();
            while(blockConnect&&!closed)pause();if(closed)throw new SocketException();
        }
        public synchronized void startHandshake()throws IOException{
            handshakes.incrementAndGet();while(blockHandshake&&!closed)pause();if(closed)throw new SocketException();
        }
        synchronized void pause()throws IOException{try{wait();}catch(InterruptedException e){throw new IOException(e);}}
        public synchronized void close(){closed=true;notifyAll();}
        public boolean isClosed(){return closed;}
        public void setSoTimeout(int timeout){}
        public InputStream getInputStream(){return new InputStream(){
            public int read()throws IOException{synchronized(FakeSocket.this){while(input.isEmpty()&&!closed&&!eof){inputWaits.incrementAndGet();pause();}
                if(closed)throw new SocketException();return input.isEmpty()?-1:input.remove();}}
            public int read(byte[] b,int off,int len)throws IOException{int first=read();if(first<0)return -1;b[off]=(byte)first;int n=1;synchronized(FakeSocket.this){while(n<len&&!input.isEmpty())b[off+n++]=(byte)(int)input.remove();}return n;}
        };}
        public OutputStream getOutputStream(){return new OutputStream(){
            public void write(int b)throws IOException{write(new byte[]{(byte)b},0,1);}
            public void write(byte[] b,int off,int len)throws IOException{synchronized(FakeSocket.this){
                while(blockWrites&&!closed){writeBlocked.set(true);pause();}if(closed)throw new SocketException();
                if(failWrite){outputFailed.set(!eof);throw new IOException("Synthetic output failure");}
                output.write(b,off,len);
            }}
            public void flush()throws IOException{synchronized(FakeSocket.this){
                if(closed)throw new SocketException();
                if(failFlush){outputFailed.set(!eof);throw new IOException("Synthetic flush failure");}
                flushes.incrementAndGet();
            }}
        };}
        synchronized void releaseWrites(){blockWrites=false;notifyAll();}
        synchronized void feed(byte[] frame)throws Exception{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();TvWire.write(bytes,frame);
            raw(bytes.toByteArray(),false);
        }
        synchronized void raw(byte[] bytes, boolean end) {
            for(byte b:bytes)input.add(b&255);eof=end;notifyAll();
        }
        void handshakeMessages()throws Exception{feed(TvControlProtocolTest.configure(579));feed(TvControlProtocolTest.active());feed(TvControlProtocolTest.start());}
        void pairingMessages()throws Exception{
            feed(polo().setPairingRequestAck(PairingRequestAck.getDefaultInstance()).build().toByteArray());
            feed(polo().setOptions(Options.getDefaultInstance()).build().toByteArray());
            feed(polo().setConfigurationAck(ConfigurationAck.getDefaultInstance()).build().toByteArray());
        }
        synchronized List<byte[]> messages(){
            List<byte[]> frames=new ArrayList<>();ByteArrayInputStream bytes=new ByteArrayInputStream(output.toByteArray());
            try{while(bytes.available()>0)frames.add(TvWire.read(bytes));}catch(IOException ignored){}return frames;
        }
        public SSLSession getSession(){return new SSLSession(){
            public Certificate[] getPeerCertificates(){return new Certificate[]{peer};}public Certificate[] getLocalCertificates(){return new Certificate[]{local};}
            public byte[] getId(){return new byte[0];}public SSLSessionContext getSessionContext(){return null;}
            public long getCreationTime(){return 0;}public long getLastAccessedTime(){return 0;}public void invalidate(){}public boolean isValid(){return true;}
            public void putValue(String n,Object v){}public Object getValue(String n){return null;}public void removeValue(String n){}public String[] getValueNames(){return new String[0];}
            @SuppressWarnings("removal") public javax.security.cert.X509Certificate[] getPeerCertificateChain(){return null;}
            public Principal getPeerPrincipal(){return peer.getSubjectX500Principal();}public Principal getLocalPrincipal(){return local.getSubjectX500Principal();}
            public String getCipherSuite(){return "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256";}public String getProtocol(){return "TLSv1.2";}public String getPeerHost(){return "10.0.0.2";}public int getPeerPort(){return port;}
            public int getPacketBufferSize(){return 65536;}public int getApplicationBufferSize(){return 65536;}
        };}
        public String[] getSupportedCipherSuites(){return new String[0];}public String[] getEnabledCipherSuites(){return new String[0];}public void setEnabledCipherSuites(String[] x){}
        public String[] getSupportedProtocols(){return new String[]{"TLSv1.2","TLSv1.3"};}public String[] getEnabledProtocols(){return getSupportedProtocols();}public void setEnabledProtocols(String[] x){}
        public void addHandshakeCompletedListener(HandshakeCompletedListener x){}public void removeHandshakeCompletedListener(HandshakeCompletedListener x){}
        public void setUseClientMode(boolean x){}public boolean getUseClientMode(){return true;}public void setNeedClientAuth(boolean x){}public boolean getNeedClientAuth(){return false;}
        public void setWantClientAuth(boolean x){}public boolean getWantClientAuth(){return false;}public void setEnableSessionCreation(boolean x){}public boolean getEnableSessionCreation(){return true;}
    }
    private static final String CA_DER="MIIDATCCAemgAwIBAgIJAICTzJVaFFhTMA0GCSqGSIb3DQEBCwUAMB0xGzAZBgNVBAMTElB1YmxpYyBUTFMgVGVzdCBDQTAgFw0yMDAxMDEwNTAwMDBaGA8yMTAwMDEwMTA1MDAwMFowHTEbMBkGA1UEAxMSUHVibGljIFRMUyBUZXN0IENBMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApPXCujvvdcnW6WE52ZmsXU7AucC1wgtshX3bjbUCdbyzMUM3x39vSid2B12U9G01X2VBrfuCHzgcDwpzfsyaeo6C0A5Az5TDYSG9HRXLRlBJM/ovMwSWZNzGB/4CPMQzeQUoN5QJoFwpI2sNnRr+72BG02yDuIQhS28Ln5kTXFVfJvMOPx+m5rNvFv8BoADUxB42/R05Gj8y8YLUsKtzweUesvyu+g8M8zCDvn65UYHoPcjIOCsz1W5E8qUQsk2IhU/pemsvZEQt/vIBLdd31RRCzgA9yErzYKnqUNqWv6/tzHaEVc9z9gsRHZWa6jNHjoKsIopqEgc84RHXvaKEMwIDAQABo0IwQDAdBgNVHQ4EFgQUj05MqCbwInxCJz1/wXzGpOCY7JMwDgYDVR0PAQH/BAQDAgIEMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEBAELReTAdgsXZMpc++KUgJUXfzz4jN9mP8Ww60m6wDY+0rbZO5j7CXvrlS3C1y9n+7i26hPk/I6LkfW9+OaSe8Ma4iA8JRCNa2Zlvt8ozmS8Fmt3g9s2WJE0ALoIgLZeDR1hKXCVIKcnQsHCCvmpGKb3eOM1F3bXS64Qz2RtJ3uZvHXR2p4TScsvpnh+EQDCKcWu9dUOU7HeXGBYZKRfsZ1nYkWv0ZMv0fwiasvha5YTMbet15VzAHupAAhqVPqFCDG66rVt1jJpWilv5BDjH4Imf9M0A0GxKtE3AnQ5Dm5puRC0fzv921dkx3DyoRtvWCV6QDs4PDWIQ7fRYBj2YXFo=";
    private static final String CHILD_DER="MIIDKTCCAhGgAwIBAgIJAM7W+HNRqqu7MA0GCSqGSIb3DQEBCwUAMB0xGzAZBgNVBAMTElB1YmxpYyBUTFMgVGVzdCBDQTAgFw0yMDAxMDEwNTAwMDBaGA8yMTAwMDEwMTA1MDAwMFowIDEeMBwGA1UEAxMVUHVibGljIFRMUyBUZXN0IENoaWxkMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAj8C2lateuWrKepD2FKXE330HM8kwC7KMn9goMcvNGIJUrlkQTklxE8KhnU5Kr2jaSPstKuolIw55iNNgr5rHmzuUPd2Y7EMk48LxF7WTlRiy3Rhxcf3qgSVCe5DxGNcF9tiSEyVJ8fuFYajH+3skWkplO4XjaDFvaacPoa9S5/6hlfi3zh5q64D3/wLhr3F9IqBn+E2a/10V92zCfnxfm+qrWleUvGwp+7GQr7SoE1mWBkSzADuboRAWgpRe7HqN22fHZZdnGQ4kAFk87X22s478NWBK7t2saC59eqfsk8Aykw7Em05MtchEVkfiSwARV1LHoe4C0LJkWqyRWRG8twIDAQABo2cwZTAdBgNVHQ4EFgQUp1+RXQ9k/lSyjnGhwuc5BIHss7gwDgYDVR0PAQH/BAQDAgWgMB8GA1UdIwQYMBaAFI9OTKgm8CJ8Qic9f8F8xqTgmOyTMBMGA1UdJQQMMAoGCCsGAQUFBwMBMA0GCSqGSIb3DQEBCwUAA4IBAQCJjMn6+yUFJ8sxQbP3HPMRx3Ui89GQlqSka8A+ufpcesbRDY2GdsHGW9/WkYI3+oNaNeFpnLGAvNzqQIIkjPqDwzQrRzWJ5jRbZn2GGanjWi1itWVK1ruZ4emf3RPgzUJUOlqT59/PLqAVGJhTm5iOFxcQqjX5vgDa72pWpvYzCkwEW+9PHvNHS0IkkvjAD3tidRyuabwXB32yq0WVyvTm09pHcw/bREmMHJWbDfOix14rSe2eiXAiRubiX3rEKt2n6iEhXRgtIxBtUKHPqs3vBnUydGFkEVfpni5gXxvEjPNpBv2M1UM9ud6Jov8iX0wNA0dJma9SP0mWq48e9TVK";
}
