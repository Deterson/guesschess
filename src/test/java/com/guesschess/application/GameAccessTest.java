package com.guesschess.application;

import com.guesschess.domain.account.AnonymousId;
import com.guesschess.domain.account.UserId;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.piece.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifie l'immutabilite du lien couleur -> joueur (etape 6 de la roadmap) :
 * withPlayerLinked ne pose le lien qu'une seule fois, "premier arrive, premier lie".
 */
class GameAccessTest {

    private final GameAccess fresh = new GameAccess(GameId.random(), PlayerToken.random(), PlayerToken.random());

    @Test
    void freshAccessHasNoLinkedPlayers() {
        assertNull(fresh.playerOf(Color.WHITE));
        assertNull(fresh.playerOf(Color.BLACK));
    }

    @Test
    void linkingAColorSetsItsPlayerAndLeavesTheOtherColorUntouched() {
        PlayerRef whiteRef = new PlayerRef.Account(UserId.random());

        GameAccess linked = fresh.withPlayerLinked(Color.WHITE, whiteRef);

        assertEquals(whiteRef, linked.playerOf(Color.WHITE));
        assertNull(linked.playerOf(Color.BLACK));
    }

    @Test
    void linkingAnAlreadyLinkedColorAgainIsANoOp() {
        PlayerRef firstRef = new PlayerRef.Anonymous(AnonymousId.random());
        PlayerRef secondRef = new PlayerRef.Account(UserId.random());

        GameAccess linkedOnce = fresh.withPlayerLinked(Color.WHITE, firstRef);
        GameAccess linkedTwice = linkedOnce.withPlayerLinked(Color.WHITE, secondRef);

        assertEquals(firstRef, linkedTwice.playerOf(Color.WHITE));
    }

    @Test
    void linkingAnAlreadyLinkedColorWithTheSameRefReturnsTheSameInstance() {
        PlayerRef ref = new PlayerRef.Anonymous(AnonymousId.random());
        GameAccess linkedOnce = fresh.withPlayerLinked(Color.WHITE, ref);

        GameAccess linkedAgain = linkedOnce.withPlayerLinked(Color.WHITE, ref);

        assertSame(linkedOnce, linkedAgain);
    }

    @Test
    void isFullIsFalseUntilBothColorsAreLinked() {
        GameAccess onlyWhiteLinked = fresh.withPlayerLinked(Color.WHITE, new PlayerRef.Account(UserId.random()));

        assertFalse(fresh.isFull());
        assertFalse(onlyWhiteLinked.isFull());
    }

    @Test
    void isFullIsTrueOnceBothColorsAreLinked() {
        GameAccess bothLinked = fresh
                .withPlayerLinked(Color.WHITE, new PlayerRef.Account(UserId.random()))
                .withPlayerLinked(Color.BLACK, new PlayerRef.Anonymous(AnonymousId.random()));

        assertTrue(bothLinked.isFull());
    }
}
