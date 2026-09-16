import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.InputMap;
import javax.swing.ActionMap;
import javax.swing.KeyStroke;
import javax.swing.AbstractAction;
import java.awt.geom.AffineTransform;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class GamePanel extends JPanel implements ActionListener {

    private static final int TILE_SIZE = 25;

    // The True Retro Green Palette
    private static final Color GB_LIGHTEST = new Color(155, 188, 15);
    private static final Color GB_DARK     = new Color(48, 98, 48);
    private static final Color GB_DARKEST  = new Color(15, 56, 15);

    private final SnakeGame game;
    private final SoundManager sound;
    private final Timer timer;
    private final Random visualRandom;
    
    private int animationTick;
    private long lastLogicTick;
    private int lastScore = 0;
    private int displayScore = 0; 
    private double scoreScale = 1.0; 
    
    private Coordinate lastAppleLoc;
    private double appleScale = 0.0; 
    
    private SnakeGame.State lastState = SnakeGame.State.TITLE;
    
    private int hitStopFrames = 0;
    private int screenShakeFrames = 0;
    private int deathFlashFrames = 0;
    private int settingsSelection = 0; // 0=Zoom, 1=Board, 2=Reset, 3=Return

    private class Particle {
        double x, y, vx, vy; int life, maxLife;
        Particle(double x, double y, double vx, double vy) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.life = this.maxLife = 15 + visualRandom.nextInt(15);
        }
    }
    
    private class FloatingText {
        String text; double x, y; int life, maxLife; boolean isCombo;
        FloatingText(String text, double x, double y, boolean isCombo) {
            this.text = text; this.x = x; this.y = y; this.isCombo = isCombo;
            this.life = this.maxLife = 50;
        }
    }

    private class GhostTrail {
        int x, y; int life;
        GhostTrail(int x, int y) { this.x = x; this.y = y; this.life = 15; }
    }
    
    private final List<Particle> particles = new ArrayList<>();
    private final List<FloatingText> floatingTexts = new ArrayList<>();
    private final LinkedList<GhostTrail> ghostTrails = new LinkedList<>();

    public GamePanel() {
        this.visualRandom = new Random();
        game = new SnakeGame();
        sound = new SoundManager();

        setupKeyBindings();

        timer = new Timer(16, this);
        timer.start();
        lastLogicTick = System.currentTimeMillis();
        setBackground(GB_LIGHTEST);
    }

    private void setupKeyBindings() {
        InputMap inputMap = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();

        String[] keys = {"LEFT", "A", "RIGHT", "D"};
        String[] actions = {"moveLeft", "moveLeft", "moveRight", "moveRight"};
        for (int i = 0; i < keys.length; i++) inputMap.put(KeyStroke.getKeyStroke(keys[i]), actions[i]);

        inputMap.put(KeyStroke.getKeyStroke("UP"), "moveUp");
        inputMap.put(KeyStroke.getKeyStroke("W"), "moveUp");
        actionMap.put("moveUp", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { 
                if (game.getState() == SnakeGame.State.SETTINGS) {
                    sound.playBlip(); settingsSelection = (settingsSelection + 3) % 4; repaint();
                } else { game.setDirection(Direction.UP); }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("DOWN"), "moveDown");
        inputMap.put(KeyStroke.getKeyStroke("S"), "moveDown");
        actionMap.put("moveDown", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { 
                if (game.getState() == SnakeGame.State.TITLE) {
                    sound.playBlip(); game.openSettings(); settingsSelection = 0; 
                } else if (game.getState() == SnakeGame.State.SETTINGS) {
                    sound.playBlip(); settingsSelection = (settingsSelection + 1) % 4; repaint();
                } else { game.setDirection(Direction.DOWN); }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("SPACE"), "action");
        inputMap.put(KeyStroke.getKeyStroke("ENTER"), "action");
        actionMap.put("action", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { 
                if (game.getState() == SnakeGame.State.TITLE) { sound.playStart(); game.startGame(); }
                else if (game.getState() == SnakeGame.State.SETTINGS) { sound.playBlip(); triggerSelectedSetting(); }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("ESCAPE"), "quit");
        actionMap.put("quit", new AbstractAction() { public void actionPerformed(ActionEvent e) { System.exit(0); } });

        inputMap.put(KeyStroke.getKeyStroke("R"), "restart");
        actionMap.put("restart", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (game.getState() == SnakeGame.State.GAME_OVER || game.getState() == SnakeGame.State.GAME_WON) {
                    sound.playStart(); game.resetGame();
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("P"), "pause");
        actionMap.put("pause", new AbstractAction() { public void actionPerformed(ActionEvent e) { sound.playBlip(); game.togglePause(); repaint(); } });

        actionMap.put("moveLeft", new MoveAction(Direction.LEFT));
        actionMap.put("moveRight", new MoveAction(Direction.RIGHT));
    }

    private class MoveAction extends AbstractAction {
        private final Direction direction;
        public MoveAction(Direction dir) { this.direction = dir; }
        public void actionPerformed(ActionEvent e) { game.setDirection(direction); }
    }

    private void triggerSelectedSetting() {
        if (settingsSelection == 0) game.toggleScale();
        else if (settingsSelection == 1) game.toggleBoardSize();
        else if (settingsSelection == 2) game.resetHighScore();
        else if (settingsSelection == 3) game.closeSettings();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        animationTick++;
        SnakeGame.State currentState = game.getState();

        if (currentState == SnakeGame.State.PLAYING && lastState != SnakeGame.State.PLAYING) {
            lastScore = game.getScore();
            displayScore = 0;
            particles.clear();
            floatingTexts.clear();
            ghostTrails.clear();
            lastAppleLoc = null;
        }

        if (currentState == SnakeGame.State.GAME_OVER && lastState == SnakeGame.State.PLAYING) {
            sound.playExplosion();
            screenShakeFrames = 25;
            deathFlashFrames = 60;
        }

        long now = System.currentTimeMillis();
        if (hitStopFrames > 0) {
            hitStopFrames--;
            lastLogicTick = now; 
        } else if (currentState == SnakeGame.State.PLAYING) {
            int currentDelay = Math.max(40, game.getBoardSize().getBaseDelay() - ((game.getLevel() - 1) * 10));
            
            if (now - lastLogicTick >= currentDelay) {
                if (!game.getSnake().isEmpty()) {
                    Coordinate h = game.getSnake().getFirst();
                    ghostTrails.add(new GhostTrail(h.getX() * TILE_SIZE, h.getY() * TILE_SIZE));
                    if (ghostTrails.size() > 8) ghostTrails.removeFirst();
                }

                game.update();
                lastLogicTick = now;

                if (game.getApple() != null && !game.getApple().equals(lastAppleLoc)) {
                    lastAppleLoc = game.getApple();
                    appleScale = 0.0; 
                }

                if (game.getAppleIdleTicks() == 51) sound.playTick(); 

                if (game.getScore() > lastScore) {
                    sound.playCrunch();
                    hitStopFrames = 4; 
                    scoreScale = 1.8;  
                    spawnAppleJuice(game.getSnake().getFirst(), game.getLastPointsScored());
                    lastScore = game.getScore();
                }
            }
        }

        if (screenShakeFrames > 0) screenShakeFrames--;
        if (deathFlashFrames > 0) deathFlashFrames--;
        if (displayScore < game.getScore()) displayScore += Math.max(1, (game.getScore() - displayScore) / 5);
        if (scoreScale > 1.0) scoreScale = Math.max(1.0, scoreScale - 0.08);
        if (appleScale < 1.0) appleScale = Math.min(1.0, appleScale + 0.1);
        
        updateJuice();
        lastState = currentState;
        repaint();
    }

    private void spawnAppleJuice(Coordinate c, int points) {
        int px = c.getX() * TILE_SIZE + (TILE_SIZE / 2);
        int py = c.getY() * TILE_SIZE + (TILE_SIZE / 2);

        boolean isCombo = points >= 3;
        String txt = isCombo ? "COMBO x" + points + "!" : "+" + points;
        floatingTexts.add(new FloatingText(txt, px - 10, py - 10, isCombo));

        for (int i = 0; i < 10; i++) {
            double vx = (visualRandom.nextDouble() - 0.5) * 12;
            double vy = (visualRandom.nextDouble() - 0.5) * 12;
            particles.add(new Particle(px, py, vx, vy));
        }
    }

    private void updateJuice() {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.x += p.vx; p.y += p.vy; p.vy += 0.4; p.life--;
            if (p.life <= 0) particles.remove(i);
        }
        for (int i = floatingTexts.size() - 1; i >= 0; i--) {
            FloatingText ft = floatingTexts.get(i);
            ft.y -= 1.0; ft.life--;
            if (ft.life <= 0) floatingTexts.remove(i);
        }
        for (int i = ghostTrails.size() - 1; i >= 0; i--) {
            GhostTrail gt = ghostTrails.get(i);
            gt.life--;
            if (gt.life <= 0) ghostTrails.remove(i);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        int gridWidth = game.getWidth() * TILE_SIZE;
        int gridHeight = game.getHeight() * TILE_SIZE;
        int hudWidth = 220;
        
        int virtualWidth = gridWidth + hudWidth;
        int virtualHeight = Math.max(gridHeight, 400); 

        double scale = game.getScale();
        int renderOffsetX = (int) ((getWidth() - (virtualWidth * scale)) / 2);
        int renderOffsetY = (int) ((getHeight() - (gridHeight * scale)) / 2);

        if (screenShakeFrames > 0) {
            renderOffsetX += (visualRandom.nextInt(9) - 4) * scale; 
            renderOffsetY += (visualRandom.nextInt(9) - 4) * scale;
        }

        g2d.translate(renderOffsetX, renderOffsetY);
        g2d.scale(scale, scale);

        if (game.getState() == SnakeGame.State.TITLE) {
            drawTitleScreen(g2d, virtualWidth, gridHeight);
            return;
        } else if (game.getState() == SnakeGame.State.SETTINGS) {
            drawSettingsScreen(g2d, virtualWidth, gridHeight);
            return;
        }

        g2d.setColor(GB_LIGHTEST);
        g2d.fillRect(0, 0, virtualWidth, gridHeight);

        g2d.setColor(GB_DARKEST);
        g2d.setStroke(new BasicStroke(4));
        g2d.drawRect(-2, -2, virtualWidth + 4, gridHeight + 4);
        g2d.drawLine(gridWidth, 0, gridWidth, gridHeight);
        g2d.setStroke(new BasicStroke(1));

        drawGrid(g2d, gridWidth, gridHeight);
        drawPanickedApple(g2d);
        
        if (game.getCombo() > 2) {
            for (GhostTrail gt : ghostTrails) {
                g2d.setColor(GB_DARK);
                g2d.drawRect(gt.x + 3, gt.y + 3, TILE_SIZE - 6, TILE_SIZE - 6);
            }
        }
        
        drawSnake(g2d);
        drawJuice(g2d);
        drawSideHUD(g2d, gridWidth, 0, hudWidth, gridHeight);

        if (game.getState() == SnakeGame.State.PAUSED) {
            drawOverlay(g2d, "PAUSED", "Press 'P'", gridWidth, gridHeight);
        } else if (game.getState() == SnakeGame.State.GAME_OVER) {
            if (deathFlashFrames == 0) drawOverlay(g2d, "GAME OVER", "Press 'R'", gridWidth, gridHeight);
        } else if (game.getState() == SnakeGame.State.GAME_WON) {
            drawOverlay(g2d, "YOU WIN!", "Press 'R'", gridWidth, gridHeight);
        }
    }

    private void drawJuice(Graphics2D g) {
        g.setColor(GB_DARKEST);
        for (Particle p : particles) {
            int size = Math.max(1, (int)(5.0 * (p.life / (double)p.maxLife)));
            g.fillRect((int)p.x, (int)p.y, size, size);
        }
        
        g.setFont(new Font("Monospaced", Font.BOLD, 16));
        for (FloatingText ft : floatingTexts) {
            if (ft.isCombo && (animationTick / 2) % 2 == 0) g.setColor(GB_LIGHTEST); 
            else g.setColor(GB_DARKEST);
            
            int jitterX = ft.isCombo ? visualRandom.nextInt(3)-1 : 0;
            int jitterY = ft.isCombo ? visualRandom.nextInt(3)-1 : 0;
            
            g.drawString(ft.text, (int)ft.x + jitterX, (int)ft.y + jitterY);
        }
    }

    private void drawSettingsScreen(Graphics2D g, int vWidth, int vHeight) {
        g.setColor(GB_DARKEST);
        g.setFont(new Font("Monospaced", Font.BOLD, 50));
        String title = "SETTINGS";
        g.drawString(title, (vWidth - g.getFontMetrics().stringWidth(title)) / 2, vHeight / 4);

        int startY = vHeight / 2 - 60;
        g.setFont(new Font("Monospaced", Font.BOLD, 20));
        
        boolean blink = (animationTick / 15) % 2 == 0;
        
        String c0 = (settingsSelection == 0 && blink) ? "> " : "  ";
        g.drawString(c0 + "ZOOM: " + game.getScale() + "x", 60, startY);
        
        String c1 = (settingsSelection == 1 && blink) ? "> " : "  ";
        g.drawString(c1 + "BOARD: " + game.getBoardSize().name(), 60, startY + 40);
        
        String c2 = (settingsSelection == 2 && blink) ? "> " : "  ";
        g.drawString(c2 + "RESET HIGH SCORE", 60, startY + 80);
        
        String c3 = (settingsSelection == 3 && blink) ? "> " : "  ";
        g.drawString(c3 + "RETURN TO TITLE", 60, startY + 120);
        
        g.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g.drawString("Use W/S to navigate, ENTER to select", 60, vHeight - 30);
    }

    private void drawSideHUD(Graphics2D g, int hudX, int hudY, int hudWidth, int hudHeight) {
        int textX = hudX + 25;
        int startY = hudY + 45;

        g.setColor(GB_DARKEST);
        g.setFont(new Font("Monospaced", Font.BOLD, 36));
        g.drawString("SNAKE", textX, startY);
        g.drawLine(textX, startY + 15, hudX + hudWidth - 25, startY + 15);

        startY += 60;
        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        g.drawString("SCORE", textX, startY);
        
        AffineTransform oldTrans = g.getTransform();
        g.translate(textX + 25, startY + 30); 
        g.scale(scoreScale, scoreScale);
        g.setFont(new Font("Monospaced", Font.BOLD, 28));
        g.drawString(String.format("%03d", displayScore), -25, 10);
        g.setTransform(oldTrans);

        startY += 60;
        g.setFont(new Font("Monospaced", Font.BOLD, 14));
        g.drawString("COMBO x" + game.getCombo(), textX, startY);
        g.drawRect(textX, startY + 10, 160, 10);
        int barFill = (int) (160 * game.getComboTimerRatio());
        if (game.getCombo() > 1 && (animationTick / 5) % 2 == 0) g.setColor(GB_DARK);
        g.fillRect(textX, startY + 10, barFill, 10);

        startY += 60;
        g.setColor(GB_DARKEST);
        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        g.drawString("HIGH SCORE", textX, startY);
        g.setFont(new Font("Monospaced", Font.BOLD, 28));
        g.drawString(String.format("%03d", game.getHighScore()), textX, startY + 30);

        startY += 70;
        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        g.drawString("LEVEL", textX, startY);
        g.setFont(new Font("Monospaced", Font.BOLD, 28));
        g.drawString(String.format("%02d", game.getLevel()), textX, startY + 30);

        int controlsY = hudHeight - 80;
        if (hudHeight < 400) controlsY = 400 - 80; 

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

        int idleTicks = game.getAppleIdleTicks();
        boolean isPanicking = idleTicks > 50;
        boolean isWarning = idleTicks > 35 && idleTicks <= 50;

        double currentAppleScale = appleScale; 
        int offset = 0;
        Color appleColor = GB_DARKEST;

        if (isPanicking) {
            // Intense frantic heartbeat throbbing
            currentAppleScale *= 1.0 + 0.15 * Math.sin(animationTick * 0.5);
            // Flashing light/dark colors
            if ((animationTick / 3) % 2 == 0) appleColor = GB_DARK;
        } else if (isWarning) {
            // Pre-panic subtle shaking
            if ((animationTick / 4) % 2 == 0) offset = (visualRandom.nextInt(3) - 1);
        }

        AffineTransform oldTrans = g.getTransform();
        g.translate(x + TILE_SIZE/2.0, y + TILE_SIZE/2.0);
        g.scale(currentAppleScale, currentAppleScale);

        g.setColor(appleColor);
        g.fillRect(-7 + offset, -4 + offset, 15, 12); 
        g.fillRect(-5 + offset, -6 + offset, 11, 2);  
        g.fillRect(-5 + offset,  8 + offset, 11, 2); 
        g.fillRect( 0 + offset, -10 + offset, 2, 4);  
        g.fillRect( 2 + offset, -8 + offset, 4, 2);  

        // The "tiny ahh hands" (sweat drops) have been permanently amputated. 

        g.setTransform(oldTrans);
    }

    private void drawSnake(Graphics2D g) {
        LinkedList<Coordinate> snake = game.getSnake();
        if (snake.isEmpty()) return;

        boolean invert = (deathFlashFrames > 0 && (deathFlashFrames / 6) % 2 == 0);
        Color headColor = invert ? GB_LIGHTEST : GB_DARKEST;
        Color eyeColor = invert ? GB_DARKEST : GB_LIGHTEST;
        Color bodyColor = invert ? GB_LIGHTEST : GB_DARKEST;

        g.setColor(headColor);
        Coordinate head = snake.getFirst();
        int hx = head.getX() * TILE_SIZE;
        int hy = head.getY() * TILE_SIZE;
        g.fillRect(hx + 2, hy + 2, TILE_SIZE - 4, TILE_SIZE - 4);

        g.setColor(eyeColor); 
        int dx = 1, dy = 0; 
        if (snake.size() > 1) {
            Coordinate neck = snake.get(1);
            dx = head.getX() - neck.getX(); dy = head.getY() - neck.getY();
        }
        if (dx == 1) { g.fillRect(hx + 16, hy + 4, 4, 4); g.fillRect(hx + 16, hy + 17, 4, 4); }
        else if (dx == -1) { g.fillRect(hx + 5, hy + 4, 4, 4); g.fillRect(hx + 5, hy + 17, 4, 4); }
        else if (dy == 1) { g.fillRect(hx + 4, hy + 16, 4, 4); g.fillRect(hx + 17, hy + 16, 4, 4); }
        else if (dy == -1) { g.fillRect(hx + 4, hy + 5, 4, 4); g.fillRect(hx + 17, hy + 5, 4, 4); }

        g.setColor(bodyColor);
        for (int i = 1; i < snake.size(); i++) {
            Coordinate part = snake.get(i);
            g.fillRect(part.getX() * TILE_SIZE + 2, part.getY() * TILE_SIZE + 2, TILE_SIZE - 4, TILE_SIZE - 4);
        }
    }

    private void drawTitleScreen(Graphics2D g, int vWidth, int vHeight) {
        g.setColor(GB_DARKEST);
        g.setFont(new Font("Monospaced", Font.BOLD, 80));
        FontMetrics metrics = getFontMetrics(g.getFont());
        String title = "SNAKE";
        g.drawString(title, (vWidth - metrics.stringWidth(title)) / 2, vHeight / 2 - 30);

        if ((animationTick / 30) % 2 == 0) {
            g.setFont(new Font("Monospaced", Font.BOLD, 20));
            String subMsg = "Press [SPACE] to start";
            g.drawString(subMsg, (vWidth - g.getFontMetrics().stringWidth(subMsg)) / 2, vHeight / 2 + 40);
        }

        g.setFont(new Font("Monospaced", Font.PLAIN, 16));
        String setMsg = "Press [S] for Settings";
        g.drawString(setMsg, (vWidth - g.getFontMetrics().stringWidth(setMsg)) / 2, vHeight / 2 + 80);
    }

    private void drawOverlay(Graphics2D g, String title, String sub, int gridWidth, int gridHeight) {
        int boxWidth = 280;
        int boxHeight = 100;
        int boxX = (gridWidth - boxWidth) / 2;
        int boxY = (gridHeight - boxHeight) / 2;

        g.setColor(GB_LIGHTEST); g.fillRect(boxX, boxY, boxWidth, boxHeight);
        g.setColor(GB_DARKEST); g.setStroke(new BasicStroke(4)); g.drawRect(boxX, boxY, boxWidth, boxHeight); g.setStroke(new BasicStroke(1));

        g.setFont(new Font("Monospaced", Font.BOLD, 40));
        g.drawString(title, (gridWidth - g.getFontMetrics().stringWidth(title)) / 2, gridHeight / 2 - 5);
        g.setFont(new Font("Monospaced", Font.BOLD, 16));
        
        if ((animationTick / 30) % 2 == 0) g.drawString(sub, (gridWidth - g.getFontMetrics().stringWidth(sub)) / 2, gridHeight / 2 + 35);
    }

    private void drawGrid(Graphics2D g, int boardWidth, int boardHeight) {
        g.setColor(GB_DARK);
        for (int x = 1; x < game.getWidth(); x++) g.drawLine(x * TILE_SIZE, 0, x * TILE_SIZE, boardHeight);
        for (int y = 1; y < game.getHeight(); y++) g.drawLine(0, y * TILE_SIZE, boardWidth, y * TILE_SIZE);
    }
}