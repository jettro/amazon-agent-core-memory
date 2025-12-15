package dev.jettro.bedrock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcorecontrol.BedrockAgentCoreControlClient;

@Configuration
public class AwsClientsConfig {

    @Bean
    public Region awsRegion(
            // Prefer explicit aws.region but fall back to Spring AI property for convenience
            @Value("${aws.region:#{null}}") String region,
            @Value("${spring.ai.bedrock.aws.region:eu-west-1}") String springAiRegion) {
        String effective = (region != null && !region.isBlank()) ? region : springAiRegion;
        return Region.of(effective);
    }

    @Bean
    public BedrockAgentCoreClient bedrockAgentCoreClient(Region region) {
        return BedrockAgentCoreClient.builder().region(region).build();
    }

    @Bean
    public BedrockAgentCoreControlClient bedrockAgentCoreControlClient(Region region) {
        return BedrockAgentCoreControlClient.builder().region(region).build();
    }

    @Bean
    public Clock clock() {
        // Use a single injectable clock to make time deterministic in tests
        return Clock.systemUTC();
    }
}
