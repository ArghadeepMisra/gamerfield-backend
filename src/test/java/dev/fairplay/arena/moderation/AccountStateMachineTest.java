package dev.fairplay.arena.moderation;

import dev.fairplay.arena.common.error.ApiException;
import dev.fairplay.arena.common.error.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountStateMachineTest {

    private final AccountStateMachine stateMachine = new AccountStateMachine();

    record TransitionCase(AccountStatus from, ModerationActionType action, AccountStatus expectedTo) {}

    private static final Map<TransitionCaseKey, AccountStatus> VALID_TRANSITIONS = Map.ofEntries(
            Map.entry(new TransitionCaseKey(AccountStatus.ACTIVE, ModerationActionType.FLAG), AccountStatus.FLAGGED),
            Map.entry(new TransitionCaseKey(AccountStatus.REINSTATED, ModerationActionType.FLAG), AccountStatus.FLAGGED),
            Map.entry(new TransitionCaseKey(AccountStatus.ACTIVE, ModerationActionType.BAN), AccountStatus.BANNED),
            Map.entry(new TransitionCaseKey(AccountStatus.FLAGGED, ModerationActionType.BAN), AccountStatus.BANNED),
            Map.entry(new TransitionCaseKey(AccountStatus.REINSTATED, ModerationActionType.BAN), AccountStatus.BANNED),
            Map.entry(new TransitionCaseKey(AccountStatus.FLAGGED, ModerationActionType.CLEAR), AccountStatus.ACTIVE),
            Map.entry(new TransitionCaseKey(AccountStatus.ACTIVE, ModerationActionType.NOTE), AccountStatus.ACTIVE),
            Map.entry(new TransitionCaseKey(AccountStatus.FLAGGED, ModerationActionType.NOTE), AccountStatus.FLAGGED),
            Map.entry(new TransitionCaseKey(AccountStatus.BANNED, ModerationActionType.NOTE), AccountStatus.BANNED),
            Map.entry(new TransitionCaseKey(AccountStatus.APPEALED, ModerationActionType.NOTE), AccountStatus.APPEALED),
            Map.entry(new TransitionCaseKey(AccountStatus.REINSTATED, ModerationActionType.NOTE), AccountStatus.REINSTATED),
            Map.entry(new TransitionCaseKey(AccountStatus.BANNED, ModerationActionType.APPEAL_SUBMITTED), AccountStatus.APPEALED),
            Map.entry(new TransitionCaseKey(AccountStatus.APPEALED, ModerationActionType.APPEAL_UPHELD), AccountStatus.BANNED),
            Map.entry(new TransitionCaseKey(AccountStatus.APPEALED, ModerationActionType.APPEAL_OVERTURNED), AccountStatus.REINSTATED)
    );

    private record TransitionCaseKey(AccountStatus from, ModerationActionType action) {}

    static Stream<TransitionCase> allPairs() {
        return Stream.of(AccountStatus.values())
                .flatMap(status -> Stream.of(ModerationActionType.values())
                        .map(action -> new TransitionCase(
                                status,
                                action,
                                VALID_TRANSITIONS.get(new TransitionCaseKey(status, action)))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allPairs")
    void testsAll35ActionStatusPairs(TransitionCase tc) {
        if (tc.expectedTo() != null) {
            assertThat(stateMachine.next(tc.from(), tc.action())).isEqualTo(tc.expectedTo());
        } else {
            assertThatThrownBy(() -> stateMachine.next(tc.from(), tc.action()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }
    }
}
