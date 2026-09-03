package com.guesschess.infrastructure.websocket;

import com.guesschess.application.GameAccess;
import com.guesschess.application.GameSnapshot;
import com.guesschess.application.MoveIntent;
import com.guesschess.application.PlayerRef;
import com.guesschess.application.account.AccountService;
import com.guesschess.application.account.AccountSnapshot;
import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameResult;
import com.guesschess.domain.game.RoundResult;
import com.guesschess.domain.game.PendingSubmission;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.notation.SanGenerator;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import com.guesschess.infrastructure.websocket.dto.GamePlayersMessage;
import com.guesschess.infrastructure.websocket.dto.GameStateMessage;
import com.guesschess.infrastructure.websocket.dto.LegalMoveMessage;
import com.guesschess.infrastructure.websocket.dto.MoveHistoryEntry;
import com.guesschess.infrastructure.websocket.dto.MySubmissionMessage;
import com.guesschess.infrastructure.websocket.dto.PlayerInfoMessage;
import com.guesschess.infrastructure.websocket.dto.ResultMessage;
import com.guesschess.infrastructure.websocket.dto.RoundSummaryMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Traduction entre le protocole fil (chaines algebriques, codes piece) et les types
 * du domaine. Isole les DTO de la structure interne de Board/Move/Piece.
 */
@Component
public class GameMessageMapper {

    private final AccountService accountService;
    private final GamePresenceService presenceService;

    public GameMessageMapper(AccountService accountService, GamePresenceService presenceService) {
        this.accountService = accountService;
        this.presenceService = presenceService;
    }

    /**
     * Identite affichable des deux joueurs (etape 14) - login vaut le displayName pour
     * un compte historique qui n'a pas encore choisi le sien (voir CLAUDE.md, migration
     * V9), jamais null, pour ne jamais afficher un nom vide sur une partie en cours.
     * connected (GamePresenceService) reflete la connexion WebSocket en direct de la
     * couleur - jamais null tant qu'un joueur reel est lie, false par defaut sinon.
     */
    public GamePlayersMessage toPlayersMessage(GameAccess access) {
        return new GamePlayersMessage(
                toPlayerInfo(access.playerOf(Color.WHITE), access.gameId(), Color.WHITE),
                toPlayerInfo(access.playerOf(Color.BLACK), access.gameId(), Color.BLACK));
    }

    private PlayerInfoMessage toPlayerInfo(PlayerRef ref, GameId gameId, Color color) {
        return switch (ref) {
            case null -> null;
            case PlayerRef.Anonymous anonymous -> new PlayerInfoMessage("ANONYMOUS", null, presenceService.isConnected(gameId, color));
            case PlayerRef.Account account -> {
                AccountSnapshot snapshot = accountService.getById(account.userId());
                yield new PlayerInfoMessage("ACCOUNT", snapshot.login() != null ? snapshot.login() : snapshot.displayName(),
                        presenceService.isConnected(gameId, color));
            }
        };
    }

    /**
     * A n'utiliser que pour une diffusion publique (/topic/games/{gameId}) : ne porte
     * jamais de soumission personnelle (voir MySubmissionMessage.NONE), par
     * construction plutot que par convention chez l'appelant.
     */
    public GameStateMessage toGameStateMessage(GameSnapshot snapshot, boolean full) {
        return toGameStateMessage(snapshot, full, PendingSubmission.NONE);
    }

    public GameStateMessage toGameStateMessage(GameSnapshot snapshot, boolean full, PendingSubmission mySubmission) {
        return new GameStateMessage(
                snapshot.id().toString(),
                snapshot.variant().name(),
                toBoardCells(snapshot.board()),
                snapshot.sideToMove().name(),
                snapshot.status().name(),
                toResultMessage(snapshot.result()),
                toRoundSummaryMessage(snapshot.lastRoundResult()),
                toLegalMoveMessages(snapshot.legalMoves()),
                toMoveHistoryEntries(snapshot.playedMoveHistory()),
                full,
                toMySubmissionMessage(mySubmission),
                snapshot.roundCount(),
                snapshot.inCheck(),
                snapshot.drawOfferedBy() == null ? null : snapshot.drawOfferedBy().name(),
                snapshot.rematchOfferedBy() == null ? null : snapshot.rematchOfferedBy().name(),
                snapshot.rematchGameId() == null ? null : snapshot.rematchGameId().toString()
        );
    }

    public MoveIntent toMoveIntent(String from, String to, String promotion) {
        Position fromPosition = Position.fromAlgebraic(from);
        Position toPosition = Position.fromAlgebraic(to);
        if (promotion == null) {
            return MoveIntent.of(fromPosition, toPosition);
        }
        return MoveIntent.promotingTo(fromPosition, toPosition, PieceType.valueOf(promotion));
    }

    private ResultMessage toResultMessage(GameResult result) {
        if (result == null) {
            return null;
        }
        return new ResultMessage(result.winner() == null ? null : result.winner().name(), result.cause().name());
    }

    private RoundSummaryMessage toRoundSummaryMessage(RoundResult roundResult) {
        if (roundResult == null) {
            return null;
        }
        return new RoundSummaryMessage(
                roundResult.mover().name(),
                roundResult.guesser().name(),
                roundResult.actualMove().from().toAlgebraic(),
                roundResult.actualMove().to().toAlgebraic(),
                roundResult.guessedMove() == null ? null : roundResult.guessedMove().from().toAlgebraic(),
                roundResult.guessedMove() == null ? null : roundResult.guessedMove().to().toAlgebraic(),
                roundResult.guessedCorrectly()
        );
    }

    private MySubmissionMessage toMySubmissionMessage(PendingSubmission mySubmission) {
        if (!mySubmission.submitted()) {
            return MySubmissionMessage.NONE;
        }
        Move move = mySubmission.move();
        if (move == null) {
            return new MySubmissionMessage(true, null, null, null);
        }
        return new MySubmissionMessage(
                true,
                move.from().toAlgebraic(),
                move.to().toAlgebraic(),
                move.promotionType() == null ? null : move.promotionType().name());
    }

    private List<LegalMoveMessage> toLegalMoveMessages(List<Move> legalMoves) {
        return legalMoves.stream()
                .map(move -> new LegalMoveMessage(
                        move.from().toAlgebraic(),
                        move.to().toAlgebraic(),
                        move.promotionType() == null ? null : move.promotionType().name()))
                .toList();
    }

    private List<MoveHistoryEntry> toMoveHistoryEntries(List<Game.PlayedMove> playedMoveHistory) {
        return playedMoveHistory.stream()
                .map(played -> new MoveHistoryEntry(
                        played.move().movedPiece().color().name(),
                        SanGenerator.toSan(played.boardBefore(), played.move(), played.boardAfter())))
                .toList();
    }

    public String[][] toBoardCells(Board board) {
        String[][] cells = new String[8][8];
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Piece piece = board.pieceAt(Position.of(file, rank));
                cells[rank][file] = piece == null ? null : toCode(piece);
            }
        }
        return cells;
    }

    private String toCode(Piece piece) {
        char colorCode = piece.color() == Color.WHITE ? 'w' : 'b';
        char typeCode = switch (piece.type()) {
            case PAWN -> 'P';
            case KNIGHT -> 'N';
            case BISHOP -> 'B';
            case ROOK -> 'R';
            case QUEEN -> 'Q';
            case KING -> 'K';
        };
        return "" + colorCode + typeCode;
    }
}
