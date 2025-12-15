package com.example.bedrock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.*;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.Memory;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AwsMemoryRepositoryTest {

    private BedrockAgentCoreClient coreClient;
    private MemoryProvider memoryProvider;

    @BeforeEach
    void setUp() {
        coreClient = mock(BedrockAgentCoreClient.class);
        // Provide a Memory (strategy not needed for list/create tests)
        Memory mem = Memory.builder()
                .id("mem-1")
                .build();
        memoryProvider = () -> mem;
    }

    @Test
    void listEvents_mapsToSpringAiMessages() {
        // given
        Event userEvent = Event.builder()
                .payload(PayloadType.builder()
                        .conversational(Conversational.builder()
                                .role(Role.USER)
                                .content(Content.fromText("hello"))
                                .build())
                        .build())
                .build();
        Event assistantEvent = Event.builder()
                .payload(PayloadType.builder()
                        .conversational(Conversational.builder()
                                .role(Role.ASSISTANT)
                                .content(Content.fromText("hi there"))
                                .build())
                        .build())
                .build();
        ListEventsResponse resp = ListEventsResponse.builder()
                .events(userEvent, assistantEvent)
                .build();
        when(coreClient.listEvents(any(ListEventsRequest.class))).thenReturn(resp);

        AwsMemoryRepository repo = new AwsMemoryRepository(coreClient, memoryProvider);

        // when
        List<Message> messages = repo.listEvents("actor-1", "session-1");

        // then
        assertEquals(2, messages.size());
        assertEquals("hello", ((UserMessage) messages.get(0)).getText());
        assertEquals("hi there", ((AssistantMessage) messages.get(1)).getText());

        ArgumentCaptor<ListEventsRequest> req = ArgumentCaptor.forClass(ListEventsRequest.class);
        verify(coreClient).listEvents(req.capture());
        assertEquals("mem-1", req.getValue().memoryId());
        assertEquals("actor-1", req.getValue().actorId());
        assertEquals("session-1", req.getValue().sessionId());
    }

    @Test
    void createUserAndAssistantEvent_buildsRequestsWithRoleAndTimestamp() {
        // given
        CreateEventResponse created = CreateEventResponse.builder()
                .event(Event.builder()
                        .payload(PayloadType.builder()
                                .conversational(Conversational.builder()
                                        .role(Role.USER)
                                        .content(Content.fromText("ignored"))
                                        .build())
                                .build())
                        .build())
                .build();
        when(coreClient.createEvent(any(CreateEventRequest.class))).thenReturn(created);

        AwsMemoryRepository repo = new AwsMemoryRepository(coreClient, memoryProvider);

        Instant ts = Instant.parse("2025-01-01T00:00:00Z");
        repo.createUserEvent("actor-1", "session-1", "hello", ts);

        ArgumentCaptor<CreateEventRequest> req1 = ArgumentCaptor.forClass(CreateEventRequest.class);
        verify(coreClient, times(1)).createEvent(req1.capture());
        CreateEventRequest sent1 = req1.getValue();
        assertEquals("mem-1", sent1.memoryId());
        assertEquals("actor-1", sent1.actorId());
        assertEquals("session-1", sent1.sessionId());
        assertEquals(ts, sent1.eventTimestamp());
        assertEquals(Role.USER, sent1.payload().getFirst().conversational().role());
        assertEquals("hello", sent1.payload().getFirst().conversational().content().text());

        // assistant
        repo.createAssistantEvent("actor-1", "session-1", "hi there", ts);
        ArgumentCaptor<CreateEventRequest> req2 = ArgumentCaptor.forClass(CreateEventRequest.class);
        verify(coreClient, times(2)).createEvent(req2.capture());
        CreateEventRequest sent2 = req2.getAllValues().get(1);
        assertEquals(Role.ASSISTANT, sent2.payload().getFirst().conversational().role());
        assertEquals("hi there", sent2.payload().getFirst().conversational().content().text());
    }

    // Note: searchMemories test is deferred until we confirm the exact SDK type for Memory strategies
}
