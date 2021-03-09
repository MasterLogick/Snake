package net.ddns.masterlogick;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedList;

public class Snake {
    private final Color color;
    private final LinkedList<Byte> turns;
    public Point start;
    private byte lastSignal = 1;

    public Snake(Color color, int x, int y, LinkedList<Byte> turns) {
        start = new Point(x, y);
        this.turns = turns;
        this.color = color;
    }

    public static Snake readFromStream(InputStream in) throws IOException {
        byte[] tmp = new byte[Integer.BYTES * 4];
        in.read(tmp);
        ByteBuffer bb = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN);
        Color c = new Color(bb.getInt());
        int x = bb.getInt();
        int y = bb.getInt();
        int size = bb.getInt();
        LinkedList<Byte> turns = new LinkedList<>();
        tmp = new byte[size];
        in.read(tmp);
        for (int i = 0; i < size; i++) {
            turns.add(tmp[i]);
        }
        return new Snake(c, x, y, turns);
    }

    private static void add(Point p, byte b) {
        int i = (b & 0b10000000) == 0 ? 1 : -1;
        p.x += i * (b & 0b01);
        p.y += i * ((b & 0b10) >> 1);
    }

    public byte go(byte signal, int rows, int columns, Point[] apples) {
        Point current = new Point(start);
        add(current, signal);
        if (current.x < rows && current.x >= 0 && current.y < columns && current.y >= 0 && !contains(current.x, current.y)) {
            turns.addFirst(signal);
            boolean ate = false;
            for (int i = 0; i < apples.length; i++) {
                if (current.equals(apples[i])) ate = true;
            }
            if (!ate && ((signal & 0b100) == 0))
                turns.removeLast();
            else {
                signal = (byte) (signal | 0b100);
            }
            start = current;
            lastSignal = (byte) (signal & 0b11111011);
            return signal;
        }
        return 0;
    }

    public void sendToStream(OutputStream out) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(Integer.BYTES * 4 + turns.size()).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(color.getRGB()).putInt(start.x).putInt(start.y).putInt(turns.size());
        for (byte turn : turns) {
            bb.put(turn);
        }
        out.write(bb.array());
        out.flush();
    }

    public void draw(Graphics2D g, int rows, int columns, int width, int height) {
        Point current = new Point(start);
        int squareWidth = (int) Math.ceil(((float) width) / columns);
        int squareHeight = (int) Math.ceil(((float) height) / rows);
        g.setColor(color);
        for (byte b : turns) {
            add(current, (byte) ((b & 0b01111111) | ((~b) & 0b10000000)));
            g.fillRect((int) Math.floor(((float) width * current.x) / columns), (int) Math.floor(((float) height * current.y) / rows),
                    squareWidth, squareHeight);
        }
        current.setLocation(start);
        g.setColor(new Color(0xffffff - color.getRGB()));
        g.fillRect((int) Math.floor(((float) width * current.x) / columns), (int) Math.floor(((float) height * current.y) / rows),
                squareWidth, squareHeight);
    }

    public boolean contains(int x, int y) {
        Point current = new Point(start);
        Point dst = new Point(x, y);
        if (current.x == dst.x && current.y == dst.y) return true;
        for (byte b : turns) {
            add(current, (byte) ((b & 0b01111111) | ((~b) & 0b10000000)));
            if (dst.x == current.x && dst.y == current.y)
                return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "Snake{c=" + color.toString() + ",x=" + start.x + ",y=" + start.y + ",t=" + Arrays.toString(turns.toArray(new Byte[1])) + "}";
    }

    public byte getLastSignal() {
        return lastSignal;
    }
}

