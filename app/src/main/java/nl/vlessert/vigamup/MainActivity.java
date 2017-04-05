package nl.vlessert.vigamup;

import android.Manifest;
import android.app.ActivityManager;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import nl.vlessert.vigamup.barcode.BarcodeCaptureActivity;

import nl.vlessert.vigamup.PlayerService.MyBinder;

public class MainActivity extends AppCompatActivity implements GameList.OnGameSelectedListener{

    private static final String LOG_TAG = "ViGaMuP";

    private static final int BARCODE_READER_REQUEST_CODE = 1;
    private static final int REQUEST_WRITE_STORAGE = 112;

    private DownloadManager downloadManager;

    private TextView mResultTextView;

    private ImageView gameLogoView;
    private ImageView trackListGameLogoView;

    private SlidingUpPanelLayout mLayout;

    private ListView lv;
    private boolean headerAddedBefore;

    int bufferBarProgress = 0;
    int seekBarThumbProgress = 0;

    String currentShowedGameTitle = "";

    boolean expanded = false;

    private SeekBar seekBar = null;

    private BroadcastReceiver receiver;
    private IntentFilter filter;

    PlayerService mPlayerService;
    boolean mServiceBound = false;

    private boolean initialized = false;

    GameList gameList;

    private GameCollection gameCollection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        setSupportActionBar(toolbar);

        Log.d(LOG_TAG,"HIERRRRRRRRRR");
        Intent startIntent = new Intent(MainActivity.this, PlayerService.class);
        startIntent.setAction(Constants.ACTION.STARTFOREGROUND_ACTION);
        startService(startIntent);
        bindService(startIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        mLayout = (SlidingUpPanelLayout) findViewById(R.id.sliding_layout);
        mLayout.addPanelSlideListener(new SlidingUpPanelLayout.PanelSlideListener() {
            @Override
            public void onPanelSlide(View panel, float slideOffset) {
                /*Window window = MainActivity.this.getWindow();
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                window.setStatusBarColor(ContextCompat.getColor(MainActivity.this.getApplicationContext(), R.color.colorPrimaryDark));*/ // todo....
                //Log.i(LOG_TAG, "onPanelSlide, offset " + slideOffset);
                //ImageView ivImage=(ImageView)findViewById(R.id.logo);
                LinearLayout ivLayout = (LinearLayout) findViewById(R.id.logoLayout);
                if (slideOffset > 0.01 && !expanded) {
                    ivLayout.setVisibility(LinearLayout.GONE);
                    Log.d(LOG_TAG, "hide...");
                    expanded = true;
                }
                if (slideOffset < 0.02 && expanded) {
                    ivLayout.setVisibility(LinearLayout.VISIBLE);
                    Log.d(LOG_TAG, "back...");
                    expanded = false;
                }
            }

            @Override
            public void onPanelStateChanged(View panel, SlidingUpPanelLayout.PanelState previousState, SlidingUpPanelLayout.PanelState newState) {
                //Log.i(LOG_TAG, "onPanelStateChanged " + newState);
            }
        });
        mLayout.setFadeOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
            }
        });

        //gameLogoView = (ImageView) findViewById(R.id.logo);

        seekBar = (SeekBar) findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Notification that the progress level has changed.
                if (progress > bufferBarProgress){
                    seekBar.setProgress(bufferBarProgress); // magic solution, ha
                }
                //Log.d("KSS","seekbar onprogresschanged");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Notification that the user has started a touch gesture.
                //Log.d("KSS","seekbar onStartTrackingTouch");

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Notification that the user has finished a touch gesture.
                //Log.d("KSS","seekbar onStopTrackingTouch: " + seekBar.getProgress());
                mPlayerService.setKssProgressJava(seekBar.getProgress());
                seekBarThumbProgress = seekBar.getProgress();
            }
        });

        gameCollection = new GameCollection(this);

        if (checkForMusicAndInitialize()) {
            gameCollection.createGameCollection();
            showMusicList(gameList);
        }

        filter = new IntentFilter();
        filter.addAction("setBufferBarProgress");
        filter.addAction("setSeekBarThumbProgress");
        filter.addAction("resetSeekBar");
        filter.addAction("setSlidingUpPanelWithGame");
        filter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals("setBufferBarProgress")) setBufferBarProgress(intent.getIntExtra("BUFFERBAR_PROGRESS_SECONDS",0));
                if (intent.getAction().equals("setSeekBarThumbProgress")) setSeekBarThumbProgress(intent.getIntExtra("SEEKBAR_PROGRESS_SECONDS",0));
                if (intent.getAction().equals("resetSeekBar")) {
                    seekBar.setMax(mPlayerService.getCurrentTrackLength());
                    bufferBarProgress = 2; // 2 seconds buffered always in advance...
                    seekBarThumbProgress = 0;
                    seekBar.setProgress(0);
                }
                if (intent.getAction().equals("setSlidingUpPanelWithGame")){
                    Game game = gameCollection.getCurrentGame();
                    seekBar.setMax(game.getCurrentTrackLength());
                    bufferBarProgress = 2; // 2 seconds buffered always in advance...
                    seekBarThumbProgress = 0;
                    seekBar.setProgress(0);
                    LinearLayout ivLayout = (LinearLayout) findViewById(R.id.logoLayout);
                    ivLayout.setVisibility(LinearLayout.GONE);
                    showGame(false);
                }
                if (intent.getAction().equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)){
                    Log.d(LOG_TAG, "Download event!!: " + intent.getAction());
                    unpackDownloadedFile(context, intent);
                }
            }
        };

        registerReceiver(receiver, filter);
        Log.d(LOG_TAG,"create receiver!!");

        downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LOG_TAG,"onResume!!");
        registerReceiver(receiver, filter);
        if (mServiceBound) {
            gameCollection = mPlayerService.gameCollection;
            Game game = gameCollection.getCurrentGame();
            TextView tv = (TextView) findViewById(R.id.playerTopLayoutName);
            tv.setText(Html.fromHtml("<b>"+game.getTitle()+"</b><br/>"+game.getCurrentTrackTitle()));
            seekBar.setMax(game.getCurrentTrackLength());
            bufferBarProgress = 2; // 2 seconds buffered always in advance...
            seekBarThumbProgress = 0;
            seekBar.setProgress(0);
            Log.d(LOG_TAG, "Game in onresume: " + game.title + " " + game.position);
            if (!game.getTitle().equals(currentShowedGameTitle)) showGame(false);
            //LinearLayout ivLayout = (LinearLayout) findViewById(R.id.logoLayout);
            //ViewGroup.LayoutParams params = ivLayout.getLayoutParams();
            //ivLayout.setVisibility(LinearLayout.GONE);
            //params.height = 0;
            //ivLayout.setLayoutParams(params);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(LOG_TAG,"onPause!!");
        try { unregisterReceiver(receiver); } catch (IllegalArgumentException iae){ Log.d(LOG_TAG, "error in onpause: " + iae);}
        try { unbindService(mServiceConnection); } catch (IllegalArgumentException iae){Log.d(LOG_TAG, "error in onpause: " + iae);}
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        seekBar.setOnSeekBarChangeListener(null);
        try { unregisterReceiver(receiver); } catch (IllegalArgumentException iae){ Log.d(LOG_TAG, "error in destroy: " + iae);}
        try { unbindService(mServiceConnection); } catch (IllegalArgumentException iae){Log.d(LOG_TAG, "error in destroy: " + iae);}        //Process.killProcess(Process.myPid());
        mServiceConnection = null;
        Log.d(LOG_TAG,"onDestory!!: " + isMyServiceRunning(PlayerService.class));
    }

    @Override
    protected void onStop(){
        super.onStop();
        Log.d(LOG_TAG,"onStop!!");
        /*try { unregisterReceiver(receiver); } catch (IllegalArgumentException iae){Log.d(LOG_TAG, "error in stop: " + iae);}
        try { unbindService(mServiceConnection); } catch (IllegalArgumentException iae){Log.d(LOG_TAG, "error in stop: " + iae);}*/
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceBound = false;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(LOG_TAG,"service connected!!");
            MyBinder myBinder = (MyBinder) service;
            mPlayerService = myBinder.getService();
            mServiceBound = true;
            if (!mPlayerService.hasGameCollection) mPlayerService.setGameCollection(gameCollection);
            else {
                LinearLayout ivLayout = (LinearLayout) findViewById(R.id.logoLayout);
                ivLayout.setVisibility(LinearLayout.GONE);
                gameCollection = mPlayerService.gameCollection;
                Game game;
                if (mPlayerService.getRandomStatus()) game = gameCollection.getCurrentGame();
                    else game = gameCollection.getCurrentGame();
                seekBar.setMax(game.getCurrentTrackLength());
                bufferBarProgress = 2; // 2 seconds buffered always in advance...
                seekBarThumbProgress = 0;
                seekBar.setProgress(0);
                Log.d(LOG_TAG, "Game in activity: " + game.title + " " + game.position);
                if (!game.getTitle().equals(currentShowedGameTitle)) showGame(false);
            }
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                //Toast.makeText(getApplicationContext(), "Item 1 Selected", Toast.LENGTH_LONG).show();
                return true;
            case R.id.download_music:
                Toast.makeText(getApplicationContext(), "Open a ViGaMuP music collection site on some PC...", Toast.LENGTH_LONG).show();
                downloadMusic();
                return true;
            case R.id.about:
                return true;
            case R.id.exit:
                try {this.getApplicationContext().unbindService(mServiceConnection); } catch (IllegalArgumentException iae){}
                Intent stopIntent = new Intent(MainActivity.this, PlayerService.class);
                stopIntent.setAction(Constants.ACTION.STOPFOREGROUND_ACTION);
                startService(stopIntent);
                try {this.getApplicationContext().unregisterReceiver(receiver); } catch (IllegalArgumentException iae){}
                this.finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == BARCODE_READER_REQUEST_CODE) {
            if (resultCode == CommonStatusCodes.SUCCESS) {
                if (data != null) {
                    Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                    Point[] p = barcode.cornerPoints;
                    Log.d(LOG_TAG, barcode.displayValue);
                    //mResultTextView.setText(barcode.displayValue);
                    Toast.makeText(getApplicationContext(), "Downloading " + barcode.displayValue + "...", Toast.LENGTH_LONG).show();
                    if(barcode.displayValue.contains("__ViGaMuP[")) {
                        Intent intent = getIntent();
                        Uri Download_Uri = Uri.parse(barcode.displayValue);
                        DownloadManager.Request request = new DownloadManager.Request(Download_Uri);
                        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
                        request.setAllowedOverRoaming(false);
                        request.setTitle(intent.getStringExtra(barcode.displayValue));
                        String fileType = barcode.displayValue.substring(barcode.displayValue.indexOf("[") + 1, barcode.displayValue.indexOf("]"));
                        Log.d(LOG_TAG,fileType);
                        if (fileType.equals("KSS")) {
                            String fileName = barcode.displayValue.substring(barcode.displayValue.lastIndexOf("_")+1, barcode.displayValue.length());
                            Log.d(LOG_TAG,"Filename: "+ fileName);
                            if (directoryExists("ViGaMuP/KSS/" + fileName)) deleteDirectory("ViGaMuP/KSS/" + fileName);
                            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"ViGaMuP/KSS/" + fileName);
                            Long downloadReference = downloadManager.enqueue(request);
                        } else {
                            Log.d(LOG_TAG,"Toast to report this is not a ViGaMuP file...");
                        }
                        //Enqueue a new download and same the referenceId
                    } else {
                        Log.d(LOG_TAG,"Toast to report this is not a ViGaMuP file...");
                    }
                }
            } else Log.e(LOG_TAG, String.format(getString(R.string.barcode_error_format),
                    CommonStatusCodes.getStatusCodeString(resultCode)));
        } else super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (mLayout.getPanelState()==SlidingUpPanelLayout.PanelState.EXPANDED) mLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
            else moveTaskToBack(true);
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void unpackDownloadedFile(Context context, Intent intent){
        Log.d(LOG_TAG,"Download event!!!!");
        String action = intent.getAction();
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {

            // get the DownloadManager instance
            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

            DownloadManager.Query q = new DownloadManager.Query();
            Cursor c = manager.query(q);

            if (c.moveToFirst()) {
                int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                Log.d(LOG_TAG,"status: "+status + ", "+DownloadManager.STATUS_FAILED);
                if (status != DownloadManager.STATUS_FAILED) {
                    String name = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
                    Log.i("DOWNLOAD LISTENER", "file name: " + name);
                    File file = new File(name);
                    File targetDirectory = new File(name.substring(0, name.lastIndexOf("/")));
                    try {
                        unzip(file, targetDirectory);
                    } catch (IOException test) {
                        Log.d(LOG_TAG, "Unzip error... very weird");
                    }
                    //finish();
                    //startActivity(getIntent());
                    if (checkForMusicAndInitialize()) {
                        gameCollection.createGameCollection();
                        showMusicList(gameList);
                    }
                } else {
                    Log.i(LOG_TAG, "Download failed!");
                    Toast.makeText(getApplicationContext(), "Download failed... try again", Toast.LENGTH_LONG).show();
                }
            } else {
                Log.i("DOWNLOAD LISTENER", "empty cursor :(");
            }
            c.close();
        }
    }

    public static void unzip(File zipFile, File targetDirectory) throws IOException {
        ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)));
        try {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDirectory, ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs())
                    throw new FileNotFoundException("Failed to ensure directory: " +
                            dir.getAbsolutePath());
                if (ze.isDirectory())
                    continue;
                FileOutputStream fout = new FileOutputStream(file);
                try {
                    while ((count = zis.read(buffer)) != -1)
                        fout.write(buffer, 0, count);
                } finally {
                    fout.close();
                }
            /* if time should be restored as well
            long time = ze.getTime();
            if (time > 0)
                file.setLastModified(time);
            */
            }
        } finally {
            zis.close();
        }
    }

    private boolean checkForMusicAndInitialize(){
        boolean hasPermission = (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);

        if (!hasPermission) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
            return false;
        } else {
            if (directoryExists("ViGaMuP")) {
                if (directoryExists("ViGaMuP/KSS")) {
                    if (dirSize(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/KSS"))> 40000) return true;
                } else {
                    makeDirectory("ViGaMuP/KSS");
                }
            } else {
                makeDirectory("ViGaMuP");
                makeDirectory("ViGaMuP/KSS");
            }
            Toast.makeText(getApplicationContext(), "No music found... open a ViGaMuP music collection site on some PC...", Toast.LENGTH_LONG).show();
            downloadMusic();
            return false;
        }
    }

    private void showMusicList(GameList gameList){
        FragmentManager fragmentManager = this.getSupportFragmentManager();
        gameList = (GameList) fragmentManager.findFragmentById(R.id.fragment1);
        gameList.updateGameList(gameCollection.getGameObjectsArrayList());
    }

    @Override
    public void gameClicked(int position) {
        gameCollection.setCurrentGame(position);
        showGame(true);
    }

    public void showGame(boolean setNewKss) {
        Game game = gameCollection.getCurrentGame();
        Log.d("KSS", "game " + game.getTitle());
        currentShowedGameTitle = game.getTitle();

        TextView tv = (TextView) findViewById(R.id.playerTopLayoutName);
        tv.setText(Html.fromHtml("<b>"+game.getTitle()+"</b><br/>"+game.getCurrentTrackTitle()));

        View header, header2;
        boolean gameLogoImageFound = true;
        BufferedInputStream buffer;

        header = getLayoutInflater().inflate(R.layout.tracklist_header, lv, false);
        trackListGameLogoView = (ImageView) header.findViewById(R.id.tracklistHeader);

        try {
            buffer = new BufferedInputStream(new FileInputStream(game.imageFile.getAbsolutePath()));
            Bitmap bitmap = BitmapFactory.decodeStream(buffer);
            trackListGameLogoView.setImageBitmap(bitmap);
            trackListGameLogoView.setBackgroundColor(Color.parseColor(game.getLogoBackGroundColor()));
            //gameLogoView.setImageBitmap(bitmap);
            //gameLogoView.setBackgroundColor(Color.parseColor(game.getLogoBackGroundColor()));
            Log.d(LOG_TAG, "load image file...");
            Log.d(LOG_TAG, "url: " + game.imageFile.getAbsolutePath());
        } catch (java.io.FileNotFoundException e) {
            gameLogoImageFound = false;
            Log.d(LOG_TAG, "load failed!!");
        }


        header2 = getLayoutInflater().inflate(R.layout.tracklist_textview_header, lv, false);
        TextView tv2 = (TextView) header2.findViewById(R.id.textView1);
        tv2.setText(game.getTitle());

        lv = (ListView) findViewById(R.id.list);

        if ((setNewKss && mServiceBound) || (!initialized && mServiceBound)) {
            mPlayerService.setKssJava(game.musicFileC);
            mPlayerService.stopPlayback();
            initialized = true;
        }
        if (setNewKss && !mServiceBound) {
            Toast.makeText(this, "Service initiating... try again", Toast.LENGTH_LONG).show();
            return;
        }

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //Log.d(LOG_TAG,"setting color on: " + lv.getChildAt(position));
                //lv.getChildAt(position).setBackgroundColor(Color.GREEN);
                //lv.setBackgroundColor(Color.WHITE);
                Game game = gameCollection.getCurrentGame();
                game.setTrack(position-1);

                //new Thread(new Runnable() {
                    //public void run() {
                        //Game game = gameCollection.getCurrentGame();
                        seekBar.setMax(game.getCurrentTrackLength());
                        bufferBarProgress = 2; // 2 seconds buffered always in advance...
                        seekBarThumbProgress = 0;
                        seekBar.setProgress(0);
                        mPlayerService.playCurrentTrack();
                    //}
                //}).start();
            }
        });

        List<String> your_array_list = game.getTrackInformationList();

        if (your_array_list.size()==0) {
            Log.d(LOG_TAG,"No track information... let's generate it!!");
            mPlayerService.generateTrackInformation();
        }

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                your_array_list );

        if (headerAddedBefore) {
            View view = lv.getAdapter().getView(0, null, lv);
            if (view instanceof LinearLayout || view instanceof ImageView){
                if (gameLogoImageFound ) {
                    lv.removeHeaderView(view);
                    lv.addHeaderView(header, null, false);
                }
            }
            if (view instanceof ImageView && !gameLogoImageFound){
                lv.removeHeaderView(view);
                lv.addHeaderView(header2, null, false);
            }
        }

        if (!headerAddedBefore) {
            if (!gameLogoImageFound) lv.addHeaderView(header2, null, false);
                else lv.addHeaderView(header, null, false);
            headerAddedBefore = true;
        }

        lv.setAdapter(arrayAdapter);

        mLayout.setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);

    }

    private boolean directoryExists(String directory) {
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + directory);
        return folder.exists();
    }

    private boolean deleteDirectory(String directory) {
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + directory);
        Log.d(LOG_TAG, "Delete: "+ directory);
        return folder.delete();
    }

    private static long dirSize(File dir) {
        if (dir.exists()) {
            long result = 0;
            File[] fileList = dir.listFiles();
            for(int i = 0; i < fileList.length; i++) {
                // Recursive call if it's a directory
                if(fileList[i].isDirectory()) {
                    result += dirSize(fileList [i]);
                } else {
                    // Sum the file size in bytes
                    result += fileList[i].length();
                }
            }
            return result; // return the file size
        }
        return 0;
    }

    private void makeDirectory(String directory){
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + directory);
        Log.d(LOG_TAG, "create Downloads/"+ directory + "?: " + folder.mkdir());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode)
        {
            case REQUEST_WRITE_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    Log.d(LOG_TAG,"Permission granted!!");
                    finish();
                    startActivity(getIntent());
                } else
                {
                    Toast.makeText(this, "The app was not allowed to write to your storage. Hence, it cannot function properly. Please consider granting it this permission", Toast.LENGTH_LONG).show();
                    checkForMusicAndInitialize();
                }
            }
        }
    }

    private void downloadMusic(){
        Log.d(LOG_TAG,"Initialize barcode scanner...");


        Intent intent = new Intent(getApplicationContext(), BarcodeCaptureActivity.class);
        startActivityForResult(intent, BARCODE_READER_REQUEST_CODE);
    }

    private void setBufferBarProgress(int bufferBarProgress){
        this.bufferBarProgress = bufferBarProgress+2;
        //Log.d(LOG_TAG,"buffer bar should be set to " + bufferBarProgress);
        seekBar.setSecondaryProgress(this.bufferBarProgress);
    }

    private void setSeekBarThumbProgress(int seekBarThumbProgress){
        this.seekBarThumbProgress = seekBarThumbProgress;
        //Log.d(LOG_TAG,"seek bar should be set to " + seekBarThumbProgress);
        seekBar.setProgress(seekBarThumbProgress);
    }
}