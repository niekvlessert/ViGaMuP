package nl.vlessert.vigamup;

/**
 * Created by niek on 25/02/17.
 */

public class GameTrack {

    private int trackNr;
    String title;
    private int length;
    private int partToSkipWhenUsingRepeat;
    private boolean repeatable;
    private int position;
    private String fileName;

    public GameTrack(int trackNr, String title, int length, int partToSkipWhenUsingRepeat, boolean repeatable, int position, String fileName){
        this.trackNr = trackNr;
        this.title = title;
        this.length = length;
        this.partToSkipWhenUsingRepeat = partToSkipWhenUsingRepeat;
        this.repeatable = repeatable;
        this.position = position;
        this.fileName = fileName;
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

    public int getPosition(){
        return position;
    }

    public String getFileName() { return fileName; }
}
