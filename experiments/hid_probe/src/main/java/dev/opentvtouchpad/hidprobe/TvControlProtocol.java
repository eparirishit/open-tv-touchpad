package dev.opentvtouchpad.hidprobe;

import dev.opentvtouchpad.hidprobe.wire.RemoteProto.*;
import java.io.IOException;

/** One ordered Remote v2 negotiation; peer metadata never leaves this parser. */
final class TvControlProtocol {
    // Include upstream's power-status feature while waiting for RemoteStart; no power input is exposed.
    private static final int SUPPORTED = 1 | 2 | 32 | 64 | 512;
    private static final int IME = 4;
    private static final int REQUIRED = 2 | 512;
    private final boolean keyboard;
    private int phase, features, appCounter;
    private boolean appCounterReported;
    private ImeField imeField;

    TvControlProtocol(boolean keyboard) { this.keyboard = keyboard; }

    boolean ready() { return phase == 3; }
    boolean imeSupported() { return (features & IME) != 0; }
    ImeField imeField() { return ready() ? imeField : null; }

    byte[] receive(byte[] bytes) throws IOException {
        RemoteMessage message = TvWire.parse(bytes, RemoteMessage.parser());
        boolean[] fields = {message.hasRemoteConfigure(), message.hasRemoteSetActive(), message.hasRemoteError(),
            message.hasRemotePingRequest(), message.hasRemotePingResponse(), message.hasRemoteKeyInject(),
            message.hasRemoteImeKeyInject(), message.hasRemoteImeBatchEdit(), message.hasRemoteImeShowRequest(),
            message.hasRemoteImeSelection(), message.hasRemoteImeReset(), message.hasRemoteImeCompletions(),
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
            features = message.getRemoteConfigure().getCode1() & (SUPPORTED | (keyboard ? IME : 0));
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
            if (!message.getRemoteStart().getStarted()) imeField = null;
            return null;
        }
        if (phase >= 2 && imeSupported()) {
            if (message.hasRemoteImeReset()) { imeField = null; return null; }
            if (message.hasRemoteImeSelection() || message.hasRemoteImeCompletions()) return null;
            updateImeField(message);
        }
        if (phase != 0 && (message.hasRemoteSetVolumeLevel() || message.hasRemoteImeKeyInject()
                || message.hasRemoteImeBatchEdit() || message.hasRemoteImeShowRequest()
                || message.hasRemoteAdjustVolumeLevel() || message.hasRemoteSetPreferredAudioDevice()
                || message.hasRemoteResetPreferredAudioDevice())) return null;
        throw invalid();
    }

    // Peer labels and field contents stay confined to the parsed message.
    private void updateImeField(RemoteMessage message) {
        if (message.hasRemoteImeKeyInject()) {
            RemoteImeKeyInject update = message.getRemoteImeKeyInject();
            appCounter = update.getAppInfo().getCounter();
            appCounterReported = appCounter != 0;
            if (update.hasTextFieldStatus()) useField(update.getTextFieldStatus().getCounterField());
            else imeField = null;
        } else if (message.hasRemoteImeShowRequest()) {
            RemoteImeShowRequest update = message.getRemoteImeShowRequest();
            if (update.hasRemoteTextFieldStatus()) useField(update.getRemoteTextFieldStatus().getCounterField());
            else imeField = null;
        } else if (message.hasRemoteImeBatchEdit() && imeField != null && !appCounterReported) {
            RemoteImeBatchEdit update = message.getRemoteImeBatchEdit();
            if (update.getFieldCounter() == imeField.fieldCounter) {
                appCounter = update.getImeCounter();
                useField(imeField.fieldCounter);
            }
        }
    }

    private void useField(int fieldCounter) {
        if (imeField == null || imeField.fieldCounter != fieldCounter || imeField.appCounter != appCounter)
            imeField = new ImeField(appCounter, fieldCounter);
    }

    /** Identity also distinguishes a field that closed and reopened with the same counters. */
    static final class ImeField {
        static final int MAX_TEXT_LENGTH = 256;
        private final int appCounter, fieldCounter;

        ImeField(int appCounter, int fieldCounter) {
            this.appCounter = appCounter;
            this.fieldCounter = fieldCounter;
        }

        static boolean accepts(String text) {
            return text != null && !text.isEmpty() && text.length() <= MAX_TEXT_LENGTH
                && text.codePoints().noneMatch(point -> Character.isISOControl(point)
                    || (point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE)
                    || point == 0x2028 || point == 0x2029);
        }

        byte[] insert(String text) {
            RemoteImeObject selection = RemoteImeObject.newBuilder()
                .setStart(text.length()).setEnd(text.length()).setValue(text).build();
            return RemoteMessage.newBuilder().setRemoteImeBatchEdit(RemoteImeBatchEdit.newBuilder()
                .setImeCounter(appCounter).setFieldCounter(fieldCounter)
                .addEditInfo(RemoteEditInfo.newBuilder().setInsert(1).setTextFieldStatus(selection)))
                .build().toByteArray();
        }
    }

    private IOException invalid() { return new IOException("Invalid TV control exchange"); }
}
