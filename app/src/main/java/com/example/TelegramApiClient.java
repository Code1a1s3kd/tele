package com.example;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TelegramApiClient {
    private static final String TAG = "TelegramApiClient";
    private static final OkHttpClient client = new OkHttpClient();
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    public static void fetchUpdates(Context context, String token, long targetUserId, long offset, ApiCallback<List<TelegramMessage>> callback) {
        executor.execute(() -> {
            try {
                String url = "https://api.telegram.org/bot" + token + "/getUpdates?timeout=10";
                if (offset > 0) {
                    url += "&offset=" + offset;
                }

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new Exception("HTTP Error " + response.code());
                    }

                    String jsonStr = response.body().string();
                    JSONObject root = new JSONObject(jsonStr);
                    if (!root.getBoolean("ok")) {
                        throw new Exception("Telegram API error: " + root.optString("description"));
                    }

                    JSONArray updates = root.getJSONArray("result");
                    List<TelegramMessage> parsedMessages = new ArrayList<>();

                    for (int i = 0; i < updates.length(); i++) {
                        JSONObject update = updates.getJSONObject(i);
                        long updateId = update.getLong("update_id");
                        
                        // We store the highest update ID to know the next offset
                        if (updateId >= offset) {
                            // save new offset
                            context.getSharedPreferences("telegram_prefs", Context.MODE_PRIVATE)
                                    .edit()
                                    .putLong("last_update_id", updateId)
                                    .apply();
                        }

                        if (!update.has("message")) {
                            continue;
                        }

                        JSONObject messageJson = update.getJSONObject("message");
                        long messageId = messageJson.getLong("message_id");
                        long date = messageJson.getLong("date");

                        JSONObject from = messageJson.getJSONObject("from");
                        long senderId = from.getLong("id");

                        // FILTER by user ID if it is specified (e.g. non-zero)
                        if (targetUserId > 0 && senderId != targetUserId) {
                            continue; // Skip messages not from our specific user
                        }

                        String firstName = from.optString("first_name", "");
                        String lastName = from.optString("last_name", "");
                        String senderName = (firstName + " " + lastName).trim();
                        if (senderName.isEmpty()) {
                            senderName = from.optString("username", "User " + senderId);
                        }

                        JSONObject chat = messageJson.getJSONObject("chat");
                        long chatId = chat.getLong("id");

                        String text = messageJson.optString("text", null);
                        String imageUrl = null;

                        if (messageJson.has("photo")) {
                            JSONArray photoArray = messageJson.getJSONArray("photo");
                            if (photoArray.length() > 0) {
                                // Get the largest photo size (last element)
                                JSONObject largestPhoto = photoArray.getJSONObject(photoArray.length() - 1);
                                String fileId = largestPhoto.getString("file_id");
                                
                                // Fetch file path synchronously within this background thread
                                String filePath = fetchFilePathSync(token, fileId);
                                if (filePath != null) {
                                    imageUrl = "https://api.telegram.org/file/bot" + token + "/" + filePath;
                                }
                            }
                        }

                        // If it has a caption, use that as the text if text is null
                        if (text == null && messageJson.has("caption")) {
                            text = messageJson.getString("caption");
                        }

                        TelegramMessage msg = new TelegramMessage(
                                messageId,
                                chatId,
                                senderId,
                                senderName,
                                text,
                                imageUrl,
                                false,
                                date
                        );
                        parsedMessages.add(msg);
                    }

                    callback.onSuccess(parsedMessages);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching updates", e);
                callback.onError(e);
            }
        });
    }

    private static String fetchFilePathSync(String token, String fileId) {
        try {
            String url = "https://api.telegram.org/bot" + token + "/getFile?file_id=" + fileId;
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    JSONObject root = new JSONObject(response.body().string());
                    if (root.getBoolean("ok")) {
                        return root.getJSONObject("result").getString("file_path");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching file path", e);
        }
        return null;
    }

    public static void sendMessage(String token, long chatId, String text, ApiCallback<TelegramMessage> callback) {
        executor.execute(() -> {
            try {
                String url = "https://api.telegram.org/bot" + token + "/sendMessage";
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("chat_id", chatId);
                jsonBody.put("text", text);

                RequestBody body = RequestBody.create(
                        jsonBody.toString(),
                        MediaType.parse("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new Exception("HTTP Error " + response.code());
                    }

                    JSONObject root = new JSONObject(response.body().string());
                    if (!root.getBoolean("ok")) {
                        throw new Exception("Telegram API error: " + root.optString("description"));
                    }

                    JSONObject result = root.getJSONObject("result");
                    long messageId = result.getLong("message_id");
                    long date = result.getLong("date");

                    TelegramMessage sentMsg = new TelegramMessage(
                            messageId,
                            chatId,
                            0, // 0 or bot's own ID
                            "Bot",
                            text,
                            null,
                            true, // Outbound
                            date
                    );

                    callback.onSuccess(sentMsg);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending message", e);
                callback.onError(e);
            }
        });
    }

    public static void saveImageToDevice(Context context, String imageUrl, ApiCallback<String> callback) {
        executor.execute(() -> {
            try {
                Request request = new Request.Builder().url(imageUrl).get().build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new Exception("Failed to download image: HTTP " + response.code());
                    }

                    String fileName = "Telegram_" + System.currentTimeMillis() + ".jpg";
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TelegramBot");
                        values.put(MediaStore.Images.Media.IS_PENDING, 1);
                    }

                    Uri imageUri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (imageUri == null) {
                        throw new Exception("Failed to create MediaStore entry.");
                    }

                    try (InputStream in = response.body().byteStream();
                         OutputStream out = context.getContentResolver().openOutputStream(imageUri)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear();
                        values.put(MediaStore.Images.Media.IS_PENDING, 0);
                        context.getContentResolver().update(imageUri, values, null, null);
                    }

                    callback.onSuccess(fileName);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving image", e);
                callback.onError(e);
            }
        });
    }
}
