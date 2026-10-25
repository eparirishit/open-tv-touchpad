package dev.opentvtouchpad.hidprobe;

import android.util.AtomicFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Native atomic file in the Activity's app-private getNoBackupFilesDir(). */
final class AtomicEnrollmentFile implements EnrollmentStore.Disk {
    private final AtomicFile file;

    AtomicEnrollmentFile(File noBackupDirectory) {
        file = new AtomicFile(new File(noBackupDirectory, "projector-enrollment"));
    }

    @Override public byte[] read() throws IOException {
        try (FileInputStream input = file.openRead()) {
            byte[] bytes = new byte[20]; // One extra byte rejects trailing/oversized data.
            int count = 0;
            while (count < bytes.length) {
                int read = input.read(bytes, count, bytes.length - count);
                if (read < 0) break;
                count += read;
            }
            return Arrays.copyOf(bytes, count);
        } catch (FileNotFoundException error) {
            if (file.getBaseFile().exists() || !file.getBaseFile().getParentFile().canRead()) throw error;
            return new byte[0];
        }
    }

    @Override public void write(byte[] record) throws IOException {
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(record);
            output.getFD().sync();
            file.finishWrite(output);
        } catch (IOException | RuntimeException error) {
            file.failWrite(output);
            throw error;
        }
    }
}
