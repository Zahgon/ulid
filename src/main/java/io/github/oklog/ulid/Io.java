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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/** Stream helpers standing in for the {@code io} package functions used by the Go source. */
final class Io {

    private Io() {
    }

    /** Equivalent of {@code io.ReadFull}: reads exactly {@code len} bytes or fails. */
    static void readFull(InputStream in, byte[] buf, int off, int len) throws IOException {
        int read = 0;
        while (read < len) {
            final int n = in.read(buf, off + read, len - read);
            if (n < 0) {
                throw new EOFException();
            }
            read += n;
        }
    }

    static void readFull(InputStream in, byte[] buf) throws IOException {
        readFull(in, buf, 0, buf.length);
    }
}
