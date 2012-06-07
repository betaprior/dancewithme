package com.dnquark.dancewithme;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

class DLMediaPlayer {

    private final DanceWithMe danceWithMe;
    private Timer timer;
    private MyTimerTask ttask;
    private MediaPlayer player;
    private String testFilePath;
    private File testFile;
    private long tOn = 0;
    private boolean playerIsReady = false;
    private static int MEDIA_PREPARED = 0;
    private static int MEDIA_STOPPED = 1;
    private static int MEDIA_PLAYING = 2;
    private int state = -1;
    
    private class MyTimerTask extends TimerTask {
        @Override
        public void run() {
            Date d = new Date();
            SimpleDateFormat tsFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
            Log.d(TAG, "RUNNING!!! " + tsFormat.format(d).toString());
            if (playerIsReady) { 
                player.start();
                state = MEDIA_PLAYING;
            } //else
               // danceWithMe.displayToast("Media error");
            }
      }


    DLMediaPlayer(DanceWithMe danceWithMe) {
        this.danceWithMe = danceWithMe;
        tOn = System.currentTimeMillis() - System.nanoTime() / 1000000;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(danceWithMe);
        testFilePath = prefs.getString("prefsMediaSyncTestFile", "");
        testFile = new File(testFilePath);
        playerInit();
    }

    private static final String TAG = "DanceWithMe Media Player";
    private long offset = 0;
    
    private void playerInit() {
        if (!testFile.exists()) {
            Log.d(TAG, "Can't find file");
            playerIsReady = false;
            return;
        }
        if (player == null) {
            player = MediaPlayer.create(danceWithMe, Uri.fromFile(testFile));
            playerIsReady = true;
            state = MEDIA_PREPARED;
        } else {
            player.reset();
            try {
                player.setDataSource(danceWithMe, Uri.fromFile(testFile));
                player.prepare();
                playerIsReady = true;
                state = MEDIA_PREPARED;
            } catch (Exception e) { }

        }

    }
    
    public void scheduleTestEvent(long tBdy) {
      
        if (timer != null) timer.cancel();
        timer = new Timer();
        ttask = new MyTimerTask();
        long schTime = getScheduledTloc(System.currentTimeMillis(), tBdy);
        double secToSchTime = (schTime - System.currentTimeMillis()) / (double) 1000;
        SimpleDateFormat tsFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
        if (secToSchTime <= 1) {
            danceWithMe.displayToast("Too close to bdy; try again");
            return;
        } else
            danceWithMe.displayToast("SCHEDULING");
        
        long actualNTPSchedTime = tlocToTs(schTime);
        Date ntpSchedDate = new Date(actualNTPSchedTime);
        Log.d(TAG, "SCHEDULE actual NTP date: " + tsFormat.format(ntpSchedDate).toString() + "; EPOCH: " + Long.toString(actualNTPSchedTime));
        Date schDate = new Date(schTime);
        Date now = new Date();
        
        Log.d(TAG, "Now is " + tsFormat.format(now).toString() + "; next " + Long.toString(tBdy/1000) + "-second bdy is in " + Long.toString((long)secToSchTime) + " seconds; scheduling for " + tsFormat.format(schDate).toString());
      
//        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(danceWithMe);
//        testFilePath = prefs.getString("prefsMediaSyncTestFile", "");
//        testFile = new File(testFilePath);
        Log.d(TAG, "testfilepath: " + testFilePath);
        playerInit();
        timer.schedule(ttask, schDate);
                
    }
    
    public void setOffset(long o) { offset = o; }
    public long getOffset() { return offset; }
    public void setOffsetFromNTP(SntpClient ntpClient) {
        tOn = System.currentTimeMillis() - System.nanoTime() / 1000000;
        offset = tOn + ntpClient.getNtpTimeReference() - ntpClient.getNtpTime();
    }
    public long tlocToTs(long tloc) { return tloc - offset; }
    public long tsToTloc(long ts) { return ts + offset; }
    public long getScheduledTloc(long tTrigSet, long tBdy) {
        Log.d(TAG, "ts msec rounded: " + Long.toString((tlocToTs(tTrigSet) / tBdy + 1) * tBdy));
        return tsToTloc((tlocToTs(tTrigSet) / tBdy + 1) * tBdy);
    }
    public void stopTTask() { 
        ttask.cancel(); if (timer != null) { timer.purge(); timer.cancel(); } 
        if (state == MEDIA_PLAYING) {
            player.stop();
            state = MEDIA_STOPPED;
        }
    }
}
