package com.guesschess.domain.pggn;

import java.util.List;
import java.util.Map;

/**
 * Representation structuree d'une partie au format PGGN (etape 10 de la roadmap) :
 * ce que produit PggnWriter a partir d'un Game, et ce que PggnParser reconstruit a
 * partir d'un texte .pggn. PggnParser fait de l'extraction simple (pas de
 * revalidation contre le moteur de regles) : un PggnGame issu d'un parsing n'est donc
 * pas une source de verite fiable, seul un PggnGame issu de PggnWriter l'est.
 */
public record PggnGame(Map<String, String> tags, List<PggnPly> plies) {
}
