package com.guesschess.application;

import com.guesschess.domain.game.GameId;

public record CreatedGame(GameId gameId, PlayerToken whiteToken, PlayerToken blackToken) {
}
