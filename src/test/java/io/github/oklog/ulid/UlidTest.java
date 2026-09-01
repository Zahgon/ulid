package io.github.oklog.ulid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Port of the unit tests in ulid_test.go. */
class UlidTest {

    /** The Go zero {@code time.Time}, which is January 1 of year 1. */
    private static final Instant GO_ZERO_TIME = Instant.ofEpochSecond(-62135596800L);

    @FunctionalInterface
    private interface Factory {
        Ulid create(long ms, InputStream entropy) throws IOException;
    }

    /** The shared assertions Go runs against both New and MustNew. */
    private static void assertFactory(Factory mk) throws Exception {
        final byte[] want = new byte[Ulid.SIZE];
        want[3] = 0x01;
        want[4] = (byte) 0x86;
        want[5] = (byte) 0xA0;

        // Entropy is optional.
        assertEquals(new Ulid(want), mk.create(100_000, null));

        final byte[] entropy = new byte[16];
        Arrays.fill(entropy, (byte) 0xFF);
        System.arraycopy(entropy, 0, want, 6, 10);
        assertEquals(new Ulid(want), mk.create(100_000, new ByteArrayInputStream(entropy)));
    }

    @Nested
    @DisplayName("create")
    class Create {
        @Test
        void buildsTheExpectedUlid() throws Exception {
            assertFactory(Ulid::create);
        }

        @Test
        void rejectsATimestampBeyondMaxTime() {
            final UlidException e =
                    assertThrows(UlidException.class, () -> Ulid.create(Ulid.maxTime() + 1, null));
            assertEquals(UlidException.Kind.BIG_TIME, e.kind());
        }

        @Test
        void reportsAnExhaustedEntropySource() {
            assertThrows(EOFException.class,
                    () -> Ulid.create(0, new ByteArrayInputStream(new byte[0])));
        }
    }

    @Nested
    @DisplayName("mustCreate")
    class MustCreate {
        @Test
        void buildsTheExpectedUlid() throws Exception {
            assertFactory(Ulid::mustCreate);
        }

        @Test
        void wrapsAnExhaustedEntropySource() {
            final UncheckedIOException e = assertThrows(UncheckedIOException.class,
                    () -> Ulid.mustCreate(0, new ByteArrayInputStream(new byte[0])));
            assertInstanceOf(EOFException.class, e.getCause());
        }
    }

    @Nested
    @DisplayName("mustCreateDefault")
    class MustCreateDefault {
        @Test
        void roundTripsThroughItsStringForm() throws Exception {
            final Ulid id = Ulid.mustCreateDefault(Instant.now());
            assertEquals(id, Ulid.parse(id.toString()));
        }

        @Test
        void rejectsATimeBeyondMaxTime() {
            final UncheckedIOException e =
                    assertThrows(UncheckedIOException.class, () -> Ulid.mustCreateDefault(GO_ZERO_TIME));
            assertEquals(UlidException.Kind.BIG_TIME,
                    assertInstanceOf(UlidException.class, e.getCause()).kind());
        }
    }

    @Test
    @DisplayName("make round trips through its string form")
    void make() throws Exception {
        final Ulid id = Ulid.make();
        assertEquals(id, Ulid.parse(id.toString()));
    }

    @Test
    @DisplayName("mustParse and mustParseStrict wrap a size failure")
    void mustParseWrapsFailures() {
        for (Runnable parse : java.util.List.<Runnable>of(
                () -> Ulid.mustParse(""), () -> Ulid.mustParseStrict(""))) {
            final UncheckedIOException e = assertThrows(UncheckedIOException.class, parse::run);
            assertEquals(UlidException.Kind.DATA_SIZE,
                    assertInstanceOf(UlidException.class, e.getCause()).kind());
        }
    }

    @Nested
    @DisplayName("marshaling errors")
    class MarshalingErrors {
        private final Ulid id = new Ulid();

        @Test
        void unmarshalBinaryRejectsTheWrongSize() {
            assertEquals(UlidException.Kind.DATA_SIZE,
                    assertThrows(UlidException.class, () -> id.unmarshalBinary(new byte[0])).kind());
        }

        @Test
        void unmarshalTextRejectsTheWrongSize() {
            assertEquals(UlidException.Kind.DATA_SIZE,
                    assertThrows(UlidException.class, () -> id.unmarshalText(new byte[0])).kind());
        }

        @Test
        void marshalBinaryToRejectsTheWrongSize() {
            assertEquals(UlidException.Kind.BUFFER_SIZE,
                    assertThrows(UlidException.class, () -> id.marshalBinaryTo(new byte[0])).kind());
        }

        @Test
        void marshalTextToRejectsTheWrongSize() {
            assertEquals(UlidException.Kind.BUFFER_SIZE,
                    assertThrows(UlidException.class, () -> id.marshalTextTo(new byte[0])).kind());
        }
    }

    @Test
    @DisplayName("parseStrict rejects an invalid byte at every position")
    void parseStrictInvalidCharacters() {
        final String base = "0000XSNJG0MQJHBF4QX1EFD6Y3";

        for (int i = 0; i < Ulid.ENCODED_SIZE; i++) {
            for (char corruption : new char[] {0x00FF, 0x0000}) {
                final char[] input = base.toCharArray();
                input[i] = corruption;

                final UlidException e = assertThrows(UlidException.class,
                        () -> Ulid.parseStrict(new String(input)),
                        String.format("0x%02X at index %d", (int) corruption, i));
                assertEquals(UlidException.Kind.INVALID_CHARACTERS, e.kind());
            }
        }
    }

    @Test
    @DisplayName("matches the reference implementation of alizain/ulid")
    void alizainCompatibility() throws Exception {
        final Ulid got = Ulid.mustCreate(1469918176385L, new ByteArrayInputStream(new byte[16]));
        assertEquals(Ulid.mustParse("01ARYZ6S410000000000000000"), got);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "00000000000000000000000000, ",
            "70000000000000000000000000, ",
            "7ZZZZZZZZZZZZZZZZZZZZZZZZZ, ",
            "80000000000000000000000000, OVERFLOW",
            "80000000000000000000000001, OVERFLOW",
            "ZZZZZZZZZZZZZZZZZZZZZZZZZZ, OVERFLOW",
    })
    @DisplayName("parse detects first-character overflow")
    void overflowHandling(String input, String expected) throws Exception {
        if (expected == null) {
            Ulid.parse(input);
            return;
        }
        assertEquals(UlidException.Kind.valueOf(expected),
                assertThrows(UlidException.class, () -> Ulid.parse(input)).kind());
    }

    @Test
    @DisplayName("now precedes a timestamp taken a millisecond later")
    void now() {
        final long before = Ulid.now();
        final long after = Ulid.timestamp(Instant.now().plusMillis(1));
        assertTrue(before < after, "clock went mad: before " + before + ", after " + after);
    }

    @Test
    @DisplayName("timestamp truncates to milliseconds and survives maxTime")
    void timestamp() {
        // 1 second plus 1000 nanoseconds, where the sub-millisecond part is dropped.
        assertEquals(1000L, Ulid.timestamp(Instant.ofEpochSecond(1, 1000)));

        final long max = Ulid.maxTime();
        final Instant at = Instant.ofEpochSecond(max / 1000, (max % 1000) * 1_000_000);
        assertEquals(max, Ulid.timestamp(at));
    }

    @Test
    @DisplayName("a time survives a conversion to milliseconds and back")
    void time() {
        final Instant original = Instant.now();
        final Duration diff = Duration.between(Ulid.time(Ulid.timestamp(original)), original);
        assertTrue(diff.compareTo(Duration.ofMillis(1)) < 0,
                "difference between original and recovered time (" + diff + ") is at least a millisecond");
    }

    @Nested
    @DisplayName("the time component")
    class TimeComponent {
        @Test
        void rejectsATimestampBeyondMaxTime() {
            final Ulid id = new Ulid();
            assertEquals(UlidException.Kind.BIG_TIME,
                    assertThrows(UlidException.class, () -> id.setTime(Ulid.maxTime() + 1)).kind());
        }

        @Test
        void roundTripsForArbitraryTimestamps() throws Exception {
            final Random rng = new Random(1469918176385L);
            final long max = Ulid.maxTime();

            for (int i = 0; i < 100_000; i++) {
                final long ms = rng.nextLong(max);
                final Ulid id = new Ulid();
                id.setTime(ms);
                assertEquals(ms, id.time(), "for " + id);
            }
        }

        @Test
        void agreesWithTheStandaloneConversion() {
            final Ulid id = Ulid.make();
            assertEquals(Ulid.time(id.time()), id.timestamp());
        }

        @Test
        void preservesTheOriginalInstantToMillisecondPrecision() {
            final Instant now = Instant.now();
            final Ulid id = Ulid.mustCreate(Ulid.timestamp(now), Ulid.defaultEntropy());
            assertEquals(now.truncatedTo(ChronoUnit.MILLIS), id.timestamp());
        }
    }

    @Test
    @DisplayName("isZero distinguishes the zero value")
    void zero() {
        assertTrue(new Ulid().isZero(), "must be true for zero-value ULIDs");
        assertFalse(Ulid.mustCreate(Ulid.now(), Ulid.defaultEntropy()).isZero(),
                "must be false for non-zero-value ULIDs");
    }

    @Nested
    @DisplayName("the entropy component")
    class EntropyComponent {
        @Test
        void rejectsTheWrongSize() {
            final Ulid id = new Ulid();
            assertEquals(UlidException.Kind.DATA_SIZE,
                    assertThrows(UlidException.class, () -> id.setEntropy(new byte[0])).kind());
        }

        @Test
        void roundTripsForArbitraryBytes() throws Exception {
            final Random rng = new Random(20160730L);

            for (int i = 0; i < 100_000; i++) {
                final byte[] want = new byte[10];
                rng.nextBytes(want);

                final Ulid id = new Ulid();
                id.setEntropy(want);
                assertArrayEquals(want, id.entropy());
            }
        }

        @Test
        void isReadInFullFromAFlakySource() throws Exception {
            final Random rng = new Random(20160731L);

            for (int i = 0; i < 10_000; i++) {
                final byte[] want = new byte[10];
                rng.nextBytes(want);

                final InputStream flaky =
                        new TestSupport.HalfInputStream(new ByteArrayInputStream(want));
                assertArrayEquals(want, Ulid.create(Ulid.now(), flaky).entropy());
            }
        }
    }

    @Nested
    @DisplayName("scan")
    class Scan {
        private final Ulid id = Ulid.mustCreate(123, TestSupport.cryptoStream());

        @Test
        void acceptsAString() throws Exception {
            final Ulid out = new Ulid();
            out.scan(id.toString());
            assertEquals(id, out);
        }

        @Test
        void acceptsTheBinaryForm() throws Exception {
            final Ulid out = new Ulid();
            out.scan(id.bytes());
            assertEquals(id, out);
        }

        @Test
        void acceptsTheTextFormAsBytes() throws Exception {
            final Ulid out = new Ulid();
            out.scan(id.marshalText());
            assertEquals(id, out);
        }

        @Test
        void leavesTheUlidUntouchedForNull() throws Exception {
            final Ulid out = new Ulid();
            out.scan(null);
            assertEquals(new Ulid(), out);
        }

        @Test
        void rejectsAnyOtherType() {
            final Ulid out = new Ulid();
            assertEquals(UlidException.Kind.SCAN_VALUE,
                    assertThrows(UlidException.class, () -> out.scan(44)).kind());
            assertEquals(new Ulid(), out);
        }

        @Test
        void rejectsBytesOfTheWrongLength() {
            final Ulid out = new Ulid();
            assertEquals(UlidException.Kind.DATA_SIZE,
                    assertThrows(UlidException.class, () -> out.scan(new byte[7])).kind());
        }

        @Test
        void valueRoundTripsThroughScan() throws Exception {
            final Ulid out = new Ulid();
            out.scan(id.value());
            assertEquals(id, out);
        }
    }

    @Test
    @DisplayName("bytes returns a copy, not the backing array")
    void bytesReturnsACopy() {
        final Ulid id = Ulid.mustCreate(
                Ulid.timestamp(Instant.ofEpochSecond(1_000_000)), Ulid.defaultEntropy());

        final byte[] copy = id.bytes();
        copy[copy.length - 1]++;

        assertFalse(Arrays.equals(id.bytes(), copy), "bytes() returned a reference to the ULID's array");
    }

    @Test
    @DisplayName("copy is independent of its source")
    void copyIsIndependent() throws Exception {
        final Ulid id = Ulid.make();
        final Ulid duplicate = id.copy();
        assertEquals(id, duplicate);

        duplicate.setTime(0);
        assertNotEquals(id, duplicate);
    }

    @Test
    @DisplayName("equal ULIDs share a hash code")
    void hashCodeAgreesWithEquals() throws Exception {
        final Ulid id = Ulid.make();
        assertEquals(id.hashCode(), Ulid.parse(id.toString()).hashCode());
    }
}
