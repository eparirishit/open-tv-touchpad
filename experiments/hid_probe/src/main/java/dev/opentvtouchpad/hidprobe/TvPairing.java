package dev.opentvtouchpad.hidprobe;

import com.google.protobuf.ByteString;
import dev.opentvtouchpad.hidprobe.wire.PoloProto.*;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

/** Strict, single-use Polo transcript. Socket identity and deadlines belong to its owner. */
final class TvPairing {
    private enum State { NEW, REQUEST_ACK, OPTIONS, CONFIG_ACK, CODE, SECRET_ACK, DONE, FAILED }
    private State state = State.NEW;
    private static OuterMessage.Builder message() {
        return OuterMessage.newBuilder().setProtocolVersion(2).setStatus(OuterMessage.Status.STATUS_OK);
    }
    private static Options.Encoding encoding() {
        return Options.Encoding.newBuilder().setType(Options.Encoding.EncodingType.ENCODING_TYPE_HEXADECIMAL)
            .setSymbolLength(6).build();
    }
    byte[] begin() throws IOException {
        if (state != State.NEW) throw failure();
        state = State.REQUEST_ACK;
        return message().setPairingRequest(PairingRequest.newBuilder()
            .setServiceName("atvremote").setClientName("Open TV Touchpad")).build().toByteArray();
    }
    boolean needsCode() { return state == State.CODE; }
    boolean done() { return state == State.DONE; }

    byte[] receive(byte[] raw) throws IOException {
        try {
            OuterMessage received = TvWire.parse(raw, OuterMessage.parser());
            int phases = (received.hasPairingRequest()?1:0) + (received.hasPairingRequestAck()?1:0)
                + (received.hasOptions()?1:0) + (received.hasConfiguration()?1:0)
                + (received.hasConfigurationAck()?1:0) + (received.hasSecret()?1:0) + (received.hasSecretAck()?1:0);
            if (received.getProtocolVersion() != 2 || received.getStatus() != OuterMessage.Status.STATUS_OK || phases != 1)
                throw failure();
            switch (state) {
                case REQUEST_ACK:
                    if (!received.hasPairingRequestAck()) throw failure();
                    state = State.OPTIONS;
                    return message().setOptions(Options.newBuilder().setPreferredRole(Options.RoleType.ROLE_TYPE_INPUT)
                        .addInputEncodings(encoding())).build().toByteArray();
                case OPTIONS:
                    if (!received.hasOptions()) throw failure();
                    state = State.CONFIG_ACK;
                    return message().setConfiguration(Configuration.newBuilder().setEncoding(encoding())
                        .setClientRole(Options.RoleType.ROLE_TYPE_INPUT)).build().toByteArray();
                case CONFIG_ACK:
                    if (!received.hasConfigurationAck()) throw failure();
                    state = State.CODE;
                    return null;
                case SECRET_ACK:
                    if (!received.hasSecretAck()) throw failure();
                    state = State.DONE;
                    return null;
                default: throw failure();
            }
        } catch (IOException | RuntimeException error) {
            throw failure();
        }
    }

    byte[] code(String code, RSAPublicKey client, RSAPublicKey server) throws IOException {
        if (state != State.CODE || code == null || !code.matches("[0-9A-Fa-f]{6}") || client == null || server == null)
            throw failure();
        byte[] digest = null;
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(unsigned(client.getModulus())); hash.update(unsigned(client.getPublicExponent()));
            hash.update(unsigned(server.getModulus())); hash.update(unsigned(server.getPublicExponent()));
            hash.update((byte)Integer.parseInt(code.substring(2,4),16));
            hash.update((byte)Integer.parseInt(code.substring(4,6),16));
            digest = hash.digest();
            if ((digest[0] & 255) != Integer.parseInt(code.substring(0,2),16)) throw failure();
            state = State.SECRET_ACK;
            return message().setSecret(Secret.newBuilder().setSecret(ByteString.copyFrom(digest))).build().toByteArray();
        } catch (NoSuchAlgorithmException | RuntimeException error) {
            throw failure();
        } finally {
            if (digest != null) Arrays.fill(digest,(byte)0);
        }
    }
    private static byte[] unsigned(BigInteger number) {
        if (number.signum() <= 0) throw new IllegalArgumentException();
        byte[] bytes = number.toByteArray();
        return bytes[0] == 0 ? Arrays.copyOfRange(bytes,1,bytes.length) : bytes;
    }
    private IOException failure() {
        state = State.FAILED;
        return new IOException("TV pairing failed");
    }
}
