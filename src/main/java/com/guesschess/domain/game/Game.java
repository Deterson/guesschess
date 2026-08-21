package com.guesschess.domain.game;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.PieceType;
import com.guesschess.domain.rules.CheckDetector;
import com.guesschess.domain.rules.MaterialEvaluator;
import com.guesschess.domain.rules.MoveGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root : encapsule le plateau, l'historique des coups et le resultat d'une
 * partie, et implemente la regle de devinette (etape 2 de la roadmap).
 *
 * A chaque round, l'adversaire du joueur au trait peut soumettre une devinette
 * (submitGuess), modifiable librement tant que le joueur au trait n'a pas soumis son
 * coup reel (submitMove). Des que le coup reel arrive, le round se resout
 * immediatement : devinette correcte -> coup annule, le trait passe au devineur sans
 * qu'aucune piece ne bouge ; devinette fausse ou absente -> coup joue normalement.
 *
 * Cas particulier : si le coup annule etait la parade a un echec, le roi reste en
 * echec et le trait passe quand meme au devineur, qui a alors normalement acces au
 * coup capturant ce roi parmi ses coups legaux. C'est un coup comme un autre : rien
 * n'oblige le devineur a le jouer, et lui-meme peut se faire deviner. Si personne ne
 * capture jamais, la regle de repetition finit par forcer la nulle.
 */
public final class Game {

    private static final int FIFTY_MOVE_HALFMOVE_LIMIT = 100;
    private static final int REPETITION_LIMIT = 3;

    private Board board;
    private final List<Board> positionHistory = new ArrayList<>();
    private final List<Move> moveHistory = new ArrayList<>();
    private GameStatus status = GameStatus.ONGOING;
    private GameResult result;
    private Move pendingGuess;
    private RoundResult lastRoundResult;

    private Game(Board initialBoard) {
        this.board = initialBoard;
        this.positionHistory.add(initialBoard);
    }

    public static Game newGame() {
        return new Game(Board.initial());
    }

    public static Game fromPosition(Board board) {
        return new Game(board);
    }

    public Board board() {
        return board;
    }

    public Color sideToMove() {
        return board.sideToMove();
    }

    public GameStatus status() {
        return status;
    }

    public GameResult result() {
        return result;
    }

    public List<Move> moveHistory() {
        return Collections.unmodifiableList(moveHistory);
    }

    public RoundResult lastRoundResult() {
        return lastRoundResult;
    }

    /**
     * Devinette actuellement enregistree pour le round en cours, null si aucune
     * (pas encore soumise, ou remise a zero apres resolution du round precedent).
     */
    public Move pendingGuess() {
        return pendingGuess;
    }

    public boolean isInCheck() {
        return isInCheck(sideToMove());
    }

    public boolean isInCheck(Color color) {
        if (status != GameStatus.ONGOING) {
            return false;
        }
        return CheckDetector.isInCheck(board, color);
    }

    public List<Move> legalMoves() {
        if (status != GameStatus.ONGOING) {
            return List.of();
        }
        return MoveGenerator.generateLegalMoves(board, sideToMove());
    }

    /**
     * Soumission (ou modification) de la devinette par l'adversaire du joueur au trait.
     * Rappelable librement tant que submitMove n'a pas ete appele pour ce round : le
     * dernier appel ecrase le precedent. guess null signifie "pas de devinette".
     */
    public void submitGuess(Move guess) {
        requireOngoing();
        if (guess != null && !MoveGenerator.isLegalMove(board, sideToMove(), guess)) {
            throw new IllegalArgumentException("guess must be a legal move for " + sideToMove() + ": " + guess);
        }
        this.pendingGuess = guess;
    }

    /**
     * Soumission du coup reel par le joueur au trait : resout immediatement le round
     * avec la devinette actuellement enregistree (ou son absence), puis l'efface pour
     * le round suivant.
     */
    public RoundResult submitMove(Move actualMove) {
        requireOngoing();
        Color mover = sideToMove();
        Color guesser = mover.opposite();
        if (!MoveGenerator.isLegalMove(board, mover, actualMove)) {
            throw new IllegalArgumentException("illegal move: " + actualMove);
        }

        Move guess = this.pendingGuess;
        this.pendingGuess = null;
        boolean guessedCorrectly = guess != null && guess.equals(actualMove);

        RoundResult roundResult;
        if (guessedCorrectly) {
            cancelRound();
            roundResult = RoundResult.cancelled(mover, guesser, actualMove, guess);
        } else {
            applyRealMove(actualMove);
            roundResult = RoundResult.played(mover, guesser, actualMove, guess);
        }
        this.lastRoundResult = roundResult;
        return roundResult;
    }

    private void applyRealMove(Move move) {
        board = board.applyMove(move);
        moveHistory.add(move);
        positionHistory.add(board);

        if (move.isCapture() && move.capturedPiece().type() == PieceType.KING) {
            finish(GameResult.win(move.movedPiece().color(), GameResultCause.KING_CAPTURED));
            return;
        }
        resolveGameEnd();
    }

    private void cancelRound() {
        board = board.pass();
        positionHistory.add(board);
        resolveGameEnd();
    }

    private void resolveGameEnd() {
        Color nextToMove = board.sideToMove();
        boolean nextHasLegalMoves = MoveGenerator.hasAnyLegalMove(board, nextToMove);

        if (!nextHasLegalMoves) {
            if (CheckDetector.isInCheck(board, nextToMove)) {
                finish(GameResult.win(nextToMove.opposite(), GameResultCause.CHECKMATE));
            } else {
                finish(GameResult.draw(GameResultCause.STALEMATE));
            }
            return;
        }
        if (board.halfmoveClock() >= FIFTY_MOVE_HALFMOVE_LIMIT) {
            finish(GameResult.draw(GameResultCause.DRAW_FIFTY_MOVE_RULE));
            return;
        }
        if (countOccurrences(board) >= REPETITION_LIMIT) {
            finish(GameResult.draw(GameResultCause.DRAW_THREEFOLD_REPETITION));
            return;
        }
        if (MaterialEvaluator.isInsufficientMaterial(board)) {
            finish(GameResult.draw(GameResultCause.DRAW_INSUFFICIENT_MATERIAL));
        }
    }

    private void requireOngoing() {
        if (status != GameStatus.ONGOING) {
            throw new IllegalStateException("game is already finished: " + result);
        }
    }

    private void finish(GameResult gameResult) {
        this.status = GameStatus.FINISHED;
        this.result = gameResult;
    }

    private int countOccurrences(Board position) {
        int count = 0;
        for (Board past : positionHistory) {
            if (past.isSamePosition(position)) {
                count++;
            }
        }
        return count;
    }
}
