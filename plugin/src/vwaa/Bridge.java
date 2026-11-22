package vwaa;

import de.vw.mib.asl.api.navbap.ASLNavBAPAPI;
import de.vw.mib.asl.api.navbap.ASLNavBAPFactory;
import de.vw.mib.asl.framework.api.dsiproxy.DSIProxy;
import de.vw.mib.asl.framework.api.dsiproxy.DSIProxyFactory;
import de.vw.mib.asl.framework.internal.framework.GenericCollector;
import de.vw.mib.asl.framework.internal.framework.ServiceManager;
import de.vw.mib.bap.mqbab2.common.api.APIFactory;
import de.vw.mib.bap.mqbab2.common.api.androidauto.AndroidAutoService;
import generated.de.vw.mib.asl.internal.ListManager;
import java.lang.reflect.Field;
import org.dsi.ifc.androidauto2.DSIAndroidAuto2;

/**
 * Android Auto turn-by-turn -> instrument cluster and head-up display, VW Touareg MH2p (EU, P2869).
 *
 * The cluster side (which HMI values to write, and how) follows the VWClusterNav plugin of
 * a78c7 / plafp "touareg-cn-carplay-mods" (GPL-3.0). The values are the stock navigation's own:
 *   732  route guidance state        738  current position (not used here)
 *   741  distance to next manoeuvre  744  turn-to street
 *   751  manoeuvre list (cluster/HUD service)   838  manoeuvre list (map manoeuvre view)
 * The stock cluster service then sends arrow, distance and street to the cluster and HUD itself.
 *
 * Marker files (in /tmp or the root of a USB stick / SD card, with or without ".txt"):
 *   vwaa_off     do nothing, pass everything through as stock
 *   vwaa_nohide  keep the stock "Navigation on mobile device is active" message
 *   vwaa_no838   do not write list 838
 *   vwaa_reset   clear the self-disable counter
 *   vwaa_nosnoop do not record what the cluster service sees
 *   vwaa_nokomo  do not offer the manoeuvre to the HUD picture service (experiment), only listen
 *   vwaa_noprobe offer it in the plain form only, do not try the other switches (0.5)
 *   vwaa_plain   write the junction symbol at every distance, without side streets (as in 0.1/0.2)
 */
final class Bridge implements Runnable {
    private static final int DP_RG_STATE = 732;
    private static final int LIST_MANEUVER = 751;
    private static final int LIST_MANEUVER2 = 838;
    private static final int[] AA_ATTRS = new int[] { 7, 8 };   // next turn event, next turn distance

    private static final long START_DELAY_MS = 45000L;
    private static final long CLEAN_AFTER_MS = 90000L;
    private static final long POLL_MS = 300L;
    private static final long HEARTBEAT_MS = 2000L;
    private static final long FLAG_CHECK_MS = 2000L;
    private static final long FLUSH_MS = 20000L;
    private static final long RETRY_MS = 10000L;
    private static final long RECENT_MS = 90000L;
    private static final long ARRIVED_SHOW_MS = 8000L;

    /** read by QuietAndroidAuto on the HMI's own threads */
    static volatile boolean hideMessage = false;

    private static Thread thread = null;
    private static volatile boolean stop = false;

    private final AaEvents events = new AaEvents();
    private final Komo komo = new Komo();
    private boolean noKomo = false;
    private boolean noProbe = false;
    private DSIAndroidAuto2 aa = null;
    private boolean subscribed = false;
    private QuietAndroidAuto wrapper = null;
    private Field serviceField = null;
    private long wrapperSince = 0L;
    private boolean wrapperGaveUp = false;

    private boolean linkOk = false;
    private boolean disabled = false;
    private boolean noHide = false;
    private boolean no838 = false;
    private long lastFlagCheck = 0L;
    private long lastRetry = 0L;
    private long lastFlush = 0L;
    private long lastErrLog = 0L;
    private long lastSnoop = 0L;
    private boolean noSnoop = false;
    private boolean plain = false;
    private boolean prevStockRg = false;
    private long rgSince = 0L;
    private long lastResub = 0L;
    private String lastSide = null;

    private boolean active = false;
    private int notLive = 0;
    private long lastHeartbeat = 0L;
    private long lastTurnSeq = 0L;
    private boolean haveManeuver = false;
    private boolean ended = true;
    private boolean destSeen = false;
    private long arrivedAt = 0L;
    private long pendingSince = 0L;
    private String pendingStreet = "";
    private int curMain = Mapper.FOLLOW_STREET;
    private int curDir = 0;
    private String curStreet = "";
    private int lastMain = -999;
    private int lastDir = -999;
    private String lastStreet = null;
    private int lastDist = -999;
    private int lastBarOn = -1;
    private int lastBar = -1;
    private String lastStateLine = "";

    static synchronized void startThread() {
        if (thread != null && thread.isAlive()) {
            return;
        }
        stop = false;
        thread = new Thread(new Bridge(), "VWAndroidAutoNav");
        thread.setDaemon(true);
        try {
            thread.setPriority(4);
        } catch (Throwable t) {
        }
        thread.start();
    }

    static synchronized void stopThread() {
        stop = true;
        if (thread != null) {
            thread.interrupt();
        }
        thread = null;
    }

    public void run() {
        Log.log("thread running, waiting " + (START_DELAY_MS / 1000) + "s for the HMI");
        try {
            Thread.sleep(START_DELAY_MS);
        } catch (InterruptedException e) {
            return;
        }
        long started = System.currentTimeMillis();
        boolean cleaned = false;
        try {
            if (Flags.onMedia("vwaa_off")) {
                Log.log("vwaa_off present at start");
            }
            if (!BootGuard.enter()) {
                Log.flushPersistent();
                return;
            }
            Log.flushPersistent();
            linkCheck();
            Log.flushPersistent();
        } catch (Throwable t) {
            Log.log("startup error: " + t);
        }
        while (!stop) {
            long now = System.currentTimeMillis();
            try {
                if (linkOk) {
                    loopOnce(now);
                }
            } catch (Throwable t) {
                if (now - lastErrLog > 10000L) {
                    lastErrLog = now;
                    Log.log("loop error: " + t);
                }
            }
            if (!cleaned && now - started > CLEAN_AFTER_MS) {
                cleaned = true;
                BootGuard.clean();
            }
            if (now - lastFlush > FLUSH_MS) {
                lastFlush = now;
                Log.flushPersistent();
            }
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                break;
            }
        }
        shutdown();
    }

    /** Touch every HMI class once, so a linkage problem shows in the log and leaves the plugin idle. */
    private void linkCheck() {
        try {
            boolean pm = ServiceManager.aslPropertyManager != null;
            boolean nb = ASLNavBAPFactory.getNavBAPApi() != null;
            boolean l1 = ListManager.getGenericASLList(LIST_MANEUVER) != null;
            boolean l2 = ListManager.getGenericASLList(LIST_MANEUVER2) != null;
            boolean px = DSIProxyFactory.getDSIProxyAPI() != null;
            Log.log("link check: propertyManager=" + pm + " navbap=" + nb + " list751=" + l1
                + " list838=" + l2 + " dsiProxy=" + px);
            linkOk = pm && nb && l1 && px;
            if (!l2) {
                no838 = true;
            }
            if (!linkOk) {
                Log.log("link check failed - plugin stays idle");
            }
        } catch (Throwable t) {
            Log.log("link check error - plugin stays idle: " + t);
            linkOk = false;
        }
    }

    private void checkFlags(long now) {
        if (now - lastFlagCheck < FLAG_CHECK_MS) {
            return;
        }
        lastFlagCheck = now;
        boolean off = Flags.onMedia("vwaa_off");
        if (off != disabled) {
            disabled = off;
            Log.log(off ? "vwaa_off found: passing everything through as stock" : "vwaa_off gone: active again");
        }
        noHide = Flags.onMedia("vwaa_nohide");
        noSnoop = Flags.onMedia("vwaa_nosnoop");
        plain = Flags.onMedia("vwaa_plain");
        noKomo = Flags.onMedia("vwaa_nokomo");
        noProbe = Flags.onMedia("vwaa_noprobe");
        if (Flags.onMedia("vwaa_no838")) {
            no838 = true;
        }
    }

    private void subscribe(long now) {
        if (subscribed || now - lastRetry < RETRY_MS) {
            return;
        }
        lastRetry = now;
        try {
            DSIProxy p = DSIProxyFactory.getDSIProxyAPI().getDSIProxy();
            if (aa == null) {
                aa = (DSIAndroidAuto2) p.getService(null, DSIAndroidAuto2.class);
            }
            if (aa == null) {
                Log.log("subscribe: Android Auto service not available yet");
                return;
            }
            aa.setNotification(AA_ATTRS, events);
            subscribed = true;
            Log.log("subscribed to Android Auto next-turn event and distance");
        } catch (Throwable t) {
            Log.log("subscribe failed: " + t);
        }
    }

    /**
     * A subscription made before the phone is connected was seen to deliver nothing once
     * guidance started (2026-11-22). So subscribe again when the stock side reports guidance,
     * and keep trying while no event has arrived.
     */
    private void resubscribe(long now, String why) {
        lastResub = now;
        try {
            DSIProxy p = DSIProxyFactory.getDSIProxyAPI().getDSIProxy();
            DSIAndroidAuto2 fresh = (DSIAndroidAuto2) p.getService(null, DSIAndroidAuto2.class);
            if (fresh == null) {
                Log.log("resubscribe (" + why + "): service not available");
                return;
            }
            try {
                if (aa != null) {
                    aa.clearNotification(AA_ATTRS, events);
                }
            } catch (Throwable t) {
            }
            aa = fresh;
            aa.setNotification(AA_ATTRS, events);
            subscribed = true;
            Log.log("resubscribed (" + why + ")");
        } catch (Throwable t) {
            Log.log("resubscribe failed (" + why + "): " + t);
        }
    }

    private void installWrapper(long now) {
        if (wrapper != null || wrapperGaveUp) {
            return;
        }
        if (wrapperSince == 0L) {
            wrapperSince = now;
        }
        try {
            if (serviceField == null) {
                serviceField = APIFactory.class.getDeclaredField("androidAutoService");
                serviceField.setAccessible(true);
            }
            Object cur = serviceField.get(null);
            if (cur == null && now - wrapperSince > 60000L) {
                cur = APIFactory.getAPIFactory().getAndroidAutoService();   // creates it if stock has not yet
                cur = serviceField.get(null);
            }
            if (cur instanceof QuietAndroidAuto) {
                wrapper = (QuietAndroidAuto) cur;
                Log.log("wrapper: already installed");
            } else if (cur instanceof AndroidAutoService) {
                QuietAndroidAuto w = new QuietAndroidAuto((AndroidAutoService) cur);
                serviceField.set(null, w);
                wrapper = w;
                Log.log("wrapper: installed around " + cur.getClass().getName());
            } else if (now - wrapperSince > 180000L) {
                wrapperGaveUp = true;
                Log.log("wrapper: service never created, not installed");
            }
        } catch (Throwable t) {
            wrapperGaveUp = true;
            Log.log("wrapper error, not installed: " + t);
        }
    }

    private void loopOnce(long now) {
        checkFlags(now);
        if (!noSnoop && now - lastSnoop >= 1000L) {
            lastSnoop = now;
            Snoop.sample(active);
        }
        if (disabled) {
            hideMessage = false;
            if (active) {
                deactivate("vwaa_off");
            }
            return;
        }
        subscribe(now);
        installWrapper(now);
        try {
            komo.connect(now);
        } catch (Throwable t) {
        }
        hideMessage = subscribed && wrapper != null && !noHide;

        String[] roadOut = new String[1];
        long[] times = new long[2];
        long[] s = events.snapshot(roadOut, times);
        long turnSeq = s[0];
        int side = (int) s[1];
        int event = (int) s[2];
        int angle = (int) s[3];
        int num = (int) s[4];
        int turnValid = (int) s[5];
        int dist = (int) s[7];
        int focus = (int) s[9];

        if (turnSeq != lastTurnSeq) {
            lastTurnSeq = turnSeq;
            if (turnValid == 1) {
                if (event == 19) {
                    destSeen = true;
                }
                String street = Streets.clean(roadOut[0]);
                if ((event == 1 || event == 2) && haveManeuver && !ended) {
                    // Google Maps sends a "depart" before every real instruction: hold it back,
                    // and only show it if nothing else arrives within a second
                    pendingSince = now;
                    pendingStreet = street;
                } else {
                    pendingSince = 0L;
                    int[] m = Mapper.map(event, side, angle, num);
                    curMain = m[0];
                    curDir = m[1];
                    curStreet = street;
                    haveManeuver = true;
                    ended = false;
                    arrivedAt = 0L;
                }
            } else if (turnValid == 2 && event == 0) {
                if (destSeen && haveManeuver) {
                    curMain = Mapper.ARRIVED;
                    curDir = 0;
                    curStreet = "";
                    arrivedAt = now;
                    Log.log("arrival: showing destination symbol");
                } else {
                    ended = true;
                }
                destSeen = false;
                pendingSince = 0L;
                Mapper.reset();
            }
        }
        if (pendingSince != 0L && now - pendingSince > 1000L) {
            pendingSince = 0L;
            curMain = Mapper.FOLLOW_STREET;
            curDir = 0;
            curStreet = pendingStreet;
        }
        if (arrivedAt != 0L && now - arrivedAt > ARRIVED_SHOW_MS) {
            arrivedAt = 0L;
            ended = true;
        }

        boolean stockRg = false;
        try {
            stockRg = wrapper != null && wrapper.stockActive();
        } catch (Throwable t) {
        }
        long lastEvent = times[0] > times[1] ? times[0] : times[1];
        if (stockRg && !prevStockRg) {
            rgSince = now;
            if (now - lastEvent > 5000L) {
                resubscribe(now, "guidance started");
            }
        }
        prevStockRg = stockRg;
        if (stockRg && lastEvent < rgSince && now - lastResub > 15000L) {
            resubscribe(now, "no events yet");
        }
        boolean recent = now - lastEvent < RECENT_MS;
        boolean session = stockRg || focus == 2 || recent;
        boolean live = session && haveManeuver && !ended;

        String stateLine = "state stockRg=" + stockRg + " focus=" + focus + " recent=" + recent
            + " haveManeuver=" + haveManeuver + " ended=" + ended + " live=" + live;
        if (!stateLine.equals(lastStateLine)) {
            lastStateLine = stateLine;
            Log.log(stateLine);
        }

        if (!live) {
            if (active && ++notLive >= 5) {
                deactivate("guidance ended");
            }
            if (ended) {
                haveManeuver = false;
            }
            return;
        }
        notLive = 0;

        boolean beat = now - lastHeartbeat >= HEARTBEAT_MS;
        if (!active) {
            activate();
            beat = true;
        }
        int showMain = plain ? curMain : Mapper.atDistance(curMain, dist);
        String arms = plain ? "" : Mapper.sideStreets(showMain, curDir);
        if (beat || showMain != lastMain || curDir != lastDir || !arms.equals(lastSide)) {
            pushManeuver(showMain, curDir, arms);
        }
        if (beat || !curStreet.equals(lastStreet)) {
            pushTurnTo(curStreet);
        }
        if (dist >= 0 && curMain != Mapper.ARRIVED) {
            int barOn = 0;
            int bar = 0;
            if (dist > 0 && dist <= 300) {
                barOn = 1;
                bar = (300 - dist) * 100 / 300;
                if (bar < 0) {
                    bar = 0;
                }
                if (bar > 100) {
                    bar = 100;
                }
            }
            if (beat || dist != lastDist || barOn != lastBarOn || bar != lastBar) {
                pushDistance(dist, barOn, bar);
            }
        }
        if (beat) {
            ServiceManager.aslPropertyManager.valueChangedInteger(DP_RG_STATE, 1);
            lastHeartbeat = now;
        }
        try {
            komo.show(now, !noKomo, !noProbe, showMain, curDir, curStreet, curMain == Mapper.ARRIVED ? -1 : dist);
        } catch (Throwable t) {
        }
    }

    private void activate() {
        Log.log("ACTIVATE: routeGuidanceState=1");
        ServiceManager.aslPropertyManager.valueChangedInteger(DP_RG_STATE, 1);
        active = true;
        lastMain = -999;
        lastDir = -999;
        lastDist = -999;
        lastBarOn = -1;
        lastBar = -1;
        lastStreet = null;
        lastSide = null;
    }

    private void deactivate(String why) {
        Log.log("DEACTIVATE (" + why + ")");
        active = false;
        try {
            komo.clear();
        } catch (Throwable t) {
        }
        try {
            ListManager.getGenericASLList(LIST_MANEUVER).updateList(new Object[0]);
            if (!no838) {
                ListManager.getGenericASLList(LIST_MANEUVER2).updateList(new Object[0]);
            }
        } catch (Throwable t) {
            Log.log("clear maneuver: " + t);
        }
        try {
            ASLNavBAPAPI api = ASLNavBAPFactory.getNavBAPApi();
            api.updateBapDistanceToNextManeuver(0, 0, false, 0);
            api.updateBapTurnToInfo("", "");
        } catch (Throwable t) {
            Log.log("clear navbap: " + t);
        }
        try {
            ServiceManager.aslPropertyManager.valueChangedInteger(DP_RG_STATE, 0);
        } catch (Throwable t) {
            Log.log("clear 732: " + t);
        }
        lastMain = -999;
        lastDir = -999;
        lastDist = -999;
        lastStreet = null;
        notLive = 0;
        Mapper.reset();
    }

    private void pushManeuver(int main, int dir, String side) {
        GenericCollector gc = new GenericCollector();
        gc.setIntItem(0, main);
        gc.setIntItem(1, dir);
        gc.setIntItem(2, 0);
        gc.setStringItem(3, side);
        Object[] items = new GenericCollector[] { gc };
        ListManager.getGenericASLList(LIST_MANEUVER).updateList(items);
        if (!no838) {
            ListManager.getGenericASLList(LIST_MANEUVER2).updateList(items);
        }
        if (main != lastMain || dir != lastDir || !side.equals(lastSide)) {
            Log.log("maneuver main=" + main + " dir=" + dir + " sideArms=" + side.length());
        }
        lastMain = main;
        lastDir = dir;
        lastSide = side;
    }

    private void pushTurnTo(String street) {
        ASLNavBAPFactory.getNavBAPApi().updateBapTurnToInfo(street, "");
        if (!street.equals(lastStreet)) {
            Log.log("turnTo street=\"" + street + "\"");
        }
        lastStreet = street;
    }

    private void pushDistance(int dist, int barOn, int bar) {
        ASLNavBAPFactory.getNavBAPApi().updateBapDistanceToNextManeuver(dist, 0, barOn == 1, bar);
        lastDist = dist;
        lastBarOn = barOn;
        lastBar = bar;
    }

    private void shutdown() {
        hideMessage = false;
        try {
            if (active) {
                deactivate("thread stop");
            }
        } catch (Throwable t) {
        }
        try {
            if (subscribed && aa != null) {
                aa.clearNotification(AA_ATTRS, events);
            }
        } catch (Throwable t) {
        }
        try {
            if (wrapper != null && serviceField != null && serviceField.get(null) == wrapper) {
                serviceField.set(null, wrapper.d);
            }
        } catch (Throwable t) {
        }
        try {
            komo.shutdown();
        } catch (Throwable t) {
        }
        Log.log("thread stopped");
        Log.flushPersistent();
    }
}
