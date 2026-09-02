package com.guesschess.infrastructure.web.dto;

public record GamePlayersHttpResponse(PlayerInfoHttpResponse white, PlayerInfoHttpResponse black) {
}
