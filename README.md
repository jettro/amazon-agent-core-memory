# Amazon AgentCore with Spring AI Bedrock Starter Example

A Spring Boot application demonstrating integration with AWS Bedrock AgentCore for conversational AI with memory capabilities.

## Overview

This application showcases:
- **Conversational AI** using AWS Bedrock's Nova 2 Lite model via Spring AI
- **Memory Management** with AWS Bedrock Agent Core for persistent conversation history
- **Semantic Search** to retrieve relevant context from previous conversations
- **Multi-actor Support** with session-based conversation tracking

## Technology Stack

- **Spring Boot 3.5.8** - Application framework
- **Java 21** - Programming language
- **Spring AI 1.0.3** - AI integration layer
- **Spring AI Bedrock AgentCore Starter 1.0.0-RC1** - Bedrock Agent integration
- **AWS SDK 2.40.8** - Bedrock Agent Core, SSO, and related services
- **Maven** - Build tool

## Key Dependencies

```xml
<!-- Web framework -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Spring AI Bedrock integration -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-bedrock-converse</artifactId>
</dependency>

<!-- Bedrock Agent Core starter -->
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-bedrock-agentcore-starter</artifactId>
    <version>1.0.0-RC1</version>
</dependency>
```

## Architecture

### Core Components

#### MyAgentService
The main service class that orchestrates:
- **Memory initialization**: Creates or loads existing Bedrock Agent Core memory on startup
- **Event handling**: Processes user prompts via the `@AgentCoreInvocation` annotation
- **Conversation management**: Stores and retrieves conversation history
- **Semantic search**: Retrieves relevant memories based on query similarity
- **AI interaction**: Calls the Bedrock Nova model with context from memory

#### MyRequest
Input record containing:
- `prompt` - User's message
- `actorId` - Unique identifier for the user/actor
- `sessionId` - Conversation session identifier

#### MyResponse
Output record containing:
- `message` - AI-generated response

#### MemoryPropertiesConfig
Configuration properties for memory management:
- `memory.identifier` - Optional: Use existing memory ID, or leave blank to create new

## Features

### 1. Memory Management
- **Initialization**: Automatically creates a new memory or loads an existing one based on configuration
- **Event Storage**: Stores both user prompts and agent responses as conversational events
- **Event Expiry**: Configurable expiration (default: 7 days)
- **Session Tracking**: Maintains separate conversation histories per actor and session

### 2. Conversation History
- **Event Loading**: Retrieves the last 10 messages from the conversation history
- **Context Preservation**: Maintains chronological order of messages
- **Multi-turn Conversations**: Supports ongoing dialogues with full context

### 3. Semantic Memory Search
- **Relevance Retrieval**: Searches for up to 4 most relevant memory records
- **Query-based**: Uses the current prompt to find related past conversations
- **Contextual Enrichment**: Adds relevant memories to the system prompt

### 4. AI Model Integration
- Uses AWS Bedrock's **Nova 2 Lite** model (`eu.amazon.nova-2-lite-v1:0`)
- Configured for **EU-West-1** region
- Includes system prompt with memory-enhanced context

## Configuration

### application.properties

```properties
# Application name
spring.application.name=bedrock-app

# AWS Bedrock configuration
spring.ai.bedrock.aws.region=eu-west-1
spring.ai.bedrock.converse.chat.options.model=eu.amazon.nova-2-lite-v1:0

# Memory configuration (optional)
memory.identifier=
```

### AWS Credentials
The application uses AWS SDK default credential provider chain. Ensure you have:
- AWS credentials configured via environment variables, AWS config files, or IAM roles
- Appropriate permissions for Bedrock Agent Core services:
  - `bedrock-agent-core:*`
  - `bedrock-agent-core-control:*`

## Running the Application

### Prerequisites
- Java 21
- Maven 3.6+
- AWS credentials with Bedrock access
- Access to AWS Bedrock Nova models in eu-west-1

### Build and Run

```bash
# Build the project
./mvnw clean package

# Run the application
./mvnw spring-boot:run
```

The application will start on the default port (8080).

## API Usage

The service exposes an agent invocation endpoint via the Spring AI Bedrock AgentCore starter. Send requests with:

**Request Format:**
```json
{
  "prompt": "Your question or message",
  "actorId": "user-123",
  "sessionId": "conversation-1"
}
```

**Response Format:**
```json
{
  "message": "AI-generated response"
}
```

## Memory Strategy

The application uses a **semantic memory strategy** that:
1. Stores all conversational events (user/assistant messages)
2. Creates embeddings for semantic search
3. Retrieves relevant context based on query similarity
4. Namespaces memory by strategy and actor for isolation

## Development Notes

- Region is hardcoded to `EU-WEST-1` in the service class
- Memory expiry is set to 7 days
- Maximum 4 relevant memories are retrieved per query
- Last 10 conversation messages are included in context
- Session IDs are automatically generated if not provided via headers

## Project Structure

```
src/
├── main/
│   ├── java/com/example/bedrock/
│   │   ├── BedrockAppApplication.java    # Main application class
│   │   ├── MyAgentService.java           # Core agent logic
│   │   ├── MyRequest.java                # Request model
│   │   ├── MyResponse.java               # Response model
│   │   └── MemoryPropertiesConfig.java   # Configuration properties
│   └── resources/
│       └── application.properties         # Application configuration
└── test/
    └── java/com/example/bedrock/
        └── BedrockAppApplicationTests.java
```

## License

This is a demonstration project.
