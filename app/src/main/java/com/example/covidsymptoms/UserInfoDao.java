package com.example.covidsymptoms;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

@Dao
public interface UserInfoDao {
    @Query("SELECT COUNT(*) FROM UserInfo")
    public int count();

    //Get latest data row
    @Query("SELECT * FROM UserInfo where timestamp=(SELECT MAX(timestamp) FROM UserInfo)")
    public UserInfo getLatestData();

    @Insert
    public long insert(UserInfo userInfo);

    @Update
    public int update(UserInfo userInfo);
}
