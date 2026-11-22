package vwaa;

import java.io.File;

/** Marker files. Checked in /tmp and in the root of any inserted USB stick or SD card. */
final class Flags {
    private static final String[] ROOTS = new String[] {
        "/tmp/", "/fs/usb0_0/", "/fs/usb1_0/", "/fs/sda0/", "/fs/sdb0/" };

    private Flags() {
    }

    static boolean onMedia(String name) {
        for (int i = 0; i < ROOTS.length; i++) {
            try {
                if (new File(ROOTS[i] + name).exists() || new File(ROOTS[i] + name + ".txt").exists()) {
                    return true;
                }
            } catch (Throwable t) {
            }
        }
        return false;
    }
}
