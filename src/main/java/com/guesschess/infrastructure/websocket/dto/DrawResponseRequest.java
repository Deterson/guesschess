package com.guesschess.infrastructure.websocket.dto;

public record DrawResponseRequest(String token, boolean accept) {
}
