package com.guesschess.application.computer;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.move.Move;

import java.util.List;

/**
 * Port (etape 15 de la roadmap) vers un moteur d'echecs externe capable de choisir un
 * coup dans une position donnee - implemente en infrastructure par StockfishChessEngine
 * (process UCI). Un seul et meme appel sert aussi bien a l'ordinateur pour jouer son
 * propre coup (il est le joueur au trait) qu'a deviner le coup de l'adversaire (il ne
 * l'est pas) : dans les deux cas la question posee au moteur est identique - "quel est
 * le meilleur coup dans cette position ?" - seule la couleur pour laquelle
 * GameLifecycleService soumet la reponse (submitMove vs submitGuess) differe. Voir
 * ComputerPlayerService.
 */
public interface ChessEngine {

    /**
     * Verification rapide (sans lancer de recherche) que le moteur est utilisable -
     * permet a GameLifecycleService.createComputerGame d'echouer immediatement
     * (ComputerUnavailableException) plutot que de creer une partie que l'ordinateur
     * ne pourra jamais jouer (voir ComputerPlayerService, qui tourne en arriere-plan et
     * ne peut donc pas faire remonter une erreur a la requete de creation).
     */
    boolean isAvailable();

    /**
     * board : position courante (voir Board.toFen - jamais reconstruite depuis un
     * historique de coups, qui ne peut pas representer les passes de trait de la regle
     * de devinette). legalMoves : coups legaux du joueur au trait dans cette position
     * (GameSnapshot.legalMoves) - le coup retourne en fait toujours partie, deja
     * enrichi (piece capturee, type de coup...).
     */
    Move chooseMove(Board board, List<Move> legalMoves, ComputerLevel level);
}
