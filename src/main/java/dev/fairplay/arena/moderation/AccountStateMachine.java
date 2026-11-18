package dev.fairplay.arena.moderation;

import dev.fairplay.arena.common.error.ApiException;
import dev.fairplay.arena.common.error.ErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class AccountStateMachine {

    private static final Map<ModerationActionType, Set<AccountStatus>> ALLOWED_FROM =
            new EnumMap<>(ModerationActionType.class);

    private static final Map<ModerationActionType, AccountStatus> TARGETS =
            new EnumMap<>(ModerationActionType.class);

    static {
        ALLOWED_FROM.put(ModerationActionType.FLAG, EnumSet.of(AccountStatus.ACTIVE, AccountStatus.REINSTATED));
        ALLOWED_FROM.put(ModerationActionType.BAN, EnumSet.of(AccountStatus.ACTIVE, AccountStatus.FLAGGED, AccountStatus.REINSTATED));
        ALLOWED_FROM.put(ModerationActionType.CLEAR, EnumSet.of(AccountStatus.FLAGGED));
        ALLOWED_FROM.put(ModerationActionType.NOTE, EnumSet.allOf(AccountStatus.class));
        ALLOWED_FROM.put(ModerationActionType.APPEAL_SUBMITTED, EnumSet.of(AccountStatus.BANNED));
        ALLOWED_FROM.put(ModerationActionType.APPEAL_UPHELD, EnumSet.of(AccountStatus.APPEALED));
        ALLOWED_FROM.put(ModerationActionType.APPEAL_OVERTURNED, EnumSet.of(AccountStatus.APPEALED));

        TARGETS.put(ModerationActionType.FLAG, AccountStatus.FLAGGED);
        TARGETS.put(ModerationActionType.BAN, AccountStatus.BANNED);
        TARGETS.put(ModerationActionType.CLEAR, AccountStatus.ACTIVE);
        TARGETS.put(ModerationActionType.APPEAL_SUBMITTED, AccountStatus.APPEALED);
        TARGETS.put(ModerationActionType.APPEAL_UPHELD, AccountStatus.BANNED);
        TARGETS.put(ModerationActionType.APPEAL_OVERTURNED, AccountStatus.REINSTATED);
    }

    public AccountStatus next(AccountStatus current, ModerationActionType action) {
        if (current == null || action == null) {
            throw new ApiException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Cannot apply " + action + " to a player in status " + current);
        }
        Set<AccountStatus> allowed = ALLOWED_FROM.get(action);
        if (allowed == null || !allowed.contains(current)) {
            throw new ApiException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Cannot apply " + action + " to a player in status " + current);
        }
        if (action == ModerationActionType.NOTE) {
            return current;
        }
        return TARGETS.get(action);
    }
}
