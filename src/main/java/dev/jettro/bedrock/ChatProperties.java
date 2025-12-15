package dev.jettro.bedrock;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chat")
public record ChatProperties(
        @NotBlank String systemPromptBase,
        Integer historyWindow
) {
}
