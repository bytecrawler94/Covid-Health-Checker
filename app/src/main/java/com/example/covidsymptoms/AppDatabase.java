package com.example.covidsymptoms;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {UserInfo.class}, version = 1)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {
    public abstract UserInfoDao userInfoDao();
    private static AppDatabase dbInstance;
    public static synchronized AppDatabase getInstance(Context context){
        //Create new database with last name for a name if none exist
        if(dbInstance == null){
            dbInstance = Room
                    .databaseBuilder(context.getApplicationContext(), AppDatabase.class, "gaur")
                    .build();
        }
        return dbInstance;
    }
}
