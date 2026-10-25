package dev.opentvtouchpad.hidprobe;

import dev.opentvtouchpad.hidprobe.wire.RemoteProto.*;
import java.io.IOException;

/** One ordered Remote v2 negotiation; peer metadata never leaves this parser. */
final class TvControlProtocol {
    // Include upstream's power-status feature while waiting for RemoteStart; no power input is exposed.
    private static final int SUPPORTED = 1 | 2 | 32 | 64 | 512;
    private static final int REQUIRED = 2 | 512;
    private int phase, features;

    boolean ready() { return phase == 3; }

    byte[] receive(byte[] bytes) throws IOException {
        RemoteMessage message = TvWire.parse(bytes, RemoteMessage.parser());
        boolean[] fields = {message.hasRemoteConfigure(), message.hasRemoteSetActive(), message.hasRemoteError(),
            message.hasRemotePingRequest(), message.hasRemotePingResponse(), message.hasRemoteKeyInject(),
            message.hasRemoteImeKeyInject(), message.hasRemoteImeBatchEdit(), message.hasRemoteImeShowRequest(),
            message.hasRemoteVoiceBegin(), message.hasRemoteVoicePayload(), message.hasRemoteVoiceEnd(),
            message.hasRemoteStart(), message.hasRemoteSetVolumeLevel(), message.hasRemoteAdjustVolumeLevel(),
            message.hasRemoteSetPreferredAudioDevice(), message.hasRemoteResetPreferredAudioDevice(),
            message.hasRemoteAppLinkLaunchRequest()};
        int count = 0;
        for (boolean present : fields) if (present) count++;
        if (count != 1) throw invalid();
        RemoteMessage.Builder reply = RemoteMessage.newBuilder();
        if (message.hasRemotePingRequest()) {
            return reply.setRemotePingResponse(RemotePingResponse.newBuilder()
                .setVal1(message.getRemotePingRequest().getVal1())).build().toByteArray();
        }
        if (message.hasRemoteConfigure()) {
            if (phase != 0) throw invalid();
            features = message.getRemoteConfigure().getCode1() & SUPPORTED;
            if ((features & REQUIRED) != REQUIRED) throw invalid();
            phase = 1;
            return reply.setRemoteConfigure(RemoteConfigure.newBuilder().setCode1(features)
                .setDeviceInfo(RemoteDeviceInfo.newBuilder().setUnknown1(1).setUnknown2("1")
                    .setPackageName("atvremote").setAppVersion("1.0.0"))).build().toByteArray();
        }
        if (message.hasRemoteSetActive()) {
            if (phase != 1) throw invalid();
            phase = 2;
            return reply.setRemoteSetActive(RemoteSetActive.newBuilder().setActive(features)).build().toByteArray();
        }
        if (message.hasRemoteStart()) {
            if (phase < 2) throw invalid();
            phase = 3; // started is power status, not authorization to skip negotiation.
            return null;
        }
        if (phase != 0 && (message.hasRemoteSetVolumeLevel() || message.hasRemoteImeKeyInject()
                || message.hasRemoteImeBatchEdit() || message.hasRemoteImeShowRequest()
                || message.hasRemoteAdjustVolumeLevel() || message.hasRemoteSetPreferredAudioDevice()
                || message.hasRemoteResetPreferredAudioDevice())) return null;
        throw invalid();
    }

    private IOException invalid() { return new IOException("Invalid TV control exchange"); }
}
