package dev.opentvtouchpad.hidprobe;

import java.io.*;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
import dev.opentvtouchpad.hidprobe.wire.RemoteProto;

public class TvWireTest {
    @Test public void framedPayloadSurvivesFragmentedReads() throws Exception {
        byte[] payload=new byte[65536]; Arrays.fill(payload,(byte)71);
        ByteArrayOutputStream out=new ByteArrayOutputStream(); TvWire.write(out,payload);
        InputStream fragmented=new FilterInputStream(new ByteArrayInputStream(out.toByteArray())) {
            @Override public int read(byte[] b,int off,int n) throws IOException {return super.read(b,off,Math.min(n,3));}
        };
        assertArrayEquals(payload,TvWire.read(fragmented));
    }
    @Test public void rejectsBadPrefixSizeAndTruncation() {
        for(byte[] bytes:new byte[][]{{},{0},{(byte)0x81,0,5},{(byte)0x80,(byte)0x80,4},
                {(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff,127},{(byte)0x80,(byte)0x80,(byte)0x80,(byte)0x80,(byte)0x80,0},{3,1,2}})
            assertThrows(IOException.class,()->TvWire.read(new ByteArrayInputStream(bytes)));
        assertThrows(IOException.class,()->TvWire.write(new ByteArrayOutputStream(),new byte[65537]));
    }
    @Test public void parserRejectsMalformedAndRecursivePeerData() throws Exception {
        assertThrows(IOException.class,()->TvWire.parse(new byte[]{10,10,1},RemoteProto.RemoteMessage.parser()));
        RemoteProto.RemoteMessage value=RemoteProto.RemoteMessage.newBuilder().setRemoteStart(
            RemoteProto.RemoteStart.newBuilder().setStarted(true)).build();
        for(int i=0;i<20;i++)value=RemoteProto.RemoteMessage.newBuilder().setRemoteError(
            RemoteProto.RemoteError.newBuilder().setMessage(value)).build();
        byte[] raw=value.toByteArray();
        assertThrows(IOException.class,()->TvWire.parse(raw,RemoteProto.RemoteMessage.parser()));
    }
    @Test public void commandsHaveOnlyFixedShortKeysOrNetflixUri() {
        for(TvCommand c:TvCommand.values()) {
            var m=c.message();
            if(c==TvCommand.NETFLIX)assertEquals("nflx://www.netflix.com/browse",m.getRemoteAppLinkLaunchRequest().getAppLink());
            else {assertTrue(m.hasRemoteKeyInject());assertEquals(RemoteProto.RemoteDirection.SHORT,m.getRemoteKeyInject().getDirection());}
        }
        assertEquals(11,TvCommand.values().length);
        assertEquals(85,TvCommand.PLAY_PAUSE.message().getRemoteKeyInject().getKeyCodeValue());
        assertEquals(3,TvCommand.HOME.message().getRemoteKeyInject().getKeyCodeValue());
    }
}
