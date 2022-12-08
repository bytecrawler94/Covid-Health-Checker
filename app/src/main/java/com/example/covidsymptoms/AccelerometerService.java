package com.example.covidsymptoms;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;

public class AccelerometerService extends Service implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor senseAccel;
    private ArrayList<Integer> accelValuesX = new ArrayList<>();

    @Override
    public void onCreate(){
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        senseAccel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, senseAccel, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        accelValuesX.clear();
        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        Sensor genericSensor = sensorEvent.sensor;
        if (genericSensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            accelValuesX.add((int)(sensorEvent.values[0] * 100));

            if(accelValuesX.size() >= 300){
                stopSelf();
            }
        }
    }

    @Override
    public void onDestroy(){

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {

                sensorManager.unregisterListener(AccelerometerService.this);

                Intent intent = new Intent("broadcastingAccelData");
                Bundle b = new Bundle();
                b.putIntegerArrayList("accelValuesX", accelValuesX);
                intent.putExtras(b);
                LocalBroadcastManager.getInstance(AccelerometerService.this).sendBroadcast(intent);
            }
        });
        thread.start();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
