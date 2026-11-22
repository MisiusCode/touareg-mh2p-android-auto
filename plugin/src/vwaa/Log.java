package vwaa;

import java.io.File;
import java.io.FileWriter;

/**
 * Logger with no HMI dependencies. Writes to RAM (/tmp/vwaa.log) and keeps a capped copy in
 * the unit's persistent log folder so a later SD-card run can collect it.
 */
public final class Log {
    static final String LOG = "/tmp/vwaa.log";
    static final String PERSIST_DIR = "/mnt/ota/system/logs";
    static final String PERSIST = PERSIST_DIR + "/vwaa.log";
    private static final long LOG_MAX = 300000L;
    private static final long PERSIST_MAX = 150000L;
    private static final StringBuffer pending = new StringBuffer();
    private static boolean persistBroken = false;

    private Log() {
    }

    public static synchronized void log(String msg) {
        StringBuffer sb = new StringBuffer();
        sb.append(System.currentTimeMillis()).append(' ').append(msg).append('\n');
        String line = sb.toString();
        append(LOG, line, LOG_MAX);
        if (pending.length() < 60000) {
            pending.append(line);
        }
    }

    /** Called from the bridge thread every ~20 s: move buffered lines to the persistent copy. */
    static synchronized void flushPersistent() {
        if (persistBroken || pending.length() == 0) {
            return;
        }
        try {
            File dir = new File(PERSIST_DIR);
            if (!dir.isDirectory()) {
                persistBroken = true;
                return;
            }
            File f = new File(PERSIST);
            // over the cap: start the file again (the newest lines are the useful ones)
            boolean append = !f.exists() || f.length() <= PERSIST_MAX;
            FileWriter w = new FileWriter(PERSIST, append);
            try {
                w.write(pending.toString());
            } finally {
                w.close();
            }
            pending.setLength(0);
        } catch (Throwable t) {
            persistBroken = true;
            append(LOG, System.currentTimeMillis() + " persistent log unavailable: " + t + "\n", LOG_MAX);
        }
    }

    private static void append(String path, String text, long max) {
        FileWriter w = null;
        try {
            File f = new File(path);
            boolean append = !f.exists() || f.length() <= max;
            w = new FileWriter(path, append);
            w.write(text);
        } catch (Throwable t) {
            // nothing we can do
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (Throwable t) {
                }
            }
        }
    }
}
