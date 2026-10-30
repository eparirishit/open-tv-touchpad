package dev.opentvtouchpad.hidprobe;

import android.util.AtomicFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Locator draft only; caller supplies the app-private getNoBackupFilesDir(). */
final class SetupAddressFile implements EnrollmentStore.Disk {
    private final AtomicFile file;

    SetupAddressFile(File noBackupDirectory) {
        file = new AtomicFile(new File(noBackupDirectory, "setup-address"));
    }

    @Override public byte[] read() throws IOException {
        try (FileInputStream input = file.openRead()) {
            byte[] bytes = new byte[18]; // One extra byte rejects trailing/oversized data.
            int count = 0;
            while (count < bytes.length) {
                int read = input.read(bytes, count, bytes.length - count);
                if (read < 0) break;
                count += read;
            }
            return Arrays.copyOf(bytes, count);
        } catch (FileNotFoundException failure) {
            File base = file.getBaseFile();
            String[] entries = base.getParentFile().list();
            if (entries == null) throw failure;
            for (String entry : entries)
                if (entry.equals(base.getName()) || entry.equals(base.getName() + ".bak")
                        || entry.equals(base.getName() + ".new")) throw failure;
            return new byte[0];
        }
    }

    @Override public void write(byte[] record) throws IOException {
        if (record == null || record.length > 17) throw new IOException("Invalid setup address size");
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(record);
            output.getFD().sync();
            file.finishWrite(output);
        } catch (IOException | RuntimeException failure) {
            if (output != null) file.failWrite(output);
            throw failure;
        }
    }
}
