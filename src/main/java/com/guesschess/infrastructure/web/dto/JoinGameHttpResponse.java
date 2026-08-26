package com.guesschess.infrastructure.web.dto;

public record JoinGameHttpResponse(String gameId, String color, String token) {
}
