package com.example.covidsymptoms;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

public class SymptomsScreen extends AppCompatActivity {

    private Spinner spinner;
    RatingBar symptomRatingBar;
    float[] tempRatings = new float[10];
    float heartRateFinal = 0.0F;
    float respiratoryRateFinal = 0.0F;
    static final String TAG = "SymptomsScreen: ";

    DBHelper DB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_symptoms_screen);

        DB = new DBHelper(this );

        symptomRatingBar = (RatingBar) findViewById(R.id.ratingBar);
        Button updateButton = (Button) findViewById(R.id.button2);

        spinner = (Spinner) findViewById(R.id.symptoms_spinner);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.symptoms_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        symptomRatingBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(RatingBar ratingBar, float v, boolean b) {
                int i = spinner.getSelectedItemPosition();
                tempRatings[i] = v;
            }
        });

        updateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                boolean uploadSignsClicked = getIntent().getExtras().getBoolean("uploadSignsClicked");
                if(uploadSignsClicked) {
                    System.out.println(TAG + "Symptoms Screen : " + uploadSignsClicked);
                }

                LocalBroadcastManager.getInstance(SymptomsScreen.this).registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        Bundle b = intent.getExtras();
                        heartRateFinal = Float.parseFloat(b.getString("calcHeartRate"));
                        respiratoryRateFinal = Float.parseFloat(b.getString("calcRespiratoryRate"));
                    }
                }, new IntentFilter("broadcastingHeartData"));

                Map<String, Float> data = new HashMap<String, Float>();
                MainPage mainPage = new MainPage();

                System.out.println(TAG + heartRateFinal);
                System.out.println(TAG + respiratoryRateFinal);

                SharedPreferences prefs = getSharedPreferences("MySharedPref", MODE_PRIVATE);
                heartRateFinal = prefs.getFloat("HR", mainPage.heartRateFinal);
                respiratoryRateFinal = prefs.getFloat("HR", mainPage.breathingRateFinal);

                data.put("HeartRate", heartRateFinal);
                data.put("RespiratoryRate", respiratoryRateFinal);
                data.put("Fever", tempRatings[0]);
                data.put("Cough", tempRatings[1]);
                data.put("Tiredness", tempRatings[2]);
                data.put("Breathlessness", tempRatings[3]);
                data.put("MuscleAches", tempRatings[4]);
                data.put("Chills", tempRatings[5]);
                data.put("SoreThroat", tempRatings[6]);
                data.put("RunnyNose", tempRatings[7]);
                data.put("HeadAche", tempRatings[8]);
                data.put("ChestPain", tempRatings[9]);

                Boolean checkInsertOperation = DB.insertUserData(data);
                if(checkInsertOperation == true) {
                    Toast.makeText(SymptomsScreen.this, "DB insertion successful", Toast.LENGTH_SHORT).show();
                } else
                    Toast.makeText(SymptomsScreen.this, "DB insertion failed", Toast.LENGTH_LONG).show();

            }

        });


        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                symptomRatingBar.setRating(tempRatings[i]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }

}