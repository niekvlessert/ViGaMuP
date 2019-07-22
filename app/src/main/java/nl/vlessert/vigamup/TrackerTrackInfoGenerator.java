package nl.vlessert.vigamup;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;

public class TrackerTrackInfoGenerator implements Runnable{

    private String zipFile;
    private static final String LOG_TAG = "ViGaMuP_Tracker";

    private HelperFunctions helpers;
    private PlayerService mPlayerService;
    private Context context;

    public TrackerTrackInfoGenerator(PlayerService ps, String zf, Context cx){
        mPlayerService = ps;
        zipFile = zf;
        context = cx;
    }
    @Override
    public void run() {
        helpers = new HelperFunctions();
        int trackNr = 1;
        File file = new File(zipFile);

        if (helpers.directoryExists("tmp/tracker_tmp")){
            helpers.deleteAllFilesInDirectory(("tmp/tracker_tmp"));
        } else {
            helpers.makeDirectory("tmp/tracker_tmp");
        }

        final File destinationFolder = new File(Constants.vigamupDirectory+"tmp/tracker_tmp");

        try {
            helpers.unzip(file, destinationFolder);
        }catch (IOException test) {
            Log.d(LOG_TAG, "Unzip error... very weird");
        }

        //create a txt file with filename, title, length from every file in there in Trackers directory. Filename similar to zip file but with .txt
        //Do we need c for that?

        //Remove tmp files and directory
    }
}
