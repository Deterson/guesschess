package com.guesschess.infrastructure.web.dto;

/**
 * variant : "GUESSCHESS" ou "GUESSMATE" (voir GameVariant), null traite comme
 * GUESSMATE. color : "WHITE", "BLACK" ou "RANDOM" (resolu cote serveur). timeControl
 * (etape 12) absent/null = partie par correspondance, sans pendule. opponent (etape 15)
 * absent/null ou "HUMAN" = flux habituel (adversaire a rejoindre via /join) ;
 * "COMPUTER" cree une partie deja complete, l'autre couleur liee a un ordinateur du
 * niveau computerLevel ("EASY"/"MEDIUM"/"HARD", requis dans ce cas) - voir
 * GameLifecycleService.createComputerGame.
 */
public record CreateGameHttpRequest(String variant, String color, TimeControlHttpRequest timeControl,
                                     String opponent, String computerLevel) {
}
