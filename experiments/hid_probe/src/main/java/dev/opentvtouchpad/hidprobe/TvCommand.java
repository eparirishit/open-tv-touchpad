package dev.opentvtouchpad.hidprobe;

import dev.opentvtouchpad.hidprobe.wire.RemoteProto.*;

/** The sole application command vocabulary; no arbitrary key, text or URI input. */
enum TvCommand {
    UP(19), DOWN(20), LEFT(21), RIGHT(22), OK(23), HOME(3), BACK(4),
    VOLUME_UP(24), VOLUME_DOWN(25), PLAY_PAUSE(85), NETFLIX(-1);
    private final int key;
    TvCommand(int key) { this.key = key; }
    RemoteMessage message() {
        RemoteMessage.Builder message = RemoteMessage.newBuilder();
        if (this == NETFLIX) message.setRemoteAppLinkLaunchRequest(
            RemoteAppLinkLaunchRequest.newBuilder().setAppLink("nflx://www.netflix.com/browse"));
        else message.setRemoteKeyInject(RemoteKeyInject.newBuilder()
            .setKeyCodeValue(key).setDirection(RemoteDirection.SHORT));
        return message.build();
    }
}
