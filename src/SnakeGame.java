import java.awt.Point;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private int hitstopFrames = 0;

    private int width;
    private int height;
    private BoardSize boardSize;
    private double scale;
    private Difficulty difficulty = Difficulty.NORMAL;
    private boolean musicEnabled = true;
    private boolean dashEnabled = true;
    private boolean hitstopEnabled = true;
    private int applesEaten;
    private boolean[][] occupiedGrid;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SaveWorker");
        t.setDaemon(true);
        return t;
    });

    public class PlayerState {
        public LinkedList<Coordinate> body = new LinkedList<>();
        public Direction currentDirection = Direction.RIGHT;
        public LinkedList<Direction> inputQueue = new LinkedList<>();
        public int score = 0;
        public int currentCombo = 1;
        public long lastAppleEatenTime = 0;
        public int lastPointsScored = 0;
        public boolean isDead = false;
    }

    private PlayerState player;

    private Coordinate apple;

    private State state;
    private int highScore;
    private final Random random;

    private static final long COMBO_MAX_TIME = 4000;

    public SnakeGame() {
        this.random = new Random();
        this.player = new PlayerState();
        loadData();
        this.state = State.STARTUP;
        initBoard();
        Runtime.getRuntime().addShutdownHook(new Thread(ioExecutor::shutdown));
    }

    private void initBoard() {
        player.body.clear();
        player.inputQueue.clear();
        player.score = 0;
        player.currentCombo = 1;
        player.lastPointsScored = 0;
        player.isDead = false;
        this.applesEaten = 0;

        // Reset collision grid
        occupiedGrid = new boolean[width][height];

        int startY = height / 2;
        int p1X = width / 2;
        player.body.addFirst(new Coordinate(p1X, startY));
        player.body.addLast(new Coordinate(p1X - 1, startY));
        player.body.addLast(new Coordinate(p1X - 2, startY));
        player.currentDirection = Direction.RIGHT;

        // Mark initial snake segments in grid
        occupiedGrid[p1X][startY] = true;
        occupiedGrid[p1X - 1][startY] = true;
        occupiedGrid[p1X - 2][startY] = true;

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

    public void toggleHitstop() {
        hitstopEnabled = !hitstopEnabled;
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
            player.lastAppleEatenTime = System.currentTimeMillis();
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
        if (state != State.PLAYING || player.isDead)
            return;
        Direction lastCommand = player.inputQueue.isEmpty()
                ? player.currentDirection : player.inputQueue.getLast();
        if (lastCommand == Direction.UP && newDirection == Direction.DOWN)
            return;
        if (lastCommand == Direction.DOWN && newDirection == Direction.UP)
            return;
        if (lastCommand == Direction.LEFT && newDirection == Direction.RIGHT)
            return;
        if (lastCommand == Direction.RIGHT && newDirection == Direction.LEFT)
            return;
        if (player.inputQueue.size() < 2)
            player.inputQueue.add(newDirection);
    }

    private boolean isSpaceOccupied(Coordinate c) {
        return occupiedGrid[c.getX()][c.getY()] || c.equals(apple);
    }

    public void update() {
        if (state != State.PLAYING)
            return;

        if (hitstopFrames > 0) {
            hitstopFrames--;
            return;
        }

        long now = System.currentTimeMillis();
        if (now - player.lastAppleEatenTime > COMBO_MAX_TIME)
            player.currentCombo = 1;
        if (!player.inputQueue.isEmpty())
            player.currentDirection = player.inputQueue.poll();

        Coordinate head = calculateNewHead(player);
        boolean eating = head.equals(apple);
        boolean dead = isWallCollision(head) || isSelfCollision(head, eating);
        if (dead) {
            player.isDead = true;
            triggerGameOver();
            return;
        }

        // Maintain collision grid: mark new head
        occupiedGrid[head.getX()][head.getY()] = true;

        player.body.addFirst(head);
        if (eating) {
            increaseScore();
            applesEaten++;
            spawnApple();
        } else {
            // Clear vacated tail from grid
            Coordinate tail = player.body.removeLast();
            occupiedGrid[tail.getX()][tail.getY()] = false;
        }
    }

    private void increaseScore() {
        player.lastPointsScored = player.currentCombo;
        player.score += player.currentCombo;
        if (player.score > highScore) {
            highScore = player.score;
            ioExecutor.submit(this::saveDataSync);
        }
        player.currentCombo++;
        player.lastAppleEatenTime = System.currentTimeMillis();
    }

    private Coordinate calculateNewHead(PlayerState p) {
        int nextX = p.body.getFirst().getX(), nextY = p.body.getFirst().getY();
        switch (p.currentDirection) {
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

    private boolean isSelfCollision(Coordinate head, boolean eating) {
        if (!occupiedGrid[head.getX()][head.getY()]) return false;
        // Grid says occupied. If not eating, the tail will vacate — allow head to take its place.
        if (!eating) {
            Coordinate tail = player.body.getLast();
            return !head.equals(tail);
        }
        return true;
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
        hitstopEnabled = true;
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
                if (data.length >= 7)
                    hitstopEnabled = Boolean.parseBoolean(data[6]);
            }
        } catch (Exception e) {
        }
        this.width = boardSize.getSize();
        this.height = boardSize.getSize();
    }

    private void saveData() {
        saveDataSync();
    }

    private void saveDataSync() {
        try {
            Path path = Path.of(System.getProperty("user.home"), ".retrosnake_data.txt");
            Files.writeString(path,
                    highScore + "," + boardSize.name() + "," + scale + "," + difficulty.name()
                            + "," + musicEnabled + "," + dashEnabled + "," + hitstopEnabled);
        } catch (Exception e) {
        }
    }

    public LinkedList<Coordinate> getSnake() {
        return player.body;
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
        return player.score;
    }

    public int getHighScore() {
        return highScore;
    }

    public int getLevel() {
        return (player.score / 5) + 1;
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

    public boolean isHitstopEnabled() {
        return hitstopEnabled;
    }

    public void setHitstopFrames(int frames) {
        this.hitstopFrames = frames;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getCombo() {
        return player.currentCombo;
    }

    public int getLastPointsScored() {
        return player.lastPointsScored;
    }

    public double getComboTimerRatio() {
        long elapsed = System.currentTimeMillis() - player.lastAppleEatenTime;
        if (elapsed > COMBO_MAX_TIME)
            return 0.0;
        return 1.0 - ((double) elapsed / COMBO_MAX_TIME);
    }

    public static record RenderSnapshot(
        int score, int highScore, int combo, int stamina,
        Point headPosition,
        List<Point> bodyPositions,
        Point applePosition,
        State gameState
    ) {}

    public synchronized RenderSnapshot createSnapshot() {
        Point headPos = null;
        List<Point> bodyPos = new ArrayList<>();
        if (!player.body.isEmpty()) {
            headPos = new Point(player.body.getFirst().getX(), player.body.getFirst().getY());
            for (Coordinate c : player.body) {
                bodyPos.add(new Point(c.getX(), c.getY()));
            }
        }
        Point applePos = apple != null ? new Point(apple.getX(), apple.getY()) : null;
        return new RenderSnapshot(
            player.score, highScore, player.currentCombo, 0,
            headPos, Collections.unmodifiableList(bodyPos), applePos, state
        );
    }
}