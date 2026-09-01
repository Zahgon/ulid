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
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/** Wraps a {@link MonotonicReader} with a lock, making it safe for concurrent use. */
public final class LockedMonotonicReader extends InputStream implements MonotonicReader {

    private final ReentrantLock lock = new ReentrantLock();
    private final InputStream stream;
    private final MonotonicReader reader;

    /**
     * @param inner an entropy source that is both a stream and a monotonic reader,
     *              mirroring the {@code MonotonicReader} embedded in the Go struct
     */
    public <T extends InputStream & MonotonicReader> LockedMonotonicReader(T inner) {
        Objects.requireNonNull(inner, "inner");
        this.stream = inner;
        this.reader = inner;
    }

    @Override
    public void monotonicRead(long ms, byte[] entropy) throws IOException {
        lock.lock();
        try {
            reader.monotonicRead(ms, entropy);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int read() throws IOException {
        lock.lock();
        try {
            return stream.read();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        lock.lock();
        try {
            return stream.read(b, off, len);
        } finally {
            lock.unlock();
        }
    }
}
