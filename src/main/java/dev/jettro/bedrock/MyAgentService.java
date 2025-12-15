package dev.jettro.bedrock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agentcore.annotation.AgentCoreInvocation;
import org.springaicommunity.agentcore.context.AgentCoreContext;
import org.springaicommunity.agentcore.context.AgentCoreHeaders;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Service
public class MyAgentService {
    private static final Logger logger = LoggerFactory.getLogger(MyAgentService.class);

    private final ChatPort chat;
    private final MemoryRepository memoryRepository;
    private final Clock clock;
    private final MemoryPropertiesConfig memoryProps;
    private final ChatProperties chatProps;

    public MyAgentService(ChatPort chat,
                          MemoryRepository memoryRepository,
                          Clock clock,
                          MemoryPropertiesConfig memoryProps,
                          ChatProperties chatProps) {
        this.chat = chat;
        this.memoryRepository = memoryRepository;
        this.clock = clock;
        this.memoryProps = memoryProps;
        this.chatProps = chatProps;
    }

    @AgentCoreInvocation
    public MyResponse handleUserPrompt(MyRequest request, AgentCoreContext context) {
        var sessionId = context.getHeader(AgentCoreHeaders.SESSION_ID);
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "session-" + request.sessionId() + "-" + request.actorId();
        }

        List<Message> events = memoryRepository.listEvents(request.actorId(), sessionId);

        memoryRepository.createUserEvent(request.actorId(), sessionId, request.prompt(), clock.instant());

        int maxResults = memoryProps.searchMaxResults() != null ? memoryProps.searchMaxResults() : 4;
        List<String> memories = memoryRepository.searchMemories(request.prompt(), request.actorId(), maxResults);
        logger.info("Retrieved {} relevant memory records from memory.", memories.size());

        String agentResponse = callAgent(request, events, memories);

        memoryRepository.createAssistantEvent(request.actorId(), sessionId, agentResponse, clock.instant());

        return new MyResponse(agentResponse);

    }

    private String callAgent(MyRequest request, List<Message> messages, List<String> memories) {
        int historyWindow = chatProps.historyWindow() != null ? chatProps.historyWindow() : 10;
        List<Message> trimmedMessages = trimToLastN(messages, historyWindow);

        String systemPromptBase = chatProps.systemPromptBase();

        String systemPrompt = buildSystemPrompt(memories, systemPromptBase);

        String content = chat.complete(systemPrompt, request.prompt(), trimmedMessages.reversed());
        logger.info("Agent response: {}", content);
        return content;
    }


    /**
     * Pure helper: trims the list to the last N elements without mutating the input list
     *
     * @param messages the list to trim
     * @param n the number of elements to keep
     * @return the trimmed list
     */
    static List<Message> trimToLastN(List<Message> messages, int n) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        if (n <= 0) {
            return List.of();
        }
        int size = messages.size();
        if (size <= n) {
            return messages;
        }
        return messages.subList(size - n, size);
    }

    /**
     * Pure helper: builds the system prompt string, optionally appending formatted memories
     * @param memories the list of memories to format
     * @param base the base prompt string
     * @return the formatted system prompt string
     */
    static String buildSystemPrompt(List<String> memories, String base) {
        if (memories == null || memories.isEmpty()) {
            return base;
        }
        StringBuilder memoryContext = new StringBuilder("Relevant memories:\n");
        for (String memory : memories) {
            memoryContext.append("- ").append(memory).append("\n");
        }
        return base + "\n" + memoryContext;
    }

}
