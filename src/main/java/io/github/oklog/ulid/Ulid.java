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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Random;

/**
 * A ULID is a 16 byte Universally Unique Lexicographically Sortable Identifier.
 *
 * <pre>
 * The components are encoded as 16 octets.
 * Each component is encoded with the MSB first (network byte order).
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                      32_bit_uint_time_high                    |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |     16_bit_uint_time_low      |       16_bit_uint_random      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                       32_bit_uint_random                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                       32_bit_uint_random                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * <p>Timestamps are Unix milliseconds held in a {@code long} but interpreted as
 * unsigned, matching the {@code uint64} of the Go original.
 *
 * <p>Instances are mutable, mirroring the pointer receivers of the Go type. Use
 * {@link #copy()} where Go would have relied on array value semantics.
 */
public final class Ulid implements Comparable<Ulid> {

    /** The length in bytes of a binary ULID. */
    public static final int SIZE = 16;

    /** The length of a text encoded ULID. */
    public static final int ENCODED_SIZE = 26;

    /** The base 32 encoding alphabet used in ULID strings. */
    public static final String ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    /** The number of entropy bytes following the 6 byte timestamp. */
    private static final int ENTROPY_SIZE = SIZE - 6;

    /** The maximum Unix time in milliseconds representable in a ULID. */
    private static final long MAX_TIME = 0xFFFFFFFFFFFFL;

    /** Sentinel for a character that is not part of the encoding alphabet. */
    private static final int INVALID = 0xFF;

    /**
     * Character to index table for O(1) lookups when unmarshaling. Both cases map to
     * the same index, which is what makes ULID parsing case insensitive.
     */
    private static final int[] DEC = new int[256];

    static {
        Arrays.fill(DEC, INVALID);
        for (int i = 0; i < ENCODING.length(); i++) {
            final char upper = ENCODING.charAt(i);
            DEC[upper] = i;
            DEC[Character.toLowerCase(upper)] = i;
        }
    }

    private static final InputStream DEFAULT_ENTROPY = new LockedMonotonicReader(
            new MonotonicEntropy(new RandomInputStream(new Random(nanoTimeSeed())), 0));

    private final byte[] data = new byte[SIZE];

    /** Creates a zero-value ULID. */
    public Ulid() {
    }

    /**
     * Creates a ULID from its 16 byte binary form.
     *
     * @throws UlidException with kind {@link UlidException.Kind#DATA_SIZE} if
     *                       {@code data} is not exactly {@link #SIZE} bytes
     */
    public Ulid(byte[] data) throws UlidException {
        unmarshalBinary(data);
    }

    /** Creates an independent copy of {@code other}. */
    public Ulid(Ulid other) {
        System.arraycopy(other.data, 0, data, 0, SIZE);
    }

    /** A zero-value ULID, the equivalent of the Go package's {@code Zero}. */
    public static Ulid zero() {
        return new Ulid();
    }

    /** Returns an independent copy of this ULID. */
    public Ulid copy() {
        return new Ulid(this);
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    /**
     * Returns a ULID with the given Unix milliseconds timestamp and an optional
     * entropy source. Use {@link #timestamp(Instant)} to convert an {@link Instant}
     * to Unix milliseconds.
     *
     * <p>Safety for concurrent use is only dependent on the safety of the entropy
     * source.
     *
     * @param ms      Unix milliseconds, interpreted as unsigned
     * @param entropy the entropy source, or null for all-zero entropy
     * @throws UlidException with kind {@link UlidException.Kind#BIG_TIME} if
     *                       {@code ms} exceeds {@link #maxTime()}
     * @throws IOException   if reading from the entropy source fails
     */
    public static Ulid create(long ms, InputStream entropy) throws IOException {
        final Ulid id = new Ulid();
        id.setTime(ms);

        if (entropy == null) {
            return id;
        }

        if (entropy instanceof MonotonicReader monotonic) {
            final byte[] bytes = new byte[ENTROPY_SIZE];
            monotonic.monotonicRead(ms, bytes);
            System.arraycopy(bytes, 0, id.data, 6, ENTROPY_SIZE);
        } else {
            Io.readFull(entropy, id.data, 6, ENTROPY_SIZE);
        }

        return id;
    }

    /**
     * Equivalent to {@link #create(long, InputStream)} but wraps failures in an
     * unchecked exception, mirroring the panic of the Go {@code MustNew}.
     */
    public static Ulid mustCreate(long ms, InputStream entropy) {
        try {
            return create(ms, entropy);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Equivalent to {@link #mustCreate(long, InputStream)} with {@link #defaultEntropy()}
     * as the entropy. May throw if the given instant is too large or too small.
     */
    public static Ulid mustCreateDefault(Instant t) {
        return mustCreate(timestamp(t), DEFAULT_ENTROPY);
    }

    /** A thread-safe, per process, monotonically increasing entropy source. */
    public static InputStream defaultEntropy() {
        return DEFAULT_ENTROPY;
    }

    /**
     * Returns a ULID with the current time in Unix milliseconds and monotonically
     * increasing entropy for the same millisecond. It is safe for concurrent use.
     */
    public static Ulid make() {
        // The default entropy never fails, so this cannot throw.
        return mustCreate(now(), DEFAULT_ENTROPY);
    }

    /**
     * Returns a source of entropy that yields strictly increasing entropy bytes,
     * to a limit governed by {@code inc}. See {@link MonotonicEntropy}.
     */
    public static MonotonicEntropy monotonic(InputStream entropy, long inc) {
        return new MonotonicEntropy(entropy, inc);
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    /**
     * Parses an encoded ULID. Invalid encodings produce undefined ULIDs; for a
     * version that reports them, see {@link #parseStrict(CharSequence)}.
     *
     * @throws UlidException with kind {@link UlidException.Kind#DATA_SIZE} if the
     *                       input length differs from {@link #ENCODED_SIZE}, or
     *                       {@link UlidException.Kind#OVERFLOW} if the first
     *                       character exceeds the valid bit depth of 128
     */
    public static Ulid parse(CharSequence ulid) throws UlidException {
        final Ulid id = new Ulid();
        parse(ulid, false, id);
        return id;
    }

    /** Parses the ASCII bytes of an encoded ULID. */
    public static Ulid parse(byte[] ulid) throws UlidException {
        return parse(latin1(ulid));
    }

    /**
     * Like {@link #parse(CharSequence)}, but additionally validates that the input
     * consists only of valid base32 characters. It is slightly slower than parse.
     *
     * @throws UlidException with kind {@link UlidException.Kind#INVALID_CHARACTERS}
     *                       for encodings outside the alphabet
     */
    public static Ulid parseStrict(CharSequence ulid) throws UlidException {
        final Ulid id = new Ulid();
        parse(ulid, true, id);
        return id;
    }

    /** Parses the ASCII bytes of an encoded ULID, validating the character set. */
    public static Ulid parseStrict(byte[] ulid) throws UlidException {
        return parseStrict(latin1(ulid));
    }

    /**
     * Equivalent to {@link #parse(CharSequence)} but wraps failures in an unchecked
     * exception, mirroring the panic of the Go {@code MustParse}.
     */
    public static Ulid mustParse(CharSequence ulid) {
        try {
            return parse(ulid);
        } catch (UlidException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Equivalent to {@link #parseStrict(CharSequence)} but wraps failures in an
     * unchecked exception, mirroring the panic of the Go {@code MustParseStrict}.
     */
    public static Ulid mustParseStrict(CharSequence ulid) {
        try {
            return parseStrict(ulid);
        } catch (UlidException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void parse(CharSequence v, boolean strict, Ulid id) throws UlidException {
        // Check if a base32 encoded ULID is the right length.
        if (v.length() != ENCODED_SIZE) {
            throw UlidException.dataSize();
        }

        // Check if all the characters in a base32 encoded ULID are part of the
        // expected base32 character set.
        if (strict) {
            for (int i = 0; i < ENCODED_SIZE; i++) {
                if (dec(v.charAt(i)) == INVALID) {
                    throw UlidException.invalidCharacters();
                }
            }
        }

        // Check if the first character in a base32 encoded ULID will overflow. This
        // happens because the base32 representation encodes 130 bits, while the
        // ULID is only 128 bits.
        //
        // See https://github.com/oklog/ulid/issues/9 for details.
        if (v.charAt(0) > '7') {
            throw UlidException.overflow();
        }

        // Use an optimized unrolled loop (from https://github.com/RobThree/NUlid)
        // to decode a base32 ULID.
        final int[] c = new int[ENCODED_SIZE];
        for (int i = 0; i < ENCODED_SIZE; i++) {
            c[i] = dec(v.charAt(i));
        }

        final byte[] o = id.data;

        // 6 bytes timestamp (48 bits)
        o[0] = (byte) ((c[0] << 5) | c[1]);
        o[1] = (byte) ((c[2] << 3) | (c[3] >> 2));
        o[2] = (byte) ((c[3] << 6) | (c[4] << 1) | (c[5] >> 4));
        o[3] = (byte) ((c[5] << 4) | (c[6] >> 1));
        o[4] = (byte) ((c[6] << 7) | (c[7] << 2) | (c[8] >> 3));
        o[5] = (byte) ((c[8] << 5) | c[9]);

        // 10 bytes of entropy (80 bits)
        o[6] = (byte) ((c[10] << 3) | (c[11] >> 2));
        o[7] = (byte) ((c[11] << 6) | (c[12] << 1) | (c[13] >> 4));
        o[8] = (byte) ((c[13] << 4) | (c[14] >> 1));
        o[9] = (byte) ((c[14] << 7) | (c[15] << 2) | (c[16] >> 3));
        o[10] = (byte) ((c[16] << 5) | c[17]);
        o[11] = (byte) ((c[18] << 3) | (c[19] >> 2));
        o[12] = (byte) ((c[19] << 6) | (c[20] << 1) | (c[21] >> 4));
        o[13] = (byte) ((c[21] << 4) | (c[22] >> 1));
        o[14] = (byte) ((c[22] << 7) | (c[23] << 2) | (c[24] >> 3));
        o[15] = (byte) ((c[24] << 5) | c[25]);
    }

    /** Looks up a character in the decoding table, treating anything out of range as invalid. */
    private static int dec(char c) {
        return c > 0xFF ? INVALID : DEC[c];
    }

    /** Widens raw bytes to characters one-for-one, so byte 0xFF becomes char 0x00FF. */
    private static CharSequence latin1(byte[] v) {
        return new String(v, StandardCharsets.ISO_8859_1);
    }

    // ------------------------------------------------------------------
    // Encoding
    // ------------------------------------------------------------------

    /** Returns a copy of the ULID as a byte array. */
    public byte[] bytes() {
        return data.clone();
    }

    /**
     * Returns a lexicographically sortable string encoded ULID (26 characters,
     * non-standard base 32) e.g. 01AN4Z07BY79KA1307SR9X4MV3.
     * Format: tttttttttteeeeeeeeeeeeeeee where t is time and e is entropy.
     */
    @Override
    public String toString() {
        final byte[] text = new byte[ENCODED_SIZE];
        encode(text);
        return new String(text, StandardCharsets.US_ASCII);
    }

    /** Returns the ULID as a 16 byte array. */
    public byte[] marshalBinary() {
        return data.clone();
    }

    /**
     * Writes the binary encoding of the ULID to the given buffer.
     *
     * @throws UlidException with kind {@link UlidException.Kind#BUFFER_SIZE} if
     *                       {@code dst} is not exactly {@link #SIZE} bytes
     */
    public void marshalBinaryTo(byte[] dst) throws UlidException {
        if (dst.length != SIZE) {
            throw UlidException.bufferSize();
        }
        System.arraycopy(data, 0, dst, 0, SIZE);
    }

    /**
     * Copies the passed data into this ULID.
     *
     * @throws UlidException with kind {@link UlidException.Kind#DATA_SIZE} if the
     *                       data length differs from {@link #SIZE}
     */
    public void unmarshalBinary(byte[] src) throws UlidException {
        if (src.length != SIZE) {
            throw UlidException.dataSize();
        }
        System.arraycopy(src, 0, data, 0, SIZE);
    }

    /** Returns the string encoded ULID as ASCII bytes. */
    public byte[] marshalText() {
        final byte[] text = new byte[ENCODED_SIZE];
        encode(text);
        return text;
    }

    /**
     * Writes the ULID as a string to the given buffer.
     *
     * @throws UlidException with kind {@link UlidException.Kind#BUFFER_SIZE} if
     *                       {@code dst} is not exactly {@link #ENCODED_SIZE} bytes
     */
    public void marshalTextTo(byte[] dst) throws UlidException {
        if (dst.length != ENCODED_SIZE) {
            throw UlidException.bufferSize();
        }
        encode(dst);
    }

    /** Parses the data as a string encoded ULID, replacing this ULID's contents. */
    public void unmarshalText(CharSequence v) throws UlidException {
        parse(v, false, this);
    }

    /** Parses the ASCII bytes as a string encoded ULID, replacing this ULID's contents. */
    public void unmarshalText(byte[] v) throws UlidException {
        parse(latin1(v), false, this);
    }

    /** Optimized unrolled loop, from https://github.com/RobThree/NUlid */
    private void encode(byte[] dst) {
        final int[] b = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            b[i] = data[i] & 0xFF;
        }

        // 10 byte timestamp
        dst[0] = (byte) ENCODING.charAt((b[0] & 224) >> 5);
        dst[1] = (byte) ENCODING.charAt(b[0] & 31);
        dst[2] = (byte) ENCODING.charAt((b[1] & 248) >> 3);
        dst[3] = (byte) ENCODING.charAt(((b[1] & 7) << 2) | ((b[2] & 192) >> 6));
        dst[4] = (byte) ENCODING.charAt((b[2] & 62) >> 1);
        dst[5] = (byte) ENCODING.charAt(((b[2] & 1) << 4) | ((b[3] & 240) >> 4));
        dst[6] = (byte) ENCODING.charAt(((b[3] & 15) << 1) | ((b[4] & 128) >> 7));
        dst[7] = (byte) ENCODING.charAt((b[4] & 124) >> 2);
        dst[8] = (byte) ENCODING.charAt(((b[4] & 3) << 3) | ((b[5] & 224) >> 5));
        dst[9] = (byte) ENCODING.charAt(b[5] & 31);

        // 16 bytes of entropy
        dst[10] = (byte) ENCODING.charAt((b[6] & 248) >> 3);
        dst[11] = (byte) ENCODING.charAt(((b[6] & 7) << 2) | ((b[7] & 192) >> 6));
        dst[12] = (byte) ENCODING.charAt((b[7] & 62) >> 1);
        dst[13] = (byte) ENCODING.charAt(((b[7] & 1) << 4) | ((b[8] & 240) >> 4));
        dst[14] = (byte) ENCODING.charAt(((b[8] & 15) << 1) | ((b[9] & 128) >> 7));
        dst[15] = (byte) ENCODING.charAt((b[9] & 124) >> 2);
        dst[16] = (byte) ENCODING.charAt(((b[9] & 3) << 3) | ((b[10] & 224) >> 5));
        dst[17] = (byte) ENCODING.charAt(b[10] & 31);
        dst[18] = (byte) ENCODING.charAt((b[11] & 248) >> 3);
        dst[19] = (byte) ENCODING.charAt(((b[11] & 7) << 2) | ((b[12] & 192) >> 6));
        dst[20] = (byte) ENCODING.charAt((b[12] & 62) >> 1);
        dst[21] = (byte) ENCODING.charAt(((b[12] & 1) << 4) | ((b[13] & 240) >> 4));
        dst[22] = (byte) ENCODING.charAt(((b[13] & 15) << 1) | ((b[14] & 128) >> 7));
        dst[23] = (byte) ENCODING.charAt((b[14] & 124) >> 2);
        dst[24] = (byte) ENCODING.charAt(((b[14] & 3) << 3) | ((b[15] & 224) >> 5));
        dst[25] = (byte) ENCODING.charAt(b[15] & 31);
    }

    // ------------------------------------------------------------------
    // Time
    // ------------------------------------------------------------------

    /**
     * Returns the Unix time in milliseconds encoded in the ULID. Use
     * {@link #time(long)} to convert the returned value to an {@link Instant}.
     */
    public long time() {
        return (long) (data[5] & 0xFF)
                | (long) (data[4] & 0xFF) << 8
                | (long) (data[3] & 0xFF) << 16
                | (long) (data[2] & 0xFF) << 24
                | (long) (data[1] & 0xFF) << 32
                | (long) (data[0] & 0xFF) << 40;
    }

    /** Returns the time encoded in the ULID as an {@link Instant}. */
    public Instant timestamp() {
        return time(time());
    }

    /** Returns true if this is a zero-value ULID. */
    public boolean isZero() {
        return compareTo(zero()) == 0;
    }

    /** The maximum Unix time in milliseconds that can be encoded in a ULID. */
    public static long maxTime() {
        return MAX_TIME;
    }

    /** The current UTC time in Unix milliseconds. */
    public static long now() {
        return timestamp(Instant.now());
    }

    /**
     * Converts an {@link Instant} to Unix milliseconds.
     *
     * <p>Because of the way ULID stores time, times from the year 10889 produce
     * undefined results.
     */
    public static long timestamp(Instant t) {
        return t.getEpochSecond() * 1000 + t.getNano() / 1_000_000;
    }

    /**
     * Converts Unix milliseconds in the format returned by {@link #timestamp(Instant)}
     * to an {@link Instant}.
     */
    public static Instant time(long ms) {
        final long s = Long.divideUnsigned(ms, 1000);
        final long ns = Long.remainderUnsigned(ms, 1000) * 1_000_000;
        return Instant.ofEpochSecond(s, ns);
    }

    /**
     * Sets the time component of the ULID to the given Unix time in milliseconds.
     *
     * @throws UlidException with kind {@link UlidException.Kind#BIG_TIME} if
     *                       {@code ms} exceeds {@link #maxTime()}
     */
    public void setTime(long ms) throws UlidException {
        if (Long.compareUnsigned(ms, MAX_TIME) > 0) {
            throw UlidException.bigTime();
        }

        data[0] = (byte) (ms >>> 40);
        data[1] = (byte) (ms >>> 32);
        data[2] = (byte) (ms >>> 24);
        data[3] = (byte) (ms >>> 16);
        data[4] = (byte) (ms >>> 8);
        data[5] = (byte) ms;
    }

    // ------------------------------------------------------------------
    // Entropy
    // ------------------------------------------------------------------

    /** Returns a copy of the entropy from the ULID. */
    public byte[] entropy() {
        return Arrays.copyOfRange(data, 6, SIZE);
    }

    /**
     * Sets the ULID entropy to the passed bytes.
     *
     * @throws UlidException with kind {@link UlidException.Kind#DATA_SIZE} if
     *                       {@code e} is not exactly 10 bytes
     */
    public void setEntropy(byte[] e) throws UlidException {
        if (e.length != ENTROPY_SIZE) {
            throw UlidException.dataSize();
        }
        System.arraycopy(e, 0, data, 6, ENTROPY_SIZE);
    }

    // ------------------------------------------------------------------
    // Comparison and SQL interop
    // ------------------------------------------------------------------

    /**
     * Compares this ULID to another lexicographically. The result is 0 if they are
     * equal, -1 if this is less than {@code other}, and +1 if this is greater.
     */
    @Override
    public int compareTo(Ulid other) {
        return Integer.signum(Arrays.compareUnsigned(data, other.data));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Ulid other && Arrays.equals(data, other.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    /**
     * Reads a value from a SQL result set into this ULID, supporting a string, the
     * 16 byte binary form, and the 26 character text encoding as bytes. A null
     * source leaves the ULID untouched.
     *
     * @throws UlidException with kind {@link UlidException.Kind#SCAN_VALUE} if the
     *                       source is neither a string nor a byte array
     */
    public void scan(Object src) throws UlidException {
        if (src == null) {
            return;
        }

        if (src instanceof CharSequence s) {
            unmarshalText(s);
            return;
        }

        if (src instanceof byte[] b) {
            // Drivers often return text/varchar columns as bytes. Accept both
            // the 16-byte binary form and the 26-character text encoding.
            if (b.length == SIZE) {
                unmarshalBinary(b);
            } else if (b.length == ENCODED_SIZE) {
                unmarshalText(b);
            } else {
                throw UlidException.dataSize();
            }
            return;
        }

        throw UlidException.scanValue();
    }

    /**
     * Returns the ULID as a byte array for storage in a SQL parameter. If your use
     * case requires a string representation instead, use {@link #toString()}.
     */
    public byte[] value() {
        return marshalBinary();
    }

    /** Seeds the default generator the way the Go implementation seeds from UnixNano. */
    private static long nanoTimeSeed() {
        final Instant now = Instant.now();
        return now.getEpochSecond() * 1_000_000_000L + now.getNano();
    }
}
