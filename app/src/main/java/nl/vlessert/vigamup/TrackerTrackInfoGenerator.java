package nl.vlessert.vigamup;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.regex.Pattern;

public class TrackerTrackInfoGenerator implements Runnable{

    private File zipFile;
    private static final String LOG_TAG = "ViGaMuP_Tracker";

    private HelperFunctions helpers;
    private PlayerService mPlayerService;
    private String fileName;
    private Context context;
    private MainActivity mainActivity;

    public TrackerTrackInfoGenerator(PlayerService ps, File zf, String fn, Context cx, MainActivity ma){
        mPlayerService = ps;
        zipFile = zf;
        fileName = fn;
        context = cx;
        mainActivity = ma;
    }
    @Override
    public void run() {
        int trackNr = 1;
        helpers = new HelperFunctions();

        try {
            File destinationFolder = new File(Constants.vigamupDirectory+"/Trackers/"+fileName);
            Log.d(LOG_TAG, destinationFolder.toString());

            if (helpers.directoryExists(destinationFolder.toString())){
                helpers.deleteAllFilesInDirectory(destinationFolder.toString());
            } else {
                helpers.makeDirectory(destinationFolder.toString());
            }

            helpers.unzip(zipFile, destinationFolder);

            Log.d(LOG_TAG, "Unzipped in " + destinationFolder.toString());

            String modifiedPath = destinationFolder.toString().substring(destinationFolder.toString().indexOf("/Trackers/")+1);

            String trackerTrackInfo = "";

            try {
                trackerTrackInfo = new String(mPlayerService.generateTrackerTrackInformation(modifiedPath),"UTF8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            Log.d(LOG_TAG, trackerTrackInfo);

            if (trackerTrackInfo.length()>1) {
                String trackInfoExploded[];
                trackInfoExploded = trackerTrackInfo.split(Pattern.quote(";"));

                File trackinfoFile = new File(Constants.vigamupDirectory + "Trackers/" + fileName + ".trackinfo");
                FileWriter fWriter = new FileWriter(trackinfoFile, false);
                File gameinfoFile = new File(Constants.vigamupDirectory + "Trackers/" + fileName + ".gameinfo");
                FileWriter fWriter2 = new FileWriter(gameinfoFile, false);
                fWriter2.write("title:" + fileName + "\n");

                String trackerfileName, extension, tracks_to_play = "";
                Log.d(LOG_TAG, "number of files: " + trackInfoExploded.length);
                for (int counter = 0; counter < trackInfoExploded.length; counter++) {
                    trackerfileName = helpers.extracted_files.get(counter).toString();
                    extension = trackerfileName.substring(trackerfileName.lastIndexOf(".") + 1);

                    int trackLength = Integer.parseInt(trackInfoExploded[counter]);

                    Log.d(LOG_TAG, "filename: " + trackerfileName + ",extension: " + extension + ",length: "+ trackLength);
                    String humanReadeableTrackerFileName = trackerfileName.replace("_", " ").replace(",", " ");
                    humanReadeableTrackerFileName = humanReadeableTrackerFileName.substring(0,humanReadeableTrackerFileName.lastIndexOf("."));

                    switch (extension) {
                        case "xm":
                        case "it":
                        case "s3m":
                        case "mod":
                            fWriter.write(trackNr + "," + humanReadeableTrackerFileName + "," + trackLength + ",,," + trackerfileName + "\n");
                            tracks_to_play += trackNr + ",";
                            trackNr++;
                            break;
                        default:
                            break;
                    }
                }
                Log.d(LOG_TAG, " tracks_to_play: " + tracks_to_play);
                fWriter.flush();
                fWriter.close();
                fWriter2.write("tracks_to_play:" + tracks_to_play.substring(0, tracks_to_play.length() - 1));
                fWriter2.flush();
                fWriter2.close();
            }
        } catch (IOException test) {
            Log.d(LOG_TAG, "Unzip error... very weird");
        }

        mainActivity.forceRefreshMusicList();
    }
}
