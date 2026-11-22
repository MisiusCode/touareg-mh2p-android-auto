package vwaa;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;

/**
 * Self-disable. A counter file is increased before the plugin touches any HMI class and removed
 * after the unit has run cleanly for a while. Three starts in a row that never reached the clean
 * point leave the plugin switched off until the counter is removed (re-run the install card, or
 * put a file named vwaa_reset on a USB stick / SD card).
 */
final class BootGuard {
    static final String MARK = "/mnt/ota/system/logs/vwaa_boot";
    static final int LIMIT = 3;
    private static boolean armed = false;

    private BootGuard() {
    }

    /** @return true when the plugin may run */
    static boolean enter() {
        int n = read();
        if (Flags.onMedia("vwaa_reset")) {
            Log.log("bootguard: vwaa_reset found on media, counter cleared (was " + n + ")");
            new File(MARK).delete();
            n = 0;
        }
        if (n >= LIMIT) {
            Log.log("bootguard: " + n + " unclean starts in a row - plugin stays OFF this boot");
            return false;
        }
        try {
            FileWriter w = new FileWriter(MARK, false);
            try {
                w.write(String.valueOf(n + 1));
            } finally {
                w.close();
            }
            armed = true;
            Log.log("bootguard: start " + (n + 1) + " of " + LIMIT + " armed");
        } catch (Throwable t) {
            Log.log("bootguard: cannot write counter (" + t + ") - running without self-disable");
        }
        return true;
    }

    static void clean() {
        if (!armed) {
            return;
        }
        armed = false;
        try {
            new File(MARK).delete();
            Log.log("bootguard: clean run, counter removed");
        } catch (Throwable t) {
            Log.log("bootguard: cannot remove counter: " + t);
        }
    }

    private static int read() {
        FileInputStream in = null;
        try {
            File f = new File(MARK);
            if (!f.exists()) {
                return 0;
            }
            in = new FileInputStream(f);
            byte[] b = new byte[4];
            int n = in.read(b);
            if (n >= 1 && b[0] >= '0' && b[0] <= '9') {
                return b[0] - '0';
            }
            return 0;
        } catch (Throwable t) {
            return 0;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable t) {
                }
            }
        }
    }
}
