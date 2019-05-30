package nl.vlessert.vigamup;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AlertDialog;
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
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class VGMRipsDownloaderActivity extends AppCompatActivity implements SearchView.OnQueryTextListener {

    private RecyclerView recyclerView;
    //private RecyclerView.Adapter mAdapter;
    private VGMRipsDownloaderAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;

    private DownloadManager downloadManager;
    private IntentFilter filter;
    private BroadcastReceiver receiver;

    private JSONArray json;
    private JSONArray previousResults = new JSONArray();
    private String previousSearchedValue = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        JSONObject obj;

        downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

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
                JSONObject obj;

                try {
                    if (previousResults.length()==0) obj = json.getJSONObject(position);
                    else obj = previousResults.getJSONObject(position);
                    Uri Download_Uri2 = Uri.parse(obj.getString("zip_url"));
                    DownloadManager.Request request2 = new DownloadManager.Request(Download_Uri2);
                    request2.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
                    request2.setAllowedOverRoaming(false);
                    request2.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "ViGaMuP/VGM/" + obj.getString("zip").substring(obj.getString("zip").lastIndexOf("/")+1) + ".zip");
                    downloadManager.enqueue(request2);
                    //Log.d("lazyloading", obj.getString("topic_title"));
                    Log.d("Vigamup", "zipurl: " + obj.getString("zip_url"));
                    showDialog();
                    /*holder.title.setText(obj.getString("topic_title"));
                    holder.chip.setText("Chips: "+obj.getString("Sound Chips"));
                    holder.tracks.setText("Tracks: "+obj.getString("Tracks"));
                    holder.length.setText("Length: "+obj.getString("Playing time"));
                    holder.composer.setText("Composer(s): "+obj.getString("Composer"));
                    holder.system.setText("System: "+obj.getString("System"));*/

                } catch (JSONException e){}

                //request2.setTitle(intent.getStringExtra(fileNameImage));

            }
        });

        filter = new IntentFilter();
        filter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d("vigamup", "event!!");
                if (intent.getAction().equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
                    Log.d("Vigamup", "Download event!!: " + intent.getAction());
                    Bundle extras = intent.getExtras();
                    DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                    DownloadManager.Query q = new DownloadManager.Query();
                    q.setFilterById(extras.getLong(DownloadManager.EXTRA_DOWNLOAD_ID));
                    Cursor c = manager.query(q);
                    if (c.moveToFirst()) {
                        String downloadFileLocalUri = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                        String name;
                        if (downloadFileLocalUri != null) {
                            File mFile = new File(Uri.parse(downloadFileLocalUri).getPath());
                            name = mFile.getAbsolutePath();
                            sendResult(name);
                        }
                    }
                }
            }
        };

        registerReceiver(receiver, filter);

    }

    private void showDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Downloading.... please wait...")
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });
        AlertDialog alert = builder.create();

        alert.show();
    }

    private void sendResult(String name) {
        Intent intent2send = new Intent();
        intent2send.putExtra("DownloadedFile",name);
        setResult(1337,intent2send);
        try {
            this.unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) { }
        finish();
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            Toast.makeText(this, "Filename downloaded: " + result.getContents(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        Log.d("lazyloadinstantsearch", "submit!!");
        return false;
    }

    private JSONArray filter(JSONArray array, String searchedValue) {

        if (previousSearchedValue.length() < searchedValue.length() && previousResults.length()!=0) {
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
        Log.d("vigamup", "change!! newText: " + newText);

        JSONArray filtered = filter (json, newText);
        Log.d("vigamup", "filtered.length" + filtered.length() + " " + previousResults.length());
        if (filtered.length() != previousResults.length()) {
            previousResults = filtered;
            mAdapter.updateData(filtered);
        }

        return false;
    }
}
