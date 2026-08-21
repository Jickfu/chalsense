package io.github.chalsense.core.state;

import io.github.chalsense.protocol.VerificationTicket;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** The lowercase SHA-256 lookup digest of a verification ticket's decoded 32 raw bytes. */
public record TicketDigest(String hexValue) {
    private static final Pattern LOWERCASE_SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public TicketDigest {
        Objects.requireNonNull(hexValue, "hexValue");
        if (!LOWERCASE_SHA_256.matcher(hexValue).matches()) {
            throw new IllegalArgumentException("ticket digest must be lowercase SHA-256 hex");
        }
    }

    public static TicketDigest from(VerificationTicket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        byte[] rawTicket = Base64.getUrlDecoder().decode(ticket.value());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawTicket);
            return new TicketDigest(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        } finally {
            Arrays.fill(rawTicket, (byte) 0);
        }
    }

    @Override
    public String toString() {
        return "TicketDigest[REDACTED]";
    }
}
