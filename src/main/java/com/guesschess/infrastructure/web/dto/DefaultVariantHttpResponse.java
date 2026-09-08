package com.guesschess.infrastructure.web.dto;

/**
 * GET /api/games/default-variant (etape 16) - variante ("GUESSCHESS"/"GUESSMATE",
 * voir GameVariant) proposee par defaut a la creation, pilotee par la propriete
 * guesschess.default-variant.
 */
public record DefaultVariantHttpResponse(String variant) {
}
