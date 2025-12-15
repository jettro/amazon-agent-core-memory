package dev.jettro.bedrock;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MyAgentServicePureHelpersTest {

    @Test
    void trimToLastN_returnsLastNInOrder() {
        List<Message> msgs = List.of(
                new UserMessage("u1"),
                new AssistantMessage("a1"),
                new UserMessage("u2"),
                new AssistantMessage("a2"),
                new UserMessage("u3")
        );

        List<Message> trimmed = MyAgentService.trimToLastN(msgs, 3);

        assertEquals(3, trimmed.size());
        assertEquals("u2", ((UserMessage) trimmed.get(0)).getText());
        assertEquals("a2", ((AssistantMessage) trimmed.get(1)).getText());
        assertEquals("u3", ((UserMessage) trimmed.get(2)).getText());

        // n larger than size -> returns original list
        List<Message> same = MyAgentService.trimToLastN(msgs, 10);
        assertSame(msgs, same);

        // edge cases
        assertTrue(MyAgentService.trimToLastN(List.of(), 5).isEmpty());
        assertTrue(MyAgentService.trimToLastN(msgs, 0).isEmpty());
        assertTrue(MyAgentService.trimToLastN(null, 5).isEmpty());
    }

    @Test
    void buildSystemPrompt_buildsBaseAndAppendsMemories() {
        String baseText = "You are an intelligent assistant helping users with their queries. " +
                "Use the provided conversation history and relevant memories to inform your responses. " +
                "If you don't know the answer, respond with 'I don't know.'";

        String base = MyAgentService.buildSystemPrompt(List.of(), baseText);
        assertTrue(base.startsWith("You are an intelligent assistant"));
        assertFalse(base.contains("Relevant memories:"));

        String withMemories = MyAgentService.buildSystemPrompt(List.of("m1", "m2"), baseText);
        assertTrue(withMemories.contains("Relevant memories:"));
        assertTrue(withMemories.contains("- m1"));
        assertTrue(withMemories.contains("- m2"));
    }
}
