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
    static final String PERSIST2 = PERSIST_DIR + "/vwaa_2.log";
    private static final long PERSIST_MAX = 200000L;
    /** 0.6: two files used in turn, so a full file never wipes the only copy */
    private static int cur = -1;
    private static final StringBuffer pending = new StringBuffer();
    private static boolean persistBroken = false;

    private Log() {
    }

    /** 0.7.1: part B recordings and start lines also go to a file that is never wiped; it just
     *  stops growing at KEEP_MAX. Collected by the card like the other vwaa* files. */
    static final String KEEP = PERSIST_DIR + "/vwaa_keep.log";
    private static final long KEEP_MAX = 400000L;
    private static final StringBuffer keepPending = new StringBuffer();
    private static boolean keepFull = false;

    private static boolean keepWorthy(String m) {
        return m.startsWith("DISP") || m.startsWith("KOMO") || m.startsWith("GFX") || m.startsWith("PROBE")
            || m.startsWith("VWAndroidAutoNav") || m.startsWith("ACTIVATE") || m.startsWith("DEACTIVATE");
    }

    public static synchronized void log(String msg) {
        StringBuffer sb = new StringBuffer();
        sb.append(System.currentTimeMillis()).append(' ').append(msg).append('\n');
        String line = sb.toString();
        append(LOG, line, LOG_MAX);
        if (pending.length() < 60000) {
            pending.append(line);
        }
        if (!keepFull && msg != null && keepWorthy(msg) && keepPending.length() < 60000) {
            keepPending.append(line);
        }
    }

    /** Called from the bridge thread every ~20 s: move buffered lines to the persistent copy. */
    static synchronized void flushPersistent() {
        flushKeep();
        flushMain();
    }

    private static void flushKeep() {
        if (keepFull || persistBroken || keepPending.length() == 0) {
            return;
        }
        try {
            File f = new File(KEEP);
            if (f.exists() && f.length() > KEEP_MAX) {
                keepFull = true;
                keepPending.setLength(0);
                return;
            }
            FileWriter w = new FileWriter(KEEP, true);
            try {
                w.write(keepPending.toString());
            } finally {
                w.close();
            }
            keepPending.setLength(0);
        } catch (Throwable t) {
            keepFull = true;
        }
    }

    private static void flushMain() {
        if (persistBroken || pending.length() == 0) {
            return;
        }
        try {
            File dir = new File(PERSIST_DIR);
            if (!dir.isDirectory()) {
                persistBroken = true;
                return;
            }
            String[] names = new String[] { PERSIST, PERSIST2 };
            if (cur < 0) {
                File a = new File(PERSIST);
                File b = new File(PERSIST2);
                cur = (a.exists() && a.length() > PERSIST_MAX && b.exists() && b.length() <= PERSIST_MAX) ? 1 : 0;
            }
            boolean append = true;
            File f = new File(names[cur]);
            if (f.exists() && f.length() > PERSIST_MAX) {
                // full: continue in the other file, starting it afresh
                cur = 1 - cur;
                append = false;
            }
            FileWriter w = new FileWriter(names[cur], append);
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
