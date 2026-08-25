package com.guesschess.infrastructure.websocket.dto;

public record CreateGameResponse(String gameId, String whiteToken, String blackToken, String variant) {
}
