package net.ddns.masterlogick;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;

public class Field extends Canvas {
    private final HashMap<Integer, Snake> snakes = new HashMap<>();
    private final int rows;
    private final int columns;
    private BufferedImage image;
    private Graphics2D g;
    private Point[] apples;

    public Field(int rows, int columns) {
        this.rows = rows;
        this.columns = columns;
        image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        g = image.createGraphics();
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                image = new BufferedImage(e.getComponent().getWidth(), e.getComponent().getHeight(), BufferedImage.TYPE_INT_RGB);
                g = image.createGraphics();
            }
        });
    }

    @Override
    public void paint(Graphics gr) {
        g.setColor(Color.WHITE);
        g.clearRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.RED);
        int squareWidth = (int) Math.ceil(((float) image.getWidth()) / columns);
        int squareHeight = (int) Math.ceil(((float) image.getHeight()) / rows);
        for (int i = 0; i < apples.length; i++) {
            g.fillRect((int) Math.floor(((float) image.getWidth() * apples[i].x) / columns), (int) Math.floor(((float) image.getHeight() * apples[i].y) / rows),
                    squareWidth, squareHeight);
        }
        snakes.values().forEach(snake -> snake.draw(g, rows, columns, image.getWidth(), image.getHeight()));
        gr.drawImage(image, 0, 0, null);
    }

    @Override
    public void update(Graphics g) {
        paint(g);
    }

    public void addNewSnake(int id, Snake s) {
        snakes.put(id, s);
    }

    public Snake getSnake(int id) {
        return snakes.get(id);
    }

    public void removeSnake(int id) {
        snakes.remove(id);
    }

    public Point[] getApples() {
        return apples;
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(100, 100);
    }

    public void setApples(Point[] apples) {
        this.apples = apples;
    }
}
