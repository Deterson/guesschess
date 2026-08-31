package com.guesschess.infrastructure.web.dto;

import java.util.List;

/**
 * Historique detaille d'une partie (etape 11 de la roadmap), expose via
 * GET /api/games/{gameId}/history. initialBoard est la position avant le premier
 * round (position de depart standard tant qu'aucun round n'a ete resolu).
 */
public record GameHistoryHttpResponse(String[][] initialBoard, List<GameHistoryEntryHttpResponse> rounds) {
}
