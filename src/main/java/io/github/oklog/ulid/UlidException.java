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

/**
 * The failure modes of the ULID codec.
 *
 * <p>This extends {@link IOException} because the original Go API funnels both
 * ULID-specific failures and entropy read failures through a single {@code error}
 * return value; sharing a checked supertype keeps the ported signatures as close
 * to the originals as Java allows.
 *
 * <p>Where Go compares sentinel error values by identity, use {@link #kind()}.
 */
public class UlidException extends IOException {

    private static final long serialVersionUID = 1L;

    /** Identifies which sentinel error of the Go implementation this corresponds to. */
    public enum Kind {
        /** Parsing or unmarshaling with the wrong data size. */
        DATA_SIZE("ulid: bad data size when unmarshaling"),
        /** Parsing or unmarshaling an invalid Base32 encoding. */
        INVALID_CHARACTERS("ulid: bad data characters when unmarshaling"),
        /** Marshaling into a buffer of insufficient size. */
        BUFFER_SIZE("ulid: bad buffer size when marshaling"),
        /** Constructing a ULID with a time larger than {@link Ulid#maxTime()}. */
        BIG_TIME("ulid: time too big"),
        /** Unmarshaling a ULID whose first character exceeds the 128-bit depth. */
        OVERFLOW("ulid: overflow when unmarshaling"),
        /** Incrementing the previous ULID's entropy bytes would overflow. */
        MONOTONIC_OVERFLOW("ulid: monotonic entropy overflow"),
        /** The value passed to scan cannot be unmarshaled into a ULID. */
        SCAN_VALUE("ulid: source value must be a string or byte slice");

        private final String message;

        Kind(String message) {
            this.message = message;
        }

        /** The message text used by the Go implementation for this error. */
        public String message() {
            return message;
        }
    }

    private final Kind kind;

    public UlidException(Kind kind) {
        super(kind.message());
        this.kind = kind;
    }

    /** The sentinel this exception stands for. */
    public Kind kind() {
        return kind;
    }

    static UlidException dataSize() {
        return new UlidException(Kind.DATA_SIZE);
    }

    static UlidException invalidCharacters() {
        return new UlidException(Kind.INVALID_CHARACTERS);
    }

    static UlidException bufferSize() {
        return new UlidException(Kind.BUFFER_SIZE);
    }

    static UlidException bigTime() {
        return new UlidException(Kind.BIG_TIME);
    }

    static UlidException overflow() {
        return new UlidException(Kind.OVERFLOW);
    }

    static UlidException monotonicOverflow() {
        return new UlidException(Kind.MONOTONIC_OVERFLOW);
    }

    static UlidException scanValue() {
        return new UlidException(Kind.SCAN_VALUE);
    }
}
