package com.guesschess.application;

import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameVariant;

public record CreatedGame(GameId gameId, PlayerToken whiteToken, PlayerToken blackToken, GameVariant variant) {
}
