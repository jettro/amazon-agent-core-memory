package dev.jettro.bedrock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BedrockAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(BedrockAppApplication.class, args);
	}

}
