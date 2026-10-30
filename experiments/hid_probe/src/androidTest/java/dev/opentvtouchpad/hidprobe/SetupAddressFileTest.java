package dev.opentvtouchpad.hidprobe;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Isolated app-private fixtures; never opens real enrollment or draft files. */
@RunWith(AndroidJUnit4.class)
public class SetupAddressFileTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder(
        InstrumentationRegistry.getInstrumentation().getTargetContext().getNoBackupFilesDir());

    @Test public void atomicSaveReopenAndReplaceLeaveCredentialFilesByteIdentical() throws Exception {
        File directory = temporary.newFolder();
        File bluetooth = new File(directory, "projector-enrollment");
        File tv = new File(directory, "tv-remote-enrollment");
        byte[] bluetoothBytes = {2, 3, 4}, tvBytes = {5, 6, 7};
        Files.write(bluetooth.toPath(), bluetoothBytes);
        Files.write(tv.toPath(), tvBytes);
        SetupAddressFile file = new SetupAddressFile(directory);
        assertArrayEquals(new byte[0], file.read());
        assertFalse(new File(directory, "setup-address").exists());
        SetupAddressStore store = new SetupAddressStore(file);
        assertTrue(store.save("192.168.255.255"));
        assertArrayEquals(bytes("1\n192.168.255.255"), file.read());
        assertTrue(store.save("10.0.0.1"));
        assertEquals("10.0.0.1", new SetupAddressStore(new SetupAddressFile(directory)).address());
        assertArrayEquals(bytes("1\n10.0.0.1"), Files.readAllBytes(new File(directory, "setup-address").toPath()));
        assertArrayEquals(bluetoothBytes, Files.readAllBytes(bluetooth.toPath()));
        assertArrayEquals(tvBytes, Files.readAllBytes(tv.toPath()));
        String[] entries = directory.list();
        assertNotNull(entries);
        Arrays.sort(entries);
        assertArrayEquals(new String[]{"projector-enrollment", "setup-address", "tv-remote-enrollment"}, entries);
    }

    @Test public void oversizedReadIsBoundedAndCannotBeSelectedAsAnAddress() throws Exception {
        File directory = temporary.newFolder();
        File base = new File(directory, "setup-address");
        byte[] oversized = new byte[4096];
        Arrays.fill(oversized, (byte) 'x');
        System.arraycopy(bytes("1\n192.168.255.255"), 0, oversized, 0, 17);
        Files.write(base.toPath(), oversized);
        SetupAddressFile file = new SetupAddressFile(directory);
        assertEquals(18, file.read().length);
        SetupAddressStore store = new SetupAddressStore(file);
        assertNull(store.address());
        assertTrue(store.failed());
        assertEquals(4096, base.length());
        assertTrue(store.save("10.0.0.1"));
        assertFalse(store.failed());
        assertEquals(10, base.length());
    }

    @Test public void atomicBackupRecoveryUsesCommittedRecordInsteadOfPartialReplacement() throws Exception {
        File directory = temporary.newFolder();
        Files.write(new File(directory, "setup-address").toPath(), bytes("partial"));
        Files.write(new File(directory, "setup-address.bak").toPath(), bytes("1\n10.0.0.1"));
        SetupAddressStore store = new SetupAddressStore(new SetupAddressFile(directory));
        assertEquals("10.0.0.1", store.address());
        assertFalse(store.failed());
    }

    @Test public void unreadablePathAndUncommittedArtifactAreNotVerifiedAbsence() throws Exception {
        File notDirectory = temporary.newFile();
        SetupAddressFile file = new SetupAddressFile(notDirectory);
        assertThrows(IOException.class, file::read);
        SetupAddressStore store = new SetupAddressStore(file);
        assertTrue(store.failed());
        assertFalse(store.save("10.0.0.1"));
        assertNull(store.address());

        File directory = temporary.newFolder();
        Files.write(new File(directory, "setup-address.new").toPath(), bytes("partial"));
        SetupAddressStore interrupted = new SetupAddressStore(new SetupAddressFile(directory));
        assertTrue(interrupted.failed());
        assertNull(interrupted.address());
        assertTrue(interrupted.save("10.0.0.1"));
        assertFalse(interrupted.failed());
    }

    @Test public void nullOrOversizedWritesCannotReplaceCommittedDraft() throws Exception {
        SetupAddressFile file = new SetupAddressFile(temporary.newFolder());
        byte[] saved = bytes("1\n10.0.0.1");
        file.write(saved);
        assertThrows(IOException.class, () -> file.write(null));
        assertThrows(IOException.class, () -> file.write(new byte[18]));
        assertArrayEquals(saved, file.read());
    }

    private static byte[] bytes(String record) { return record.getBytes(StandardCharsets.US_ASCII); }
}
