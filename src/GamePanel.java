import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.InputMap;
import javax.swing.ActionMap;
import javax.swing.KeyStroke;
import javax.swing.AbstractAction;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.Random;

public class GamePanel extends JPanel implements ActionListener {

    private static final int TILE_SIZE = 25;
    private static final int GRID_WIDTH = 24;
    private static final int GRID_HEIGHT = 24;
    private static final int BASE_DELAY = 130;

    private static final Color GB_LIGHTEST = new Color(155, 188, 15);
    private static final Color GB_LIGHT    = new Color(139, 172, 15);
    private static final Color GB_DARK     = new Color(48, 98, 48);
    private static final Color GB_DARKEST  = new Color(15, 56, 15);

    private final SnakeGame game;
    private final Timer timer;
    private final Random visualRandom;
    private int animationTick;

    public GamePanel() {
        this.visualRandom = new Random();
        game = new SnakeGame(GRID_WIDTH, GRID_HEIGHT);

        setupKeyBindings();

        timer = new Timer(BASE_DELAY, this);
        timer.start();
    }

    private void setupKeyBindings() {
        InputMap inputMap = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();

        String[] keys = {"UP", "W", "DOWN", "S", "LEFT", "A", "RIGHT", "D"};
        String[] actions = {"moveUp", "moveUp", "moveDown", "moveDown", "moveLeft", "moveLeft", "moveRight", "moveRight"};
        for (int i = 0; i < keys.length; i++) inputMap.put(KeyStroke.getKeyStroke(keys[i]), actions[i]);

        inputMap.put(KeyStroke.getKeyStroke("SPACE"), "start");
        actionMap.put("start", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { if (game.getState() == SnakeGame.State.TITLE) game.startGame(); }
        });

        inputMap.put(KeyStroke.getKeyStroke("S"), "settings");
        actionMap.put("settings", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { if (game.getState() == SnakeGame.State.TITLE) game.openSettings(); repaint(); }
        });

        inputMap.put(KeyStroke.getKeyStroke("T"), "theme");
        actionMap.put("theme", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { if (game.getState() == SnakeGame.State.SETTINGS) game.toggleTheme(); repaint(); }
        });

        inputMap.put(KeyStroke.getKeyStroke("X"), "resetHigh");
        actionMap.put("resetHigh", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { if (game.getState() == SnakeGame.State.SETTINGS) game.resetHighScore(); repaint(); }
        });

        inputMap.put(KeyStroke.getKeyStroke("ENTER"), "back");
        actionMap.put("back", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { if (game.getState() == SnakeGame.State.SETTINGS) game.closeSettings(); repaint(); }
        });

        inputMap.put(KeyStroke.getKeyStroke("ESCAPE"), "quit");
        actionMap.put("quit", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { System.exit(0); }
        });

        inputMap.put(KeyStroke.getKeyStroke("R"), "restart");
        actionMap.put("restart", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (game.getState() == SnakeGame.State.GAME_OVER || game.getState() == SnakeGame.State.GAME_WON) {
                    game.resetGame();
                    timer.setDelay(BASE_DELAY);
                    timer.restart();
                    repaint();
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("P"), "pause");
        actionMap.put("pause", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { game.togglePause(); repaint(); }
        });

        actionMap.put("moveUp", new MoveAction(Direction.UP));
        actionMap.put("moveDown", new MoveAction(Direction.DOWN));
        actionMap.put("moveLeft", new MoveAction(Direction.LEFT));
        actionMap.put("moveRight", new MoveAction(Direction.RIGHT));
    }

    private class MoveAction extends AbstractAction {
        private final Direction direction;
        public MoveAction(Direction dir) { this.direction = dir; }
        public void actionPerformed(ActionEvent e) { game.setDirection(direction); }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        animationTick++;
        game.update();
        if (game.getState() == SnakeGame.State.PLAYING) {
            int newDelay = Math.max(50, BASE_DELAY - ((game.getLevel() - 1) * 10));
            timer.setDelay(newDelay);
        }
        repaint();
    }

    // --- UPDATED: Massive Theme Color Engine ---
    private Color getColor(String element) {
        switch (game.getTheme()) {
            case GAMEBOY:
                return switch (element) {
                    case "bezel" -> GB_LIGHT;
                    case "console" -> GB_LIGHTEST;
                    case "border", "text", "score", "highScore", "apple" -> GB_DARKEST;
                    case "textDim", "applePanic" -> GB_DARK;
                    default -> Color.MAGENTA;
                };
            case SYNTHWAVE:
                return switch (element) {
                    case "bezel" -> new Color(15, 5, 25);
                    case "console" -> new Color(25, 10, 45);
                    case "border", "text" -> new Color(0, 255, 255); // Cyan
                    case "textDim" -> new Color(0, 150, 150);
                    case "score", "highScore" -> new Color(255, 0, 255); // Pink
                    case "apple" -> new Color(255, 255, 0); // Yellow
                    case "applePanic" -> new Color(255, 120, 0);
                    default -> Color.MAGENTA;
                };
            case HACKER:
                return switch (element) {
                    case "bezel" -> new Color(5, 10, 5);
                    case "console" -> new Color(0, 0, 0); // Pure Black
                    case "border", "text", "score", "highScore", "apple" -> new Color(50, 255, 50); // Neon Green
                    case "textDim", "applePanic" -> new Color(20, 120, 20);
                    default -> Color.MAGENTA;
                };
            case VIRTUAL_BOY:
                return switch (element) {
                    case "bezel" -> new Color(15, 0, 0);
                    case "console" -> new Color(0, 0, 0); // Pure Black
                    case "border", "text", "score", "highScore", "apple" -> new Color(255, 0, 0); // Intense Red
                    case "textDim", "applePanic" -> new Color(120, 0, 0);
                    default -> Color.MAGENTA;
                };
            case CLASSIC:
            default:
                return switch (element) {
                    case "bezel" -> new Color(15, 15, 20); // Dark Slate
                    case "console" -> new Color(5, 5, 10); // Very Dark Slate
                    case "border", "text" -> new Color(240, 240, 240); // Off-White
                    case "textDim" -> new Color(100, 100, 110);
                    case "score" -> new Color(50, 255, 50);
                    case "highScore" -> new Color(255, 50, 50);
                    case "apple" -> new Color(255, 30, 30);
                    case "applePanic" -> new Color(255, 150, 0);
                    default -> Color.MAGENTA;
                };
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Color targetBg = (game.getState() == SnakeGame.State.TITLE || game.getState() == SnakeGame.State.SETTINGS)
                            ? getColor("console") : getColor("bezel");

        if (!getBackground().equals(targetBg)) setBackground(targetBg);
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Anti-aliasing looks bad on 4-color retro themes, but good on modern ones
        if (game.getTheme() == SnakeGame.Theme.GAMEBOY || game.getTheme() == SnakeGame.Theme.VIRTUAL_BOY || game.getTheme() == SnakeGame.Theme.HACKER) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        } else {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }

        if (game.getState() == SnakeGame.State.TITLE) {
            drawTitleScreen(g2d, getWidth(), getHeight());
            return;
        } else if (game.getState() == SnakeGame.State.SETTINGS) {
            drawSettingsScreen(g2d, getWidth(), getHeight());
            return;
        }

        int gridWidth = GRID_WIDTH * TILE_SIZE;
        int gridHeight = GRID_HEIGHT * TILE_SIZE;
        int hudWidth = 220;
        int totalWidth = gridWidth + hudWidth;

        int offsetX = (getWidth() - totalWidth) / 2;
        int offsetY = (getHeight() - gridHeight) / 2;

        g2d.translate(offsetX, offsetY);

        g2d.setColor(getColor("console"));
        g2d.fillRect(0, 0, totalWidth, gridHeight);

        g2d.setColor(getColor("border"));
        g2d.setStroke(new BasicStroke(4));
        g2d.drawRect(-2, -2, totalWidth + 4, gridHeight + 4);
        g2d.drawLine(gridWidth, 0, gridWidth, gridHeight);
        g2d.setStroke(new BasicStroke(1));

        drawGrid(g2d, gridWidth, gridHeight);
        drawGhostApple(g2d);
        drawPanickedApple(g2d);
        drawSnake(g2d);
        drawSideHUD(g2d, gridWidth, 0, hudWidth, gridHeight);

        if (game.getState() == SnakeGame.State.PAUSED) {
            drawOverlay(g2d, "PAUSED", "Press 'P' to Resume", gridWidth, gridHeight);
        } else if (game.getState() == SnakeGame.State.GAME_OVER) {
            drawOverlay(g2d, "GAME OVER", "Press 'R' to Restart", gridWidth, gridHeight);
        } else if (game.getState() == SnakeGame.State.GAME_WON) {
            drawOverlay(g2d, "YOU WIN!", "Press 'R' to Restart", gridWidth, gridHeight);
        }

        g2d.translate(-offsetX, -offsetY);
    }

    private void drawSettingsScreen(Graphics2D g, int screenWidth, int screenHeight) {
        g.setColor(getColor("text"));
        g.setFont(new Font("Monospaced", Font.BOLD, 60));
        String title = "SETTINGS";
        g.drawString(title, (screenWidth - g.getFontMetrics().stringWidth(title)) / 2, 100);

        g.setFont(new Font("Monospaced", Font.BOLD, 22));

        // Cleanly formats enums like "VIRTUAL_BOY" to "VIRTUAL BOY"
        String themeName = game.getTheme().name().replace("_", " ");
        String themeLabel = "THEME: " + themeName;
        g.drawString(themeLabel, 100, 250);

        g.setColor(getColor("textDim"));
        g.setFont(new Font("Monospaced", Font.PLAIN, 16));
        g.drawString("> Press 'T' to switch themes", 100, 280);

        g.setColor(getColor("text"));
        g.setFont(new Font("Monospaced", Font.BOLD, 22));
        g.drawString("HIGH SCORE: " + game.getHighScore(), 100, 360);

        g.setColor(getColor("textDim"));
        g.setFont(new Font("Monospaced", Font.PLAIN, 16));
        g.drawString("> Press 'X' to reset high score to 0", 100, 390);

        g.setColor(getColor("text"));
        g.setFont(new Font("Monospaced", Font.BOLD, 22));
        String backLabel = "Press [ENTER] to return to Title";
        g.drawString(backLabel, (screenWidth - g.getFontMetrics().stringWidth(backLabel)) / 2, screenHeight - 100);
    }

    private void drawSideHUD(Graphics2D g, int hudX, int hudY, int hudWidth, int hudHeight) {
        int textX = hudX + 25;
        int startY = hudY + 45;

        g.setColor(getColor("text"));
        g.setFont(new Font("Monospaced", Font.BOLD, 36));
        g.drawString("SNAKE", textX, startY);
        g.drawLine(textX, startY + 15, hudX + hudWidth - 25, startY + 15);

        startY += 70;
        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        g.drawString("SCORE", textX, startY);
        g.setColor(getColor("score"));
        g.setFont(new Font("Monospaced", Font.BOLD, 28));
        g.drawString(String.format("%03d", game.getScore()), textX, startY + 30);

        startY += 80;
        g.setColor(getColor("text"));
        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        g.drawString("HIGH SCORE", textX, startY);
        g.setColor(getColor("highScore"));
        g.setFont(new Font("Monospaced", Font.BOLD, 28));
        g.drawString(String.format("%03d", game.getHighScore()), textX, startY + 30);

        startY += 80;
        g.setColor(getColor("text"));
        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        g.drawString("LEVEL", textX, startY);
        g.setFont(new Font("Monospaced", Font.BOLD, 28));
        g.drawString(String.format("%02d", game.getLevel()), textX, startY + 30);

        if (game.getGhostTimer() > 0) {
            startY += 80;
            g.setColor(game.getTheme() == SnakeGame.Theme.CLASSIC ? new Color(0, 255, 255) : getColor("textDim"));
            g.setFont(new Font("Monospaced", Font.BOLD, 18));
            g.drawString("PHANTOM", textX, startY);
            g.drawString(game.getGhostTimer() + " MOVES", textX, startY + 30);
        }

        int controlsY = hudHeight - 80;
        g.setColor(getColor("textDim"));
        g.setFont(new Font("Monospaced", Font.BOLD, 14));
        g.drawString("WASD  : Move", textX, controlsY);
        g.drawString("P     : Pause", textX, controlsY + 25);
        g.drawString("R     : Restart", textX, controlsY + 50);
        g.drawString("ESC   : Quit", textX, controlsY + 75);
    }

    private void drawPanickedApple(Graphics2D g) {
        Coordinate apple = game.getApple();
        if (apple == null) return;
        int x = apple.getX() * TILE_SIZE;
        int y = apple.getY() * TILE_SIZE;

        if (game.getAppleIdleTicks() > 50) {
            x += visualRandom.nextInt(5) - 2;
            y += visualRandom.nextInt(5) - 2;
            g.setColor(animationTick % 2 == 0 ? getColor("apple") : getColor("applePanic"));
        } else {
            g.setColor(getColor("apple"));
        }

        if (game.getTheme() == SnakeGame.Theme.GAMEBOY || game.getTheme() == SnakeGame.Theme.VIRTUAL_BOY || game.getTheme() == SnakeGame.Theme.HACKER) {
            g.fillRect(x + 6, y + 2, TILE_SIZE - 12, TILE_SIZE - 4);
            g.fillRect(x + 2, y + 6, TILE_SIZE - 4, TILE_SIZE - 12);
        } else {
            g.fillOval(x + 4, y + 4, TILE_SIZE - 8, TILE_SIZE - 8);
        }
    }

    private void drawGhostApple(Graphics2D g) {
        Coordinate gApple = game.getGhostApple();
        if (gApple == null) return;

        if (game.getTheme() == SnakeGame.Theme.GAMEBOY || game.getTheme() == SnakeGame.Theme.VIRTUAL_BOY || game.getTheme() == SnakeGame.Theme.HACKER) {
            g.setColor(getColor("textDim"));
            g.drawRect(gApple.getX() * TILE_SIZE + 4, gApple.getY() * TILE_SIZE + 4, TILE_SIZE - 8, TILE_SIZE - 8);
            g.drawRect(gApple.getX() * TILE_SIZE + 5, gApple.getY() * TILE_SIZE + 5, TILE_SIZE - 10, TILE_SIZE - 10);
        } else {
            g.setColor(new Color(0, 255, 255));
            g.fillOval(gApple.getX() * TILE_SIZE + 4, gApple.getY() * TILE_SIZE + 4, TILE_SIZE - 8, TILE_SIZE - 8);
        }
    }

    private void drawSnake(Graphics2D g) {
        LinkedList<Coordinate> snake = game.getSnake();
        if (snake.isEmpty()) return;

        boolean isGhost = game.getGhostTimer() > 0;

        if (isGhost && game.getGhostTimer() <= 10 && animationTick % 2 == 0) {
            isGhost = false;
        }

        Color headColor, bodyColor;

        // --- UPDATED: Snake colors specifically designed for each theme ---
        switch (game.getTheme()) {
            case GAMEBOY:
                headColor = isGhost ? GB_DARK : GB_DARKEST;
                bodyColor = isGhost ? GB_LIGHT : GB_DARK;
                break;
            case SYNTHWAVE:
                headColor = isGhost ? new Color(255, 0, 255, 100) : new Color(255, 0, 255);
                bodyColor = isGhost ? new Color(200, 0, 200, 80) : new Color(200, 0, 200);
                break;
            case HACKER:
                headColor = isGhost ? new Color(50, 255, 50, 100) : new Color(50, 255, 50);
                bodyColor = isGhost ? new Color(20, 150, 20, 80) : new Color(20, 150, 20);
                break;
            case VIRTUAL_BOY:
                headColor = isGhost ? new Color(255, 0, 0, 100) : new Color(255, 0, 0);
                bodyColor = isGhost ? new Color(150, 0, 0, 80) : new Color(150, 0, 0);
                break;
            case CLASSIC:
            default:
                headColor = isGhost ? new Color(0, 255, 255, 180) : new Color(0, 200, 0);
                bodyColor = isGhost ? new Color(100, 200, 200, 120) : new Color(0, 150, 0);
                break;
        }

        Coordinate head = snake.getFirst();
        g.setColor(headColor);
        g.fillRect(head.getX() * TILE_SIZE, head.getY() * TILE_SIZE, TILE_SIZE, TILE_SIZE);

        for (int i = 1; i < snake.size(); i++) {
            Coordinate part = snake.get(i);
            int px = part.getX() * TILE_SIZE;
            int py = part.getY() * TILE_SIZE;

            g.setColor(bodyColor);
            g.fillRect(px + 1, py + 1, TILE_SIZE - 2, TILE_SIZE - 2);

            if (game.getTheme() == SnakeGame.Theme.GAMEBOY || game.getTheme() == SnakeGame.Theme.VIRTUAL_BOY || game.getTheme() == SnakeGame.Theme.HACKER) {
                g.setColor(getColor("console"));
                g.fillRect(px + 6, py + 6, TILE_SIZE - 12, TILE_SIZE - 12);
            }
        }
    }

    private void drawTitleScreen(Graphics2D g, int screenWidth, int screenHeight) {
        g.setColor(getColor("text"));
        g.setFont(new Font("Monospaced", Font.BOLD, 100));
        FontMetrics metrics = getFontMetrics(g.getFont());
        String title = "SNAKE";
        int titleX = (screenWidth - metrics.stringWidth(title)) / 2;
        int titleY = screenHeight / 2 - 40;

        g.setColor(getColor("textDim"));
        g.drawString(title, titleX + 8, titleY + 8);
        g.setColor(getColor("text"));
        g.drawString(title, titleX, titleY);

        g.setFont(new Font("Monospaced", Font.BOLD, 24));
        String subMsg = "Press [SPACE] to start";
        g.drawString(subMsg, (screenWidth - g.getFontMetrics().stringWidth(subMsg)) / 2, screenHeight / 2 + 50);

        g.setColor(getColor("textDim"));
        g.setFont(new Font("Monospaced", Font.PLAIN, 18));
        String setMsg = "Press [S] for Settings";
        g.drawString(setMsg, (screenWidth - g.getFontMetrics().stringWidth(setMsg)) / 2, screenHeight / 2 + 100);
    }

    private void drawOverlay(Graphics2D g, String title, String sub, int gridWidth, int gridHeight) {
        int boxWidth = 360;
        int boxHeight = 140;
        int boxX = (gridWidth - boxWidth) / 2;
        int boxY = (gridHeight - boxHeight) / 2;

        g.setColor(getColor("console"));
        g.fillRect(boxX, boxY, boxWidth, boxHeight);

        g.setColor(getColor("border"));
        g.setStroke(new BasicStroke(4));
        g.drawRect(boxX, boxY, boxWidth, boxHeight);
        g.setStroke(new BasicStroke(1));

        g.setColor(getColor("text"));
        g.setFont(new Font("Monospaced", Font.BOLD, 45));
        g.drawString(title, (gridWidth - g.getFontMetrics().stringWidth(title)) / 2, gridHeight / 2 - 10);

        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        g.setColor(getColor("textDim"));
        g.drawString(sub, (gridWidth - g.getFontMetrics().stringWidth(sub)) / 2, gridHeight / 2 + 40);
    }

    private void drawGrid(Graphics2D g, int boardWidth, int boardHeight) {
        g.setColor(getColor("textDim"));

        if (game.getTheme() == SnakeGame.Theme.GAMEBOY || game.getTheme() == SnakeGame.Theme.VIRTUAL_BOY || game.getTheme() == SnakeGame.Theme.HACKER) {
            // Point grid for the pixelated/harsh retro themes
            for (int x = 1; x < GRID_WIDTH; x++) {
                for (int y = 1; y < GRID_HEIGHT; y++) {
                    g.fillRect(x * TILE_SIZE - 1, y * TILE_SIZE - 1, 2, 2);
                }
            }
        } else {
            // Line grid for Classic and Synthwave
            for (int x = 1; x < GRID_WIDTH; x++) g.drawLine(x * TILE_SIZE, 0, x * TILE_SIZE, boardHeight);
            for (int y = 1; y < GRID_HEIGHT; y++) g.drawLine(0, y * TILE_SIZE, boardWidth, y * TILE_SIZE);
        }
    }
}
