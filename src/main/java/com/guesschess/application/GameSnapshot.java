package com.guesschess.application;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameResult;
import com.guesschess.domain.game.GameStatus;
import com.guesschess.domain.game.GameVariant;
import com.guesschess.domain.game.RoundResult;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;

import java.util.List;

/**
 * Photo immuable de l'etat public d'une partie, prise a l'interieur d'un acces
 * synchronise a l'agregat (GameRepository.withGame). Ne contient jamais la devinette
 * en attente : c'est precisement ce qui doit rester cache tant que le round n'est
 * pas resolu (anti-triche). lastRoundResult n'est renseigne qu'apres resolution,
 * moment ou reveler la devinette jouee est le mecanisme voulu, pas une fuite.
 * legalMoves ne concerne que le joueur au trait (sideToMove) : les reveler ne fuite
 * jamais la devinette ou le coup en attente.
 */
public record GameSnapshot(
        GameId id,
        GameVariant variant,
        Board board,
        Color sideToMove,
        GameStatus status,
        GameResult result,
        RoundResult lastRoundResult,
        List<Move> legalMoves,
        List<Move> moveHistory
) {

    public static GameSnapshot of(Game game) {
        return new GameSnapshot(
                game.id(),
                game.variant(),
                game.board(),
                game.sideToMove(),
                game.status(),
                game.result(),
                game.lastRoundResult(),
                game.legalMoves(),
                game.moveHistory()
        );
    }
}
