package com.guesschess.application;

import com.guesschess.domain.piece.Color;

/**
 * Leve (etape 7, durcissement) quand l'identite resolue pour la connexion appelante ne
 * correspond pas a celle deja liee a color pour cette partie - le token seul ne suffit
 * plus a agir des qu'une identite differente a deja revendique la couleur (premier
 * arrive, premier lie, voir GameAccessRepository.linkPlayer). requester null (identite
 * non resolue) ne declenche jamais cette exception : voir GameLifecycleService.
 */
public class NotYourColorException extends RuntimeException {

    public NotYourColorException(Color color) {
        super("color " + color + " is already controlled by another player");
    }
}
