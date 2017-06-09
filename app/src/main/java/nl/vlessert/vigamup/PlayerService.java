package nl.vlessert.vigamup;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import java.io.File;

public class PlayerService extends Service{
    private static final String NOTIFICATION_DELETED_ACTION = "NOTIFICATION_DELETED";

    private static final String LOG_TAG = "KSS PlayerService";
    private IBinder mBinder = new MyBinder();
    private boolean alreadyRunning = false;
    NotificationCompat.Builder mNotificationCompatBuilder;
    Notification notification;

    private boolean kssSet = false, kssTrackSet = false, notificationPlaying = false;
    private boolean paused = true;
    public boolean hasGameCollection = false;

    public GameCollection gameCollection;

    PendingIntent ppreviousIntent;
    PendingIntent pplayIntent;
    PendingIntent pnextIntent;
    PendingIntent prepeatIntent;

    MediaSession mMediaSession;

    IntentFilter filter;

    RemoteViews views, bigViews;

    int repeatMode = Constants.REPEAT_MODES.NORMAL_PLAYBACK;

    private AudioFocusChangeListenerImpl mAudioFocusChangeListener;
    private boolean mFocusGranted, mFocusChanged;
    private boolean transientlostbefore = false;

    int secondsPlayedFromCurrentTrack = 0;
    int secondsBufferedFromCurrentTrack = 0;

    private boolean randomizer = false;
    private Game currentGame;

    private boolean justStarted = true;
    private boolean alreadyPlaying = false;

    private String currentFile = "";
    private String currentLogoFile = "";
    private int currentBigViewType = Constants.BIG_VIEW_TYPES.SQUARE;

    static {
        System.loadLibrary("kss_player");
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {


        //Log.i(LOG_TAG, "Received Intent " + intent.getAction());
        if (intent==null) {
            //Log.d("KSS","Intent empty... no idea why");
            return START_NOT_STICKY;
        }
        if (intent.getAction().equals(Constants.ACTION.STARTFOREGROUND_ACTION) && !alreadyRunning) {
            alreadyRunning = true;

            createEngine();
            int sampleRate = 0;
            int bufSize = 0;

            AudioManager myAudioMgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            mAudioFocusChangeListener = new AudioFocusChangeListenerImpl();
            int result = myAudioMgr.requestAudioFocus(mAudioFocusChangeListener,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

            switch (result) {
                case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                    mFocusGranted = true;
                    break;
                case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                    mFocusGranted = false;
                    break;
            }

            String message = "Focus request " + (mFocusGranted ? "granted" : "failed");
            Log.i(LOG_TAG, message);

            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
                sampleRate = Integer.parseInt(myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));
                bufSize = Integer.parseInt(myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
                Log.d(LOG_TAG, "samplerate " + sampleRate + "bufzei " + bufSize);
            } else {
                sampleRate = 44100;
                bufSize = 240;
            }


            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                if (mMediaSession == null) {
                    Log.d("init()", "API " + Build.VERSION.SDK_INT + " greater or equals " + Build.VERSION_CODES.LOLLIPOP);
                    Log.d("init()", "Using MediaSession API.");

                    mMediaSession = new MediaSession(this, "PlayerServiceMediaSession");
                    mMediaSession.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
                    mMediaSession.setActive(true);
                    catchMediaPlayerButtons();
                }

                updateA2DPPlayState(false);
            }
            createBufferQueueAudioPlayer(sampleRate, bufSize);

            if (!hasGameCollection){
                createGameCollection();
                hasGameCollection = true;
            }
            createNotification(Constants.BIG_VIEW_TYPES.SQUARE);

        } else if (intent.getAction().equals(Constants.ACTION.PREV_ACTION)) {
            Log.i(LOG_TAG, "Clicked Previous");
            previousTrack();
        } else if (intent.getAction().equals(Constants.ACTION.PLAY_ACTION)) {
            Log.i(LOG_TAG, "Clicked Play/Pause");
            togglePlaybackJava();
        } else if (intent.getAction().equals(Constants.ACTION.NEXT_ACTION)) {
            Log.i(LOG_TAG, "Clicked Next");
            nextTrack();
        } else if (intent.getAction().equals(Constants.ACTION.REPEAT_ACTION)) {
            Log.i(LOG_TAG, "Clicked Repeat");
            repeatActivator();
        } else if (intent.getAction().equals(
                Constants.ACTION.STOPFOREGROUND_ACTION)) {
            Log.i(LOG_TAG, "Received Stop Foreground Intent");
            stopService(intent);
            stopForeground(true);
            stopSelf();

            shutdown();
        }

        filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.registerReceiver(mReceiver, filter);

        return START_STICKY;
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            sendBroadcast(new Intent("shutdownActivity"));
            Intent stopIntent = new Intent(PlayerService.this, PlayerService.class);
            stopIntent.setAction(Constants.ACTION.STOPFOREGROUND_ACTION);
            stopService(stopIntent);
            stopForeground(true);
            unregisterReceiver(this);
            stopSelf();

            shutdown();
        }
    };

    //The BroadcastReceiver that listens for bluetooth broadcasts
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                Log.d(LOG_TAG, "Device found"); //
            }
            else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                Log.d(LOG_TAG, "Device connected");
                if (paused) updateA2DPPlayState(false); // car audio systeme will start playback (in my case...)
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(LOG_TAG, "Done searching"); //
            }
            else if (BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action)) {
                Log.d(LOG_TAG, "Device about disconnect");
            }
            else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                Log.d(LOG_TAG, "Disconnect...");
                if (!paused) togglePlaybackJava();
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(receiver);} catch (IllegalArgumentException iae){}
        try { unregisterReceiver(mReceiver);} catch (IllegalArgumentException iae){}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mMediaSession!=null) mMediaSession.release();
        }
        //shutdown();
        Log.i(LOG_TAG, "In onDestroy");
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(LOG_TAG, "in onBind");
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.v(LOG_TAG, "in onRebind");
        Intent seekBarIntent = new Intent("setBufferBarProgress");
        seekBarIntent.putExtra("BUFFERBAR_PROGRESS_SECONDS",secondsBufferedFromCurrentTrack);
        sendBroadcast(seekBarIntent);
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.v(LOG_TAG, "in onUnbind");
        return true;
    }

    public class MyBinder extends Binder {
        PlayerService getService() {
            return PlayerService.this;
        }
    }

    private class AudioFocusChangeListenerImpl implements AudioManager.OnAudioFocusChangeListener {

        @Override
        public void onAudioFocusChange(int focusChange) {
            mFocusChanged = true;
            Log.i(LOG_TAG, "Focus changed");

            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    Log.i(LOG_TAG, "AUDIOFOCUS_GAIN");
                    if (!paused && transientlostbefore) {
                        togglePlayback();
                        transientlostbefore = false;
                        setPlayingStatePlaying();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.i(LOG_TAG, "AUDIOFOCUS_LOSS");
                    if (!paused) {
                        togglePlayback();
                        setPlayingStatePause();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Log.i(LOG_TAG, "AUDIOFOCUS_LOSS_TRANSIENT");
                    if (!paused) {
                        togglePlayback();
                        setPlayingStatePause();
                    }
                    transientlostbefore = true;
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    Log.i(LOG_TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                    break;
            }
        }
    }

    public boolean isPaused(){ return paused; }

    private void createNotification(int bigViewType){
        currentBigViewType = bigViewType;
        //Log.d(LOG_TAG, "hmmm: " +currentBigViewType);

        Log.i(LOG_TAG, "Received Start Foreground Intent ");
        Intent notificationIntent = new Intent(this, MainActivity.class);

        Intent intent = new Intent(NOTIFICATION_DELETED_ACTION);
        PendingIntent pendingIntent2 = PendingIntent.getBroadcast(this, 0, intent, 0);
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_DELETED_ACTION));

        notificationIntent.setAction(Constants.ACTION.MAIN_ACTION);

        views = new RemoteViews(getPackageName(), R.layout.custom_notification);
        if (bigViewType == Constants.BIG_VIEW_TYPES.STRETCHED) {
            bigViews = new RemoteViews(getPackageName(), R.layout.custom_notification_expanded_stretched_image);
        } else {
            if (bigViewType == Constants.BIG_VIEW_TYPES.RECTANGULAR) {
                bigViews = new RemoteViews(getPackageName(), R.layout.custom_notification_expanded_rectangular_image);
            } else {
                bigViews = new RemoteViews(getPackageName(), R.layout.custom_notification_expanded_square_image);
            }
        }
        //views.setViewVisibility(R.id.status_bar_icon, View.VISIBLE);
        //views.setViewVisibility(R.id.status_bar_album_art, View.GONE);
        views.setImageViewBitmap(R.id.status_bar_album_art, BitmapFactory.decodeResource(getResources(), R.drawable.music_note_transparant_tiny));
        bigViews.setImageViewBitmap(R.id.status_bar_album_art, BitmapFactory.decodeResource(getResources(), R.drawable.music_note_transparant));

        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        Intent previousIntent = new Intent(this, PlayerService.class);
        previousIntent.setAction(Constants.ACTION.PREV_ACTION);
        ppreviousIntent = PendingIntent.getService(this, 0,
                previousIntent, 0);

        Intent playIntent = new Intent(this, PlayerService.class);
        playIntent.setAction(Constants.ACTION.PLAY_ACTION);
        pplayIntent = PendingIntent.getService(this, 0,
                playIntent, 0);

        Intent nextIntent = new Intent(this, PlayerService.class);
        nextIntent.setAction(Constants.ACTION.NEXT_ACTION);
        pnextIntent = PendingIntent.getService(this, 0,
                nextIntent, 0);

        Intent repeatIntent = new Intent(this, PlayerService.class);
        repeatIntent.setAction(Constants.ACTION.REPEAT_ACTION);
        prepeatIntent = PendingIntent.getService(this, 0,
                repeatIntent, 0);

        views.setOnClickPendingIntent(R.id.status_bar_play, pplayIntent);
        bigViews.setOnClickPendingIntent(R.id.status_bar_play, pplayIntent);

        views.setOnClickPendingIntent(R.id.status_bar_next, pnextIntent);
        bigViews.setOnClickPendingIntent(R.id.status_bar_next, pnextIntent);

        //views.setOnClickPendingIntent(R.id.status_bar_prev, ppreviousIntent);
        bigViews.setOnClickPendingIntent(R.id.status_bar_prev, ppreviousIntent);

        bigViews.setOnClickPendingIntent(R.id.status_bar_repeat, prepeatIntent);

        views.setImageViewResource(R.id.status_bar_play,
                android.R.drawable.ic_media_play);
        bigViews.setImageViewResource(R.id.status_bar_play,
                android.R.drawable.ic_media_play);

        views.setTextViewText(R.id.status_bar_track_name, "");
        bigViews.setTextViewText(R.id.status_bar_track_name, "");

        views.setTextViewText(R.id.status_bar_artist_name, "");
        bigViews.setTextViewText(R.id.status_bar_artist_name, "");

        bigViews.setTextViewText(R.id.status_bar_album_name, "");

        mNotificationCompatBuilder = new NotificationCompat.Builder(this)
                .setContentTitle("")
                .setTicker("")
                .setContentText("")
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.music_note_transparant_tiny)
                .setLargeIcon(
                        Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.music_note_transparant), 128, 128, false))
                .setContentIntent(pendingIntent)
                //.setOngoing(true)
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_media_previous,
                        "", ppreviousIntent)
                .addAction(android.R.drawable.ic_media_play, "",
                        pplayIntent)
                .addAction(android.R.drawable.ic_media_next, "",
                        pnextIntent)
                .addAction(R.drawable.img_btn_repeat, "", prepeatIntent)
                .setContent(views)
                .setDeleteIntent(pendingIntent2)
                .setCustomBigContentView(bigViews);

        notification = mNotificationCompatBuilder.build();

        int repeatModeIcon = R.drawable.img_btn_repeat;
        //Log.d(LOG_TAG, "repeatmode: " + repeatMode);
        switch(repeatMode) {
            case Constants.REPEAT_MODES.NORMAL_PLAYBACK:
                repeatModeIcon = R.drawable.img_btn_repeat;
                break;
            case Constants.REPEAT_MODES.LOOP_GAME:
            case Constants.REPEAT_MODES.LOOP_TRACK:
                repeatModeIcon = R.drawable.img_btn_repeat_pressed;
                break;
            case Constants.REPEAT_MODES.SHUFFLE_IN_GAME:
            case Constants.REPEAT_MODES.SHUFFLE_IN_PLATFORM:
                repeatModeIcon = R.drawable.img_btn_shuffle_pressed;
                break;
        }

        bigViews.setImageViewResource(R.id.status_bar_repeat, repeatModeIcon);

        startForeground(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE,
                notification);
    }

    public void setPlayingStatePlaying() {
        //Thread t = new Thread(new Runnable() {
            //public void run() {
                NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

                views.setImageViewResource(R.id.status_bar_play,
                        android.R.drawable.ic_media_pause);
                bigViews.setImageViewResource(R.id.status_bar_play,
                        android.R.drawable.ic_media_pause);

                Log.d(LOG_TAG, "setPlayingStatePlaying");

                mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
                notificationPlaying = true;

                updateA2DPPlayState(true);
            //}
        //});

        //t.start();
        //try { t.join(); } catch (InterruptedException ie){}
    }

    public void setPlayingStatePause() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        views.setImageViewResource(R.id.status_bar_play,
                android.R.drawable.ic_media_play);
        bigViews.setImageViewResource(R.id.status_bar_play,
                android.R.drawable.ic_media_play);

        Log.d(LOG_TAG, "setPlayingStatePause");

        mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
        notificationPlaying = false;

        updateA2DPPlayState(false);
    }

    public void updateNotificationTitles(){
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Game game = gameCollection.getCurrentGame();

        File file = new File(game.imageFile.getAbsolutePath());
                //trackListGameLogoView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.music_note_transparant));

        if (!currentLogoFile.equals(game.imageFile.getAbsolutePath())) {
            currentLogoFile = game.imageFile.getAbsolutePath();
            if (file.exists()) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(game.imageFile.getAbsolutePath(), options);
                int width = options.outWidth;
                int height = options.outHeight;
                Log.d(LOG_TAG, "width: " + width + " height: " + height + " currentBigViewType: " + currentBigViewType);
                if (width/height > 4) {
                    if (currentBigViewType != Constants.BIG_VIEW_TYPES.STRETCHED)
                        createNotification(Constants.BIG_VIEW_TYPES.STRETCHED);
                } else {
                    if ((width / height <= 4) && (width / height > 1.2)) {
                        Log.d(LOG_TAG, "setting rectangular!");
                        if (currentBigViewType != Constants.BIG_VIEW_TYPES.RECTANGULAR) {
                            createNotification(Constants.BIG_VIEW_TYPES.RECTANGULAR);
                        }
                    } else {
                        if (currentBigViewType != Constants.BIG_VIEW_TYPES.SQUARE) {
                            createNotification(Constants.BIG_VIEW_TYPES.SQUARE);
                        }
                    }
                }

                Bitmap image = BitmapFactory.decodeFile(game.imageFile.getAbsolutePath());
                if (game.getLogoBackGroundColor()!="black") {
                    Bitmap newBitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), image.getConfig());
                    Canvas canvas = new Canvas(newBitmap);
                    canvas.drawColor(Color.WHITE);
                    canvas.drawBitmap(image, 0, 0, null);
                    views.setImageViewBitmap(R.id.status_bar_album_art, newBitmap);
                    bigViews.setImageViewBitmap(R.id.status_bar_album_art, newBitmap);
                } else {
                    views.setImageViewBitmap(R.id.status_bar_album_art, image);
                    bigViews.setImageViewBitmap(R.id.status_bar_album_art, image);
                }
            } else {
                views.setImageViewBitmap(R.id.status_bar_album_art, Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.music_note_transparant), 128, 128, false));
                bigViews.setImageViewBitmap(R.id.status_bar_album_art, Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.music_note_transparant), 128, 128, false));
            }
            if (!paused) setPlayingStatePlaying();
        }

        views.setTextViewText(R.id.status_bar_track_name, game.getCurrentTrackTitle());
        bigViews.setTextViewText(R.id.status_bar_track_name, game.getCurrentTrackTitle());

        views.setTextViewText(R.id.status_bar_artist_name, game.getTitle());
        bigViews.setTextViewText(R.id.status_bar_artist_name, game.getTitle());

        bigViews.setTextViewText(R.id.status_bar_album_name, game.getVendor());

        mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
    }

    public void createGameCollection(){
        Log.d(LOG_TAG,"has game collection?: " +hasGameCollection);
        if (!hasGameCollection) {
            gameCollection = new GameCollection(this);
            gameCollection.createGameCollection();
            hasGameCollection = true;

            Intent startIntent = new Intent();
            startIntent.setAction(Constants.ACTION.STARTFOREGROUND_ACTION);
            Log.d(LOG_TAG, "starting service...");

            onStartCommand(startIntent, 0, 0);
        }
    }

    private void nextTrack() {
        fixUninitialized();

        //Log.d(LOG_TAG,"Next track called..." + Debug.getNativeHeapAllocatedSize() + " and " + Debug.getNativeHeapSize());
        //Log.d(LOG_TAG,"game: " + currentGame.getTitle());

        switch (repeatMode) {
            case Constants.REPEAT_MODES.NORMAL_PLAYBACK:
            case Constants.REPEAT_MODES.LOOP_TRACK:
                if (!currentGame.setNextTrack()) {
                    gameCollection.setNextGame();
                    currentGame = gameCollection.getCurrentGame();
                    currentGame.setFirstTrack();
                    Log.d(LOG_TAG, "Game in service: " + currentGame.gameName);
                    if (currentGame.getMusicType()==0) setKssJava(currentGame.musicFileC);
                }
                break;
            case Constants.REPEAT_MODES.LOOP_GAME:
                currentGame.setNextTrack();
                break;
            case Constants.REPEAT_MODES.SHUFFLE_IN_GAME:
                GameTrack gt = currentGame.getNextRandomTrack();
                Log.d(LOG_TAG,"tracknr. : " + gt.getTrackNr() + "track position " + gt.getPosition());
                currentGame.setTrack(gt.getPosition());
                break;
            case Constants.REPEAT_MODES.SHUFFLE_IN_PLATFORM:
                justStarted = false;
                String gameAndTrackInfo = gameCollection.getNextRandomGameAndTrack();
                String[] gameAndTrackInfoArray = gameAndTrackInfo.split(",");
                gameCollection.setCurrentGame(Integer.parseInt(gameAndTrackInfoArray[0]));
                currentGame = gameCollection.getCurrentGame();
                currentGame.setTrack(Integer.parseInt(gameAndTrackInfoArray[1]));
                if (currentGame.getMusicType()==0) setKssJava(currentGame.musicFileC);
                Log.d(LOG_TAG,"gameAndTrackInfo: " + gameAndTrackInfo);
                break;
        }

        sendBroadcast(new Intent("resetSeekBar"));

        Log.d(LOG_TAG, "Randomizer: " + randomizer);
        Log.d(LOG_TAG, "getmusictype: " + currentGame.getMusicType());

        switch (currentGame.getMusicType()){
            case 0:
                setKssTrackJava(currentGame.getCurrentTrackNumber(), currentGame.getCurrentTrackLength());
                if (!paused) {
                    alreadyPlaying = true;
                    startKssPlayback();
                }
                break;
            case 1:
                secondsPlayedFromCurrentTrack = 0;
                secondsBufferedFromCurrentTrack = 0;
                currentGame.extractCurrentSpcTrackfromRSN();
                startSpcPlayback(currentGame.getCurrentTrackFileNameFullPath(), currentGame.getCurrentTrackLength());
                break;
        }

        Intent intent = new Intent("setSlidingUpPanelWithGame");
        sendBroadcast(intent);

        updateNotificationTitles();

        updateA2DPInfo();
    }

    private void previousTrack() {
        fixUninitialized();

        switch (repeatMode) {
            case Constants.REPEAT_MODES.NORMAL_PLAYBACK:
            case Constants.REPEAT_MODES.LOOP_TRACK:
                if (!currentGame.setPreviousTrack()) {
                    gameCollection.setPreviousGame();
                    currentGame = gameCollection.getCurrentGame();
                    currentGame.setLastTrack();
                    Log.d(LOG_TAG, "Game in service: " + currentGame.title + " " + currentGame.position);
                    if (currentGame.getMusicType()==0) setKssJava(currentGame.musicFileC);
                }
                break;
            case Constants.REPEAT_MODES.LOOP_GAME:
                currentGame.setPreviousTrack();
                break;
            case Constants.REPEAT_MODES.SHUFFLE_IN_GAME:
                GameTrack gt = currentGame.getPreviousRandomTrack();
                currentGame.setTrack(gt.getPosition());
                break;
            case Constants.REPEAT_MODES.SHUFFLE_IN_PLATFORM:
                justStarted = false;
                String gameAndTrackInfo = gameCollection.getPreviousRandomGameAndTrack();
                String[] gameAndTrackInfoArray = gameAndTrackInfo.split(",");
                gameCollection.setCurrentGame(Integer.parseInt(gameAndTrackInfoArray[0]));
                currentGame = gameCollection.getCurrentGame();
                currentGame.setTrack(Integer.parseInt(gameAndTrackInfoArray[1]));
                if (currentGame.getMusicType()==0) setKssJava(currentGame.musicFileC);
                Log.d(LOG_TAG,"gameAndTrackInfo: " + gameAndTrackInfo);
                break;
        }

        Intent intent = new Intent("setSlidingUpPanelWithGame");
        sendBroadcast(new Intent("resetSeekBar"));

        switch (currentGame.getMusicType()){
            case 0:
                setKssTrackJava(currentGame.getCurrentTrackNumber(), currentGame.getCurrentTrackLength());
                if (!paused) {
                    alreadyPlaying = true;
                    startKssPlayback();
                }
                break;
            case 1:
                secondsPlayedFromCurrentTrack = 0;
                secondsBufferedFromCurrentTrack = 0;
                currentGame.extractCurrentSpcTrackfromRSN();
                startSpcPlayback(currentGame.getCurrentTrackFileNameFullPath(), currentGame.getCurrentTrackLength());
                break;
        }

        sendBroadcast(intent);
        updateNotificationTitles();
        updateA2DPInfo();
    }

    public void playCurrentTrack() {
        Game game = gameCollection.getCurrentGame();
        switch (game.getMusicType()){
            case 0:
                setKssTrackJava(game.getCurrentTrackNumber(), game.getCurrentTrackLength());
                startKssPlayback();
                break;
            case 1:
                game.extractCurrentSpcTrackfromRSN();
                startSpcPlayback(game.getCurrentTrackFileNameFullPath(), game.getCurrentTrackLength());
                break;
        }
        alreadyPlaying = true;
        updateNotificationTitles();
        updateA2DPInfo();
        setPlayingStatePlaying();
        if (paused) paused = togglePlayback();
    }

    public void playSpc(String fileName){
        Game game = gameCollection.getCurrentGame();
        secondsPlayedFromCurrentTrack = 0;
        secondsBufferedFromCurrentTrack = 0;

        //game.extractCurrentSpcTrackfromRSN();
        startSpcPlayback(fileName, game.getCurrentTrackLength());

        updateNotificationTitles();
        updateA2DPInfo();
        setPlayingStatePlaying();

        if (paused) paused = togglePlayback();
    }

    public int getCurrentTrackLength(){
        return gameCollection.getCurrentGame().getCurrentTrackLength();
    }

    public void setKssJava(String file){
        kssSet = true;
        kssTrackSet = false;
        if (!currentFile.equals(file)) {
            setKss(file);
            currentFile = file;
        }
    }

    public void setKssTrackJava(int track, int length){
        Log.d(LOG_TAG, "track: " + track + " length: " + length);
        kssTrackSet = true;
        secondsPlayedFromCurrentTrack = 0;
        secondsBufferedFromCurrentTrack = 0;
        //alreadyPlaying = false;
        setKssTrack(track, length);
    }

    public void startPlayback(){
        if (paused) {
            togglePlayback();
            updateA2DPInfo();
            updateNotificationTitles();
            setPlayingStatePlaying();
            paused = false;
        }
    }

    public void stopPlayback(){
        if (!paused) {
            togglePlayback();
            setPlayingStatePause();
            paused = true;
        }
    }
    private void fixUninitialized(){
        if (!hasGameCollection) createGameCollection();
        currentGame = gameCollection.getCurrentGame();
        switch (currentGame.getMusicType()) {
            case 0:
                if (!kssSet) {
                    setKssJava(currentGame.musicFileC);
                    setKssTrackJava(currentGame.getCurrentTrackNumber(), currentGame.getCurrentTrackLength());
                }
                if (!kssTrackSet) {
                    setKssTrackJava(currentGame.getCurrentTrackNumber(), currentGame.getCurrentTrackLength());
                }
                break;
        }
    }

    public void togglePlaybackJava(){
        fixUninitialized();
        Game game = gameCollection.getCurrentGame();

        if (repeatMode == Constants.REPEAT_MODES.SHUFFLE_IN_PLATFORM && justStarted){
            nextTrack();
            justStarted = false;
        }

        Log.d(LOG_TAG,"alreadyPlaying...: " + alreadyPlaying + ", Musictype: " + game.getMusicType());

        if (!alreadyPlaying) {
            switch (game.getMusicType()){
                case 0:
                    startKssPlayback();
                    break;
                case 1:
                    game.extractCurrentSpcTrackfromRSN();
                    startSpcPlayback(game.getCurrentTrackFileNameFullPath(), game.getCurrentTrackLength());
                    break;
            }
            alreadyPlaying = true;
        }

        paused = togglePlayback();
        if (paused) setPlayingStatePause(); else setPlayingStatePlaying();
        Log.d(LOG_TAG,"togglePlaybackJava...: " + notificationPlaying);

        updateNotificationTitles();
        updateA2DPInfo();
        if (!paused) sendBroadcast(new Intent("setSlidingUpPanelWithGame"));
        sendBroadcast(new Intent("setPlayButtonInPlayerBar"));
        if (paused) this.stopForeground(false);
            else startForeground(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE,
                notification);
    }

    private void repeatActivator(){
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Game game = gameCollection.getCurrentGame();
        Handler handler = new Handler();
        switch(repeatMode) {
            case Constants.REPEAT_MODES.NORMAL_PLAYBACK :
                randomizer = false;
                bigViews.setImageViewResource(R.id.status_bar_repeat,
                        R.drawable.img_btn_repeat_pressed);
                bigViews.setTextViewText(R.id.status_bar_track_name,"Repeat 1: loop current track (if supported, from next track)");
                repeatMode = Constants.REPEAT_MODES.LOOP_TRACK;
                mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
                toggleLoopTrack();
                handler.postDelayed(new MyRunnable(game), 3000);
                break;
            case Constants.REPEAT_MODES.LOOP_TRACK :
                repeatMode = Constants.REPEAT_MODES.LOOP_GAME;
                bigViews.setTextViewText(R.id.status_bar_track_name,"Repeat 2: loop current game/collection");
                toggleLoopTrack();
                mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
                handler.postDelayed(new MyRunnable(game), 3000);
                break;
            case Constants.REPEAT_MODES.LOOP_GAME :
                randomizer = true;
                repeatMode = Constants.REPEAT_MODES.SHUFFLE_IN_GAME;
                bigViews.setImageViewResource(R.id.status_bar_repeat,
                        R.drawable.img_btn_shuffle_pressed);
                bigViews.setTextViewText(R.id.status_bar_track_name,"Shuffle 1: shuffle current game/collection");
                mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
                handler.postDelayed(new MyRunnable(game), 3000);
                break;
            case Constants.REPEAT_MODES.SHUFFLE_IN_GAME :
                repeatMode = Constants.REPEAT_MODES.SHUFFLE_IN_PLATFORM;
                bigViews.setTextViewText(R.id.status_bar_track_name,"Shuffle 2: shuffle everything");
                mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
                handler.postDelayed(new MyRunnable(game), 3000);
                break;
            case Constants.REPEAT_MODES.SHUFFLE_IN_PLATFORM :
                randomizer = false;
                repeatMode = Constants.REPEAT_MODES.NORMAL_PLAYBACK;
                bigViews.setImageViewResource(R.id.status_bar_repeat,
                        R.drawable.img_btn_repeat);
                mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
                break;
        }
        /*Log.d(LOG_TAG, "repeat: " + repeatMode);
        Log.d(LOG_TAG, "Randomizer in repeatActivator: " + randomizer);*/
    }

    private class MyRunnable implements Runnable {
        private Game game;
        private MyRunnable(Game game) {
            this.game = game;
        }

        @Override
        public void run() {
            NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            bigViews.setTextViewText(R.id.status_bar_track_name,game.getCurrentTrackTitle());
            mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
        }
    }

    public void updateA2DPInfo(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Game game = gameCollection.getCurrentGame();
            MediaMetadata metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, game.getCurrentTrackTitle())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, game.getVendor())
                .putString(MediaMetadata.METADATA_KEY_ALBUM, game.getTitle())
                .putLong(MediaMetadata.METADATA_KEY_DURATION, game.getCurrentTrackLength()*1000)
                .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, game.getCurrentTrackNumber())
                .build();

            mMediaSession.setMetadata(metadata);
        }
    }

    private void updateA2DPPlayState(boolean play){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (play) {
                PlaybackState state = new PlaybackState.Builder()
                        .setActions(
                                PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE |
                                        PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_PAUSE |
                                        PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                        .setState(PlaybackState.STATE_PLAYING, secondsPlayedFromCurrentTrack * 1000, 1.0f, SystemClock.elapsedRealtime())
                        .build();
                mMediaSession.setPlaybackState(state);
            } else {
                PlaybackState state = new PlaybackState.Builder()
                        .setActions(
                                PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE |
                                        PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_PAUSE |
                                        PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                        .setState(PlaybackState.STATE_PAUSED, secondsPlayedFromCurrentTrack * 1000, 1.0f, 0)
                        .build();
                mMediaSession.setPlaybackState(state);
            }
        }
    }

    private void catchMediaPlayerButtons() {
        //capture media events like play, stop
        //you don't actually use these callbacks
        //but you have to have this in order to pretend to be a media application
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mMediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            mMediaSession.setCallback(new MediaSession.Callback() {
                @Override
                public void onPlay() {
                    //Toast.makeText(getApplicationContext(), "Play!!", Toast.LENGTH_LONG).show();
                    togglePlaybackJava();
                    super.onPlay();
                }

                @Override
                public void onPause() {
                    //Toast.makeText(getApplicationContext(), "Pause!!", Toast.LENGTH_LONG).show();
                    togglePlaybackJava();
                    super.onPause();
                }

                @Override
                public void onSkipToNext() {
                    //Toast.makeText(getApplicationContext(), "Next!!", Toast.LENGTH_LONG).show();
                    nextTrack();
                    super.onSkipToNext();
                }

                @Override
                public void onSkipToPrevious() {
                    //Toast.makeText(getApplicationContext(), "Previous!!", Toast.LENGTH_LONG).show();
                    previousTrack();
                    super.onSkipToPrevious();
                }

                @Override
                public void onStop() {
                    stopPlayback();
                    kssSet = false;
                    kssTrackSet = false;
                    notificationPlaying = false;
                    paused = true;
                    //Toast.makeText(getApplicationContext(), "Stop!!", Toast.LENGTH_LONG).show();
                    super.onStop();
                }
            });
        }
    }

    private void setBufferBarProgress() {
        secondsBufferedFromCurrentTrack++;
        Intent intent = new Intent("setBufferBarProgress");
        intent.putExtra("BUFFERBAR_PROGRESS_SECONDS",secondsBufferedFromCurrentTrack);
        sendBroadcast(intent);
    }

    public int getBufferBarProgress() {
        return secondsBufferedFromCurrentTrack;
    }

    private void setSeekBarThumbProgress(){
        secondsPlayedFromCurrentTrack++;
        Intent intent = new Intent("setSeekBarThumbProgress");
        intent.putExtra("SEEKBAR_PROGRESS_SECONDS",secondsPlayedFromCurrentTrack);
        sendBroadcast(intent);
    }

    public void setKssProgressJava(int progress){
        secondsPlayedFromCurrentTrack = progress;
        updateA2DPPlayState(true);
        setKssProgress(progress);
    }

    public Game getCurrentGame(){
        return currentGame;
    }


    public boolean getPaused(){
        return paused;
    }

    public native void createEngine();
    public static native void createBufferQueueAudioPlayer(int sampleRate, int samplesPerBuf);
    public static native void setKss(String file);
    public static native void setKssTrack(int track, int length);
    public static native void startKssPlayback();
    public static native void startSpcPlayback(String fileName, int length);
    public static native boolean togglePlayback();
    public static native boolean toggleLoopTrack();
    public native void setKssProgress(int progress);
    public native void shutdown();

    public native void generateTrackInformation();
    public native String generateSpcTrackInformation(String spcFile);

}