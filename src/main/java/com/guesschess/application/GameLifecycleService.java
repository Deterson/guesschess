package com.guesschess.application;

import com.guesschess.domain.account.UserId;
import com.guesschess.domain.board.Board;
import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameNotFoundException;
import com.guesschess.domain.game.GameRepository;
import com.guesschess.domain.game.GameResult;
import com.guesschess.domain.game.GameStatus;
import com.guesschess.domain.game.GameVariant;
import com.guesschess.domain.game.PendingSubmission;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.pggn.PggnWriter;
import com.guesschess.domain.piece.Color;
import org.springframework.stereotype.Service;

import java.util.List;
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
     * n'est pas deja liee ; et (etape 7, durcissement) rejette l'action si une identite
     * differente a deja revendique cette couleur - le token seul ne suffit plus des
     * qu'un premier joueur a agi. requester null (identite non resolue) ne lie rien et
     * ne declenche jamais ce rejet : le jeu reste possible en anonyme pour un appelant
     * qui ne resout pas d'identite.
     */
    public Optional<GameSnapshot> submitMove(PlayerToken token, MoveIntent intent, PlayerRef requester) {
        GameAccess access = requireAccess(token);
        requireFull(access);
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
        requireFull(access);
        Color color = access.colorOf(token);
        return gameRepository.withGame(access.gameId(), game -> {
            requireColor(access, token, game.sideToMove().opposite());
            requireOwnership(access.gameId(), color, requester);
            Move guess = intent == null ? null : resolveMove(game, intent);
            return game.submitGuess(guess).map(result -> GameSnapshot.of(game));
        });
    }

    /**
     * Proposition de nulle (etape supplementaire, hors roadmap initiale) - a la
     * difference de submitMove/submitGuess, ne depend pas du trait : n'importe quel
     * joueur peut proposer a tout moment. Toujours resolue synchroniquement (pas de
     * paire de soumissions a attendre), donc toujours diffusable immediatement.
     */
    public GameSnapshot offerDraw(PlayerToken token, PlayerRef requester) {
        GameAccess access = requireAccess(token);
        requireFull(access);
        Color color = access.colorOf(token);
        return gameRepository.withGame(access.gameId(), game -> {
            requireOwnership(access.gameId(), color, requester);
            game.offerDraw(color);
            return GameSnapshot.of(game);
        });
    }

    /**
     * Reponse (acceptation ou refus) a l'offre de nulle en attente - voir
     * GameLifecycleService.offerDraw et Game.respondToDraw.
     */
    public GameSnapshot respondToDraw(PlayerToken token, boolean accept, PlayerRef requester) {
        GameAccess access = requireAccess(token);
        requireFull(access);
        Color color = access.colorOf(token);
        return gameRepository.withGame(access.gameId(), game -> {
            requireOwnership(access.gameId(), color, requester);
            game.respondToDraw(color, accept);
            return GameSnapshot.of(game);
        });
    }

    public GameSnapshot viewGame(GameId id) {
        return gameRepository.withGame(id, GameSnapshot::of);
    }

    /**
     * Export PGGN (etape 10 de la roadmap) - lecture seule, accessible sans jeton au
     * meme titre que viewGame (le mode spectateur n'a jamais requis d'authentification,
     * voir GameCreationController). Event/Date/White/Black restent "?" (voir
     * PggnWriter) tant que la page profil de l'etape 8 n'existe pas.
     */
    public String exportPggn(GameId id) {
        return gameRepository.withGame(id, PggnWriter::write);
    }

    /**
     * Historique detaille rond par rond (etape 11 de la roadmap) - lecture seule,
     * accessible sans jeton comme exportPggn/viewGame. initialBoard est le plateau
     * avant le premier round (ou le plateau courant si aucun round n'a encore ete
     * resolu) ; rounds porte, pour chaque round (y compris annules), le plateau
     * avant/apres (Game.roundHistoryWithPositions) necessaire au frontend pour
     * naviguer dans l'historique et afficher la devinette en fantome.
     */
    public record GameHistorySnapshot(Board initialBoard, List<Game.RoundContext> rounds) {
    }

    public GameHistorySnapshot gameHistory(GameId id) {
        return gameRepository.withGame(id, game -> {
            List<Game.RoundContext> contexts = game.roundHistoryWithPositions();
            Board initial = contexts.isEmpty() ? game.board() : contexts.get(0).boardBefore();
            return new GameHistorySnapshot(initial, contexts);
        });
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
     * Identite des deux joueurs (etape 14, affichage du pseudo cote plateau) - vide si
     * gameId n'existe pas, whitePlayer/blackPlayer valent null tant que la couleur
     * correspondante n'est pas encore liee (voir GameAccess).
     */
    public Optional<GameAccess> findAccess(GameId gameId) {
        return gameAccessRepository.findByGameId(gameId);
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

    /**
     * Resolution en lecture seule token -> couleur, pour un usage qui n'a besoin de
     * verifier "es-tu un joueur de cette partie" sans agir sur l'agregat Game (ex. chat
     * ephemere). Vide si token est null, inconnu, ou appartient a une autre partie -
     * jamais d'exception, contrairement a requireAccess qui est reservee aux actions de
     * jeu (coup/devinette).
     */
    public Optional<Color> resolveColor(GameId id, PlayerToken token) {
        if (token == null) {
            return Optional.empty();
        }
        return gameAccessRepository.findByToken(token)
                .filter(access -> access.gameId().equals(id))
                .map(access -> access.colorOf(token));
    }

    /**
     * Photo d'une partie du point de vue d'un compte (etape 8, "Mes parties") : sa
     * couleur, l'adversaire (encore inconnu tant que l'autre siege n'est pas pris),
     * et de quoi deriver l'issue (outcome()) et une miniature du plateau cote appelant.
     */
    public record GameSummary(GameId gameId, Color myColor, PlayerRef opponent, GameStatus status, GameResult result,
                               Board board) {

        public enum Outcome {
            WON, LOST, DRAW, ONGOING
        }

        public Outcome outcome() {
            if (status != GameStatus.FINISHED) {
                return Outcome.ONGOING;
            }
            if (result.isDraw()) {
                return Outcome.DRAW;
            }
            return result.winner() == myColor ? Outcome.WON : Outcome.LOST;
        }
    }

    /**
     * Parties d'un compte (etape 8), triees par recence - voir
     * GameAccessRepository.findAllByAccount. Chaque partie est relue via withGame comme
     * viewGame/gameHistory : un verrou pessimiste bref par ligne, largement suffisant a
     * l'echelle visee (mesurer avant d'optimiser, voir CLAUDE.md).
     */
    public List<GameSummary> listGamesForAccount(UserId userId, int page, int size) {
        PlayerRef me = new PlayerRef.Account(userId);
        return gameAccessRepository.findAllByAccount(userId, page, size).stream()
                .map(access -> toSummary(access, me))
                .toList();
    }

    private GameSummary toSummary(GameAccess access, PlayerRef me) {
        Color myColor = me.equals(access.playerOf(Color.WHITE)) ? Color.WHITE : Color.BLACK;
        PlayerRef opponent = access.playerOf(myColor.opposite());
        return gameRepository.withGame(access.gameId(), game ->
                new GameSummary(access.gameId(), myColor, opponent, game.status(), game.result(), game.board()));
    }

    private GameAccess requireAccess(PlayerToken token) {
        return gameAccessRepository.findByToken(token)
                .orElseThrow(() -> new UnknownPlayerTokenException(token));
    }

    /**
     * Rejette toute soumission (coup ou devinette) tant que les deux couleurs ne sont
     * pas liees a un joueur reel - empeche le createur de jouer contre lui-meme (ou de
     * deviner son propre coup) avant qu'un adversaire n'ait rejoint la partie.
     */
    private void requireFull(GameAccess access) {
        if (!access.isFull()) {
            throw new GameNotFullException(access.gameId());
        }
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
