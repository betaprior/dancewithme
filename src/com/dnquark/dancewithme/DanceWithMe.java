package com.dnquark.dancewithme;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.dropbox.client2.*;
import com.dropbox.client2.android.*;
import com.dropbox.client2.exception.*;
import com.dropbox.client2.session.*;
import com.dropbox.client2.session.Session.AccessType;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.DropBoxManager.Entry;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
//import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;


public class DanceWithMe extends Activity implements OnSharedPreferenceChangeListener {

    public static String DEVICE_ID;
    public static boolean DEBUG_FILENAME = false; 
    
    private static final String TAG = "DanceWithMe";  
    private static final String APP_KEY = "9r1k9vh8ge076h5";
    private static final String APP_SECRET = "e18afra2tklir0n";
    private static final AccessType ACCESS_TYPE = AccessType.APP_FOLDER;
    private static final String DBOX_APP_DIR_PATH = "Apps/DanceWithMe"; 
    static final String PREFS_DBOX_KEY = "dropboxAccesTokenKey";
    static final String PREFS_DBOX_SECRET = "dropboxAccesTokenSecret";
    private DropboxAPI<AndroidAuthSession> mDBApi;
    private AppKeyPair dboxAppKeys;
    private AndroidAuthSession dboxSession;
    
    private final int MENU_PREFS=1, MENU_NTP_SYNC=2, MENU_CHOOSE_FILE=3, MENU_FORCE_REAUTH=4;
    
    private final int GROUP_DEFAULT=0;
    
    static final String EOL = String.format("%n");

    private TextView ntpStatusText, recordingFileText;
    private TextView atomicHour, atomicMinute, atomicSecond;
    private ListView fileListView;
    
    private Button syncButton, stopButton;
    
    private Button syncPlayButton, syncPlayStopButton, delayPosButton, delayNegButton;
    
    private Chronometer chronometer;
    
    SharedPreferences prefs;
    SharedPreferences appDataPrefs;
    static final String DATA_PREFS_FILENAME = "datastorePrefs";
    FileManager fileManager;
    SntpClient ntpClient;
    DLMediaPlayer mediaPlayer;

    private PowerManager pm;
    private PowerManager.WakeLock wl;
    
    private long ntpOffset = 0;
    
    private boolean buttonTimerDone = false;
    private static final int BUTTON_HOLD_INTERVAL = 2000;
    private Handler btnHoldHandler = new Handler(); 

    private Calendar atomic, now;
    private int oldAtAmPm = -1;
    private int oldAtHour = -1;
    private int oldAtMinute = -1;
    private int oldAtSecond = -1;
    
    public static String pad(long num) {
        String str = String.valueOf(num);
        if (num < 10L) 
            str = "0" + str;
        return str;
    }

    private final Handler timeHandler = new Handler();
    private final Runnable updateTimeTask = new Runnable() {
            public void run() {
                now.setTimeInMillis(System.currentTimeMillis());
                if (DanceWithMe.this.ntpOffset == 0) // TODO: better indication of invalidity
                    atomic.setTimeInMillis(System.currentTimeMillis());
                else
                    atomic.setTimeInMillis(System.currentTimeMillis() + ntpOffset);
                int hh_at = DanceWithMe.this.atomic.get(Calendar.HOUR_OF_DAY);
                int mm_at = DanceWithMe.this.atomic.get(Calendar.MINUTE);
                int ss_at = DanceWithMe.this.atomic.get(Calendar.SECOND);
                // int hh_loc = DanceWithMe.this.now.get();
                // int mm_loc = DanceWithMe.this.now.get();
                // int ss_loc = DanceWithMe.this.now.get();
                if (DanceWithMe.this.oldAtHour != hh_at) {
                    DanceWithMe.this.atomicHour.setText(pad(hh_at));
                    DanceWithMe.this.oldAtHour = hh_at;
                }
                if (DanceWithMe.this.oldAtMinute != mm_at) {
                    DanceWithMe.this.atomicMinute.setText(pad(mm_at));
                    DanceWithMe.this.oldAtMinute = mm_at;
                }
                if (DanceWithMe.this.oldAtSecond != ss_at) {
                    DanceWithMe.this.atomicSecond.setText(pad(ss_at));
                    DanceWithMe.this.oldAtSecond = ss_at;
                }
                int ms_til_update = 1000 - DanceWithMe.this.atomic.get(Calendar.MILLISECOND);
                DanceWithMe.this.timeHandler.postDelayed(this, ms_til_update);
                
            }
        };

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        initComponents();
        updateFileList();
        
        new GetNtpTime().execute();
        timeHandler.post(this.updateTimeTask);
        Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler(
                        "/sdcard/dancewithme/", null));
    }

    private void initComponents() {
        fileManager = new FileManager(this);
        ntpClient = new SntpClient();
        mediaPlayer = new DLMediaPlayer(this);
        
        atomic = Calendar.getInstance();
        now = Calendar.getInstance();
        
        dboxAppKeys = new AppKeyPair(APP_KEY, APP_SECRET);
        dboxSession = new AndroidAuthSession(dboxAppKeys, ACCESS_TYPE);
        mDBApi = new DropboxAPI<AndroidAuthSession>(dboxSession);
        
        appDataPrefs = getSharedPreferences(DATA_PREFS_FILENAME, MODE_PRIVATE);

        atomicHour = (TextView) findViewById(R.id.a_hour);
        atomicMinute = (TextView) findViewById(R.id.a_minute);
        atomicSecond = (TextView) findViewById(R.id.a_second);

        recordingFileText = (TextView) findViewById(R.id.textViewStatusRecFile);
        ntpStatusText = (TextView) findViewById(R.id.textViewStatusNtp);
        recordingFileText.setText("Music in: " + fileManager.getDataDir().getPath());

        syncPlayButton = (Button) findViewById(R.id.syncPlayButton1);
        syncPlayStopButton = (Button) findViewById(R.id.syncPlayStopButton1);
        delayPosButton = (Button) findViewById(R.id.syncDelayPosButton1);
        delayNegButton = (Button) findViewById(R.id.syncDelayNegButton1);


        chronometer = (Chronometer) findViewById(R.id.chronometer1);
        initializeChronometer();
        
        fileListView = (ListView) findViewById(R.id.filelist);
  
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
        prefs = PreferenceManager.getDefaultSharedPreferences(this); 
        prefs.registerOnSharedPreferenceChangeListener(this);
    }
    
    private View.OnTouchListener myButtonLongPressListener(final Runnable delayedAction) {
        return new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()){
                case MotionEvent.ACTION_DOWN:
                      buttonTimerDone = true;
                      displayToast("Keep holding...");
                      // Handler handler = new Handler(); 
                      // handler.postDelayed(delayedAction, BUTTON_HOLD_INTERVAL);
                      btnHoldHandler.postDelayed(delayedAction, BUTTON_HOLD_INTERVAL);
                    break;
                case MotionEvent.ACTION_MOVE:
                    break;
                case MotionEvent.ACTION_UP:
                    buttonTimerDone = false;
                    btnHoldHandler.removeCallbacks(delayedAction);
                    break;
                }
                return true;
            }
        };
    }
    
  
    private void initializeChronometer() {
        chronometer.setOnChronometerTickListener(
                new Chronometer.OnChronometerTickListener(){
                    public void onChronometerTick(Chronometer chr) {
                        long ts = SystemClock.uptimeMillis();
                        String chrText = chr.getText().toString();            
                    }}
                );
    }

 
   
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(GROUP_DEFAULT, MENU_PREFS, 0, "Preferences");
        menu.add(GROUP_DEFAULT, MENU_NTP_SYNC, 0, "NTP Sync");
        menu.add(GROUP_DEFAULT, MENU_CHOOSE_FILE, 0, "Choose file");
        menu.add(GROUP_DEFAULT, MENU_FORCE_REAUTH, 0, "Dbox reauth");
        return super.onCreateOptionsMenu(menu);
    }
    
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
       //TODO: update the file listing if new directory was set
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_PREFS:
            startActivity(new Intent(this, PrefsActivity.class));
            return true;
        case MENU_NTP_SYNC:
            new GetNtpTime().execute();
            return true;
        case MENU_CHOOSE_FILE:
            chooseFile();
            return true;
        case MENU_FORCE_REAUTH:
            SharedPreferences.Editor appDataPrefsEditor = appDataPrefs.edit();
            appDataPrefsEditor.remove(PREFS_DBOX_KEY);
            appDataPrefsEditor.remove(PREFS_DBOX_SECRET);
            appDataPrefsEditor.commit();
            chooseFile();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
 
    public void chooseFile() {
        AccessTokenPair savedTokens = getDboxAccessTokenFromPrefs();
        if (savedTokens == null) {
            Log.d(TAG, "got NULL for saved tokens; authenticating...");
            mDBApi.getSession().startAuthentication(DanceWithMe.this);
        }
        else
            dboxSession = new AndroidAuthSession(dboxAppKeys, ACCESS_TYPE, savedTokens);
        try {
            DropboxAPI.Entry dirDataEntry = mDBApi.metadata("/", 0, null, true, null);
            for (DropboxAPI.Entry item : dirDataEntry.contents) {
                Log.d(TAG, "DBOX DIRLIST: " + item.path);
            }
        
    } catch (DropboxUnlinkedException e) {
        // User has unlinked, ask them to link again here.
        Log.e(TAG, "DBOX: User has unlinked.");
    } catch (DropboxServerException e) {
        Log.e(TAG, "DBOX: SERVER EXC");
    } catch (DropboxIOException e) {
        Log.e(TAG, "DBOX IO EXC");
    } catch (DropboxException e) {
        Log.e(TAG, "DBOX: Error while listing appdir metadata");
        }
        }
 
    
    /* invoked via android:onClick="myClickHandler" in the button entry of the layout XML */
    public void myClickHandler(View view) {
        switch (view.getId()) {
        case R.id.syncPlayButton1:
            mediaPlayer.scheduleTestEvent(5000L);
            break;
        case R.id.syncPlayStopButton1:
            mediaPlayer.stopTTask();
            break;
        }
    }
    
   
 


    public void updateFileList() {
        List<String> filenames = fileManager.getFileListing();
        if (filenames == null) return;
        Collections.sort(filenames, Collections.reverseOrder());
        String tsDateEOL = ".*\\d{8}-\\d{6}$";
        List<String> filenamesFiltered = new ArrayList<String>();
        for (String l : filenames)
            if (l.matches(tsDateEOL))
                filenamesFiltered.add(l);
        ArrayAdapter<String> fileList =
            new ArrayAdapter<String>(this, R.layout.row, filenamesFiltered);

        fileListView.setAdapter(fileList);

    }
   
 
    void displayToast(String msg) {
        Toast.makeText(getBaseContext(), msg, 
                Toast.LENGTH_SHORT).show();        
    }    

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
     
  
    
    public String getTimestamp() {
        Date dateNow = new Date ();
        SimpleDateFormat tsFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
        return tsFormat.format(dateNow).toString();
    }
    
    protected void onResume() {
        super.onResume();
        if (mDBApi.getSession().authenticationSuccessful()) {
            try {
                // MANDATORY call to complete auth.
                // Sets the access token on the session
                mDBApi.getSession().finishAuthentication();
                
                AccessTokenPair tokens = mDBApi.getSession().getAccessTokenPair();
                
                // Provide your own storeKeys to persist the access token pair
                // A typical way to store tokens is using SharedPreferences
                storeKeys(tokens.key, tokens.secret);
            } catch (IllegalStateException e) {
                Log.i("DbAuthLog", "Error authenticating", e);
            }
        }
        
    }
    
    private void storeKeys(String key, String secret) {
        SharedPreferences.Editor appDataPrefsEditor = appDataPrefs.edit();
        appDataPrefsEditor.putString(PREFS_DBOX_KEY, key);
        appDataPrefsEditor.putString(PREFS_DBOX_SECRET, secret);
        appDataPrefsEditor.commit();
    }

    private AccessTokenPair getDboxAccessTokenFromPrefs() {
        String key = appDataPrefs.getString(PREFS_DBOX_KEY, null);
        String secret = appDataPrefs.getString(PREFS_DBOX_SECRET, null);

        if (key == null || secret == null)
            return null;
        else
            return new AccessTokenPair(key, secret);
    }
    
    class GetNtpTime extends AsyncTask<Void, Void, Boolean> {
        private String NTP_SERVER = "pool.ntp.org";
        private int NTP_TIMEOUT_MS = 2000;
        private int RETRIES = 3;
        private int RETRY_INTERVAL = 500;
        
        @Override
        protected Boolean doInBackground(Void... v) { //
            // long ntptime, ntptimeref;
            for (int i = 0; i < RETRIES; i++) {
                if (ntpClient.requestTime(NTP_SERVER, NTP_TIMEOUT_MS)) {
                    ntpOffset = ntpClient.getClockOffset();
                    // Log.d(TAG, "Nano->ms time: " + Long.toString(System.nanoTime() / 1000000));
                    // Log.d(TAG, "NTPRef: " + Long.toString(ntpClient.getNtpTimeReference()));
                    return true;
                } else {
                    try {
                        Thread.sleep(RETRY_INTERVAL);
                    } catch (InterruptedException e) { }
                }
            }
            return false;
        }
            
        @Override
        protected void onProgressUpdate(Void... v) { 
            super.onProgressUpdate(v);
        }

        @Override
        protected void onPostExecute(Boolean success) { 
            if (success) {
                ntpStatusText.setText(getResources().getString(R.string.statusFieldNtp) 
                        + " " + Long.toString(ntpOffset) + " ms");
                SharedPreferences.Editor appDataPrefsEditor = appDataPrefs.edit();
                appDataPrefsEditor.putLong("ntpSyncTimeEpoch", System.currentTimeMillis()); 
                appDataPrefsEditor.putLong("ntpSyncClockOffset", ntpClient.getClockOffset());
                appDataPrefsEditor.putLong("ntpTime", ntpClient.getNtpTime());
                appDataPrefsEditor.putLong("ntpSyncTimeRef", ntpClient.getNtpTimeReference());
                appDataPrefsEditor.commit();
                
                mediaPlayer.setOffsetFromNTP(ntpClient);
            } else {
                ntpStatusText.setText(getResources().getString(R.string.statusFieldNtp) + " unavailable");
                displayToast("Failed to get time from " + NTP_SERVER);
            }
        }

    }

    
}
