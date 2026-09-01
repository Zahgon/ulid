/*
 * Copyright 2016 The Oklog Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.oklog.ulid;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Random;

/**
 * An entropy source that yields strictly increasing entropy bytes, to a limit
 * governed by the {@code inc} parameter.
 *
 * <p>Calls to {@link #monotonicRead} within the same ULID timestamp return entropy
 * incremented by a random number between 1 and {@code inc} inclusive. If an increment
 * results in entropy that would overflow the available space, {@link #monotonicRead}
 * throws {@link UlidException} with kind {@link UlidException.Kind#MONOTONIC_OVERFLOW}.
 *
 * <p>Passing {@code inc == 0} results in the reasonable default of 2<sup>32</sup>-1.
 * Lower values provide more monotonic entropy in a single millisecond, at the cost of
 * easier guessability of generated ULIDs. If your code depends on ULIDs having secure
 * entropy bytes then the secure default of {@code inc == 0} is recommended, unless you
 * know what you are doing.
 *
 * <p>The provided entropy source must actually yield random bytes. Otherwise monotonic
 * reads are not guaranteed to terminate, since there is not enough randomness to compute
 * an increment number.
 *
 * <p>This type is not safe for concurrent use; wrap it in a {@link LockedMonotonicReader}
 * if you need that.
 */
public final class MonotonicEntropy extends InputStream implements MonotonicReader {

    /** The default increment ceiling, used when {@code inc == 0} is requested. */
    private static final long MAX_UINT32 = 0xFFFFFFFFL;

    private final InputStream reader;
    private final long inc;

    /** Fast path for sources backed directly by a {@link Random}. */
    private final Random rng;

    private final Uint80 entropy = new Uint80();

    /**
     * Scratch space for {@link #random()}. Deliberately long-lived: the unrolled
     * conversion below may widen the freshly read prefix with bytes left over from
     * an earlier iteration, and the Go implementation behaves the same way.
     */
    private final byte[] rand = new byte[8];

    private long ms;

    /**
     * @param entropy the underlying source of random bytes
     * @param inc     the exclusive ceiling on a single increment, or 0 for the default
     */
    public MonotonicEntropy(InputStream entropy, long inc) {
        Objects.requireNonNull(entropy, "entropy");
        this.reader = new BufferedInputStream(entropy);
        this.inc = inc == 0 ? MAX_UINT32 : inc;
        this.rng = entropy instanceof RandomInputStream source ? source.random() : null;
    }

    @Override
    public void monotonicRead(long ms, byte[] out) throws IOException {
        if (!entropy.isZero() && this.ms == ms) {
            // Note the ordering: a failure to draw the increment aborts before the
            // destination is touched, but an overflowing increment is still written
            // out before the error surfaces. Both match the Go implementation.
            final boolean overflow = entropy.add(random());
            entropy.appendTo(out);
            if (overflow) {
                throw UlidException.monotonicOverflow();
            }
        } else {
            Io.readFull(reader, out);
            this.ms = ms;
            entropy.setBytes(out);
        }
    }

    /**
     * Returns a uniform random value in [1, inc], reading entropy from the underlying
     * source. When {@code inc <= 1} it returns 1.
     */
    private long random() throws IOException {
        if (Long.compareUnsigned(inc, 1) <= 0) {
            return 1;
        }

        // Fast path for a directly available generator.
        if (rng != null) {
            return 1 + rng.nextLong(inc);
        }

        // The maximum bit length needed to encode a value < inc.
        final int bitLen = 64 - Long.numberOfLeadingZeros(inc);

        // The maximum byte length needed to encode a value < inc.
        final int byteLen = (bitLen + 7) / 8;

        // The number of bits in the most significant byte of inc-1.
        final int msbitLen = bitLen % 8 == 0 ? 8 : bitLen % 8;

        long candidate = 0;
        while (candidate == 0 || Long.compareUnsigned(candidate, inc) >= 0) {
            Io.readFull(reader, rand, 0, byteLen);

            // Clear bits in the first byte to increase the probability
            // that the candidate is < inc.
            rand[0] &= (byte) ((1 << msbitLen) - 1);

            candidate = switch (byteLen) {
                case 1 -> rand[0] & 0xFFL;
                case 2 -> littleEndian(2);
                case 3, 4 -> littleEndian(4);
                default -> littleEndian(8);
            };
        }

        return 1 + candidate;
    }

    /** Reads the first {@code n} bytes of {@link #rand} as a little-endian integer. */
    private long littleEndian(int n) {
        long v = 0;
        for (int i = n - 1; i >= 0; i--) {
            v = (v << 8) | (rand[i] & 0xFFL);
        }
        return v;
    }

    @Override
    public int read() throws IOException {
        return reader.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return reader.read(b, off, len);
    }
}
