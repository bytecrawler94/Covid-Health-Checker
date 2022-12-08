package com.example.covidsymptoms;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.lang.Math.abs;

public class MainPage extends AppCompatActivity {
    private static String TAG = "MainPage: ";

    private static final int VIDEO_CAPTURE = 101;
    private Uri fileUri;
    private int windows = 9;
    long startExecTime;
    private TextView heartRateTextView;
    private TextView breathingRateTextView;
    public float breathingRateFinal = 0;
    public float heartRateFinal = 0;
    private boolean uploadSignsClicked = false;
    private boolean ongoingHeartRateProcess = false;
    private boolean ongoingBreathingRateProcess = false;

    DBHelper DB = new DBHelper(this);

    private String rootPath = Environment.getExternalStorageDirectory().getPath();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_signs_screen);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        final TextView heartRateTextView = (TextView) findViewById(R.id.tv_heart_rate);
        final TextView breathingRateTextView = (TextView) findViewById(R.id.tv_breathing_rate);
        Button recordButton = (Button) findViewById(R.id.btn_record_video);
        Button measureHeartRateButton = (Button) findViewById(R.id.btn_measure_heart_rate);
        Button measureBreathingButton = (Button) findViewById(R.id.btn_measure_breathing);
        Button uploadSymptomsButton = (Button) findViewById(R.id.btn_upload_symptoms_button);
        Button uploadSignsButton = (Button) findViewById(R.id.btn_upload_signs);

        handlePermissions(MainPage.this);

        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(ongoingHeartRateProcess == true) {
                    Toast.makeText(MainPage.this, "Please wait for process to complete before recording a new video!",
                            Toast.LENGTH_SHORT).show();
                } else {
                    startRecording();
                }
            }
        });

        measureHeartRateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                File videoFile = new File(rootPath + "/finger_tip.mp4");
                fileUri = Uri.fromFile(videoFile);

                if(ongoingHeartRateProcess == true) {
                    Toast.makeText(MainPage.this, "Please wait for process to complete before starting a new one!",
                            Toast.LENGTH_SHORT).show();
                } else if (videoFile.exists()) {
                    ongoingHeartRateProcess = true;
                    heartRateTextView.setText("Please Wait...");

                    startExecTime = System.currentTimeMillis();
                    System.gc();
                    Intent heartIntent = new Intent(MainPage.this, HeartRateService.class);
                    startService(heartIntent);

                } else {
                    Toast.makeText(MainPage.this, "Please record a video first!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        measureBreathingButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if(ongoingBreathingRateProcess == true) {
                    Toast.makeText(MainPage.this, "In Progress...",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainPage.this, "Place the phone on your abdomen \nfor 45s", Toast.LENGTH_LONG).show();
                    ongoingBreathingRateProcess = true;
                    breathingRateTextView.setText("In Progress...");
                    Intent accelIntent = new Intent(MainPage.this, AccelerometerService.class);
                    startService(accelIntent);
                }
            }
        });

        uploadSymptomsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                uploadSignsClicked = true;
                Intent intent = new Intent(MainPage.this, SymptomsScreen.class);
                intent.putExtra("uploadSignsClicked", uploadSignsClicked);
                startActivity(intent);
            }
        });

        uploadSignsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(ongoingBreathingRateProcess ==  true || ongoingHeartRateProcess == true) {
                    Toast.makeText(MainPage.this, "Please wait...", Toast.LENGTH_SHORT).show();
                } else {
                    heartRateFinal = Float.parseFloat(heartRateTextView.getText().toString());
                    breathingRateFinal = Float.parseFloat(breathingRateTextView.getText().toString());

                    SharedPreferences sp = getSharedPreferences("MySharedPref", Context.MODE_PRIVATE);

                    SharedPreferences.Editor edit = sp.edit();

                    edit.putFloat("HR", heartRateFinal);
                    edit.putFloat("RR", breathingRateFinal);
                    edit.commit();

                    Intent intent = new Intent("broadcastingHeartData");
                    Bundle b = new Bundle();
                    b.putString("calcHeartRate", String.valueOf(Float.parseFloat(heartRateTextView.getText().toString())));
                    b.putString("calcRespiratoryRate", String.valueOf(Float.parseFloat(breathingRateTextView.getText().toString())));
                    intent.putExtras(b);
                    sendBroadcast(intent);

                    Map<String, Float> data = new HashMap<String, Float>();

                    data.put("HeartRate", heartRateFinal);
                    data.put("RespiratoryRate", breathingRateFinal);
                    Boolean checkInsertOperation = DB.insertUserData(data);
                }

            }
        });

        LocalBroadcastManager.getInstance(MainPage.this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Boolean checkInsertOperation = null;

                Bundle b = intent.getExtras();
                BreathingRateDetector runnable = new BreathingRateDetector(b.getIntegerArrayList("accelValuesX"));

                Thread thread = new Thread(runnable);
                thread.start();

                try {
                    thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                breathingRateFinal = runnable.breathingRate;
                breathingRateTextView.setText(runnable.breathingRate + "");

                Map<String, Float> data = new HashMap<String, Float>();

                if(breathingRateFinal != 0.0) {
                    data.put("RespiratoryRate", breathingRateFinal);
                    Toast.makeText(MainPage.this, "Respiratory rate calculated!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainPage.this, "Please measure respiratory rate", Toast.LENGTH_SHORT).show();
                }

                ongoingBreathingRateProcess = false;
                b.clear();
                System.gc();

            }
        }, new IntentFilter("broadcastingAccelData"));


        LocalBroadcastManager.getInstance(MainPage.this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                Bundle b = intent.getExtras();
                float heartRate = 0;
                int fail = 0;

                for (int i = 0; i < windows; i++) {

                    ArrayList<Integer> heartData = null;
                    heartData = b.getIntegerArrayList("heartData"+i);

                    float zeroCrossings = peakFinding(heartData);
                    heartRate += zeroCrossings/2;

                    String csvFilePath = rootPath + "/finger_tip" + i + ".csv";
                    saveToCSV(heartData, csvFilePath);
                }

                heartRate = (heartRate*12)/ windows;
//                heartRateFinal = heartRate;
                heartRateTextView.setText(heartRate + "");
                ongoingHeartRateProcess = false;
                Toast.makeText(MainPage.this, "Heart rate calculated!", Toast.LENGTH_SHORT).show();
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

    public int peakFinding(ArrayList<Integer> data) {

        int diff, prev, slope = 0, zeroCrossings = 0;
        int j = 0;
        prev = data.get(0);

        while(slope == 0 && j + 1 < data.size()) {
            diff = data.get(j + 1) - data.get(j);
            if(diff != 0) {
                slope = diff/abs(diff);
            }
            j++;
        }

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

    public void startRecording() {

        File mediaFile = new File( rootPath + "/finger_tip.mp4");
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT,45);

//        fileUri = FileProvider.getUriForFile(getApplicationContext(), getApplicationContext().getApplicationContext().getPackageName() + ".provider", mediaFile);
        fileUri = Uri.fromFile(mediaFile);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
        startActivityForResult(intent, VIDEO_CAPTURE);
    }

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

                String timeString = videoRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                long time = Long.parseLong(timeString)/1000;

                if(time<45) {

                    Toast.makeText(this,
                            "Please record for at least 45 seconds! ", Toast.LENGTH_SHORT).show();
                    deleteFile = true;
                } else{
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