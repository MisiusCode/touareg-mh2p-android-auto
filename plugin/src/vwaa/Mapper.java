package vwaa;

/**
 * Android Auto next-turn event -> cluster manoeuvre (main element, direction byte).
 *
 * The translation rules follow the table published by fifthBro for the Porsche/Audi
 * mh2p-cluster project (https://github.com/fifthBro/mh2p-cluster, CC BY-NC-SA 4.0).
 * Direction byte: 0 straight, 64 left, 128 back, 192 right (256 steps per full turn, counter-clockwise).
 */
final class Mapper {
    static final int ARRIVED = 3;
    static final int FOLLOW_STREET = 11;
    static final int TURN = 13;
    static final int TURN_ON_MAINROAD = 14;
    static final int EXIT_RIGHT = 15;
    static final int EXIT_LEFT = 16;
    static final int FORK_2 = 19;
    static final int ROUNDABOUT_TRS_RIGHT = 21;
    static final int ROUNDABOUT_TRS_LEFT = 22;
    static final int UTURN = 25;

    static final int SIDE_LEFT = 1;
    static final int SIDE_RIGHT = 2;

    /** direction latched for the roundabout being driven, -1 when none */
    private static int roundaboutDir = -1;

    private Mapper() {
    }

    static void reset() {
        roundaboutDir = -1;
    }

    static int angleToByte(int deg) {
        int a = deg % 360;
        if (a < 0) {
            a += 360;
        }
        return (a * 256) / 360;
    }

    /** @return {mainElement, direction} */
    static int[] map(int event, int side, int angle, int num) {
        int main = FOLLOW_STREET;
        int dir = 0;
        boolean roundabout = event >= 11 && event <= 13;

        switch (event) {
            case 3:
            case 4:
            case 5:
                main = TURN;
                break;
            case 6:
                main = UTURN;
                break;
            case 7:
                main = TURN_ON_MAINROAD;
                break;
            case 8:
            case 10:
                main = side == SIDE_RIGHT ? EXIT_RIGHT : (side == SIDE_LEFT ? EXIT_LEFT : FOLLOW_STREET);
                break;
            case 9:
                main = FORK_2;
                break;
            case 14:
                if (side == 0 && angle == 0 && num == 1) {
                    main = FOLLOW_STREET;
                } else if (side == SIDE_LEFT) {
                    main = EXIT_LEFT;
                } else {
                    main = EXIT_RIGHT;      // right-hand traffic default
                }
                break;
            case 11:
            case 12:
            case 13:
                main = side == SIDE_LEFT ? ROUNDABOUT_TRS_LEFT : ROUNDABOUT_TRS_RIGHT;
                break;
            default:
                main = FOLLOW_STREET;
                break;
        }

        if (roundabout) {
            if (angle > 0) {
                // side 1 = clockwise roundabout (left-hand traffic), otherwise counter-clockwise
                int conv = side == SIDE_LEFT ? (540 - angle) % 360 : (angle + 180) % 360;
                dir = angleToByte(conv);
            } else if (num >= 3) {
                dir = side == SIDE_LEFT ? 192 : 64;
            } else if (num == 2) {
                dir = 0;
            } else if (num == 1) {
                dir = side == SIDE_LEFT ? 64 : 192;
            }
            if (event == 12 && roundaboutDir >= 0) {
                dir = roundaboutDir;        // keep the arrow while inside the roundabout
            } else {
                roundaboutDir = dir;
            }
        } else {
            roundaboutDir = -1;
            if (angle != 0) {
                dir = angleToByte(angle);
            } else if ((event >= 3 && event <= 7) || event == 9) {
                if (side == SIDE_LEFT) {
                    dir = event == 3 ? 32 : 64;
                } else if (side == SIDE_RIGHT) {
                    dir = event == 3 ? 224 : 192;
                }
            }
        }
        return new int[] { main, dir };
    }
}
