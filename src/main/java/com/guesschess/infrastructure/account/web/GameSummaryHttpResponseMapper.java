package com.guesschess.infrastructure.account.web;

import com.guesschess.application.GameLifecycleService;
import com.guesschess.application.PlayerRef;
import com.guesschess.application.account.AccountService;
import com.guesschess.infrastructure.websocket.GameMessageMapper;

/** Partage entre AccountController ("mes parties") et PlayerProfileController (profil public). */
final class GameSummaryHttpResponseMapper {

    private GameSummaryHttpResponseMapper() {
    }

    static GameSummaryHttpResponse toResponse(GameLifecycleService.GameSummary summary, AccountService accountService, GameMessageMapper mapper) {
        String opponentName = switch (summary.opponent()) {
            case null -> null;
            case PlayerRef.Account account -> accountService.getById(account.userId()).displayName();
            case PlayerRef.Anonymous anonymous -> null;
            // Pas de displayName pour un ordinateur : le niveau tient lieu de "nom",
            // le frontend le traduit/formate lui-meme (voir opponentType COMPUTER).
            case PlayerRef.Computer computer -> computer.level().name();
        };
        GameSummaryHttpResponse.OpponentType opponentType = switch (summary.opponent()) {
            case null -> GameSummaryHttpResponse.OpponentType.NONE;
            case PlayerRef.Account account -> GameSummaryHttpResponse.OpponentType.ACCOUNT;
            case PlayerRef.Anonymous anonymous -> GameSummaryHttpResponse.OpponentType.ANONYMOUS;
            case PlayerRef.Computer computer -> GameSummaryHttpResponse.OpponentType.COMPUTER;
        };
        return new GameSummaryHttpResponse(
                summary.gameId().toString(), summary.myColor().name(), opponentName, opponentType,
                summary.outcome().name(), mapper.toBoardCells(summary.board()));
    }
}
