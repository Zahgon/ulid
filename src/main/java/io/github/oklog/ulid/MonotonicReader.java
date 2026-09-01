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
 * Yields monotonically increasing entropy into the provided array for all calls
 * with the same {@code ms} parameter. If an entropy source passed to
 * {@link Ulid#create(long, java.io.InputStream)} also implements this interface,
 * {@link #monotonicRead} is used instead of a plain read.
 *
 * <p>Implementations are expected to also be an {@link java.io.InputStream}, which
 * mirrors the {@code io.Reader} embedded in the Go interface.
 */
public interface MonotonicReader {

    /**
     * Fills {@code entropy} with entropy that is strictly greater than the entropy
     * previously yielded for the same {@code ms}.
     *
     * @param ms      Unix milliseconds timestamp of the ULID being built
     * @param entropy destination for the 10 entropy bytes
     * @throws UlidException if the entropy would overflow the available space
     * @throws IOException   if the underlying source fails
     */
    void monotonicRead(long ms, byte[] entropy) throws IOException;
}
