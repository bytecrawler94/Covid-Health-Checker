package com.example.covidsymptoms;

import androidx.room.Database;
import androidx.room.TypeConverter;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class Converters {

    static DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    static {
        df.setTimeZone(TimeZone.getTimeZone("Asia/Calcutta"));
    }

    @TypeConverter
    public static String fromTimestamp(Date value) {
        if(value != null){
            return df.format(value);
        } else {
        return null;
        }
    }

    @TypeConverter
    public static Date dateToTimestamp(String value) {
        if(value != null){
            try {
                return df.parse(value);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            return null;
        } else {
            return null;
        }
    }
}
