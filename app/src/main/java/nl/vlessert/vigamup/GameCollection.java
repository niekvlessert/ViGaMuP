package nl.vlessert.vigamup;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

public class GameCollection {
    private int activeGame = 0;
    private boolean randomizerActive = false;
    private ArrayList<Game> gameObjects;
    private ArrayList<Game> gameObjectsWithTrackInformation;
    private ArrayList<Game> gameObjectsWithTrackInformationRandomized;
    private Context ctx;
    private Random randomGenerator = new Random();

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
        Log.d(LOG_TAG, "gameObjectsWithTrackInformation size: "+ gameObjectsWithTrackInformation.size());

        gameObjectsWithTrackInformationRandomized = new ArrayList<>(gameObjectsWithTrackInformation);
        long seed = System.nanoTime();
        Collections.shuffle(gameObjectsWithTrackInformationRandomized, new Random(seed));

        /*Log.d(LOG_TAG, "non random: " + Arrays.toString(gameObjectsWithTrackInformation.toArray()));
        Log.d(LOG_TAG, "random: " + Arrays.toString(gameObjectsWithTrackInformationRandomized.toArray()));*/
    }

    public void enableRandomizer(){ randomizerActive = true; }
    public void disableRandomizer(){ randomizerActive = false; }

    public void setCurrentGame(int position){
        activeGame = position;
    }

    public Game getCurrentGame(){
        if (randomizerActive) return gameObjectsWithTrackInformationRandomized.get(activeGame); else return gameObjectsWithTrackInformation.get(activeGame);
    }

    public Game getCurrentGameNonRandom(){ return gameObjectsWithTrackInformation.get(activeGame); }

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
}