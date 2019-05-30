package nl.vlessert.vigamup;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import net.lingala.zip4j.unzip.Unzip;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

/**
 * Created by niek on 20/02/17.
 */

public class Game {
    public String gameName;
    public String title = null;
    public File imageFile;
    private int musicType;
    public String musicExtension = "";
    public String musicArchive = "";
    public File musicFile;
    public String musicFileC;
    //private int[] trackList = null;
    private ArrayList<Integer> trackList = null;
    public int position = 0;
    private boolean trackInformationAvailable = true;

    private HelperFunctions helpers;

    private PlayerService playerService;

    //private Context ctx;

    public String vendor = "";
    public String year = "";
    private String composers = "";
    public String chips = "";
    public String japaneseTitle = "";
    public String fullTitle = "";
    private String logoBackGroundColor = null;

    private ArrayList<GameTrack> trackInformation = new ArrayList<>();
    private ArrayList<GameTrack> randomizedTrackInformation;
    private int randomizedTrackInformationPosition = 0;

    private static final int REPEAT_TIMES=2;
    private static final String LOG_TAG = "Game";

    //private boolean unzippingDone = false;

    public Game(String gameName, int musicType, int position, String trackerFileExtension){
        //this.ctx = ctx;
        this.gameName = gameName;
        this.musicType = musicType;
        if (musicType == Constants.PLATFORM.KSS) {
            musicExtension = "KSS";
            musicArchive = ".kss";
        }
        if (musicType == Constants.PLATFORM.SPC) {
            musicExtension = "SPC";
            musicArchive = ".rsn";
        }
        if (musicType == Constants.PLATFORM.VGM) {
            musicExtension = "VGM";
            musicArchive = ".zip";
        }
        if (musicType == Constants.PLATFORM.TRACKERS) {
            musicExtension = "Trackers";
        }
        if (!gameName.equals("header")) {
            this.musicFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/" + musicExtension + "/" + gameName + musicArchive);
            this.musicFileC = "/sdcard/Download/ViGaMuP/" + musicExtension + "/" + gameName + musicArchive;
            this.imageFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/" + musicExtension + "/" + gameName + ".png");
            this.position = position;

            if (musicType == Constants.PLATFORM.NSF) readInfoFromNSF(gameName);
            if (musicType == Constants.PLATFORM.TRACKERS) addTrackerTrack(gameName);

            switch (musicType){
                case Constants.PLATFORM.KSS:
                case Constants.PLATFORM.SPC:
                case Constants.PLATFORM.VGM:
                    readGameInfo(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/" + musicExtension + "/" + gameName + ".gameinfo"));
                    if (!readTrackInformation(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/" + musicExtension + "/" + gameName + ".trackinfo"))) {
                        trackInformationAvailable = false;
                    }
                    break;
                case Constants.PLATFORM.TRACKERS:
                    this.musicFileC = this.musicFileC+"."+trackerFileExtension;
                    break;
            }

            helpers = new HelperFunctions();
        }
    }

    public boolean readTrackInformation(File trackInfoFile){
        Scanner s;
        int track = 0;
        int length = 0;
        String title = null;
        int partToSkip = 0;
        boolean repeatable = false;
        String fileName = "";
        String tmp;
        int trackNumber = 1;
        ArrayList<Integer> addedTracks = new ArrayList<>();
        try {
            FileInputStream is = new FileInputStream(trackInfoFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line = reader.readLine();
            while(line !=null){
                s = new Scanner(line).useDelimiter(",");
                track = Integer.parseInt(s.next());
                //Log.d(LOG_TAG,"track: " +track);
                if (s.hasNext()) {
                    if (trackNumber<10) title = "0"+ Integer.toString(trackNumber); else title = Integer.toString(trackNumber);
                    title += " - "+s.next(); //Log.d(LOG_TAG,"title: " + title); }
                }
                if (s.hasNext()) {
                    tmp = s.next();
                    if (tmp.length()!=0) length = Integer.parseInt(tmp); //Log.d(LOG_TAG,"length: " + length); }
                }
                if (s.hasNext()) {
                    tmp = s.next();
                    if (tmp.length()!=0) partToSkip = Integer.parseInt(tmp); //Log.d(LOG_TAG,"partToSkip: " + partToSkip); }
                }
                if (s.hasNext()) {
                    tmp = s.next();
                    if (tmp.length()!=0) {
                        if (tmp.equals("y")) repeatable = true;
                        else repeatable = false;
                    }
                    //Log.d(LOG_TAG,"repeatable: " + repeatable);
                }
                if (s.hasNext()) {
                    tmp = s.next();
                    if (tmp != null) {
                        fileName = tmp;
                    }
                }
                line = reader.readLine();

                if (trackList.contains(track)) {
                    trackInformation.add(new GameTrack(track, title, length, partToSkip, repeatable, trackNumber, fileName));
                    addedTracks.add(track);
                }
                trackNumber++;

                length = 0;
                title = null;
                partToSkip = 0;
                repeatable = false;
            }
            // Messy way to add tracks which should be played according to gameinfo but are not in trackinfo...
            List<Integer> trackListCopy = new ArrayList<>(trackList);

            for (Integer trackInt:addedTracks){
                trackListCopy.remove(trackInt);
            }
            for (Integer trackInt:trackListCopy){
                //Log.d("Game", "Skipped, so add with defaults: " + trackInt.toString());
                if (trackNumber<10) title = "0"+ Integer.toString(trackNumber); else title = Integer.toString(trackNumber);
                title += " - Track " +trackInt.toString(); //Log.d(LOG_TAG,"title: " + title); }
                trackInformation.add(new GameTrack(trackInt, title, 120, 0, false, trackNumber, fileName));
                trackNumber++;
            }
        }  catch (IOException e) {
            Log.d(LOG_TAG,"error reading "+trackInfoFile);
            if (trackList == null){
                return false;
            } else {
                for (int i =0; i < trackList.size(); i++){
                    if (i+1<10) title = "0"+ Integer.toString(i+1); else title = Integer.toString(i+1);
                    title += " - Track " + trackList.get(i);
                    trackInformation.add(new GameTrack(trackList.get(i), title, 30, 0, true, i, fileName));
                }
            }
        }
        randomizedTrackInformation = new ArrayList<>(trackInformation);
        Collections.shuffle(randomizedTrackInformation);
        return true;
    }

    private boolean readGameInfo(File gameInfoFile){
        ArrayList<String> sInfo = new ArrayList<>();
        try {

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            new FileInputStream(gameInfoFile)));
            String line;
            String[] saLineElements;
            while ((line = br.readLine()) != null)
            {
                saLineElements = line.split(";");
                for (int i = 0; i < saLineElements.length; i++)
                    sInfo.add(saLineElements[i]);
                for (String s: saLineElements) {
                    String type = s.substring(0,s.indexOf(":"));
                    String value = s.substring(s.indexOf(":")+1,s.length());
                    //Log.d(LOG_TAG,type + " " + value);
                    if (type.equals("tracks_to_play")) {
                        String[] strArray = value.split(",");
                        //trackList = new int[strArray.length];
                        trackList = new ArrayList<>();
                        for (int i = 0; i < strArray.length; i++) {
                            trackList.add(Integer.parseInt(strArray[i]));
                        }
                    }
                    if (type.equals("vendor")) this.vendor=value;
                    if (type.equals("title")) this.title=value;
                    if (type.equals("full_title")) this.fullTitle=value;
                    if (type.equals("japanese_title")) this.japaneseTitle=value;
                    if (type.equals("year")) this.year=value;
                    if (type.equals("composers")) this.composers=value;
                    if (type.equals("chips")) this.chips=value;
                    if (type.equals("logo_background_color")) this.logoBackGroundColor=value;
                }
            }
            br.close();
        }
        catch (FileNotFoundException e) {
            Log.d(LOG_TAG, "FileNotFoundException: " + e.getMessage());
            StringBuffer tracksToPlay = new StringBuffer("tracks_to_play:");
            for (int i = 0; i < 256; i++) {
                tracksToPlay.append(Integer.toString(i)+",");
            }
            String tracksToPlayString = tracksToPlay.substring(0, tracksToPlay.length()-1);

            File file = new File(gameInfoFile.toString());
            try {
                file.createNewFile();
                PrintWriter p = new PrintWriter(new FileOutputStream(file));
                p.write(tracksToPlayString);
                p.close();
            } catch (IOException f) {}
            readGameInfo(gameInfoFile);
            return true;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean hasTrackInformationAvailable() {
        return trackInformationAvailable;
    }

    public boolean hasTrackInformationList(){
        if (trackInformation.size()==0) return false;
        return true;
    }

    public List<String> getTrackInformationList(){
        List<String> tl = new ArrayList<>();
        for (GameTrack gt: trackInformation) {
            tl.add(""+ gt.title + " - " + getCorrectTimeDisplay(gt.getPlayTimeWithRepeat(REPEAT_TIMES)));
        }
        return tl;
    }

    public String getComposers() {
        return composers;
    }

    public int getTotalPLayTime(){
        int total = 0;
        if (trackInformation.size()>0) {
            for (GameTrack gt : trackInformation) {
                total = total + gt.getPlayTimeWithRepeat(2);
            }
        } else {
            if (trackList != null) total = trackList.size() * 120;
        }
        //Log.d(LOG_TAG,"Total playtime from "  + gameName +": " + total); //cache gamelist, so functions won't be called all the time
        return total;
    }

    public String getTotalPLayTimeHumanReadable(){
        return getCorrectTimeDisplay(getTotalPLayTime());
    }

    public String getLogoBackGroundColor(){
        if (logoBackGroundColor != null) return logoBackGroundColor; else return "black";
    }

    private String getCorrectTimeDisplay(int totalSecs)
    {
        int hours = totalSecs / 3600;
        int minutes = (totalSecs % 3600) / 60;
        int seconds = totalSecs % 60;

        if (hours>0) return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        else return String.format("%02d:%02d", minutes, seconds);
    }

    public String getTitle(){
        if (title!=null) return title; else return gameName.substring(0, 1).toUpperCase() + gameName.substring(1);
    }

    public String getVendor(){
        if (vendor!=null) return vendor; else return "";
    }

    public int getCurrentTrackNumber(){
        /*Log.d(LOG_TAG, "trackNr randomized??: " + randomized);
        Log.d(LOG_TAG, "non random: " + Arrays.toString(trackInformation.toArray()));
        Log.d(LOG_TAG, "random: " + Arrays.toString(trackInformationRandomized.toArray()));*/
        return trackInformation.get(position).getTrackNr();
    }

    public int getCurrentTrackLength(){
        //Log.d(LOG_TAG, "tracklength: " + trackInformation.get(position).getTrackLength());
        return trackInformation.get(position).getPlayTimeWithRepeat(REPEAT_TIMES);
    }

    public String getCurrentTrackTitle(){
        return trackInformation.get(position).getTrackTitle();
    }

    public String getCurrentTrackFileName() { return trackInformation.get(position).getFileName(); }

    public void extractCurrentSpcTrackfromRSN() {
        String spcFileName = getCurrentTrackFileName();
        String rsnFileName = musicFileC;
        Log.d(LOG_TAG,"Spcfilename: " + spcFileName + ", rsnfilename: " + rsnFileName);
        ExtractArchive ea = new ExtractArchive();
        ea.extractFileFromArchive(new File(rsnFileName), new File(spcFileName), new File(Constants.vigamupDirectory + "tmp/"));
    }

    public void serviceObjectToGameObject(PlayerService ps) {
        Log.d(LOG_TAG, "playerService Connected!!");
        this.playerService = ps;
    }

    public void extractCurrentVgmTrackfromZip() {
        //String vgmFileName = getCurrentTrackFileName();
        String zipFileName = musicFileC;
        Unzipper unzipper = new Unzipper(playerService);
        //try {
            unzipper.execute(zipFileName);
        //} catch (InterruptedException | ExecutionException e){
            //Log.d("VGM", e.toString());
        //}
        //try {
            //String fileName = trackInformation.get(position).getFileName();
            //Log.d(LOG_TAG, "VGMFile in functie: " + fileName + " en " + trackInformation.get(position).getFileName());
            //Runnable r = new Unzipper(zipFileName);
            //Thread t = new Thread (r);
            //t.start();
            //try { t.join(); } catch (InterruptedException e) { e.printStackTrace(); }
        //helpers.unzipFile(new File(zipFileName), new File(Constants.vigamupDirectory + "tmp"), trackInformation.get(position).getFileName());
        //} catch (IOException e) {
            //Log.d("VGM", e.toString());
        //}
    }

    /*class Unzipper implements Runnable {
        private String zipFileName;
        private HelperFunctions helperFunctions;

        public Unzipper(String zipFileName){
            this.zipFileName = zipFileName;
            helperFunctions = new HelperFunctions();
        }

        @Override
        public void run() {
            try {
                Log.d(LOG_TAG, "VGMFile in functie: " + zipFileName + " en " + trackInformation.get(position).getFileName());
                helperFunctions.unzipFile(new File(zipFileName), new File(Constants.vigamupDirectory + "tmp"), trackInformation.get(position).getFileName());
            } catch (IOException e) {
                Log.d("VGM", e.toString());
            }
        }
    }*/

    private class Unzipper extends AsyncTask<String, Void, String> {
        private HelperFunctions helperFunctions;
        private PlayerService playerService;

        public Unzipper(PlayerService ps){
            this.playerService = ps;
        }

        @Override
        protected String doInBackground(String... zipFileNames){
            try {
                helperFunctions = new HelperFunctions();
                Log.d(LOG_TAG, "VGMFile in functie: " + zipFileNames[0] + " en " + trackInformation.get(position).getFileName());
                helperFunctions.unzipFile(new File(zipFileNames[0]), new File(Constants.vigamupDirectory + "tmp"), trackInformation.get(position).getFileName());
                return "Unzip succesful";
            } catch (IOException e) {
                Log.d("VGM", e.toString());
                return "Unzip problem";
            }
        }

        @Override
        protected void onPostExecute(String result) {
            Log.d(LOG_TAG, "Extraction done, starting playback!");
            playerService.startVGMPlayback();
        }
    }

    public String getCurrentTrackFileNameFullPath() {
        switch (musicType){
            case Constants.PLATFORM.KSS:
            case Constants.PLATFORM.OTHERS:
            case Constants.PLATFORM.NSF:
            case Constants.PLATFORM.TRACKERS:
                Log.d("ViGaMuP nieuw", "filename: " +musicFileC);
                return musicFileC;
            case Constants.PLATFORM.SPC:
            case Constants.PLATFORM.VGM:
                return Constants.vigamupDirectory+"tmp/"+trackInformation.get(position).getFileName();
                //return "/sdcard/Download/ViGaMuP/tmp/"+trackInformation.get(position).getFileName();
        }
        return null;
    }

    private void addTrackerTrack(String trackFileName) {
        trackInformation.add(new GameTrack(0, trackFileName.substring(0, 1).toUpperCase() + trackFileName.substring(1), 30, 0, true, 0, "/sdcard/Download/ViGaMuP/Trackers/" + trackFileName + ".xm"));
    }

    private void readInfoFromNSF(String gameName) {
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/NSF/");
        File[] listOfFiles = folder.listFiles();

        for (File file : listOfFiles)
        {
            if (file.isFile())
            {
                String[] filename = file.getName().split("\\.(?=[^\\.]+$)"); //split filename from it's extension
                if(filename[0].equalsIgnoreCase(gameName)) {
                    if (filename[1].equalsIgnoreCase("nsf")) {
                        findDataInNsf(filename[0] + "." + filename[1]);
                    }
                }
            }
        }
    }

    private void findDataInNsf(String fileName){
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/NSF/" + fileName);
        try {
            RandomAccessFile raf = new RandomAccessFile(file, "r");

            raf.seek(6);
            int trackCount = (int)raf.readByte();

            raf.seek(14);
            String tempTitle = "";
            byte character;
            boolean readMore = true;
            while (readMore) {
                character = raf.readByte();
                if ((int)character==0){
                    readMore = false;
                } else {
                    tempTitle += (char)character;
                }
            }
            if (tempTitle.length()>0) title = tempTitle;

            String tempComposers = "";
            raf.seek(46);
            readMore = true;
            while (readMore) {
                character = raf.readByte();
                if ((int)character==0){
                    readMore = false;
                } else {
                    tempComposers += (char)character;
                }
            }
            if (tempComposers.length()>0) composers = tempComposers;

            String trackTitle;
            trackList = new ArrayList<>();
            for (int i =0; i < trackCount; i++){
                trackList.add(i);
                if (i+1<10) trackTitle = "0"+ Integer.toString(i+1); else trackTitle = Integer.toString(i+1);
                trackTitle += " - Track " + (i+1);
                trackInformation.add(new GameTrack(i, trackTitle, 30, 0, true, i, musicFileC));
                Log.d("VIGAMUP", "call aangeroepen!!");
            }

        } catch (FileNotFoundException e) {
            Log.d(LOG_TAG, "File not found!!");
        } catch (IOException e) {}
        this.musicFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/NSF/" + gameName + ".nsf");
        this.musicFileC = "/sdcard/Download/ViGaMuP/NSF/" + gameName + ".nsf";

    }

    public void setTrack(int position){
        this.position = position;
    }

    public boolean setNextTrack(){
        if (position == trackList.size()-1) {
            position = 0;
            return false;
        } else {
            position++;
            return true;
        }
    }

    public boolean setPreviousTrack(){
        if (position == 0) {
            position = trackList.size()-1;
            return false;
        } else {
            position--;
            return true;
        }
    }

    public void setFirstTrack(){ position = 0; }
    public void setLastTrack(){ position = trackList.size()-1; }
    public List<GameTrack> getGameTrackList(){
        return trackInformation;
    }
    public GameTrack getNextRandomTrack(){
        if (++randomizedTrackInformationPosition == randomizedTrackInformation.size()-1) randomizedTrackInformationPosition = 0;
        Log.d(LOG_TAG,"track nr: " +randomizedTrackInformationPosition);
        return randomizedTrackInformation.get(randomizedTrackInformationPosition);
    }

    public GameTrack getPreviousRandomTrack() {
        if (--randomizedTrackInformationPosition == 0) randomizedTrackInformationPosition = randomizedTrackInformation.size() - 1;
        return randomizedTrackInformation.get(randomizedTrackInformationPosition);
    }

    public String getVendorAndYear(){
        String result = "";
        if (vendor.length() > 0) result = vendor;
        if (year.length() > 0 ) result = result.concat(", " + year);
        return result;
    }

    public Integer getMusicType(){
        return musicType;
    }

    public boolean isGame() { return true; }
}
