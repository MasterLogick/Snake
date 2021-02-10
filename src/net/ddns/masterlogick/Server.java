package net.ddns.masterlogick;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server extends Thread {
    public static final byte NEW_USER_SIGNAL = 12;
    public static final byte DEATH_SIGNAL = 13;
    public static final byte UPDATE_SIGNAL = 14;
    public static final byte APPLE_SIGNAL = 15;
    private static final Logger log = Logger.getGlobal();
    private final int port;
    private final int rows;
    private final int columns;
    private final Random r = new Random();
    private final ArrayList<Integer> deathList = new ArrayList<>();
    private final int ups;
    Vector<Handler> clients = new Vector<>();
    HashMap<Integer, LinkedList<Byte>> nextSignal = new HashMap<>();
    private int idCounter = 0;
    private Point apple;

    public Server(int port, int rows, int columns, int ups) {
        this.port = port;
        this.rows = rows;
        this.columns = columns;
        this.ups = ups;
        setName("Server-Thread");
        apple = new Point(r.nextInt(rows), r.nextInt(columns));
    }

    @Override
    public void run() {
        try {
            Thread gameCycle = new Thread(() -> {
                while (true) {
                    boolean updateApple = false;
                    long last = System.currentTimeMillis();
                    synchronized (deathList) {
                        ByteBuffer update = ByteBuffer.allocate((Integer.BYTES + 1) * clients.size() + Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
                        update.putInt(clients.size());
                        for (int i = 0; i < nextSignal.size(); i++) {
                            Handler h = clients.get(i);
                            Snake s = h.user.getSnake();
                            byte signal;
                            byte nextSig = nextSignal.get(h.user.getId()).isEmpty() ? s.getLastSignal() : nextSignal.get(h.user.getId()).removeFirst();
                            if ((s.getLastSignal() & 0b11) == (nextSig & 0b11) &&
                                    ((nextSig ^ s.getLastSignal()) & 0b10000000) == 0) {
                                signal = s.getLastSignal();
                            } else {
                                signal = nextSig;
                            }
                            update.putInt(h.user.getId());
                            byte upd = s.go(signal, rows, columns, apple);
                            updateApple = updateApple || ((upd & 0b100) != 0);
                            update.put(upd);
                            AtomicBoolean intersect = new AtomicBoolean(false);
                            clients.forEach(handler -> {
                                if (handler != h)
                                    intersect.set(intersect.get() || handler.getUser().getSnake().contains(h.user.getSnake().start.x, h.user.getSnake().start.y));
                            });
                            if (upd == 0 || intersect.get()) {
                                deathList.add(h.user.getId());
                                log.log(Level.INFO, "User " + h.user.getId() + " died");
                            }
                        }
                        if (updateApple) apple = new Point(r.nextInt(rows), r.nextInt(columns));
                        ArrayList<Integer> disconnected = new ArrayList<>();
                        for (Handler handler : clients) {
                            try {
                                handler.sendUpdate(update, deathList);
                                if (updateApple) handler.sendAppleUpdateSignal(apple);
                            } catch (IOException e) {
                                disconnected.add(handler.getUser().getId());
                            }
                        }
                        deathList.forEach(id -> nextSignal.remove(id));
                        clients.removeIf(handler -> deathList.contains(handler.user.getId()));
                        deathList.clear();
                        deathList.addAll(disconnected);
                    }
                    try {
                        Thread.sleep(Math.max(0, last + (1000 / ups) - System.currentTimeMillis()));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            gameCycle.setDaemon(true);
            gameCycle.start();
            ServerSocket ss = new ServerSocket(port);
            while (true) {
                Socket s = ss.accept();
                Handler h = new Handler(s, idCounter);
                h.start();
                idCounter++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Snake createNewSnake(Color color) {
        bbb:
        while (true) {
            int x = r.nextInt(columns - 4);
            int y = r.nextInt(rows);
            for (Handler client : clients) {
                if (client.getUser().getSnake().contains(x, y)) continue bbb;
            }
            LinkedList<Byte> l = new LinkedList<>();
            for (int j = 0; j < 3; j++) {
                l.add((byte) 0);
            }
            return new Snake(color, x, y, l);
        }
    }

    private class Handler extends Thread {
        private final InputStream in;
        private final OutputStream out;
        private final Object outWriteLocker = new Object();
        private final User user;
        private final Socket s;
        private boolean alive = true;

        private Handler(Socket s, int id) throws IOException {
            this.s = s;
            in = s.getInputStream();
            out = s.getOutputStream();
            Snake sn = createNewSnake(new Color(readInt()));
            user = new User(sn, id);
            synchronized (outWriteLocker) {
                writeInt(user.getId());
                writeInt(rows);
                writeInt(columns);
                writeInt(apple.x);
                writeInt(apple.y);
                out.flush();
                user.getSnake().sendToStream(out);
                log.log(Level.INFO, "New user connected: " + s.getInetAddress() + " " + user.toString());
            }
            synchronized (deathList) {
                clients.forEach(handler -> {
                    try {
                        handler.sendNewPlayerData(user);
                        sendNewPlayerData(handler.getUser());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                clients.add(this);
                LinkedList<Byte> queue = new LinkedList<>();
                queue.addLast(user.getSnake().getLastSignal());
                nextSignal.put(user.getId(), queue);
            }
            setName("Handler-" + id);
        }

        @Override
        public void run() {
            while (alive) {
                try {
                    byte kbEvent = readKBEvent();
                    if (kbEvent == DEATH_SIGNAL) {
                        synchronized (deathList) {
                            deathList.add(user.getId());
                        }
                        in.close();
                        out.close();
                        s.close();
                        return;
                    } else if (nextSignal.containsKey(user.getId())) {
                        LinkedList<Byte> sigList = nextSignal.get(user.getId());
                        if (sigList.isEmpty() || sigList.getLast() != kbEvent)
                            sigList.addLast(kbEvent);
                        if (sigList.size() > UPDATE_SIGNAL) sigList.removeFirst();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private User getUser() {
            return user;
        }

        private byte readKBEvent() throws IOException {
            return (byte) in.read();
        }

        private void sendNewPlayerData(User user) throws IOException {
            synchronized (outWriteLocker) {
                out.write(NEW_USER_SIGNAL);
                writeInt(user.getId());
                out.flush();
                user.getSnake().sendToStream(out);
            }
        }

        private void sendUpdate(ByteBuffer update, ArrayList<Integer> died) throws IOException {
            synchronized (outWriteLocker) {
                out.write(new byte[]{Server.UPDATE_SIGNAL});
                out.write(update.array());
                out.write(new byte[]{DEATH_SIGNAL});
                writeInt(died.size());
                for (int id : died) {
                    writeInt(id);
                    if (id == user.getId()) alive = false;
                }
                out.flush();
            }
        }

        private int readInt() throws IOException {
            byte[] tmp = new byte[Integer.BYTES];
            in.read(tmp);
            return ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();
        }

        private void writeInt(int i) throws IOException {
            out.write(ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(i).array());
        }

        public void sendAppleUpdateSignal(Point apple) throws IOException {
            synchronized (outWriteLocker) {
                out.write(APPLE_SIGNAL);
                writeInt(apple.x);
                writeInt(apple.y);
                out.flush();
            }
        }
    }
}
