package nl.vlessert.vigamup;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

public class GameCollection {
    private int activeGame = 0;
    private ArrayList<Game> gameObjects;
    private ArrayList<Game> gameObjectsWithTrackInformation;
    private Context ctx;
    private Random randomGenerator = new Random();


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
    }

    public void setCurrentGame(int position){
        activeGame = position;
    }

    public String getCurrentGameName(){
        if (activeGame == 0) activeGame=1;
        return gameObjects.get(activeGame).gameName;
    }

    public Game getCurrentGame(){
        return gameObjects.get(activeGame);
    }

    public void setRandomGameWithTrackInformation(){
        int index  = randomGenerator.nextInt(gameObjectsWithTrackInformation.size());
        Game game = gameObjectsWithTrackInformation.get(index);
        //Log.d("KSS", "wtf!!! "+ gameObjectsWithTrackInformation.size() + " - " + index + " - " +game.position);
        activeGame = game.position;
        //Log.d("KSS","game set: " + gameObjects.get(activeGame).gameName);
    }

    /*public Game getNextGame(){

    }

    public Game getPreviousGame(){

    }

    public void setRandomizerMode(int mode){

    }*/

    public ArrayList<Game> getGameObjectsArrayList(){
        //Log.d("KSS","test vanuit getGameObjectsArrayList: " + getCurrentGameName());
        return gameObjects;
    }

    //public
}