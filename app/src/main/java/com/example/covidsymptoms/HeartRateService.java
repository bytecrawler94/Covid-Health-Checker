package com.example.covidsymptoms;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Heart Rate service runs in background to extract frames from 9 5-second windows of the heart rate video
 * and returns them to the HomeScreen activity for processing
 */
public class HeartRateService extends Service {

    private Bundle b = new Bundle();
    private String rootPath = Environment.getExternalStorageDirectory().getPath();
    private int windows = 9;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        System.gc();
        Toast.makeText(this, "Processing video...", Toast.LENGTH_LONG).show();
        Log.i("log", "Heart Rate service started");

        //Multithreading for frame extracting from 9 5-second windows of heart rate video
        HeartRateWindowSplitting runnable = new HeartRateWindowSplitting();
        Thread thread = new Thread(runnable);
        thread.start();

        return START_STICKY;
    }

    /**
     * Runnable for multithreading of processing frames function
     */
    public class HeartRateWindowSplitting implements Runnable {
        @Override
        public void run() {

            ExecutorService executor = Executors.newFixedThreadPool(6);
            List<FrameExtractor> taskList = new ArrayList<>();

            //Only 45 seconds of video processed as 9 5-second windows, even if video length exceeds 45 seconds
            for (int i = 0; i < windows; i++) {
                FrameExtractor frameExtractor = new FrameExtractor(i * 5);
                taskList.add(frameExtractor);
            }

            List<Future<ArrayList<Integer>>> resultList = null;
            try {
                resultList = executor.invokeAll(taskList);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            executor.shutdown();
            System.gc();

            for (int i = 0; i < resultList.size(); i++) {

                Future<ArrayList<Integer>> future = resultList.get(i);
                try {
                    b.putIntegerArrayList("heartData" + i, future.get());
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    e.getCause();
                }
            }
            //Service stopped once frames are extracted
            stopSelf();
        }
    }

    /** Class for Frame extraction and average redness calculation
     *
     */
    public class FrameExtractor implements Callable<ArrayList<Integer>> {
        private int startTime;

        FrameExtractor(int startTime){
            this.startTime = startTime;
        }

        /** Method for Frame extraction and average redness calculation
         *
         * @return ArrayList of average redness of frames
         */
        @RequiresApi(api = Build.VERSION_CODES.P)
        private ArrayList<Integer> getFrames(){
            Bitmap bitmap = null;

            try {
                String path = rootPath + "/finger_tip.mp4";
                ArrayList<Integer> avgColorArr = new ArrayList<>();
                FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(path);
                AndroidFrameConverter converterToBitMap = new AndroidFrameConverter();
                grabber.start();
                grabber.setTimestamp(startTime*1000000);
                double frameRate = grabber.getFrameRate();

                for (int i = 0; i < 5*frameRate;) {
                    Frame frame = grabber.grabFrame();
                    if (frame == null) {
                        break;
                    }
                    if (frame.image == null) {
                        continue;
                    }
                    i++;
                    Log.i("log", "Processing frame " + i);
                    System.gc();


                    bitmap = converterToBitMap.convert(frame);
                    int avgColor = getAverageColor(bitmap);

                    avgColorArr.add(avgColor);
                }

                return avgColorArr;

            } catch(Exception e) {
                Log.e("FrameError",e.toString());
                System.out.println(e.toString());
            }
            return null;
        }

        @RequiresApi(api = Build.VERSION_CODES.P)
        @Override
        public ArrayList<Integer> call() {

            ArrayList<Integer> rednessData = new ArrayList<>();
            try {
                rednessData = getFrames();

            } catch (Exception e) {
                e.printStackTrace();
            }
            return rednessData;
        }
    }

    /**
     * Calculates average redness value of a frame's bitmap
     * @param bitmap Bitmap of the extracted frame
     * @return Average red value
     */
    private int getAverageColor(Bitmap bitmap){

        long redBucket = 0;
        long pixelCount = 0;

        //Optimised by taking every 5th pixel from every column and row
        for (int y = 0; y < bitmap.getHeight(); y+=5) {
            for (int x = 0; x < bitmap.getWidth(); x+=5) {
                int c = bitmap.getPixel(x, y);
                pixelCount++;
                redBucket += Color.red(c);
            }
        }

        return (int)(redBucket / pixelCount);
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Sends average redness data of extracted frames in a broadcast
     *
     */
    @Override
    public void onDestroy() {

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i("log", "Heart Service stopping");
                Intent intent = new Intent("broadcastingHeartData");
                intent.putExtras(b);
                LocalBroadcastManager.getInstance(HeartRateService.this).sendBroadcast(intent);
                b.clear();
                System.gc();
            }
        });

        thread.start();
    }


}
