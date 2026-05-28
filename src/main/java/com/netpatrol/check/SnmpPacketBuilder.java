package com.netpatrol.check;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

class SnmpPacketBuilder {
    static byte[] v2cGet(String community) {
        try {
            byte[] version = tlv(0x02, new byte[]{0x01});
            byte[] communityBytes = community.getBytes("ISO-8859-1");
            byte[] communityValue = tlv(0x04, communityBytes);
            byte[] oid = oid("1.3.6.1.2.1.1.1.0");
            byte[] nullValue = tlv(0x05, new byte[0]);
            byte[] varbind = sequence(concat(oid, nullValue));
            byte[] varbindList = sequence(varbind);
            byte[] requestId = tlv(0x02, new byte[]{0x01});
            byte[] errorStatus = tlv(0x02, new byte[]{0x00});
            byte[] errorIndex = tlv(0x02, new byte[]{0x00});
            byte[] pdu = tlv(0xA0, concat(requestId, errorStatus, errorIndex, varbindList));
            return sequence(concat(version, communityValue, pdu));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] sequence(byte[] value) throws IOException {
        return tlv(0x30, value);
    }

    private static byte[] tlv(int tag, byte[] value) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        writeLength(out, value.length);
        out.write(value);
        return out.toByteArray();
    }

    private static void writeLength(ByteArrayOutputStream out, int length) {
        if (length < 128) {
            out.write(length);
        } else if (length < 256) {
            out.write(0x81);
            out.write(length);
        } else {
            out.write(0x82);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
        }
    }

    private static byte[] oid(String dotted) throws IOException {
        String[] parts = dotted.split("\\.");
        ByteArrayOutputStream value = new ByteArrayOutputStream();
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        value.write(first * 40 + second);
        for (int i = 2; i < parts.length; i++) {
            writeBase128(value, Integer.parseInt(parts[i]));
        }
        return tlv(0x06, value.toByteArray());
    }

    private static void writeBase128(ByteArrayOutputStream out, int number) {
        int[] stack = new int[5];
        int count = 0;
        stack[count++] = number & 0x7F;
        number >>= 7;
        while (number > 0) {
            stack[count++] = 0x80 | (number & 0x7F);
            number >>= 7;
        }
        for (int i = count - 1; i >= 0; i--) {
            out.write(stack[i]);
        }
    }

    private static byte[] concat(byte[]... values) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] value : values) {
            out.write(value);
        }
        return out.toByteArray();
    }
}
