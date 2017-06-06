package nl.vlessert.vigamup;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

public class MultipleItemsList extends ListFragment {

    private static final String LOG_TAG = "ViGaMuP_List";
    GameCollection gameCollection;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View v = inflater.inflate(R.layout.gamelist_fragment, container, false);

        Bundle b = getArguments();
        //gameCollection = new GameCollection(getActivity().getApplicationContext(), (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE));
        //gameCollection.createGameCollection(b.getInt("PlatformAndType"));
        //gameCollection.createGameCollection();
        gameCollection = ((MainActivity)getActivity()).getGameCollection();
        GameCollectionShowPerPlatform gc = new GameCollectionShowPerPlatform();
        gc.setAdapterStuff(getActivity().getApplicationContext(), (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE));
        //gameCollection.setAdapterStuff(getActivity().getApplicationContext(), (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE));
        gc.addGamesFromPlatform(gameCollection, b.getInt("PlatformAndType"));
        //gameCollection.setMusicTypeToDisplay(b.getInt("PlatformAndType"));
        //setListAdapter(gameCollection);
        setListAdapter(gc);
        return v;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Game game = ((Game)getListAdapter().getItem(position));
        ((MainActivity)getActivity()).gameClicked(gameCollection.getGameObjectsWithTrackInformation().indexOf(game));
    }

    public static class ViewHolder {
        public TextView textView;
        public TextView textView2;
    }

    public static MultipleItemsList newInstance(int platformAndType) {

        MultipleItemsList f = new MultipleItemsList();
        Bundle b = new Bundle();
        //Log.d(LOG_TAG,"platformAndType"+platformAndType);
        b.putInt("PlatformAndType", platformAndType);
        f.setArguments(b);

        return f;
    }

    public class GameCollectionShowPerPlatform extends BaseAdapter {
        private ArrayList<Game> gameObjects = new ArrayList<>();
        private int musicTypeToDisplay;
        private static final int NO_TRACK_INFORMATION = 0;
        private static final int TRACK_INFORMATION = 1;
        private LayoutInflater mInflater;
        private Context ctx;

        public void addGamesFromPlatform(GameCollection gameCollection, int platform){
            musicTypeToDisplay = platform;

            for (Game game: gameCollection.getGameObjects()){
                if (game.getMusicType()==platform) {
                    gameObjects.add(game);
                }
            }
            Log.d(LOG_TAG,"gameObjects size: " + gameObjects.size());
        }

        public void setAdapterStuff(Context ctx, LayoutInflater layoutInflater){
            this.ctx = ctx;
            mInflater = layoutInflater;
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
                convertView = mInflater.inflate(R.layout.gamelist_item, null);

                holder.textView = (TextView)convertView.findViewById(R.id.text);
                holder.textView2 = (TextView)convertView.findViewById(R.id.text2);

                convertView.setTag(holder);
            } else {
                holder = (MultipleItemsList.ViewHolder) convertView.getTag();
            }

            // Need to have the gamecollection from the service to know which game is active..
            //Log.d("Vigamup", "position: " + position + " activeGame: " + activeGame);
            //MainActivity ma = (MainActivity) ctx;
            //if (gameObjects.get(position).getTitle().equals(ma.gameCollection.getCurrentGame().getTitle())) holder.textView.setBackgroundColor(0xfff00000);

            holder.textView.setText(gameObjects.get(position).getTitle());

            switch (type) {
                case NO_TRACK_INFORMATION:
                    holder.textView2.setText("No track information, click to let ViGaMup generate it...");

                    break;
                case TRACK_INFORMATION:
                    String information = gameObjects.get(position).getTotalPLayTimeHumanReadable();
                    if (gameObjects.get(position).getVendorAndYear().length()>0) {
                        information = information.concat("\n"+gameObjects.get(position).getVendorAndYear());
                        //holder2.textView.setHeight(300);
                    }
                    holder.textView2.setText(information);

                    break;
            }

            return convertView;
        }
    }
}