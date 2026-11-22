package vwaa;

/**
 * Tidies the text Android Auto sends as "road". Navigation apps put driving instructions there
 * as well as street names; the HUD line is short, so only the name is kept.
 */
final class Streets {
    private static final String[] PREFIX = new String[] { "towards ", "toward ", "onto ", "on " };
    private static final String[] INSTRUCTION = new String[] {
        "turn ", "head ", "make a u-turn", "continue", "keep ", "slight ", "sharp ", "take the ", "merge", "at the roundabout", "enter the roundabout" };

    private Streets() {
    }

    static String clean(String road) {
        if (road == null) {
            return "";
        }
        String s = road.trim();
        String low = s.toLowerCase();
        for (int i = 0; i < PREFIX.length; i++) {
            if (low.startsWith(PREFIX[i])) {
                s = s.substring(PREFIX[i].length()).trim();
                low = s.toLowerCase();
                break;
            }
        }
        for (int i = 0; i < INSTRUCTION.length; i++) {
            if (low.startsWith(INSTRUCTION[i])) {
                return "";
            }
        }
        return s;
    }
}
