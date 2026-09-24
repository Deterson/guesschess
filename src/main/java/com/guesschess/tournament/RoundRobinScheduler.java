package com.guesschess.tournament;

import com.guesschess.domain.board.Board;

import java.util.ArrayList;
import java.util.List;

/**
 * Genere les fixtures d'un tournoi round-robin (etape 22) : chaque paire d'agents distincts
 * se rencontre une fois par position de depart, couleurs alternees (les deux assignations
 * de couleur, meme position) pour que le desequilibre d'une position ne favorise
 * durablement personne.
 */
public final class RoundRobinScheduler {

    private RoundRobinScheduler() {
    }

    public record Fixture(int gameIndex, int openingIndex, Board opening, AgentSpec white, AgentSpec black) {
    }

    public static List<Fixture> schedule(List<AgentSpec> agents, List<Board> openings) {
        List<Fixture> fixtures = new ArrayList<>();
        int gameIndex = 0;
        for (int i = 0; i < agents.size(); i++) {
            for (int j = i + 1; j < agents.size(); j++) {
                for (int o = 0; o < openings.size(); o++) {
                    Board opening = openings.get(o);
                    fixtures.add(new Fixture(gameIndex++, o, opening, agents.get(i), agents.get(j)));
                    fixtures.add(new Fixture(gameIndex++, o, opening, agents.get(j), agents.get(i)));
                }
            }
        }
        return fixtures;
    }
}
