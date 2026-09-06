package com.guesschess.infrastructure.computer;

import com.guesschess.application.GameAccess;
import com.guesschess.application.GameAccessRepository;
import com.guesschess.application.GameLifecycleService;
import com.guesschess.application.GameSnapshot;
import com.guesschess.application.MoveIntent;
import com.guesschess.application.PlayerRef;
import com.guesschess.application.PlayerToken;
import com.guesschess.application.computer.ChessEngine;
import com.guesschess.application.computer.ComputerLevel;
import com.guesschess.domain.board.Board;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameStatus;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.infrastructure.websocket.GameBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Fait jouer/deviner l'ordinateur (etape 15 de la roadmap) : declenchee a chaque debut
 * de round (creation d'une partie contre l'ordinateur, ou resolution du round
 * precedent - voir GameCreationController/GameController), jamais par polling. Le role
 * de l'ordinateur pour CE round (joueur au trait, donc il soumet son propre coup, ou
 * adversaire du trait, donc il devine) decoule simplement de Game.sideToMove() compare
 * a la couleur liee a PlayerRef.Computer - dans les deux cas ChessEngine.chooseMove
 * pose exactement la meme question ("meilleur coup dans cette position ?"), voir
 * ChessEngine.
 *
 * Le calcul (process Stockfish, potentiellement quelques centaines de ms) tourne sur
 * un thread virtuel plutot que de bloquer le thread qui vient de traiter la soumission
 * humaine - la reponse de l'ordinateur arrive alors comme un message pousse ordinaire
 * (GameBroadcastService), jamais comme une attente synchrone cote client. Si ce coup
 * complete a son tour la paire du round (l'humain avait deja devine par avance, voir
 * Game.submitMove/submitGuess), ce thread diffuse lui-meme le resultat et se
 * redeclenche pour le round suivant - aucun autre appelant ne surveille cette
 * completion asynchrone.
 */
@Component
public class ComputerPlayerService {

    private static final Logger log = LoggerFactory.getLogger(ComputerPlayerService.class);

    private final GameLifecycleService gameLifecycleService;
    private final GameAccessRepository gameAccessRepository;
    private final ChessEngine chessEngine;
    private final GameBroadcastService gameBroadcastService;

    public ComputerPlayerService(GameLifecycleService gameLifecycleService, GameAccessRepository gameAccessRepository,
                                  ChessEngine chessEngine, GameBroadcastService gameBroadcastService) {
        this.gameLifecycleService = gameLifecycleService;
        this.gameAccessRepository = gameAccessRepository;
        this.chessEngine = chessEngine;
        this.gameBroadcastService = gameBroadcastService;
    }

    public void onRoundStarted(GameId gameId) {
        GameAccess access = gameAccessRepository.findByGameId(gameId).orElse(null);
        if (access == null) {
            return;
        }
        Color computerColor = computerColorOf(access);
        if (computerColor == null) {
            return;
        }
        ComputerLevel level = ((PlayerRef.Computer) access.playerOf(computerColor)).level();
        GameSnapshot snapshot = gameLifecycleService.viewGame(gameId);
        if (snapshot.status() != GameStatus.ONGOING) {
            return;
        }

        PlayerToken token = computerColor == Color.WHITE ? access.whiteToken() : access.blackToken();
        boolean computerIsMover = snapshot.sideToMove() == computerColor;
        Board board = snapshot.board();
        List<Move> legalMoves = snapshot.legalMoves();

        Thread.ofVirtual().name("computer-player-" + gameId).start(
                () -> act(gameId, token, computerIsMover, board, legalMoves, level));
    }

    private void act(GameId gameId, PlayerToken token, boolean computerIsMover, Board board,
                      List<Move> legalMoves, ComputerLevel level) {
        try {
            Move chosen = chessEngine.chooseMove(board, legalMoves, level);
            MoveIntent intent = chosen.promotionType() == null
                    ? MoveIntent.of(chosen.from(), chosen.to())
                    : MoveIntent.promotingTo(chosen.from(), chosen.to(), chosen.promotionType());
            Optional<GameSnapshot> resolved = computerIsMover
                    ? gameLifecycleService.submitMove(token, intent)
                    : gameLifecycleService.submitGuess(token, intent);
            resolved.ifPresent(snapshot -> {
                gameBroadcastService.broadcast(snapshot);
                onRoundStarted(gameId);
            });
        } catch (Exception e) {
            log.error("computer player failed to act for game {}", gameId, e);
        }
    }

    private Color computerColorOf(GameAccess access) {
        if (access.whitePlayer() instanceof PlayerRef.Computer) {
            return Color.WHITE;
        }
        if (access.blackPlayer() instanceof PlayerRef.Computer) {
            return Color.BLACK;
        }
        return null;
    }
}
