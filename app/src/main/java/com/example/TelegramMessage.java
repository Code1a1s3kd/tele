package com.example;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "telegram_messages")
public class TelegramMessage {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private long messageId;
    private long chatId;
    private long senderId;
    private String senderName;
    private String text;
    private String imageUrl; // Local file path or remote file path
    private boolean isOutbound; // true if sent from app, false if received
    private long timestamp; // Unix timestamp in seconds

    public TelegramMessage() {
    }

    public TelegramMessage(long messageId, long chatId, long senderId, String senderName, String text, String imageUrl, boolean isOutbound, long timestamp) {
        this.messageId = messageId;
        this.chatId = chatId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.text = text;
        this.imageUrl = imageUrl;
        this.isOutbound = isOutbound;
        this.timestamp = timestamp;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getMessageId() {
        return messageId;
    }

    public void setMessageId(long messageId) {
        this.messageId = messageId;
    }

    public long getChatId() {
        return chatId;
    }

    public void setChatId(long chatId) {
        this.chatId = chatId;
    }

    public long getSenderId() {
        return senderId;
    }

    public void setSenderId(long senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isOutbound() {
        return isOutbound;
    }

    public void setOutbound(boolean outbound) {
        isOutbound = outbound;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
