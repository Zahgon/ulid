# Universally Unique Lexicographically Sortable Identifier

A Java port of [oklog/ulid](https://github.com/oklog/ulid) (v2), which is itself a Go port of
[ulid/javascript](https://github.com/ulid/javascript) with the binary format implemented.

## Background

A GUID/UUID can be suboptimal for many use-cases because:

- It isn't the most character efficient way of encoding 128 bits
- UUID v1/v2 is impractical in many environments, as it requires access to a unique, stable MAC address
- UUID v3/v5 requires a unique seed and produces randomly distributed IDs, which can cause fragmentation in many data structures
- UUID v4 provides no other information than randomness which can cause fragmentation in many data structures

A ULID however:

- Is compatible with UUID/GUID's
- 1.21e+24 unique ULIDs per millisecond (1,208,925,819,614,629,174,706,176 to be exact)
- Lexicographically sortable
- Canonically encoded as a 26 character string, as opposed to the 36 character UUID
- Uses Crockford's base32 for better efficiency and readability (5 bits per character)
- Case insensitive
- No special characters (URL safe)
- Monotonic sort order (correctly detects and handles the same millisecond)

## Install

Requires Java 17 or later.

```xml
<dependency>
  <groupId>io.github.oklog</groupId>
  <artifactId>ulid</artifactId>
  <version>2.1.1-SNAPSHOT</version>
</dependency>
```

## Usage

ULIDs are constructed from two things: a timestamp with millisecond precision, and some random data.

Timestamps are Unix milliseconds. They are held in a `long` but interpreted as **unsigned**, matching
the `uint64` of the Go original. Produce one by passing an `Instant` to `Ulid.timestamp(Instant)`.

Random data is taken from a provided `InputStream`. This design allows for greater flexibility when
choosing trade-offs, but can be a bit confusing to newcomers.

If you just want to generate a ULID and don't (yet) care about details like performance or
cryptographic security, use `Ulid.make()`. It reads the clock for a timestamp and uses a
process-global, pseudo-random, monotonic source of entropy.

```java
System.out.println(Ulid.make());
// 01G65Z755AFWAKHE12NY0CQ9FH
```

More advanced use cases should use `Ulid.create(long, InputStream)`.

```java
InputStream entropy = new RandomInputStream(new Random(System.nanoTime()));
long ms = Ulid.timestamp(Instant.now());
System.out.println(Ulid.create(ms, entropy));
// 01G65Z755AFWAKHE12NY0CQ9FH
```

Care should be taken when providing a source of entropy. Security-sensitive use cases should always
use cryptographically secure entropy, for example `new RandomInputStream(new SecureRandom())`.

Monotonicity is a property that says each ULID is "bigger than" the previous one. ULIDs are
automatically monotonic, but only to millisecond precision. ULIDs generated within the same
millisecond are ordered by their random component, which means they are by default un-ordered. Use
`MonotonicEntropy`, optionally wrapped in a `LockedMonotonicReader` for concurrent use, to create
ULIDs that are monotonic within a given millisecond, with caveats. See the Javadoc for details.

```java
MonotonicEntropy entropy = Ulid.monotonic(new RandomInputStream(new SecureRandom()), 0);
LockedMonotonicReader safe = new LockedMonotonicReader(entropy);
Ulid first = Ulid.create(ms, safe);
Ulid second = Ulid.create(ms, safe);   // strictly greater than first
```

If you don't care about time-based ordering of generated IDs, then there's no reason to use ULIDs!
There are many other kinds of IDs that are easier, faster, smaller, etc. Consider UUIDs.

## API mapping from Go

| Go | Java |
| --- | --- |
| `ULID` | `Ulid` |
| `New(ms, entropy)` | `Ulid.create(long, InputStream)` |
| `MustNew` | `Ulid.mustCreate` |
| `MustNewDefault(t)` | `Ulid.mustCreateDefault(Instant)` |
| `Make()` | `Ulid.make()` |
| `DefaultEntropy()` | `Ulid.defaultEntropy()` |
| `Parse` / `ParseStrict` | `Ulid.parse` / `Ulid.parseStrict` |
| `MustParse` / `MustParseStrict` | `Ulid.mustParse` / `Ulid.mustParseStrict` |
| `id.String()` | `id.toString()` |
| `id.Bytes()` | `id.bytes()` |
| `MarshalBinary` / `MarshalBinaryTo` | `marshalBinary` / `marshalBinaryTo` |
| `MarshalText` / `MarshalTextTo` | `marshalText` / `marshalTextTo` |
| `UnmarshalBinary` / `UnmarshalText` | `unmarshalBinary` / `unmarshalText` |
| `id.Time()` | `id.time()` |
| `id.Timestamp()` | `id.timestamp()` |
| `Time(ms)` | `Ulid.time(long)` |
| `Timestamp(t)` | `Ulid.timestamp(Instant)` |
| `Now()` / `MaxTime()` | `Ulid.now()` / `Ulid.maxTime()` |
| `SetTime` / `SetEntropy` / `Entropy` | `setTime` / `setEntropy` / `entropy` |
| `id.Compare(other)` | `id.compareTo(other)` |
| `id.IsZero()` / `Zero` | `id.isZero()` / `Ulid.zero()` |
| `Scan` / `Value` | `scan(Object)` / `value()` |
| `Monotonic(r, inc)` | `Ulid.monotonic(InputStream, long)` |
| `MonotonicEntropy` | `MonotonicEntropy` |
| `LockedMonotonicReader` | `LockedMonotonicReader` |
| `io.Reader` entropy | `InputStream` entropy |
| `*math/rand.Rand` entropy | `RandomInputStream` |
| `ErrDataSize`, `ErrOverflow`, ... | `UlidException` with a `Kind` |

### Behavioural notes

These are the places where a faithful port cannot be a literal one:

- **Errors are exceptions.** Go returns sentinel `error` values compared by identity; here a single
  `UlidException` carries a `Kind` enum that names the sentinel. It extends `IOException` because the
  Go API funnels both ULID failures and entropy read failures through one `error` return.
- **The `Must*` variants throw `UncheckedIOException`**, wrapping the cause, where Go panics with the
  error value. Inspect `getCause()` to recover the `Kind`.
- **An exhausted entropy source raises `EOFException`**, standing in for Go's `io.EOF`.
- **`Ulid` is mutable**, mirroring Go's pointer receivers on `SetTime`, `SetEntropy`, `UnmarshalText`
  and friends. Go gets copies for free from array value semantics; use `copy()` for the same effect.
- **On monotonic overflow no ULID is returned.** Go returns a populated ULID *alongside*
  `ErrMonotonicOverflow`; a throwing method cannot. The error itself is raised identically.
- **`Ulid.make()` does not pool entropy sources.** Go uses a `sync.Pool`; here a single locked
  monotonic reader is shared, which is equivalent in behaviour if not in contention.
- **The default entropy stream differs.** Go seeds `math/rand`, Java seeds `java.util.Random`. The
  two generators produce different byte sequences, so ULIDs built from a *seeded* generator will not
  match Go's for the same seed. Everything downstream of the entropy bytes is identical, which is
  what the golden vectors verify.

## Commandline tool

The port includes the `cmd/ulid` tool, which generates and parses ULIDs at the command line.

```shell
mvn -o package
java -cp target/classes io.github.oklog.ulid.cmd.UlidMain --help
```

Usage:

```shell
Usage: ulid [-hlqz] [-f <format>] [parameters ...]
 -f, --format=<format>  when parsing, show times in this format: default, rfc3339, unix, ms
 -h, --help             print this help text
 -l, --local            when parsing, show local time instead of UTC
 -q, --quick            when generating, use non-crypto-grade entropy
 -z, --zero             when generating, fix entropy to all-zeroes
```

Examples:

```shell
$ ulid
01D78XYFJ1PRM1WPBCBT3VHMNV
$ ulid -z
01D78XZ44G0000000000000000
$ ulid 01D78XZ44G0000000000000000
Sun Mar 31 03:51:23.536 UTC 2019
$ ulid --format=rfc3339 --local 01D78XZ44G0000000000000000
2019-03-31T05:51:23.536+02:00
```

As in the Go original, the parsed time is written to stderr and generated ULIDs to stdout.

One deliberate deviation: for timestamps beyond the year 2262 the Go tool's `ms` format overflows,
because it derives milliseconds from `time.Time.UnixNano()` and a nanosecond count that large does
not fit in an `int64`. At the maximum ULID it reports `4773815605011` instead of `281474976710655`.
This port computes the value directly and reports the correct number. Every other format, and `ms`
for any timestamp in the representable-as-nanoseconds range, matches the Go tool exactly.

## Specification

Below is the current specification of ULID as implemented in this repository.

### Components

**Timestamp**
- 48 bits
- UNIX-time in milliseconds
- Won't run out of space till the year 10889 AD

**Entropy**
- 80 bits
- User defined entropy source.
- Monotonicity within the same millisecond with `Ulid.monotonic`

### Encoding

[Crockford's Base32](http://www.crockford.com/wrmg/base32.html) is used as shown.
This alphabet excludes the letters I, L, O, and U to avoid confusion and abuse.

```
0123456789ABCDEFGHJKMNPQRSTVWXYZ
```

### Binary Layout and Byte Order

The components are encoded as 16 octets. Each component is encoded with the Most Significant Byte
first (network byte order).

```
0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                      32_bit_uint_time_high                    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|     16_bit_uint_time_low      |       16_bit_uint_random      |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       32_bit_uint_random                      |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       32_bit_uint_random                      |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### String Representation

```
 01AN4Z07BY      79KA1307SR9X4MV3
|----------|    |----------------|
 Timestamp           Entropy
  10 chars           16 chars
   48bits             80bits
   base32             base32
```

## Test

```shell
mvn -o test
```

The suite is a port of `ulid_test.go`, with the `testing/quick` properties rewritten as
seeded loops so that a failure reproduces exactly.

On top of that, `src/test/resources/vectors.tsv` holds golden vectors generated by running the
original Go implementation: encodings, parse results for well-formed and malformed input, monotonic
sequences driven by a deterministic entropy source, timestamp conversions and the overflow
boundaries. `GoldenVectorTest` replays all of them, so any divergence in the base32 codec, the
monotonic increment arithmetic or the time conversion fails the build.

## Prior Art

- [oklog/ulid](https://github.com/oklog/ulid)
- [ulid/javascript](https://github.com/ulid/javascript)
- [RobThree/NUlid](https://github.com/RobThree/NUlid)
- [imdario/go-ulid](https://github.com/imdario/go-ulid)
