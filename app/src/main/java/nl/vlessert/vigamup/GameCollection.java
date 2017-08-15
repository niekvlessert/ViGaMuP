package nl.vlessert.vigamup;

import android.content.Context;
import android.content.IntentFilter;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class GameCollection{
    private int activeGame = 0;
    private ArrayList<Game> gameObjects;
    private ArrayList<Game> gameObjectsWithTrackInformation;
    private ArrayList<Game> gameObjectsWithoutTrackInformation;
    private Context ctx;
    private boolean gameCollectionCreated = false;

    private List<String> randomizedGameAndTrackList = new ArrayList<>();
    private int randomizedGameAndTrackListPosition = 0;

    private final String LOG_TAG = "ViGaMuP game collection";

    private ArrayList<Integer> foundMusicTypes = new ArrayList<>();

    private int musicTypeToDisplay = 0;

    public void setMusicTypeToDisplay(int musicType) {
        this.musicTypeToDisplay = musicType;
    }

    public GameCollection(Context ctx){
        this.ctx = ctx;
    }

    public void createGameCollection() {
        File parentDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/KSS/");
        gameObjects = new ArrayList<>();
        File[] files = parentDir.listFiles();
        String[] strings;
        int position = 0;
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".kss")) {
                    strings = file.getName().split("\\.");
                    gameObjects.add(new Game(strings[0], Constants.PLATFORM.MSX, ctx, position));
                    position++;
                }
            }
        }
        if (position>0) {
            Log.d(LOG_TAG,"foundMusicTypes.add(Constants.PLATFORM.MSX");
            foundMusicTypes.add(Constants.PLATFORM.MSX);
        }
        int storedPosition = position;
        parentDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/SPC/");
        files = parentDir.listFiles();
        if (files!=null) {
            for (File file : files) {
                if (file.getName().endsWith(".rsn")) {
                    strings = file.getName().split("\\.");
                    gameObjects.add(new Game(strings[0], Constants.PLATFORM.SNES, ctx, position));
                    position++;
                }
            }
        }
        if (position>storedPosition) {
            Log.d(LOG_TAG,"foundMusicTypes.add(Constants.PLATFORM.SNES");
            foundMusicTypes.add(Constants.PLATFORM.SNES);
        }

        storedPosition = position;
        parentDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/VGM/");
        files = parentDir.listFiles();
        if (files!=null) {
            for (File file : files) {
                if (file.getName().endsWith(".zip")) {
                    strings = file.getName().split("\\.");
                    gameObjects.add(new Game(strings[0], Constants.PLATFORM.VGM, ctx, position));
                    position++;
                }
            }
        }
        if (position>storedPosition) {
            Log.d(LOG_TAG,"foundMusicTypes.add(Constants.PLATFORM.VGM");
            foundMusicTypes.add(Constants.PLATFORM.VGM);
        }

        gameObjectsWithTrackInformation = new ArrayList<>();
        gameObjectsWithoutTrackInformation = new ArrayList<>();
        for (Game game : gameObjects){
            if (game.hasTrackInformationList()){
                gameObjectsWithTrackInformation.add(game);
                //Log.d("KSS","adding: " + game.gameName + " " + game.position);
            } else gameObjectsWithoutTrackInformation.add(game);
        }
        Collections.sort(gameObjectsWithTrackInformation, new GameCollectionTitleComperator());
        Collections.sort(gameObjectsWithoutTrackInformation, new GameCollectionTitleComperator());

        gameObjects = new ArrayList<>(gameObjectsWithTrackInformation);
        gameObjects.addAll(gameObjectsWithoutTrackInformation);

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

        gameCollectionCreated = true;

        //Log.d(LOG_TAG,"hmmmm " + gameObjects.size() + " == " + gameObjectsWithTrackInformation.size() + "?");
        /*Log.d(LOG_TAG, "non random: " + Arrays.toString(gameObjectsWithTrackInformation.toArray()));
        Log.d(LOG_TAG, "random: " + Arrays.toString(gameObjectsWithTrackInformationRandomized.toArray()));*/
    }

    public boolean isGameCollectionCreated(){
        return gameCollectionCreated;
    }

    public void deleteGameCollectionObjects() {
        gameObjects = null;
        gameObjectsWithoutTrackInformation = null;
        gameObjectsWithTrackInformation = null;
        gameCollectionCreated = false;
        foundMusicTypes = new ArrayList<>();
    }

    public void setCurrentGame(int position){
        activeGame = position;
    }

    public Game getCurrentGame(){
        Log.d(LOG_TAG, "active game: " + activeGame + " title: " + gameObjectsWithTrackInformation.get(activeGame).gameName);
        return gameObjectsWithTrackInformation.get(activeGame);
    }

    public Game getGameAtPosition(int position){ return gameObjectsWithTrackInformation.get(position); }
    //public Game getGameAtPosition(int position){ return gameObjects.get(position); }


    public void setNextGame(){
        Log.d(LOG_TAG, "active game: "+activeGame);
        for (Game game: gameObjectsWithTrackInformation){
            Log.d(LOG_TAG, "Game info: "+ gameObjectsWithTrackInformation.indexOf(game)+ " " + game.gameName);
        }
        if (activeGame == gameObjectsWithTrackInformation.size()-1) activeGame = 0;
        else activeGame++;
    }

    public void setPreviousGame(){
        if (activeGame == 0) activeGame = gameObjectsWithTrackInformation.size()-1;
        else activeGame--;
    }

    public ArrayList<Game> getGameObjects(){
        //Log.d("KSS","test vanuit getGameObjects: " + getCurrentGameName());
        return gameObjects;
    }

    public ArrayList<Game> getGameObjectsWithTrackInformation(){
        //Log.d("KSS","test vanuit getGameObjects: " + getCurrentGameName());
        return gameObjectsWithTrackInformation;
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

    public ArrayList<Integer> getFoundMusicTypes(){
        return foundMusicTypes;
    }
}

class GameCollectionTitleComperator implements Comparator<Game>
{
    public int compare(Game left, Game right) {
        return left.getTitle().compareTo(right.getTitle());
    }
}