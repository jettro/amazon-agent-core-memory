package com.example.bedrock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.*;

import java.time.Instant;
import java.util.List;

@Service
public class AwsMemoryRepository implements MemoryRepository {

    private static final Logger logger = LoggerFactory.getLogger(AwsMemoryRepository.class);

    private final BedrockAgentCoreClient coreClient;
    private final MemoryProvider memoryProvider;

    public AwsMemoryRepository(BedrockAgentCoreClient coreClient, MemoryProvider memoryProvider) {
        this.coreClient = coreClient;
        this.memoryProvider = memoryProvider;
    }

    @Override
    public List<Message> listEvents(String actorId, String sessionId) {
        ListEventsRequest listRequest = ListEventsRequest.builder()
                .memoryId(memoryProvider.get().id())
                .actorId(actorId)
                .sessionId(sessionId)
                .build();

        ListEventsResponse response = coreClient.listEvents(listRequest);

        response.events().forEach(event -> {
            event.payload().forEach(payload -> {
                if (payload.conversational() != null) {
                    logger.info("Loaded conversational content from memory: {}",
                            payload.conversational().content().text());
                }
            });
        });

        return convertAgentCoreMemoryEventToSpringAIMessages(response.events());
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


    @Override
    public void createAssistantEvent(String actorId, String sessionId, String content, Instant timestamp) {
        createEvent(actorId, sessionId, content, Role.ASSISTANT, timestamp);
    }

    public void createUserEvent(String actorId, String sessionId, String content, Instant timestamp) {
        createEvent(actorId, sessionId, content, Role.USER, timestamp);
    }

    private void createEvent(String actorId, String sessionId, String content, Role role,Instant timestamp) {
        Conversational agentOutput = Conversational.builder()
                .content(Content.fromText(content))
                .role(role)
                .build();

        PayloadType payload = PayloadType.builder().conversational(agentOutput).build();

        CreateEventRequest buildRequest = CreateEventRequest.builder()
                .memoryId(memoryProvider.get().id())
                .sessionId(sessionId)
                .actorId(actorId)
                .payload(payload)
                .eventTimestamp(timestamp)
                .build();

        CreateEventResponse event = coreClient.createEvent(buildRequest);

        event.event().payload().forEach(i -> {
            if (i.conversational() != null) {
                logger.info("Stored conversational content in memory: {}", i.conversational().content().text());
            }
        });
    }

    @Override
    public List<String> searchMemories(String query, String actorId, int maxResults) {
        var memoryStrategyId = memoryProvider.get().strategies().getFirst().strategyId();

        SearchCriteria searchCriteria = SearchCriteria.builder()
                .memoryStrategyId(memoryStrategyId)
                .searchQuery(query)
                .build();

        RetrieveMemoryRecordsRequest retrieveMemoryRecordsRequest = RetrieveMemoryRecordsRequest.builder()
                .memoryId(memoryProvider.get().id())
                .maxResults(maxResults)
                .namespace("/strategies/" + memoryStrategyId + "/actors/" + actorId)
                .searchCriteria(searchCriteria)
                .build();
        RetrieveMemoryRecordsResponse retrieveMemoryRecordsResponse = coreClient.retrieveMemoryRecords(retrieveMemoryRecordsRequest);

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
}
