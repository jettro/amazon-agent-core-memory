package com.example.bedrock;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

public interface ChatPort {
    String complete(String system, String user, List<Message> messages);
}
