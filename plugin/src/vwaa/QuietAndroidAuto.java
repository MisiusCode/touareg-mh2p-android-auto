package vwaa;

import de.vw.mib.bap.mqbab2.common.api.androidauto.AndroidAutoService;
import de.vw.mib.bap.mqbab2.common.api.androidauto.AndroidAutoServiceListener;

/**
 * Wraps the cluster-protocol Android Auto service. The only consumer of
 * isAndroidAutoRouteGuidanceActive() is the function that makes the cluster show
 * "Navigation on mobile device is active". While this plugin is feeding real turn data
 * that message is hidden; in every other case the stock answer is passed through.
 */
final class QuietAndroidAuto implements AndroidAutoService {
    final AndroidAutoService d;

    QuietAndroidAuto(AndroidAutoService d) {
        this.d = d;
    }

    public void addAndroidAutoServiceListener(AndroidAutoServiceListener l, int[] ids) {
        d.addAndroidAutoServiceListener(l, ids);
    }

    public void removeAndroidAutoServiceListener(AndroidAutoServiceListener l, int[] ids) {
        d.removeAndroidAutoServiceListener(l, ids);
    }

    public boolean isAndroidAutoRouteGuidanceActive() {
        if (Bridge.hideMessage) {
            return false;
        }
        return d.isAndroidAutoRouteGuidanceActive();
    }

    /** the stock answer, for the bridge */
    boolean stockActive() {
        return d.isAndroidAutoRouteGuidanceActive();
    }
}
