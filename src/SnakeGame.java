import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class SnakeGame {
    public enum State {
        TITLE, SETTINGS, PLAYING, PAUSED, GAME_OVER, GAME_WON, STARTUP
    }

    public enum BoardSize {
        SMALL(16), MEDIUM(24), LARGE(32);

        private final int size;

        BoardSize(int size) {
            this.size = size;
        }

        public int getSize() {
            return size;
        }
    }

    public enum Difficulty {
        EASY(150), NORMAL(120), HARD(90);

        private final int baseDelay;

        Difficulty(int baseDelay) {
            this.baseDelay = baseDelay;
        }

        public int getBaseDelay() {
            return baseDelay;
        }
    }

    private int width;
    private int height;
    private BoardSize boardSize;
    private double scale;
    private Difficulty difficulty = Difficulty.NORMAL;
    private boolean musicEnabled = true;
    private boolean dashEnabled = true;
    private int applesEaten;

    private LinkedList<Coordinate> snake;
    private Direction currentDirection;
    private final LinkedList<Direction> inputQueue;

    private Coordinate apple;

    private State state;
    private int score;
    private int highScore;
    private final Random random;

    private int currentCombo;
    private long lastAppleEatenTime;
    private int lastPointsScored;
    private static final long COMBO_MAX_TIME = 4000;

    public SnakeGame() {
        this.random = new Random();
        this.inputQueue = new LinkedList<>();
        loadData();
        this.state = State.STARTUP;
        initBoard();
    }

    private void initBoard() {
        this.snake = new LinkedList<>();
        this.inputQueue.clear();
        this.score = 0;
        this.applesEaten = 0;
        this.currentCombo = 1;
        this.lastPointsScored = 0;

        int startX = width / 2;
        int startY = height / 2;
        snake.addFirst(new Coordinate(startX, startY));
        snake.addLast(new Coordinate(startX - 1, startY));
        snake.addLast(new Coordinate(startX - 2, startY));

        this.currentDirection = Direction.RIGHT;
        spawnApple();
    }

    public void toggleBoardSize() {
        int nextOrdinal = (boardSize.ordinal() + 1) % BoardSize.values().length;
        boardSize = BoardSize.values()[nextOrdinal];
        this.width = boardSize.getSize();
        this.height = boardSize.getSize();
        saveData();
        initBoard();
    }

    public void toggleScale() {
        scale += 0.5;
        if (scale > 3.0)
            scale = 1.0;
        saveData();
    }

    public void toggleDifficulty() {
        int nextOrdinal = (difficulty.ordinal() + 1) % Difficulty.values().length;
        difficulty = Difficulty.values()[nextOrdinal];
        saveData();
    }

    public void toggleMusic() {
        musicEnabled = !musicEnabled;
        saveData();
    }

    public void toggleDash() {
        dashEnabled = !dashEnabled;
        saveData();
    }

    public void startGame() {
        if (state == State.TITLE)
            state = State.PLAYING;
    }

    public void openSettings() {
        if (state == State.TITLE)
            state = State.SETTINGS;
    }

    public void closeSettings() {
        if (state == State.SETTINGS)
            state = State.TITLE;
    }

    public void togglePause() {
        if (state == State.PLAYING)
            state = State.PAUSED;
        else if (state == State.PAUSED) {
            state = State.PLAYING;
            lastAppleEatenTime = System.currentTimeMillis();
        }
    }

    public void resetGame() {
        initBoard();
        state = State.PLAYING;
    }

    public void resetHighScore() {
        highScore = 0;
        saveData();
    }

    public void setDirection(Direction newDirection) {
        if (state != State.PLAYING)
            return;
        Direction lastCommand = inputQueue.isEmpty() ? currentDirection : inputQueue.getLast();
        if (lastCommand == Direction.UP && newDirection == Direction.DOWN)
            return;
        if (lastCommand == Direction.DOWN && newDirection == Direction.UP)
            return;
        if (lastCommand == Direction.LEFT && newDirection == Direction.RIGHT)
            return;
        if (lastCommand == Direction.RIGHT && newDirection == Direction.LEFT)
            return;
        if (inputQueue.size() < 2)
            inputQueue.add(newDirection);
    }

    private boolean isSpaceOccupied(Coordinate c) {
        return snake.contains(c) || c.equals(apple);
    }

    public void update() {
        if (state != State.PLAYING)
            return;

        if (System.currentTimeMillis() - lastAppleEatenTime > COMBO_MAX_TIME) {
            currentCombo = 1;
        }

        if (!inputQueue.isEmpty())
            currentDirection = inputQueue.poll();

        Coordinate newHead = calculateNewHead(snake.getFirst());
        boolean eatingApple = newHead.equals(apple);

        if (isWallCollision(newHead) || isSelfCollision(newHead, eatingApple)) {
            triggerGameOver();
            return;
        }

        snake.addFirst(newHead);

        if (eatingApple) {
            applesEaten++;
            increaseScore();
            spawnApple();
        } else {
            snake.removeLast();
        }
    }

    private void increaseScore() {
        lastPointsScored = currentCombo;
        score += currentCombo;
        if (score > highScore) {
            highScore = score;
            saveData();
        }
        currentCombo++;
        lastAppleEatenTime = System.currentTimeMillis();
    }

    private Coordinate calculateNewHead(Coordinate head) {
        int nextX = head.getX(), nextY = head.getY();
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
            if (snake.get(i) == headToCheck)
                continue;
            if (snake.get(i).equals(headToCheck))
                return true;
        }
        return false;
    }

    private void spawnApple() {
        List<Coordinate> free = getFreeSpaces();
        if (!free.isEmpty()) {
            this.apple = free.get(random.nextInt(free.size()));
        } else {
            this.state = State.GAME_WON;
        }
    }

    private List<Coordinate> getFreeSpaces() {
        List<Coordinate> spaces = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Coordinate c = new Coordinate(x, y);
                if (!isSpaceOccupied(c))
                    spaces.add(c);
            }
        }
        return spaces;
    }

    private void triggerGameOver() {
        state = State.GAME_OVER;
    }

    private void loadData() {
        highScore = 0;
        boardSize = BoardSize.MEDIUM;
        scale = 2.0;
        difficulty = Difficulty.NORMAL;
        musicEnabled = true;
        dashEnabled = true;
        try {
            Path path = Path.of(System.getProperty("user.home"), ".retrosnake_data.txt");
            if (Files.exists(path)) {
                String[] data = Files.readString(path).trim().split(",");
                if (data.length >= 1)
                    highScore = Integer.parseInt(data[0]);
                if (data.length >= 2)
                    boardSize = BoardSize.valueOf(data[1]);
                if (data.length >= 3)
                    scale = Double.parseDouble(data[2]);
                if (data.length >= 4)
                    difficulty = Difficulty.valueOf(data[3]);
                if (data.length >= 5)
                    musicEnabled = Boolean.parseBoolean(data[4]);
                if (data.length >= 6)
                    dashEnabled = Boolean.parseBoolean(data[5]);
            }
        } catch (Exception e) {
        }
        this.width = boardSize.getSize();
        this.height = boardSize.getSize();
    }

    private void saveData() {
        try {
            Path path = Path.of(System.getProperty("user.home"), ".retrosnake_data.txt");
            Files.writeString(path,
                    highScore + "," + boardSize.name() + "," + scale + "," + difficulty.name() + "," + musicEnabled + "," + dashEnabled);
        } catch (Exception e) {
        }
    }

    public LinkedList<Coordinate> getSnake() {
        return snake;
    }

    public Coordinate getApple() {
        return apple;
    }

    public State getState() {
        return state;
    }

    public void setState(State newState) {
        this.state = newState;
    }

    public int getScore() {
        return score;
    }

    public int getHighScore() {
        return highScore;
    }

    public int getLevel() {
        return (applesEaten / 5) + 1;
    }

    public BoardSize getBoardSize() {
        return boardSize;
    }

    public double getScale() {
        return scale;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public boolean isMusicEnabled() {
        return musicEnabled;
    }

    public boolean isDashEnabled() {
        return dashEnabled;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getCombo() {
        return currentCombo;
    }

    public int getLastPointsScored() {
        return lastPointsScored;
    }

    public double getComboTimerRatio() {
        if (currentCombo <= 1)
            return 0.0;
        double elapsed = System.currentTimeMillis() - lastAppleEatenTime;
        return Math.max(0.0, 1.0 - (elapsed / (double) COMBO_MAX_TIME));
    }
}