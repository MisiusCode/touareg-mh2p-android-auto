package de.vw.mib.asl.framework.internal.framework;
public class GenericCollector {
    public java.util.Map m = new java.util.TreeMap();
    public GenericCollector() { }
    public void setIntItem(int i, int v) { m.put(new Integer(i), new Integer(v)); }
    public void setStringItem(int i, String v) { m.put(new Integer(i), v); }
    public String toString() { return m.toString(); }
}
