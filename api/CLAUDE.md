# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is **Bella Assistant API**, a Java Spring Boot application that provides OpenAI-compatible Assistant API endpoints with tool integration. The system implements a sophisticated assistant execution engine with support for multiple tools, file handling, and streaming responses.

## Build & Development Commands

### Maven Commands
- **Build project**: `mvn clean compile`
- **Run application**: `mvn spring-boot:run`
- **Run tests**: `mvn test`
- **Package**: `mvn clean package`
- **Generate JOOQ code**: `mvn org.jooq:jooq-codegen-maven:generate`

### Database Setup
- **Database**: MySQL (bella_assistant schema)
- **Connection**: localhost:3306 with root/123456 (configurable via application.yml)
- **Schema initialization**: Execute `sql/01-init-tables.sql`

### Running the Application
- **Default port**: 8087
- **API documentation**: http://localhost:8087/docs/index.html (Swagger UI)
- **Health check**: http://localhost:8087/actuator/health

## Architecture Overview

### Core Components

#### 1. Run Execution Engine (`core.run`)
- **RunExecutor**: Main orchestrator for assistant run execution with state management
- **ExecutionContext**: Carries all execution state and context through the run lifecycle
- **RunStateManager**: Manages run status transitions (queued → in_progress → completed/failed/etc.)
- **MessageExecutor**: Handles message creation and streaming to clients

#### 2. Planning System (`core.plan`)
- **Planner**: Core decision-making component that determines next actions in run execution
- **PlannerDecision**: Encodes decisions (LLM_CALL, WAIT_FOR_TOOL, COMPLETE, etc.)
- **Template system**: Uses Pebble templates for system prompts (`templates/planner_prompt.pebble`)

#### 3. Tool System (`core.tools`)
- **ToolExecutor**: Manages parallel tool execution with CompletableFuture
- **ToolHandler**: Interface for implementing tool functionality
- **ToolFetcher**: Resolves tool types to handlers
- Built-in tools: weather, web search, image generation, chart creation, RAG, file reading

#### 4. Memory Management (`core.memory`)
- **ContextTruncator**: Manages conversation context length to stay within model limits

#### 5. AI Integration (`core.ai`)
- **ChatService**: Handles LLM API calls with streaming support
- Uses Bella OpenAI SDK for model communication

### Key Design Patterns

#### Assistant Execution Flow
1. **Run Creation**: Client creates thread and run with assistant configuration
2. **Context Building**: ExecutionContext assembled with messages, tools, files
3. **Planning Loop**: Planner decides next action (LLM call, tool execution, completion)
4. **Execution**: RunExecutor coordinates between ChatService and ToolExecutor
5. **Streaming**: Real-time SSE streaming of results to client
6. **State Management**: Proper status transitions and error handling

#### Tool Architecture
- **Pluggable Design**: Tools implement ToolHandler interface
- **Parallel Execution**: Multiple tools can execute simultaneously
- **Final vs Non-Final**: Some tools produce final outputs (charts), others are intermediate (RAG)
- **Context Isolation**: Each tool receives isolated ToolContext

#### Memory Strategy
- **Conversation Truncation**: Automatically manages context window limits
- **File Attachment**: Supports file references in messages with S3 storage
- **Template Rendering**: Dynamic system prompts with context injection

### Database Layer

#### JOOQ Integration
- **Code Generation**: Database schema → Java classes in `src/codegen/java`
- **Type Safety**: Compile-time SQL validation
- **Repository Pattern**: Repos in `db.repo` package extend BaseRepo

#### Key Tables
- **thread**: Conversation threads
- **message**: Messages within threads  
- **run**: Assistant execution runs
- **run_step**: Individual steps within runs
- **assistant**: Assistant configurations
- **assistant_tool**: Tool assignments to assistants

### Configuration Management

#### Properties Structure
- **Database**: HikariCP connection pooling to MySQL
- **Redis**: Redisson for caching and distributed locking
- **S3**: AWS S3 for file storage (configurable endpoint for MinIO)
- **Apollo**: Optional configuration center integration
- **Tools**: Individual tool configurations with URLs, API keys, models

#### Environment-Specific Config
- **application.yml**: Base configuration
- **application-ut.yml**: Unit test overrides
- **Apollo namespaces**: Runtime configuration updates

## Development Guidelines

### Code Organization
- **Controllers**: REST endpoints in `controller` package
- **Services**: Business logic in `service` package  
- **Core**: Assistant execution engine in `core` package
- **Generated Code**: JOOQ classes in `src/codegen/java` (do not edit manually)

### Tool Development
1. Implement `ToolHandler` interface
2. Register in `ToolFetcher` 
3. Add configuration in `application.yml`
4. Handle both sync and async execution patterns

### Testing Strategy
- **Unit Tests**: Use Spring Boot Test with H2 or MySQL testcontainers
- **Controller Tests**: Use `@WebMvcTest` with mocked services
- **Integration Tests**: Full application context with real database

### Deployment
- **Docker**: Multi-stage build with OpenJDK 8 base image
- **Volumes**: Persistent storage for logs, cache, config
- **Health Checks**: Actuator endpoints for monitoring
- **Metrics**: Prometheus integration for observability

## Common Development Tasks

### Adding a New Tool
1. Create handler class implementing `ToolHandler`
2. Add tool configuration to `application.yml` under `bella.assistant.tools`
3. Register handler in `ToolFetcher.getToolHandler()`
4. Add assistant tool mapping in database

### Debugging Runs
- **Logs**: Check application logs with DEBUG level for `com.ke.assistant`
- **Database**: Query `run` and `run_step` tables for execution history
- **SSE Stream**: Monitor browser network tab for real-time execution events

### Database Schema Changes
1. Update MySQL schema
2. Regenerate JOOQ code: `mvn org.jooq:jooq-codegen-maven:generate`
3. Update repository methods as needed
4. Add migration scripts to `sql/` directory