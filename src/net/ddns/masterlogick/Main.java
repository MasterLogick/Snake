package net.ddns.masterlogick;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.function.Function;

public class Main {
    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--test")) {
            startServer(1555, 100, 100, 1, 3);
            startClient("127.0.0.1", 1555, Color.RED);
            startClient("127.0.0.1", 1555, Color.BLUE);
        } else if (args.length == 6 && args[0].equals("--dedicated-server"))
            startServer(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]));
        else if (args.length > 0 && args[0].equals("--dedicated-server")) {
            System.out.println("Usage: java -jar Snake.jar --dedicated-server port width height ups apples");
        } else if (args.length == 1 && args[0].equals("--server")) {
            JFrame f = new JFrame("SNAAAAAAKE!!!-Server");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setLocationRelativeTo(null);
            JPanel root = new JPanel();
            root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
            JColorChooser jcc = new JColorChooser(Color.BLUE);
            root.add(jcc);
            JPanel p1 = new JPanel();
            p1.setLayout(new BoxLayout(p1, BoxLayout.X_AXIS));
            p1.add(new JLabel("Port:"));
            JTextField port = new JTextField(6);
            p1.add(port);
            root.add(p1);
            JPanel p2 = new JPanel();
            p2.setLayout(new BoxLayout(p2, BoxLayout.X_AXIS));
            p2.add(new JLabel("Width:"));
            JTextField width = new JTextField(6);
            p2.add(width);
            root.add(p2);
            JPanel p3 = new JPanel();
            p3.setLayout(new BoxLayout(p3, BoxLayout.X_AXIS));
            p3.add(new JLabel("Height:"));
            JTextField height = new JTextField(6);
            p3.add(height);
            root.add(p3);
            JPanel p4 = new JPanel();
            p4.setLayout(new BoxLayout(p4, BoxLayout.X_AXIS));
            p4.add(new JLabel("UPS:"));
            JTextField ups = new JTextField(6);
            p4.add(ups);
            root.add(p4);
            JPanel p5 = new JPanel();
            p5.setLayout(new BoxLayout(p5, BoxLayout.X_AXIS));
            p5.add(new JLabel("Apples:"));
            JTextField apples = new JTextField(6);
            p5.add(apples);
            root.add(p5);
            JPanel p6 = new JPanel();
            p6.setLayout(new BoxLayout(p6, BoxLayout.X_AXIS));
            JButton startButton = new JButton("Start server");
            startButton.addActionListener(e -> {
                int portVal = Integer.parseInt(port.getText());
                int widthVal = Integer.parseInt(width.getText());
                int heightVal = Integer.parseInt(height.getText());
                int upsVal = Integer.parseInt(ups.getText());
                int app = Integer.parseInt(apples.getText());
                startServer(portVal, widthVal, heightVal, upsVal, app);
                startClient("127.0.0.1", portVal, jcc.getColor());
                f.dispose();
            });
            p6.add(startButton);
            root.add(p6);
            f.setContentPane(root);
            f.pack();
            f.setResizable(false);
            f.setVisible(true);
        } else {
            JFrame f = new JFrame("SNAAAAAAKE!!!-Client");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setLocationRelativeTo(null);
            JPanel root = new JPanel();
            root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
            JColorChooser jcc = new JColorChooser(Color.BLUE);
            root.add(jcc);
            JPanel p1 = new JPanel();
            p1.setLayout(new BoxLayout(p1, BoxLayout.X_AXIS));
            p1.add(new JLabel("Host:"));
            JTextField host = new JTextField(15);
            p1.add(host);
            root.add(p1);
            JPanel p2 = new JPanel();
            p2.setLayout(new BoxLayout(p2, BoxLayout.X_AXIS));
            p2.add(new JLabel("Port:"));
            JTextField port = new JTextField(6);
            p2.add(port);
            root.add(p2);
            JPanel p3 = new JPanel();
            p3.setLayout(new BoxLayout(p3, BoxLayout.X_AXIS));
            JButton startButton = new JButton("Connect");
            startButton.addActionListener(e -> {
                String hostVal = host.getText();
                int portVal = Integer.parseInt(port.getText());
                if (!hostVal.isEmpty()) {
                    startClient(hostVal, portVal, jcc.getColor());
                    f.dispose();
                }
            });
            p3.add(startButton);
            root.add(p3);
            f.setContentPane(root);
            f.pack();
            f.setResizable(false);
            f.setVisible(true);
        }
    }

    private static void startServer(int port, int width, int height, int ups, int apples) {
        new Server(port, width, height, ups, apples).start();
    }

    private static void startClient(String address, int port, Color color) {
        JFrame fr = new JFrame("SNAAAAAAKE!!!");
        fr.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        fr.setLocationRelativeTo(null);
        final Client[] c = {null};
        Function<Void, Void>[] onDeath = new Function[]{null};
        onDeath[0] = unused -> {
            try {
                c[0] = new Client(address, port, color, onDeath[0]);
                fr.getContentPane().removeAll();
                fr.add(c[0].getField());
                c[0].getField().requestFocus();
                c[0].start();
                SwingUtilities.updateComponentTreeUI(fr);
            } catch (IOException e) {
                System.exit(0);
            }
            return null;
        };
        try {
            c[0] = new Client(address, port, color, onDeath[0]);
        } catch (IOException e) {
            System.exit(0);
        }
        fr.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                c[0].getField().requestFocus();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    c[0].close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
                fr.dispose();
                System.exit(0);
            }
        });
        c[0].start();
        fr.add(c[0].getField());
        fr.setSize(500, 500);
        fr.setVisible(true);
    }
}
