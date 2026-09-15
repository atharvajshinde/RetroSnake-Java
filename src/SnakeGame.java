import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class SnakeGame {
    public enum State { TITLE, SETTINGS, PLAYING, PAUSED, GAME_OVER, GAME_WON }
    public enum Theme { CLASSIC, GAMEBOY, SYNTHWAVE, HACKER, VIRTUAL_BOY }

    private final int width;
    private final int height;

    private LinkedList<Coordinate> snake;
    private Direction currentDirection;
    private final LinkedList<Direction> inputQueue;

    private Coordinate apple;
    private int appleIdleTicks;
    private int appleMoveCooldown;

    private State state;
    private Theme currentTheme;
    private int score;
    private int highScore;
    private final Random random;

    public SnakeGame(int width, int height) {
        this.width = width;
        this.height = height;
        this.random = new Random();
        this.inputQueue = new LinkedList<>();

        this.currentTheme = Theme.CLASSIC;
        loadData();

        this.state = State.TITLE;
        initBoard();
    }

    private void initBoard() {
        this.snake = new LinkedList<>();
        this.inputQueue.clear();
        this.score = 0;

        int startX = width / 2;
        int startY = height / 2;
        snake.addFirst(new Coordinate(startX, startY));
        snake.addLast(new Coordinate(startX - 1, startY));
        snake.addLast(new Coordinate(startX - 2, startY));

        this.currentDirection = Direction.RIGHT;
        spawnApple();
    }

    public void startGame() { if (state == State.TITLE) state = State.PLAYING; }
    public void openSettings() { if (state == State.TITLE) state = State.SETTINGS; }
    public void closeSettings() { if (state == State.SETTINGS) state = State.TITLE; }

    public void togglePause() {
        if (state == State.PLAYING) state = State.PAUSED;
        else if (state == State.PAUSED) state = State.PLAYING;
    }

    public void resetGame() {
        initBoard();
        state = State.PLAYING;
    }

    public void toggleTheme() {
        int nextOrdinal = (currentTheme.ordinal() + 1) % Theme.values().length;
        currentTheme = Theme.values()[nextOrdinal];
        saveData();
    }

    public void resetHighScore() {
        highScore = 0;
        saveData();
    }

    public void setDirection(Direction newDirection) {
        if (state != State.PLAYING) return;
        Direction lastCommand = inputQueue.isEmpty() ? currentDirection : inputQueue.getLast();
        if (lastCommand == Direction.UP && newDirection == Direction.DOWN) return;
        if (lastCommand == Direction.DOWN && newDirection == Direction.UP) return;
        if (lastCommand == Direction.LEFT && newDirection == Direction.RIGHT) return;
        if (lastCommand == Direction.RIGHT && newDirection == Direction.LEFT) return;
        if (inputQueue.size() < 2) inputQueue.add(newDirection);
    }

    private boolean isSpaceOccupied(Coordinate c) {
        return snake.contains(c) || c.equals(apple);
    }

    public void update() {
        if (state != State.PLAYING) return;

        if (!inputQueue.isEmpty()) currentDirection = inputQueue.poll();

        handlePanickedApple();

        Coordinate newHead = calculateNewHead(snake.getFirst());
        boolean eatingApple = newHead.equals(apple);

        // Standard collision checks
        if (isWallCollision(newHead) || isSelfCollision(newHead, eatingApple)) {
            triggerGameOver();
            return;
        }

        snake.addFirst(newHead);

        if (newHead.equals(apple)) {
            increaseScore(1);
            spawnApple();
        }
        else {
            snake.removeLast();
        }
    }

    private void increaseScore(int amount) {
        score += amount;
        if (score > highScore) {
            highScore = score;
            saveData();
        }
    }

    private void handlePanickedApple() {
        appleIdleTicks++;
        if (appleIdleTicks > 50) {
            appleMoveCooldown--;
            if (appleMoveCooldown <= 0) {
                appleMoveCooldown = 2;
                Coordinate escapeRoute = getAppleEscapeCoordinate();
                if (escapeRoute != null) apple = escapeRoute;
            }
        }
    }

    private Coordinate getAppleEscapeCoordinate() {
        int ax = apple.getX(), ay = apple.getY();
        int hx = snake.getFirst().getX(), hy = snake.getFirst().getY();
        List<Coordinate> options = new ArrayList<>();

        if (hx < ax) options.add(new Coordinate(ax + 1, ay));
        else if (hx > ax) options.add(new Coordinate(ax - 1, ay));
        else {
            options.add(new Coordinate(ax + 1, ay));
            options.add(new Coordinate(ax - 1, ay));
        }

        if (hy < ay) options.add(new Coordinate(ax, ay + 1));
        else if (hy > ay) options.add(new Coordinate(ax, ay - 1));
        else {
            options.add(new Coordinate(ax, ay + 1));
            options.add(new Coordinate(ax, ay - 1));
        }

        options.removeIf(c -> isWallCollision(c) || isSpaceOccupied(c));
        return options.isEmpty() ? null : options.get(random.nextInt(options.size()));
    }

    private Coordinate calculateNewHead(Coordinate head) {
        int nextX = head.getX();
        int nextY = head.getY();
        switch (currentDirection) {
            case UP -> nextY--;
            case DOWN -> nextY++;
            case LEFT -> nextX--;
            case RIGHT -> nextX++;
        }
        return new Coordinate(nextX, nextY);
    }

    private boolean isWallCollision(Coordinate head) {
        return head.getX() < 0 || head.getX() >= width || head.getY() < 0 || head.getY() >= height;
    }

    private boolean isSelfCollision(Coordinate headToCheck, boolean eatingApple) {
        int limit = eatingApple ? snake.size() : snake.size() - 1;
        for (int i = 0; i < limit; i++) {
            if (snake.get(i) == headToCheck) continue;
            if (snake.get(i).equals(headToCheck)) return true;
        }
        return false;
    }

    private void spawnApple() {
        List<Coordinate> free = getFreeSpaces();
        if (!free.isEmpty()) {
            this.apple = free.get(random.nextInt(free.size()));
            this.appleIdleTicks = 0;
            this.appleMoveCooldown = 2;
        } else {
            this.state = State.GAME_WON;
        }
    }

    private List<Coordinate> getFreeSpaces() {
        List<Coordinate> spaces = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Coordinate c = new Coordinate(x, y);
                if (!isSpaceOccupied(c)) spaces.add(c);
            }
        }
        return spaces;
    }

    private void triggerGameOver() { state = State.GAME_OVER; }

    private void loadData() {
        highScore = 0;
        currentTheme = Theme.CLASSIC;
        try {
            String userHome = System.getProperty("user.home");
            Path path = Path.of(userHome, ".retrosnake_data.txt");

            if (Files.exists(path)) {
                String[] data = Files.readString(path).trim().split(",");
                try { highScore = Integer.parseInt(data[0]); } catch (Exception e) {}
                if (data.length > 1) {
                    try { currentTheme = Theme.valueOf(data[1]); } catch (Exception e) {}
                }
            }
        }
        catch (Exception e) {}
    }

    private void saveData() {
        try {
            String userHome = System.getProperty("user.home");
            Path path = Path.of(userHome, ".retrosnake_data.txt");
            Files.writeString(path, highScore + "," + currentTheme.name());
        } catch (Exception e) {}
    }

    public LinkedList<Coordinate> getSnake() { return snake; }
    public Coordinate getApple() { return apple; }
    public int getAppleIdleTicks() { return appleIdleTicks; }
    public State getState() { return state; }
    public Theme getTheme() { return currentTheme; }
    public int getScore() { return score; }
    public int getHighScore() { return highScore; }
    public int getLevel() { return (score / 5) + 1; }
}