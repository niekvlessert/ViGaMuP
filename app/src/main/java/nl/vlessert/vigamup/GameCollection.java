package nl.vlessert.vigamup;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GameCollection {
    private int activeGame = 0;
    private ArrayList<Game> gameObjects;
    private ArrayList<Game> gameObjectsWithTrackInformation;
    private Context ctx;

    private List<String> randomizedGameAndTrackList = new ArrayList<>();
    private int randomizedGameAndTrackListPosition = 0;

    private final String LOG_TAG = "ViGaMuP game collection";

    public GameCollection(Context ctx){
        this.ctx = ctx;
    }

    public void createGameCollection(){
        File parentDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/KSS/");
        gameObjects = new ArrayList<>();
        File[] files = parentDir.listFiles();
        String[] strings;
        int position = 0;
        for (File file : files) {
            if(file.getName().endsWith(".kss")){
                strings = file.getName().split("\\.");
                gameObjects.add(new Game(strings[0], strings[1].toUpperCase(), ctx, position));
                position++;
            }
        }
        gameObjectsWithTrackInformation = new ArrayList<>();
        for (Game game : gameObjects){
            if (game.hasTrackInformationList()){
                gameObjectsWithTrackInformation.add(game);
                //Log.d("KSS","adding: " + game.gameName + " " + game.position);
            }
        }

        int a, b = 0;
        for (Game game : gameObjectsWithTrackInformation){
            List<GameTrack> list = game.getGameTrackList();
            for (a = 0; a < list.size(); a++){
                randomizedGameAndTrackList.add(b+","+a);
                //Log.d(LOG_TAG, "game title: " + game.getTitle() + ", track position: " + a);
            }
            b++;
        }
        Collections.shuffle(randomizedGameAndTrackList);

        /*Log.d(LOG_TAG, "non random: " + Arrays.toString(gameObjectsWithTrackInformation.toArray()));
        Log.d(LOG_TAG, "random: " + Arrays.toString(gameObjectsWithTrackInformationRandomized.toArray()));*/
    }

    public void setCurrentGame(int position){
        activeGame = position;
    }

    public Game getCurrentGame(){
        return gameObjectsWithTrackInformation.get(activeGame);
    }

    public Game getGameAtPosition(int position){ return gameObjectsWithTrackInformation.get(position); }

    public void setNextGame(){
        if (activeGame == gameObjectsWithTrackInformation.size()-1) activeGame = 0;
        else activeGame++;
    }

    public void setPreviousGame(){
        if (activeGame == 0) activeGame = gameObjectsWithTrackInformation.size()-1;
        else activeGame--;
    }

    public ArrayList<Game> getGameObjectsArrayList(){
        //Log.d("KSS","test vanuit getGameObjectsArrayList: " + getCurrentGameName());
        return gameObjects;
    }

    public String getNextRandomGameAndTrack(){
        if (randomizedGameAndTrackListPosition == randomizedGameAndTrackList.size()-1) randomizedGameAndTrackListPosition = 0;
            else randomizedGameAndTrackListPosition++;
        return randomizedGameAndTrackList.get(randomizedGameAndTrackListPosition);
    }

    public String getPreviousRandomGameAndTrack(){
        if (randomizedGameAndTrackListPosition == 0) randomizedGameAndTrackListPosition = randomizedGameAndTrackList.size()-1;
            else randomizedGameAndTrackListPosition--;
        return randomizedGameAndTrackList.get(randomizedGameAndTrackListPosition);
    }
}