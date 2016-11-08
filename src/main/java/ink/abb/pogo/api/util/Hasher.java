/*
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ink.abb.pogo.api.util;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Hasher {

    private static final int HASH_SEED = 0x61247FBF;

    private static final String[] magic_table = {
            "0x95C05F4D1512959E", "0xE4F3C46EEF0DCF07",
            "0x6238DC228F980AD2", "0x53F3E3BC49607092",
            "0x4E7BE7069078D625", "0x1016D709D1AD25FC",
            "0x044E89B8AC76E045", "0xE0B684DDA364BFA1",
            "0x90C533B835E89E5F", "0x3DAF462A74FA874F",
            "0xFEA54965DD3EF5A0", "0x287A5D7CCB31B970",
            "0xAE681046800752F8", "0x121C2D6EAF66EC6E",
            "0xEE8F8CA7E090FB20", "0xCE1AE25F48FE0A52",};

    private static final BigInteger ROUND_MAGIC = U128_hex("0x78F32468CD48D6DE", "0x14C983660183C0AE");
    private static final BigInteger FINAL_MAGIC0 = U128_hex("0xBDB31B10864F3F87");
    private static final BigInteger FINAL_MAGIC1 = U128_hex("0x5B7E9E828A9B8ABD");
    private static final BigInteger UINT128_MAX = U128_hex("0xffffffffffffffffffffffffffffffff");
    private static final BigInteger UINT128_CLEAR1 = U128_hex("0x7fffffffffffffffffffffffffffffff");
    private static final BigInteger UINT128_CLEAR2 = U128_hex("0x3fffffffffffffffffffffffffffffff");
    private static final BigInteger UINT_1 = new BigInteger("1");

    private static BigInteger hi(BigInteger n) {
        return n.shiftRight(32).and(new BigInteger("ffffffff", 16));
    }

    private static BigInteger lo(BigInteger n) {
        return n.and(new BigInteger("ffffffff", 16));
    }

    private static BigInteger U128(String high, String low) {
        return new BigInteger(high).shiftLeft(64).or(new BigInteger(low));
    }

    private static BigInteger U128_hex(String num) {
        if (!num.startsWith("0x")) {
            throw new NumberFormatException("Number was not hex: " + num);
        }
        return new BigInteger(num.substring(2), 16);
    }

    private static BigInteger U128_hex(String high, String low) {
        if (!high.startsWith("0x")) {
            throw new NumberFormatException("High number was not hex: " + high);
        }
        if (!low.startsWith("0x")) {
            throw new NumberFormatException("Low number was not hex: " + low);
        }
        return new BigInteger(high.substring(2), 16).shiftLeft(64).or(new BigInteger(low.substring(2), 16));
    }

    private static BigInteger read_int64(byte[] p) {
        long n = 0;
        for (int i = 7; i >= 0; i--) {
            n = (n << 8) | (p[i] & 0xff);
        }
        return fromLong(n);
    }

    public static int hash32(byte[] buffer) {
        return hash32salt(buffer, intToByteArray(HASH_SEED));
    }

    public static int hash32salt(byte[] buffer, byte[] salt) {
        long result = hash64salt(buffer, salt).longValue();
        return (int) ((result & 0xffffffffL) ^ (result >>> 32));
    }

    public static BigInteger hash64(byte[] buffer) {
        return hash64salt(buffer, intToByteArray(HASH_SEED));
    }

    public static BigInteger hash64salt(byte[] buffer, byte[] salt) {
        byte[] newBuffer = new byte[buffer.length + salt.length];
        System.arraycopy(salt, 0, newBuffer, 0, salt.length);
        System.arraycopy(buffer, 0, newBuffer, 0 + salt.length, buffer.length);
        return compute_hash(newBuffer);
    }

    public static long hash64salt64(byte[] buffer, long s) {
        byte[] salt = ByteBuffer.allocate(8).putLong(s).array();
        return hash64salt(buffer, salt).longValue();
    }

    public static BigInteger compute_hash(byte[] in) {
        int len = in.length;
        int num_chunks = len / 128;

        // copy tail, pad with zeroes
        byte[] tail = new byte[128];
        int tail_size = len % 128;
        System.arraycopy(in, len - tail_size, tail, 0, tail_size);

        BigInteger hash;
        if (num_chunks > 0) {
            // Hash the first 128 bytes
            hash = hash_chunk(in, 128);
        } else {
            // Hash the tail
            hash = hash_chunk(tail, tail_size);
        }

        hash = fakeOverflow(hash.add(ROUND_MAGIC));

        int in_offset = 128;
        if (num_chunks > 0) {
            while (--num_chunks > 0) {
                hash = hash_muladd(hash, ROUND_MAGIC, hash_chunk(Arrays.copyOfRange(in, in_offset, in_offset + 128), 128));
                in_offset += 128;
            }

            if (tail_size > 0) {
                hash = hash_muladd(hash, ROUND_MAGIC, hash_chunk(tail, tail_size));
            }
        }

        BigInteger x01 = new BigInteger("101", 16);

        // Finalize the hash
        hash = fakeOverflow(hash.add(U128("" + (tail_size * 8), "0")));
        if (hash.compareTo(U128_hex("0x7fffffffffffffff", "0xffffffffffffffff")) >= 0) {
            hash = fakeOverflow(hash.add(UINT_1));
        }
        hash = clearHighBits1(hash);

        long hash_high = hash.shiftRight(64).longValue();
        long hash_low = hash.longValue();
        long X = hash_high + hi(hash_low);
        X = hi(X + hi(X) + 1) + hash_high;
        long Y = (X << 32) + hash_low;

        long A = X + FINAL_MAGIC0.longValue();
        if (fromLong(A).compareTo(fromLong(X)) < 0) {
            A += 0x101;
        }

        long B = Y + FINAL_MAGIC1.longValue();
        if (fromLong(B).compareTo(fromLong(Y)) < 0) {
            B += 0x101;
        }

        BigInteger H = fakeOverflow(fromLong(A).multiply(fromLong(B)));
        H = fakeOverflow(fakeOverflow(x01.multiply(H.shiftRight(64))).add(fromLong(H.longValue())));
        H = fakeOverflow(fakeOverflow(x01.multiply(H.shiftRight(64))).add(fromLong(H.longValue())));
        if (H.shiftRight(64).longValue() > 0) {
            H = H.add(x01);
        }
        if (H.compareTo(new BigInteger("FFFFFFFFFFFFFEFE", 16)) > 0) {
            H = H.add(x01);
        }
        return H;
    }

    private static BigInteger fakeOverflow(BigInteger in) {
        return in.and(UINT128_MAX);
    }

    private static BigInteger clearHighBits1(BigInteger in) {
        return in.and(UINT128_CLEAR1);
    }
    private static BigInteger clearHighBits2(BigInteger in) {
        return in.and(UINT128_CLEAR2);
    }

    private static BigInteger hash_chunk(byte[] chunk, long size) {
        BigInteger hash = new BigInteger("0");
        for (int i = 0; i < 8; i++) {
            int offset = i * 16;
            if (offset >= size) {
                break;
            }
            BigInteger a = read_int64(Arrays.copyOfRange(chunk, offset, offset + 8));
            BigInteger b = read_int64(Arrays.copyOfRange(chunk, offset + 8, offset + 16));

            BigInteger tmp = fromLong(a.longValue() + U128_hex(magic_table[i * 2]).longValue());
            BigInteger tmp2 = fromLong(b.longValue() + U128_hex(magic_table[i * 2 + 1]).longValue());
            BigInteger mul = fakeOverflow(tmp.multiply(tmp2));

            hash = fakeOverflow(hash.add(mul));
        }
        return clearHighBits2(hash);
    }

    private static BigInteger fromLong(long l) {
        //"cheat" using hex conversions (lazy, I know)
        return new BigInteger(Long.toHexString(l), 16);
    }

    private static long hi(long n) {
        return n >>> 32;
    }

    private static long lo(long n) {
        return (n & 0xffffffffL);
    }

    public static byte[] intToByteArray(int i) {
        byte[] ret = new byte[4];
        ret[3] = (byte) (i & 0xFF);
        ret[2] = (byte) ((i >> 8) & 0xFF);
        ret[1] = (byte) ((i >> 16) & 0xFF);
        ret[0] = (byte) ((i >> 24) & 0xFF);
        return ret;
    }

    private static BigInteger hash_muladd(BigInteger hash, BigInteger mul, BigInteger add) {
        long a0 = lo(add).longValue();
        long a1 = hi(add).longValue();
        long a23 = add.shiftRight(64).longValue();
        long m0 = lo(mul).longValue();
        long m1 = hi(mul).longValue();
        long m2 = lo(mul.shiftRight(64)).longValue();
        long m3 = hi(mul.shiftRight(64)).longValue();
        long h0 = lo(hash).longValue();
        long h1 = hi(hash).longValue();
        long h2 = lo(hash.shiftRight(64)).longValue();
        long h3 = hi(hash.shiftRight(64)).longValue();

		/* Column sums, before carry */
        long c0 = (h0 * m0);
        long c1 = (h0 * m1) + (h1 * m0);
        long c2 = (h0 * m2) + (h1 * m1) + (h2 * m0);
        long c3 = (h0 * m3) + (h1 * m2) + (h2 * m1) + (h3 * m0);
        long c4 = (h1 * m3) + (h2 * m2) + (h3 * m1);
        long c5 = (h2 * m3) + (h3 * m2);
        long c6 = (h3 * m3);

		/* Combine, add, and carry (bugs included) */
        long r2 = c2 + (c6 << 1) + a23;
        long r3 = c3 + hi(r2);
        long r0 = c0 + (c4 << 1) + a0 + (r3 >>> 31);
        long r1 = c1 + (c5 << 1) + a1 + hi(r0);

		/* Return as uint128_t */
        BigInteger result = fromLong(r3 << 33 >>> 1).or(fromLong(lo(r2))).add(fromLong(hi(r1)));
        result = fakeOverflow(result);
        return fakeOverflow(result.shiftLeft(64)).or(fromLong(r1 << 32)).or(fromLong(lo(r0)));
    }
}
