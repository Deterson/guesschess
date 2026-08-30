package com.guesschess.infrastructure.persistence.jpa;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.CastlingRights;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameResult;
import com.guesschess.domain.game.RoundResult;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.move.MoveType;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Conversion Game (domaine, via Game.Memento) <-> GameEntity (JPA, colonnes
 * scalaires + state JSONB). Seul point du code qui connait a la fois le domaine et
 * la forme de stockage.
 */
@Component
class GameJpaMapper {

    GameEntity toNewEntity(Game game) {
        Game.Memento memento = game.toMemento();
        Instant now = Instant.now();
        return new GameEntity(
                memento.id().value(),
                memento.variant(),
                memento.status(),
                memento.result() != null ? memento.result().winner() : null,
                memento.result() != null ? memento.result().cause() : null,
                memento.board().sideToMove(),
                toStateJson(memento),
                now,
                now
        );
    }

    void updateEntity(GameEntity entity, Game game) {
        Game.Memento memento = game.toMemento();
        entity.updateFrom(
                memento.status(),
                memento.result() != null ? memento.result().winner() : null,
                memento.result() != null ? memento.result().cause() : null,
                memento.board().sideToMove(),
                toStateJson(memento),
                Instant.now()
        );
    }

    Game toDomain(GameEntity entity) {
        GameStateJson state = entity.getState();
        GameResult result = entity.getResultCause() != null
                ? new GameResult(entity.getResultWinner(), entity.getResultCause())
                : null;
        List<Game.PositionRecord> positionHistory = state.positionHistory().stream().map(this::toPositionRecord).toList();
        List<RoundResult> roundHistory = state.roundHistory().stream().map(this::toRoundResult).toList();

        Game.Memento memento = new Game.Memento(
                new GameId(entity.getId()),
                entity.getVariant(),
                toBoard(state.board()),
                positionHistory,
                roundHistory,
                entity.getStatus(),
                result,
                toMove(state.pendingMove()),
                state.guessSubmitted(),
                toMove(state.pendingGuess()),
                toMove(state.whiteGuessedMove()),
                state.whiteGuessedMoveStreak(),
                toMove(state.blackGuessedMove()),
                state.blackGuessedMoveStreak()
        );
        return Game.fromMemento(memento);
    }

    private GameStateJson toStateJson(Game.Memento memento) {
        return new GameStateJson(
                toBoardJson(memento.board()),
                memento.positionHistory().stream().map(this::toPositionRecordJson).toList(),
                toMoveJson(memento.pendingMove()),
                memento.guessSubmitted(),
                toMoveJson(memento.pendingGuess()),
                memento.roundHistory().stream().map(this::toRoundResultJson).toList(),
                toMoveJson(memento.whiteGuessedMove()),
                memento.whiteGuessedMoveStreak(),
                toMoveJson(memento.blackGuessedMove()),
                memento.blackGuessedMoveStreak()
        );
    }

    private PositionRecordJson toPositionRecordJson(Game.PositionRecord record) {
        return new PositionRecordJson(toBoardJson(record.board()), record.origin().name());
    }

    private Game.PositionRecord toPositionRecord(PositionRecordJson json) {
        return new Game.PositionRecord(toBoard(json.board()), Game.PositionOrigin.valueOf(json.origin()));
    }

    private BoardJson toBoardJson(Board board) {
        Piece[] squares = board.squaresSnapshot();
        List<PieceJson> squaresJson = new ArrayList<>(squares.length);
        for (Piece piece : squares) {
            squaresJson.add(toPieceJson(piece));
        }
        CastlingRights rights = board.castlingRights();
        return new BoardJson(
                squaresJson,
                board.sideToMove().name(),
                new CastlingRightsJson(rights.whiteKingside(), rights.whiteQueenside(), rights.blackKingside(), rights.blackQueenside()),
                board.enPassantTarget() != null ? board.enPassantTarget().toAlgebraic() : null,
                board.halfmoveClock(),
                board.fullmoveNumber()
        );
    }

    private Board toBoard(BoardJson json) {
        Piece[] squares = new Piece[json.squares().size()];
        for (int i = 0; i < squares.length; i++) {
            squares[i] = toPiece(json.squares().get(i));
        }
        CastlingRightsJson rights = json.castlingRights();
        return Board.reconstruct(
                squares,
                Color.valueOf(json.sideToMove()),
                new CastlingRights(rights.whiteKingside(), rights.whiteQueenside(), rights.blackKingside(), rights.blackQueenside()),
                json.enPassantTarget() != null ? Position.fromAlgebraic(json.enPassantTarget()) : null,
                json.halfmoveClock(),
                json.fullmoveNumber()
        );
    }

    private PieceJson toPieceJson(Piece piece) {
        return piece != null ? new PieceJson(piece.type().name(), piece.color().name()) : null;
    }

    private Piece toPiece(PieceJson json) {
        return json != null ? Piece.of(PieceType.valueOf(json.type()), Color.valueOf(json.color())) : null;
    }

    private MoveJson toMoveJson(Move move) {
        if (move == null) {
            return null;
        }
        return new MoveJson(
                move.from().toAlgebraic(),
                move.to().toAlgebraic(),
                toPieceJson(move.movedPiece()),
                toPieceJson(move.capturedPiece()),
                move.type().name(),
                move.promotionType() != null ? move.promotionType().name() : null
        );
    }

    private Move toMove(MoveJson json) {
        if (json == null) {
            return null;
        }
        return new Move(
                Position.fromAlgebraic(json.from()),
                Position.fromAlgebraic(json.to()),
                toPiece(json.movedPiece()),
                toPiece(json.capturedPiece()),
                MoveType.valueOf(json.type()),
                json.promotionType() != null ? PieceType.valueOf(json.promotionType()) : null
        );
    }

    private RoundResultJson toRoundResultJson(RoundResult result) {
        if (result == null) {
            return null;
        }
        return new RoundResultJson(
                result.mover().name(),
                result.guesser().name(),
                toMoveJson(result.actualMove()),
                toMoveJson(result.guessedMove())
        );
    }

    private RoundResult toRoundResult(RoundResultJson json) {
        if (json == null) {
            return null;
        }
        return new RoundResult(
                Color.valueOf(json.mover()),
                Color.valueOf(json.guesser()),
                toMove(json.actualMove()),
                toMove(json.guessedMove())
        );
    }
}
