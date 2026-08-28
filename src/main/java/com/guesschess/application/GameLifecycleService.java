package com.guesschess.application;

import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameNotFoundException;
import com.guesschess.domain.game.GameRepository;
import com.guesschess.domain.game.GameVariant;
import com.guesschess.domain.game.PendingSubmission;
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
        return createGame(GameVariant.REGULAR);
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
     * n'est pas deja liee ; et (etape 7, durcissement) rejette l'action si une identite
     * differente a deja revendique cette couleur - le token seul ne suffit plus des
     * qu'un premier joueur a agi. requester null (identite non resolue) ne lie rien et
     * ne declenche jamais ce rejet : le jeu reste possible en anonyme pour un appelant
     * qui ne resout pas d'identite.
     */
    public Optional<GameSnapshot> submitMove(PlayerToken token, MoveIntent intent, PlayerRef requester) {
        GameAccess access = requireAccess(token);
        Color color = access.colorOf(token);
        return gameRepository.withGame(access.gameId(), game -> {
            requireColor(access, token, game.sideToMove());
            requireOwnership(access.gameId(), color, requester);
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
     * Variante liant en plus (etape 6) requester a la couleur du jeton, avec le meme
     * durcissement (etape 7) - voir submitMove(token, intent, requester).
     */
    public Optional<GameSnapshot> submitGuess(PlayerToken token, MoveIntent intent, PlayerRef requester) {
        GameAccess access = requireAccess(token);
        Color color = access.colorOf(token);
        return gameRepository.withGame(access.gameId(), game -> {
            requireColor(access, token, game.sideToMove().opposite());
            requireOwnership(access.gameId(), color, requester);
            Move guess = intent == null ? null : resolveMove(game, intent);
            return game.submitGuess(guess).map(result -> GameSnapshot.of(game));
        });
    }

    public GameSnapshot viewGame(GameId id) {
        return gameRepository.withGame(id, GameSnapshot::of);
    }

    /**
     * Variante (correction du bug de rechargement de page) qui identifie le
     * demandeur par jeton pour joindre a l'etat public sa propre soumission en
     * attente pour le round en cours - jamais celle de l'adversaire, jamais pour un
     * spectateur (token null, invalide, ou d'une autre partie degrade silencieusement
     * vers PendingSubmission.NONE plutot que d'echouer : consulter une partie n'a
     * jamais necessite de jeton valide).
     */
    public GameView viewGame(GameId id, PlayerToken token) {
        return gameRepository.withGame(id, game -> {
            Color color = token == null ? null : gameAccessRepository.findByToken(token)
                    .filter(access -> access.gameId().equals(id))
                    .map(access -> access.colorOf(token))
                    .orElse(null);
            PendingSubmission mySubmission = color == null ? PendingSubmission.NONE : game.mySubmission(color);
            return new GameView(GameSnapshot.of(game), mySubmission);
        });
    }

    /**
     * Les deux couleurs sont-elles liees a un joueur reel (compte ou identite
     * anonyme) ? Conditionne cote frontend l'affichage du bouton "Rejoindre cette
     * partie" a un spectateur - false (partie non trouvee) plutot qu'une exception,
     * ce cas n'ayant pas besoin d'etre distingue par l'appelant ici.
     */
    public boolean isFull(GameId gameId) {
        return gameAccessRepository.findByGameId(gameId)
                .map(GameAccess::isFull)
                .orElse(false);
    }

    /**
     * Acceptation d'une invitation (etape 7, sans token) : revendique l'unique couleur
     * encore libre de gameId pour requester - il n'y a plus de choix de couleur a faire
     * cote appelant, puisqu'un lien reel n'a jamais qu'un seul siege libre (le
     * createur revendique deja le sien a la creation). linkedToRequester distingue une
     * liaison reussie (nouvelle, ou reclic sur son propre lien) d'une invitation deja
     * consommee par un autre joueur entre-temps (course perdue).
     *
     * @throws NoOpenColorException si les deux couleurs sont deja revendiquees
     */
    public JoinResult joinGame(GameId gameId, PlayerRef requester) {
        GameAccess access = gameAccessRepository.findByGameId(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId));
        Color openColor = access.playerOf(Color.WHITE) == null ? Color.WHITE
                : access.playerOf(Color.BLACK) == null ? Color.BLACK
                : null;
        if (openColor == null) {
            throw new NoOpenColorException(gameId);
        }
        PlayerToken token = openColor == Color.WHITE ? access.whiteToken() : access.blackToken();
        PlayerRef bound = gameAccessRepository.linkPlayer(gameId, openColor, requester);
        return new JoinResult(gameId, openColor, token, bound.equals(requester));
    }

    /**
     * Retrouve le jeton/couleur de requester pour gameId a partir de sa seule identite
     * (etape 7, recuperation d'acces) - utile a un joueur anonyme qui a perdu l'URL
     * contenant son jeton (onglet ferme sans l'avoir enregistree ailleurs) : son
     * identite (cookie anonyme persistant, ou compte) suffit a la retrouver. Vide si
     * requester n'est lie a aucune des deux couleurs de cette partie, y compris si
     * requester est null (identite non resolue).
     */
    public Optional<MyAccess> findMyAccess(GameId gameId, PlayerRef requester) {
        if (requester == null) {
            return Optional.empty();
        }
        GameAccess access = gameAccessRepository.findByGameId(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId));
        if (requester.equals(access.playerOf(Color.WHITE))) {
            return Optional.of(new MyAccess(Color.WHITE, access.whiteToken()));
        }
        if (requester.equals(access.playerOf(Color.BLACK))) {
            return Optional.of(new MyAccess(Color.BLACK, access.blackToken()));
        }
        return Optional.empty();
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

    private void requireOwnership(GameId gameId, Color color, PlayerRef requester) {
        if (requester == null) {
            return;
        }
        PlayerRef bound = gameAccessRepository.linkPlayer(gameId, color, requester);
        if (!bound.equals(requester)) {
            throw new NotYourColorException(color);
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
