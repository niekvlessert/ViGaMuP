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
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

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

    MediaSession mMediaSession;

    IntentFilter filter;
    BroadcastReceiver receiver;

    private AudioFocusChangeListenerImpl mAudioFocusChangeListener;
    private boolean mFocusGranted, mFocusChanged;
    private boolean transientlostbefore = false;

    static {
        System.loadLibrary("kss_player");
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(LOG_TAG, "Received Intent ");
        if (intent==null) {
            Log.d("KSS","Intent empty... no idea why");
            return START_STICKY;
        }
        if (intent.getAction().equals(Constants.ACTION.STARTFOREGROUND_ACTION) && !alreadyRunning) {
            alreadyRunning = true;

            createEngine();
            int sampleRate = 0;
            int bufSize = 0;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
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
            }
            createBufferQueueAudioPlayer(sampleRate, bufSize);

            Log.i(LOG_TAG, "Received Start Foreground Intent ");
            Intent notificationIntent = new Intent(this, MainActivity.class);
            notificationIntent.setAction(Constants.ACTION.MAIN_ACTION);
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

            Bitmap icon = BitmapFactory.decodeResource(getResources(),
                    R.drawable.yngwie);

            mNotificationCompatBuilder = new NotificationCompat.Builder(this)
                    .setContentTitle("")
                    .setTicker("")
                    .setContentText("")
                    //.setCustomBigContentView(bigView)
                    .setSmallIcon(R.drawable.yngwie)
                    .setLargeIcon(
                            Bitmap.createScaledBitmap(icon, 128, 128, false))
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .addAction(android.R.drawable.ic_media_previous,
                            "", ppreviousIntent)
                    .addAction(android.R.drawable.ic_media_play, "",
                            pplayIntent)
                    .addAction(android.R.drawable.ic_media_next, "",
                            pnextIntent);

                    notification = mNotificationCompatBuilder.build();
            startForeground(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE,
                    notification);
        } else if (intent.getAction().equals(Constants.ACTION.PREV_ACTION)) {
            Log.i(LOG_TAG, "Clicked Previous");
            previousTrack();
        } else if (intent.getAction().equals(Constants.ACTION.PLAY_ACTION)) {
            Log.i(LOG_TAG, "Clicked Play/Pause");
            togglePlaybackJava();
        } else if (intent.getAction().equals(Constants.ACTION.NEXT_ACTION)) {
            Log.i(LOG_TAG, "Clicked Next");
            nextTrack();
        } else if (intent.getAction().equals(
                Constants.ACTION.STOPFOREGROUND_ACTION)) {
            Log.i(LOG_TAG, "Received Stop Foreground Intent");
            stopForeground(true);
            stopSelf();
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
                if (paused){
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        PlaybackState state = new PlaybackState.Builder()
                                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE |
                                        PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_PAUSE |
                                        PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                                .setState(PlaybackState.STATE_PAUSED, 0, 1.0f)
                                .build();

                        mMediaSession.setPlaybackState(state);
                    }
                }
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
        shutdown();
        unregisterReceiver(mReceiver);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mMediaSession!=null) mMediaSession.release();
        }
        super.onDestroy();
        Log.i(LOG_TAG, "In onDestroy");
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(LOG_TAG, "in onBind");
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.v(LOG_TAG, "in onRebind");
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

    public void setPlayingStatePlaying() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationCompatBuilder.mActions.clear();
        mNotificationCompatBuilder.addAction(android.R.drawable.ic_media_previous,
                "", ppreviousIntent)
                .addAction(android.R.drawable.ic_media_pause, "",
                        pplayIntent)
                .addAction(android.R.drawable.ic_media_next, "",
                        pnextIntent);

        notification = mNotificationCompatBuilder.build();

        Log.d(LOG_TAG, "setPlayingStatePlaying");

        mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
        notificationPlaying = true;
    }

    public void setPlayingStatePause() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationCompatBuilder.mActions.clear();
            mNotificationCompatBuilder.addAction(android.R.drawable.ic_media_previous,
                    "", ppreviousIntent)
                    .addAction(android.R.drawable.ic_media_play, "",
                            pplayIntent)
                    .addAction(android.R.drawable.ic_media_next, "",
                            pnextIntent);

        notification = mNotificationCompatBuilder.build();

        Log.d(LOG_TAG, "setPlayingStatePause");

        mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
        notificationPlaying = false;
    }

    public void updateNotificationTitles(){
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Game game = gameCollection.getCurrentGame();
        mNotificationCompatBuilder.setContentTitle(game.getCurrentTrackTitle())
                .setTicker(game.getCurrentTrackTitle())
                .setContentText(game.getTitle())
                .setLargeIcon(BitmapFactory.decodeFile(game.imageFile.getAbsolutePath()));
        notification = mNotificationCompatBuilder.build();
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
        if (!hasGameCollection) createGameCollection();
        Log.d("KSS","Next track called...");
        Game game = gameCollection.getCurrentGame();
        sendBroadcast(new Intent("resetSeekBar"));
        if (!game.setNextTrack()) {
            gameCollection.setRandomGameWithTrackInformation();
            game = gameCollection.getCurrentGame();
            Log.d(LOG_TAG, "Game in service: " + game.title + " " + game.position);
            setKssJava(game.musicFileC);
        }
        setKssTrackJava(game.getCurrentTrackNumber(), game.getCurrentTrackLength());
        sendBroadcast(new Intent("setSlidingUpPanelWithGame"));
        updateNotificationTitles();
        updateA2DPInfo();
    }

    private void previousTrack() {
        if (!hasGameCollection) createGameCollection();
        Game game = gameCollection.getCurrentGame();
        sendBroadcast(new Intent("resetSeekBar"));
        if (!game.setPreviousTrack()) {
            gameCollection.setRandomGameWithTrackInformation();
            game = gameCollection.getCurrentGame();
            Log.d(LOG_TAG, "Game in service: " + game.fullTitle + game.position);
            setKssJava(game.musicFileC);
        }
        setKssTrackJava(game.getCurrentTrackNumber(), game.getCurrentTrackLength());
        sendBroadcast(new Intent("setSlidingUpPanelWithGame"));
        updateNotificationTitles();
        updateA2DPInfo();
    }

    public int getCurrentTrackLength(){
        return gameCollection.getCurrentGame().getCurrentTrackLength();
    }

    public void setKssJava(String file){
        kssSet = true;
        kssTrackSet = false;
        updateNotificationTitles();
        updateA2DPInfo();
        setKss(file);
    }

    public void setKssTrackJava(int track, int length){
        kssTrackSet = true;
        setKssTrack(track, length);
    }

    public void startPlayback(){
        if (paused) {
            togglePlayback();
            setPlayingStatePlaying();
            updateA2DPInfo();
            paused = false;
        }
    }

    public void stopPlayback(){
        if (!paused) {
            togglePlayback();
            setPlayingStatePause();
            updateA2DPInfo();
            paused = true;
        }
    }

    public void togglePlaybackJava(){
        if (!hasGameCollection) createGameCollection();
        if (!kssSet) {
            gameCollection.setRandomGameWithTrackInformation();
            Game game = gameCollection.getCurrentGame();
            setKssJava(game.musicFileC);
            setKssTrackJava(game.getCurrentTrackNumber(), game.getCurrentTrackLength());
        }
        if (!kssTrackSet) {
            Game game = gameCollection.getCurrentGame();
            setKssTrackJava(game.getCurrentTrackNumber(), game.getCurrentTrackLength());
            togglePlayback();
        }
        paused = togglePlayback();
        if (paused) setPlayingStatePause(); else setPlayingStatePlaying();
        Log.d("KSS","togglePlaybackJava...: " + notificationPlaying);
        updateNotificationTitles();
        updateA2DPInfo();
    }

    private void updateA2DPInfo(){
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
            PlaybackState state = new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY| PlaybackState.ACTION_PLAY_PAUSE |
                            PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_PAUSE |
                            PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                    .setState(PlaybackState.STATE_PLAYING, 1, 1.0f, SystemClock.elapsedRealtime())
                    .build();

            mMediaSession.setPlaybackState(state);
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
                    togglePlayback();
                    setPlayingStatePlaying();
                    super.onPlay();
                }

                @Override
                public void onPause() {
                    //Toast.makeText(getApplicationContext(), "Pause!!", Toast.LENGTH_LONG).show();
                    togglePlayback();
                    setPlayingStatePause();
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
        sendBroadcast(new Intent("setBufferBarProgress"));
    }

    private void setSeekBarThumbProgress(){
        sendBroadcast(new Intent("setSeekBarThumbProgress"));
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