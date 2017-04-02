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

public class PlayerService extends Service{
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
    private boolean fromActivity = false;

    private boolean previousActionNext = false;
    private boolean previousActionPrevious = false;

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
            String nativeParam = myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            sampleRate = Integer.parseInt(nativeParam);
            nativeParam = myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
            bufSize = Integer.parseInt(nativeParam);

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

            createNotification();

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
                Log.d(LOG_TAG, "Device connected"); //
                if (paused) updateA2DPPlayState(false);
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
        unregisterReceiver(mReceiver);
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

    private void createNotification(){
        Log.i(LOG_TAG, "Received Start Foreground Intent ");
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Constants.ACTION.MAIN_ACTION);

        views = new RemoteViews(getPackageName(), R.layout.custom_notification);
        bigViews = new RemoteViews(getPackageName(), R.layout.custom_notification_expanded);

        //views.setViewVisibility(R.id.status_bar_icon, View.VISIBLE);
        //views.setViewVisibility(R.id.status_bar_album_art, View.GONE);
        views.setImageViewBitmap(R.id.status_bar_album_art, BitmapFactory.decodeResource(getResources(), R.drawable.default_album_art));
        bigViews.setImageViewBitmap(R.id.status_bar_album_art, BitmapFactory.decodeResource(getResources(), R.drawable.default_album_art));

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

        views.setTextViewText(R.id.status_bar_track_name, "Song Title");
        bigViews.setTextViewText(R.id.status_bar_track_name, "Song Title");

        views.setTextViewText(R.id.status_bar_artist_name, "Artist Name");
        bigViews.setTextViewText(R.id.status_bar_artist_name, "Artist Name");

        bigViews.setTextViewText(R.id.status_bar_album_name, "Album Name");

        mNotificationCompatBuilder = new NotificationCompat.Builder(this)
                .setContentTitle("")
                .setTicker("")
                .setContentText("")
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.default_album_art)
                .setLargeIcon(
                        Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.default_album_art), 128, 128, false))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_previous,
                        "", ppreviousIntent)
                .addAction(android.R.drawable.ic_media_play, "",
                        pplayIntent)
                .addAction(android.R.drawable.ic_media_next, "",
                        pnextIntent)
                .addAction(R.drawable.img_btn_repeat, "",
                        prepeatIntent)
                .setContent(views)
                .setCustomBigContentView(bigViews);

        notification = mNotificationCompatBuilder.build();
        startForeground(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE,
                notification);
    }

    public void setPlayingStatePlaying() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        views.setImageViewResource(R.id.status_bar_play,
                android.R.drawable.ic_media_pause);
        bigViews.setImageViewResource(R.id.status_bar_play,
                android.R.drawable.ic_media_pause);

        Log.d(LOG_TAG, "setPlayingStatePlaying");

        mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
        notificationPlaying = true;

        updateA2DPPlayState(true);
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
        boolean randomizeOnlyWhenFromNotifcation = !fromActivity && randomizer;
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Game game = gameCollection.getCurrentGame();

        views.setTextViewText(R.id.status_bar_track_name, game.getCurrentTrackTitle(randomizeOnlyWhenFromNotifcation));
        bigViews.setTextViewText(R.id.status_bar_track_name, game.getCurrentTrackTitle(randomizeOnlyWhenFromNotifcation));

        views.setTextViewText(R.id.status_bar_artist_name, game.getTitle());
        bigViews.setTextViewText(R.id.status_bar_artist_name, game.getTitle());

        bigViews.setTextViewText(R.id.status_bar_album_name, game.getVendor());

        views.setImageViewBitmap(R.id.status_bar_album_art, BitmapFactory.decodeFile(game.imageFile.getAbsolutePath()));
        bigViews.setImageViewBitmap(R.id.status_bar_album_art, BitmapFactory.decodeFile(game.imageFile.getAbsolutePath()));

        mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
    }

    public void setGameCollection(GameCollection gc){
        hasGameCollection = true;
        gameCollection = gc;
    }

    public void createGameCollection(){
        gameCollection = new GameCollection(this);
        gameCollection.createGameCollection();
        hasGameCollection = true;
        Intent startIntent = new Intent();
        startIntent.setAction(Constants.ACTION.STARTFOREGROUND_ACTION);
        Log.d(LOG_TAG,"starting service...");
        onStartCommand(startIntent,0,0);
    }

    private void nextTrack() {
        fixUninitialized();
        Log.d(LOG_TAG,"Next track called..." + Debug.getNativeHeapAllocatedSize() + " and " + Debug.getNativeHeapSize());
        Game game = gameCollection.getCurrentGame();
        Log.d(LOG_TAG,"game: " + game.getTitle());
        sendBroadcast(new Intent("resetSeekBar"));
        switch (repeatMode) {
            case Constants.REPEAT_MODES.NORMAL_PLAYBACK:
            case Constants.REPEAT_MODES.LOOP_TRACK:
            case Constants.REPEAT_MODES.SHUFFLE_IN_GAME:
                if (!game.setNextTrack()) {
                    gameCollection.setNextGame();
                    game = gameCollection.getCurrentGame();
                    game.setFirstTrack();
                    Log.d(LOG_TAG, "Game in service: " + game.title + " " + game.position);
                    setKssJava(game.musicFileC);
                }
                break;
            case Constants.REPEAT_MODES.LOOP_GAME:
                game.setNextTrack();
                break;
            case Constants.REPEAT_MODES.SHUFFLE_IN_PLATFORM:
                gameCollection.setNextGame();
                game = gameCollection.getCurrentGame();
                if (!previousActionPrevious) game.setNextTrack();
                setKssJava(game.musicFileC);
                previousActionNext = true;
                previousActionPrevious = false;
                break;
        }
        Log.d(LOG_TAG, "Randomizer: " + randomizer);
        setKssTrackJava(game.getCurrentTrackNumber(randomizer), game.getCurrentTrackLength(randomizer));
        Intent intent = new Intent("setSlidingUpPanelWithGame");
        intent.putExtra("RANDOMIZER",randomizer);
        sendBroadcast(intent);
        updateNotificationTitles();
        updateA2DPInfo();
    }

    private void previousTrack() {
        fixUninitialized();
        Game game = gameCollection.getCurrentGame();
        sendBroadcast(new Intent("resetSeekBar"));
        switch (repeatMode) {
            case Constants.REPEAT_MODES.NORMAL_PLAYBACK:
            case Constants.REPEAT_MODES.LOOP_TRACK:
            case Constants.REPEAT_MODES.SHUFFLE_IN_GAME:
                if (!game.setPreviousTrack()) {
                    gameCollection.setPreviousGame();
                    game = gameCollection.getCurrentGame();
                    game.setLastTrack();
                    Log.d(LOG_TAG, "Game in service: " + game.title + " " + game.position);
                    setKssJava(game.musicFileC);
                }
                break;
            case Constants.REPEAT_MODES.LOOP_GAME:
                game.setPreviousTrack();
                break;
            case Constants.REPEAT_MODES.SHUFFLE_IN_PLATFORM:
                gameCollection.setPreviousGame();
                game = gameCollection.getCurrentGame();
                if (!previousActionNext) game.setPreviousTrack();
                setKssJava(game.musicFileC);
                previousActionNext = false;
                previousActionPrevious = true;
                break;
        }
        setKssTrackJava(game.getCurrentTrackNumber(randomizer), game.getCurrentTrackLength(randomizer));
        Intent intent = new Intent("setSlidingUpPanelWithGame");
        intent.putExtra("RANDOMIZER",randomizer);
        sendBroadcast(intent);
        updateNotificationTitles();
        updateA2DPInfo();
    }

    public void playCurrentTrack() {
        fromActivity = true;
        Game game = gameCollection.getCurrentGame();
        setKssTrackJava(game.getCurrentTrackNumber(false), game.getCurrentTrackLength(false));
        //sendBroadcast(new Intent("setSlidingUpPanelWithGame"));
        updateNotificationTitles();
        updateA2DPInfo();
        setPlayingStatePlaying();
        fromActivity = false;
        if (paused) paused = togglePlayback();
    }

    public int getCurrentTrackLength(){
        return gameCollection.getCurrentGame().getCurrentTrackLength(randomizer);
    }

    public void setKssJava(String file){
        kssSet = true;
        kssTrackSet = false;
        setKss(file);
    }

    public void setKssTrackJava(int track, int length){
        Log.d(LOG_TAG, "track: " + track + " length: " + length);
        kssTrackSet = true;
        secondsPlayedFromCurrentTrack = 0;
        secondsBufferedFromCurrentTrack = 0;
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
        if (!kssSet) {
            //gameCollection.setRandomGameWithTrackInformation();
            Game game = gameCollection.getCurrentGame();
            setKssJava(game.musicFileC);
            setKssTrackJava(game.getCurrentTrackNumber(randomizer), game.getCurrentTrackLength(randomizer));
        }
        if (!kssTrackSet) {
            Game game = gameCollection.getCurrentGame();
            setKssTrackJava(game.getCurrentTrackNumber(randomizer), game.getCurrentTrackLength(randomizer));
            togglePlayback();
        }
    }

    public void togglePlaybackJava(){
        fixUninitialized();
        paused = togglePlayback();
        if (paused) setPlayingStatePause(); else setPlayingStatePlaying();
        Log.d("KSS","togglePlaybackJava...: " + notificationPlaying);
        updateNotificationTitles();
        updateA2DPInfo();
        if (!paused) sendBroadcast(new Intent("setSlidingUpPanelWithGame"));
    }

    private void repeatActivator(){
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Game game = gameCollection.getCurrentGame();
        Handler handler = new Handler();
        switch(repeatMode) {
            case Constants.REPEAT_MODES.NORMAL_PLAYBACK :
                gameCollection.disableRandomizer();
                randomizer = false;
                bigViews.setImageViewResource(R.id.status_bar_repeat,
                        R.drawable.img_btn_repeat_pressed);
                bigViews.setTextViewText(R.id.status_bar_track_name,"Repeat mode 1 activated: loop current track");
                repeatMode = Constants.REPEAT_MODES.LOOP_TRACK;
                mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
                //setKssLoopMode(true);
                handler.postDelayed(new MyRunnable(game), 3000);
                break;
            case Constants.REPEAT_MODES.LOOP_TRACK :
                repeatMode = Constants.REPEAT_MODES.LOOP_GAME;
                bigViews.setTextViewText(R.id.status_bar_track_name,"Repeat mode 2: loop current game/collection");
                //setKssLoopMode(false);
                mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
                handler.postDelayed(new MyRunnable(game), 3000);
                break;
            case Constants.REPEAT_MODES.LOOP_GAME :
                randomizer = true;
                repeatMode = Constants.REPEAT_MODES.SHUFFLE_IN_GAME;
                bigViews.setImageViewResource(R.id.status_bar_repeat,
                        R.drawable.img_btn_shuffle_pressed);
                bigViews.setTextViewText(R.id.status_bar_track_name,"Shuffle mode 1: shuffle current game/collection");
                mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
                handler.postDelayed(new MyRunnable(game), 3000);
                break;
            case Constants.REPEAT_MODES.SHUFFLE_IN_GAME :
                gameCollection.enableRandomizer();
                repeatMode = Constants.REPEAT_MODES.SHUFFLE_IN_PLATFORM;
                bigViews.setTextViewText(R.id.status_bar_track_name,"Shuffle mode 2: shuffle in current platform");
                mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
                handler.postDelayed(new MyRunnable(game), 3000);
                break;
            case Constants.REPEAT_MODES.SHUFFLE_IN_PLATFORM :
                gameCollection.disableRandomizer();
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
            bigViews.setTextViewText(R.id.status_bar_track_name,game.getCurrentTrackTitle(randomizer));
            mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
        }
    }


    public void updateA2DPInfo(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Game game = gameCollection.getCurrentGame();
            MediaMetadata metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, game.getCurrentTrackTitle(randomizer))
                .putString(MediaMetadata.METADATA_KEY_ARTIST, game.getVendor())
                .putString(MediaMetadata.METADATA_KEY_ALBUM, game.getTitle())
                .putLong(MediaMetadata.METADATA_KEY_DURATION, game.getCurrentTrackLength(randomizer)*1000)
                .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, game.getCurrentTrackNumber(randomizer))
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

    public native void createEngine();
    public static native void createBufferQueueAudioPlayer(int sampleRate, int samplesPerBuf);
    public static native void setKss(String file);
    public static native void setKssTrack(int track, int length);
    public static native boolean togglePlayback();
    public native void setKssProgress(int progress);
    public native void shutdown();

    public native void generateTrackInformation();

}