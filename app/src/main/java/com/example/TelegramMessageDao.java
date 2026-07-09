package com.example;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TelegramMessageDao {

    @Query("SELECT * FROM telegram_messages ORDER BY timestamp ASC")
    List<TelegramMessage> getAllMessages();

    @Query("SELECT * FROM telegram_messages WHERE messageId = :messageId LIMIT 1")
    TelegramMessage getMessageByTelegramId(long messageId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TelegramMessage message);

    @Delete
    void delete(TelegramMessage message);

    @Query("DELETE FROM telegram_messages")
    void deleteAll();
}
