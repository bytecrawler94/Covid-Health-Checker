package com.example.covidsymptoms;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Date;
import java.util.ArrayList;

import static java.lang.Math.abs;

/**
 * Main Screen with buttons for recording video, calculating breathing and heart rates, uploading signs and uploading symptoms
 */
public class HomeScreen extends AppCompatActivity {

    private static final int VIDEO_CAPTURE = 101;
    private Uri fileUri;
    private int windows = 9;
    long startExecTime;
    private TextView heartRateTextView;
    private TextView breathingRateTextView;
    // Text views to show breathing and heart rates

    private boolean uploadSignsClicked = false;
    // Used to tell if Upload Signs button has been pressed
    private boolean ongoingHeartRateProcess = false;
    private boolean ongoingBreathingRateProcess = false;
    // Used to tell if calculation process is ongoing to avoid starting duplicate processes

    private AppDatabase db;

    private String rootPath = Environment.getExternalStorageDirectory().getPath();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_signs_screen);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        Button record = findViewById(R.id.btn_record_video);
        Button recordButton = (Button) findViewById(R.id.record_button);
        Button measureHeartRateButton = (Button) findViewById(R.id.measure_heart_rate_button);
        Button measureBreathingButton = (Button) findViewById(R.id.measure_breathing);
        Button uploadSymptomsButton = (Button) findViewById(R.id.upload_symptoms_button);
        Button uploadSignsButton = (Button) findViewById(R.id.upload_signs_button);

        heartRateTextView = (TextView) findViewById(R.id.heart_rate);
        breathingRateTextView = (TextView) findViewById(R.id.breathing_rate);

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                // Create or get app database
                try{
                    db = AppDatabase.getInstance(getApplicationContext());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();

        if(!hasCamera()){
            recordButton.setEnabled(false);
        }

        // Ask user for permissions to read and write to external memory
        handlePermissions(HomeScreen.this);

        // Opens recorder on click
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(ongoingHeartRateProcess == true) {
                    Toast.makeText(HomeScreen.this, "Please wait for process to complete before recording a new video!",
                            Toast.LENGTH_SHORT).show();
                } else {
                    startRecording();
                }
            }
        });

        // Starts Accelerometer service on click
        measureHeartRateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                File videoFile = new File(rootPath + "/heart_rate.mp4");
                fileUri = Uri.fromFile(videoFile);

                // Checks if heart rate video exists and if there is an existing heart rate detection process running
                if(ongoingHeartRateProcess == true) {
                    Toast.makeText(HomeScreen.this, "Please wait for process to complete before starting a new one!",
                            Toast.LENGTH_SHORT).show();
                } else if (videoFile.exists()) {
                    ongoingHeartRateProcess = true;
                    heartRateTextView.setText("Calculating...");

                    startExecTime = System.currentTimeMillis();
                    System.gc();
                    Intent heartIntent = new Intent(HomeScreen.this, HeartRateService.class);
                    startService(heartIntent);

                } else {
                    Toast.makeText(HomeScreen.this, "Please record a video first!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Starts Heart Rate service on click
        measureBreathingButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                // Checks if there is an existing respiratory rate detection process running
                if(ongoingBreathingRateProcess == true) {
                    Toast.makeText(HomeScreen.this, "Please wait for process to complete before starting a new one!",
                            Toast.LENGTH_SHORT).show();

                } else {
                    Toast.makeText(HomeScreen.this, "Place the phone on your abdomen \nfor 45s", Toast.LENGTH_LONG).show();
                    ongoingBreathingRateProcess = true;
                    breathingRateTextView.setText("Sensing...");
                    Intent accelIntent = new Intent(HomeScreen.this, AccelerometerService.class);
                    startService(accelIntent);
                }
            }
        });

        // Updates the 10 columns of symptom ratings on click
        uploadSymptomsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(HomeScreen.this, SymptomsScreen.class);
                intent.putExtra("uploadSignsClicked", uploadSignsClicked);
                startActivity(intent);
            }
        });

        // Creates a row in the database with signs data inserted
        uploadSignsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                uploadSignsClicked = true;
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        UserInfo data = new UserInfo();
                        data.heartRate = Float.parseFloat(heartRateTextView.getText().toString());
                        data.breathingRate = Float.parseFloat(breathingRateTextView.getText().toString());
                        data.timestamp = new Date(System.currentTimeMillis());
                        db.userInfoDao().insert(data);
                    }
                });
                thread.start();

                Toast.makeText(HomeScreen.this, "Signs uploaded!", Toast.LENGTH_SHORT).show();
            }

        });

        //Listens for local broadcast containing X values sent by Accelerometer service for calculation
        LocalBroadcastManager.getInstance(HomeScreen.this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                Bundle b = intent.getExtras();
                BreathingRateDetector runnable = new BreathingRateDetector(b.getIntegerArrayList("accelValuesX"));

                Thread thread = new Thread(runnable);
                thread.start();

                try {
                    thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                breathingRateTextView.setText(runnable.breathingRate + "");

                Toast.makeText(HomeScreen.this, "Respiratory rate calculated!", Toast.LENGTH_SHORT).show();
                ongoingBreathingRateProcess = false;
                b.clear();
                System.gc();

            }
        }, new IntentFilter("broadcastingAccelData"));


        //Listens for local broadcast containing average red values of extracted frames sent by Heart rate service for calculation
        LocalBroadcastManager.getInstance(HomeScreen.this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                Bundle b = intent.getExtras();
                float heartRate = 0;
                int fail = 0;
                //Processes 9 windows of 5 second video snippets separately to calculate heart rate
                for (int i = 0; i < windows; i++) {

                    ArrayList<Integer> heartData = null;
                    heartData = b.getIntegerArrayList("heartData"+i);

                    //Removes noise from raw average redness frame data
                    ArrayList<Integer> denoisedRedness = denoise(heartData, 5);

                    //Runs peakfinding algorithm on denoised data
                    float zeroCrossings = peakFinding(denoisedRedness);
                    heartRate += zeroCrossings/2;
                    Log.i("log", "heart rate for " + i + ": " + zeroCrossings/2);

                    //Write csv files to memory with raw average redness frames data for reference
                    String csvFilePath = rootPath + "/heart_rate" + i + ".csv";
                    saveToCSV(heartData, csvFilePath);

                    //Write csv files to memory with denoised average redness frames data for reference
                    csvFilePath = rootPath + "/heart_rate_denoised" + i + ".csv";
                    saveToCSV(denoisedRedness, csvFilePath);
                }

                heartRate = (heartRate*12)/ windows;
                Log.i("log", "Final heart rate: " + heartRate);
                heartRateTextView.setText(heartRate + "");
                ongoingHeartRateProcess = false;
                Toast.makeText(HomeScreen.this, "Heart rate calculated!", Toast.LENGTH_SHORT).show();
                System.gc();
                b.clear();

            }
        }, new IntentFilter("broadcastingHeartData"));

    }

    @Override
    protected void onStart() {
        super.onStart();
        uploadSignsClicked = false;
    }

    /**
     * Class that implements runnable to process breathing rate data for calculation
     */
    public class BreathingRateDetector implements Runnable{

        public float breathingRate;
        ArrayList<Integer> accelValuesX;

        BreathingRateDetector(ArrayList<Integer> accelValuesX){
            this.accelValuesX = accelValuesX;
        }

        @Override
        public void run() {

            String csvFilePath = rootPath + "/x_values.csv";
            saveToCSV(accelValuesX, csvFilePath);

            //Noise reduction from Accelerometer X values
            ArrayList<Integer> accelValuesXDenoised = denoise(accelValuesX, 10);

            csvFilePath = rootPath + "/x_values_denoised.csv";
            saveToCSV(accelValuesXDenoised, csvFilePath);

            //Peak detection algorithm running on denoised Accelerometer X values
            int  zeroCrossings = peakFinding(accelValuesXDenoised);
            breathingRate = (zeroCrossings*60)/90;
            Log.i("log", "Respiratory rate" + breathingRate);
        }

    }

    /**
     * Reduces noise such as irregular close peaks from input data using moving average
     * @param data ArrayList data to remove noise from (average redness values/ X values)
     * @param filter Factor to use for doing moving average smoothing
     * @return Data with noise reduced
     */
    public ArrayList<Integer> denoise(ArrayList<Integer> data, int filter){

        ArrayList<Integer> movingAvgArr = new ArrayList<>();
        int movingAvg = 0;

        for(int i=0; i< data.size(); i++){
            movingAvg += data.get(i);
            if(i+1 < filter) {
                continue;
            }
            movingAvgArr.add((movingAvg)/filter);
            movingAvg -= data.get(i+1 - filter);
        }

        return movingAvgArr;

    }

    /**
     * Calculates number of times the signs of slope of the data curve is reversed to get zero crossings
     * @param data ArrayList data to remove noise from (average redness values/ X values)
     * @return Returns number of zero crossings
     */
    public int peakFinding(ArrayList<Integer> data) {

        int diff, prev, slope = 0, zeroCrossings = 0;
        int j = 0;
        prev = data.get(0);

        //Get initial slope
        while(slope == 0 && j + 1 < data.size()){
            diff = data.get(j + 1) - data.get(j);
            if(diff != 0){
                slope = diff/abs(diff);
            }
            j++;
        }

        //Get total number of zero crossings in data curve
        for(int i = 1; i<data.size(); i++) {

            diff = data.get(i) - prev;
            prev = data.get(i);

            if(diff == 0) continue;

            int currSlope = diff/abs(diff);

            if(currSlope == -1* slope){
                slope *= -1;
                zeroCrossings++;
            }
        }

        return zeroCrossings;
    }

    /**
     * Writes data to csv in internal storage for reference
     * @param data To be written to CSV
     * @param path path of csv file
     */
    public void saveToCSV(ArrayList<Integer> data, String path){

        File file = new File(path);

        try {
            FileWriter outputFile = new FileWriter(file);
            CSVWriter writer = new CSVWriter(outputFile);
            String[] header = { "Index", "Data"};
            writer.writeNext(header);
            int i = 0;
            for (int d : data) {
                String dataRow[] = {i + "", d + ""};
                writer.writeNext(dataRow);
                i++;
            }
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Verify and ask for Storae read, write and camera permissions
     * @param activity main activity
     */
    public static void handlePermissions(Activity activity) {

        int storagePermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int REQUEST_EXTERNAL_STORAGE = 1;

        String[] PERMISSIONS = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA

        };

        if (storagePermission != PackageManager.PERMISSION_GRANTED) {
            Log.i("log", "Read/Write Permissions needed!");
        }

        ActivityCompat.requestPermissions(
                activity,
                PERMISSIONS,
                REQUEST_EXTERNAL_STORAGE
        );

        Log.i("log", "Permissions Granted!");

    }

    /**
     * Check if camera exists on device
     * @return
     */
    private boolean hasCamera() {

        if (getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_CAMERA_ANY)){
            return true;
        } else {
            return false;
        }
    }

    /**
     * Creates and starts intent to record video for heart rate
     */
    public void startRecording() {

        File mediaFile = new File( rootPath + "/heart_rate.mp4");
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT,45);

        fileUri = Uri.fromFile(mediaFile);

        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
        startActivityForResult(intent, VIDEO_CAPTURE);
    }

    /**
     * Handles saving recording
     * @param requestCode
     * @param resultCode
     * @param data
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        boolean deleteFile = false;
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VIDEO_CAPTURE) {
            if (resultCode == RESULT_OK) {

                MediaMetadataRetriever videoRetriever = new MediaMetadataRetriever();
                FileInputStream input = null;
                try {
                    input = new FileInputStream(fileUri.getPath());
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

                try {
                    videoRetriever.setDataSource(input.getFD());
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //Get duration of heart rate video file
                String timeString = videoRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                long time = Long.parseLong(timeString)/1000;

                //Prompt user to record again if duration less than 45s
                if(time<45) {

                    Toast.makeText(this,
                            "Please record for at least 45 seconds! ", Toast.LENGTH_SHORT).show();
                    deleteFile = true;
                } else {
                    Toast.makeText(this, "Video has been saved to:\n" +
                            data.getData(), Toast.LENGTH_SHORT).show();
                }

            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Video recording cancelled.",
                        Toast.LENGTH_SHORT).show();
                deleteFile = true;
            } else {
                Toast.makeText(this, "Failed to record video",
                        Toast.LENGTH_SHORT).show();
            }

            if(deleteFile) {
                File fdelete = new File(fileUri.getPath());

                if (fdelete.exists()) {
                    if (fdelete.delete()) {
                        System.out.println("Recording deleted");
                    }
                }
            }
            fileUri = null;
        }
    }
}
