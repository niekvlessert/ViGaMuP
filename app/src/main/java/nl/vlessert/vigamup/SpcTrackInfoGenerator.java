package nl.vlessert.vigamup;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.util.regex.Pattern;

public class SpcTrackInfoGenerator implements Runnable{

    private String rarFile;

    private HelperFunctions helpers;
    private PlayerService mPlayerService;
    private Context context;

    public SpcTrackInfoGenerator(PlayerService ps, String rf, Context cx){
        mPlayerService = ps;
        rarFile = rf;
        context = cx;
    }

    @Override
    public void run() {
        String spcTrackInfo;
        helpers = new HelperFunctions();
        String baseGameName = rarFile.substring(0, rarFile.indexOf("."));
        int trackNr = 1;

        if (helpers.directoryExists("tmp")){
            helpers.deleteAllFilesInDirectory(("tmp"));
        } else {
            helpers.makeDirectory("tmp");
        }

        final File rar = new File(Constants.vigamupDirectory+"SPC/"+rarFile);
        final File destinationFolder = new File(Constants.vigamupDirectory+"tmp/");
        ExtractArchive extractArchive = new ExtractArchive();
        extractArchive.extractArchive(rar, destinationFolder);
        File files[] = helpers.getAllFilesInDirectory("tmp/");
        helpers.deleteFile("SPC", baseGameName+".trackinfo");
        //helpers.createFile("SPC", baseGameName+".trackinfo");
        helpers.deleteFile("SPC", baseGameName+".gameinfo");
        //helpers.createFile("SPC", baseGameName+".gameinfo");

        try {
            File trackinfoFile = new File(Constants.vigamupDirectory+"SPC/"+baseGameName+".trackinfo");
            FileWriter fWriter = new FileWriter(trackinfoFile, true);
            File gameinfoFile = new File(Constants.vigamupDirectory+"SPC/"+baseGameName+".gameinfo");
            FileWriter fWriter2 = new FileWriter(gameinfoFile, true);
            String tracks_to_play = "";
            boolean gameInfoWritten = false;

            for (File file: files) {
                String fileNameNoPath = file.toString().substring(file.toString().lastIndexOf("/") + 1);
                if (fileNameNoPath.substring(fileNameNoPath.indexOf(".") + 1).equals("spc")) {
                    //Log.d("ViGaMuP SPC generator", "trying: " + fileNameNoPath);
                    spcTrackInfo = new String(mPlayerService.generateSpcTrackInformation(fileNameNoPath),"UTF8");
                    Log.d("ViGaMuP SPC generator", "trying: " + spcTrackInfo);
                    if (spcTrackInfo.length()>1) {
                        String[] trackInfoExploded = spcTrackInfo.split(Pattern.quote(";"));
                        fWriter.write(Integer.toString(trackNr) + "," + trackInfoExploded[0].replace(",", "") + "," + trackInfoExploded[4] + ",,," + fileNameNoPath + "\n");
                        if (!gameInfoWritten) {
                            fWriter2.write("title:" + trackInfoExploded[1].replace(",", "") + "\n");
                            fWriter2.write("vendor:" + trackInfoExploded[3] + "\n");
                            fWriter2.write("composers:" + trackInfoExploded[2].replace(",", "") + "\n");
                            gameInfoWritten = true;
                        }
                        tracks_to_play+=trackNr+",";
                    }
                    trackNr++;
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