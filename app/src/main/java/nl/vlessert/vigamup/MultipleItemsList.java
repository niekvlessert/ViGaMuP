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
import android.widget.Toast;

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
        //gameCollection.setAdapterStuff(getActivity().getApplicationContext(), (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE));
        //gameCollection.setMusicTypeToDisplay(b.getInt("PlatformAndType"));
        //setListAdapter(gameCollection);

        gameCollection = ((MainActivity)getActivity()).getGameCollection();

        GameCollectionShowPerPlatform gc = new GameCollectionShowPerPlatform();
        gc.setAdapterStuff(getActivity().getApplicationContext(), (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE));
        gc.addGamesFromPlatform(gameCollection, b.getInt("PlatformAndType"));

        setListAdapter(gc);
        return v;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Game game = ((Game)getListAdapter().getItem(position));
        if (game.isGame()) {
            if (game.hasTrackInformationAvailable()) {
                ((MainActivity) getActivity()).gameClicked(gameCollection.getGameObjectsWithTrackInformation().indexOf(game));
            } else {
                Toast.makeText((getActivity()), "This game has no track information... please add it, look at vigamup.club for more information.", Toast.LENGTH_LONG).show();
            }
        }
    }

    public static class ViewHolder {
        public TextView textView;
        public TextView textView2;
        public TextView textView3;
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
        private static final int NO_TRACK_INFORMATION = 0;
        private static final int TRACK_INFORMATION = 1;
        private static final int GAME_LIST_MENU_ITEM = 2;
        private LayoutInflater mInflater;
        private Context ctx;

        private void addGamesFromPlatform(GameCollection gameCollection, int platform){

            // insert / append gameListMenuItems for every platform type
            GameListMenuItem item = new GameListMenuItem("header", platform, ctx, 0);
            ArrayList<Integer> foundMusicTypes = gameCollection.getFoundMusicTypes();
            if (foundMusicTypes.indexOf(platform)>0) item.setMorePlatformsBefore();
            if (foundMusicTypes.indexOf(platform)+1<foundMusicTypes.size()) item.setMorePlatformsAfter();
            //Log.d(LOG_TAG, "foundMusicTypes.indexOf(platform): "+foundMusicTypes.indexOf(platform) + " foundMusicTypes.size(): " + foundMusicTypes.size());

            gameObjects.add(item);

            for (Game game: gameCollection.getGameObjects()){
                if (game.getMusicType()==platform) {
                    gameObjects.add(game);
                }
            }
            Log.d(LOG_TAG,"gameObjects size: " + gameObjects.size());

            // head
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
            return 3;
        }

        @Override
        public int getCount() {
            return gameObjects.size();
        }

        @Override
        public int getItemViewType(int position) {
            if (!gameObjects.get(position).isGame()) return GAME_LIST_MENU_ITEM;

            return gameObjects.get(position).hasTrackInformationList() ? TRACK_INFORMATION : NO_TRACK_INFORMATION;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            MultipleItemsList.ViewHolder holder;
            int type = getItemViewType(position);

            if (type == GAME_LIST_MENU_ITEM) {
                holder = new MultipleItemsList.ViewHolder();
                GameListMenuItem item = (GameListMenuItem) gameObjects.get(position);
                boolean hasMorePlatFormsAfter = item.getMorePlatformsAfter();
                boolean hasMorePlatFormsBefore = item.getMorePlatformsBefore();
                convertView = mInflater.inflate(R.layout.gamelist_menu_item,null);
                holder.textView = convertView.findViewById(R.id.previous);
                holder.textView2 = convertView.findViewById(R.id.type);
                holder.textView3 = convertView.findViewById(R.id.next);
                int platform = gameObjects.get(position).getMusicType();
                if (hasMorePlatFormsBefore) holder.textView.setText("<");
                if (hasMorePlatFormsAfter) holder.textView3.setText(">");
                switch (platform) {
                    case Constants.PLATFORM.KSS:
                        holder.textView2.setText("KSS");
                        break;
                    case Constants.PLATFORM.SPC:
                        holder.textView2.setText("SPC");
                        break;
                    case Constants.PLATFORM.VGM:
                        holder.textView2.setText("VGM");
                        break;
                    case Constants.PLATFORM.NSF:
                        holder.textView2.setText("NSF");
                        break;
                    case Constants.PLATFORM.TRACKERS:
                        holder.textView2.setText("Trackers");
                        break;
                    case Constants.PLATFORM.OTHERS:
                        holder.textView2.setText("Other platforms");
                        break;
                }
                //holder.textView2.setText("tekst");
                convertView.setTag(holder);
                return convertView;
            }

            if (convertView == null) {
                holder = new MultipleItemsList.ViewHolder();
                convertView = mInflater.inflate(R.layout.gamelist_item, null);

                holder.textView = convertView.findViewById(R.id.text);
                holder.textView2 = convertView.findViewById(R.id.text2);

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
                    holder.textView2.setText("No track information, FFT to do it automagically is coming (I hope)...");

                    break;
                case TRACK_INFORMATION:
                    String information = gameObjects.get(position).getTotalPLayTimeHumanReadable();
                    if (gameObjects.get(position).getVendorAndYear().length()>0) {
                        information = information.concat("\n"+gameObjects.get(position).getVendorAndYear());
                        //holder2.textView.setHeight(300);
                    } else {
                        int musicType = gameObjects.get(position).getMusicType();
                        if (musicType == Constants.PLATFORM.NSF) {
                            information = information.concat("\nNSF format, NES");
                            String composers = gameObjects.get(position).getComposers();
                            if (composers.length()>0) information = information.concat("\n"+ composers);
                        }
                    }
                    holder.textView2.setText(information);

                    break;
            }

            return convertView;
        }
    }
}