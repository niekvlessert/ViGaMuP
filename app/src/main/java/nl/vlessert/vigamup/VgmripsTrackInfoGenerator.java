package nl.vlessert.vigamup;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

public class VgmripsTrackInfoGenerator implements Runnable{

    private String zipFile;

    private HelperFunctions helpers;
    private PlayerService mPlayerService;
    private Context context;

    public VgmripsTrackInfoGenerator(PlayerService ps, String rf, Context cx){
        mPlayerService = ps;
        zipFile = rf;
        context = cx;
    }

    @Override
    public void run() {
        String vgmTrackInfo;
        helpers = new HelperFunctions();
        String baseGameName = zipFile.substring(0, zipFile.indexOf("."));
        int trackNr = 1;
        String splitChar = "";

        if (helpers.directoryExists("tmp")){
            //helpers.deleteAllFilesInDirectory(("tmp"));
        } else {
            helpers.makeDirectory("tmp");
        }

        final File zip = new File(Constants.vigamupDirectory+"VGM/"+ zipFile);
        final File destinationFolder = new File(Constants.vigamupDirectory+"tmp");

        //Log.d("VGM info generator", "zip: " + zip + ", folder: " + destinationFolder);
        try {
            helpers.unzip(zip, destinationFolder);
        } catch (IOException e) {
            Log.d("VGM", e.toString());
        }
        File files[] = helpers.getAllFilesInDirectory("tmp/");
        Log.d("VGM info generator", "after zip: " + files.length);
        helpers.deleteFile("VGM", baseGameName+".trackinfo");
        helpers.deleteFile("VGM", baseGameName+".gameinfo");

        try {
            File trackinfoFile = new File(Constants.vigamupDirectory+"VGM/"+baseGameName+".trackinfo");
            FileWriter fWriter = new FileWriter(trackinfoFile, true);
            File gameinfoFile = new File(Constants.vigamupDirectory+"VGM/"+baseGameName+".gameinfo");
            FileWriter fWriter2 = new FileWriter(gameinfoFile, true);
            String tracks_to_play = "";

            for (File file: files) {
                String fileNameNoPath = file.toString().substring(file.toString().lastIndexOf("/") + 1);
                String trackExtension = fileNameNoPath.substring(fileNameNoPath.indexOf(".") + 1);
                String trackName;
                if (trackExtension.equals("vgm") || trackExtension.equals("vgz")) {
                    File f = file;
                    byte[] vgmHeaderBytes = new byte[0x4];
                    InputStream is = new FileInputStream (f);
                    is.read(vgmHeaderBytes);
                    is.close();
                    String vgmHeader = new String(vgmHeaderBytes,0,3);
                    Log.d("VGM", "vgmHeader maybe?: " + vgmHeader);
                    if (!vgmHeader.equals("Vgm")){
                        String vgz = Constants.vigamupDirectory+ "tmp/" + fileNameNoPath;
                        String extractedVgm = Constants.vigamupDirectory+ "tmp/" + fileNameNoPath.substring(0,fileNameNoPath.lastIndexOf(".")) + ".vgm2";
                        helpers.unGunzipFile(vgz, extractedVgm);
                        f = new File(Constants.vigamupDirectory+ "tmp/" + fileNameNoPath.substring(0,fileNameNoPath.lastIndexOf(".")) + ".vgm2");
                    }
                    int trackLengthSeconds = 0;
                    byte[] header = new byte[0x24];
                    is = new FileInputStream (f);
                    if (is.read(header) != header.length){

                    }
                    is.close();
                    byte[] trackLength = new byte[]{header[0x1b], header[0x1a], header[0x19], header[0x18]};
                    //Log.d("VGM", "value: " + Integer.toHexString(header[0x18] & 0xff));
                    //Log.d("VGM", "value: " + Integer.toHexString(header[0x19] & 0xff));
                    //Log.d("VGM", "value: " + Integer.toHexString(header[0x1a] & 0xff));
                    //Log.d("VGM", "value: " + Integer.toHexString(header[0x1b] & 0xff ));
                    //Log.d("VGM", "samen: " + Long.parseLong(Integer.toHexString(header[0x18] & 0xff)+Integer.toHexString(header[0x19] & 0xff)+Integer.toHexString(header[0x1a] & 0xff)+Integer.toHexString(header[0x1b] & 0xff), 16));
                    Log.d("VGM", "tracklength: " + Long.parseLong(helpers.bytesToHex(trackLength), 16)/44100);
                    byte[] loopLength = new byte[]{header[0x23], header[0x22], header[0x21], header[0x20]};
                    Log.d("VGM", "looplength: " + Long.parseLong(helpers.bytesToHex(loopLength), 16)/44100);
                    Long trackLengthLong = Long.parseLong(helpers.bytesToHex(trackLength), 16);
                    Long loopLengthLong = Long.parseLong(helpers.bytesToHex(loopLength), 16);
                    if (loopLengthLong > 0) trackLengthLong = trackLengthLong + (1*loopLengthLong);
                    Long trackLengthSecondsLong = trackLengthLong/44100;
                    trackLengthSeconds = helpers.safeLongToInt(trackLengthSecondsLong)+5;
                    Log.d("VGM", "ok, tracklengthlong: " + trackLengthLong + ", seconds: " + trackLengthSeconds);
                    if (splitChar.length()==0) {
                        if (fileNameNoPath.indexOf(" ")==2) splitChar = " ";
                        if (fileNameNoPath.indexOf("_")==2) splitChar = "_";
                    }
                    trackName = fileNameNoPath.substring(fileNameNoPath.indexOf(splitChar)+1);
                    trackName = trackName.substring(0,trackName.lastIndexOf(".")).replace(",","-");
                    fileNameNoPath = fileNameNoPath.replace(",",";;;");
                    fWriter.write(Integer.toString(trackNr) + "," + trackName + ","+ Integer.toString(trackLengthSeconds) + ",,," + fileNameNoPath + "\n");
                    tracks_to_play+=Integer.toString(trackNr)+",";
                    trackNr++;
                }
                if (fileNameNoPath.contains(".png")) {
                    Log.d("VGM", "Moving file: " + fileNameNoPath);
                    helpers.deleteFile("VGM", baseGameName + ".png");
                    helpers.moveFile("tmp/" + fileNameNoPath, "VGM/" + baseGameName + ".png");
                }
                if (fileNameNoPath.contains(".txt")) {
                    RandomAccessFile raf = new RandomAccessFile(file, "rw");
                    String line;
                    while ((line = raf.readLine()) != null) {
                        if (line.contains("Game name:")) fWriter2.write("title:" + line.substring(line.indexOf(":")+1).trim() + "\n");
                        if (line.contains("Game developer:")) fWriter2.write("vendor:" + line.substring(line.indexOf(":")+1).trim() + "\n");
                        if (line.contains("Game release date:")) fWriter2.write("year:" + line.substring(line.indexOf(":")+1).trim() + "\n");
                    }
                }
            }
            fWriter.flush();
            fWriter.close();
            fWriter2.write("tracks_to_play:"+tracks_to_play.substring(0,tracks_to_play.length()-1));
            fWriter2.flush();
            fWriter2.close();

        }
        catch (FileNotFoundException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
        catch (Exception f) {
            Log.e("Exception", "File write failed: " + f.toString());
        }
    }


}