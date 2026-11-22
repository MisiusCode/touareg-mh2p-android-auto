package de.vw.mib.asl.internal.list.impl;
public class GenericASLList {
    public final int id;
    Object[] items = new Object[0];
    public GenericASLList(int id) { this.id = id; }
    public void updateList(Object[] items) { this.items = items; test.Rec.add("list" + id + " <- " + java.util.Arrays.asList(items)); }
    public int getItemCount() { return items.length; }
    public Object getRowItem(int i) { return items[i]; }
}
