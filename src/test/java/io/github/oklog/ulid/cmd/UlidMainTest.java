package io.github.oklog.ulid.cmd;

import io.github.oklog.ulid.Ulid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks in the command line behaviour. Every expected string here was captured from
 * the original Go binary built from cmd/ulid.
 */
class UlidMainTest {

    /** A ULID encoding 1469918176385 ms with zero entropy. */
    private static final String FIXED = "01ARYZ6S410000000000000000";

    private record Result(int code, String out, String err) {
    }

    private static Result run(String... args) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();

        final int code = UlidMain.run(args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("parsing prints the default time layout")
    void parseDefaultFormat() {
        final Result result = run(FIXED);
        assertEquals(0, result.code());
        assertEquals("Sat Jul 30 22:36:16.385 UTC 2016\n", result.err());
    }

    @Test
    @DisplayName("a whole-second time drops the fractional part entirely")
    void parseDropsEmptyFraction() {
        assertEquals("Thu Jan 01 00:00:00 UTC 1970\n", run("0000000000ZZZZZZZZZZZZZZZZ").err());
    }

    @Test
    @DisplayName("rfc3339 always prints three fractional digits")
    void parseRfc3339Format() {
        assertEquals("2016-07-30T22:36:16.385Z\n", run("-f", "rfc3339", FIXED).err());
    }

    @Test
    @DisplayName("a year past 9999 prints without a sign, as Go does")
    void parseFarFutureYear() {
        final String max = "7ZZZZZZZZZZZZZZZZZZZZZZZZZ";
        assertEquals("Tue Aug 02 05:31:50.655 UTC 10889\n", run(max).err());
        assertEquals("10889-08-02T05:31:50.655Z\n", run("-f", "rfc3339", max).err());
        assertEquals("281474976710\n", run("-f", "unix", max).err());
    }

    @Test
    @DisplayName("unix prints whole seconds")
    void parseUnixFormat() {
        assertEquals("1469918176\n", run("-f", "unix", FIXED).err());
    }

    @Test
    @DisplayName("ms prints milliseconds")
    void parseMsFormat() {
        assertEquals("1469918176385\n", run("-f", "ms", FIXED).err());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"-frfc3339", "--format=rfc3339"})
    @DisplayName("the format value may be glued to the flag")
    void formatValueMayBeAttached(String flag) {
        assertEquals("2016-07-30T22:36:16.385Z\n", run(flag, FIXED).err());
    }

    @Test
    @DisplayName("short flags bundle together")
    void shortFlagsBundle() {
        // Milliseconds are zone independent, so this holds whatever the host zone is.
        assertEquals("1469918176385\n", run("-lf", "ms", FIXED).err());
    }

    @Test
    @DisplayName("local time selects the host zone without moving the instant")
    void localTimeKeepsTheInstant() {
        assertEquals(run("-f", "ms", FIXED).err(), run("-l", "-f", "ms", FIXED).err());
    }

    @Test
    @DisplayName("an unknown format is rejected")
    void rejectsUnknownFormat() {
        final Result result = run("-f", "nope", FIXED);
        assertEquals(1, result.code());
        assertEquals("invalid --format nope\n", result.err());
    }

    @Test
    @DisplayName("a malformed ULID is reported with the library's message")
    void rejectsMalformedUlid() {
        final Result result = run("ZZZZ");
        assertEquals(1, result.code());
        assertEquals("ulid: bad data size when unmarshaling\n", result.err());
    }

    @Test
    @DisplayName("an overflowing ULID is reported")
    void rejectsOverflowingUlid() {
        final Result result = run("80000000000000000000000000");
        assertEquals(1, result.code());
        assertEquals("ulid: overflow when unmarshaling\n", result.err());
    }

    @Test
    @DisplayName("an unknown option is rejected")
    void rejectsUnknownOption() {
        assertEquals(1, run("-x", FIXED).code());
        assertEquals(1, run("--nonsense", FIXED).code());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"-h", "--help"})
    @DisplayName("help prints the usage text and succeeds")
    void printsHelp(String flag) {
        final Result result = run(flag);
        assertEquals(0, result.code());
        assertEquals("""
                Usage: ulid [-hlqz] [-f <format>] [parameters ...]
                 -f, --format=<format>  when parsing, show times in this format: default, rfc3339, unix, ms
                 -h, --help             print this help text
                 -l, --local            when parsing, show local time instead of UTC
                 -q, --quick            when generating, use non-crypto-grade entropy
                 -z, --zero             when generating, fix entropy to all-zeroes
                """, result.err());
    }

    @ParameterizedTest(name = "flags=[{0}]")
    @ValueSource(strings = {"", "-q"})
    @DisplayName("generating writes one parseable ULID to stdout")
    void generates(String flag) throws Exception {
        final Result result = flag.isEmpty() ? run() : run(flag);

        assertEquals(0, result.code());
        assertEquals("", result.err());

        final String printed = result.out();
        assertTrue(printed.endsWith("\n"), "output should end with a newline");

        final String id = printed.strip();
        assertEquals(Ulid.ENCODED_SIZE, id.length());
        assertEquals(id, Ulid.parseStrict(id).toString(), "generated ULID should round trip");
    }

    @Test
    @DisplayName("--zero fixes the entropy to all zeroes")
    void generatesWithZeroEntropy() throws Exception {
        final String id = run("-z").out().strip();

        assertEquals(Ulid.ENCODED_SIZE, id.length());
        assertEquals("0000000000000000", id.substring(10), "entropy should encode as zeroes");
        assertTrue(Ulid.parseStrict(id).time() > 0, "the timestamp should still be the current time");
    }
}
