package com.example.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.model.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnMessageActionListener {
        void onSpeakMessage(ChatMessage message);
        void onCopyMessage(ChatMessage message);
    }

    private final List<ChatMessage> messages = new ArrayList<>();
    private final OnMessageActionListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    public ChatAdapter(OnMessageActionListener listener) {
        this.listener = listener;
    }

    public void setMessages(List<ChatMessage> newMessages) {
        messages.clear();
        if (newMessages != null) {
            messages.addAll(newMessages);
        }
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public List<ChatMessage> getMessagesSnapshot() {
        return new ArrayList<>(messages);
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isUser() ? ChatMessage.TYPE_USER : ChatMessage.TYPE_ASSISTANT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == ChatMessage.TYPE_USER) {
            View view = inflater.inflate(R.layout.item_chat_user, parent, false);
            return new UserViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_chat_assistant, parent, false);
            return new AssistantViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        String formattedTime = timeFormat.format(new Date(message.getTimestamp()));
        if (holder instanceof UserViewHolder) {
            UserViewHolder userHolder = (UserViewHolder) holder;
            userHolder.messageText.setText(message.getText());
            userHolder.timeText.setText(formattedTime);
        } else if (holder instanceof AssistantViewHolder) {
            AssistantViewHolder aiHolder = (AssistantViewHolder) holder;
            aiHolder.messageText.setText(message.getText());
            aiHolder.timeText.setText(formattedTime);
            if (message.getModeTag() != null && !message.getModeTag().isEmpty()) {
                aiHolder.modeTagText.setVisibility(View.VISIBLE);
                aiHolder.modeTagText.setText(message.getModeTag());
            } else {
                aiHolder.modeTagText.setVisibility(View.GONE);
            }
            aiHolder.replayButton.setOnClickListener(v -> {
                if (listener != null) listener.onSpeakMessage(message);
            });
            aiHolder.copyButton.setOnClickListener(v -> {
                if (listener != null) listener.onCopyMessage(message);
            });
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        final TextView messageText;
        final TextView timeText;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.userMessageText);
            timeText = itemView.findViewById(R.id.userMessageTime);
        }
    }

    static class AssistantViewHolder extends RecyclerView.ViewHolder {
        final TextView messageText;
        final TextView timeText;
        final TextView modeTagText;
        final ImageButton replayButton;
        final ImageButton copyButton;

        AssistantViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.assistantMessageText);
            timeText = itemView.findViewById(R.id.assistantMessageTime);
            modeTagText = itemView.findViewById(R.id.assistantModeTag);
            replayButton = itemView.findViewById(R.id.replaySpeechButton);
            copyButton = itemView.findViewById(R.id.copyMessageButton);
        }
    }
}
