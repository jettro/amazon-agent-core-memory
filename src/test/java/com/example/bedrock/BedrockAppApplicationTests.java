package com.example.bedrock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "chat.system-prompt-base=Test base prompt")
class BedrockAppApplicationTests {

	@Test
	void contextLoads() {
	}

}
