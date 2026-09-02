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
 * submitMove et submitGuess restent tous deux librement modifiables (rappelables,
 * chaque nouvel appel ecrasant le precedent) tant que le round n'est pas resolu, donc
 * tant que l'autre moitie de la paire n'est pas arrivee - aucune limite de nombre de
 * changements. Cette possibilite de changement n'a de sens que hors contexte
 * chronometre (aucun controle du temps n'existe encore - etape 12 de la roadmap - donc
 * elle s'applique aujourd'hui a toutes les parties ; a restreindre aux seules parties
 * par correspondance sans timer une fois le controle du temps modelise). Le round se
 * resout des que la seconde des deux soumissions arrive : devinette correcte -> coup
 * annule, le trait passe au devineur sans qu'aucune piece ne bouge ; devinette fausse
 * ou absente -> coup joue normalement.
 *
 * Cas particulier (variante NOGUESSMATE, la regle de base - voir GameVariant) : si le
 * coup annule etait la parade a un echec, le roi reste en echec et le trait passe
 * quand meme au devineur, qui a alors normalement acces au coup capturant ce roi
 * parmi ses coups legaux. C'est un coup comme un autre : rien n'oblige le devineur a
 * le jouer, et lui-meme peut se faire deviner. Si personne ne capture jamais, la
 * regle de repetition finit par forcer la nulle.
 *
 * Variante GUESSCHESS (par defaut) : dans ce meme cas particulier (devinette correcte
 * du coup qui parait un echec), la partie se termine immediatement, victoire du
 * devineur, plutot que de simplement annuler le coup et attendre une capture
 * ulterieure du roi.
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
    private final List<RoundResult> roundHistory = new ArrayList<>();
    private GameStatus status = GameStatus.ONGOING;
    private GameResult result;
    private Move pendingMove;
    private boolean guessSubmitted;
    private Move pendingGuess;

    /**
     * Couleur ayant une offre de nulle en attente, ou null. Effacee des qu'un round se
     * resout (coup joue ou annule par une devinette - voir resolveRound) : une offre
     * non traitee vaut implicitement refus des que la partie avance d'un coup, plutot
     * que de rester active indefiniment.
     */
    private Color drawOfferedBy;

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

    private Game(GameId id, GameVariant variant, Board board, List<PositionRecord> positionHistory, List<RoundResult> roundHistory,
                 GameStatus status, GameResult result, Move pendingMove, boolean guessSubmitted,
                 Move pendingGuess, Move whiteGuessedMove, int whiteGuessedMoveStreak,
                 Move blackGuessedMove, int blackGuessedMoveStreak, Color drawOfferedBy) {
        this.id = id;
        this.variant = variant;
        this.board = board;
        this.positionHistory.addAll(positionHistory);
        this.roundHistory.addAll(roundHistory);
        this.status = status;
        this.result = result;
        this.pendingMove = pendingMove;
        this.guessSubmitted = guessSubmitted;
        this.pendingGuess = pendingGuess;
        this.whiteGuessedMove = whiteGuessedMove;
        this.whiteGuessedMoveStreak = whiteGuessedMoveStreak;
        this.blackGuessedMove = blackGuessedMove;
        this.blackGuessedMoveStreak = blackGuessedMoveStreak;
        this.drawOfferedBy = drawOfferedBy;
    }

    public static Game newGame() {
        return newGame(GameId.random(), GameVariant.GUESSCHESS);
    }

    public static Game newGame(GameVariant variant) {
        return newGame(GameId.random(), variant);
    }

    public static Game newGame(GameId id) {
        return newGame(id, GameVariant.GUESSCHESS);
    }

    public static Game newGame(GameId id, GameVariant variant) {
        return new Game(id, Board.initial(), variant);
    }

    public static Game fromPosition(Board board) {
        return fromPosition(GameId.random(), board, GameVariant.GUESSCHESS);
    }

    public static Game fromPosition(Board board, GameVariant variant) {
        return fromPosition(GameId.random(), board, variant);
    }

    public static Game fromPosition(GameId id, Board board) {
        return fromPosition(id, board, GameVariant.GUESSCHESS);
    }

    public static Game fromPosition(GameId id, Board board, GameVariant variant) {
        return new Game(id, board, variant);
    }

    /**
     * Reconstruit une partie a l'identique depuis un memento (persistance uniquement -
     * ne pas utiliser dans le flux de jeu normal, qui passe par newGame/fromPosition).
     */
    public static Game fromMemento(Memento memento) {
        return new Game(memento.id(), memento.variant(), memento.board(), memento.positionHistory(), memento.roundHistory(),
                memento.status(), memento.result(), memento.pendingMove(), memento.guessSubmitted(),
                memento.pendingGuess(), memento.whiteGuessedMove(),
                memento.whiteGuessedMoveStreak(), memento.blackGuessedMove(), memento.blackGuessedMoveStreak(),
                memento.drawOfferedBy());
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

    public Color drawOfferedBy() {
        return drawOfferedBy;
    }

    /**
     * Proposition de nulle par proposer, valable jusqu'a ce qu'elle soit acceptee,
     * refusee, ou implicitement caduque au round suivant (voir resolveRound). Rejetee
     * si une offre est deja en attente, quelle que soit la couleur qui l'a emise -
     * un seul appel de nulle actif a la fois.
     */
    public void offerDraw(Color proposer) {
        requireOngoing();
        if (drawOfferedBy != null) {
            throw new IllegalStateException("a draw offer is already pending");
        }
        this.drawOfferedBy = proposer;
    }

    /**
     * Reponse de responder a l'offre de nulle en attente - forcement l'autre couleur
     * que celle qui a propose (une couleur ne peut pas repondre a sa propre offre).
     * Acceptee -> partie terminee en nulle (DRAW_BY_AGREEMENT) ; refusee -> l'offre
     * est simplement levee, la partie continue normalement.
     */
    public void respondToDraw(Color responder, boolean accept) {
        requireOngoing();
        if (drawOfferedBy == null) {
            throw new IllegalStateException("no draw offer is pending");
        }
        if (drawOfferedBy == responder) {
            throw new IllegalStateException("cannot respond to your own draw offer");
        }
        this.drawOfferedBy = null;
        if (accept) {
            finish(GameResult.draw(GameResultCause.DRAW_BY_AGREEMENT));
        }
    }

    /**
     * Coups reellement joues, derive de roundHistory (les rounds annules n'y figurent
     * pas) plutot que stocke separement - une seule source de verite pour eviter une
     * resynchronisation manuelle entre deux historiques qui ne feraient que doubler
     * l'un l'autre.
     */
    public List<Move> moveHistory() {
        return roundHistory.stream().filter(RoundResult::movePlayed).map(RoundResult::actualMove).toList();
    }

    /**
     * Historique complet des rounds resolus (coup reel + devinette), un par round, y
     * compris les rounds annules par une devinette correcte - source de verite pour
     * reconstruire le PGGN (etape 10 de la roadmap).
     */
    public List<RoundResult> roundHistory() {
        return Collections.unmodifiableList(roundHistory);
    }

    public RoundResult lastRoundResult() {
        return roundHistory.isEmpty() ? null : roundHistory.get(roundHistory.size() - 1);
    }

    /**
     * Photo (avant/apres) de chaque coup reellement joue, pour la generation de
     * notation SAN qui a besoin du plateau juste avant le coup (desambiguisation) et
     * juste apres (suffixe echec/mat). positionHistory a toujours un element de plus
     * que roundHistory (la position initiale en tete), donc positionHistory.get(i) est
     * la position avant le round i et positionHistory.get(i + 1) la position apres.
     */
    public List<PlayedMove> playedMoveHistory() {
        List<PlayedMove> played = new ArrayList<>();
        for (int i = 0; i < roundHistory.size(); i++) {
            RoundResult round = roundHistory.get(i);
            if (round.movePlayed()) {
                played.add(new PlayedMove(positionHistory.get(i).board(), round.actualMove(), positionHistory.get(i + 1).board()));
            }
        }
        return played;
    }

    public record PlayedMove(Board boardBefore, Move move, Board boardAfter) {
    }

    /**
     * Chaque round de roundHistory associe a son plateau juste avant (toujours
     * disponible) et juste apres (nullable). boardAfter est absent dans le seul cas
     * ou aucune entree n'a ete ajoutee a positionHistory pour ce round : le round
     * terminal Guessmate (devinette correcte d'un coup qui parait un echec, partie
     * terminee immediatement sans jamais appeler applyRealMove ni cancelRound - voir
     * resolveRound). Utilise par le PGGN (etape 10 de la roadmap), qui a besoin du
     * "avant" pour tout round (coup reel et/ou devine) mais du "apres" seulement pour
     * calculer le suffixe echec/mat d'un coup reellement joue.
     */
    public record RoundContext(RoundResult round, Board boardBefore, Board boardAfter) {
    }

    public List<RoundContext> roundHistoryWithPositions() {
        List<RoundContext> contexts = new ArrayList<>();
        for (int i = 0; i < roundHistory.size(); i++) {
            Board before = positionHistory.get(i).board();
            Board after = (i + 1 < positionHistory.size()) ? positionHistory.get(i + 1).board() : null;
            contexts.add(new RoundContext(roundHistory.get(i), before, after));
        }
        return contexts;
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
     * Soumission (ou modification) du coup reel par le joueur au trait. Rappelable
     * librement tant que la devinette de l'adversaire n'est pas deja arrivee (chaque
     * appel remplace le precedent) - voir le commentaire de classe. Le round ne se
     * resout que si cette devinette est deja arrivee ; sinon ce coup reste en attente
     * jusqu'a ce qu'elle arrive.
     *
     * @return le resultat du round si la devinette etait deja arrivee, vide si le
     * round doit encore attendre la devinette
     */
    public Optional<RoundResult> submitMove(Move actualMove) {
        requireOngoing();
        if (!MoveGenerator.isLegalMove(board, sideToMove(), actualMove)) {
            throw new IllegalArgumentException("illegal move: " + actualMove);
        }
        this.pendingMove = actualMove;
        return guessSubmitted ? Optional.of(resolveRound()) : Optional.empty();
    }

    private RoundResult resolveRound() {
        this.drawOfferedBy = null;
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
            if (variant == GameVariant.GUESSCHESS && moverWasInCheck) {
                finish(GameResult.win(guesser, GameResultCause.CHECK_PARRY_GUESSED));
            } else {
                cancelRound(mover, actualMove);
            }
            roundResult = RoundResult.cancelled(mover, guesser, actualMove, guess);
        } else {
            applyRealMove(actualMove);
            roundResult = RoundResult.played(mover, guesser, actualMove, guess);
        }
        this.roundHistory.add(roundResult);
        return roundResult;
    }

    private void applyRealMove(Move move) {
        board = board.applyMove(move);
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
        return new Memento(id, variant, board, List.copyOf(positionHistory), List.copyOf(roundHistory),
                status, result, pendingMove, guessSubmitted, pendingGuess,
                whiteGuessedMove, whiteGuessedMoveStreak, blackGuessedMove, blackGuessedMoveStreak, drawOfferedBy);
    }

    public record Memento(
            GameId id,
            GameVariant variant,
            Board board,
            List<PositionRecord> positionHistory,
            List<RoundResult> roundHistory,
            GameStatus status,
            GameResult result,
            Move pendingMove,
            boolean guessSubmitted,
            Move pendingGuess,
            Move whiteGuessedMove,
            int whiteGuessedMoveStreak,
            Move blackGuessedMove,
            int blackGuessedMoveStreak,
            Color drawOfferedBy
    ) {
    }
}
