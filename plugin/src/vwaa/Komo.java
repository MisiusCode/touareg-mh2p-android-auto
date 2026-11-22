package vwaa;

import de.vw.mib.asl.framework.api.dsiproxy.DSIProxy;
import de.vw.mib.asl.framework.api.dsiproxy.DSIProxyFactory;
import org.dsi.ifc.komogfxstreamsink.DSIKOMOGfxStreamSink;
import org.dsi.ifc.komonavinfo.DSIKOMONavInfo;
import org.dsi.ifc.komoview.DSIKOMOView;
import org.dsi.ifc.komoview.DSIKOMOViewListener;
import org.dsi.ifc.komoview.ManeuverElement;
import org.dsi.ifc.komoview.RouteInfoElement;
import org.dsi.ifc.komoview.TrafficInfo;

/**
 * Experimental. On this car the HUD arrow is a picture: the head unit draws a manoeuvre view
 * ("KOMO view", instance 1 = HUD, 0 = cluster) and streams it. Recorded on 2026-11-22: with
 * factory guidance the HUD view reports visible=true and view type 2 / 1; with Android Auto
 * guidance it stays invisible and ignores a route info element sent to it (0.4).
 *
 * 0.5 tries the remaining switches of that service one after the other while Android Auto
 * guidance runs, and logs for each whether the service reacted. If the HUD view becomes
 * visible the sequence stops where it is.
 *
 * Markers: vwaa_nokomo  listen and log only, send nothing
 *          vwaa_noprobe send the plain element only (as 0.4)
 */
final class Komo {
    private static final int[] ATTRS = new int[] { 1, 2, 3 };
    private static final String[] NAMES = new String[] { "cluster", "hud" };
    private static final int STOCK_STYLE = 5;
    private static final int[] STYLES = new int[] { 1, 2, 3, 4, 6 };

    private static final long BASELINE_MS = 30000L;
    private static final long STEP_MS = 20000L;
    private static final long STYLE_MS = 10000L;
    private static final long PAUSE_MS = 60000L;

    private static final int S_BASE = 0;
    private static final int S_LIST = 1;
    private static final int S_VISIBLE = 2;
    private static final int S_REENABLE = 3;
    private static final int S_STYLE = 4;          // 4 .. 4 + STYLES.length - 1
    private static final int S_RGI = S_STYLE + 5;
    private static final int S_PAUSE = S_RGI + 1;

    private final DSIKOMOView[] view = new DSIKOMOView[2];
    private final DSIKOMOGfxStreamSink[] sink = new DSIKOMOGfxStreamSink[2];
    private DSIKOMONavInfo navInfo = null;
    private final KomoEvents[] ev = new KomoEvents[] { new KomoEvents("cluster"), new KomoEvents("hud") };
    private final GfxEvents[] gfx = new GfxEvents[] { new GfxEvents("cluster"), new GfxEvents("hud") };
    private long lastTry = 0L;
    private int tries = 0;
    private boolean ready = false;

    private String lastKey = "";
    private long lastSend = 0L;
    private long sent = 0L;
    private boolean cleared = true;
    private boolean failedLogged = false;

    private int step = S_BASE;
    private long stepSince = 0L;
    private long stepChanges = 0L;
    private int cycle = 0;
    private boolean styleChanged = false;
    private boolean rgiDone = false;
    private boolean hit = false;
    private String reacted = "";

    void connect(long now) {
        if (ready || tries >= 20 || now - lastTry < 15000L) {
            return;
        }
        lastTry = now;
        tries++;
        try {
            DSIProxy p = DSIProxyFactory.getDSIProxyAPI().getDSIProxy();
            int ok = 0;
            for (int i = 0; i < 2; i++) {
                if (view[i] == null) {
                    try {
                        DSIKOMOView v = (DSIKOMOView) p.getService(null, DSIKOMOView.class, i);
                        if (v != null) {
                            try {
                                p.addResponseListener(null, DSIKOMOViewListener.class, i, ev[i]);
                            } catch (Throwable t) {
                                Log.log("KOMO " + NAMES[i] + " response listener: " + t);
                            }
                            v.setNotification(ATTRS, ev[i]);
                            view[i] = v;
                            Log.log("KOMO " + NAMES[i] + " service connected");
                        }
                    } catch (Throwable t) {
                        Log.log("KOMO " + NAMES[i] + " connect: " + t);
                    }
                }
                if (sink[i] == null) {
                    try {
                        DSIKOMOGfxStreamSink s = (DSIKOMOGfxStreamSink) p.getService(null, DSIKOMOGfxStreamSink.class, i);
                        if (s != null) {
                            s.setNotification(ATTRS, gfx[i]);
                            sink[i] = s;
                            Log.log("GFX " + NAMES[i] + " stream service connected");
                        }
                    } catch (Throwable t) {
                        Log.log("GFX " + NAMES[i] + " connect: " + t);
                    }
                }
                if (view[i] != null && sink[i] != null) {
                    ok++;
                }
            }
            if (navInfo == null) {
                try {
                    navInfo = (DSIKOMONavInfo) p.getService(null, DSIKOMONavInfo.class);
                    if (navInfo != null) {
                        Log.log("KOMO nav info service connected");
                    }
                } catch (Throwable t) {
                    Log.log("KOMO nav info connect: " + t);
                }
            }
            ready = ok == 2 && navInfo != null;
        } catch (Throwable t) {
            Log.log("KOMO connect: " + t);
        }
    }

    /** called on every loop while Android Auto guidance is live */
    void show(long now, boolean sendAllowed, boolean probeAllowed, int main, int dir, String street, int dist) {
        if (!sendAllowed) {
            return;
        }
        if (cleared) {
            cleared = false;
            step = S_BASE;
            stepSince = now;
            stepChanges = totalChanges();
            hit = false;
            Log.log("PROBE session start, plain element first | " + states());
        }
        if (probeAllowed && !hit) {
            advance(now);
        }
        String shown = distanceText(dist);
        String key = main + "/" + dir + "/" + street + "/" + shown;
        boolean changed = !key.equals(lastKey);
        if (!changed && now - lastSend < 2000L) {
            return;
        }
        lastKey = key;
        lastSend = now;
        RouteInfoElement e = blank();
        e.routeInfoElementType = 3;                      // manoeuvre
        e.maneuverDescriptor = new ManeuverElement[] { new ManeuverElement(main, (short) dir, 0) };
        e.turnToStreet = street;
        if (dist >= 0) {
            e.distance = dist;
            e.distanceToElement = shown;
            e.distanceToManeuver = shown;
            e.distanceUnit = dist >= 1000 ? "km" : "m";
        }
        boolean list = step == S_LIST || step == S_VISIBLE;
        for (int i = 0; i < 2; i++) {
            if (view[i] == null) {
                continue;
            }
            try {
                view[i].setRouteInfoElement(e);
                if (list) {
                    view[i].setRouteInfo(new RouteInfoElement[] { e });
                }
                sent++;
            } catch (Throwable t) {
                logFail(i, t);
            }
        }
    }

    private void advance(long now) {
        if (ev[1].visible && !hit) {
            hit = true;
            Log.log("PROBE HIT: HUD view became visible during step " + name(step) + " - staying here | " + states());
            return;
        }
        long len = step == S_BASE ? BASELINE_MS : step == S_PAUSE ? PAUSE_MS
            : (step >= S_STYLE && step < S_RGI) ? STYLE_MS : STEP_MS;
        if (now - stepSince < len) {
            return;
        }
        long c = totalChanges();
        boolean r = c != stepChanges;
        if (r) {
            reacted = reacted + " " + name(step);
        }
        Log.log("PROBE " + name(step) + " end: " + (r ? "SERVICE REACTED" : "no reaction") + " | " + states());
        int next = step + 1;
        if (step == S_BASE || step == S_PAUSE) {
            next = S_LIST;
            cycle++;
            if (cycle > 3) {
                next = S_PAUSE;
            }
        }
        if (next == S_RGI && rgiDone) {
            next = S_PAUSE;
        }
        if (step >= S_STYLE && step < S_RGI && next >= S_RGI) {
            restoreStyle();
        }
        step = next;
        stepSince = now;
        stepChanges = totalChanges();
        if (step != S_PAUSE) {
            Log.log("PROBE " + name(step) + " start (round " + cycle + ")");
        }
        try {
            enter(step);
        } catch (Throwable t) {
            Log.log("PROBE " + name(step) + " failed: " + t);
        }
        stepChanges = totalChanges();
    }

    private void enter(int s) {
        if (s == S_VISIBLE) {
            for (int i = 0; i < 2; i++) {
                if (view[i] != null) {
                    view[i].notifyVisibility(true);
                }
            }
        } else if (s == S_REENABLE) {
            for (int i = 0; i < 2; i++) {
                if (view[i] != null) {
                    view[i].enableKomoView(false);
                    view[i].enableKomoView(true);
                }
            }
        } else if (s >= S_STYLE && s < S_RGI) {
            if (view[1] != null) {
                styleChanged = true;
                view[1].setKomoViewStyle(STYLES[s - S_STYLE]);
            }
        } else if (s == S_RGI) {
            rgiDone = true;
            if (navInfo != null) {
                navInfo.setRgSelect(0);
            }
        }
    }

    private void restoreStyle() {
        if (!styleChanged) {
            return;
        }
        styleChanged = false;
        try {
            if (view[1] != null) {
                view[1].setKomoViewStyle(STOCK_STYLE);
                Log.log("PROBE HUD view style back to " + STOCK_STYLE);
            }
        } catch (Throwable t) {
            Log.log("PROBE style restore failed: " + t);
        }
    }

    private static String name(int s) {
        if (s == S_BASE) {
            return "1-plain-element";
        }
        if (s == S_LIST) {
            return "2-list-form";
        }
        if (s == S_VISIBLE) {
            return "3-declare-visible";
        }
        if (s == S_REENABLE) {
            return "4-off-and-on";
        }
        if (s >= S_STYLE && s < S_RGI) {
            return "5-style-" + STYLES[s - S_STYLE];
        }
        if (s == S_RGI) {
            return "6-arrow-mode";
        }
        return "pause";
    }

    private long totalChanges() {
        return ev[0].changes + ev[1].changes;
    }

    private String states() {
        return ev[1].state() + " " + ev[0].state() + " sent=" + sent;
    }

    void clear() {
        if (cleared) {
            return;
        }
        cleared = true;
        lastKey = "";
        if (!hit) {
            restoreStyle();
        }
        RouteInfoElement e = blank();
        for (int i = 0; i < 2; i++) {
            if (view[i] != null) {
                try {
                    view[i].setRouteInfoElement(e);
                } catch (Throwable t) {
                    logFail(i, t);
                }
            }
        }
        Log.log("PROBE session end: " + (hit ? "HIT in " + name(step) : "no hit") + ", reactions in:"
            + (reacted.length() == 0 ? " none" : reacted) + " | " + states());
    }

    private void logFail(int i, Throwable t) {
        if (!failedLogged) {
            failedLogged = true;
            Log.log("KOMO " + NAMES[i] + " send failed: " + t);
        }
    }

    private static RouteInfoElement blank() {
        RouteInfoElement e = new RouteInfoElement();
        e.distanceToElement = "";
        e.distance = 0;
        e.distanceFormat = 0;
        e.estimatedTimeToElement = "";
        e.etaHours = 0;
        e.etaMinutes = 0;
        e.etaTimeFormat = 2;                             // not available
        e.routeInfoElementType = 0;
        e.elementIconIDs = new int[0];
        e.prio1EventText = "";
        e.streetIconText = "";
        e.streetIconID = 0;
        e.exitNumber = "";
        e.turnToStreet = "";
        e.pOIElementNames = new String[0];
        e.maneuverDescriptor = new ManeuverElement[0];
        e.trafficInfo = new TrafficInfo("", "", false);
        e.trafficDelayMinutes = 0;
        e.trafficDelayText = "";
        e.trafficDelayFormat = 0;
        e.trafficIcon = 0;
        e.destinationIndex = 0;
        e.signPostInfo = "";
        e.distanceToManeuver = "";
        e.estimatedTimeToManeuver = "";
        e.streetCardinalDirection = "";
        e.exitIconId = 0;
        e.decimalMark = ",";
        e.timeDelimiter = ":";
        e.hourUnit = "h";
        e.minutesUnit = "min";
        e.clockUnit = "";
        e.distanceUnit = "m";
        e.distancePrefix = "";
        e.offroadText = "";
        return e;
    }

    /** distance as the stock display rounds it: 50 m steps below 1 km, one decimal up to 10 km */
    static String distanceText(int m) {
        if (m < 0) {
            return "";
        }
        if (m < 1000) {
            int r = m < 100 ? (m / 10) * 10 : (m / 50) * 50;
            return String.valueOf(r);
        }
        if (m < 10000) {
            int tenths = m / 100;
            return (tenths / 10) + "," + (tenths % 10);
        }
        return String.valueOf(m / 1000);
    }

    void shutdown() {
        try {
            clear();
        } catch (Throwable t) {
        }
        restoreStyle();
        for (int i = 0; i < 2; i++) {
            try {
                if (view[i] != null) {
                    view[i].clearNotification(ATTRS, ev[i]);
                }
            } catch (Throwable t) {
            }
            try {
                if (sink[i] != null) {
                    sink[i].clearNotification(ATTRS, gfx[i]);
                }
            } catch (Throwable t) {
            }
        }
    }
}
