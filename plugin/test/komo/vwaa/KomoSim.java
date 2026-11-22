package vwaa;
import java.lang.reflect.*;
import de.vw.mib.asl.framework.api.dsiproxy.*;
import org.dsi.ifc.komoview.DSIKOMOViewListener;
public class KomoSim {
    static DSIKOMOViewListener hud;
    static long t;
    static Object proxy(final Class c, final String tag) {
        return Proxy.newProxyInstance(KomoSim.class.getClassLoader(), new Class[] { c }, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] a) {
                String n = m.getName();
                if (n.equals("getDSIProxy")) return proxy(DSIProxy.class, "proxy");
                if (n.equals("getService")) return proxy((Class) a[1], ((Class) a[1]).getName().replaceAll(".*DSIKOMO", "") + (a.length > 2 ? a[2] : ""));
                if (n.equals("addResponseListener")) { if (((Integer) a[2]).intValue() == 1) hud = (DSIKOMOViewListener) a[3]; return null; }
                if (n.equals("setNotification") || n.equals("clearNotification") || n.equals("setRouteInfoElement") || n.equals("setRouteInfo")) return null;
                if (n.equals("toString")) return tag; if (n.equals("hashCode")) return new Integer(1);
                StringBuffer sb = new StringBuffer("        t=" + t / 1000 + "s  " + tag + "." + n + "(");
                for (int i = 0; a != null && i < a.length; i++) sb.append(i > 0 ? "," : "").append(a[i]);
                System.out.println(sb.append(")")); return null;
            }
        });
    }
    public static void main(String[] x) throws Exception {
        DSIProxyFactory.api = (DSIProxyAPI) proxy(DSIProxyAPI.class, "api");
        boolean hitRun = x.length > 0;
        Komo k = new Komo();
        k.connect(100000L);
        for (t = 0; t <= (hitRun ? 80000L : 560000L); t += 300) {
            if (hitRun && t == 60000L) hud.updateVisibility(true, 1);
            if (!hitRun && t == 125100L) hud.komoViewResult(1);
            k.show(100000L + t, true, true, 13, 192, "Birutes g.", 400);
        }
        k.clear(); k.shutdown();
    }
}
