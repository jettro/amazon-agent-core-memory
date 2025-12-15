package com.example.bedrock;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SpringAiChatAdapter implements ChatPort {

    private final ChatClient chatClient;

    public SpringAiChatAdapter(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String complete(String system, String user, List<Message> messages) {
        return chatClient
                .prompt()
                .system(system)
                .user(user)
                .messages(messages)
                .call()
                .content();
    }
}
