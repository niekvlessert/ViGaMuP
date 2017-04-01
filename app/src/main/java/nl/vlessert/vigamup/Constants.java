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
}