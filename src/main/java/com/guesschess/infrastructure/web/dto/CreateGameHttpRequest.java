package com.guesschess.infrastructure.web.dto;

/**
 * variant : "GUESSCHESS" ou "NOGUESSMATE" (voir GameVariant), null traite comme
 * GUESSCHESS. color : "WHITE", "BLACK" ou "RANDOM" (resolu cote serveur). timeControl
 * (etape 12) absent/null = partie par correspondance, sans pendule.
 */
public record CreateGameHttpRequest(String variant, String color, TimeControlHttpRequest timeControl) {
}
