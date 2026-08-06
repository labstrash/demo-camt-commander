package com.example.commander.service;

import com.example.commander.domain.pht.PhtAccountBalance;
import com.example.commander.domain.pht.PhtBalanceMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Parses an inbound PHT balance message's semicolon-delimited wire format into a {@link
 * PhtBalanceMessage}.
 *
 * <p>Format: a six-field header ({@code
 * messageLength;versionNumber;messageDate;messageTime;accountOwner;accountCount}) followed by
 * {@code accountCount} quadruplets of {@code
 * (clearingNumber;accountNumber;balance;settlementAmount)}. Delimiter-split, but each
 * individual field can itself carry fixed-width space padding — every field is trimmed after
 * splitting so a padded {@code accountCount}/{@code messageDate}/{@code messageTime} still
 * parses, a padded {@code accountOwner}/{@code clearingNumber}/{@code accountNumber} still
 * matches the corresponding unpadded database value, and a padded {@code balance}/{@code
 * settlementAmount} still matches byte-for-byte between messages.
 *
 * <p>Pure Java, no database dependency — easily testable without a database or Spring
 * context, same posture as {@code ReportMessageAssembler}.
 */
@Component
public class PhtMessageParser {

    private static final int HEADER_FIELD_COUNT = 6;
    private static final int ACCOUNT_FIELD_COUNT = 4;

    /**
     * Parses {@code rawMessage} into a {@link PhtBalanceMessage}.
     *
     * @param rawMessage the raw semicolon-delimited message body
     * @return the parsed message
     * @throws IllegalArgumentException if {@code rawMessage} is null/blank, has fewer than
     *     the six header fields, its {@code accountCount} field isn't a valid integer, or the
     *     total field count doesn't match {@code accountCount}
     */
    public PhtBalanceMessage parse(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            throw new IllegalArgumentException("PHT message body cannot be null or blank");
        }

        String[] fields = Arrays.stream(rawMessage.trim().split(";", -1))
                .map(String::trim)
                .toArray(String[]::new);
        if (fields.length < HEADER_FIELD_COUNT) {
            throw new IllegalArgumentException("PHT message has %d field(s), expected at least %d (header)"
                    .formatted(fields.length, HEADER_FIELD_COUNT));
        }

        String messageLength = fields[0];
        String versionNumber = fields[1];
        String messageDate = fields[2];
        String messageTime = fields[3];
        String accountOwner = fields[4];
        int accountCount = parseAccountCount(fields[5]);

        int expectedFieldCount = HEADER_FIELD_COUNT + (accountCount * ACCOUNT_FIELD_COUNT);
        if (fields.length != expectedFieldCount) {
            throw new IllegalArgumentException(
                    "PHT message declares accountCount=%d (expects %d field(s) total) but has %d field(s)"
                            .formatted(accountCount, expectedFieldCount, fields.length));
        }

        List<PhtAccountBalance> accounts = new ArrayList<>(accountCount);
        int index = HEADER_FIELD_COUNT;
        for (int i = 0; i < accountCount; i++) {
            String clearingNumber = fields[index++];
            String accountNumber = fields[index++];
            String balance = fields[index++];
            String settlementAmount = fields[index++];
            accounts.add(new PhtAccountBalance(clearingNumber, accountNumber, balance, settlementAmount));
        }

        return new PhtBalanceMessage(messageLength, versionNumber, messageDate, messageTime, accountOwner, accounts);
    }

    private static int parseAccountCount(String field) {
        try {
            return Integer.parseInt(field);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "PHT message accountCount field is not a valid integer: '" + field + "'", ex);
        }
    }
}
