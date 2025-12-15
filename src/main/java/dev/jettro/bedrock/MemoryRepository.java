package dev.jettro.bedrock;


import org.springframework.ai.chat.messages.Message;

import java.time.Instant;
import java.util.List;

/**
 * Port that encapsulates all Bedrock Agent Core memory interactions.
 */
public interface MemoryRepository {
    List<Message> listEvents(String actorId, String sessionId);

    void createUserEvent(String actorId, String sessionId, String content, Instant timestamp);

    void createAssistantEvent(String actorId, String sessionId, String content, Instant timestamp);

    List<String> searchMemories(String query, String actorId, int maxResults);
}
