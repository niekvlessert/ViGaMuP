package nl.vlessert.vigamup;

import android.content.ClipData;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class VGMRipsDownloaderActivity extends AppCompatActivity implements SearchView.OnQueryTextListener {

    private RecyclerView recyclerView;
    //private RecyclerView.Adapter mAdapter;
    private VGMRipsDownloaderAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;


    private JSONArray json;
    private JSONArray previousResults = new JSONArray();
    private String previousSearchedValue = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        JSONObject obj;

        setContentView(R.layout.activity_vgmrips_downloader);
        recyclerView = (RecyclerView) findViewById(R.id.my_recycler_view);
        //recyclerView = findViewById(R.id.vgmrips_downloader_layout);
        // use this setting to
        // improve performance if you know that changes
        // in content do not change the layout size
        // of the RecyclerView
        recyclerView.setHasFixedSize(true);
        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(this);

        //final MenuItem searchItem = mLayoutManager.findViewById(R.id.action_search);
        final SearchView searchView = (SearchView) findViewById(R.id.action_search);
        searchView.setOnQueryTextListener(this);

        recyclerView.setLayoutManager(mLayoutManager);
        //setContentView(R.layout.activity_vgmrips_downloader);

        final List<String> input = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            input.add("Test" + i);
        }

        // define an adapter
        try {
            json = new JSONArray(loadJSONFromAsset());
            mAdapter = new VGMRipsDownloaderAdapter(getApplicationContext(), json);
        } catch (JSONException e){ e.printStackTrace();}

        recyclerView.setAdapter(mAdapter);
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(40);
        recyclerView.setDrawingCacheEnabled(true);
        recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(), 1);
        recyclerView.addItemDecoration(dividerItemDecoration);

        ItemTouchHelper.SimpleCallback simpleItemTouchCallback =
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
                    @Override
                    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder
                            target) {
                        return false;
                    }
                    @Override
                    public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
                        //input.remove(viewHolder.getAdapterPosition());
                        //mAdapter.notifyItemRemoved(viewHolder.getAdapterPosition());
                    }

                    @Override
                    public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                        int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
                        int swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;
                        return makeMovementFlags(0, 0);
                    }
                };
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleItemTouchCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        ItemClickSupport.addTo(recyclerView).setOnItemClickListener(new ItemClickSupport.OnItemClickListener() {
            @Override
            public void onItemClicked(RecyclerView recyclerView, int position, View v) {
                Log.d("ViGaMuP", "blerp");
                // do it
            }
        });

    }

    public String loadJSONFromAsset() {
        String json;
        try {
            InputStream is = getAssets().open("vgmrips_full.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //getMenuInflater().inflate(R.menu.vgmrips_downloader_menu_main, menu);

        final MenuItem searchItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        searchView.setOnQueryTextListener(this);

        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        Log.d("lazyloadinstantsearch", "submit!!");
        return false;
    }

    private JSONArray filter(JSONArray array, String searchedValue) {

        if (previousSearchedValue.length() < searchedValue.length()) {
            array = previousResults;
            Log.d("hmmm", "longer value!!");
        }
        previousSearchedValue = searchedValue;

        JSONArray results = new JSONArray();
        String[] values = searchedValue.split("\\s+");

        try {
            for (String s : values) {
                for (int i = 0; i < array.length(); ++i) {
                    JSONObject obj = array.getJSONObject(i);
                    if (obj.toString().contains(s)) {
                        results.put(obj);
                    }
                }
                array = results;
                results = new JSONArray();
            }
        } catch (JSONException e) {
            // handle exception
        }

        return array;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        //Log.d("lazyloadinstantsearch", "change!!");

        JSONArray filtered = filter (json, newText);
        if (filtered.length() != previousResults.length()) {
            previousResults = filtered;
            mAdapter.updateData(filtered);
        }

        return false;
    }
}
