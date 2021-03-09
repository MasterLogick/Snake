package net.ddns.masterlogick;

import java.awt.*;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server extends Thread {
    public static final byte NEW_USER_SIGNAL = 50;
    public static final byte DEATH_SIGNAL = 51;
    public static final byte UPDATE_SIGNAL = 52;
    public static final byte APPLE_SIGNAL = 53;
    public static final byte BAN_SIGNAL = 54;
    private static final Logger log = Logger.getGlobal();
    private final int port;
    private final int rows;
    private final int columns;
    private final Random r = new Random();
    private final ArrayList<Integer> deathList = new ArrayList<>();
    private final ArrayList<InetAddress> banList = new ArrayList<>();
    private final int ups;
    Vector<Handler> clients = new Vector<>();
    HashMap<Integer, LinkedList<Byte>> nextSignal = new HashMap<>();
    private int idCounter = 0;
    private final Point[] apples;

    public Server(int port, int rows, int columns, int ups, int apples) {
        this.port = port;
        this.rows = rows;
        this.columns = columns;
        this.ups = ups;
        setName("Server-Thread");
        this.apples = new Point[apples];
        for (int i = 0; i < apples; i++) {
            this.apples[i] = new Point(r.nextInt(rows), r.nextInt(columns));
        }
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
                            if (!h.s.isClosed()) {
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
                                byte upd = s.go(signal, rows, columns, apples);
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
                            } else deathList.add(h.user.getId());
                        }

                        if (updateApple) {
                            for (int i = 0; i < apples.length; i++) {
                                int finalI = i;
                                clients.forEach(handler -> {
                                    if (handler.getUser().getSnake().contains(apples[finalI].x, apples[finalI].y)) {
                                        apples[finalI] = new Point(r.nextInt(rows), r.nextInt(columns));
                                    }
                                });
                            }
                        }
                        ArrayList<Integer> disconnected = new ArrayList<>();
                        for (Handler handler : clients) {
                            if (!handler.s.isClosed()) {
                                try {
                                    handler.sendUpdate(update, deathList);
                                    if (updateApple) handler.sendAppleUpdateSignal(apples);
                                } catch (IOException e) {
                                    disconnected.add(handler.getUser().getId());
                                }
                            } else {
                                disconnected.add(handler.user.getId());
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
            Thread banThread = new Thread(() -> {
                BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));
                while (true) {
                    String s = null;
                    try {
                        s = bf.readLine();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        int id = Integer.parseInt(s);
                        synchronized (deathList) {
                            clients.forEach(handler -> {
                                if (handler.getUser().getId() == id) {
                                    handler.interrupt();
                                    banList.add(handler.s.getInetAddress());
                                    System.out.println(handler.s.getInetAddress());
                                    try {
                                        handler.out.write(Server.BAN_SIGNAL);
                                        handler.s.close();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                            deathList.add(id);
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
            banThread.setDaemon(true);
            banThread.start();
            ServerSocket ss = new ServerSocket(port);
            while (true) {
                Socket s = ss.accept();
                final boolean[] banned = {false};
                banList.forEach(addr -> {
                    if (addr.equals(s.getInetAddress())) banned[0] = true;
                });
                if (banned[0]) continue;
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
                writeInt(apples.length);
                for (int i = 0; i < apples.length; i++) {
                    writeInt(apples[i].x);
                    writeInt(apples[i].y);
                }
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
                        kbEvent = (byte) (kbEvent & 0b11111011);
                        LinkedList<Byte> sigList = nextSignal.get(user.getId());
                        if ((sigList.isEmpty() && user.getSnake().getLastSignal() != (byte) ((kbEvent & 0b01111111) | ((~kbEvent) & 0b10000000)))
                                || (!sigList.isEmpty() && sigList.getLast() != (byte) ((kbEvent & 0b01111111) | ((~kbEvent) & 0b10000000))))
                            sigList.addLast(kbEvent);
                        if (sigList.size() > 4) sigList.removeFirst();
                    }
                } catch (IOException e) {
                    synchronized (deathList) {
                        deathList.add(user.getId());
                    }
                    try {
                        in.close();
                        out.close();
                        s.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                    return;
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

        public void sendAppleUpdateSignal(Point[] apples) throws IOException {
            synchronized (outWriteLocker) {
                out.write(APPLE_SIGNAL);
                for (int i = 0; i < apples.length; i++) {
                    writeInt(apples[i].x);
                    writeInt(apples[i].y);
                }
                out.flush();
            }
        }
    }
}
