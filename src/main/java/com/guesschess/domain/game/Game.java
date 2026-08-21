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
import java.util.Optional;

/**
 * Aggregate root : encapsule le plateau, l'historique des coups et le resultat d'une
 * partie, et implemente la regle de devinette (etape 2 de la roadmap).
 *
 * Un round attend obligatoirement les deux soumissions - le coup reel du joueur au
 * trait (submitMove) et la devinette de son adversaire (submitGuess, qui peut valoir
 * "pas de devinette") - avant de se resoudre, quel que soit l'ordre d'arrivee.
 * submitMove reste definitif une fois appele pour un round (pas de correction) ;
 * submitGuess reste modifiable tant qu'elle n'a pas ete soumise pour ce round. Le
 * round se resout des que la seconde des deux soumissions arrive : devinette
 * correcte -> coup annule, le trait passe au devineur sans qu'aucune piece ne bouge ;
 * devinette fausse ou absente -> coup joue normalement.
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

    private final GameId id;
    private Board board;
    private final List<Board> positionHistory = new ArrayList<>();
    private final List<Move> moveHistory = new ArrayList<>();
    private GameStatus status = GameStatus.ONGOING;
    private GameResult result;
    private Move pendingMove;
    private boolean guessSubmitted;
    private Move pendingGuess;
    private RoundResult lastRoundResult;

    private Game(GameId id, Board initialBoard) {
        this.id = id;
        this.board = initialBoard;
        this.positionHistory.add(initialBoard);
    }

    public static Game newGame() {
        return newGame(GameId.random());
    }

    public static Game newGame(GameId id) {
        return new Game(id, Board.initial());
    }

    public static Game fromPosition(Board board) {
        return fromPosition(GameId.random(), board);
    }

    public static Game fromPosition(GameId id, Board board) {
        return new Game(id, board);
    }

    public GameId id() {
        return id;
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
     * Soumission (ou modification) de la devinette par l'adversaire du joueur au
     * trait. Rappelable librement tant qu'elle n'a pas deja resolu ce round (donc
     * tant que le coup reel n'est pas aussi arrive). guess null signifie
     * explicitement "pas de devinette" - utile plus tard pour un timeout - et
     * compte comme une soumission a part entiere : le round ne se resoudra pas tant
     * que submitGuess n'a pas ete appelee au moins une fois, meme avec null.
     *
     * @return le resultat du round si cette soumission completait la paire, vide si
     * le coup reel n'est pas encore arrive
     */
    public Optional<RoundResult> submitGuess(Move guess) {
        requireOngoing();
        if (guess != null && !MoveGenerator.isLegalMove(board, sideToMove(), guess)) {
            throw new IllegalArgumentException("guess must be a legal move for " + sideToMove() + ": " + guess);
        }
        this.guessSubmitted = true;
        this.pendingGuess = guess;
        return pendingMove != null ? Optional.of(resolveRound()) : Optional.empty();
    }

    /**
     * Soumission du coup reel par le joueur au trait, definitive pour ce round (pas
     * de nouvel appel avant resolution). Le round ne se resout que si la devinette
     * de l'adversaire est deja arrivee ; sinon ce coup reste en attente jusqu'a ce
     * qu'elle arrive.
     *
     * @return le resultat du round si la devinette etait deja arrivee, vide si le
     * round doit encore attendre la devinette
     */
    public Optional<RoundResult> submitMove(Move actualMove) {
        requireOngoing();
        if (pendingMove != null) {
            throw new IllegalStateException("a move has already been submitted for this round");
        }
        if (!MoveGenerator.isLegalMove(board, sideToMove(), actualMove)) {
            throw new IllegalArgumentException("illegal move: " + actualMove);
        }
        this.pendingMove = actualMove;
        return guessSubmitted ? Optional.of(resolveRound()) : Optional.empty();
    }

    private RoundResult resolveRound() {
        Move actualMove = this.pendingMove;
        Move guess = this.pendingGuess;
        Color mover = sideToMove();
        Color guesser = mover.opposite();
        boolean guessedCorrectly = guess != null && guess.equals(actualMove);

        this.pendingMove = null;
        this.guessSubmitted = false;
        this.pendingGuess = null;

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
