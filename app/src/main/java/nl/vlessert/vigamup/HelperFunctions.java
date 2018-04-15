package nl.vlessert.vigamup;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class HelperFunctions {

    private static final String LOG_TAG = "ViGaMuP helpers";
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

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

    public void moveFile(String fromString, String toString) {
        File from = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/" + fromString);
        File to = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ViGaMuP/" + toString);
        Log.d(LOG_TAG, "Moving file" + from.getAbsolutePath() + " to " + to.getAbsolutePath());
        from.renameTo(to);
    }

    public static int safeLongToInt(long l) {
        if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
            throw new IllegalArgumentException
                    (l + " cannot be cast to int without changing its value.");
        }
        return (int) l;
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public void unGunzipFile(String compressedFile, String decompressedFile) {

        byte[] buffer = new byte[1024];

        try {

            FileInputStream fileIn = new FileInputStream(compressedFile);

            GZIPInputStream gZIPInputStream = new GZIPInputStream(fileIn);

            FileOutputStream fileOutputStream = new FileOutputStream(decompressedFile);

            int bytes_read;

            while ((bytes_read = gZIPInputStream.read(buffer)) > 0) {

                fileOutputStream.write(buffer, 0, bytes_read);
            }

            gZIPInputStream.close();
            fileOutputStream.close();

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void unzip(File zipFile, File targetDirectory) throws IOException {
        ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)));
        try {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDirectory, ze.getName());
                Log.d("vigamup", ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs())
                    throw new FileNotFoundException("Failed to ensure directory: " +
                            dir.getAbsolutePath());
                if (ze.isDirectory())
                    continue;
                FileOutputStream fout = new FileOutputStream(file);
                try {
                    while ((count = zis.read(buffer)) != -1)
                        fout.write(buffer, 0, count);
                } finally {
                    fout.close();
                }
            /* if time should be restored as well
            long time = ze.getTime();
            if (time > 0)
                file.setLastModified(time);
            */
            }
        } finally {
            zis.close();
        }
    }

    public static void unzipFile(File zipFile, File targetDirectory, String fileToExtract) throws IOException {
        ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)));
        try {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.toString().equals(fileToExtract)) {
                    File file = new File(targetDirectory, ze.getName());
                    Log.d("vigamup", "match found!!: " + ze.getName());
                    File dir = ze.isDirectory() ? file : file.getParentFile();
                    if (!dir.isDirectory() && !dir.mkdirs())
                        throw new FileNotFoundException("Failed to ensure directory: " +
                                dir.getAbsolutePath());
                    if (ze.isDirectory())
                        continue;
                    FileOutputStream fout = new FileOutputStream(file);
                    try {
                        while ((count = zis.read(buffer)) != -1)
                            fout.write(buffer, 0, count);
                    } finally {
                        fout.close();
                    }
            /* if time should be restored as well
            long time = ze.getTime();
            if (time > 0)
                file.setLastModified(time);
            */
                } /*else {
                    Log.d("vigamup", "skipping: " + ze.toString());
                }*/
            }
        } finally {
            zis.close();
        }
    }
}