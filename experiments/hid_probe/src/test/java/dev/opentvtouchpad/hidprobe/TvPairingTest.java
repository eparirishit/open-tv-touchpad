package dev.opentvtouchpad.hidprobe;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import org.junit.Test;
import static org.junit.Assert.*;
import dev.opentvtouchpad.hidprobe.wire.PoloProto.*;
import com.google.protobuf.ByteString;

public class TvPairingTest {
    private static OuterMessage.Builder ok(){return OuterMessage.newBuilder().setProtocolVersion(2).setStatus(OuterMessage.Status.STATUS_OK);}
    private static TvPairing ready() throws Exception {
        TvPairing p=new TvPairing();assertTrue(OuterMessage.parseFrom(p.begin()).hasPairingRequest());
        assertTrue(OuterMessage.parseFrom(p.receive(ok().setPairingRequestAck(PairingRequestAck.getDefaultInstance()).build().toByteArray())).hasOptions());
        assertTrue(OuterMessage.parseFrom(p.receive(ok().setOptions(Options.getDefaultInstance()).build().toByteArray())).hasConfiguration());
        assertNull(p.receive(ok().setConfigurationAck(ConfigurationAck.getDefaultInstance()).build().toByteArray()));
        assertTrue(p.needsCode());return p;
    }
    private static RSAPublicKey key(String first,String middle,String last) throws Exception {
        return (RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
            new BigInteger(first+middle.repeat(254)+last,16),BigInteger.valueOf(65537)));
    }
    @Test public void transcriptUsesUnsignedRsaAndExactCodeBinding() throws Exception {
        TvPairing p=ready();byte[] bytes=p.code("02ABCD",key("80","11","01"),key("90","22","03"));
        assertEquals("0298ac5e638fcb38a02428d67bcff8a08ffb9746b5c20a99b13683791417060a",java.util.HexFormat.of().formatHex(OuterMessage.parseFrom(bytes).getSecret().getSecret().toByteArray()));
        assertFalse(p.done());p.receive(ok().setSecretAck(SecretAck.newBuilder().setSecret(ByteString.EMPTY)).build().toByteArray());assertTrue(p.done());
        assertThrows(IOException.class,()->p.receive(ok().setSecretAck(SecretAck.newBuilder().setSecret(ByteString.EMPTY)).build().toByteArray()));
    }
    @Test public void badCodeAndUnexpectedMessageTerminatePairing() throws Exception {
        for(String code:new String[]{"12345","1234567"," Z1234","ZZZZZZ","00ABCD"}){
            TvPairing p=ready();assertThrows(IOException.class,()->p.code(code,key("80","11","01"),key("90","22","03")));
            assertFalse(p.done());assertFalse(p.needsCode());
        }
        TvPairing p=ready();assertThrows(IOException.class,()->p.receive(ok().setPairingRequestAck(PairingRequestAck.getDefaultInstance()).build().toByteArray()));assertFalse(p.needsCode());
    }
    @Test public void wrongVersionErrorAndMixedPhasesRejected() throws Exception {
        for(OuterMessage m:new OuterMessage[]{ok().setProtocolVersion(1).setPairingRequestAck(PairingRequestAck.getDefaultInstance()).build(),
            ok().setStatus(OuterMessage.Status.STATUS_BAD_SECRET).build(),
            ok().setPairingRequestAck(PairingRequestAck.getDefaultInstance()).setOptions(Options.getDefaultInstance()).build()}){
            TvPairing p=new TvPairing();p.begin();assertThrows(IOException.class,()->p.receive(m.toByteArray()));
        }
    }
}
