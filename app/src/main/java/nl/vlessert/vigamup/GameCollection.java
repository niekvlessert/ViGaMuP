package nl.vlessert.vigamup;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GameCollection {
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

    public GameCollection(Context ctx) {
        this.ctx = ctx;
    }

    private void searchForMusicTypeAndAddToListIfFound(String directory, String extension, int typeToAdd) {
        Log.d(LOG_TAG, "directory: " + directory + " - extension:" + extension + "typeToAdd: " + typeToAdd);
        File parentDir;
        File[] files;
        String[] strings;
        int position = 0;

        parentDir = new File(directory);
        files = parentDir.listFiles();
        if (typeToAdd != 4) {
            if (files != null) {
                for (File file : files) {
                    if (!extension.equals("")) {
                        if (file.getName().endsWith(extension)) {
                            strings = file.getName().split("\\.");
                            Log.d(LOG_TAG, "wow " + strings[1] + " found...");
                            gameObjects.add(new Game(strings[0], typeToAdd, 0, strings[1]));
                            position++;
                        }
                    }
                }
            }
        } else {
            for (File file : files) {
                Log.d(LOG_TAG, "file: " + file);
                if (file.isDirectory()) {
                    Log.d(LOG_TAG, "wow some tracker directory found: " + file.getName());

                    gameObjects.add(new Game(file.getName(), typeToAdd, 0, "Tracker"));
                }
                position++;
            }
        }
        if (position>0 && !foundMusicTypes.contains(typeToAdd)) {
            Log.d(LOG_TAG,"adding "+extension+" to foundMusicTypes");
            foundMusicTypes.add(typeToAdd);
        }
    }

    public void createGameCollection() {
        gameObjects = new ArrayList<>();

        searchForMusicTypeAndAddToListIfFound(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/KSS/", "kss", Constants.PLATFORM.KSS);
        searchForMusicTypeAndAddToListIfFound(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/SPC/", "rsn", Constants.PLATFORM.SPC);
        searchForMusicTypeAndAddToListIfFound(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/VGM/", "zip", Constants.PLATFORM.VGM);
        searchForMusicTypeAndAddToListIfFound(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/NSF/", "nsf", Constants.PLATFORM.NSF);
        searchForMusicTypeAndAddToListIfFound(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/Trackers/", "", Constants.PLATFORM.TRACKERS);

        gameObjectsWithTrackInformation = new ArrayList<>();
        gameObjectsWithoutTrackInformation = new ArrayList<>();
        Log.d(LOG_TAG, "hmm: " + gameObjects.size());
        for (Game game : gameObjects){
            if (game.hasTrackInformationList()){
                gameObjectsWithTrackInformation.add(game);
                Log.d(LOG_TAG,"adding: " + game.gameName + " " + game.position);
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