package nl.vlessert.vigamup;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;

public class HelperFunctions {

    private static final String LOG_TAG = "ViGaMuP helpers";

    public HelperFunctions() {}

    public boolean baseDirectoryExists(String directory) {
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + directory);
        return folder.exists();
    }

    public boolean directoryExists(String directory) {
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/" + directory);
        return folder.exists();
    }

    public boolean deleteDirectory(String directory) {
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/" + directory);
        Log.d(LOG_TAG, "Delete: "+ directory);
        return folder.delete();
    }

    public static long dirSize(File dir) {
        if (dir.exists()) {
            long result = 0;
            File[] fileList = dir.listFiles();
            for(int i = 0; i < fileList.length; i++) {
                // Recursive call if it's a directory
                if(fileList[i].isDirectory()) {
                    result += dirSize(fileList [i]);
                } else {
                    // Sum the file size in bytes
                    result += fileList[i].length();
                }
            }
            return result; // return the file size
        }
        return 0;
    }

    public void deleteAllFilesInDirectory(String directory){
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/" + directory);
        if (dir.isDirectory())
        {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++)
            {
                new File(dir, children[i]).delete();
            }
        }
    }

    public void deleteFile(String directory, String file) {
        new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/" + directory + "/" + file).delete();
    }

    public void baseMakeDirectory(String directory){
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + directory);
        Log.d(LOG_TAG, "create Downloads/"+ directory + "?: " + folder.mkdir());
    }

    public File[] getAllFilesInDirectory(String directory){
        File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/" + directory);
        return f.listFiles();
    }

    public void makeDirectory(String directory){
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/" + directory);
        Log.d(LOG_TAG, "create Downloads/"+ directory + "?: " + folder.mkdir());
    }

    public void createFile(String directory, String fileName){
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/" + directory + "/" + fileName);
        Log.d(LOG_TAG,"Creating file: " + file);
        try {
            file.createNewFile();
        } catch (IOException i) {Log.d(LOG_TAG, "error: "+i.toString());}
    }
}