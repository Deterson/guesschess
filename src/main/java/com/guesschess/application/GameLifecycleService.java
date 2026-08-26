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
     * Variante (etape 7) qui lie immediatement le createur a la couleur choisie, avant
     * meme que la partie ne soit annoncee a un adversaire - contrairement au lien
     * paresseux de submitMove/submitGuess (premier coup/devinette soumis).
     */
    public CreatedGame createGame(GameVariant variant, Color creatorColor, PlayerRef creator) {
        CreatedGame created = createGame(variant);
        gameAccessRepository.linkPlayer(created.gameId(), creatorColor, creator);
        return created;
    }

    /**
     * Soumission du coup reel par le joueur au trait. Le round n'attend plus que la
     * devinette de l'adversaire : si elle est deja arrivee, retourne l'etat
     * resultant (a diffuser publiquement, le round vient de se resoudre) ; sinon
     * vide, le coup reste enregistre en attente.
     */
    public Optional<GameSnapshot> submitMove(PlayerToken token, MoveIntent intent) {
        return submitMove(token, intent, null);
    }

    /**
     * Variante liant en plus (etape 6) requester - le compte ou l'identite anonyme
     * resolue pour la connexion appelante - a la couleur du jeton, si cette couleur
     * n'est pas deja liee. requester null (identite non resolue) ne lie rien mais
     * n'empeche pas la soumission : le jeu reste possible en anonyme.
     */
    public Optional<GameSnapshot> submitMove(PlayerToken token, MoveIntent intent, PlayerRef requester) {
        GameAccess access = requireAccess(token);
        Color color = access.colorOf(token);
        return gameRepository.withGame(access.gameId(), game -> {
            requireColor(access, token, game.sideToMove());
            if (requester != null) {
                gameAccessRepository.linkPlayer(access.gameId(), color, requester);
            }
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
        return submitGuess(token, intent, null);
    }

    /**
     * Variante liant en plus (etape 6) requester a la couleur du jeton - voir
     * submitMove(token, intent, requester).
     */
    public Optional<GameSnapshot> submitGuess(PlayerToken token, MoveIntent intent, PlayerRef requester) {
        GameAccess access = requireAccess(token);
        Color color = access.colorOf(token);
        return gameRepository.withGame(access.gameId(), game -> {
            requireColor(access, token, game.sideToMove().opposite());
            if (requester != null) {
                gameAccessRepository.linkPlayer(access.gameId(), color, requester);
            }
            Move guess = intent == null ? null : resolveMove(game, intent);
            return game.submitGuess(guess).map(result -> GameSnapshot.of(game));
        });
    }

    public GameSnapshot viewGame(GameId id) {
        return gameRepository.withGame(id, GameSnapshot::of);
    }

    /**
     * Acceptation d'un lien d'invitation (etape 7) : lie requester a la couleur du
     * jeton s'il n'y a pas deja quelqu'un d'autre. linkedToRequester distingue une
     * liaison reussie (nouvelle, ou reclic sur son propre lien) d'une invitation deja
     * consommee par un autre joueur - a l'appelant de traduire ce dernier cas en erreur
     * claire (l'invitation est a usage unique).
     */
    public JoinResult joinGame(PlayerToken token, PlayerRef requester) {
        GameAccess access = requireAccess(token);
        Color color = access.colorOf(token);
        PlayerRef bound = gameAccessRepository.linkPlayer(access.gameId(), color, requester);
        return new JoinResult(access.gameId(), color, bound.equals(requester));
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
