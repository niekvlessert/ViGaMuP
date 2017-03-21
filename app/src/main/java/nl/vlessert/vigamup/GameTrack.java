package nl.vlessert.vigamup;

/**
 * Created by niek on 25/02/17.
 */

public class GameTrack {

    int trackNr;
    String title;
    int length;
    int partToSkipWhenUsingRepeat;
    boolean repeatable;

    public GameTrack(int trackNr, String title, int length, int partToSkipWhenUsingRepeat, boolean repeatable){
        this.trackNr = trackNr;
        this.title = title;
        this.length = length;
        this.partToSkipWhenUsingRepeat = partToSkipWhenUsingRepeat;
        this.repeatable = repeatable;
    }

    public int getPlayTimeWithRepeat(int amountToRepeat) {
        if (repeatable)
            return length + (amountToRepeat * (length - partToSkipWhenUsingRepeat));
        else
            return length;
    }

    public int getTrackNr(){
        return trackNr;
    }

    public String getTrackTitle() { return title; }

    public int getTrackLength(){
        return length;
    }
}
