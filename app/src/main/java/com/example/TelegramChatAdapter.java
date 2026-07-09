package com.example;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TelegramChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_INBOUND = 1;
    private static final int VIEW_TYPE_OUTBOUND = 2;

    private final List<TelegramMessage> messages = new ArrayList<>();
    private final OnMessageInteractionListener listener;

    public interface OnMessageInteractionListener {
        void onImageTapped(TelegramMessage message);
        void onTextTapped(TelegramMessage message);
        void onMessageLongPressed(TelegramMessage message);
    }

    public TelegramChatAdapter(OnMessageInteractionListener listener) {
        this.listener = listener;
    }

    public void setMessages(List<TelegramMessage> newMessages) {
        this.messages.clear();
        this.messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        TelegramMessage message = messages.get(position);
        return message.isOutbound() ? VIEW_TYPE_OUTBOUND : VIEW_TYPE_INBOUND;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_OUTBOUND) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_outbound, parent, false);
            return new OutboundViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_inbound, parent, false);
            return new InboundViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        TelegramMessage message = messages.get(position);
        boolean hasImage = message.getImageUrl() != null && !message.getImageUrl().isEmpty();

        // Formatted Time
        String timeStr = formatTime(message.getTimestamp());

        if (holder instanceof InboundViewHolder) {
            InboundViewHolder inboundHolder = (InboundViewHolder) holder;
            inboundHolder.senderName.setText(message.getSenderName());
            inboundHolder.messageTime.setText(timeStr);

            if (hasImage) {
                inboundHolder.messageImage.setVisibility(View.VISIBLE);
                Picasso.get()
                        .load(message.getImageUrl())
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                        .into(inboundHolder.messageImage);

                // Tap image to save
                inboundHolder.messageImage.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onImageTapped(message);
                    }
                });
            } else {
                inboundHolder.messageImage.setVisibility(View.GONE);
            }

            if (message.getText() != null && !message.getText().isEmpty()) {
                inboundHolder.messageText.setVisibility(View.VISIBLE);
                inboundHolder.messageText.setText(message.getText());
            } else {
                inboundHolder.messageText.setVisibility(View.GONE);
            }

            // Tap text to copy
            inboundHolder.bubbleCard.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTextTapped(message);
                }
            });

            // Long press to delete
            inboundHolder.bubbleCard.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onMessageLongPressed(message);
                }
                return true;
            });

            inboundHolder.messageImage.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onMessageLongPressed(message);
                }
                return true;
            });

        } else if (holder instanceof OutboundViewHolder) {
            OutboundViewHolder outboundHolder = (OutboundViewHolder) holder;
            outboundHolder.messageTime.setText(timeStr);

            if (hasImage) {
                outboundHolder.messageImage.setVisibility(View.VISIBLE);
                Picasso.get()
                        .load(message.getImageUrl())
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                        .into(outboundHolder.messageImage);

                outboundHolder.messageImage.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onImageTapped(message);
                    }
                });
            } else {
                outboundHolder.messageImage.setVisibility(View.GONE);
            }

            if (message.getText() != null && !message.getText().isEmpty()) {
                outboundHolder.messageText.setVisibility(View.VISIBLE);
                outboundHolder.messageText.setText(message.getText());
            } else {
                outboundHolder.messageText.setVisibility(View.GONE);
            }

            // Tap text to copy
            outboundHolder.bubbleCard.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTextTapped(message);
                }
            });

            // Long press to delete
            outboundHolder.bubbleCard.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onMessageLongPressed(message);
                }
                return true;
            });

            outboundHolder.messageImage.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onMessageLongPressed(message);
                }
                return true;
            });
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    private String formatTime(long timestampInSeconds) {
        Calendar cal = Calendar.getInstance(Locale.ENGLISH);
        cal.setTimeInMillis(timestampInSeconds * 1000L);
        return DateFormat.format("hh:mm a", cal).toString();
    }

    static class InboundViewHolder extends RecyclerView.ViewHolder {
        TextView senderName;
        TextView messageText;
        TextView messageTime;
        ImageView messageImage;
        View bubbleCard;

        public InboundViewHolder(@NonNull View itemView) {
            super(itemView);
            senderName = itemView.findViewById(R.id.sender_name);
            messageText = itemView.findViewById(R.id.message_text);
            messageTime = itemView.findViewById(R.id.message_time);
            messageImage = itemView.findViewById(R.id.message_image);
            bubbleCard = itemView.findViewById(R.id.bubble_card);
        }
    }

    static class OutboundViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView messageTime;
        ImageView messageImage;
        View bubbleCard;

        public OutboundViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.message_text);
            messageTime = itemView.findViewById(R.id.message_time);
            messageImage = itemView.findViewById(R.id.message_image);
            bubbleCard = itemView.findViewById(R.id.bubble_card);
        }
    }
}
