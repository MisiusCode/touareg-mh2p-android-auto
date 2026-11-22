package test;
public class Rec {
    static long t0 = System.currentTimeMillis();
    static String last = "";
    static int rep = 0;
    public static synchronized void add(String s) {
        if (s.equals(last)) { rep++; return; }
        if (rep > 0) System.out.println("          (repeated " + rep + "x)");
        rep = 0; last = s;
        System.out.println("  [" + ((System.currentTimeMillis() - t0) / 1000) + "s] HMI " + s);
    }
}
