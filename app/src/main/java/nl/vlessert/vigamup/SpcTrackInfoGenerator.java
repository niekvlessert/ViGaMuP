package nl.vlessert.vigamup;

import android.util.Log;

import java.io.File;

public class SpcTrackInfoGenerator implements Runnable{

    private String rarFile;

    private HelperFunctions helpers;
    private PlayerService mPlayerService;

    public SpcTrackInfoGenerator(PlayerService ps, String rf){
        mPlayerService = ps;
        rarFile = rf;
    }

    @Override
    public void run() {
        String spcTrackInfo;
        helpers = new HelperFunctions();
        //gameFileName = rarFile.substring(0, rarFile.indexOf("."));

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
        helpers.createFile("SPC", rarFile.substring(0,rarFile.lastIndexOf(".")-1)+".trackinfo");
        helpers.createFile("SPC", rarFile.substring(0,rarFile.lastIndexOf(".")-1)+".gameinfo");
        for (File file: files) {
            String fileNameNoPath = file.toString().substring(file.toString().lastIndexOf("/") + 1);
            if (fileNameNoPath.substring(fileNameNoPath.indexOf(".") + 1).equals("spc")) {
                //Log.d("ViGaMuP SPC generator", "trying: " + fileNameNoPath);
                Log.d("ViGaMuP SPC generator", "String: "+mPlayerService.generateSpcTrackInformation(fileNameNoPath));
            }
        }
    }
}