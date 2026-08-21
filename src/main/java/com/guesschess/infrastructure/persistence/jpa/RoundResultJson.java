package com.guesschess.infrastructure.persistence.jpa;

record RoundResultJson(String mover, String guesser, MoveJson actualMove, MoveJson guessedMove, boolean movePlayed) {
}
