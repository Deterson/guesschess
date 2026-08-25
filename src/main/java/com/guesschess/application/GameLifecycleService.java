package com.guesschess.application;

import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameRepository;
import com.guesschess.domain.game.GameVariant;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Orchestre le cycle de vie d'une partie : creation, et soumission des coups/
 * devinettes par jeton. Traduit les intentions externes (MoveIntent) en commandes du
 * domaine, et n'expose jamais la devinette en attente (GameSnapshot le garantit).
 */
@Service
public class GameLifecycleService {

    private final GameRepository gameRepository;
    private final GameAccessRepository gameAccessRepository;

    public GameLifecycleService(GameRepository gameRepository, GameAccessRepository gameAccessRepository) {
        this.gameRepository = gameRepository;
        this.gameAccessRepository = gameAccessRepository;
    }

    public CreatedGame createGame() {
        return createGame(GameVariant.GUESSCHESS);
    }

    public CreatedGame createGame(GameVariant variant) {
        Game game = Game.newGame(variant);
        gameRepository.insert(game);

        PlayerToken whiteToken = PlayerToken.random();
        PlayerToken blackToken = PlayerToken.random();
        gameAccessRepository.save(new GameAccess(game.id(), whiteToken, blackToken));

        return new CreatedGame(game.id(), whiteToken, blackToken, variant);
    }

    /**
     * Soumission du coup reel par le joueur au trait. Le round n'attend plus que la
     * devinette de l'adversaire : si elle est deja arrivee, retourne l'etat
     * resultant (a diffuser publiquement, le round vient de se resoudre) ; sinon
     * vide, le coup reste enregistre en attente.
     */
    public Optional<GameSnapshot> submitMove(PlayerToken token, MoveIntent intent) {
        GameAccess access = requireAccess(token);
        return gameRepository.withGame(access.gameId(), game -> {
            requireColor(access, token, game.sideToMove());
            Move move = resolveMove(game, intent);
            return game.submitMove(move).map(result -> GameSnapshot.of(game));
        });
    }

    /**
     * Soumission (ou modification) de la devinette par l'adversaire du joueur au
     * trait. intent null signifie explicitement "pas de devinette" - une soumission
     * a part entiere, pas une absence. Si le coup reel est deja arrive, retourne
     * l'etat resultant (a diffuser publiquement) ; sinon vide, le contenu doit
     * rester cache tant que le round n'est pas resolu.
     */
    public Optional<GameSnapshot> submitGuess(PlayerToken token, MoveIntent intent) {
        GameAccess access = requireAccess(token);
        return gameRepository.withGame(access.gameId(), game -> {
            requireColor(access, token, game.sideToMove().opposite());
            Move guess = intent == null ? null : resolveMove(game, intent);
            return game.submitGuess(guess).map(result -> GameSnapshot.of(game));
        });
    }

    public GameSnapshot viewGame(GameId id) {
        return gameRepository.withGame(id, GameSnapshot::of);
    }

    private GameAccess requireAccess(PlayerToken token) {
        return gameAccessRepository.findByToken(token)
                .orElseThrow(() -> new UnknownPlayerTokenException(token));
    }

    private void requireColor(GameAccess access, PlayerToken token, Color expected) {
        Color actual = access.colorOf(token);
        if (actual != expected) {
            throw new WrongTurnException(expected, actual);
        }
    }

    private Move resolveMove(Game game, MoveIntent intent) {
        return game.legalMoves().stream()
                .filter(m -> m.from().equals(intent.from())
                        && m.to().equals(intent.to())
                        && m.promotionType() == intent.promotion())
                .findFirst()
                .orElseThrow(() -> new NoSuchLegalMoveException(intent));
    }
}
