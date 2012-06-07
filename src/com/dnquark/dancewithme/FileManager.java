package com.dnquark.dancewithme;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.os.Environment;

class FileManager {
    /**
     * 
     */
    private final DanceWithMe danceWithMe;
    public static final String SAVE_FILEDIR = "dancewithme";
    public static final String SAVE_FILENAME_BASE = "dancewithme";
    private static final String SAVE_FILENAME_BASE_TS = "dl";
    private File root, storeDir;
    private File[] files;
    private List<String> filenames;
    private String currentFilename;

    public FileManager(DanceWithMe danceWithMe) {
        this.danceWithMe = danceWithMe;
        root = Environment.getExternalStorageDirectory();
        storeDir = new File(root, FileManager.SAVE_FILEDIR);
        if (! storeDir.isDirectory()) 
            storeDir.mkdir();
        filenames = new ArrayList<String>();
        currentFilename = "";
    }
    public File getDataDir() { return storeDir; } 
    public String makeTsFilename() {
        currentFilename = SAVE_FILENAME_BASE_TS + "-" + DanceWithMe.DEVICE_ID  + "-" + this.danceWithMe.getTimestamp();
        if (DanceWithMe.DEBUG_FILENAME) currentFilename = "test";
        return currentFilename;
    }
    public String getTsFilename() {
        if (currentFilename.equals(""))
            return makeTsFilename();
        else
            return currentFilename;
    }

    public List<String> getFileListing() {
        filenames.clear();
        files = storeDir.listFiles();
        for (int i = files.length - 1; i >= 0; i--) 
            filenames.add(files[i].getName());
        return filenames;
    }

    public void deleteFiles() {
        for (File f : storeDir.listFiles())
            deleteRecursive(f);
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        fileOrDirectory.delete();
    }


}
