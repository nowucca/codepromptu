package com.codepromptu.gateway.filter;

import com.codepromptu.gateway.model.LLMProvider;
import com.codepromptu.gateway.service.LLMProviderDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * Gateway filter for securely passing API keys to LLM providers.
 * Extracts API keys from client requests, validates them, and forwards
 * them to the appropriate LLM provider with proper headers.
 */
@Component
public class ApiKeyPassThroughFilter extends AbstractGatewayFilterFactory<ApiKeyPassThroughFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyPassThroughFilter.class);
    
    private final LLMProviderDetector providerDetector;

    public ApiKeyPassThroughFilter(LLMProviderDetector providerDetector) {
        super(Config.class);
        this.providerDetector = providerDetector;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return new ApiKeyPassThroughGatewayFilter(config);
    }

    public class ApiKeyPassThroughGatewayFilter implements GatewayFilter, Ordered {
        
        private final Config config;

        public ApiKeyPassThroughGatewayFilter(Config config) {
            this.config = config;
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            // Detect the LLM provider
            LLMProvider provider = providerDetector.detectProvider(exchange);
            
            if (provider == LLMProvider.UNKNOWN) {
                log.warn("Unknown LLM provider for request: {}", exchange.getRequest().getPath().value());
                return handleUnknownProvider(exchange);
            }

            // Extract API key
            String apiKey = providerDetector.extractApiKey(exchange, provider);
            if (!StringUtils.hasText(apiKey)) {
                log.warn("Missing API key for provider: {} on path: {}", 
                    provider, exchange.getRequest().getPath().value());
                return handleMissingApiKey(exchange, provider);
            }

            // Validate API key format
            if (!providerDetector.isValidApiKeyFormat(apiKey, provider)) {
                log.warn("Invalid API key format for provider: {} (key hash: {})", 
                    provider, hashApiKey(apiKey));
                return handleInvalidApiKey(exchange, provider);
            }

            // Store hashed API key for logging/monitoring
            String hashedApiKey = hashApiKey(apiKey);
            exchange.getAttributes().put("api-key-hash", hashedApiKey);
            exchange.getAttributes().put("llm-provider", provider);

            log.debug("Processing request for provider: {} with API key hash: {}", provider, hashedApiKey);

            // Configure headers for the provider
            ServerHttpRequest modifiedRequest = configureProviderHeaders(
                exchange.getRequest(), provider, apiKey);

            ServerWebExchange modifiedExchange = exchange.mutate()
                .request(modifiedRequest)
                .build();

            return chain.filter(modifiedExchange);
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE + 100; // Run after PromptCaptureFilter
        }

        /**
         * Configures headers specific to each LLM provider.
         */
        private ServerHttpRequest configureProviderHeaders(ServerHttpRequest request, 
                                                          LLMProvider provider, 
                                                          String apiKey) {
            ServerHttpRequest.Builder builder = request.mutate();

            switch (provider) {
                case OPENAI:
                    builder.header("Authorization", "Bearer " + apiKey);
                    builder.header("Content-Type", "application/json");
                    break;

                case ANTHROPIC:
                    builder.header("x-api-key", apiKey);
                    builder.header("anthropic-version", "2023-06-01");
                    builder.header("Content-Type", "application/json");
                    break;

                case GOOGLE_AI:
                    // Google AI can use header or query param, prefer header
                    builder.header("x-goog-api-key", apiKey);
                    builder.header("Content-Type", "application/json");
                    break;

                default:
                    log.warn("Unknown provider configuration: {}", provider);
                    break;
            }

            // Add any additional required headers
            Map<String, String> requiredHeaders = providerDetector.getRequiredHeaders(provider);
            requiredHeaders.forEach(builder::header);

            // Add security headers
            builder.header("User-Agent", "CodePromptu-Gateway/1.0");
            
            return builder.build();
        }

        /**
         * Handles requests with unknown providers.
         */
        private Mono<Void> handleUnknownProvider(ServerWebExchange exchange) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            response.getHeaders().add("Content-Type", "application/json");
            
            String errorBody = """
                {
                    "error": {
                        "message": "Unknown LLM provider. Supported providers: OpenAI, Anthropic, Google AI",
                        "type": "invalid_request_error",
                        "code": "unknown_provider"
                    }
                }
                """;
            
            return response.writeWith(Mono.just(
                response.bufferFactory().wrap(errorBody.getBytes(StandardCharsets.UTF_8))
            ));
        }

        /**
         * Handles requests with missing API keys.
         */
        private Mono<Void> handleMissingApiKey(ServerWebExchange exchange, LLMProvider provider) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().add("Content-Type", "application/json");
            
            String headerName = getExpectedHeaderName(provider);
            String errorBody = String.format("""
                {
                    "error": {
                        "message": "Missing API key. Please provide a valid API key in the %s header.",
                        "type": "authentication_error",
                        "code": "missing_api_key",
                        "provider": "%s"
                    }
                }
                """, headerName, provider.name().toLowerCase());
            
            return response.writeWith(Mono.just(
                response.bufferFactory().wrap(errorBody.getBytes(StandardCharsets.UTF_8))
            ));
        }

        /**
         * Handles requests with invalid API key formats.
         */
        private Mono<Void> handleInvalidApiKey(ServerWebExchange exchange, LLMProvider provider) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().add("Content-Type", "application/json");
            
            String errorBody = String.format("""
                {
                    "error": {
                        "message": "Invalid API key format for %s provider.",
                        "type": "authentication_error",
                        "code": "invalid_api_key_format",
                        "provider": "%s"
                    }
                }
                """, provider.name(), provider.name().toLowerCase());
            
            return response.writeWith(Mono.just(
                response.bufferFactory().wrap(errorBody.getBytes(StandardCharsets.UTF_8))
            ));
        }

        /**
         * Gets the expected header name for the provider.
         */
        private String getExpectedHeaderName(LLMProvider provider) {
            switch (provider) {
                case OPENAI:
                    return "Authorization (Bearer token)";
                case ANTHROPIC:
                    return "x-api-key";
                case GOOGLE_AI:
                    return "x-goog-api-key";
                default:
                    return "Authorization";
            }
        }
    }

    /**
     * Hashes API key for secure logging.
     */
    private String hashApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "null";
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            
            // Convert to hex string (first 8 characters for logging)
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < Math.min(4, hash.length); i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            return "hash-error";
        }
    }

    /**
     * Configuration class for the filter.
     */
    public static class Config {
        private boolean validateApiKeys = true;
        private boolean logApiKeyHashes = true;
        private int maxApiKeyLength = 200;

        public boolean isValidateApiKeys() {
            return validateApiKeys;
        }

        public void setValidateApiKeys(boolean validateApiKeys) {
            this.validateApiKeys = validateApiKeys;
        }

        public boolean isLogApiKeyHashes() {
            return logApiKeyHashes;
        }

        public void setLogApiKeyHashes(boolean logApiKeyHashes) {
            this.logApiKeyHashes = logApiKeyHashes;
        }

        public int getMaxApiKeyLength() {
            return maxApiKeyLength;
        }

        public void setMaxApiKeyLength(int maxApiKeyLength) {
            this.maxApiKeyLength = maxApiKeyLength;
        }
    }
}
