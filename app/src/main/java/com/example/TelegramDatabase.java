package com.example;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {TelegramMessage.class}, version = 1, exportSchema = false)
public abstract class TelegramDatabase extends RoomDatabase {

    private static volatile TelegramDatabase instance;

    public abstract TelegramMessageDao telegramMessageDao();

    public static synchronized TelegramDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            TelegramDatabase.class, "telegram_chat_db")
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
