package vwaa;

import de.vw.mib.asl.framework.internal.framework.GenericCollector;
import de.vw.mib.asl.internal.list.impl.GenericASLList;
import de.vw.mib.bap.mqbab2.common.api.APIFactory;
import de.vw.mib.bap.mqbab2.common.api.navigation.NavigationService;
import de.vw.mib.bap.mqbab2.common.api.navigation.datatypes.NavigationDistanceToNextManeuver;
import de.vw.mib.bap.mqbab2.common.api.navigation.datatypes.NavigationTurnToInfo;
import de.vw.mib.bap.mqbab2.common.api.navigation.datatypes.iterator.elements.NavigationManeuverDescriptorElement;
import generated.de.vw.mib.asl.internal.ListManager;
import java.util.Iterator;

/**
 * Read-only diagnostics: what the cluster service currently sees as navigation data, whoever
 * wrote it (this plugin or the factory navigation). Logged only when it changes. Used to compare
 * the factory navigation's arrow data with this plugin's. Switch off with the marker vwaa_nosnoop.
 */
final class Snoop {
    private static String last = "";

    private Snoop() {
    }

    static void sample(boolean mine) {
        String s;
        try {
            s = build();
        } catch (Throwable t) {
            s = "error " + t;
        }
        if (!s.equals(last)) {
            last = s;
            Log.log("SNOOP " + (mine ? "plugin" : "other") + " " + s);
        }
    }

    private static String build() {
        StringBuffer sb = new StringBuffer();
        NavigationService n = APIFactory.getAPIFactory().getNavigationService();
        if (n == null) {
            return "navigationService=null";
        }
        sb.append("rg=").append(n.getRouteGuidanceState());
        sb.append(" navStatus=").append(n.getNavigationStatus());
        try {
            sb.append(" mapSwitch=").append(n.getMapSwitchState());
            sb.append(" kombiMap=").append(n.getKombiMapStatus()).append('/').append(n.getKombiMapVisibility());
        } catch (Throwable t) {
            sb.append(" map=?");
        }
        sb.append(" bap=[");
        try {
            Iterator it = n.getManeuverDescriptor();
            int k = 0;
            while (it != null && it.hasNext() && k < 4) {
                Object o = it.next();
                if (o instanceof NavigationManeuverDescriptorElement) {
                    NavigationManeuverDescriptorElement e = (NavigationManeuverDescriptorElement) o;
                    if (k > 0) {
                        sb.append("; ");
                    }
                    sb.append(e.getMainelement()).append('/').append(e.getDirection()).append('/')
                      .append(e.getZLevelGuidance()).append("/\"").append(e.getSideStreets()).append('"');
                }
                k++;
            }
        } catch (Throwable t) {
            sb.append('?').append(t);
        }
        sb.append(']');
        try {
            NavigationTurnToInfo t = n.getTurnToInfo();
            if (t != null) {
                sb.append(" turnTo=\"").append(t.getTurnToInfoStreet()).append("\"/\"").append(t.getTurnToInfoSignPost()).append('"');
            }
            NavigationDistanceToNextManeuver d = n.getDistanceToNextManeuver();
            if (d != null) {
                // distance rounded to 50 so the log does not grow with every metre
                int dist = d.getDistanceToNextManeuverDistance();
                sb.append(" dist~").append((dist / 50) * 50).append(" unit=").append(d.getDistanceToNextManeuverDistanceUnit())
                  .append(" bar=").append(d.getDistanceToNextManeuverBargraphOnOff());
            }
        } catch (Throwable t) {
            sb.append(" turnTo/dist=?").append(t);
        }
        rows(sb, 751);
        rows(sb, 838);
        return sb.toString();
    }

    private static void rows(StringBuffer sb, int id) {
        sb.append(" L").append(id).append('=');
        try {
            GenericASLList l = ListManager.getGenericASLList(id);
            if (l == null) {
                sb.append("null");
                return;
            }
            int c = l.getItemCount();
            sb.append(c).append('[');
            for (int i = 0; i < c && i < 3; i++) {
                Object o = l.getRowItem(i);
                if (i > 0) {
                    sb.append("; ");
                }
                if (o instanceof GenericCollector) {
                    GenericCollector g = (GenericCollector) o;
                    String txt = String.valueOf(g);
                    if (txt.length() > 160) {
                        txt = txt.substring(0, 160);
                    }
                    sb.append(txt);
                } else {
                    sb.append(o == null ? "null" : o.getClass().getName());
                }
            }
            sb.append(']');
        } catch (Throwable t) {
            sb.append('?').append(t);
        }
    }
}
