package com.example.covidsymptoms;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.sql.SQLOutput;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class DBHelper extends SQLiteOpenHelper {

    String heartRate = "0.0";
    String respiratoryRate = "0.0";
    private static String TAG = "DBHelper: ";

    public DBHelper(Context context) {
        super(context, "Bawane.db", null  , 1);
    }

    @Override
    public void onCreate(SQLiteDatabase DB) {
        DB.execSQL("create Table Bawane(HeartRate TEXT, RespiratoryRate TEXT, Fever TEXT, Cough TEXT, Tiredness TEXT, Breathlessness TEXT, " +
                "MuscleAches TEXT, Chills TEXT,SoreThroat TEXT, RunnyNose TEXT,HeadAche TEXT, ChestPain TEXT, TimeStamp TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase DB, int i, int i1) {
        DB.execSQL("drop table if exists userdetails");
    }

    public boolean insertUserData(Map<String, Float> data) {

        SQLiteDatabase dbWriter = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        String selectQuery = "SELECT * FROM " + "Bawane";

        System.out.println(TAG + "Final user structure before DB insertion");
        for (Map.Entry<String, Float> x : data.entrySet()) {
            System.out.println(TAG + "key:value => " + x.getKey() + " " + x.getValue());
            contentValues.put(x.getKey(), x.getValue());
        }

        contentValues.put("TimeStamp", new Date(System.currentTimeMillis()).toString());

        long result = dbWriter.insert("Bawane", null, contentValues);

        if (result == -1)
            return false;
        return true;
    }

    public boolean updateUserData(Map<String, Float> data) {

        SQLiteDatabase dbWriter = this.getWritableDatabase();
        SQLiteDatabase dbReader = this.getReadableDatabase();
        ContentValues contentValues = new ContentValues();

        for (Map.Entry<String, Float> x : data.entrySet()) {
            contentValues.put(x.getKey(), x.getValue());
        }

        String selectQuery = "SELECT * FROM " + "Bawane";

        Cursor readCursor = dbReader.rawQuery(selectQuery, null);

        heartRate = readCursor.getString(0);
        respiratoryRate =  readCursor.getString(1);

        System.out.println(TAG + "Printing initial two values : " + heartRate + " " + respiratoryRate);

        if (readCursor.getCount() > 0) {
            System.out.println(TAG + "cursor get count" + readCursor.getCount());

            readCursor.close();
            return true;
        } else
            return false;
    }

    public Cursor getdata() {
        SQLiteDatabase DB = this.getWritableDatabase();
        Cursor cursor = DB.rawQuery("Select * from Bawane ", null);

        return cursor;
    }
}
