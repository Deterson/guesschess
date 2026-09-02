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
        };
        GameSummaryHttpResponse.OpponentType opponentType = switch (summary.opponent()) {
            case null -> GameSummaryHttpResponse.OpponentType.NONE;
            case PlayerRef.Account account -> GameSummaryHttpResponse.OpponentType.ACCOUNT;
            case PlayerRef.Anonymous anonymous -> GameSummaryHttpResponse.OpponentType.ANONYMOUS;
        };
        return new GameSummaryHttpResponse(
                summary.gameId().toString(), summary.myColor().name(), opponentName, opponentType,
                summary.outcome().name(), mapper.toBoardCells(summary.board()));
    }
}
