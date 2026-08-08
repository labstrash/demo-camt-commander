package com.example.commander.domain.ext;

import java.util.List;
import java.util.Objects;

/**
 * A parsed inbound EXT balance message.
 *
 * <p>The wire format is semicolon-delimited: a six-field header
 * ({@code messageLength;versionNumber;messageDate;messageTime;accountOwner;accountCount})
 * followed by {@code accountCount} quadruplets of {@code
 * (clearingNumber;accountNumber;balance;settlementAmount)}.
 * Every header field is kept as EXT's own string representation — none of them are
 * parsed/interpreted here (in particular, {@code messageDate}/{@code messageTime} are not
 * converted to a temporal type; nothing downstream needs them as one).
 *
 * @param messageLength EXT's own message-length field, as sent
 * @param versionNumber EXT's message format version, as sent
 * @param messageDate EXT's message date, as sent
 * @param messageTime EXT's message time, as sent
 * @param accountOwner the external engagement identifier this message's accounts belong to
 *     ({@code CAMT.Agreement.EngagementId})
 * @param accounts the parsed account balances; its size is what EXT's {@code accountCount}
 *     header field is validated against during parsing — not stored separately here, so the
 *     two can never diverge post-parse
 */
public record ExtBalanceMessage(
        String messageLength,
        String versionNumber,
        String messageDate,
        String messageTime,
        String accountOwner,
        List<ExtAccountBalance> accounts) {

    public ExtBalanceMessage {
        Objects.requireNonNull(messageLength, "messageLength");
        Objects.requireNonNull(versionNumber, "versionNumber");
        Objects.requireNonNull(messageDate, "messageDate");
        Objects.requireNonNull(messageTime, "messageTime");
        Objects.requireNonNull(accountOwner, "accountOwner");
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
    }
}
