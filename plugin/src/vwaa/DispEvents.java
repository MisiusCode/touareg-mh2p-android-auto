package vwaa;

import org.dsi.ifc.displaymanagement.DSIDisplayManagementListener;

/**
 * 0.7, preparation for Part B. Listens to the display manager's answers and logs which layers
 * ("displayables") each display shows and which context is active. Sends nothing.
 * Displays: 0 centre, 4 cluster, 5 HUD.
 */
final class DispEvents implements DSIDisplayManagementListener {
    private final String[] last = new String[16];
    private int lines = 0;

    private void log(int display, String kind, String text) {
        int k = (display >= 0 && display < 8) ? display * 2 + ("ctx".equals(kind) ? 1 : 0) : -1;
        if (k >= 0) {
            if (text.equals(last[k])) {
                return;
            }
            last[k] = text;
        }
        if (lines < 2000) {
            lines++;
            Log.log("DISP d" + display + " " + kind + " " + text);
        }
    }

    public void showDisplayablesResult(int display, int[] ids, int a, int b) {
        StringBuffer sb = new StringBuffer("[");
        for (int i = 0; ids != null && i < ids.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ids[i]);
        }
        log(display, "layers", sb.append("] ").append(a).append('/').append(b).toString());
    }

    public void activeContext(int display, int context, int c) {
        log(display, "ctx", context + "/" + c);
    }

    public void getDisplayableInfo(int a, int b, int c) {
        log(-1, "info", a + "/" + b + "/" + c);
    }

    public void startComponentResult(int a, int b, int c, int d) {
        log(-1, "startComponent", a + "/" + b + "/" + c + "/" + d);
    }

    public void stopComponentResult(int a, int b, int c, int d) {
        log(-1, "stopComponent", a + "/" + b + "/" + c + "/" + d);
    }

    public void getDisplaySizeResult(int a, int b, int c, int d) {
        log(-1, "size", a + "/" + b + "/" + c + "/" + d);
    }

    public void getAvailableDisplaysResult(int[] ids) {
        showDisplayablesResult(-1, ids, 0, 0);
    }

    public void getExtents(int a, int b, int c) { }
    public void fadeStarted(int a, int b) { }
    public void fadeComplete(int a, int b) { }
    public void getDisplayPower(int a, int b) { }
    public void getDisplayBrightness(int a, int b) { }
    public void getBrightness(int a, int b) { }
    public void getContrast(int a, int b) { }
    public void getColor(int a, int b) { }
    public void getTint(int a, int b) { }
    public void lockDisplayResult(int a) { }
    public void unlockDisplayResult(int a) { }
    public void setCroppingResult(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j, int k) { }
    public void takeScreenshotOnExternalStorageResult(int a, int b, String s) { }
    public void setDisplayTypeResult(int a, int b) { }
    public void getDisplayTypeResult(int a, int b) { }
    public void setUpdateRateResult(int a, int b) { }
    public void getUpdateRateResult(int a, int b) { }
    public void setAnnotationDataResponse(int a, int b) { }
    public void initAnnotationsResponse(int a, int b) { }
    public void destroyImageDisplayableResponse(int a, int b) { }
    public void requestUpdateImageDisplayableResponse(int a, int b) { }
    public void createImageDisplayableResponse(int a, int b) { }
    public void dumpDisplayableResult(int a, int b) { }
    public void deferUpdatesResult(int a, int b, int c) { }

    public void asyncException(int code, String text, int request) {
        Log.log("DISP asyncException code=" + code + " request=" + request + " " + text);
    }
}
