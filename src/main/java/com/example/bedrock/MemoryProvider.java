package com.example.bedrock;

import software.amazon.awssdk.services.bedrockagentcorecontrol.model.Memory;

/**
 * Provides access to a {@link Memory} instance. Implementations may lazily
 * create or load the memory and cache it for subsequent calls.
 */
public interface MemoryProvider {
    Memory get();
}
