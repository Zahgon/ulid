// Copyright 2016 The Oklog Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package ulid

import (
	"database/sql/driver"
	"errors"
	"io"
	"math/rand"
	"sync"
	"time"
)

/*
A ULID is a 16 byte Universally Unique Lexicographically Sortable Identifier

	The components are encoded as 16 octets.
	Each component is encoded with the MSB first (network byte order).

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
*/
type ULID [16]byte

var (
	// ErrDataSize is returned when parsing or unmarshaling ULIDs with the wrong
	// data size.
	ErrDataSize = errors.New("ulid: bad data size when unmarshaling")

	// ErrInvalidCharacters is returned when parsing or unmarshaling ULIDs with
	// invalid Base32 encodings.
	ErrInvalidCharacters = errors.New("ulid: bad data characters when unmarshaling")

	// ErrBufferSize is returned when marshalling ULIDs to a buffer of insufficient
	// size.
	ErrBufferSize = errors.New("ulid: bad buffer size when marshaling")

	// ErrBigTime is returned when constructing a ULID with a time that is larger
	// than MaxTime.
	ErrBigTime = errors.New("ulid: time too big")

	// ErrOverflow is returned when unmarshaling a ULID whose first character is
	// larger than 7, thereby exceeding the valid bit depth of 128.
	ErrOverflow = errors.New("ulid: overflow when unmarshaling")

	// ErrMonotonicOverflow is returned by a Monotonic entropy source when
	// incrementing the previous ULID's entropy bytes would result in overflow.
	ErrMonotonicOverflow = errors.New("ulid: monotonic entropy overflow")

	// ErrScanValue is returned when the value passed to scan cannot be unmarshaled
	// into the ULID.
	ErrScanValue = errors.New("ulid: source value must be a string or byte slice")

	// Zero is a zero-value ULID.
	Zero ULID
)

// MonotonicReader is an interface that should yield monotonically increasing
// entropy into the provided slice for all calls with the same ms parameter. If
// a MonotonicReader is provided to the New constructor, its MonotonicRead
// method will be used instead of Read.
type MonotonicReader interface {
	io.Reader
	MonotonicRead(ms uint64, p []byte) error
}

// New returns a ULID with the given Unix milliseconds timestamp and an
// optional entropy source. Use the Timestamp function to convert
// a time.Time to Unix milliseconds.
//
// ErrBigTime is returned when passing a timestamp bigger than MaxTime.
// Reading from the entropy source may also return an error.
//
// Safety for concurrent use is only dependent on the safety of the
// entropy source.
func New(ms uint64, entropy io.Reader) (id ULID, err error) {
	_ = "STUB: not implemented"
	return *new(ULID), nil
}

// MustNew is a convenience function equivalent to New that panics on failure
// instead of returning an error.
func MustNew(ms uint64, entropy io.Reader) ULID { _ = "STUB: not implemented"; return *new(ULID) }

// MustNewDefault is a convenience function equivalent to MustNew with
// DefaultEntropy as the entropy. It may panic if the given time.Time is too
// large or too small.
func MustNewDefault(t time.Time) ULID { _ = "STUB: not implemented"; return *new(ULID) }

var defaultEntropy = func() io.Reader {
	rng := rand.New(rand.NewSource(time.Now().UnixNano()))
	return &LockedMonotonicReader{MonotonicReader: Monotonic(rng, 0)}
}()

// DefaultEntropy returns a thread-safe per process monotonically increasing
// entropy source.
func DefaultEntropy() io.Reader {
	_ = "STUB: not implemented"
	return *

	// Make returns a ULID with the current time in Unix milliseconds and
	// monotonically increasing entropy for the same millisecond.
	// It is safe for concurrent use, leveraging a sync.Pool underneath for minimal
	// contention.
	new(io.Reader)
}

func Make() (id ULID) {
	_ = "STUB: not implemented"
	// NOTE: MustNew can't panic since DefaultEntropy never returns an error.
	return *new(ULID)
}

// Parse parses an encoded ULID, returning an error in case of failure.
//
// ErrDataSize is returned if the len(ulid) is different from an encoded
// ULID's length. Invalid encodings produce undefined ULIDs. For a version that
// returns an error instead, see ParseStrict.
func Parse(ulid string) (id ULID, err error) { _ = "STUB: not implemented"; return *new(ULID), nil }

// ParseStrict parses an encoded ULID, returning an error in case of failure.
//
// It is like Parse, but additionally validates that the parsed ULID consists
// only of valid base32 characters. It is slightly slower than Parse.
//
// ErrDataSize is returned if the len(ulid) is different from an encoded
// ULID's length. Invalid encodings return ErrInvalidCharacters.
func ParseStrict(ulid string) (id ULID, err error) {
	_ = "STUB: not implemented"
	return *new(ULID), nil
}

func parse(v []byte, strict bool, id *ULID) error {
	_ = "STUB: not implemented"
	// Check if a base32 encoded ULID is the right length.
	return nil
}

// Check if all the characters in a base32 encoded ULID are part of the
// expected base32 character set.

// Check if the first character in a base32 encoded ULID will overflow. This
// happens because the base32 representation encodes 130 bits, while the
// ULID is only 128 bits.
//
// See https://github.com/oklog/ulid/issues/9 for details.

// Use an optimized unrolled loop (from https://github.com/RobThree/NUlid)
// to decode a base32 ULID.

// 6 bytes timestamp (48 bits)

// 10 bytes of entropy (80 bits)

// MustParse is a convenience function equivalent to Parse that panics on failure
// instead of returning an error.
func MustParse(ulid string) ULID { _ = "STUB: not implemented"; return *new(ULID) }

// MustParseStrict is a convenience function equivalent to ParseStrict that
// panics on failure instead of returning an error.
func MustParseStrict(ulid string) ULID { _ = "STUB: not implemented"; return *new(ULID) }

// Bytes returns bytes slice representation of ULID.
func (id ULID) Bytes() []byte {
	_ = "STUB: not implemented"

	// String returns a lexicographically sortable string encoded ULID
	// (26 characters, non-standard base 32) e.g. 01AN4Z07BY79KA1307SR9X4MV3.
	// Format: tttttttttteeeeeeeeeeeeeeee where t is time and e is entropy.
	return nil
}

func (id ULID) String() string { _ = "STUB: not implemented"; return "" }

// MarshalBinary implements the encoding.BinaryMarshaler interface by
// returning the ULID as a byte slice.
func (id ULID) MarshalBinary() ([]byte, error) { _ = "STUB: not implemented"; return nil, nil }

// MarshalBinaryTo writes the binary encoding of the ULID to the given buffer.
// ErrBufferSize is returned when the len(dst) != 16.
func (id ULID) MarshalBinaryTo(dst []byte) error { _ = "STUB: not implemented"; return nil }

// UnmarshalBinary implements the encoding.BinaryUnmarshaler interface by
// copying the passed data and converting it to a ULID. ErrDataSize is
// returned if the data length is different from ULID length.
func (id *ULID) UnmarshalBinary(data []byte) error { _ = "STUB: not implemented"; return nil }

// Encoding is the base 32 encoding alphabet used in ULID strings.
const Encoding = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

// MarshalText implements the encoding.TextMarshaler interface by
// returning the string encoded ULID.
func (id ULID) MarshalText() ([]byte, error) { _ = "STUB: not implemented"; return nil, nil }

// MarshalTextTo writes the ULID as a string to the given buffer.
// ErrBufferSize is returned when the len(dst) != 26.
func (id ULID) MarshalTextTo(dst []byte) error {
	_ = "STUB: not implemented"
	// Optimized unrolled loop ahead.
	// From https://github.com/RobThree/NUlid
	return nil
}

// 10 byte timestamp

// 16 bytes of entropy

// Byte to index table for O(1) lookups when unmarshaling.
// We use 0xFF as sentinel value for invalid indexes.
var dec = [...]byte{
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x01,
	0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0xFF, 0xFF,
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E,
	0x0F, 0x10, 0x11, 0xFF, 0x12, 0x13, 0xFF, 0x14, 0x15, 0xFF,
	0x16, 0x17, 0x18, 0x19, 0x1A, 0xFF, 0x1B, 0x1C, 0x1D, 0x1E,
	0x1F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x0A, 0x0B, 0x0C,
	0x0D, 0x0E, 0x0F, 0x10, 0x11, 0xFF, 0x12, 0x13, 0xFF, 0x14,
	0x15, 0xFF, 0x16, 0x17, 0x18, 0x19, 0x1A, 0xFF, 0x1B, 0x1C,
	0x1D, 0x1E, 0x1F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
}

// EncodedSize is the length of a text encoded ULID.
const EncodedSize = 26

// UnmarshalText implements the encoding.TextUnmarshaler interface by
// parsing the data as string encoded ULID.
//
// ErrDataSize is returned if the len(v) is different from an encoded
// ULID's length. Invalid encodings produce undefined ULIDs.
func (id *ULID) UnmarshalText(v []byte) error { _ = "STUB: not implemented"; return nil }

// Time returns the Unix time in milliseconds encoded in the ULID.
// Use the top level Time function to convert the returned value to
// a time.Time.
func (id ULID) Time() uint64 { _ = "STUB: not implemented"; return 0 }

// Timestamp returns the time encoded in the ULID as a time.Time.
func (id ULID) Timestamp() time.Time {
	_ = "STUB: not implemented"
	return *

	// IsZero returns true if the ULID is a zero-value ULID, i.e. ulid.Zero.
	new(time.Time)
}

func (id ULID) IsZero() bool { _ = "STUB: not implemented"; return false }

// maxTime is the maximum Unix time in milliseconds that can be
// represented in a ULID.
var maxTime = ULID{0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF}.Time()

// MaxTime returns the maximum Unix time in milliseconds that
// can be encoded in a ULID.
func MaxTime() uint64 {
	_ = "STUB: not implemented"

	// Now is a convenience function that returns the current
	// UTC time in Unix milliseconds. Equivalent to:
	//
	//	Timestamp(time.Now().UTC())
	return 0
}

func Now() uint64 { _ = "STUB: not implemented"; return 0 }

// Timestamp converts a time.Time to Unix milliseconds.
//
// Because of the way ULID stores time, times from the year
// 10889 produces undefined results.
func Timestamp(t time.Time) uint64 { _ = "STUB: not implemented"; return 0 }

// Time converts Unix milliseconds in the format
// returned by the Timestamp function to a time.Time.
func Time(ms uint64) time.Time { _ = "STUB: not implemented"; return *new(time.Time) }

// SetTime sets the time component of the ULID to the given Unix time
// in milliseconds.
func (id *ULID) SetTime(ms uint64) error { _ = "STUB: not implemented"; return nil }

// Entropy returns the entropy from the ULID.
func (id ULID) Entropy() []byte { _ = "STUB: not implemented"; return nil }

// SetEntropy sets the ULID entropy to the passed byte slice.
// ErrDataSize is returned if len(e) != 10.
func (id *ULID) SetEntropy(e []byte) error { _ = "STUB: not implemented"; return nil }

// Compare returns an integer comparing id and other lexicographically.
// The result will be 0 if id==other, -1 if id < other, and +1 if id > other.
func (id ULID) Compare(other ULID) int { _ = "STUB: not implemented"; return 0 }

// Scan implements the sql.Scanner interface. It supports scanning
// a string or byte slice.
func (id *ULID) Scan(src interface{}) error { _ = "STUB: not implemented"; return nil }

// Value implements the sql/driver.Valuer interface, returning the ULID as a
// slice of bytes, by invoking MarshalBinary. If your use case requires a string
// representation instead, you can create a wrapper type that calls String()
// instead.
//
//	type stringValuer ulid.ULID
//
//	func (v stringValuer) Value() (driver.Value, error) {
//	    return ulid.ULID(v).String(), nil
//	}
//
//	// Example usage.
//	db.Exec("...", stringValuer(id))
//
// All valid ULIDs, including zero-value ULIDs, return a valid Value with a nil
// error. If your use case requires zero-value ULIDs to return a non-nil error,
// you can create a wrapper type that special-cases this behavior.
//
//	var zeroValueULID ulid.ULID
//
//	type invalidZeroValuer ulid.ULID
//
//	func (v invalidZeroValuer) Value() (driver.Value, error) {
//	    if ulid.ULID(v).Compare(zeroValueULID) == 0 {
//	        return nil, fmt.Errorf("zero value")
//	    }
//	    return ulid.ULID(v).Value()
//	}
//
//	// Example usage.
//	db.Exec("...", invalidZeroValuer(id))
func (id ULID) Value() (driver.Value, error) {
	_ = "STUB: not implemented"
	return *new(driver.Value), nil
}

// Monotonic returns a source of entropy that yields strictly increasing entropy
// bytes, to a limit governeed by the `inc` parameter.
//
// Specifically, calls to MonotonicRead within the same ULID timestamp return
// entropy incremented by a random number between 1 and `inc` inclusive. If an
// increment results in entropy that would overflow available space,
// MonotonicRead returns ErrMonotonicOverflow.
//
// Passing `inc == 0` results in the reasonable default `math.MaxUint32`. Lower
// values of `inc` provide more monotonic entropy in a single millisecond, at
// the cost of easier "guessability" of generated ULIDs. If your code depends on
// ULIDs having secure entropy bytes, then it's recommended to use the secure
// default value of `inc == 0`, unless you know what you're doing.
//
// The provided entropy source must actually yield random bytes. Otherwise,
// monotonic reads are not guaranteed to terminate, since there isn't enough
// randomness to compute an increment number.
//
// The returned type isn't safe for concurrent use.
func Monotonic(entropy io.Reader, inc uint64) *MonotonicEntropy {
	_ = "STUB: not implemented"
	return nil
}

type rng interface{ Int63n(n int64) int64 }

// LockedMonotonicReader wraps a MonotonicReader with a sync.Mutex for safe
// concurrent use.
type LockedMonotonicReader struct {
	mu sync.Mutex
	MonotonicReader
}

// MonotonicRead synchronizes calls to the wrapped MonotonicReader.
func (r *LockedMonotonicReader) MonotonicRead(ms uint64, p []byte) (err error) {
	_ = "STUB: not implemented"
	return nil
}

// MonotonicEntropy is an opaque type that provides monotonic entropy.
type MonotonicEntropy struct {
	io.Reader
	ms      uint64
	inc     uint64
	entropy uint80
	rand    [8]byte
	rng     rng
}

// MonotonicRead implements the MonotonicReader interface.
func (m *MonotonicEntropy) MonotonicRead(ms uint64, entropy []byte) (err error) {
	_ = "STUB: not implemented"
	return nil
}

// increment the previous entropy number with a random number
// of up to m.inc (inclusive).
func (m *MonotonicEntropy) increment() error { _ = "STUB: not implemented"; return nil }

// random returns a uniform random value in [1, m.inc), reading entropy
// from m.Reader. When m.inc == 0 || m.inc == 1, it returns 1.
// Adapted from: https://golang.org/pkg/crypto/rand/#Int
func (m *MonotonicEntropy) random() (inc uint64, err error) {
	_ = "STUB: not implemented"
	return 0, nil
}

// Fast path for using a underlying rand.Rand directly.

// Range: [1, m.inc)

// bitLen is the maximum bit length needed to encode a value < m.inc.

// byteLen is the maximum byte length needed to encode a value < m.inc.

// msbitLen is the number of bits in the most significant byte of m.inc-1.

// Clear bits in the first byte to increase the probability
// that the candidate is < m.inc.

// Convert the read bytes into an uint64 with byteLen
// Optimized unrolled loop.

// Range: [1, m.inc)

type uint80 struct {
	Hi uint16
	Lo uint64
}

func (u *uint80) SetBytes(bs []byte) { _ = "STUB: not implemented"; return }

func (u *uint80) AppendTo(bs []byte) { _ = "STUB: not implemented"; return }

func (u *uint80) Add(n uint64) (overflow bool) { _ = "STUB: not implemented"; return false }

func (u uint80) IsZero() bool { _ = "STUB: not implemented"; return false }
