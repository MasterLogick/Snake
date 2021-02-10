package net.ddns.masterlogick;

public class User {
    private final Snake snake;
    private final int id;

    public User(Snake snake, int id) {
        this.snake = snake;
        this.id = id;
    }

    public Snake getSnake() {
        return snake;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return "User{id=" + id + ",s=" + snake.toString() + "}";
    }
}
