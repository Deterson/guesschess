package com.guesschess.infrastructure.websocket.dto;

/**
 * Le coup ou la devinette que CE joueur (jamais l'adversaire, jamais un spectateur) a
 * deja soumis pour le round en cours, si connu du serveur - permet au frontend de
 * refleter une soumission survivant a un rechargement de page sans retenter un envoi
 * que le serveur bloquerait (second coup reel du meme round) ou ecraserait
 * silencieusement (devinette modifiee). submitted=false : rien soumis pour ce round,
 * from/to/promotion alors toujours null. submitted=true et from/to null : devinette
 * explicitement "aucune" (bouton "Ne pas deviner"). N'apparait jamais que dans la
 * reponse privee a /app/games/{id}/view - toujours NONE dans la diffusion publique
 * /topic/games/{gameId}, jamais la soumission de l'adversaire.
 */
public record MySubmissionMessage(boolean submitted, String from, String to, String promotion) {

    public static final MySubmissionMessage NONE = new MySubmissionMessage(false, null, null, null);
}
