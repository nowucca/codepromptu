package com.codepromptu.gateway.model;

public enum LLMProvider {
    OPENAI("OpenAI", "https://api.openai.com"),
    ANTHROPIC("Anthropic", "https://api.anthropic.com"),
    GOOGLE_AI("Google AI", "https://generativelanguage.googleapis.com"),
    UNKNOWN("Unknown", "");
    
    private final String displayName;
    private final String baseUrl;
    
    LLMProvider(String displayName, String baseUrl) {
        this.displayName = displayName;
        this.baseUrl = baseUrl;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getBaseUrl() {
        return baseUrl;
    }
}
