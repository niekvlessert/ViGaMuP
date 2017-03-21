package nl.vlessert.vigamup;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

/**
 * Created by niek on 20/02/17.
 */

public class Game {
    public String gameName;
    public String title = null;
    public File imageFile;
    public String musicType; // KSS for now...
    public File musicFile;
    public String musicFileC;
    public int[] trackList = null;
    public int position = 0;

    private Context ctx;

    public String vendor = null;
    public String year = null;
    public String composers = null;
    public String chips = null;
    public String japaneseTitle = null;
    public String fullTitle = null;
    private String logoBackGroundColor = null;

    public ArrayList<GameTrack> trackInformation = new ArrayList<>();

    private static final int REPEAT_TIMES=2;

    public Game(String gameName, String musicType, Context ctx, int position){
        this.ctx = ctx;
        this.gameName = gameName;
        this.musicType = musicType;
        this.musicFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/" + musicType + "/" + gameName + ".kss");
        this.musicFileC = "/sdcard/Download/ViGaMuP/" + musicType + "/" + gameName + ".kss";
        this.imageFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/" + musicType + "/" + gameName + ".png");
        this.position = position;
        readGameInfo(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/" + musicType + "/" + gameName + ".gameinfo"));
        readTrackInformation(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/" + musicType + "/" + gameName + ".trackinfo"));
    }

    public boolean readTrackInformation(File trackInfoFile){
        Scanner s;
        int track = 0;
        int length = 0;
        String title = null;
        int partToSkip = 0;
        boolean repeatable = false;
        String tmp;
        int trackNumber = 1;
        try {
            FileInputStream is = new FileInputStream(trackInfoFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line = reader.readLine();
            while(line !=null){
                s = new Scanner(line).useDelimiter(",");
                track = Integer.parseInt(s.next());
                //Log.d("KSS","track: " +track);
                if (s.hasNext()) {
                    if (trackNumber<10) title = "0"+ Integer.toString(trackNumber); else title = Integer.toString(trackNumber);
                    title += " - "+ctx.getString(R.string.tab)+s.next(); //Log.d("KSS","title: " + title); }
                }
                if (s.hasNext()) length = Integer.parseInt(s.next()); //Log.d("KSS","length: " + length); }
                if (s.hasNext()) partToSkip = Integer.parseInt(s.next()); //Log.d("KSS","partToSkip: " + partToSkip); }
                if (s.hasNext()) {
                    tmp = s.next();
                    if (tmp.equals("y")) repeatable = true; else repeatable = false;
                    //Log.d("KSS","repeatable: " + repeatable);
                }
                line = reader.readLine();

                if (Arrays.binarySearch(trackList, track) >=0) {
                    //Log.d("KSS", "adding track: " + track + " " + title + " ");
                    trackInformation.add(new GameTrack(track, title, length, partToSkip, repeatable));
                    trackNumber++;
                }

                length = 0;
                title = null;
                partToSkip = 0;
                repeatable = false;
            }
        }  catch (IOException e) {
            //Log.d("KSS","error reading "+trackInfoFile);
            if (trackList == null){
                return false;
            } else {
                for (int i =0; i < trackList.length; i++){
                    if (i<10) title = "0"+ Integer.toString(i); else title = Integer.toString(i);
                    title += " - Track " + trackList[i];
                    trackInformation.add(new GameTrack(trackList[i], title, 30, 0, true));
                }
            }
        }
        return true;
    }

    public boolean readGameInfo(File trackInfoFile){
        ArrayList<String> sInfo = new ArrayList<>();
        try {

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            new FileInputStream(trackInfoFile)));
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
                    //Log.d("KSS",type + " " + value);
                    if (type.equals("tracks_to_play")) {
                        String[] strArray = value.split(",");
                        trackList = new int[strArray.length];
                        for (int i = 0; i < strArray.length; i++) {
                            trackList[i] = Integer.parseInt(strArray[i]);
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
            Log.d("KSS","FileNotFoundException: " + e.getMessage());
            return false;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean hasTrackInformationList(){
        //Log.d("KSS","yoyo" + gameName + " " + trackInformation.size());
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

    public int getTotalPLayTime(){
        int total = 0;
        if (trackInformation.size()>0) {
            for (GameTrack gt : trackInformation) {
                total = total + gt.getPlayTimeWithRepeat(2);
            }
        } else {
            if (trackList != null) total = trackList.length * 120;
        }
        //Log.d("KSS","Total playtime from "  + gameName +": " + total); //cache gamelist, so functions won't be called all the time
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

    public void shuffle(){
        int index, temp;
        Random random = new Random();
        for (int i = trackList.length - 1; i > 0; i--)
        {
            index = random.nextInt(i + 1);
            temp = trackList[index];
            trackList[index] = trackList[i];
            trackList[i] = temp;
        }
        position=0;
    }

    public String getTitle(){
        if (title!=null) return title; else return gameName.substring(0, 1).toUpperCase() + gameName.substring(1);
    }

    public int getCurrentTrackNumber(){
        //Log.d("KSS", "trackrrrkrkr: " + trackInformation.get(position).getTrackNr());
        return trackInformation.get(position).getTrackNr();
    }

    public int getCurrentTrackLength(){
        //Log.d("KSS", "tracklength: " + trackInformation.get(position).getTrackLength());
        return trackInformation.get(position).getPlayTimeWithRepeat(REPEAT_TIMES);
    }

    public String getCurrentTrackTitle(){
        return trackInformation.get(position).getTrackTitle();
    }

    public void setTrack(int position){
        this.position = position;
    }

    public void setNextTrack(){
        if (position == trackList.length-1) position = 0; else position++;
    }

    public void setPreviousTrack(){
        if (position == 0) position = trackList.length-1; else position--;
    }
}
