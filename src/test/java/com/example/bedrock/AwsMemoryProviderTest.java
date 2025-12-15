package com.example.bedrock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.bedrockagentcorecontrol.BedrockAgentCoreControlClient;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.CreateMemoryRequest;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.CreateMemoryResponse;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.GetMemoryRequest;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.GetMemoryResponse;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.Memory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AwsMemoryProviderTest {

    private BedrockAgentCoreControlClient controlClient;

    @BeforeEach
    void setUp() {
        controlClient = mock(BedrockAgentCoreControlClient.class);
    }

    @Test
    void whenIdentifierBlank_createsNewMemoryOnceAndCaches() {
        // given: no identifier configured -> creation path
        MemoryPropertiesConfig props = new MemoryPropertiesConfig("", null);

        Memory created = Memory.builder()
                .id("mem-123")
                .name("created-memory")
                .build();

        CreateMemoryResponse createResp = CreateMemoryResponse.builder()
                .memory(created)
                .build();

        when(controlClient.createMemory(any(CreateMemoryRequest.class))).thenReturn(createResp);

        AwsMemoryProvider provider = new AwsMemoryProvider(props, controlClient);

        // when
        Memory m1 = provider.get();
        Memory m2 = provider.get(); // should be cached, no second call

        // then
        assertSame(m1, m2, "Memory should be cached and identical across calls");
        assertEquals("mem-123", m1.id());

        // verify only one create call
        ArgumentCaptor<CreateMemoryRequest> reqCaptor = ArgumentCaptor.forClass(CreateMemoryRequest.class);
        verify(controlClient, times(1)).createMemory(reqCaptor.capture());

        CreateMemoryRequest sent = reqCaptor.getValue();
        assertNotNull(sent, "CreateMemoryRequest should be sent");
        // basic sanity checks on the request filled by provider
        assertEquals("jettro_demo_memory", sent.name());
        assertEquals(7, sent.eventExpiryDuration());
        assertTrue(sent.hasMemoryStrategies(), "Memory strategies must be set");
    }

    @Test
    void whenIdentifierProvided_loadsExistingMemoryOnceAndCaches() {
        // given: identifier configured -> load path
        String identifier = "mem-existing";
        MemoryPropertiesConfig props = new MemoryPropertiesConfig(identifier, null);

        Memory existing = Memory.builder()
                .id(identifier)
                .name("existing-memory")
                .build();

        GetMemoryResponse getResp = GetMemoryResponse.builder()
                .memory(existing)
                .build();

        when(controlClient.getMemory(any(GetMemoryRequest.class))).thenReturn(getResp);

        AwsMemoryProvider provider = new AwsMemoryProvider(props, controlClient);

        // when
        Memory m1 = provider.get();
        Memory m2 = provider.get(); // cached

        // then
        assertSame(m1, m2);
        assertEquals(identifier, m1.id());

        ArgumentCaptor<GetMemoryRequest> reqCaptor = ArgumentCaptor.forClass(GetMemoryRequest.class);
        verify(controlClient, times(1)).getMemory(reqCaptor.capture());
        GetMemoryRequest sent = reqCaptor.getValue();
        assertEquals(identifier, sent.memoryId());
    }
}
