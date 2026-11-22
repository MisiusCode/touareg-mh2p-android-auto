package vwaa;

import org.dsi.ifc.komoview.DSIKOMOViewListener;

/** Records what the manoeuvre-picture service ("KOMO view") reports. Stores and logs only. */
final class KomoEvents implements DSIKOMOViewListener {
    private final String tag;
    volatile int lastResult = -1;
    volatile long results = 0L;
    volatile boolean visible = false;
    volatile boolean enabled = false;
    volatile int viewType = -1;
    /** counts every report of the service, of any kind */
    volatile long changes = 0L;

    KomoEvents(String tag) {
        this.tag = tag;
    }

    public void updateKomoViewEnabled(boolean on, int valid) {
        enabled = on;
        changes++;
        Log.log("KOMO " + tag + " enabled=" + on + " valid=" + valid);
    }

    public void updateVisibility(boolean on, int valid) {
        visible = on;
        changes++;
        Log.log("KOMO " + tag + " visible=" + on + " valid=" + valid);
    }

    public void komoViewResult(int result) {
        results++;
        changes++;
        if (result != lastResult || results <= 3) {
            Log.log("KOMO " + tag + " result=" + result + " (0 ok, 1 not operable, 2 error, 3 unsupported)");
        }
        lastResult = result;
    }

    public void updateCurrentKomoViewType(int type, int valid) {
        viewType = type;
        changes++;
        Log.log("KOMO " + tag + " viewType=" + type + " valid=" + valid + " (1 manoeuvre view, 2 route info)");
    }

    public void asyncException(int code, String text, int request) {
        changes++;
        Log.log("KOMO " + tag + " asyncException code=" + code + " request=" + request + " " + text);
    }

    String state() {
        return tag + "[visible=" + visible + " type=" + viewType + " enabled=" + enabled + " answers=" + results + "]";
    }
}
