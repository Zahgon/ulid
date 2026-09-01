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

/**
 * An 80-bit unsigned integer, held as a 16-bit high half and a 64-bit low half,
 * matching the layout of the ULID entropy section.
 */
final class Uint80 {

    /** High 16 bits, kept in the range [0, 65535]. */
    private int hi;

    /** Low 64 bits, interpreted as unsigned. */
    private long lo;

    /** Reads the first 10 bytes of {@code bs} as a big-endian 80-bit integer. */
    void setBytes(byte[] bs) {
        hi = ((bs[0] & 0xFF) << 8) | (bs[1] & 0xFF);
        long v = 0;
        for (int i = 2; i < 10; i++) {
            v = (v << 8) | (bs[i] & 0xFF);
        }
        lo = v;
    }

    /** Writes this value big-endian into the first 10 bytes of {@code bs}. */
    void appendTo(byte[] bs) {
        bs[0] = (byte) (hi >>> 8);
        bs[1] = (byte) hi;
        for (int i = 0; i < 8; i++) {
            bs[2 + i] = (byte) (lo >>> (56 - 8 * i));
        }
    }

    /**
     * Adds {@code n} to this value, wrapping on overflow.
     *
     * @return true if the addition overflowed the available 80 bits
     */
    boolean add(long n) {
        final long previousLo = lo;
        final int previousHi = hi;

        lo += n;
        if (Long.compareUnsigned(lo, previousLo) < 0) {
            hi = (hi + 1) & 0xFFFF;
        }
        return hi < previousHi;
    }

    boolean isZero() {
        return hi == 0 && lo == 0;
    }
}
