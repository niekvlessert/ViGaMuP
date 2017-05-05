package nl.vlessert.vigamup;

public class Constants {
    public interface ACTION {
        public static String MAIN_ACTION = "nl.vlessert.vigamup.action.main";
        public static String PREV_ACTION = "nl.vlessert.vigamup.action.prev";
        public static String PLAY_ACTION = "nl.vlessert.vigamup.action.play";
        public static String NEXT_ACTION = "nl.vlessert.vigamup.action.next";
        public static String REPEAT_ACTION = "nl.vlessert.vigamup.action.repeat";
        public static String STARTFOREGROUND_ACTION = "nl.vlessert.vigamup.action.startforeground";
        public static String STOPFOREGROUND_ACTION = "nl.vlessert.vigamup.action.stopforeground";
    }

    public interface NOTIFICATION_ID {
        public static int FOREGROUND_SERVICE = 101;
    }

    public interface REPEAT_MODES {
        public static int NORMAL_PLAYBACK = 0;
        public static int LOOP_TRACK = 1;
        public static int LOOP_GAME = 2;
        public static int SHUFFLE_IN_GAME = 3;
        public static int SHUFFLE_IN_PLATFORM = 4;
    }

    public interface BIG_VIEW_TYPES {
        int SQUARE = 0;
        int RECTANGULAR = 1;
        int STRETCHED = 2;
    }
}