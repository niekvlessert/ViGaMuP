package nl.vlessert.vigamup;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

public class MultipleItemsList extends ListFragment {

    private static final String LOG_TAG = "ViGaMuP_List";
    GameCollection gameCollection;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View v = inflater.inflate(R.layout.gamelist_fragment, container, false);

        Bundle b = getArguments();
        gameCollection = new GameCollection(getActivity().getApplicationContext(), (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE));
        gameCollection.createGameCollection(b.getInt("PlatformAndType"));
        setListAdapter(gameCollection);

        return v;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {

        Bundle b = getArguments();
        if (b.getInt("PlatformAndType") == Constants.PLATFORM.MSX) {
            Log.d("KSS","position: " + position);
            Log.d("KSS", ((Game)getListAdapter().getItem(position)).getTitle());
            ((MainActivity)getActivity()).gameClicked(position);
        } else {
            Log.d(LOG_TAG, "SPC Found");
            ((MainActivity)getActivity()).spcClicked();
        }

    }

    public static class ViewHolder {
        public TextView textView;
        public TextView textView2;
    }

    public static MultipleItemsList newInstance(int platformAndType) {

        MultipleItemsList f = new MultipleItemsList();
        Bundle b = new Bundle();
        b.putInt("PlatformAndType", platformAndType);
        f.setArguments(b);

        return f;
    }
}