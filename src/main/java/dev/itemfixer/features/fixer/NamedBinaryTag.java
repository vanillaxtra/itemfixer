package dev.itemfixer.features.fixer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class NamedBinaryTag {
    private NamedBinaryTag() {
    }

    public static Saved read(byte[] bytes) throws IOException {
        boolean gzip = bytes.length >= 2 && bytes[0] == 31 && bytes[1] == -117;
        try (DataInputStream in = new DataInputStream(gzip
                ? new GZIPInputStream(new ByteArrayInputStream(bytes))
                : new ByteArrayInputStream(bytes))) {
            int type = in.readUnsignedByte();
            if (type != 10) {
                throw new IOException("player data root is not a compound");
            }
            String name = in.readUTF();
            return new Saved(name, readCompound(in), gzip);
        }
    }

    public static byte[] write(Saved saved) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(saved.gzip ? new GZIPOutputStream(raw) : raw)) {
            out.writeByte(10);
            out.writeUTF(saved.name);
            writeCompound(out, saved.root);
        }
        return raw.toByteArray();
    }

    public static Compound readBody(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            Compound compound = readCompound(in);
            if (in.read() != -1) {
                throw new IOException("trailing bytes");
            }
            return compound;
        }
    }

    public static byte[] writeBody(Compound compound) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(raw)) {
            writeCompound(out, compound);
        }
        return raw.toByteArray();
    }

    private static Compound readCompound(DataInputStream in) throws IOException {
        Compound compound = new Compound();
        while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) {
                return compound;
            }
            compound.put(in.readUTF(), readPayload(in, type));
        }
    }

    private static void writeCompound(DataOutputStream out, Compound compound) throws IOException {
        for (Map.Entry<String, Tag> entry : compound.values.entrySet()) {
            out.writeByte(entry.getValue().type());
            out.writeUTF(entry.getKey());
            writePayload(out, entry.getValue());
        }
        out.writeByte(0);
    }

    private static Tag readPayload(DataInputStream in, int type) throws IOException {
        return switch (type) {
            case 1 -> new NumberTag(1, in.readByte());
            case 2 -> new NumberTag(2, in.readShort());
            case 3 -> new NumberTag(3, in.readInt());
            case 4 -> new NumberTag(4, in.readLong());
            case 5 -> new NumberTag(5, in.readInt());
            case 6 -> new NumberTag(6, in.readLong());
            case 7 -> {
                byte[] data = new byte[in.readInt()];
                in.readFully(data);
                yield new Blob(7, data, null, null);
            }
            case 8 -> new Text(in.readUTF());
            case 9 -> {
                int element = in.readUnsignedByte();
                int length = in.readInt();
                if (length < 0) {
                    throw new IOException("negative list");
                }
                List<Tag> values = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    values.add(readPayload(in, element));
                }
                yield new ListTag(element, values);
            }
            case 10 -> readCompound(in);
            case 11 -> {
                int length = in.readInt();
                int[] data = new int[length];
                for (int i = 0; i < length; i++) {
                    data[i] = in.readInt();
                }
                yield new Blob(11, null, data, null);
            }
            case 12 -> {
                int length = in.readInt();
                long[] data = new long[length];
                for (int i = 0; i < length; i++) {
                    data[i] = in.readLong();
                }
                yield new Blob(12, null, null, data);
            }
            default -> throw new IOException("unknown tag " + type);
        };
    }

    private static void writePayload(DataOutputStream out, Tag tag) throws IOException {
        switch (tag.type()) {
            case 1 -> out.writeByte((int) ((NumberTag) tag).raw);
            case 2 -> out.writeShort((int) ((NumberTag) tag).raw);
            case 3 -> out.writeInt((int) ((NumberTag) tag).raw);
            case 4 -> out.writeLong(((NumberTag) tag).raw);
            case 5 -> out.writeInt((int) ((NumberTag) tag).raw);
            case 6 -> out.writeLong(((NumberTag) tag).raw);
            case 7 -> {
                byte[] data = ((Blob) tag).bytes;
                out.writeInt(data.length);
                out.write(data);
            }
            case 8 -> out.writeUTF(((Text) tag).value);
            case 9 -> {
                ListTag list = (ListTag) tag;
                out.writeByte(list.elementType);
                out.writeInt(list.values.size());
                for (Tag value : list.values) {
                    writePayload(out, value);
                }
            }
            case 10 -> writeCompound(out, (Compound) tag);
            case 11 -> {
                int[] data = ((Blob) tag).ints;
                out.writeInt(data.length);
                for (int value : data) {
                    out.writeInt(value);
                }
            }
            case 12 -> {
                long[] data = ((Blob) tag).longs;
                out.writeInt(data.length);
                for (long value : data) {
                    out.writeLong(value);
                }
            }
            default -> throw new IOException("unknown tag " + tag.type());
        }
    }

    public static final class Saved {
        public final String name;
        public final Compound root;
        public final boolean gzip;

        public Saved(String name, Compound root, boolean gzip) {
            this.name = name;
            this.root = root;
            this.gzip = gzip;
        }
    }

    public interface Tag {
        int type();
    }

    public static final class Compound implements Tag {
        private final LinkedHashMap<String, Tag> values = new LinkedHashMap<>();

        public Tag get(String key) {
            return values.get(key);
        }

        public void put(String key, Tag tag) {
            values.put(key, tag);
        }

        public Tag remove(String key) {
            return values.remove(key);
        }

        @Override
        public int type() {
            return 10;
        }

        private Iterable<? extends Map.Entry<String, Tag>> entries() {
            return values.entrySet();
        }
    }

    public static final class ListTag implements Tag {
        private final int elementType;
        private final List<Tag> values;

        public ListTag(int elementType, List<Tag> values) {
            this.elementType = elementType;
            this.values = values;
        }

        public List<Tag> values() {
            return values;
        }

        @Override
        public int type() {
            return 9;
        }
    }

    public static final class NumberTag implements Tag {
        private final int type;
        private final long raw;

        public NumberTag(int type, long raw) {
            this.type = type;
            this.raw = raw;
        }

        public long asLong() {
            return raw;
        }

        @Override
        public int type() {
            return type;
        }
    }

    public static final class Text implements Tag {
        private final String value;

        public Text(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        @Override
        public int type() {
            return 8;
        }
    }

    public static final class Blob implements Tag {
        private final int type;
        private final byte[] bytes;
        private final int[] ints;
        private final long[] longs;

        public Blob(int type, byte[] bytes, int[] ints, long[] longs) {
            this.type = type;
            this.bytes = bytes;
            this.ints = ints;
            this.longs = longs;
        }

        @Override
        public int type() {
            return type;
        }
    }

    public static void main(String[] args) throws IOException {
        Compound item = new Compound();
        item.put("id", new Text("minecraft:stone"));
        item.put("count", new NumberTag(3, 4));
        item.put("Slot", new NumberTag(1, 100));
        ListTag inventory = new ListTag(10, new ArrayList<>(List.of(item)));
        Compound root = new Compound();
        root.put("Inventory", inventory);
        root.put("note", new Text("hi"));
        root.put("WorldUUIDMost", new NumberTag(4, 7L));
        root.put("WorldUUIDLeast", new NumberTag(4, -3L));
        root.put("bytes", new Blob(7, new byte[] {1, 2}, null, null));
        root.put("ints", new Blob(11, null, new int[] {9}, null));
        root.put("longs", new Blob(12, null, null, new long[] {11L}));
        root.put("empty", new ListTag(0, new ArrayList<>()));
        byte[] gzip = write(new Saved("", root, true));
        Saved read = read(gzip);
        assert read.gzip;
        assert read.name.isEmpty();
        assert ((Text) read.root.get("note")).value().equals("hi");
        assert ((NumberTag) read.root.get("WorldUUIDMost")).asLong() == 7L;
        assert ((NumberTag) read.root.get("WorldUUIDLeast")).asLong() == -3L;
        ListTag list = (ListTag) read.root.get("Inventory");
        Compound back = (Compound) list.values().get(0);
        assert ((Text) back.get("id")).value().equals("minecraft:stone");
        assert ((NumberTag) back.get("Slot")).asLong() == 100L;
        byte[] body = writeBody(back);
        Compound bodyRead = readBody(body);
        assert ((NumberTag) bodyRead.get("count")).asLong() == 4L;
        byte[] plain = write(new Saved("root", root, false));
        assert !read(plain).gzip;
        assert read(plain).name.equals("root");
    }
}
