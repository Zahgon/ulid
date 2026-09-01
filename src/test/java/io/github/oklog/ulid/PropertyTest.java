package io.github.oklog.ulid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Port of the {@code testing/quick} property tests in ulid_test.go. Go generates
 * inputs randomly on every run; here the seeds are fixed so that a failure can be
 * reproduced exactly.
 */
class PropertyTest {

    private static final int ITERATIONS = 100_000;

    @Test
    @DisplayName("a ULID survives every marshaling round trip")
    void roundTrips() throws Exception {
        final Random rng = new Random(20160730L);

        for (int i = 0; i < ITERATIONS; i++) {
            final Ulid id = TestSupport.randomUlid(rng);

            final Ulid fromBinary = new Ulid();
            fromBinary.unmarshalBinary(id.marshalBinary());

            final Ulid fromText = new Ulid();
            fromText.unmarshalText(id.marshalText());

            assertEquals(id, fromBinary, "binary round trip");
            assertEquals(id, fromText, "text round trip");
            assertEquals(id, Ulid.mustParse(id.toString()), "parse round trip");
            assertEquals(id, Ulid.mustParseStrict(id.toString()), "strict parse round trip");
        }
    }

    @Test
    @DisplayName("every encoded character comes from the alphabet")
    void encoding() {
        final Random rng = new Random(20160731L);
        final boolean[] allowed = new boolean[128];
        for (int i = 0; i < Ulid.ENCODING.length(); i++) {
            allowed[Ulid.ENCODING.charAt(i)] = true;
        }

        for (int i = 0; i < ITERATIONS; i++) {
            final String encoded = TestSupport.randomUlid(rng).toString();
            for (int j = 0; j < encoded.length(); j++) {
                final char c = encoded.charAt(j);
                assertTrue(c < 128 && allowed[c], "unexpected character " + c + " in " + encoded);
            }
        }
    }

    @Test
    @DisplayName("string order follows timestamp order")
    void lexicographicalOrder() {
        // Walk down from the upper boundary of the state space first.
        Ulid top = Ulid.mustCreate(Ulid.maxTime(), null);
        for (int i = 0; i < 10; i++) {
            final Ulid next = Ulid.mustCreate(top.time() - 1, null);
            assertOrdered(top, next);
            top = next;
        }

        final Random rng = new Random(20160801L);
        for (int i = 0; i < ITERATIONS; i++) {
            assertOrdered(TestSupport.randomUlid(rng), TestSupport.randomUlid(rng));
        }
    }

    private static void assertOrdered(Ulid a, Ulid b) {
        final long t1 = a.time();
        final long t2 = b.time();
        final String s1 = a.toString();
        final String s2 = b.toString();
        final int ord = a.compareTo(b);

        final boolean ok = t1 == t2
                || (t1 > t2 && s1.compareTo(s2) > 0 && ord == +1)
                || (t1 < t2 && s1.compareTo(s2) < 0 && ord == -1);

        assertTrue(ok, String.format(
                "bad lexicographical order: (%d, %s) vs (%d, %s), compare = %d", t1, s1, t2, s2, ord));
    }

    @Test
    @DisplayName("parsing is case insensitive")
    void caseInsensitivity() {
        final Random rng = new Random(20160802L);

        for (int i = 0; i < 10_000; i++) {
            final String encoded = TestSupport.randomUlid(rng).toString();
            assertEquals(Ulid.mustParse(encoded.toUpperCase(Locale.ROOT)),
                    Ulid.mustParse(encoded.toLowerCase(Locale.ROOT)));
        }
    }

    @Test
    @DisplayName("non-strict parsing never fails on well-sized input")
    void parseRobustness() throws Exception {
        final byte[] regression = {
                0x1, (byte) 0xc0, 0x73, 0x62, 0x4a, (byte) 0xaf, 0x39, 0x78, 0x51, 0x4e,
                (byte) 0xf8, 0x44, 0x3b, (byte) 0xb2, (byte) 0xa8, 0x59, (byte) 0xc7, 0x5f,
                (byte) 0xc3, (byte) 0xcc, 0x6a, (byte) 0xf2, 0x6d, 0x5a, (byte) 0xaa, 0x20,
        };
        Ulid.parse(new String(regression, StandardCharsets.ISO_8859_1));

        final Random rng = new Random(20160803L);
        for (int i = 0; i < 10_000; i++) {
            final byte[] input = new byte[Ulid.ENCODED_SIZE];
            rng.nextBytes(input);

            // Constrain the leading character so that it cannot overflow, which is
            // the same artificial narrowing the Go test applies.
            if ((input[0] & 0xFF) > '7') {
                input[0] = (byte) ((input[0] & 0xFF) % '7');
            }

            Ulid.parse(new String(input, StandardCharsets.ISO_8859_1));
        }
    }

    @Test
    @DisplayName("a timestamp survives a conversion to an instant and back")
    void timestampRoundTrips() {
        final Random rng = new Random(20160804L);

        for (int i = 0; i < ITERATIONS; i++) {
            // The full unsigned 64-bit range, matching Go's generator.
            final long ts = rng.nextLong();
            assertEquals(ts, Ulid.timestamp(Ulid.time(ts)),
                    "round trip of " + Long.toUnsignedString(ts));
        }
    }

    @Test
    @DisplayName("compare agrees with comparing the encoded strings")
    void compare() {
        final Random rng = new Random(20160805L);

        for (int i = 0; i < ITERATIONS; i++) {
            final Ulid a = TestSupport.randomUlid(rng);
            final Ulid b = TestSupport.randomUlid(rng);

            assertEquals(Integer.signum(a.toString().compareTo(b.toString())), a.compareTo(b),
                    a + " vs " + b);
        }
    }
}
