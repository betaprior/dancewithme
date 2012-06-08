package com.dnquark.dancewithme;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

public class FileExplore extends Activity {

    // Stores names of traversed directories
    ArrayList<String> str = new ArrayList<String>();

    // Check if the first level of the directory structure is the one showing
    private Boolean firstLvl = true;

    private static final String TAG = "F_PATH";

    private Item[] fileList;
    private File path = new File(Environment.getExternalStorageDirectory() + "");
    private String chosenFile;
    private static final int DIALOG_LOAD_FILE = 1000;

    ListAdapter adapter;
    
    public FileExplore() { super(); }
    
    public FileExplore(File path) {
        super();
        if (path != null && path.exists())
            this.path = path;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        loadFileList();

        showDialog(DIALOG_LOAD_FILE);
        Log.d(TAG, path.getAbsolutePath());

    }

    private void loadFileList() {
        try {
            path.mkdirs();
        } catch (SecurityException e) {
            Log.e(TAG, "unable to write on the sd card ");
        }

        // Checks whether path exists
        if (path.exists()) {
            FilenameFilter filter = new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String filename) {
                        File sel = new File(dir, filename);
                        // Filters based on whether the file is hidden or not
                        return (sel.isFile() || sel.isDirectory())
                            && !sel.isHidden();

                    }
                };

            String[] fList = path.list(filter);
            fileList = new Item[fList.length];
            for (int i = 0; i < fList.length; i++) {
                fileList[i] = new Item(fList[i], R.drawable.file_icon);
                fileList[i].setFile(path, fList[i]);
            }
            
            Arrays.sort(fileList);
            
            if (!firstLvl) {
               Item temp[] = new Item[fileList.length + 1];
                for (int i = 0; i < fileList.length; i++) {
                    temp[i + 1] = fileList[i];
                }
                temp[0] = new Item("Up", R.drawable.directory_up);
                fileList = temp;
            }
        } else {
            Log.e(TAG, "path does not exist");
        }

        adapter = new ArrayAdapter<Item>(this,
                android.R.layout.select_dialog_item, android.R.id.text1,
                fileList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                // creates view
                View view = super.getView(position, convertView, parent);
                TextView textView = (TextView) view.findViewById(android.R.id.text1);

                // put the image on the text view
                textView.setCompoundDrawablesWithIntrinsicBounds(
                        fileList[position].icon, 0, 0, 0);

                // add margin between image and text (support various screen
                // densities)
                int dp5 = (int) (5 * getResources().getDisplayMetrics().density + 0.5f);
                textView.setCompoundDrawablePadding(dp5);

                return view;
            }
        };

    }

    private class Item implements Comparable {
        public String file;
        public int icon;
        public boolean isDir = false;
        public boolean isValid = false;
        private File f;
        
        public Item(String file, Integer icon) {
            this.file = file;
            this.icon = icon;
        }
        
        public void setFile(String path) { 
            f = new File(path); 
            isValid = f.exists();
            isDir = f.isDirectory();
            if (isDir) icon = R.drawable.directory_icon;
        }
        public void setFile(File parPath, String childPath) { 
            f = new File(parPath, childPath); 
            isValid = f.exists();
            isDir = f.isDirectory();
            if (isDir) icon = R.drawable.directory_icon;
        }
        public File getFile() { return f; }
        
        public int compareTo(Object o1) {
            if (this.isDir) {
                if (!((Item)o1).isDir)
                    return -1;
            } else {
                if (((Item)o1).isDir)
                    return 1;
            }
            return this.file.toLowerCase().compareTo(((Item)o1).file.toLowerCase());
        }

        @Override
        public String toString() {
            return file;
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;
        AlertDialog.Builder builder = new Builder(this);

        if (fileList == null) {
            Log.e(TAG, "No files loaded");
            dialog = builder.create();
            return dialog;
        }

        switch (id) {
        case DIALOG_LOAD_FILE:
            builder.setTitle("Choose your file");
            builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        chosenFile = fileList[which].file;
                        File sel = new File(path + "/" + chosenFile);
                        if (sel.isDirectory()) {
                            firstLvl = false;

                            // Adds chosen directory to list
                            str.add(chosenFile);
                            fileList = null;
                            path = new File(sel + "");

                            loadFileList();

                            removeDialog(DIALOG_LOAD_FILE);
                            showDialog(DIALOG_LOAD_FILE);
                            Log.d(TAG, path.getAbsolutePath());

                        }

                        // Checks if 'up' was clicked
                        else if (chosenFile.equalsIgnoreCase("up") && !sel.exists()) {

                            // present directory removed from list
                            String s = str.remove(str.size() - 1);

                            // path modified to exclude present directory
                            path = new File(path.toString().substring(0,
                                            path.toString().lastIndexOf(s)));
                            fileList = null;

                            // if there are no more directories in the list, then
                            // its the first level
                            if (str.isEmpty()) {
                                firstLvl = true;
                            }
                            loadFileList();

                            removeDialog(DIALOG_LOAD_FILE);
                            showDialog(DIALOG_LOAD_FILE);
                            Log.d(TAG, path.getAbsolutePath());

                        }
                        // File picked
                        else {
                            // Perform action with file picked
                        }

                    }
                });
            break;
        }
        dialog = builder.show();
        return dialog;
    }

}
