# Sprint 4: Advanced Features Implementation Plan

## Overview

This document outlines the implementation approach for building the remaining core services to complete the CodePromptu platform. Sprint 4 focuses on advanced prompt processing, analytics, user interface, and system monitoring capabilities.

## Current Foundation Status

### ✅ Completed Services (Sprint 1-3)
- **API Service**: Complete CRUD operations, vector similarity search, Spring AI integration
- **Gateway Service**: LLM proxy functionality, prompt capture, API key pass-through
- **Config Server**: Centralized configuration management with Spring Cloud Config
- **Database**: PostgreSQL with pgvector, full schema, embedding management
- **Shared Library**: Common domain models, utilities, and configurations
- **Testing**: Comprehensive test suite (57 tests passing, 0 failures)
- **Docker Infrastructure**: Containerized deployment ready

### 🎯 Sprint 4 Objectives
Build the 4 remaining services to complete the platform:
1. **Processor Service**: Advanced prompt analysis and template induction
2. **Worker Service**: Background job processing and maintenance
3. **UI Service**: React frontend for system management
4. **Monitoring Service**: Observability and system health monitoring

## Implementation Timeline

**Sprint Duration**: 8 weeks (August - September 2025)
**Team Capacity**: 4 developers (Backend, Frontend, DevOps, Full-stack)

### Week 1-2: Processor Service Foundation
### Week 3: Worker Service Implementation  
### Week 4-6: UI Service Development
### Week 7: Monitoring Service Setup
### Week 8: Integration Testing & Deployment

---

## Service 1: Processor Service (Weeks 1-2)

### Purpose
Advanced prompt processing pipeline for template induction, conversation analysis, and analytics generation.

### Core Components

#### A. Template Induction Engine
**Purpose**: Automatically discover prompt templates from captured data

```java
@Service
public class TemplateInductionService {
    
    private final PromptClusteringService clusteringService;
    private final LCSTemplateExtractor templateExtractor;
    private final TemplateRepository templateRepository;
    
    @Async
    public CompletableFuture<List<PromptTemplate>> induceTemplates(List<Prompt> prompts) {
        // 1. Cluster similar prompts using embedding similarity
        List<PromptCluster> clusters = clusteringService.clusterPrompts(prompts);
        
        // 2. Extract templates using Longest Common Subsequence
        List<PromptTemplate> templates = new ArrayList<>();
        for (PromptCluster cluster : clusters) {
            PromptTemplate template = templateExtractor.extractTemplate(cluster);
            templates.add(template);
        }
        
        // 3. Store templates with metadata
        return CompletableFuture.completedFuture(
            templateRepository.saveAll(templates)
        );
    }
}
```

#### B. Conversation Tracking Service
**Purpose**: Group related prompts and responses into complete conversation sessions

```java
@Service
public class ConversationTrackingService {
    
    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final SessionAnalyzer sessionAnalyzer;
    
    public ConversationSession trackPrompt(PromptUsage usage) {
        // 1. Identify conversation context
        ConversationContext context = sessionAnalyzer.analyzeContext(usage);
        
        // 2. Find or create conversation session
        ConversationSession session = findOrCreateSession(context);
        
        // 3. Create conversation message for the prompt
        ConversationMessage promptMessage = ConversationMessage.builder()
            .sessionId(session.getId())
            .messageType(MessageType.PROMPT)
            .content(usage.getPromptContent())
            .timestamp(usage.getTimestamp())
            .metadata(usage.getMetadata())
            .build();
        
        messageRepository.save(promptMessage);
        session.incrementMessageCount();
        
        return conversationRepository.save(session);
    }
    
    public ConversationSession trackResponse(LLMResponse response) {
        // 1. Find the conversation session by correlation ID
        ConversationSession session = findSessionByCorrelationId(response.getCorrelationId());
        
        if (session != null) {
            // 2. Create conversation message for the response
            ConversationMessage responseMessage = ConversationMessage.builder()
                .sessionId(session.getId())
                .messageType(MessageType.RESPONSE)
                .content(response.getContent())
                .timestamp(response.getTimestamp())
                .provider(response.getProvider())
                .model(response.getModel())
                .tokenUsage(response.getTokenUsage())
                .metadata(response.getMetadata())
                .build();
            
            messageRepository.save(responseMessage);
            session.incrementMessageCount();
            session.addTokenUsage(response.getTokenUsage());
            
            return conversationRepository.save(session);
        }
        
        return null;
    }
    
    public List<ConversationMessage> getFullConversation(UUID sessionId) {
        return messageRepository.findBySessionIdOrderByTimestamp(sessionId);
    }
    
    private ConversationSession findOrCreateSession(ConversationContext context) {
        return conversationRepository
            .findActiveSessionByContext(context)
            .orElseGet(() -> createNewSession(context));
    }
    
    private ConversationSession findSessionByCorrelationId(String correlationId) {
        return conversationRepository.findByCorrelationId(correlationId);
    }
}
```

#### C. Analytics Generation Service
**Purpose**: Generate insights and metrics from prompt usage data

```java
@Service
public class AnalyticsGenerationService {
    
    private final AnalyticsRepository analyticsRepository;
    private final MetricsCalculator metricsCalculator;
    
    @Scheduled(fixedRate = 3600000) // Every hour
    public void generateHourlyAnalytics() {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(1, ChronoUnit.HOURS);
        
        // 1. Collect usage data
        List<PromptUsage> usageData = getUsageData(startTime, endTime);
        
        // 2. Calculate metrics
        AnalyticsMetrics metrics = metricsCalculator.calculateMetrics(usageData);
        
        // 3. Store analytics
        AnalyticsReport report = AnalyticsReport.builder()
            .timeRange(TimeRange.of(startTime, endTime))
            .metrics(metrics)
            .generatedAt(Instant.now())
            .build();
            
        analyticsRepository.save(report);
    }
}
```

### Database Schema Extensions

```sql
-- Template storage
CREATE TABLE prompt_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    pattern TEXT NOT NULL,
    variables JSONB,
    cluster_id UUID,
    confidence_score DECIMAL(3,2),
    usage_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Enhanced conversation sessions with correlation tracking
CREATE TABLE conversation_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    correlation_id VARCHAR(255) UNIQUE NOT NULL,
    user_context JSONB,
    session_start TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    session_end TIMESTAMP,
    message_count INTEGER DEFAULT 0,
    total_tokens INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Individual conversation messages (prompts and responses)
CREATE TABLE conversation_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES conversation_sessions(id) ON DELETE CASCADE,
    message_type VARCHAR(20) NOT NULL CHECK (message_type IN ('PROMPT', 'RESPONSE')),
    content TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    provider VARCHAR(50),
    model VARCHAR(100),
    token_usage JSONB,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for efficient conversation retrieval
CREATE INDEX idx_conversation_messages_session_timestamp 
ON conversation_messages(session_id, timestamp);

CREATE INDEX idx_conversation_sessions_correlation 
ON conversation_sessions(correlation_id);

CREATE INDEX idx_conversation_sessions_status 
ON conversation_sessions(status, updated_at);

-- Analytics reports
CREATE TABLE analytics_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    time_range_start TIMESTAMP NOT NULL,
    time_range_end TIMESTAMP NOT NULL,
    metrics JSONB NOT NULL,
    report_type VARCHAR(50) NOT NULL,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Message types enum for type safety
CREATE TYPE message_type_enum AS ENUM ('PROMPT', 'RESPONSE');
ALTER TABLE conversation_messages 
ALTER COLUMN message_type TYPE message_type_enum 
USING message_type::message_type_enum;
```

### Domain Models for Conversation Tracking

```java
// ConversationSession.java
@Entity
@Table(name = "conversation_sessions")
public class ConversationSession {
    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;
    
    @Column(name = "correlation_id", unique = true, nullable = false)
    private String correlationId;
    
    @Column(name = "user_context", columnDefinition = "jsonb")
    private String userContext;
    
    @Column(name = "session_start")
    private Instant sessionStart;
    
    @Column(name = "session_end")
    private Instant sessionEnd;
    
    @Column(name = "message_count")
    private Integer messageCount = 0;
    
    @Column(name = "total_tokens")
    private Integer totalTokens = 0;
    
    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.ACTIVE;
    
    public void incrementMessageCount() {
        this.messageCount++;
        this.updatedAt = Instant.now();
    }
    
    public void addTokenUsage(TokenUsage tokenUsage) {
        if (tokenUsage != null) {
            this.totalTokens += tokenUsage.getTotalTokens();
        }
    }
    
    // getters, setters, builder pattern...
}

// ConversationMessage.java
@Entity
@Table(name = "conversation_messages")
public class ConversationMessage {
    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;
    
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private MessageType messageType;
    
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "timestamp")
    private Instant timestamp;
    
    @Column(name = "provider")
    private String provider;
    
    @Column(name = "model")
    private String model;
    
    @Column(name = "token_usage", columnDefinition = "jsonb")
    private String tokenUsage;
    
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
    
    // getters, setters, builder pattern...
}

// Supporting enums and classes
public enum MessageType {
    PROMPT, RESPONSE
}

public enum SessionStatus {
    ACTIVE, COMPLETED, EXPIRED
}

// LLMResponse.java - for capturing responses from gateway
@Data
@Builder
public class LLMResponse {
    private String correlationId;
    private String content;
    private Instant timestamp;
    private String provider;
    private String model;
    private TokenUsage tokenUsage;
    private Map<String, Object> metadata;
}

// TokenUsage.java - for tracking token consumption
@Data
@Builder
public class TokenUsage {
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
}
```

### Gateway Service Enhancements for Response Capture

```java
// Enhanced LLM Proxy Filter to capture responses
@Component
public class LLMProxyFilter implements GlobalFilter, Ordered {
    
    private final ConversationTrackingService conversationService;
    private final ObjectMapper objectMapper;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = generateCorrelationId();
        exchange.getRequest().mutate()
            .header("X-Correlation-ID", correlationId);
        
        // Capture the request (prompt)
        return captureRequest(exchange, correlationId)
            .then(chain.filter(exchange))
            .then(captureResponse(exchange, correlationId));
    }
    
    private Mono<Void> captureRequest(ServerWebExchange exchange, String correlationId) {
        return exchange.getRequest().getBody()
            .cast(DataBuffer.class)
            .collectList()
            .map(dataBuffers -> {
                String requestBody = extractBody(dataBuffers);
                
                // Extract prompt from request body
                String promptContent = extractPromptFromRequest(requestBody);
                
                // Create prompt usage record
                PromptUsage usage = PromptUsage.builder()
                    .correlationId(correlationId)
                    .promptContent(promptContent)
                    .timestamp(Instant.now())
                    .provider(extractProvider(exchange))
                    .metadata(extractMetadata(exchange))
                    .build();
                
                // Track the prompt in conversation
                conversationService.trackPrompt(usage);
                
                return requestBody;
            })
            .then();
    }
    
    private Mono<Void> captureResponse(ServerWebExchange exchange, String correlationId) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();
        
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux) {
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
                    
                    return super.writeWith(fluxBody.map(dataBuffer -> {
                        // Extract response content
                        String responseContent = extractResponseContent(dataBuffer);
                        
                        // Create LLM response record
                        LLMResponse llmResponse = LLMResponse.builder()
                            .correlationId(correlationId)
                            .content(responseContent)
                            .timestamp(Instant.now())
                            .provider(extractProvider(exchange))
                            .model(extractModel(responseContent))
                            .tokenUsage(extractTokenUsage(responseContent))
                            .metadata(extractResponseMetadata(exchange))
                            .build();
                        
                        // Track the response in conversation
                        conversationService.trackResponse(llmResponse);
                        
                        return dataBuffer;
                    }));
                }
                return super.writeWith(body);
            }
        };
        
        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }
    
    private String extractPromptFromRequest(String requestBody) {
        try {
            JsonNode jsonNode = objectMapper.readTree(requestBody);
            
            // Handle different LLM provider request formats
            if (jsonNode.has("messages")) {
                // OpenAI/Anthropic format
                JsonNode messages = jsonNode.get("messages");
                if (messages.isArray() && messages.size() > 0) {
                    JsonNode lastMessage = messages.get(messages.size() - 1);
                    return lastMessage.get("content").asText();
                }
            } else if (jsonNode.has("prompt")) {
                // Direct prompt format
                return jsonNode.get("prompt").asText();
            }
            
            return requestBody; // Fallback to full body
        } catch (Exception e) {
            log.warn("Failed to extract prompt from request: {}", e.getMessage());
            return requestBody;
        }
    }
    
    private String extractResponseContent(DataBuffer dataBuffer) {
        try {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            String responseBody = new String(bytes, StandardCharsets.UTF_8);
            
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            
            // Handle different LLM provider response formats
            if (jsonNode.has("choices")) {
                // OpenAI format
                JsonNode choices = jsonNode.get("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode firstChoice = choices.get(0);
                    if (firstChoice.has("message")) {
                        return firstChoice.get("message").get("content").asText();
                    } else if (firstChoice.has("text")) {
                        return firstChoice.get("text").asText();
                    }
                }
            } else if (jsonNode.has("content")) {
                // Anthropic format
                JsonNode content = jsonNode.get("content");
                if (content.isArray() && content.size() > 0) {
                    return content.get(0).get("text").asText();
                }
            }
            
            return responseBody; // Fallback to full response
        } catch (Exception e) {
            log.warn("Failed to extract response content: {}", e.getMessage());
            return ""; // Return empty string on error
        }
    }
    
    private TokenUsage extractTokenUsage(String responseContent) {
        try {
            JsonNode jsonNode = objectMapper.readTree(responseContent);
            
            if (jsonNode.has("usage")) {
                JsonNode usage = jsonNode.get("usage");
                return TokenUsage.builder()
                    .promptTokens(usage.get("prompt_tokens").asInt())
                    .completionTokens(usage.get("completion_tokens").asInt())
                    .totalTokens(usage.get("total_tokens").asInt())
                    .build();
            }
        } catch (Exception e) {
            log.warn("Failed to extract token usage: {}", e.getMessage());
        }
        
        return null;
    }
    
    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
    
    @Override
    public int getOrder() {
        return -1; // High priority to capture all requests/responses
    }
}
```

### Configuration

```yaml
# processor.yml
spring:
  application:
    name: processor
  batch:
    job:
      enabled: true
    initialize-schema: always

codepromptu:
  processor:
    clustering:
      similarity-threshold: 0.85
      min-cluster-size: 3
      max-clusters: 100
    template-induction:
      min-pattern-length: 10
      confidence-threshold: 0.7
    analytics:
      batch-size: 1000
      retention-days: 365
    conversation:
      session-timeout-minutes: 30
      max-messages-per-session: 100
```

---

## Service 2: Worker Service (Week 3)

### Purpose
Background job processing, maintenance tasks, and asynchronous operations.

### Core Components

#### A. Job Processing Engine
**Purpose**: Handle background tasks with queue management

```java
@Service
public class JobProcessingService {
    
    private final JobRepository jobRepository;
    private final TaskExecutor taskExecutor;
    
    @EventListener
    public void handleJobSubmission(JobSubmissionEvent event) {
        Job job = Job.builder()
            .type(event.getJobType())
            .payload(event.getPayload())
            .status(JobStatus.QUEUED)
            .submittedAt(Instant.now())
            .build();
            
        jobRepository.save(job);
        scheduleJob(job);
    }
    
    private void scheduleJob(Job job) {
        taskExecutor.execute(() -> {
            try {
                processJob(job);
            } catch (Exception e) {
                handleJobFailure(job, e);
            }
        });
    }
}
```

#### B. Bulk Embedding Service
**Purpose**: Generate embeddings for large batches of prompts

```java
@Service
public class BulkEmbeddingService {
    
    private final EmbeddingClient embeddingClient;
    private final PromptRepository promptRepository;
    
    @Async
    public CompletableFuture<Void> generateBulkEmbeddings(List<UUID> promptIds) {
        List<Prompt> prompts = promptRepository.findAllById(promptIds);
        
        // Process in batches to avoid API limits
        List<List<Prompt>> batches = Lists.partition(prompts, 100);
        
        for (List<Prompt> batch : batches) {
            processBatch(batch);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    private void processBatch(List<Prompt> batch) {
        List<String> contents = batch.stream()
            .map(Prompt::getContent)
            .collect(Collectors.toList());
            
        List<List<Double>> embeddings = embeddingClient.embed(contents);
        
        for (int i = 0; i < batch.size(); i++) {
            Prompt prompt = batch.get(i);
            prompt.setEmbedding(embeddings.get(i));
            promptRepository.save(prompt);
        }
    }
}
```

#### C. Maintenance Tasks
**Purpose**: System cleanup and optimization

```java
@Component
public class MaintenanceTasks {
    
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void cleanupOldData() {
        // Remove old analytics data
        analyticsRepository.deleteOlderThan(
            Instant.now().minus(365, ChronoUnit.DAYS)
        );
        
        // Archive inactive prompts
        archiveInactivePrompts();
        
        // Optimize database indexes
        optimizeIndexes();
    }
    
    @Scheduled(cron = "0 0 * * * ?") // Hourly
    public void updateStatistics() {
        // Update prompt usage statistics
        updatePromptStatistics();
        
        // Refresh materialized views
        refreshAnalyticsViews();
    }
}
```

---

## Service 3: UI Service (Weeks 4-6)

### Purpose
React-based frontend for system management, analytics, and user interaction.

### Technology Stack
- **Frontend**: React 18+ with TypeScript
- **State Management**: Redux Toolkit + RTK Query
- **UI Framework**: Material-UI (MUI) v5
- **Charts**: Recharts for analytics visualization
- **Authentication**: JWT with refresh tokens
- **Build Tool**: Vite for fast development

### Core Components

#### A. Prompt Management Interface
**Purpose**: CRUD operations for prompts with advanced search

```typescript
// components/PromptManager.tsx
export const PromptManager: React.FC = () => {
  const [prompts, setPrompts] = useState<Prompt[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [filters, setFilters] = useState<PromptFilters>({});
  
  const { data, isLoading, error } = useGetPromptsQuery({
    search: searchQuery,
    filters,
    page: 1,
    size: 20
  });
  
  return (
    <Container>
      <PromptSearchBar 
        value={searchQuery}
        onChange={setSearchQuery}
        filters={filters}
        onFiltersChange={setFilters}
      />
      
      <PromptGrid 
        prompts={data?.prompts || []}
        loading={isLoading}
        onEdit={handleEdit}
        onDelete={handleDelete}
      />
      
      <PromptDialog 
        open={dialogOpen}
        prompt={selectedPrompt}
        onSave={handleSave}
        onClose={() => setDialogOpen(false)}
      />
    </Container>
  );
};
```

#### B. Analytics Dashboard
**Purpose**: Visual insights into prompt usage and patterns

```typescript
// components/AnalyticsDashboard.tsx
export const AnalyticsDashboard: React.FC = () => {
  const { data: analytics } = useGetAnalyticsQuery({
    timeRange: 'last_7_days'
  });
  
  return (
    <Grid container spacing={3}>
      <Grid item xs={12} md={6}>
        <MetricCard
          title="Total Prompts"
          value={analytics?.totalPrompts}
          change={analytics?.promptsChange}
        />
      </Grid>
      
      <Grid item xs={12} md={6}>
        <MetricCard
          title="Active Templates"
          value={analytics?.activeTemplates}
          change={analytics?.templatesChange}
        />
      </Grid>
      
      <Grid item xs={12}>
        <UsageChart data={analytics?.usageOverTime} />
      </Grid>
      
      <Grid item xs={12} md={6}>
        <TopPromptsTable prompts={analytics?.topPrompts} />
      </Grid>
      
      <Grid item xs={12} md={6}>
        <ProviderDistribution data={analytics?.providerStats} />
      </Grid>
    </Grid>
  );
};
```

#### C. Template Visualization
**Purpose**: Display and manage discovered prompt templates

```typescript
// components/TemplateVisualization.tsx
export const TemplateVisualization: React.FC = () => {
  const { data: templates } = useGetTemplatesQuery();
  
  return (
    <Box>
      <TemplateFilters />
      
      <Grid container spacing={2}>
        {templates?.map(template => (
          <Grid item xs={12} md={6} lg={4} key={template.id}>
            <TemplateCard
              template={template}
              onEdit={handleEdit}
              onApprove={handleApprove}
              onReject={handleReject}
            />
          </Grid>
        ))}
      </Grid>
      
      <TemplateDetailDialog
        template={selectedTemplate}
        open={detailDialogOpen}
        onClose={() => setDetailDialogOpen(false)}
      />
    </Box>
  );
};
```

#### D. Conversation Viewer
**Purpose**: Display full conversations with prompts and responses

```typescript
// components/ConversationViewer.tsx
export const ConversationViewer: React.FC = () => {
  const [selectedSession, setSelectedSession] = useState<string | null>(null);
  const { data: sessions } = useGetConversationSessionsQuery();
  const { data: messages } = useGetConversationMessagesQuery(
    selectedSession || '',
    { skip: !selectedSession }
  );

  return (
    <Grid container spacing={3}>
      <Grid item xs={12} md={4}>
        <ConversationSessionList
          sessions={sessions || []}
          selectedSession={selectedSession}
          onSessionSelect={setSelectedSession}
        />
      </Grid>
      
      <Grid item xs={12} md={8}>
        {selectedSession ? (
          <ConversationMessages
            messages={messages || []}
            sessionId={selectedSession}
          />
        ) : (
          <Box display="flex" justifyContent="center" alignItems="center" height="400px">
            <Typography variant="h6" color="textSecondary">
              Select a conversation to view messages
            </Typography>
          </Box>
        )}
      </Grid>
    </Grid>
  );
};

// components/ConversationMessages.tsx
export const ConversationMessages: React.FC<{
  messages: ConversationMessage[];
  sessionId: string;
}> = ({ messages, sessionId }) => {
  return (
    <Paper elevation={1} sx={{ height: '600px', overflow: 'auto', p: 2 }}>
      <Box display="flex" justifyContent="between" alignItems="center" mb={2}>
        <Typography variant="h6">Conversation Messages</Typography>
        <Chip label={`${messages.length} messages`} size="small" />
      </Box>
      
      <List>
        {messages.map((message, index) => (
          <React.Fragment key={message.id}>
            <ListItem alignItems="flex-start">
              <ListItemAvatar>
                <Avatar sx={{ 
                  bgcolor: message.messageType === 'PROMPT' ? 'primary.main' : 'secondary.main' 
                }}>
                  {message.messageType === 'PROMPT' ? '👤' : '🤖'}
                </Avatar>
              </ListItemAvatar>
              
              <ListItemText
                primary={
                  <Box display="flex" justifyContent="space-between" alignItems="center">
                    <Typography variant="subtitle2">
                      {message.messageType === 'PROMPT' ? 'User' : `${message.provider} (${message.model})`}
                    </Typography>
                    <Typography variant="caption" color="textSecondary">
                      {formatTimestamp(message.timestamp)}
                    </Typography>
                  </Box>
                }
                secondary={
                  <Box mt={1}>
                    <Typography variant="body2" component="div">
                      <ReactMarkdown>{message.content}</ReactMarkdown>
                    </Typography>
                    
                    {message.tokenUsage && (
                      <Box mt={1}>
                        <Chip 
                          label={`${JSON.parse(message.tokenUsage).totalTokens} tokens`}
                          size="small"
                          variant="outlined"
                        />
                      </Box>
                    )}
                  </Box>
                }
              />
            </ListItem>
            
            {index < messages.length - 1 && <Divider variant="inset" component="li" />}
          </React.Fragment>
        ))}
      </List>
    </Paper>
  );
};

// components/ConversationSessionList.tsx
export const ConversationSessionList: React.FC<{
  sessions: ConversationSession[];
  selectedSession: string | null;
  onSessionSelect: (sessionId: string) => void;
}> = ({ sessions, selectedSession, onSessionSelect }) => {
  return (
    <Paper elevation={1} sx={{ height: '600px', overflow: 'auto' }}>
      <Box p={2} borderBottom={1} borderColor="divider">
        <Typography variant="h6">Conversations</Typography>
        <Typography variant="body2" color="textSecondary">
          {sessions.length} active sessions
        </Typography>
      </Box>
      
      <List>
        {sessions.map((session) => (
          <ListItem
            key={session.id}
            button
            selected={selectedSession === session.id}
            onClick={() => onSessionSelect(session.id)}
          >
            <ListItemText
              primary={
                <Box display="flex" justifyContent="space-between">
                  <Typography variant="subtitle2">
                    Session {session.id.substring(0, 8)}...
                  </Typography>
                  <Chip 
                    label={session.status}
                    size="small"
                    color={session.status === 'ACTIVE' ? 'success' : 'default'}
                  />
                </Box>
              }
              secondary={
                <Box>
                  <Typography variant="body2" color="textSecondary">
                    {session.messageCount} messages • {session.totalTokens} tokens
                  </Typography>
                  <Typography variant="caption" color="textSecondary">
                    Started: {formatTimestamp(session.sessionStart)}
                  </Typography>
                </Box>
              }
            />
          </ListItem>
        ))}
      </List>
    </Paper>
  );
};
```

### API Integration Layer

```typescript
// api/promptApi.ts
export const promptApi = createApi({
  reducerPath: 'promptApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/api/v1/',
    prepareHeaders: (headers, { getState }) => {
      const token = (getState() as RootState).auth.token;
      if (token) {
        headers.set('authorization', `Bearer ${token}`);
      }
      return headers;
    },
  }),
  tagTypes: ['Prompt', 'Template', 'Analytics', 'Conversation'],
  endpoints: (builder) => ({
    getPrompts: builder.query<PromptsResponse, PromptsRequest>({
      query: (params) => ({
        url: 'prompts',
        params,
      }),
      providesTags: ['Prompt'],
    }),
    
    createPrompt: builder.mutation<Prompt, CreatePromptRequest>({
      query: (prompt) => ({
        url: 'prompts',
        method: 'POST',
        body: prompt,
      }),
      invalidatesTags: ['Prompt'],
    }),
    
    getAnalytics: builder.query<AnalyticsData, AnalyticsRequest>({
      query: (params) => ({
        url: 'analytics',
        params,
      }),
      providesTags: ['Analytics'],
    }),
    
    // Conversation endpoints
    getConversationSessions: builder.query<ConversationSession[], void>({
      query: () => 'conversations/sessions',
      providesTags: ['Conversation'],
    }),
    
    getConversationMessages: builder.query<ConversationMessage[], string>({
      query: (sessionId) => `conversations/sessions/${sessionId}/messages`,
      providesTags: (result, error, sessionId) => [
        { type: 'Conversation', id: sessionId }
      ],
    }),
    
    getConversationAnalytics: builder.query<ConversationAnalytics, ConversationAnalyticsRequest>({
      query: (params) => ({
        url: 'conversations/analytics',
        params,
      }),
      providesTags: ['Analytics'],
    }),
  }),
});

// Export hooks for components
export const {
  useGetPromptsQuery,
  useCreatePromptMutation,
  useGetAnalyticsQuery,
  useGetConversationSessionsQuery,
  useGetConversationMessagesQuery,
  useGetConversationAnalyticsQuery,
} = promptApi;
```

### Enhanced Success Criteria for Full Conversation Recovery

```typescript
// Integration test for full conversation recovery
@Test
void shouldRecoverFullConversationWithResponses() {
    // 1. Send a prompt through gateway
    String promptContent = "Explain microservices architecture";
    String correlationId = sendPromptThroughGateway(promptContent);
    
    // 2. Simulate LLM response
    String responseContent = "Microservices architecture is a design pattern...";
    simulateLLMResponse(correlationId, responseContent);
    
    // 3. Wait for conversation tracking to complete
    await().atMost(10, SECONDS)
        .until(() -> conversationExists(correlationId));
    
    // 4. Retrieve full conversation
    ConversationSession session = getConversationByCorrelationId(correlationId);
    List<ConversationMessage> messages = getConversationMessages(session.getId());
    
    // 5. Verify both prompt and response are captured
    assertThat(messages).hasSize(2);
    
    ConversationMessage promptMessage = messages.get(0);
    assertThat(promptMessage.getMessageType()).isEqualTo(MessageType.PROMPT);
    assertThat(promptMessage.getContent()).isEqualTo(promptContent);
    
    ConversationMessage responseMessage = messages.get(1);
    assertThat(responseMessage.getMessageType()).isEqualTo(MessageType.RESPONSE);
    assertThat(responseMessage.getContent()).isEqualTo(responseContent);
    
    // 6. Verify conversation can be fully recovered
    String recoveredConversation = reconstructConversation(messages);
    assertThat(recoveredConversation).contains(promptContent);
    assertThat(recoveredConversation).contains(responseContent);
    
    // 7. Verify UI can display the conversation
    String conversationHtml = renderConversationInUI(session.getId());
    assertThat(conversationHtml).contains("👤"); // User avatar
    assertThat(conversationHtml).contains("🤖"); // Bot avatar
    assertThat(conversationHtml).contains(promptContent);
    assertThat(conversationHtml).contains(responseContent);
}
```

---

## Service 4: Monitoring Service (Week 7)

### Purpose
Comprehensive system monitoring, alerting, and observability.

### Technology Stack
- **Metrics**: Prometheus for metrics collection
- **Visualization**: Grafana for dashboards
- **Alerting**: Alertmanager for notifications
- **Logging**: ELK Stack (Elasticsearch, Logstash, Kibana)
- **Tracing**: Jaeger for distributed tracing

### Core Components

#### A. Metrics Collection
**Purpose**: Gather system and business metrics

```yaml
# docker-compose.monitoring.yml
version: '3.8'
services:
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    volumes:
      - grafana_data:/var/lib/grafana
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ./monitoring/grafana/datasources:/etc/grafana/provisioning/datasources
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin123
```

#### B. Custom Metrics
**Purpose**: Business-specific metrics for CodePromptu

```java
@Component
public class CodePromptuMetrics {
    
    private final Counter promptsProcessed;
    private final Counter templatesGenerated;
    private final Gauge activeConversations;
    private final Timer embeddingGenerationTime;
    
    public CodePromptuMetrics(MeterRegistry meterRegistry) {
        this.promptsProcessed = Counter.builder("codepromptu.prompts.processed")
            .description("Total number of prompts processed")
            .tag("service", "processor")
            .register(meterRegistry);
            
        this.templatesGenerated = Counter.builder("codepromptu.templates.generated")
            .description("Total number of templates generated")
            .register(meterRegistry);
            
        this.activeConversations = Gauge.builder("codepromptu.conversations.active")
            .description("Number of active conversation sessions")
            .register(meterRegistry);
            
        this.embeddingGenerationTime = Timer.builder("codepromptu.embedding.generation.time")
            .description("Time taken to generate embeddings")
            .register(meterRegistry);
    }
    
    public void recordPromptProcessed(String provider) {
        promptsProcessed.increment(Tags.of("provider", provider));
    }
    
    public void recordTemplateGenerated() {
        templatesGenerated.increment();
    }
    
    public void updateActiveConversations(int count) {
        activeConversations.set(count);
    }
    
    public Timer.Sample startEmbeddingTimer() {
        return Timer.start(embeddingGenerationTime);
    }
}
```

#### C. Health Checks
**Purpose**: Comprehensive system health monitoring

```java
@Component
public class SystemHealthIndicator implements HealthIndicator {
    
    private final DatabaseHealthChecker databaseChecker;
    private final RedisHealthChecker redisChecker;
    private final EmbeddingServiceHealthChecker embeddingChecker;
    
    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        
        // Check database connectivity
        if (!databaseChecker.isHealthy()) {
            return builder.down()
                .withDetail("database", "Connection failed")
                .build();
        }
        
        // Check Redis connectivity
        if (!redisChecker.isHealthy()) {
            builder.withDetail("redis", "Connection issues");
        }
        
        // Check embedding service
        if (!embeddingChecker.isHealthy()) {
            builder.withDetail("embedding", "Service unavailable");
        }
        
        return builder.up()
            .withDetail("timestamp", Instant.now())
            .withDetail("version", getClass().getPackage().getImplementationVersion())
            .build();
    }
}
```

---

## Integration & Testing Strategy (Week 8)

### End-to-End Testing
**Purpose**: Validate complete system functionality

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CodePromptuIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg15");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine");
    
    @Test
    void shouldCompleteFullWorkflow() {
        // 1. Capture prompt via gateway
        String promptContent = "How to implement REST API in Java?";
        capturePromptViaGateway(promptContent);
        
        // 2. Verify prompt stored in API service
        Prompt storedPrompt = verifyPromptStored(promptContent);
        
        // 3. Wait for background processing
        await().atMost(30, SECONDS)
            .until(() -> hasEmbedding(storedPrompt.getId()));
        
        // 4. Verify template induction
        await().atMost(60, SECONDS)
            .until(() -> hasGeneratedTemplate(storedPrompt));
        
        // 5. Verify analytics generation
        AnalyticsReport report = getLatestAnalyticsReport();
        assertThat(report.getMetrics().getTotalPrompts()).isGreaterThan(0);
        
        // 6. Verify UI can display data
        String dashboardResponse = restTemplate.getForObject("/ui/dashboard", String.class);
        assertThat(dashboardResponse).contains("Total Prompts");
    }
}
```

### Performance Testing
**Purpose**: Validate system performance under load

```java
@Test
void shouldHandleHighThroughput() {
    // Simulate 1000 concurrent prompt captures
    List<CompletableFuture<Void>> futures = IntStream.range(0, 1000)
        .mapToObj(i -> CompletableFuture.runAsync(() -> {
            capturePrompt("Test prompt " + i);
        }))
        .collect(Collectors.toList());
    
    // Wait for all to complete
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .join();
    
    // Verify all prompts processed within acceptable time
    await().atMost(5, MINUTES)
        .until(() -> getProcessedPromptCount() >= 1000);
}
```

---

## Deployment Strategy

### Docker Compose Updates
**Purpose**: Add new services to deployment stack

```yaml
# docker-compose.yml additions
services:
  processor:
    build: ./processor
    ports:
      - "8082:8082"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - CONFIG_SERVER_URI=http://config:8888
    depends_on:
      - database
      - cache
      - config
      - api

  worker:
    build: ./worker
    ports:
      - "8084:8084"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - CONFIG_SERVER_URI=http://config:8888
    depends_on:
      - database
      - cache
      - config

  ui:
    build: ./ui
    ports:
      - "3000:3000"
    environment:
      - REACT_APP_API_URL=http://localhost:8080
    depends_on:
      - gateway

  monitoring:
    build: ./monitoring
    ports:
      - "9090:9090"  # Prometheus
      - "3001:3000"  # Grafana
    volumes:
      - ./monitoring/config:/etc/monitoring
    depends_on:
      - prometheus
      - grafana
```

---

## Success Criteria

### Functional Requirements
- ✅ Template induction generates meaningful patterns from prompt clusters
- ✅ Conversation tracking groups related prompts accurately
- ✅ Background jobs process without blocking main operations
- ✅ UI provides intuitive prompt management and analytics
- ✅ Monitoring captures all critical system metrics
- ✅ End-to-end workflow completes successfully

### Performance Requirements
- ✅ Template induction processes 1000 prompts within 5 minutes
- ✅ UI loads dashboard within 2 seconds
- ✅ Background jobs don't impact API response times
- ✅ System handles 100 concurrent users
- ✅ Analytics generation completes within 1 hour

### Quality Requirements
- ✅ Test coverage >85% for all new services
- ✅ All services pass integration tests
- ✅ UI passes accessibility standards (WCAG 2.1)
- ✅ Monitoring alerts trigger correctly
- ✅ Documentation complete for all APIs

---

## Risk Mitigation

### Technical Risks
1. **Template Induction Accuracy**: Risk of generating poor-quality templates
   - **Mitigation**: Implement confidence scoring and manual review workflow

2. **UI Performance**: Risk of slow dashboard loading with large datasets
   - **Mitigation**: Implement pagination, caching, and lazy loading

3. **Background Job Failures**: Risk of job queue backup
   - **Mitigation**: Implement retry logic, dead letter queues, and monitoring

### Resource Risks
1. **Development Timeline**: Risk of 8-week timeline being too aggressive
   - **Mitigation**: Prioritize core features, implement MVP first

2. **Team Capacity**: Risk of insufficient frontend expertise
   - **Mitigation**: Provide React training, use component libraries

---

## Post-Sprint 4 Roadmap

### Phase 2 Enhancements (Future Sprints)
- **Advanced Analytics**: ML-based insights and predictions
- **Multi-tenant Support**: Organization and team management
- **API Versioning**: Backward compatibility and migration tools
- **Enterprise Security**: RBAC, audit logging, compliance features
- **Performance Optimization**: Caching layers, query optimization
- **Mobile App**: React Native mobile interface

This comprehensive plan provides a clear roadmap for completing the CodePromptu platform with advanced features while maintaining the high quality and testing standards established in previous sprints.
