package net.rasanovum.roxy.legacy.loader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Rebinds a single class ABI in memory. No patched Voxy class is written to disk. */
final class MapperReferencePatch {

    private static final String ORIGINAL = "me/cortex/voxy/common/world/other/Mapper";
    private static final String ADAPTER = "net/rasanovum/roxy/legacy/bridge/LegacyMapper";

    private MapperReferencePatch() {}

    static byte[] apply(byte[] source) throws IOException {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(source));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(source.length + 256);
        DataOutputStream output = new DataOutputStream(bytes);
        if (input.readInt() != 0xCAFEBABE) {
            throw new IOException("Invalid Voxy class header");
        }
        output.writeInt(0xCAFEBABE);
        output.writeShort(input.readUnsignedShort());
        output.writeShort(input.readUnsignedShort());
        int count = input.readUnsignedShort();
        output.writeShort(count);
        int replacements = 0;
        for (int i = 1; i < count; i++) {
            int tag = input.readUnsignedByte();
            output.writeByte(tag);
            switch (tag) {
                case 1:
                    String value = input.readUTF();
                    String patched = value.equals(ORIGINAL) ? ADAPTER
                        : value.replace("L" + ORIGINAL + ";", "L" + ADAPTER + ";");
                    if (!value.equals(patched)) {
                        replacements++;
                    }
                    output.writeUTF(patched);
                    break;
                case 3:
                case 4:
                case 9:
                case 10:
                case 11:
                case 12:
                case 17:
                case 18:
                    output.writeInt(input.readInt());
                    break;
                case 5:
                case 6:
                    output.writeLong(input.readLong());
                    i++;
                    break;
                case 7:
                case 8:
                case 16:
                case 19:
                case 20:
                    output.writeShort(input.readUnsignedShort());
                    break;
                case 15:
                    output.writeByte(input.readUnsignedByte());
                    output.writeShort(input.readUnsignedShort());
                    break;
                default:
                    throw new IOException("Unsupported constant-pool tag " + tag);
            }
        }
        if (replacements == 0) {
            throw new IOException("The expected Voxy Mapper ABI was not found");
        }
        byte[] tail = new byte[input.available()];
        input.readFully(tail);
        output.write(tail);
        return bytes.toByteArray();
    }
}
