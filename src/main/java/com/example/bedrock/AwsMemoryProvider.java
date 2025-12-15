package com.example.bedrock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.bedrockagentcorecontrol.BedrockAgentCoreControlClient;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.CreateMemoryRequest;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.CreateMemoryResponse;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.GetMemoryRequest;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.Memory;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.MemoryStrategyInput;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.SemanticMemoryStrategyInput;

/**
 * AWS-backed implementation of {@link MemoryProvider} that lazily creates or loads
 * a Memory using the Bedrock Agent Core Control client.
 */
@Service
public class AwsMemoryProvider implements MemoryProvider {

    private static final Logger log = LoggerFactory.getLogger(AwsMemoryProvider.class);

    private final MemoryPropertiesConfig memoryPropertiesConfig;
    private final BedrockAgentCoreControlClient controlClient;

    private volatile Memory memory;

    public AwsMemoryProvider(MemoryPropertiesConfig memoryPropertiesConfig,
                             BedrockAgentCoreControlClient controlClient) {
        this.memoryPropertiesConfig = memoryPropertiesConfig;
        this.controlClient = controlClient;
    }

    @Override
    public Memory get() {
        var m = memory;
        if (m != null) {
            return m;
        }
        synchronized (this) {
            if (memory != null) {
                return memory;
            }
            if (memoryPropertiesConfig.identifier() == null || memoryPropertiesConfig.identifier().isBlank()) {
                log.info("No memory identifier configured, creating a new memory.");
                memory = createNewMemory();
            } else {
                log.info("Memory identifier configured, loading existing memory for identifier {}.",
                        memoryPropertiesConfig.identifier());
                memory = loadExistingMemory(memoryPropertiesConfig.identifier());
            }
            return memory;
        }
    }

    private Memory createNewMemory() {
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

        CreateMemoryResponse response = controlClient.createMemory(request);
        log.info("Created new memory with id {}", response.memory().id());
        return response.memory();
    }

    private Memory loadExistingMemory(String identifier) {
        GetMemoryRequest getRequest = GetMemoryRequest.builder()
                .memoryId(identifier)
                .build();

        Memory memory = controlClient.getMemory(getRequest).memory();
        log.info("Memory found: {}", memory.name());
        return memory;
    }
}
