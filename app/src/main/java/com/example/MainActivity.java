package com.example;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements TelegramChatAdapter.OnMessageInteractionListener {

    private TelegramDatabase database;
    private TelegramChatAdapter adapter;
    private ExecutorService dbExecutor;
    private Handler pollHandler;
    private Runnable pollRunnable;

    private View settingsCard;
    private EditText tokenInput;
    private EditText userIdInput;
    private View statusIndicator;
    private TextView statusText;
    private RecyclerView recyclerView;
    private EditText replyInput;
    private View sendButton;

    private boolean isPollingActive = false;
    private String botToken = "";
    private long targetUserId = 0;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Executors and Database
        dbExecutor = Executors.newSingleThreadExecutor();
        database = TelegramDatabase.getInstance(this);
        pollHandler = new Handler(Looper.getMainLooper());

        // Bind Views
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        settingsCard = findViewById(R.id.settings_card);
        tokenInput = findViewById(R.id.token_input);
        userIdInput = findViewById(R.id.user_id_input);
        statusIndicator = findViewById(R.id.status_indicator);
        statusText = findViewById(R.id.status_text);
        recyclerView = findViewById(R.id.chat_recycler);
        replyInput = findViewById(R.id.reply_input);
        sendButton = findViewById(R.id.send_button);

        // Setup Toolbar Menu/Click to toggle settings card visibility
        toolbar.setNavigationIcon(R.drawable.ic_settings);
        toolbar.setNavigationOnClickListener(v -> {
            if (settingsCard.getVisibility() == View.VISIBLE) {
                settingsCard.setVisibility(View.GONE);
            } else {
                settingsCard.setVisibility(View.VISIBLE);
            }
        });

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TelegramChatAdapter(this);
        recyclerView.setAdapter(adapter);

        // Load Credentials from SharedPreferences (with fallback to BuildConfig if set)
        prefs = getSharedPreferences("telegram_prefs", MODE_PRIVATE);
        
        // Try getting token from BuildConfig (exposed from secrets/.env if available)
        String defaultToken = "";
        try {
            defaultToken = BuildConfig.TELEGRAM_BOT_TOKEN;
        } catch (Exception ignored) {}
        if (defaultToken == null || defaultToken.isEmpty() || defaultToken.contains("YOUR_TELEGRAM_BOT_TOKEN")) {
            defaultToken = "";
        }

        String defaultUserIdStr = "";
        try {
            defaultUserIdStr = BuildConfig.TELEGRAM_USER_ID;
        } catch (Exception ignored) {}
        if (defaultUserIdStr == null || defaultUserIdStr.isEmpty() || defaultUserIdStr.contains("YOUR_TELEGRAM_USER_ID")) {
            defaultUserIdStr = "";
        }

        botToken = prefs.getString("bot_token", defaultToken);
        targetUserId = prefs.getLong("target_user_id", 0);
        if (targetUserId == 0 && !defaultUserIdStr.isEmpty()) {
            try {
                targetUserId = Long.parseLong(defaultUserIdStr);
            } catch (NumberFormatException ignored) {}
        }

        // Pre-populate input fields
        tokenInput.setText(botToken);
        if (targetUserId > 0) {
            userIdInput.setText(String.valueOf(targetUserId));
        }

        // Setup save/connect button click listener
        findViewById(R.id.save_connect_button).setOnClickListener(v -> saveAndConnect());

        // Setup send button click listener
        sendButton.setOnClickListener(v -> sendReply());

        // Load offline messages from database immediately
        loadMessagesFromDb();

        // Start active polling if credentials are set
        if (!botToken.isEmpty() && targetUserId > 0) {
            startPolling();
        } else {
            updateStatusUI(false, "Requires Setup");
        }
    }

    private void saveAndConnect() {
        String token = tokenInput.getText().toString().trim();
        String userIdStr = userIdInput.getText().toString().trim();

        if (token.isEmpty()) {
            Toast.makeText(this, "Please enter a valid Bot Token", Toast.LENGTH_SHORT).show();
            return;
        }

        long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter a valid Telegram User ID", Toast.LENGTH_SHORT).show();
            return;
        }

        // If token has changed, clear the last update ID offset to fetch afresh from the new bot
        if (!token.equals(botToken)) {
            prefs.edit().putLong("last_update_id", 0).apply();
        }

        botToken = token;
        targetUserId = userId;

        prefs.edit()
                .putString("bot_token", botToken)
                .putLong("target_user_id", targetUserId)
                .apply();

        Toast.makeText(this, "Credentials saved!", Toast.LENGTH_SHORT).show();
        settingsCard.setVisibility(View.GONE);

        startPolling();
    }

    private void startPolling() {
        isPollingActive = true;
        updateStatusUI(true, "Connecting...");

        if (pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }

        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPollingActive) {
                    pollUpdates();
                }
                pollHandler.postDelayed(this, 4000); // Poll every 4 seconds
            }
        };

        pollHandler.post(pollRunnable);
    }

    private void stopPolling() {
        isPollingActive = false;
        if (pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
        updateStatusUI(false, "Disconnected");
    }

    private void pollUpdates() {
        long lastUpdateId = prefs.getLong("last_update_id", 0);
        long offset = lastUpdateId > 0 ? lastUpdateId + 1 : 0;

        TelegramApiClient.fetchUpdates(this, botToken, targetUserId, offset, new TelegramApiClient.ApiCallback<List<TelegramMessage>>() {
            @Override
            public void onSuccess(List<TelegramMessage> newMessages) {
                runOnUiThread(() -> updateStatusUI(true, "Connected & Synced"));
                
                if (newMessages != null && !newMessages.isEmpty()) {
                    dbExecutor.execute(() -> {
                        boolean anyNew = false;
                        for (TelegramMessage msg : newMessages) {
                            TelegramMessage existing = database.telegramMessageDao().getMessageByTelegramId(msg.getMessageId());
                            if (existing == null) {
                                database.telegramMessageDao().insert(msg);
                                anyNew = true;
                            }
                        }
                        if (anyNew) {
                            loadMessagesFromDb();
                        }
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> updateStatusUI(false, "Connection Error"));
            }
        });
    }

    private void sendReply() {
        if (botToken.isEmpty() || targetUserId == 0) {
            Toast.makeText(this, "Please configure connection settings first!", Toast.LENGTH_SHORT).show();
            settingsCard.setVisibility(View.VISIBLE);
            return;
        }

        String replyText = replyInput.getText().toString().trim();
        if (replyText.isEmpty()) {
            return;
        }

        // Disable send button temporarily
        sendButton.setEnabled(false);

        TelegramApiClient.sendMessage(botToken, targetUserId, replyText, new TelegramApiClient.ApiCallback<TelegramMessage>() {
            @Override
            public void onSuccess(TelegramMessage sentMsg) {
                runOnUiThread(() -> {
                    replyInput.setText("");
                    sendButton.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Reply sent!", Toast.LENGTH_SHORT).show();
                });

                // Save to DB and refresh list
                dbExecutor.execute(() -> {
                    database.telegramMessageDao().insert(sentMsg);
                    loadMessagesFromDb();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    sendButton.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Failed to send: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadMessagesFromDb() {
        dbExecutor.execute(() -> {
            List<TelegramMessage> msgs = database.telegramMessageDao().getAllMessages();
            runOnUiThread(() -> {
                adapter.setMessages(msgs);
                if (!msgs.isEmpty()) {
                    recyclerView.scrollToPosition(msgs.size() - 1);
                }
            });
        });
    }

    private void updateStatusUI(boolean connected, String text) {
        statusText.setText(text);
        if (connected) {
            statusIndicator.setBackground(ContextCompat.getDrawable(this, R.drawable.status_indicator_connected));
        } else {
            statusIndicator.setBackground(ContextCompat.getDrawable(this, R.drawable.status_indicator_disconnected));
        }
    }

    // Message Interaction Overrides
    @Override
    public void onImageTapped(TelegramMessage message) {
        if (message.getImageUrl() == null || message.getImageUrl().isEmpty()) {
            return;
        }

        Toast.makeText(this, "Saving image to device...", android.widget.Toast.LENGTH_SHORT).show();
        
        TelegramApiClient.saveImageToDevice(this, message.getImageUrl(), new TelegramApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String fileName) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Image saved successfully: " + fileName, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Failed to save image: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    public void onTextTapped(TelegramMessage message) {
        if (message.getText() == null || message.getText().isEmpty()) {
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Telegram Message", message.getText());
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Message text copied to clipboard!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMessageLongPressed(TelegramMessage message) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Message")
                .setMessage("Are you sure you want to delete this message from the app?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    dbExecutor.execute(() -> {
                        database.telegramMessageDao().delete(message);
                        loadMessagesFromDb();
                    });
                    Toast.makeText(MainActivity.this, "Message deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
        if (dbExecutor != null) {
            dbExecutor.shutdown();
        }
    }
}
