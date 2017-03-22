package nl.vlessert.vigamup;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;

public class PlayerService extends Service {
    private static final String LOG_TAG = "KSS PlayerService";
    private IBinder mBinder = new MyBinder();
    private boolean alreadyRunning = false;
    NotificationCompat.Builder mNotificationCompatBuilder;
    Notification notification;

    private boolean kssSet = false, kssTrackSet = false, notificationPlaying = false;
    private boolean paused = false;
    public boolean hasGameCollection = false;

    public GameCollection gameCollection;

    PendingIntent ppreviousIntent;
    PendingIntent pplayIntent;
    PendingIntent pnextIntent;

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
                String nativeParam = myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
                sampleRate = Integer.parseInt(nativeParam);
                nativeParam = myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
                bufSize = Integer.parseInt(nativeParam);
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
            togglePlaybackJava(false);
        } else if (intent.getAction().equals(Constants.ACTION.NEXT_ACTION)) {
            Log.i(LOG_TAG, "Clicked Next");
            nextTrack();
        } else if (intent.getAction().equals(
                Constants.ACTION.STOPFOREGROUND_ACTION)) {
            Log.i(LOG_TAG, "Received Stop Foreground Intent");
            stopForeground(true);
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        shutdown();
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

    public void setPlayingState(){
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (!notificationPlaying) {
            mNotificationCompatBuilder.mActions.clear();
            mNotificationCompatBuilder.addAction(android.R.drawable.ic_media_previous,
                    "", ppreviousIntent)
                    .addAction(android.R.drawable.ic_media_pause, "",
                            pplayIntent)
                    .addAction(android.R.drawable.ic_media_next, "",
                            pnextIntent);

            notification = mNotificationCompatBuilder.build();

            mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
            notificationPlaying = true;
        } else {
            mNotificationCompatBuilder.mActions.clear();
            mNotificationCompatBuilder.addAction(android.R.drawable.ic_media_previous,
                    "", ppreviousIntent)
                    .addAction(android.R.drawable.ic_media_play, "",
                            pplayIntent)
                    .addAction(android.R.drawable.ic_media_next, "",
                            pnextIntent);

            notification = mNotificationCompatBuilder.build();

            mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
            notificationPlaying = false;
        }
    }

    public void setPlayingStateForcePause(){
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationCompatBuilder.mActions.clear();
        mNotificationCompatBuilder.addAction(android.R.drawable.ic_media_previous,
                "", ppreviousIntent)
                .addAction(android.R.drawable.ic_media_play, "",
                        pplayIntent)
                .addAction(android.R.drawable.ic_media_next, "",
                        pnextIntent);

        notification = mNotificationCompatBuilder.build();

        mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
        paused = true;
    }

    public void setPlayingStateForcePlay(){
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationCompatBuilder.mActions.clear();
        mNotificationCompatBuilder.addAction(android.R.drawable.ic_media_previous,
                "", ppreviousIntent)
                .addAction(android.R.drawable.ic_media_pause, "",
                        pplayIntent)
                .addAction(android.R.drawable.ic_media_next, "",
                        pnextIntent);

        notification = mNotificationCompatBuilder.build();

        mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
        paused = false;
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
            Game gameNew = gameCollection.getCurrentGame();
            setKssJava(gameNew.musicFileC);
            setKssTrackJava(gameNew.getCurrentTrackNumber(), gameNew.getCurrentTrackLength());
        } else {
            setKssTrack(game.getCurrentTrackNumber(), game.getCurrentTrackLength());
        }
        updateNotificationTitles();
    }

    private void previousTrack() {
        if (!hasGameCollection) createGameCollection();
        Game game = gameCollection.getCurrentGame();
        sendBroadcast(new Intent("resetSeekBar"));
        game.setPreviousTrack();
        updateNotificationTitles();
        setKssTrack(game.getCurrentTrackNumber(), game.getCurrentTrackLength());
    }

    public int getCurrentTrackLength(){
        return gameCollection.getCurrentGame().getCurrentTrackLength();
    }

    public void setKssJava(String file){
        kssSet = true;
        kssTrackSet = false;
        notificationPlaying=true;
        updateNotificationTitles();
        setPlayingState();
        setKss(file);
    }

    public void setKssTrackJava(int track, int length){
        kssTrackSet = true;
        setKssTrack(track, length);
    }

    public void togglePlaybackJava(boolean fromActivity){
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
        if (!fromActivity) {
            paused = togglePlayback();
        } else {
            if (paused) {
                paused = togglePlayback();
                notificationPlaying = false;
            } else notificationPlaying = false;
        }
        setPlayingState();
        updateNotificationTitles();
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