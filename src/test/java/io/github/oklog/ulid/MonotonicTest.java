package io.github.oklog.ulid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Port of the monotonic entropy tests in ulid_test.go. */
class MonotonicTest {

    private static final int SEQUENCE_LENGTH = 10_000;

    static Stream<Arguments> sources() {
        final long seed = Ulid.now();
        final List<Arguments> combinations = new ArrayList<>();

        final Supplier<InputStream> crypto = TestSupport::cryptoStream;
        final Supplier<InputStream> math = () -> TestSupport.mathStream(seed);

        for (long inc : new long[] {0, 1, 2, 256, 65_536, 4_294_967_296L}) {
            combinations.add(Arguments.of("cryptorand", crypto, inc));
            combinations.add(Arguments.of("mathrand", math, inc));
        }
        return combinations.stream();
    }

    @ParameterizedTest(name = "entropy={0} inc={2}")
    @MethodSource("sources")
    @DisplayName("entropy strictly increases within one millisecond")
    void monotonic(String name, Supplier<InputStream> source, long inc) throws IOException {
        final MonotonicEntropy entropy = Ulid.monotonic(source.get(), inc);

        Ulid previous = new Ulid();
        for (int i = 0; i < SEQUENCE_LENGTH; i++) {
            final Ulid next = Ulid.create(123, entropy);
            final Ulid current = previous;

            assertTrue(current.compareTo(next) < 0, () -> String.format(
                    "previous %d %s is not below next %d %s",
                    current.time(), hex(current.entropy()), next.time(), hex(next.entropy())));

            previous = next;
        }
    }

    @Test
    @DisplayName("incrementing past the entropy space reports an overflow")
    void monotonicOverflow() throws IOException {
        final byte[] ones = new byte[10];
        Arrays.fill(ones, (byte) 0xFF);

        // The first ULID consumes the all-ones entropy, so the next one in the same
        // millisecond has nowhere left to grow.
        final MonotonicEntropy entropy = Ulid.monotonic(
                new SequenceInputStream(new ByteArrayInputStream(ones), TestSupport.cryptoStream()), 0);

        final Ulid previous = Ulid.create(0, entropy);

        final UlidException e =
                assertThrows(UlidException.class, () -> Ulid.create(previous.time(), entropy));
        assertEquals(UlidException.Kind.MONOTONIC_OVERFLOW, e.kind());
    }

    @Test
    @DisplayName("a locked reader stays monotonic under concurrency")
    void monotonicSafe() throws Exception {
        final int threads = 100;
        final int perThread = 1024;

        final LockedMonotonicReader safe = new LockedMonotonicReader(
                Ulid.monotonic(TestSupport.mathStream(System.nanoTime()), 0));
        final long t0 = Ulid.timestamp(java.time.Instant.now());

        final List<Callable<Void>> tasks = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                Ulid previous = Ulid.mustCreate(t0, safe);
                for (int j = 0; j < perThread; j++) {
                    final Ulid next = Ulid.mustCreate(t0, safe);
                    if (previous.toString().compareTo(next.toString()) >= 0) {
                        throw new AssertionError(String.format(
                                "%s (%d %s) >= %s (%d %s)",
                                previous, previous.time(), hex(previous.entropy()),
                                next, next.time(), hex(next.entropy())));
                    }
                    previous = next;
                }
                return null;
            });
        }

        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (Future<Void> result : pool.invokeAll(tasks)) {
                // Surfaces any AssertionError raised inside a worker.
                result.get();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("a fresh source is read in full rather than incremented")
    void firstReadUsesTheSource() throws IOException {
        final byte[] fixed = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        final MonotonicEntropy entropy = Ulid.monotonic(new ByteArrayInputStream(fixed), 0);

        org.junit.jupiter.api.Assertions.assertArrayEquals(
                fixed, Ulid.create(123, entropy).entropy());
    }

    @Test
    @DisplayName("a new millisecond re-reads the source instead of incrementing")
    void timestampChangeResetsEntropy() throws IOException {
        final byte[] first = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        final byte[] second = {(byte) 0xAA, 0, 0, 0, 0, 0, 0, 0, 0, 1};

        final InputStream source = new SequenceInputStream(
                new ByteArrayInputStream(first), new ByteArrayInputStream(second));
        final MonotonicEntropy entropy = Ulid.monotonic(source, 0);

        org.junit.jupiter.api.Assertions.assertArrayEquals(
                first, Ulid.create(123, entropy).entropy());
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                second, Ulid.create(124, entropy).entropy());
    }

    @Test
    @DisplayName("an increment of 1 advances the entropy by exactly one")
    void incrementOfOne() throws IOException {
        // A zero entropy reads as "unset" and forces a fresh read, so the source has
        // to start from something non-zero for the increment path to be exercised.
        final byte[] start = new byte[10];
        start[9] = 1;
        final MonotonicEntropy entropy = Ulid.monotonic(new ByteArrayInputStream(start), 1);

        assertEquals(1L, toLong(Ulid.create(7, entropy).entropy()));
        assertEquals(2L, toLong(Ulid.create(7, entropy).entropy()));
        assertEquals(3L, toLong(Ulid.create(7, entropy).entropy()));
    }

    private static long toLong(byte[] entropy) {
        long v = 0;
        for (byte b : entropy) {
            v = (v << 8) | (b & 0xFF);
        }
        return v;
    }

    private static String hex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }
}
