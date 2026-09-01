package io.github.oklog.ulid;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Random;

/** Shared fixtures for the ported test suite. */
final class TestSupport {

    private TestSupport() {
    }

    /**
     * A deterministic, chunk-size independent byte source: the byte at stream
     * position {@code i} is always {@code (i * 167 + 13) & 0xFF}. It mirrors the
     * generator used to produce the golden vectors, so the Java and Go monotonic
     * paths consume an identical stream.
     *
     * <p>Deliberately not a {@link RandomInputStream}, so that
     * {@link MonotonicEntropy} takes the byte-reading increment path.
     */
    static final class DetInputStream extends InputStream {
        private int position;

        @Override
        public int read() {
            return (position++ * 167 + 13) & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            for (int i = 0; i < len; i++) {
                b[off + i] = (byte) ((position++ * 167 + 13) & 0xFF);
            }
            return len;
        }
    }

    /** Returns at most half the requested bytes per call, like Go's {@code iotest.HalfReader}. */
    static final class HalfInputStream extends InputStream {
        private final InputStream in;

        HalfInputStream(InputStream in) {
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            return in.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return in.read(b, off, (len + 1) / 2);
        }
    }

    /**
     * A cryptographic byte source that is <em>not</em> a {@link RandomInputStream},
     * standing in for Go's {@code crypto/rand.Reader}. This matters: it forces
     * {@link MonotonicEntropy} down the byte-reading increment path rather than the
     * generator fast path, exactly as the Go tests do.
     */
    static InputStream cryptoStream() {
        final SecureRandom secure = new SecureRandom();
        return new InputStream() {
            @Override
            public int read() {
                final byte[] one = new byte[1];
                secure.nextBytes(one);
                return one[0] & 0xFF;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                final byte[] chunk = new byte[len];
                secure.nextBytes(chunk);
                System.arraycopy(chunk, 0, b, off, len);
                return len;
            }
        };
    }

    /** A source backed by {@link Random}, which does take the generator fast path. */
    static InputStream mathStream(long seed) {
        return new RandomInputStream(new Random(seed));
    }

    /** Builds a ULID from 16 random bytes, standing in for Go's {@code quick.Check} generation. */
    static Ulid randomUlid(Random rnd) {
        final byte[] bytes = new byte[Ulid.SIZE];
        rnd.nextBytes(bytes);
        try {
            return new Ulid(bytes);
        } catch (UlidException e) {
            throw new AssertionError("16 random bytes are always a valid ULID", e);
        }
    }
}
