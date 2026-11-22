package vwaa;

import org.dsi.ifc.komogfxstreamsink.DSIKOMOGfxStreamSinkListener;

/** Records what the picture stream to the HUD / cluster reports. Logs only. */
final class GfxEvents implements DSIKOMOGfxStreamSinkListener {
    private final String tag;

    GfxEvents(String tag) {
        this.tag = tag;
    }

    public void updateGfxState(int state, int valid) {
        Log.log("GFX " + tag + " state=" + state + " valid=" + valid + " (1 available)");
    }

    public void updateRequestSync(int sync, int valid) {
        Log.log("GFX " + tag + " requestSync=" + sync + " valid=" + valid);
    }

    public void updateDataRate(int rate, int valid) {
        Log.log("GFX " + tag + " dataRate=" + rate + " valid=" + valid + " (0 off, 1 reduced, 2 full)");
    }

    public void setFGLayerResult(int r) {
    }

    public void fadeInResult() {
    }

    public void fadeOutResult() {
    }

    public void asyncException(int code, String text, int request) {
        Log.log("GFX " + tag + " asyncException code=" + code + " request=" + request + " " + text);
    }
}
