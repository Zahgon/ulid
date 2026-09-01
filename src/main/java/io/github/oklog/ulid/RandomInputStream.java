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

import java.io.InputStream;
import java.util.Objects;
import java.util.Random;

/**
 * Presents a {@link Random} as an infinite stream of random bytes.
 *
 * <p>{@link MonotonicEntropy} recognises this type and draws its increments
 * straight from {@link #random()}, which is the equivalent of the Go
 * implementation's type assertion against {@code *math/rand.Rand}.
 */
public final class RandomInputStream extends InputStream {

    private final Random random;

    public RandomInputStream(Random random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /** The underlying generator, used for the monotonic increment fast path. */
    public Random random() {
        return random;
    }

    @Override
    public int read() {
        return random.nextInt(256);
    }

    @Override
    public int read(byte[] b, int off, int len) {
        Objects.checkFromIndexSize(off, len, b.length);
        if (len == 0) {
            return 0;
        }
        if (off == 0 && len == b.length) {
            random.nextBytes(b);
            return len;
        }
        final byte[] chunk = new byte[len];
        random.nextBytes(chunk);
        System.arraycopy(chunk, 0, b, off, len);
        return len;
    }
}
