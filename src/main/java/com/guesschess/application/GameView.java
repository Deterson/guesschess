package com.guesschess.application;

import com.guesschess.domain.game.PendingSubmission;

/**
 * Reponse a une demande de consultation identifiee par jeton (voir
 * GameLifecycleService.viewGame(GameId, PlayerToken)) : l'etat public habituel,
 * accompagne de la soumission de CE joueur pour le round en cours - jamais celle de
 * l'adversaire (voir Game.mySubmission). mySubmission vaut PendingSubmission.NONE des
 * qu'aucune couleur n'a ete identifiee pour le jeton fourni (jeton null, invalide, ou
 * d'une autre partie) : ce cas degrade en spectateur plutot que d'echouer.
 */
public record GameView(GameSnapshot snapshot, PendingSubmission mySubmission) {
}
