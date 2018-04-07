package nl.vlessert.vigamup;

import android.content.Context;

/**
 * Created by niek on 05/11/2017.
 */

public class GameListMenuItem extends Game {
    private boolean morePlatformsAfter = false;
    private boolean morePlatformsBefore = false;

    public GameListMenuItem(String gameName, int musicType, Context ctx, int position){
        super(gameName, musicType, ctx, position);
    }

    @Override
    public boolean isGame(){ return false; }

    public void setMorePlatformsAfter(){
        morePlatformsAfter = true;
    }
    public void setMorePlatformsBefore(){
        morePlatformsBefore = true;
    }

    public boolean getMorePlatformsAfter(){
        return morePlatformsAfter;
    }
    public boolean getMorePlatformsBefore(){
        return morePlatformsBefore;
    }

}
