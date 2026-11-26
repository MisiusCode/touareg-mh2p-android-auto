package vwaa;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/** HMI bundle entry point. Does nothing but start one daemon thread; never throws. */
public class Activator implements BundleActivator {
    static final String VERSION = "0.7.1";

    public void start(BundleContext ctx) throws Exception {
        try {
            Log.log("VWAndroidAutoNav " + VERSION + " Activator.start");
            Bridge.startThread();
        } catch (Throwable t) {
            try {
                Log.log("Activator.start error: " + t);
            } catch (Throwable t2) {
            }
        }
    }

    public void stop(BundleContext ctx) throws Exception {
        try {
            Bridge.stopThread();
        } catch (Throwable t) {
        }
    }
}
