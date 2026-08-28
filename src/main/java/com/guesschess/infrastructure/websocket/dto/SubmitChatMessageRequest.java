package com.guesschess.infrastructure.websocket.dto;

public record SubmitChatMessageRequest(String token, String text) {
}
