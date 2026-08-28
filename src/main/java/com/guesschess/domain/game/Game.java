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
 * Cas particulier (variante GUESSMATE, la regle de base - voir GameVariant) : si le
 * coup annule etait la parade a un echec, le roi reste en echec et le trait passe
 * quand meme au devineur, qui a alors normalement acces au coup capturant ce roi
 * parmi ses coups legaux. C'est un coup comme un autre : rien n'oblige le devineur a
 * le jouer, et lui-meme peut se faire deviner. Si personne ne capture jamais, la
 * regle de repetition finit par forcer la nulle.
 *
 * Variante GUESSMATE : dans ce meme cas particulier (devinette correcte du coup qui
 * parait un echec), la partie se termine immediatement, victoire du devineur, plutot
 * que de simplement annuler le coup et attendre une capture ulterieure du roi.
 */
public final class Game {

    private static final int FIFTY_MOVE_HALFMOVE_LIMIT = 100;
    private static final int REPETITION_LIMIT = 3;
    private static final int GUESS_REPETITION_LIMIT_PER_SIDE = 3;

    /**
     * Origine d'une entree de positionHistory : MOVE pour un coup reellement joue
     * (y compris la position initiale), GUESS pour un round annule par une devinette
     * correcte (le plateau ne bouge pas, seul le trait passe - voir Game.cancelRound).
     * Seules les entrees MOVE comptent pour la nulle par triple repetition (voir
     * countOccurrences) ; la regle "three guess repetition" est trackee separement
     * (voir whiteGuessedMove/blackGuessedMove ci-dessous), car elle porte sur l'identite
     * du coup devine, pas seulement sur la position. Public (comme Memento) pour rester
     * lisible depuis la persistance JPA, seul autre endroit qui manipule l'historique.
     */
    public enum PositionOrigin {
        MOVE,
        GUESS
    }

    public record PositionRecord(Board board, PositionOrigin origin) {
    }

    private final GameId id;
    private final GameVariant variant;
    private Board board;
    private final List<PositionRecord> positionHistory = new ArrayList<>();
    private final List<Move> moveHistory = new ArrayList<>();
    private GameStatus status = GameStatus.ONGOING;
    private GameResult result;
    private Move pendingMove;
    private boolean guessSubmitted;
    private Move pendingGuess;
    private RoundResult lastRoundResult;

    /**
     * Suivi de la regle de "three guess repetition" : pour chaque couleur, le dernier
     * coup devine correctement quand elle etait au trait, et le nombre de fois de suite
     * (sans coup reellement joue entre-temps) que ce meme coup a ete devine. La nulle
     * est declaree quand les deux compteurs atteignent GUESS_REPETITION_LIMIT_PER_SIDE
     * simultanement - donc un meme coup repete 3 fois cote blancs ET un meme coup
     * (eventuellement different) repete 3 fois cote noirs, dans un enchainement continu
     * de devinettes correctes. Un coup reellement joue reinitialise les deux compteurs
     * (voir resetGuessedMoveStreaks) ; un coup devine qui differe du precedent pour
     * une couleur ne reinitialise que le compteur de cette couleur-la.
     */
    private Move whiteGuessedMove;
    private int whiteGuessedMoveStreak;
    private Move blackGuessedMove;
    private int blackGuessedMoveStreak;

    private Game(GameId id, Board initialBoard, GameVariant variant) {
        this.id = id;
        this.board = initialBoard;
        this.variant = variant;
        this.positionHistory.add(new PositionRecord(initialBoard, PositionOrigin.MOVE));
    }

    private Game(GameId id, GameVariant variant, Board board, List<PositionRecord> positionHistory, List<Move> moveHistory,
                 GameStatus status, GameResult result, Move pendingMove, boolean guessSubmitted,
                 Move pendingGuess, RoundResult lastRoundResult, Move whiteGuessedMove, int whiteGuessedMoveStreak,
                 Move blackGuessedMove, int blackGuessedMoveStreak) {
        this.id = id;
        this.variant = variant;
        this.board = board;
        this.positionHistory.addAll(positionHistory);
        this.moveHistory.addAll(moveHistory);
        this.status = status;
        this.result = result;
        this.pendingMove = pendingMove;
        this.guessSubmitted = guessSubmitted;
        this.pendingGuess = pendingGuess;
        this.lastRoundResult = lastRoundResult;
        this.whiteGuessedMove = whiteGuessedMove;
        this.whiteGuessedMoveStreak = whiteGuessedMoveStreak;
        this.blackGuessedMove = blackGuessedMove;
        this.blackGuessedMoveStreak = blackGuessedMoveStreak;
    }

    public static Game newGame() {
        return newGame(GameId.random(), GameVariant.REGULAR);
    }

    public static Game newGame(GameVariant variant) {
        return newGame(GameId.random(), variant);
    }

    public static Game newGame(GameId id) {
        return newGame(id, GameVariant.REGULAR);
    }

    public static Game newGame(GameId id, GameVariant variant) {
        return new Game(id, Board.initial(), variant);
    }

    public static Game fromPosition(Board board) {
        return fromPosition(GameId.random(), board, GameVariant.REGULAR);
    }

    public static Game fromPosition(Board board, GameVariant variant) {
        return fromPosition(GameId.random(), board, variant);
    }

    public static Game fromPosition(GameId id, Board board) {
        return fromPosition(id, board, GameVariant.REGULAR);
    }

    public static Game fromPosition(GameId id, Board board, GameVariant variant) {
        return new Game(id, board, variant);
    }

    /**
     * Reconstruit une partie a l'identique depuis un memento (persistance uniquement -
     * ne pas utiliser dans le flux de jeu normal, qui passe par newGame/fromPosition).
     */
    public static Game fromMemento(Memento memento) {
        return new Game(memento.id(), memento.variant(), memento.board(), memento.positionHistory(), memento.moveHistory(),
                memento.status(), memento.result(), memento.pendingMove(), memento.guessSubmitted(),
                memento.pendingGuess(), memento.lastRoundResult(), memento.whiteGuessedMove(),
                memento.whiteGuessedMoveStreak(), memento.blackGuessedMove(), memento.blackGuessedMoveStreak());
    }

    public GameId id() {
        return id;
    }

    public GameVariant variant() {
        return variant;
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
     * Ce que color a deja soumis pour le round en cours (son propre coup reel si
     * color est au trait, sa propre devinette sinon) - reserve a informer CE joueur
     * de sa propre soumission en attente (typiquement apres un rechargement de page,
     * pour eviter qu'il ne retente une soumission que le serveur bloquerait). Ne
     * jamais utiliser pour renvoyer la soumission de l'AUTRE couleur : ce serait
     * exactement la fuite anti-triche que GameSnapshot evite deliberement.
     */
    public PendingSubmission mySubmission(Color color) {
        if (status != GameStatus.ONGOING) {
            return PendingSubmission.NONE;
        }
        if (color == sideToMove()) {
            return pendingMove == null ? PendingSubmission.NONE : PendingSubmission.of(pendingMove);
        }
        return guessSubmitted ? PendingSubmission.of(pendingGuess) : PendingSubmission.NONE;
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
        boolean moverWasInCheck = CheckDetector.isInCheck(board, mover);

        this.pendingMove = null;
        this.guessSubmitted = false;
        this.pendingGuess = null;

        RoundResult roundResult;
        if (guessedCorrectly) {
            if (variant == GameVariant.GUESSMATE && moverWasInCheck) {
                finish(GameResult.win(guesser, GameResultCause.CHECK_PARRY_GUESSED));
            } else {
                cancelRound(mover, actualMove);
            }
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
        positionHistory.add(new PositionRecord(board, PositionOrigin.MOVE));
        resetGuessedMoveStreaks();

        if (move.isCapture() && move.capturedPiece().type() == PieceType.KING) {
            finish(GameResult.win(move.movedPiece().color(), GameResultCause.KING_CAPTURED));
            return;
        }
        resolveGameEnd();
    }

    private void cancelRound(Color mover, Move guessedMove) {
        board = board.pass();
        positionHistory.add(new PositionRecord(board, PositionOrigin.GUESS));
        recordGuessedMove(mover, guessedMove);
        resolveGameEnd();
    }

    private void recordGuessedMove(Color mover, Move move) {
        switch (mover) {
            case WHITE -> {
                if (move.equals(whiteGuessedMove)) {
                    whiteGuessedMoveStreak++;
                } else {
                    whiteGuessedMove = move;
                    whiteGuessedMoveStreak = 1;
                }
            }
            case BLACK -> {
                if (move.equals(blackGuessedMove)) {
                    blackGuessedMoveStreak++;
                } else {
                    blackGuessedMove = move;
                    blackGuessedMoveStreak = 1;
                }
            }
        }
    }

    private void resetGuessedMoveStreaks() {
        whiteGuessedMove = null;
        whiteGuessedMoveStreak = 0;
        blackGuessedMove = null;
        blackGuessedMoveStreak = 0;
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
        if (whiteGuessedMoveStreak >= GUESS_REPETITION_LIMIT_PER_SIDE && blackGuessedMoveStreak >= GUESS_REPETITION_LIMIT_PER_SIDE) {
            finish(GameResult.draw(GameResultCause.DRAW_THREE_GUESS_REPETITION));
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
        for (PositionRecord past : positionHistory) {
            if (past.origin() == PositionOrigin.MOVE && past.board().isSamePosition(position)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Photo complete de l'etat interne, y compris le round en cours (persistance
     * uniquement - a la difference de GameSnapshot, cote application, qui cache
     * volontairement la devinette en attente).
     */
    public Memento toMemento() {
        return new Memento(id, variant, board, List.copyOf(positionHistory), List.copyOf(moveHistory),
                status, result, pendingMove, guessSubmitted, pendingGuess, lastRoundResult,
                whiteGuessedMove, whiteGuessedMoveStreak, blackGuessedMove, blackGuessedMoveStreak);
    }

    public record Memento(
            GameId id,
            GameVariant variant,
            Board board,
            List<PositionRecord> positionHistory,
            List<Move> moveHistory,
            GameStatus status,
            GameResult result,
            Move pendingMove,
            boolean guessSubmitted,
            Move pendingGuess,
            RoundResult lastRoundResult,
            Move whiteGuessedMove,
            int whiteGuessedMoveStreak,
            Move blackGuessedMove,
            int blackGuessedMoveStreak
    ) {
    }
}
