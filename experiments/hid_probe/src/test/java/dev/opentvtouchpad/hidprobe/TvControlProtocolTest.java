package dev.opentvtouchpad.hidprobe;

import dev.opentvtouchpad.hidprobe.wire.RemoteProto.*;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class TvControlProtocolTest {
    @Test public void negotiatesOnlySupportedFeaturesAndWaitsForOrderedStart() throws Exception {
        TvControlProtocol protocol = new TvControlProtocol();
        RemoteMessage reply = parse(protocol.receive(configure(1023)));
        assertEquals(611, reply.getRemoteConfigure().getCode1());
        assertFalse(protocol.ready());
        reply = parse(protocol.receive(active()));
        assertEquals(611, reply.getRemoteSetActive().getActive());
        assertFalse(protocol.ready());
        assertNull(protocol.receive(start()));
        assertTrue(protocol.ready());
    }

    @Test public void missingKeysOrAppLinkAndOutOfOrderMessagesFail() throws Exception {
        for (int features : new int[]{0, 2, 512})
            assertThrows(IOException.class, () -> new TvControlProtocol().receive(configure(features)));
        assertThrows(IOException.class, () -> new TvControlProtocol().receive(start()));
        assertThrows(IOException.class, () -> new TvControlProtocol().receive(active()));
        TvControlProtocol protocol = new TvControlProtocol();
        protocol.receive(configure(579));
        assertThrows(IOException.class, () -> protocol.receive(configure(579)));
    }

    @Test public void pingAndBoundedInfoDoNotAuthorizeOrCreateInput() throws Exception {
        TvControlProtocol protocol = new TvControlProtocol();
        RemoteMessage pong = parse(protocol.receive(ping(37)));
        assertEquals(37, pong.getRemotePingResponse().getVal1());
        assertFalse(protocol.ready());
        protocol.receive(configure(579));
        assertNull(protocol.receive(info()));
        assertFalse(protocol.ready());
        protocol.receive(active()); protocol.receive(start());
        assertNull(protocol.receive(start())); // Power information is not a fresh session.
        assertTrue(protocol.ready());
    }

    @Test public void malformedEmptyMixedAndUnrequestedOperationsFail() {
        byte[][] invalid = {new byte[]{1}, new byte[]{(byte)0x98, 6, 1},
            RemoteMessage.newBuilder().setRemoteError(RemoteError.getDefaultInstance()).build().toByteArray(),
            TvCommand.HOME.message().toByteArray(),
            RemoteMessage.newBuilder().setRemoteVoiceBegin(RemoteVoiceBegin.getDefaultInstance()).build().toByteArray(),
            RemoteMessage.newBuilder().setRemoteConfigure(RemoteConfigure.newBuilder().setCode1(579))
                .setRemotePingRequest(RemotePingRequest.getDefaultInstance()).build().toByteArray()};
        for (byte[] message : invalid)
            assertThrows(IOException.class, () -> new TvControlProtocol().receive(message));
    }

    static byte[] configure(int features) {
        return RemoteMessage.newBuilder().setRemoteConfigure(RemoteConfigure.newBuilder().setCode1(features)).build().toByteArray();
    }
    static byte[] active() {
        return RemoteMessage.newBuilder().setRemoteSetActive(RemoteSetActive.newBuilder().setActive(1)).build().toByteArray();
    }
    static byte[] start() {
        return RemoteMessage.newBuilder().setRemoteStart(RemoteStart.newBuilder().setStarted(true)).build().toByteArray();
    }
    static byte[] ping(int value) {
        return RemoteMessage.newBuilder().setRemotePingRequest(RemotePingRequest.newBuilder().setVal1(value)).build().toByteArray();
    }
    static byte[] info() {
        return RemoteMessage.newBuilder().setRemoteSetVolumeLevel(RemoteSetVolumeLevel.getDefaultInstance()).build().toByteArray();
    }
    static RemoteMessage parse(byte[] bytes) throws Exception { return TvWire.parse(bytes, RemoteMessage.parser()); }
}
