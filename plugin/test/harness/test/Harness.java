package test;

import java.lang.reflect.*;
import de.vw.mib.asl.framework.internal.framework.ServiceManager;
import de.vw.mib.asl.api.navbap.*;
import de.vw.mib.asl.framework.api.dsiproxy.*;
import de.vw.mib.bap.mqbab2.common.api.APIFactory;
import de.vw.mib.bap.mqbab2.common.api.androidauto.*;
import org.dsi.ifc.androidauto2.*;
import org.dsi.ifc.base.DSIListener;

public class Harness {
    static DSIAndroidAuto2Listener sub;
    static boolean stockRg = false;

    static Object proxy(Class c, final String tag) {
        return Proxy.newProxyInstance(Harness.class.getClassLoader(), new Class[] { c }, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] a) throws Throwable {
                String n = m.getName();
                if (n.equals("getDSIProxy")) return proxy(DSIProxy.class, "dsiProxy");
                if (n.equals("getService")) return a[1] == DSIAndroidAuto2.class ? proxy(DSIAndroidAuto2.class, "aaService") : proxy((Class) a[1], "komo" + a[2]);
                if (n.equals("setNotification") && tag.startsWith("komo")) { Rec.add(tag + ".setNotification"); return null; }
                if (n.equals("setNotification")) { sub = (DSIAndroidAuto2Listener) a[1]; Rec.add("aa.setNotification " + java.util.Arrays.toString((int[]) a[0])); return null; }
                if (n.equals("hashCode")) return new Integer(1);
                if (n.equals("getNavigationService")) return proxy(de.vw.mib.bap.mqbab2.common.api.navigation.NavigationService.class, "nav");
                if (tag.equals("nav")) {
                    Class r = m.getReturnType();
                    if (r == int.class) return new Integer(n.equals("getRouteGuidanceState") ? 1 : 0);
                    if (r == boolean.class) return Boolean.FALSE;
                    if (n.equals("getManeuverDescriptor")) return new java.util.ArrayList().iterator();
                    return null;
                }
                if (n.equals("toString")) return tag;
                StringBuffer sb = new StringBuffer(tag + "." + n + "(");
                if (a != null) for (int i = 0; i < a.length; i++) sb.append(i > 0 ? ", " : "").append(a[i] instanceof String ? "\"" + a[i] + "\"" : String.valueOf(a[i]));
                Rec.add(sb.append(")").toString());
                return null;
            }
        });
    }

    static void say(String s) { System.out.println("--- " + s); }

    public static void main(String[] args) throws Exception {
        ServiceManager.aslPropertyManager = (de.vw.mib.asl.ASLPropertyManager) proxy(de.vw.mib.asl.ASLPropertyManager.class, "propertyManager");
        ASLNavBAPFactory.api = (ASLNavBAPAPI) proxy(ASLNavBAPAPI.class, "navbap");
        DSIProxyFactory.api = (DSIProxyAPI) proxy(DSIProxyAPI.class, "dsiProxyApi");
        AndroidAutoService stock = new AndroidAutoService() {
            public void addAndroidAutoServiceListener(AndroidAutoServiceListener l, int[] i) { }
            public void removeAndroidAutoServiceListener(AndroidAutoServiceListener l, int[] i) { }
            public boolean isAndroidAutoRouteGuidanceActive() { return stockRg; }
        };
        APIFactory.stockCreate(stock);
        APIFactory.self = (de.vw.mib.bap.mqbab2.common.api.APIFactoryInterface) proxy(de.vw.mib.bap.mqbab2.common.api.APIFactoryInterface.class, "apiFactory");

        say("bundle start");
        vwaa.Activator act = new vwaa.Activator();
        act.start(null);
        Thread.sleep(47000);
        say("after start delay: subscribed=" + (sub != null) + " wrapper=" + APIFactory.current().getClass().getName());

        say("phone starts guidance; cluster asks 'is phone guidance active?' -> " + APIFactory.current().isAndroidAutoRouteGuidanceActive() + " (stock would say " + true + ")");
        stockRg = true;
        Thread.sleep(1500);
        say("far turn right (720 m), then near (240 m)");
        sub.updateNavigationNextTurnEvent("Trumpoji g.", 2, 4, 0, 0, 1);
        sub.updateNavigationNextTurnDistance(720, 60, 1);
        Thread.sleep(2500);
        sub.updateNavigationNextTurnDistance(240, 20, 1);
        Thread.sleep(1500);
        say("straight (event 14, side 0)");
        sub.updateNavigationNextTurnEvent("towards Slyv\u0173 g.", 0, 14, 0, 0, 1);
        sub.updateNavigationNextTurnDistance(90, 9, 1);
        Thread.sleep(1500);
        say("placeholder event (valid=0) must be ignored");
        sub.updateNavigationNextTurnEvent("", 0, 0, 0, 0, 0);
        Thread.sleep(1200);
        say("roundabout, second exit");
        sub.updateNavigationNextTurnEvent("Račkūnų gatvė", 2, 13, 0, 2, 1);
        sub.updateNavigationNextTurnDistance(400, 30, 1);
        Thread.sleep(1500);
        say("slight left, then destination ahead");
        sub.updateNavigationNextTurnEvent("Slyvų gatvė", 1, 3, 0, 0, 1);
        Thread.sleep(1200);
        sub.updateNavigationNextTurnEvent("", 0, 19, 0, 0, 1);
        sub.updateNavigationNextTurnDistance(60, 5, 1);
        Thread.sleep(1500);
        say("arrival (event 0, valid 2), phone guidance off");
        sub.updateNavigationNextTurnEvent("", 0, 0, 0, 0, 2);
        stockRg = false;
        Thread.sleep(11000);
        say("vwaa_off marker: plugin must pass the stock answer through");
        new java.io.File("/tmp/vwaa_off").createNewFile();
        stockRg = true;
        Thread.sleep(3000);
        say("cluster asks with vwaa_off -> " + APIFactory.current().isAndroidAutoRouteGuidanceActive());
        new java.io.File("/tmp/vwaa_off").delete();
        say("bundle stop");
        act.stop(null);
        Thread.sleep(1500);
        say("after stop: service is " + (APIFactory.current() == stock ? "the stock object again" : APIFactory.current().getClass().getName()));
    }
}
