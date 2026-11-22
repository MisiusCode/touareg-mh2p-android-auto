package generated.de.vw.mib.asl.internal;
import de.vw.mib.asl.internal.list.impl.GenericASLList;
public class ListManager {
    static java.util.Map m = new java.util.HashMap();
    public static synchronized GenericASLList getGenericASLList(int id) {
        Object o = m.get(new Integer(id));
        if (o == null) { o = new GenericASLList(id); m.put(new Integer(id), o); }
        return (GenericASLList) o;
    }
}
