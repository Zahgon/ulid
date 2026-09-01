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

package io.github.oklog.ulid.cmd;

import io.github.oklog.ulid.RandomInputStream;
import io.github.oklog.ulid.Ulid;
import io.github.oklog.ulid.UlidException;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Function;

/** Command line tool that generates ULIDs, or reports the time encoded in one. */
public final class UlidMain {

    private static final String PROGRAM = "ulid";

    /** UTC as a named zone, so that the short zone name renders as "UTC" rather than "Z". */
    private static final ZoneId UTC = ZoneId.of("UTC");

    private static final DateTimeFormatter DEFAULT_HEAD =
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss", Locale.US);

    // A "yyyy" pattern prefixes years past 9999 with a plus sign, which Go's layouts
    // do not, so the year is appended explicitly with a sign only when negative.
    private static final DateTimeFormatter DEFAULT_TAIL = new DateTimeFormatterBuilder()
            .appendPattern("zzz ")
            .appendValue(ChronoField.YEAR, 4, 10, SignStyle.NORMAL)
            .toFormatter(Locale.US);
    private static final DateTimeFormatter RFC3339_MS = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4, 10, SignStyle.NORMAL)
            .appendPattern("-MM-dd'T'HH:mm:ss.SSSXXX")
            .toFormatter(Locale.US);

    private UlidMain() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        final Options options;
        try {
            options = parse(args);
        } catch (OptionException e) {
            err.print(e.getMessage() + "\n");
            return 1;
        }

        if (options.help) {
            printUsage(err);
            return 0;
        }

        final Function<ZonedDateTime, String> formatFunc = switch (options.format.toLowerCase(Locale.ROOT)) {
            case "default" -> UlidMain::formatDefault;
            case "rfc3339" -> RFC3339_MS::format;
            case "unix" -> t -> Long.toString(t.toEpochSecond());
            case "ms" -> t -> Long.toString(t.toInstant().toEpochMilli());
            default -> null;
        };
        if (formatFunc == null) {
            err.print("invalid --format " + options.format + "\n");
            return 1;
        }

        if (options.positional.isEmpty()) {
            return generate(options.quick, options.zero, out, err);
        }
        return parse(options.positional.get(0), options.local, formatFunc, err);
    }

    private static int generate(boolean quick, boolean zero, PrintStream out, PrintStream err) {
        InputStream entropy = new RandomInputStream(new SecureRandom());
        if (quick) {
            entropy = new RandomInputStream(new Random(System.nanoTime()));
        }
        if (zero) {
            entropy = new ZeroInputStream();
        }

        try {
            final Ulid id = Ulid.create(Ulid.timestamp(Instant.now()), entropy);
            out.print(id + "\n");
            return 0;
        } catch (IOException e) {
            err.print(e.getMessage() + "\n");
            return 1;
        }
    }

    private static int parse(String s, boolean local, Function<ZonedDateTime, String> f, PrintStream err) {
        final Ulid id;
        try {
            id = Ulid.parse(s);
        } catch (UlidException e) {
            err.print(e.getMessage() + "\n");
            return 1;
        }

        final ZoneId zone = local ? ZoneId.systemDefault() : UTC;
        // Note: like the Go original, the parsed time is written to stderr.
        err.print(f.apply(Ulid.time(id.time()).atZone(zone)) + "\n");
        return 0;
    }

    /** Renders a time the way Go's "Mon Jan 02 15:04:05.999 MST 2006" layout does. */
    private static String formatDefault(ZonedDateTime t) {
        final StringBuilder sb = new StringBuilder(DEFAULT_HEAD.format(t));

        // A ".999" layout element omits trailing zeroes, and the separator with them.
        final int millis = t.getNano() / 1_000_000;
        if (millis != 0) {
            String fraction = String.format(Locale.ROOT, "%03d", millis);
            while (fraction.endsWith("0")) {
                fraction = fraction.substring(0, fraction.length() - 1);
            }
            sb.append('.').append(fraction);
        }

        return sb.append(' ').append(DEFAULT_TAIL.format(t)).toString();
    }

    private static void printUsage(PrintStream err) {
        err.print("Usage: " + PROGRAM + " [-hlqz] [-f <format>] [parameters ...]\n");
        err.print(" -f, --format=<format>  when parsing, show times in this format: default, rfc3339, unix, ms\n");
        err.print(" -h, --help             print this help text\n");
        err.print(" -l, --local            when parsing, show local time instead of UTC\n");
        err.print(" -q, --quick            when generating, use non-crypto-grade entropy\n");
        err.print(" -z, --zero             when generating, fix entropy to all-zeroes\n");
    }

    // ------------------------------------------------------------------
    // Option parsing
    // ------------------------------------------------------------------

    static final class Options {
        String format = "default";
        boolean local;
        boolean quick;
        boolean zero;
        boolean help;
        final List<String> positional = new ArrayList<>();
    }

    static final class OptionException extends Exception {
        private static final long serialVersionUID = 1L;

        OptionException(String message) {
            super(message);
        }
    }

    static Options parse(String[] args) throws OptionException {
        final Options options = new Options();
        boolean optionsEnded = false;
        int i = 0;

        while (i < args.length) {
            final String arg = args[i];

            if (optionsEnded || arg.length() < 2 || arg.charAt(0) != '-') {
                options.positional.add(arg);
                i++;
            } else if (arg.equals("--")) {
                optionsEnded = true;
                i++;
            } else if (arg.startsWith("--")) {
                i = parseLong(arg.substring(2), args, i, options);
            } else {
                i = parseShort(arg.substring(1), args, i, options);
            }
        }

        return options;
    }

    private static int parseLong(String body, String[] args, int i, Options options) throws OptionException {
        final int eq = body.indexOf('=');
        final String name = eq < 0 ? body : body.substring(0, eq);
        String value = eq < 0 ? null : body.substring(eq + 1);

        switch (name) {
            case "format" -> {
                if (value == null) {
                    if (i + 1 >= args.length) {
                        throw new OptionException("missing parameter for --format");
                    }
                    value = args[++i];
                }
                options.format = value;
            }
            case "local" -> options.local = true;
            case "quick" -> options.quick = true;
            case "zero" -> options.zero = true;
            case "help" -> options.help = true;
            default -> throw new OptionException("unknown option --" + name);
        }

        return i + 1;
    }

    private static int parseShort(String body, String[] args, int i, Options options) throws OptionException {
        int j = 0;
        while (j < body.length()) {
            final char flag = body.charAt(j);
            switch (flag) {
                case 'f' -> {
                    // The value is either glued to the flag or is the next argument.
                    String value = body.substring(j + 1);
                    if (value.isEmpty()) {
                        if (i + 1 >= args.length) {
                            throw new OptionException("missing parameter for -f");
                        }
                        value = args[++i];
                    }
                    options.format = value;
                    j = body.length();
                    continue;
                }
                case 'l' -> options.local = true;
                case 'q' -> options.quick = true;
                case 'z' -> options.zero = true;
                case 'h' -> options.help = true;
                default -> throw new OptionException("unknown option -" + flag);
            }
            j++;
        }

        return i + 1;
    }

    /** An endless source of zero bytes, for {@code --zero}. */
    private static final class ZeroInputStream extends InputStream {
        @Override
        public int read() {
            return 0;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            java.util.Arrays.fill(b, off, off + len, (byte) 0);
            return len;
        }
    }
}
