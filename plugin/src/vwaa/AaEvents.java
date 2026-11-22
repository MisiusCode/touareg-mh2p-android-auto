package vwaa;

import org.dsi.ifc.androidauto2.DSIAndroidAuto2Listener;
import org.dsi.ifc.androidauto2.PlaybackInfo;
import org.dsi.ifc.androidauto2.TrackData;
import org.dsi.ifc.global.ResourceLocator;

/**
 * Receives the Android Auto service notifications. Only the two navigation attributes are
 * subscribed; every callback just stores what it got. Nothing here touches the HMI, so a
 * callback can never block or break the service thread that delivers it.
 */
final class AaEvents implements DSIAndroidAuto2Listener {
    private final Object lock = new Object();

    private String road = "";
    private int side;
    private int event;
    private int angle;
    private int num;
    private int turnValid = -1;
    private long turnSeq;
    private long turnTime;

    private int dist = -1;
    private int distTime = -1;
    private long distSeq;
    private long distStamp;

    private int focus = -1;
    private long focusSeq;

    /** copy of the current state: {turnSeq, side, event, angle, num, turnValid, distSeq, dist, focusSeq, focus} */
    long[] snapshot(String[] roadOut, long[] times) {
        synchronized (lock) {
            roadOut[0] = road;
            times[0] = turnTime;
            times[1] = distStamp;
            return new long[] { turnSeq, side, event, angle, num, turnValid, distSeq, dist, focusSeq, focus };
        }
    }

    public void updateNavigationNextTurnEvent(String roadName, int turnSide, int eventCode,
                                              int turnAngle, int turnNumber, int validFlag) {
        synchronized (lock) {
            road = roadName == null ? "" : roadName;
            side = turnSide;
            event = eventCode;
            angle = turnAngle;
            num = turnNumber;
            turnValid = validFlag;
            turnSeq++;
            turnTime = System.currentTimeMillis();
        }
        Log.log("AA turn road=\"" + (roadName == null ? "" : roadName) + "\" side=" + turnSide + " event=" + eventCode
            + " angle=" + turnAngle + " num=" + turnNumber + " valid=" + validFlag);
    }

    public void updateNavigationNextTurnDistance(int distance, int timeToTurn, int validFlag) {
        boolean logIt;
        synchronized (lock) {
            if (validFlag != 1) {
                logIt = true;
            } else {
                logIt = distSeq % 20 == 0 || Math.abs(distance - dist) > 200;
                dist = distance;
                distTime = timeToTurn;
                distSeq++;
                distStamp = System.currentTimeMillis();
            }
        }
        if (logIt) {
            Log.log("AA dist=" + distance + "m time=" + timeToTurn + "s valid=" + validFlag);
        }
    }

    public void navFocusRequestNotification(int focusType, int validFlag) {
        synchronized (lock) {
            focus = focusType;
            focusSeq++;
        }
        Log.log("AA navFocus type=" + focusType + " valid=" + validFlag);
    }

    public void asyncException(int errorCode, String message, int requestType) {
        Log.log("AA asyncException code=" + errorCode + " req=" + requestType + " msg=" + message);
    }

    // not subscribed, not used
    public void videoFocusRequestNotification(int a, int b) { }
    public void videoAvailable(boolean a, int b) { }
    public void audioFocusRequestNotification(int a, int b) { }
    public void audioAvailable(int a, boolean b, int c) { }
    public void voiceSessionNotification(int a, int b) { }
    public void microphoneRequestNotification(int a, int b) { }
    public void updateNowPlayingData(TrackData a, int b) { }
    public void updatePlaybackState(PlaybackInfo a, int b) { }
    public void updatePlayposition(int a, int b) { }
    public void updateCoverArtUrl(ResourceLocator a, int b) { }
    public void bluetoothPairingRequest(String a, int b) { }
    public void updateStreamParameters(int a, int b, int c, int d, int e, int f, int g) { }
    public void getConfiguration() { }
    public void responseSetConfiguration() { }
}
