package net.ddns.masterlogick;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Function;

public class Client extends Thread {
    private final int id;
    private final InputStream in;
    private final OutputStream out;
    private final Field f;
    private final int rows;
    private final int columns;
    private final Socket s;
    private final Function<Void, Void> onDeath;
    private boolean alive = true;

    public Client(String address, int port, Color userColor, Function<Void, Void> onDeath) throws IOException {
        this.onDeath = onDeath;
        setName("Client-Thread");
        s = new Socket(address, port);
        in = s.getInputStream();
        out = s.getOutputStream();
        sendColor(userColor);
        id = readInt();
        rows = readInt();
        columns = readInt();
        f = new Field(rows, columns);
        f.setApple(new Point(readInt(), readInt()));
        f.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int keyCode = e.getKeyCode();
                if (keyCode == KeyEvent.VK_W || keyCode == KeyEvent.VK_UP) {
                    try {
                        sendKBEvent(0b10000010);
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                } else if (keyCode == KeyEvent.VK_A || keyCode == KeyEvent.VK_LEFT) {
                    try {
                        sendKBEvent(0b10000001);
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                } else if (keyCode == KeyEvent.VK_S || keyCode == KeyEvent.VK_DOWN) {
                    try {
                        sendKBEvent(0b00000010);
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                } else if (keyCode == KeyEvent.VK_D || keyCode == KeyEvent.VK_RIGHT) {
                    try {
                        sendKBEvent(0b00000001);
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
            }
        });
        Snake snake = Snake.readFromStream(in);
        f.addNewSnake(id, snake);
    }

    public void sendKBEvent(int event) throws IOException {
        if (!s.isClosed()) {
            out.write((byte) event);
            out.flush();
        }
    }

    @Override
    public void run() {
        try {
            while (alive) {
                byte msgType = (byte) in.read();
                switch (msgType) {
                    case Server.NEW_USER_SIGNAL: {
                        int newId = readInt();
                        Snake newSnake = Snake.readFromStream(in);
                        f.addNewSnake(newId, newSnake);
                    }
                    break;
                    case Server.DEATH_SIGNAL: {
                        int count = readInt();
                        for (int i = 0; i < count; i++) {
                            int id = readInt();
                            f.removeSnake(id);
                            if (id == this.id) {
                                in.close();
                                out.close();
                                onDeath.apply(null);
                                return;
                            }
                        }
                        break;
                    }
                    case Server.UPDATE_SIGNAL: {
                        int count = readInt();
                        for (int i = 0; i < count; i++) {
                            int id = readInt();
                            byte change = (byte) in.read();
                            Snake s = f.getSnake(id);
                            if (s != null)
                                s.go(change, rows, columns, new Point(0, 0));
                        }
                        SwingUtilities.updateComponentTreeUI(f);
                    }
                    break;
                    case Server.APPLE_SIGNAL: {
                        f.setApple(new Point(readInt(), readInt()));
                    }
                    case Server.BAN_SIGNAL:
                        in.close();
                        out.close();
                        onDeath.apply(null);
                        return;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            try {
                close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    private void sendColor(Color color) throws IOException {
        writeInt(color.getRGB());
        out.flush();
    }

    public Field getField() {
        return f;
    }

    private int readInt() throws IOException {
        byte[] tmp = new byte[Integer.BYTES];
        in.read(tmp);
        return ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private void writeInt(int i) throws IOException {
        out.write(ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(i).array());
    }

    public void close() throws IOException {
        sendKBEvent(Server.DEATH_SIGNAL);
        alive = false;
        in.close();
        out.close();
        s.close();
    }
}
