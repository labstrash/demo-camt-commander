package com.example.commander.domain.pht;

import java.util.List;
import java.util.Objects;

/**
 * A parsed inbound PHT balance message.
 *
 * <p>The wire format is semicolon-delimited: a six-field header
 * ({@code messageLength;versionNumber;messageDate;messageTime;accountOwner;accountCount})
 * followed by {@code accountCount} quadruplets of {@code
 * (clearingNumber;accountNumber;balance;settlementAmount)}.
 * Every header field is kept as PHT's own string representation — none of them are
 * parsed/interpreted here (in particular, {@code messageDate}/{@code messageTime} are not
 * converted to a temporal type; nothing downstream needs them as one).
 *
 * @param messageLength PHT's own message-length field, as sent
 * @param versionNumber PHT's message format version, as sent
 * @param messageDate PHT's message date, as sent
 * @param messageTime PHT's message time, as sent
 * @param accountOwner the external engagement identifier this message's accounts belong to
 *     ({@code CAMT.Agreement.EngagementId})
 * @param accounts the parsed account balances; its size is what PHT's {@code accountCount}
 *     header field is validated against during parsing — not stored separately here, so the
 *     two can never diverge post-parse
 */
public record PhtBalanceMessage(
        String messageLength,
        String versionNumber,
        String messageDate,
        String messageTime,
        String accountOwner,
        List<PhtAccountBalance> accounts) {

    public PhtBalanceMessage {
        Objects.requireNonNull(messageLength, "messageLength");
        Objects.requireNonNull(versionNumber, "versionNumber");
        Objects.requireNonNull(messageDate, "messageDate");
        Objects.requireNonNull(messageTime, "messageTime");
        Objects.requireNonNull(accountOwner, "accountOwner");
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
    }
}
