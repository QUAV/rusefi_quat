package com.rusefi.tracing;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Entry {
    private final String name;
    private final Phase phase;
    private double timestampSeconds;

    public Entry(String name, Phase phase, double timestampSeconds) {
        this.name = name;
        this.phase = phase;
        this.timestampSeconds = timestampSeconds;
    }

    private static void AppendKeyValuePair(StringBuilder sb, String x, String y) {
        sb.append('"');
        sb.append(x);
        sb.append("\":\"");
        sb.append(y);
        sb.append('"');
    }

    private static void AppendKeyValuePair(StringBuilder sb, String x, int y) {
        sb.append('"');
        sb.append(x);
        sb.append("\":");
        sb.append(y);
    }

    private static void AppendKeyValuePair(StringBuilder sb, String x, double y) {
        sb.append('"');
        sb.append(x);
        sb.append("\":");
        sb.append(y);
    }

    public static int readInt(DataInputStream in) throws IOException {
        int ch1 = in.read();
        int ch2 = in.read();
        int ch3 = in.read();
        int ch4 = in.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new EOFException();
        return ((ch4 << 24) + (ch3 << 16) + (ch2 << 8) + (ch1 << 0));
    }


    public static List<Entry> parseBuffer(byte[] packet) {
        List<Entry> result = new ArrayList<>();
        double minValue = Double.MAX_VALUE;
        try {
            DataInputStream is = new DataInputStream(new ByteArrayInputStream(packet));
            is.readByte(); // skip TS result code
            int firstTimeStamp = 0;
            for (int i = 0; i < packet.length - 1; i += 8) {
                byte type = is.readByte();
                byte phase = is.readByte();
                byte data = is.readByte();
                byte thread = is.readByte();

                int timestampNt = readInt(is);
                if (i == 0) {
                    firstTimeStamp = timestampNt;
                } else {
                    if (timestampNt < firstTimeStamp) {
                        System.out.println("Dropping the remainder of the packet at " + i + " due to "
                                + timestampNt + " below " + firstTimeStamp);
                        break;
                    }
                }


                double timestampSeconds = timestampNt / 1000000.0;
                minValue = Math.min(minValue, timestampNt);
                Entry e = new Entry("t" + type, Phase.decode(phase), timestampSeconds);
                result.add(e);
            }

            for (Entry e : result)
                e.adjustTimestamp(minValue);


        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    private void adjustTimestamp(double minValue) {
        timestampSeconds -= minValue;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("{");
        AppendKeyValuePair(sb, "name", name);

        sb.append(",");
        AppendKeyValuePair(sb, "ph", phase.toString());
        sb.append(",");
        AppendKeyValuePair(sb, "tid", 0);
        sb.append(",");

        AppendKeyValuePair(sb, "pid", 0);
        sb.append(",");
        AppendKeyValuePair(sb, "ts", timestampSeconds);
        sb.append("}");

        return sb.toString();
    }
}