package nl.vlessert.vigamup;

import android.content.Context;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class GameCollection extends BaseAdapter {
    private int activeGame = 0;
    private ArrayList<Game> gameObjects;
    private ArrayList<Game> gameObjectsWithTrackInformation;
    private ArrayList<Game> gameObjectsWithoutTrackInformation;
    private Context ctx;

    private List<String> randomizedGameAndTrackList = new ArrayList<>();
    private int randomizedGameAndTrackListPosition = 0;

    private final String LOG_TAG = "ViGaMuP game collection";

    private static final int NO_TRACK_INFORMATION = 0;
    private static final int TRACK_INFORMATION = 1;

    private ArrayList mData = new ArrayList();
    private LayoutInflater mInflater;

    private TreeSet mSeparatorsSet = new TreeSet();

    public GameCollection(Context ctx, LayoutInflater layoutInflater){
        this.ctx = ctx;
        mInflater = layoutInflater;
    }

    public GameCollection(Context ctx){
        this.ctx = ctx;
    }

    public void createGameCollection(int platformAndGameType){
        switch (platformAndGameType) {
            case Constants.PLATFORM.MSX:
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
                break;
            case Constants.PLATFORM.SNES:
                parentDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/SPC/");
                gameObjects = new ArrayList<>();
                files = parentDir.listFiles();
                position = 0;
                for (File file : files) {
                    if(file.getName().endsWith(".spc")){
                        strings = file.getName().split("\\.");
                        gameObjects.add(new Game(strings[0], strings[1].toUpperCase(), ctx, position));
                        position++;
                    }
                }
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

        //Log.d(LOG_TAG,"hmmmm " + gameObjects.size() + " == " + gameObjectsWithTrackInformation.size() + "?");
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

    @Override
    public Game getItem(int position) {
        return gameObjects.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getCount() {
        return gameObjects.size();
    }

    @Override
    public int getItemViewType(int position) {
        return gameObjects.get(position).hasTrackInformationList() ? TRACK_INFORMATION : NO_TRACK_INFORMATION;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        MultipleItemsList.ViewHolder holder;
        int type = getItemViewType(position);
        if (convertView == null) {
            holder = new MultipleItemsList.ViewHolder();

            switch (type) {
                case NO_TRACK_INFORMATION:
                    convertView = mInflater.inflate(R.layout.gamelist_item, null);

                    holder.textView = (TextView)convertView.findViewById(R.id.text);
                    holder.textView2 = (TextView)convertView.findViewById(R.id.text2);

                    holder.textView2.setText("No track information, click to let ViGaMup generate it...");

                    break;
                case TRACK_INFORMATION:
                    convertView = mInflater.inflate(R.layout.gamelist_item, null);

                    holder.textView = (TextView)convertView.findViewById(R.id.text);
                    holder.textView2 = (TextView)convertView.findViewById(R.id.text2);

                    String information = gameObjects.get(position).getTotalPLayTimeHumanReadable();
                    if (gameObjects.get(position).getVendorAndYear().length()>0) {
                        information = information.concat("\n"+gameObjects.get(position).getVendorAndYear());
                        //holder2.textView.setHeight(300);
                    }
                    holder.textView2.setText(information);

                    break;
            }

            //if (position==5) holder.textView.setBackgroundColor(0xfff00000);
            convertView.setTag(holder);

        } else {
            holder = (MultipleItemsList.ViewHolder)convertView.getTag();
        }
        holder.textView.setText(gameObjects.get(position).getTitle());
        return convertView;
    }
}

class GameCollectionTitleComperator implements Comparator<Game>
{
    public int compare(Game left, Game right) {
        return left.getTitle().compareTo(right.getTitle());
    }
}