package com.example.bedrock;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agentcore.annotation.AgentCoreInvocation;
import org.springaicommunity.agentcore.context.AgentCoreContext;
import org.springaicommunity.agentcore.context.AgentCoreHeaders;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.*;
import software.amazon.awssdk.services.bedrockagentcore.model.Content;
import software.amazon.awssdk.services.bedrockagentcorecontrol.BedrockAgentCoreControlClient;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.*;

import java.time.Instant;
import java.util.List;

@Service
public class MyAgentService {
    private static final Logger logger = LoggerFactory.getLogger(MyAgentService.class);

    private final MemoryPropertiesConfig memoryPropertiesConfig;
    private Memory memory;
    private final ChatClient chatClient;

    public MyAgentService(MemoryPropertiesConfig memoryPropertiesConfig, ChatClient.Builder chatClient) {
        this.memoryPropertiesConfig = memoryPropertiesConfig;
        this.chatClient = chatClient.build();
    }

    @AgentCoreInvocation
    public MyResponse handleUserPrompt(MyRequest request, AgentCoreContext context) {
        var sessionId = context.getHeader(AgentCoreHeaders.SESSION_ID);
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "session-" + request.sessionId() + "-" + request.actorId();
        }

        try (BedrockAgentCoreClient build = BedrockAgentCoreClient.builder()
                .region(Region.EU_WEST_1)
                .build()) {

            List<Event> events = loadEventsFromMemory(request.actorId(), sessionId, build);

            storeUserEvent(request, sessionId, build);

            List<String> memories = retrieveEventsFromMemory(request.prompt(), request.actorId(),  build);
            logger.info("Retrieved {} relevant memory records from memory.", memories.size());

            String agentResponse = callAgent(request, events, memories);

            storeAgentResponseEvent(agentResponse, request.actorId(), sessionId, build);

            return new MyResponse(agentResponse);
        }

    }

    private String callAgent(MyRequest request, List<Event> events, List<String> memories) {
        List<Message> messages = convertAgentCoreMemoryEventToSpringAIMessages(events);
        if (messages.size() > 10) {
            messages = messages.subList(messages.size() - 10, messages.size());
        }

        String systemPrompt = "You are an intelligent assistant helping users with their queries. " +
                "Use the provided conversation history and relevant memories to inform your responses. " +
                "If you don't know the answer, respond with 'I don't know.'";
        if (!memories.isEmpty()) {
            StringBuilder memoryContext = new StringBuilder("Relevant memories:\n");
            for (String memory : memories) {
                memoryContext.append("- ").append(memory).append("\n");
            }
            systemPrompt += "\n" + memoryContext;
        }

        String content = chatClient
                .prompt()
                .system(systemPrompt)
                .user(request.prompt())
                .messages(messages.reversed()) // Ensure chronological order ir right
                .call()
                .content();
        logger.info("Agent response: {}", content);
        return content;
    }

    @NonNull
    private static List<Message> convertAgentCoreMemoryEventToSpringAIMessages(List<Event> events) {
        return events.stream().map(event -> {
            Conversational conversational = event.payload().stream()
                    .filter(payload -> payload.conversational() != null)
                    .findFirst()
                    .map(PayloadType::conversational)
                    .orElseThrow(() -> new IllegalStateException("No conversational payload found in event"));

            String content = conversational.content().text();
            if (conversational.role() == Role.USER) {
                return (Message) new UserMessage(content);
            } else {
                return new AssistantMessage(content);
            }
        }).toList();
    }

    private void storeAgentResponseEvent(String agentResponse, String actorId, String sessionId,
                                         BedrockAgentCoreClient build) {
        Conversational agentOutput = Conversational.builder()
                .content(Content.fromText(agentResponse))
                .role(Role.ASSISTANT)
                .build();

        PayloadType payload = PayloadType.builder().conversational(agentOutput).build();

        storeEvent(payload, actorId, sessionId, build);
    }

    private void storeUserEvent(MyRequest request, String sessionId, BedrockAgentCoreClient build) {
        Conversational userInput = Conversational.builder()
                .content(Content.fromText(request.prompt()))
                .role(Role.USER)
                .build();

        PayloadType payload = PayloadType.builder().conversational(userInput).build();

        storeEvent(payload, request.actorId(), sessionId, build);
    }

    private void storeEvent(PayloadType payload, String actorId, String sessionId, BedrockAgentCoreClient build) {
        CreateEventRequest buildRequest = CreateEventRequest.builder()
                .memoryId(memory.id())
                .sessionId(sessionId)
                .actorId(actorId)
                .payload(payload)
                .eventTimestamp(Instant.now())
                .build();

        CreateEventResponse event = build.createEvent(buildRequest);

        event.event().payload().forEach(i -> {
            if (i.conversational() != null) {
                logger.info("Stored conversational content in memory: {}", i.conversational().content().text());
            }
        });
    }

    private List<Event> loadEventsFromMemory(String actorId, String sessionId, BedrockAgentCoreClient build) {
        ListEventsRequest listRequest = ListEventsRequest.builder()
                .memoryId(memory.id())
                .actorId(actorId)
                .sessionId(sessionId)
                .build();

        ListEventsResponse response = build.listEvents(listRequest);

        response.events().forEach(event -> {
            event.payload().forEach(payload -> {
                if (payload.conversational() != null) {
                    logger.info("Loaded conversational content from memory: {}",
                            payload.conversational().content().text());
                }
            });
        });

        return response.events();
    }

    private List<String> retrieveEventsFromMemory(String query, String actorId, BedrockAgentCoreClient client) {
        var memoryStrategyId = this.memory.strategies().getFirst().strategyId();

        SearchCriteria searchCriteria = SearchCriteria.builder()
                .memoryStrategyId(memoryStrategyId)
                .searchQuery(query)
                .build();

        RetrieveMemoryRecordsRequest retrieveMemoryRecordsRequest = RetrieveMemoryRecordsRequest.builder()
                .memoryId(this.memory.id())
                .maxResults(4)
                .namespace("/strategies/" + memoryStrategyId + "/actors/" + actorId)
                .searchCriteria(searchCriteria)
                .build();
        RetrieveMemoryRecordsResponse retrieveMemoryRecordsResponse = client.retrieveMemoryRecords(retrieveMemoryRecordsRequest);

        if (!retrieveMemoryRecordsResponse.hasMemoryRecordSummaries()) {
            logger.info("No memory records found for query: {}", query);
            return List.of();
        }

        return retrieveMemoryRecordsResponse.memoryRecordSummaries().stream()
                .map(item -> {
                    logger.info("Memory record content: {}", item.content().text());
                    return item.content().text();
                })
                .toList();
    }

    @PostConstruct
    public void initMemory() {
        if (memoryPropertiesConfig.identifier() == null || memoryPropertiesConfig.identifier().isBlank()) {
            logger.info("No memory identifier configured, creating a new memory.");
            this.memory = createNewMemory();
        } else {
            logger.info("Memory identifier configured, loading existing memory for identifier {}.",
                    memoryPropertiesConfig.identifier());
            this.memory = loadExistingMemory(memoryPropertiesConfig.identifier());
        }
    }

    private Memory createNewMemory() {
        try (BedrockAgentCoreControlClient build =
                     BedrockAgentCoreControlClient.builder().region(Region.EU_WEST_1).build()) {

            SemanticMemoryStrategyInput semanticStrategy = SemanticMemoryStrategyInput.builder()
                    .name("jettro_demo_semantic_strategy")
                    .description("Semantic memory strategy for Jettro Bedrock Demo Application")
                    .build();

            MemoryStrategyInput memoryStrategyInput = MemoryStrategyInput.builder()
                    .semanticMemoryStrategy(semanticStrategy)
                    .build();

            CreateMemoryRequest request = CreateMemoryRequest.builder()
                    .name("jettro_demo_memory")
                    .description("This memory component is used by the Jettro Bedrock Demo Application")
                    .eventExpiryDuration(7) // in days
                    .memoryStrategies(memoryStrategyInput)
                    .build();

            CreateMemoryResponse memory = build.createMemory(request);

            logger.info("Created new memory with id {}", memory.memory().id());
            return memory.memory();
        }
    }

    private Memory loadExistingMemory(String identifier) {
        try (BedrockAgentCoreControlClient build =
                     BedrockAgentCoreControlClient.builder().region(Region.EU_WEST_1).build()) {

            GetMemoryRequest getRequest = GetMemoryRequest.builder()
                    .memoryId(identifier)
                    .build();

            Memory memory = build.getMemory(getRequest).memory();

            logger.info("Memory found: {}", memory.name());
            return memory;
        }

    }
}
