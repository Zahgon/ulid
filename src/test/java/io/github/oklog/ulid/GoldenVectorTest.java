package io.github.oklog.ulid;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies this port against golden vectors produced by the original Go
 * implementation. Any divergence in the base32 codec, the monotonic increment
 * arithmetic or the timestamp conversion shows up here.
 */
@DisplayName("golden vectors from the Go implementation")
class GoldenVectorTest {

    private static final HexFormat HEX = HexFormat.of();

    private static List<String[]> vectors;

    @BeforeAll
    static void loadVectors() throws IOException {
        try (InputStream in = GoldenVectorTest.class.getResourceAsStream("/vectors.tsv")) {
            if (in == null) {
                throw new IOException("vectors.tsv is missing from the test resources");
            }
            vectors = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .filter(line -> !line.isEmpty())
                    .map(line -> line.split("\t", -1))
                    .collect(Collectors.toList());
        }
    }

    private static List<String[]> of(String kind) {
        return vectors.stream().filter(v -> v[0].equals(kind)).toList();
    }

    private static String[] single(String kind) {
        final List<String[]> found = of(kind);
        assertEquals(1, found.size(), "expected exactly one " + kind + " vector");
        return found.get(0);
    }

    @TestFactory
    @DisplayName("encoding a timestamp and entropy reproduces the Go string")
    Stream<DynamicTest> encode() {
        return of("encode").stream().map(v -> {
            final long ms = Long.parseUnsignedLong(v[1]);
            final byte[] entropy = HEX.parseHex(v[2]);
            final String want = v[3];

            return DynamicTest.dynamicTest("ms=" + v[1] + " entropy=" + v[2], () -> {
                final Ulid id = Ulid.create(ms, new ByteArrayInputStream(entropy));
                assertEquals(want, id.toString(), "encoded form");

                // The vector also pins down the component accessors and a round trip.
                assertEquals(ms, id.time(), "decoded timestamp");
                assertEquals(v[2], HEX.formatHex(id.entropy()), "decoded entropy");
                assertEquals(id, Ulid.parse(want), "parse of the encoded form");
                assertEquals(id, Ulid.parseStrict(want), "strict parse of the encoded form");
            });
        });
    }

    @TestFactory
    @DisplayName("parse agrees with Go, including on malformed input")
    Stream<DynamicTest> parse() {
        return parseVectors("parse", false);
    }

    @TestFactory
    @DisplayName("parseStrict agrees with Go, including on malformed input")
    Stream<DynamicTest> parseStrict() {
        return parseVectors("parsestrict", true);
    }

    private Stream<DynamicTest> parseVectors(String kind, boolean strict) {
        return of(kind).stream().map(v -> {
            final String input = new String(HEX.parseHex(v[1]), StandardCharsets.ISO_8859_1);
            final String wantError = v[2];
            final String wantBytes = v[3];

            return DynamicTest.dynamicTest(kind + " " + v[1], () -> {
                final Ulid[] result = new Ulid[1];
                final String error = captureError(
                        () -> result[0] = strict ? Ulid.parseStrict(input) : Ulid.parse(input));

                assertEquals(wantError, error, "error for input " + v[1]);
                if (wantError.equals("OK")) {
                    assertEquals(wantBytes, HEX.formatHex(result[0].bytes()), "decoded bytes");
                }
            });
        });
    }

    @TestFactory
    @DisplayName("monotonic sequences match Go byte for byte")
    Stream<DynamicTest> monotonic() {
        // Vectors for one increment ceiling form a single ordered run against one
        // entropy instance, so they have to be replayed together and in order.
        final Map<String, List<String[]>> byIncrement = new LinkedHashMap<>();
        for (String[] v : of("mono")) {
            byIncrement.computeIfAbsent(v[1], k -> new ArrayList<>()).add(v);
        }

        return byIncrement.entrySet().stream().map(entry -> DynamicTest.dynamicTest(
                "inc=" + entry.getKey(),
                () -> {
                    final MonotonicEntropy entropy =
                            Ulid.monotonic(new TestSupport.DetInputStream(), Long.parseUnsignedLong(entry.getKey()));

                    for (String[] v : entry.getValue()) {
                        final long ms = Long.parseUnsignedLong(v[2]);
                        final String index = v[3];
                        final String wantError = v[4];
                        final String wantString = v[5];

                        final Ulid[] result = new Ulid[1];
                        final String error = captureError(() -> result[0] = Ulid.create(ms, entropy));

                        assertEquals(wantError, error, "error at index " + index);
                        if (wantError.equals("OK")) {
                            assertEquals(wantString, result[0].toString(), "ULID at index " + index);
                        }
                    }
                }));
    }

    @Test
    @DisplayName("monotonic entropy overflows exactly where Go does")
    void monotonicOverflow() throws IOException {
        final String[] v = single("monooverflow");

        final byte[] ones = new byte[10];
        java.util.Arrays.fill(ones, (byte) 0xFF);
        final MonotonicEntropy entropy = Ulid.monotonic(
                new SequenceInputStream(new ByteArrayInputStream(ones), new TestSupport.DetInputStream()),
                0);

        final Ulid first = Ulid.create(0, entropy);
        assertEquals(v[1], first.toString(), "first ULID");
        assertEquals("OK", v[2], "vector expects the first read to succeed");

        final UlidException thrown =
                assertThrows(UlidException.class, () -> Ulid.create(first.time(), entropy));
        assertEquals(v[3], goErrorName(thrown.kind()), "second read");
    }

    @Test
    @DisplayName("the documented example string is reproduced exactly")
    void example() throws IOException {
        final String[] v = single("example");

        // The entropy bytes are the ones Go's math/rand produced for the example, so
        // the string can be reproduced verbatim without porting Go's generator.
        final Ulid id = Ulid.create(
                Long.parseUnsignedLong(v[1]), new ByteArrayInputStream(HEX.parseHex(v[2])));

        assertEquals(v[3], id.toString());
        assertEquals("0000XSNJG0MQJHBF4QX1EFD6Y3", id.toString());
    }

    @TestFactory
    @DisplayName("millisecond to instant conversion matches Go")
    Stream<DynamicTest> timeConversion() {
        return of("time").stream().map(v -> DynamicTest.dynamicTest("ms=" + v[1], () -> {
            final long ms = Long.parseUnsignedLong(v[1]);
            final Instant instant = Ulid.time(ms);

            assertEquals(Long.parseLong(v[2]), instant.getEpochSecond(), "epoch second");
            assertEquals(Integer.parseInt(v[3]), instant.getNano(), "nanosecond");
            assertEquals(Long.parseUnsignedLong(v[4]), Ulid.timestamp(instant), "round trip");
        }));
    }

    @Test
    @DisplayName("maxTime matches Go")
    void maxTime() {
        assertEquals(Long.parseUnsignedLong(single("maxtime")[1]), Ulid.maxTime());
    }

    // ------------------------------------------------------------------

    private interface Action {
        void run() throws IOException;
    }

    /** Runs {@code action} and reports its outcome using the Go implementation's error names. */
    private static String captureError(Action action) {
        try {
            action.run();
            return "OK";
        } catch (UlidException e) {
            return goErrorName(e.kind());
        } catch (EOFException e) {
            return "EOF";
        } catch (UncheckedIOException e) {
            return fail("unexpected unchecked failure", e);
        } catch (IOException e) {
            return "Err:" + e.getMessage();
        }
    }

    private static String goErrorName(UlidException.Kind kind) {
        return switch (kind) {
            case DATA_SIZE -> "ErrDataSize";
            case INVALID_CHARACTERS -> "ErrInvalidCharacters";
            case BUFFER_SIZE -> "ErrBufferSize";
            case BIG_TIME -> "ErrBigTime";
            case OVERFLOW -> "ErrOverflow";
            case MONOTONIC_OVERFLOW -> "ErrMonotonicOverflow";
            case SCAN_VALUE -> "ErrScanValue";
        };
    }
}
