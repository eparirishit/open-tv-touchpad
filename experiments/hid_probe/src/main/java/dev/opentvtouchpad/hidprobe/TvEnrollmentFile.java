package dev.opentvtouchpad.hidprobe;

import android.util.AtomicFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Separate app-private record; caller supplies getNoBackupFilesDir(). */
final class TvEnrollmentFile implements TvEnrollment.Disk {
    private final AtomicFile file;

    TvEnrollmentFile(File noBackupDirectory) {
        file = new AtomicFile(new File(noBackupDirectory, "tv-remote-enrollment"));
    }

    @Override public byte[] read() throws IOException {
        try (FileInputStream input = file.openRead()) {
            if (input.getChannel().size() > TvEnrollment.MAX_RECORD_BYTES)
                throw new IOException("TV enrollment too large");
            byte[] bytes = new byte[TvEnrollment.MAX_RECORD_BYTES];
            int count = 0;
            while (count < bytes.length) {
                int read = input.read(bytes, count, bytes.length - count);
                if (read < 0) break;
                count += read;
            }
            if (input.read() != -1) throw new IOException("TV enrollment too large");
            return Arrays.copyOf(bytes, count);
        } catch (FileNotFoundException failure) {
            // An unreadable base or recovery artifact is unknown, never verified absence.
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
        if (record == null || record.length > TvEnrollment.MAX_RECORD_BYTES)
            throw new IOException("Invalid TV enrollment size");
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
