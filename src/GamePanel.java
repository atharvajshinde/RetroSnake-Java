import javax.swing.JPanel;
import javax.swing.InputMap;
import javax.swing.ActionMap;
import javax.swing.KeyStroke;
import javax.swing.AbstractAction;
import java.awt.AlphaComposite;
import java.awt.geom.AffineTransform;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class GamePanel extends JPanel {

    private static final int TILE_SIZE = 25;

    // The True Retro Green Palette
    private static final Color GB_LIGHTEST = new Color(155, 188, 15);
    private static final Color GB_DARK     = new Color(48, 98, 48);
    private static final Color GB_DARKEST  = new Color(15, 56, 15);

    // ---- Arcade cabinet layout constants ----
    private static final int MARGIN           = 40;  // "plastic casing" margin around bezel
    private static final int OUTER_BORDER     = 8;   // outer bezel stroke width (GB_DARKEST)
    private static final int BEZEL_GAP        = 4;   // gap between outer and inner bezel rings
    private static final int INNER_BORDER     = 2;   // inner bezel stroke width (GB_DARK)
    private static final int HEADER_H         = 52;  // HUD header zone height
    private static final int FOOTER_H         = 52;  // HUD footer zone height
    // Total inset on each side: OUTER_BORDER + BEZEL_GAP + INNER_BORDER = 14
    private static final int BEZEL_INSET      = OUTER_BORDER + BEZEL_GAP + INNER_BORDER;
    private static final int STAMINA_SEGMENTS = 10;  // number of retro stamina bar blocks

    // ---- Wipe transition constants ----
    private static final int   COL_W          = 48;    // column width in virtual-canvas pixels
    private static final float STAGGER_WEIGHT = 0.35f; // fraction of progress used for stagger delay

    private final SnakeGame    game;
    private final SoundManager sound;
    private final Object       gameLock = new Object();
    private volatile boolean   running  = true;
    private Thread             gameThread;
    private final Random       visualRandom;

    /**
     * Custom TrueType font loaded from {@code /fonts/game_font.ttf} on the classpath.
     * Null if the resource is absent; {@link #gf} falls back to Monospaced in that case.
     * To supply a font, place a .ttf at {@code src/fonts/game_font.ttf} and ensure it
     * is included in the compiled classpath or jar.
     */
    private Font gameFont = null;

    private int      settingsSelection = 0;
    private int      titleSelection    = 0; // 0 = PLAY, 1 = SETTINGS
    private volatile boolean isDashing   = false;

    private String cachedScoreStr = "000";
    private String cachedHighScoreStr = "000";
    private String cachedComboStr = "x1";
    private int lastRenderedScore = -1;
    private int lastRenderedHighScore = -1;
    private int lastRenderedCombo = -1;

    private void updateCachedHudStrings(SnakeGame.RenderSnapshot snapshot) {
        if (snapshot.score() != lastRenderedScore) {
            cachedScoreStr = String.format("%03d", snapshot.score());
            lastRenderedScore = snapshot.score();
        }
        if (snapshot.highScore() != lastRenderedHighScore) {
            cachedHighScoreStr = String.format("%03d", snapshot.highScore());
            lastRenderedHighScore = snapshot.highScore();
        }
        if (snapshot.combo() != lastRenderedCombo) {
            cachedComboStr = "x" + snapshot.combo();
            lastRenderedCombo = snapshot.combo();
        }
    }
    private double    dashStamina       = 100.0;
    private final double MAX_STAMINA    = 100.0;
    private long     lastLogicTick;
    private int      lastScore          = 0;
    private int      displayScore       = 0;
    private double   scoreScale         = 1.0;

    private int        animationTick;
    private Coordinate lastAppleLoc;
    private double     appleScale      = 0.0;

    private int      startupTicks    = 0;
    private float    wipeProgress    = 0.0f;   // 0 = nothing drawn, 1 = fully covered
    private boolean  wipeCovering    = false;  // true = sweeping in, false = sweeping out
    private boolean  wipeActive      = false;  // true while any wipe is running
    private Runnable pendingAction   = null;
    private SnakeGame.State lastState = SnakeGame.State.TITLE;

    private int hitStopFrames     = 0;
    private int screenShakeFrames = 0;
    private int deathFlashFrames  = 0;

    // ---- Inner data classes ----

    private class Particle {
        float x, y, vx, vy, alpha;
        Color color;
        boolean active;

        void reset(float x, float y, float vx, float vy, float alpha, Color color) {
            this.x     = x;  this.y     = y;
            this.vx    = vx; this.vy    = vy;
            this.alpha = alpha;
            this.color = color;
            this.active = true;
        }
    }

    private class FloatingText {
        String text;
        double x, y;
        int    life, maxLife;
        boolean isCombo;

        FloatingText(String text, double x, double y, boolean isCombo) {
            this.text    = text;
            this.x       = x;
            this.y       = y;
            this.isCombo = isCombo;
            this.life = this.maxLife = 50;
        }
    }

    private class GhostTrail {
        int x, y, life;

        GhostTrail(int x, int y) {
            this.x    = x;
            this.y    = y;
            this.life = 15;
        }
    }

    private final Particle[]             particlePool  = new Particle[128];
    private final List<FloatingText>     floatingTexts = new ArrayList<>();
    private final LinkedList<GhostTrail> ghostTrails   = new LinkedList<>();

    // =========================================================================
    // Construction & Initialization
    // =========================================================================

    public GamePanel() {
        this.visualRandom = new Random();
        game  = new SnakeGame();
        sound = new SoundManager();

        loadGameFont();
        setupKeyBindings();

        // Pre-instantiate particle pool
        for (int i = 0; i < particlePool.length; i++) {
            particlePool[i] = new Particle();
        }

        lastLogicTick = System.currentTimeMillis();
        setBackground(GB_LIGHTEST);

        // Start dedicated game loop thread
        gameThread = new Thread(this::gameLoop, "GameLoop");
        gameThread.setDaemon(true);
        gameThread.start();
    }

    /** Attempts to load a custom TrueType font from the classpath. */
    private void loadGameFont() {
        try {
            InputStream is = getClass().getResourceAsStream("/fonts/game_font.ttf");
            if (is != null) {
                gameFont = Font.createFont(Font.TRUETYPE_FONT, is);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(gameFont);
                is.close();
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Returns a derived Font using the custom TrueType face when loaded,
     * falling back to Monospaced.
     */
    private Font gf(int style, int size) {
        return (gameFont != null)
                ? gameFont.deriveFont(style, (float) size)
                : new Font("Monospaced", style, size);
    }

    // =========================================================================
    // Key Bindings
    // =========================================================================

    private void setupKeyBindings() {
        InputMap  inputMap  = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();

        // ---- W: navigate title menu up / settings up / move snake ----
        inputMap.put(KeyStroke.getKeyStroke("W"), "p1_up");
        actionMap.put("p1_up", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (game.getState() == SnakeGame.State.TITLE) {
                    titleSelection = (titleSelection - 1 + 2) % 2;
                    sound.playBlip();
                    repaint();
                } else if (game.getState() == SnakeGame.State.SETTINGS) {
                    sound.playBlip();
                    settingsSelection = (settingsSelection + 7) % 8;
                    repaint();
                } else {
                    safeSetDirection(Direction.UP);
                }
            }
        });

        // ---- S: navigate title menu down / settings down / move snake ----
        inputMap.put(KeyStroke.getKeyStroke("S"), "p1_down");
        actionMap.put("p1_down", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (game.getState() == SnakeGame.State.TITLE) {
                    titleSelection = (titleSelection + 1) % 2;
                    sound.playBlip();
                    repaint();
                } else if (game.getState() == SnakeGame.State.SETTINGS) {
                    sound.playBlip();
                    settingsSelection = (settingsSelection + 1) % 8;
                    repaint();
                } else {
                    safeSetDirection(Direction.DOWN);
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("A"), "p1_left");
        actionMap.put("p1_left", new MoveAction(Direction.LEFT));
        inputMap.put(KeyStroke.getKeyStroke("D"), "p1_right");
        actionMap.put("p1_right", new MoveAction(Direction.RIGHT));

        // ---- UP ARROW: navigate title menu up / settings up / move snake ----
        inputMap.put(KeyStroke.getKeyStroke("UP"), "p2_up");
        actionMap.put("p2_up", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (game.getState() == SnakeGame.State.TITLE) {
                    titleSelection = (titleSelection - 1 + 2) % 2;
                    sound.playBlip();
                    repaint();
                } else if (game.getState() == SnakeGame.State.SETTINGS) {
                    sound.playBlip();
                    settingsSelection = (settingsSelection + 7) % 8;
                    repaint();
                } else {
                    safeSetDirection(Direction.UP);
                }
            }
        });

        // ---- DOWN ARROW: navigate title menu down / settings down / move snake ----
        inputMap.put(KeyStroke.getKeyStroke("DOWN"), "p2_down");
        actionMap.put("p2_down", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (game.getState() == SnakeGame.State.TITLE) {
                    titleSelection = (titleSelection + 1) % 2;
                    sound.playBlip();
                    repaint();
                } else if (game.getState() == SnakeGame.State.SETTINGS) {
                    sound.playBlip();
                    settingsSelection = (settingsSelection + 1) % 8;
                    repaint();
                } else {
                    safeSetDirection(Direction.DOWN);
                }
            }
        });

        // ---- LEFT ARROW: toggle selected setting / move snake ----
        inputMap.put(KeyStroke.getKeyStroke("LEFT"), "p2_left");
        actionMap.put("p2_left", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (game.getState() == SnakeGame.State.SETTINGS) {
                    sound.playBlip();
                    triggerSelectedSetting();
                } else {
                    safeSetDirection(Direction.LEFT);
                }
            }
        });

        // ---- RIGHT ARROW: toggle selected setting / move snake ----
        inputMap.put(KeyStroke.getKeyStroke("RIGHT"), "p2_right");
        actionMap.put("p2_right", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (game.getState() == SnakeGame.State.SETTINGS) {
                    sound.playBlip();
                    triggerSelectedSetting();
                } else {
                    safeSetDirection(Direction.RIGHT);
                }
            }
        });

        // ---- SPACE / ENTER: confirm selection ----
        inputMap.put(KeyStroke.getKeyStroke("SPACE"), "action");
        inputMap.put(KeyStroke.getKeyStroke("ENTER"), "action");
        actionMap.put("action", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (game.getState() == SnakeGame.State.TITLE) {
                    if (titleSelection == 0) {
                        // PLAY
                        sound.playStart();
                        startTransition(() -> game.startGame());
                    } else {
                        // SETTINGS
                        sound.playBlip();
                        startTransition(() -> {
                            game.openSettings();
                            settingsSelection = 0;
                        });
                    }
                } else if (game.getState() == SnakeGame.State.SETTINGS) {
                    sound.playBlip();
                    triggerSelectedSetting();
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("ESCAPE"), "quit");
        actionMap.put("quit", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { System.exit(0); }
        });

        inputMap.put(KeyStroke.getKeyStroke("R"), "restart");
        actionMap.put("restart", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (game.getState() == SnakeGame.State.GAME_OVER || game.getState() == SnakeGame.State.GAME_WON) {
                    sound.playStart();
                    startTransition(() -> game.resetGame());
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("P"), "pause");
        actionMap.put("pause", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                sound.playBlip();
                synchronized (gameLock) {
                    game.togglePause();
                }
                repaint();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("shift LEFT"), "dashDown");
        inputMap.put(KeyStroke.getKeyStroke("Q"),           "dashDown");
        inputMap.put(KeyStroke.getKeyStroke("control RIGHT"), "dashDown");
        actionMap.put("dashDown", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (game.isDashEnabled()) isDashing = true;
            }
        });
        inputMap.put(KeyStroke.getKeyStroke("released shift LEFT"), "dashUp");
        inputMap.put(KeyStroke.getKeyStroke("released Q"),           "dashUp");
        inputMap.put(KeyStroke.getKeyStroke("released control RIGHT"), "dashUp");
        actionMap.put("dashUp", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { isDashing = false; }
        });
    }

    private class MoveAction extends AbstractAction {
        private final Direction direction;

        public MoveAction(Direction dir) {
            this.direction = dir;
        }

        public void actionPerformed(ActionEvent e) {
            safeSetDirection(direction);
        }
    }

    // =========================================================================
    // State Transitions & Settings
    // =========================================================================

    private void startTransition(Runnable action) {
        synchronized (gameLock) {
            if (pendingAction != null || wipeActive) return;
            pendingAction = action;
            wipeProgress  = 0.0f;
            wipeCovering  = true;
            wipeActive    = true;
        }
    }

    private void triggerSelectedSetting() {
        synchronized (gameLock) {
            if      (settingsSelection == 0) game.toggleScale();
            else if (settingsSelection == 1) game.toggleBoardSize();
            else if (settingsSelection == 2) game.toggleDifficulty();
            else if (settingsSelection == 3) game.toggleDash();
            else if (settingsSelection == 4) game.toggleHitstop();
            else if (settingsSelection == 5) {
                game.toggleMusic();
                if (!game.isMusicEnabled()) sound.stopBGM();
            } else if (settingsSelection == 6) game.resetHighScore();
            else if (settingsSelection == 7)   startTransition(() -> game.closeSettings());
        }
    }

    // =========================================================================
    // Thread-safe helpers for EDT → game-thread communication
    // =========================================================================

    private void safeSetDirection(Direction dir) {
        synchronized (gameLock) {
            game.setDirection(dir);
        }
    }

    // =========================================================================
    // Game Loop (dedicated thread, fixed-timestep accumulator at 60 TPS)
    // =========================================================================

    /**
     * Dedicated game loop running on its own thread.
     * Uses a fixed-timestep accumulator at 60 TPS for deterministic ticks.
     * Dispatches repaint() to Swing's EDT after processing pending ticks.
     */
    private void gameLoop() {
        final long TICK_NS = 1_000_000_000L / 60;
        long lastTime = System.nanoTime();
        long accumulator = 0;

        while (running) {
            long now = System.nanoTime();
            long delta = now - lastTime;
            lastTime = now;
            accumulator += delta;

            boolean ticked = false;
            while (accumulator >= TICK_NS) {
                synchronized (gameLock) {
                    tick();
                }
                accumulator -= TICK_NS;
                ticked = true;
            }

            if (ticked) {
                repaint();
            }

            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void tick() {
        animationTick++;
        SnakeGame.State currentState = game.getState();

        // ---- STARTUP: count frames, then trigger wipe → TITLE ----
        if (currentState == SnakeGame.State.STARTUP) {
            if (startupTicks <= 90) startupTicks++;
            if (startupTicks > 90 && !wipeActive) {
                startTransition(() -> game.setState(SnakeGame.State.TITLE));
            }
        }

        // ---- Block-wipe transition ticker (independent of current state) ----
        if (wipeActive) {
            wipeProgress += 0.07f;
            if (wipeCovering && wipeProgress >= 1.0f) {
                // Screen fully covered: swap state, then begin uncover phase
                wipeProgress = 0.0f;
                wipeCovering = false;
                if (pendingAction != null) {
                    pendingAction.run();
                    pendingAction = null;
                    currentState  = game.getState();
                }
            } else if (!wipeCovering && wipeProgress >= 1.0f) {
                // Uncover phase complete: transition done
                wipeProgress = 0.0f;
                wipeActive   = false;
            }
        }

        if (currentState != lastState) {
            if (currentState == SnakeGame.State.PLAYING) {
                if (lastState != SnakeGame.State.PAUSED) sound.stopBGM();
                if (game.isMusicEnabled()) sound.playBGM();
            } else if (currentState == SnakeGame.State.PAUSED) {
                sound.pauseBGM();
            } else {
                sound.stopBGM();
            }
        }

        if (currentState == SnakeGame.State.PLAYING && lastState != SnakeGame.State.PLAYING) {
            lastScore    = game.getScore();
            displayScore = 0;
            for (int i = 0; i < particlePool.length; i++) particlePool[i].active = false;
            floatingTexts.clear();
            ghostTrails.clear();
            lastAppleLoc = null;
        }

        if (currentState == SnakeGame.State.GAME_OVER && lastState == SnakeGame.State.PLAYING) {
            sound.playExplosion();
            screenShakeFrames = 25;
            deathFlashFrames  = 60;
        }

        long now = System.currentTimeMillis();
        if (currentState == SnakeGame.State.PLAYING) {
            if (isDashing && dashStamina > 0) {
                dashStamina = Math.max(0, dashStamina - 2.5);
                if (dashStamina == 0) isDashing = false;
            } else {
                dashStamina = Math.min(MAX_STAMINA, dashStamina + 0.8);
            }

            int delay = Math.max(40, game.getDifficulty().getBaseDelay() - ((game.getLevel() - 1) * 10));
            if (isDashing && dashStamina > 0) delay = Math.max(20, delay / 3);

            if (now - lastLogicTick >= delay) {
                if (!game.getSnake().isEmpty()) {
                    Coordinate h = game.getSnake().getFirst();
                    ghostTrails.add(new GhostTrail(h.getX() * TILE_SIZE, h.getY() * TILE_SIZE));
                }
                while (ghostTrails.size() > 16) ghostTrails.removeFirst();

                game.update();
                lastLogicTick = now;

                if (game.getApple() != null && !game.getApple().equals(lastAppleLoc)) {
                    lastAppleLoc = game.getApple();
                    appleScale   = 0.0;
                }

                if (game.getScore() > lastScore) {
                    sound.playCrunch();
                    if (game.isHitstopEnabled()) game.setHitstopFrames(2);
                    scoreScale = 1.8;
                    spawnAppleJuice(game.getSnake().getFirst(), game.getLastPointsScored());
                    spawnExplosion(game.getSnake().getFirst().getX(), game.getSnake().getFirst().getY(), GB_DARKEST, 15);
                    spawnExplosion(game.getSnake().getFirst().getX(), game.getSnake().getFirst().getY(), GB_DARK,    10);
                    lastScore = game.getScore();
                }
            }
        }

        if (screenShakeFrames > 0) screenShakeFrames--;
        if (deathFlashFrames  > 0) deathFlashFrames--;

        if (displayScore < game.getScore())
            displayScore += Math.max(1, (game.getScore() - displayScore) / 5);

        if (scoreScale > 1.0) scoreScale = Math.max(1.0, scoreScale - 0.08);
        if (appleScale < 1.0) appleScale = Math.min(1.0, appleScale + 0.10);

        updateJuice();
        lastState = currentState;
    }

    // =========================================================================
    // Juice (particles, floating text, ghost trails)
    // =========================================================================

    private void spawnAppleJuice(Coordinate c, int points) {
        int px      = c.getX() * TILE_SIZE + (TILE_SIZE / 2);
        int py      = c.getY() * TILE_SIZE + (TILE_SIZE / 2);
        boolean isCombo = points >= 3;
        String txt  = isCombo ? "COMBO x" + points + "!" : "+" + points;
        floatingTexts.add(new FloatingText(txt, px - 10, py - 10, isCombo));
    }

    private void spawnExplosion(int gridX, int gridY, Color c, int count) {
        float px = gridX * TILE_SIZE + (TILE_SIZE / 2.0f);
        float py = gridY * TILE_SIZE + (TILE_SIZE / 2.0f);
        int spawned = 0;
        for (int i = 0; i < particlePool.length && spawned < count; i++) {
            if (!particlePool[i].active) {
                float vx = (float) ((visualRandom.nextFloat() - 0.5) * 10.0);
                float vy = (float) ((visualRandom.nextFloat() - 0.5) * 10.0);
                particlePool[i].reset(px, py, vx, vy, 1.0f, c);
                spawned++;
            }
        }
    }

    private void updateJuice() {
        for (int i = 0; i < particlePool.length; i++) {
            Particle p = particlePool[i];
            if (!p.active) continue;
            p.x += p.vx;   p.y += p.vy;
            p.vx *= 0.85f; p.vy *= 0.85f;
            p.alpha -= 0.05f;
            if (p.alpha <= 0) p.active = false;
        }
        for (int i = floatingTexts.size() - 1; i >= 0; i--) {
            FloatingText ft = floatingTexts.get(i);
            ft.y -= 1.0;
            ft.life--;
            if (ft.life <= 0) floatingTexts.remove(i);
        }
        for (int i = ghostTrails.size() - 1; i >= 0; i--) {
            GhostTrail gt = ghostTrails.get(i);
            gt.life--;
            if (gt.life <= 0) ghostTrails.remove(i);
        }
    }

    // =========================================================================
    // Rendering — main entry point
    // =========================================================================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        SnakeGame.RenderSnapshot snapshot = game.createSnapshot();
        if (snapshot == null) return;
        updateCachedHudStrings(snapshot);

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // ---- Layout math ----
        int gridW = game.getWidth()  * TILE_SIZE;
        int gridH = game.getHeight() * TILE_SIZE;

        int contentW = gridW;
        int contentH = HEADER_H + gridH + FOOTER_H;
        int bezelW   = contentW + BEZEL_INSET * 2;
        int bezelH   = contentH + BEZEL_INSET * 2;
        int virtualWidth  = bezelW + MARGIN * 2;
        int virtualHeight = bezelH + MARGIN * 2;

        double scale      = game.getScale();
        int renderOffsetX = (int) ((getWidth()  - virtualWidth  * scale) / 2);
        int renderOffsetY = (int) ((getHeight() - virtualHeight * scale) / 2);

        if (screenShakeFrames > 0) {
            renderOffsetX += (int) ((visualRandom.nextInt(9) - 4) * scale);
            renderOffsetY += (int) ((visualRandom.nextInt(9) - 4) * scale);
        }

        g2d.translate(renderOffsetX, renderOffsetY);
        g2d.scale(scale, scale);

        // ---- State dispatch ----
        if (snapshot.gameState() == SnakeGame.State.STARTUP) {
            drawStartupScreen(g2d, virtualWidth, virtualHeight);

        } else if (snapshot.gameState() == SnakeGame.State.TITLE) {
            drawTitleScreen(g2d, virtualWidth, virtualHeight, bezelW, bezelH);

        } else if (snapshot.gameState() == SnakeGame.State.SETTINGS) {
            drawSettingsScreen(g2d, virtualWidth, virtualHeight, bezelW, bezelH);

        } else {
            // ---- Plastic casing (full canvas background) ----
            g2d.setColor(GB_LIGHTEST);
            g2d.fillRect(0, 0, virtualWidth, virtualHeight);

            // ---- Dual-layer arcade bezel ----
            drawBezel(g2d, MARGIN, MARGIN, bezelW, bezelH);

            // ---- Shift into content coordinate space ----
            int contentX = MARGIN + BEZEL_INSET;
            int contentY = MARGIN + BEZEL_INSET;
            g2d.translate(contentX, contentY);

            drawHeader(g2d, contentW, HEADER_H);

            g2d.setColor(GB_DARK);
            g2d.setStroke(new BasicStroke(1));
            g2d.drawLine(0, HEADER_H,         contentW, HEADER_H);
            g2d.drawLine(0, HEADER_H + gridH, contentW, HEADER_H + gridH);

            // ---- Shift into grid coordinate space ----
            g2d.translate(0, HEADER_H);

            drawGrid(g2d, gridW, gridH);
            drawApple(g2d, snapshot);

            if (snapshot.combo() > 2 || (isDashing && dashStamina > 0)) {
                for (GhostTrail gt : ghostTrails) {
                    g2d.setColor(GB_DARK);
                    g2d.drawRect(gt.x + 3, gt.y + 3, TILE_SIZE - 6, TILE_SIZE - 6);
                }
            }

            drawSnake(g2d, snapshot);
            drawJuice(g2d);

            if (snapshot.gameState() == SnakeGame.State.PAUSED) {
                drawOverlay(g2d, "PAUSED", "Press 'P'", gridW, gridH);
            } else if (snapshot.gameState() == SnakeGame.State.GAME_OVER) {
                if (deathFlashFrames == 0) {
                    drawOverlay(g2d, "GAME OVER", "Press 'R'", gridW, gridH);
                }
            } else if (snapshot.gameState() == SnakeGame.State.GAME_WON) {
                drawOverlay(g2d, "YOU WIN! BUT HOW TF DID YOU DO IT?",
                        "Press 'R' you unemployed bozo.", gridW, gridH);
            }

            g2d.translate(0, -HEADER_H);

            drawFooter(g2d, contentW, HEADER_H + gridH, FOOTER_H, snapshot);

            g2d.translate(-contentX, -contentY);

        } // end playing-state block

        // ---- Block-wipe transition overlay ----
        if (wipeActive) drawWipe(g2d, virtualWidth, virtualHeight);
    }

    // =========================================================================
    // Draw: Startup / Studio Splash
    // =========================================================================

    /**
     * Draws the pre-title studio splash screen.
     * A full {@code GB_DARKEST} canvas with the studio name centred and
     * styled consistently with the rest of the arcade cabinet UI:
     * 2px {@code GB_DARK} drop-shadow behind {@code GB_LIGHTEST} text.
     * The block-wipe transition sweeps over this screen when the 90-tick
     * hold is complete, revealing the title menu underneath.
     */
    private void drawStartupScreen(Graphics2D g, int vW, int vH) {
        // Reset to screen-space coordinates so the fill covers the entire JPanel,
        // not just the scaled virtual canvas area.
        AffineTransform savedTx = g.getTransform();
        g.setTransform(new AffineTransform());
        g.setColor(GB_DARKEST);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setTransform(savedTx);

        // Studio name — drawn in virtual-canvas space so it scales with the game
        g.setFont(gf(Font.BOLD, 20));
        FontMetrics fm  = g.getFontMetrics();
        String splash   = "atharvajshinde studios";
        int textW       = fm.stringWidth(splash);
        int textX       = (vW - textW) / 2;
        int textY       = vH / 2 + fm.getAscent() / 2;

        // Drop shadow (+2, +2 in GB_DARK)
        g.setColor(GB_DARK);
        g.drawString(splash, textX + 2, textY + 2);

        // Real text in GB_LIGHTEST
        g.setColor(GB_LIGHTEST);
        g.drawString(splash, textX, textY);
    }

    // =========================================================================
    // Draw: Block-Wipe Transition
    // =========================================================================

    /**
     * Draws the staggered column wipe over the full virtual canvas.
     *
     * <p><b>Cover phase</b> ({@link #wipeCovering} = true):<br>
     * Each column sweeps downward (top → bottom) from left to right with a
     * stagger delay of {@link #STAGGER_WEIGHT} × total progress.
     *
     * <p><b>Uncover phase</b> ({@link #wipeCovering} = false):<br>
     * Each column's {@code GB_DARKEST} block slides upward, revealing the
     * new state underneath. Stagger runs right to left so the screen opens
     * in the opposite direction to the cover.
     *
     * @param vW virtual canvas width  (includes bezels + margins)
     * @param vH virtual canvas height (includes bezels + margins)
     */
    private void drawWipe(Graphics2D g, int vW, int vH) {
        int numCols = (int) Math.ceil((double) vW / COL_W);
        g.setColor(GB_DARKEST);

        for (int i = 0; i < numCols; i++) {
            int colX = i * COL_W;
            int colW = Math.min(COL_W, vW - colX);

            if (wipeCovering) {
                // Left-to-right stagger: later columns start later
                float stagger  = (float) i / numCols * STAGGER_WEIGHT;
                float colProg  = Math.min(1f, Math.max(0f,
                        (wipeProgress - stagger) / (1f - STAGGER_WEIGHT)));
                int   drawH    = (int) (colProg * vH);
                if (drawH > 0) g.fillRect(colX, 0, colW, drawH);
            } else {
                // Right-to-left stagger: rightmost columns clear first
                float stagger  = (float) (numCols - 1 - i) / numCols * STAGGER_WEIGHT;
                float colProg  = Math.min(1f, Math.max(0f,
                        (wipeProgress - stagger) / (1f - STAGGER_WEIGHT)));
                int   topY     = (int) (colProg * vH);
                int   drawH    = vH - topY;
                if (drawH > 0) g.fillRect(colX, topY, colW, drawH);
            }
        }
    }

    // =========================================================================
    // Draw: Menu Chrome (shared hardware background for title & settings)
    // =========================================================================

    /**
     * Draws the shared arcade cabinet chrome visible behind both menu screens:
     * GB_LIGHTEST plastic-casing fill, dual-layer bezel, and dot grid.
     * Keeps the application feeling physically unified as one arcade cabinet
     * regardless of which screen is active.
     *
     * @param vW     virtual canvas width
     * @param vH     virtual canvas height
     * @param bezelW bezel bounding-box width
     * @param bezelH bezel bounding-box height
     */
    private void drawMenuChrome(Graphics2D g, int vW, int vH, int bezelW, int bezelH) {
        // Plastic casing background
        g.setColor(GB_LIGHTEST);
        g.fillRect(0, 0, vW, vH);

        // Dual-layer arcade bezel (identical to in-game chrome)
        drawBezel(g, MARGIN, MARGIN, bezelW, bezelH);

        // Dot grid covering the entire bezel interior
        int innerX = MARGIN + BEZEL_INSET;
        int innerY = MARGIN + BEZEL_INSET;
        int innerW = bezelW - BEZEL_INSET * 2;
        int innerH = bezelH - BEZEL_INSET * 2;

        g.setColor(GB_DARK);
        for (int xi = TILE_SIZE; xi < innerW; xi += TILE_SIZE) {
            for (int yi = TILE_SIZE; yi < innerH; yi += TILE_SIZE) {
                g.fillRect(innerX + xi, innerY + yi, 1, 1);
            }
        }
    }

    // =========================================================================
    // Draw: Bezel
    // =========================================================================

    /**
     * Draws the dual-layer arcade cabinet bezel:
     *  - 8px outer ring in GB_DARKEST
     *  - 4px gap (transparent)
     *  - 2px inner ring in GB_DARK
     *
     * @param x top-left x of the bezel bounding box (in virtual canvas space)
     * @param y top-left y of the bezel bounding box
     * @param w total width of the bezel bounding box
     * @param h total height of the bezel bounding box
     */
    private void drawBezel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(GB_DARKEST);
        g.setStroke(new BasicStroke(OUTER_BORDER));
        g.drawRect(x + OUTER_BORDER / 2,
                   y + OUTER_BORDER / 2,
                   w - OUTER_BORDER,
                   h - OUTER_BORDER);

        int off = OUTER_BORDER + BEZEL_GAP;
        g.setColor(GB_DARK);
        g.setStroke(new BasicStroke(INNER_BORDER));
        g.drawRect(x + off, y + off, w - off * 2, h - off * 2);

        g.setStroke(new BasicStroke(1));
    }

    // =========================================================================
    // Draw: Header HUD
    // =========================================================================

    /**
     * Draws the top HUD bar (height = HEADER_H, origin = top-left of content area).
     *
     * <pre>
     *  [SCORE  042]       SNAKE       [HI  099]
     * </pre>
     *
     * Score digits animate with a pop/scale effect on eat.
     * All text is vertically centered within {@code height} using FontMetrics.
     */
    private void drawHeader(Graphics2D g, int width, int height) {
        g.setColor(GB_DARKEST);

        // ---- LEFT: "SCORE" label ----
        g.setFont(gf(Font.BOLD, 12));
        FontMetrics labelFm  = g.getFontMetrics();
        int labelBaseY       = (height - labelFm.getHeight()) / 2 + labelFm.getAscent();
        String scoreLabel    = "SCORE";
        g.drawString(scoreLabel, 14, labelBaseY);

        // Score digits with pop animation
        g.setFont(gf(Font.BOLD, 24));
        FontMetrics numFm    = g.getFontMetrics();
        String scoreNum      = cachedScoreStr;
        int scoreNumW        = numFm.stringWidth(scoreNum);
        int scoreNumX        = 14 + labelFm.stringWidth(scoreLabel) + 6;

        AffineTransform saved = g.getTransform();
        double cx = scoreNumX + scoreNumW / 2.0;
        double cy = height / 2.0;
        g.translate(cx, cy);
        g.scale(scoreScale, scoreScale);
        int numHalfH = (numFm.getAscent() - numFm.getDescent()) / 2;
        g.drawString(scoreNum, -scoreNumW / 2, numHalfH);
        g.setTransform(saved);

        // ---- CENTER: "SNAKE" title ----
        g.setFont(gf(Font.BOLD, 30));
        FontMetrics titleFm  = g.getFontMetrics();
        String title         = "SNAKE";
        int titleW           = titleFm.stringWidth(title);
        int titleBaseY       = (height - titleFm.getHeight()) / 2 + titleFm.getAscent();
        g.drawString(title, (width - titleW) / 2, titleBaseY);

        // ---- RIGHT: high score digits ----
        g.setFont(gf(Font.BOLD, 24));
        numFm                = g.getFontMetrics();
        String hsNum         = cachedHighScoreStr;
        int hsNumW           = numFm.stringWidth(hsNum);
        int hsNumBaseY       = (height - numFm.getHeight()) / 2 + numFm.getAscent();
        g.drawString(hsNum, width - 14 - hsNumW, hsNumBaseY);

        g.setFont(gf(Font.BOLD, 12));
        labelFm              = g.getFontMetrics();
        String hiLabel       = "HI";
        int hiLabelX         = width - 14 - hsNumW - labelFm.stringWidth(hiLabel) - 6;
        int hiLabelBaseY     = (height - labelFm.getHeight()) / 2 + labelFm.getAscent();
        g.drawString(hiLabel, hiLabelX, hiLabelBaseY);
    }

    // =========================================================================
    // Draw: Footer HUD
    // =========================================================================

    /**
     * Draws the bottom HUD bar.
     *
     * <pre>
     *  [LVL  3]      [■■■■■□□□□□]      [COMBO x4]
     *                  STAMINA
     * </pre>
     *
     * @param width    content width
     * @param footerY  y-coordinate of the footer zone's top edge (content space)
     * @param height   footer zone height
     */
    private void drawFooter(Graphics2D g, int width, int footerY, int height, SnakeGame.RenderSnapshot snapshot) {
        g.setColor(GB_DARKEST);

        // ---- LEFT: "LVL" label + level number ----
        g.setFont(gf(Font.BOLD, 12));
        FontMetrics labelFm  = g.getFontMetrics();
        int labelBaseY       = footerY + (height - labelFm.getHeight()) / 2 + labelFm.getAscent();
        String lvlLabel      = "LVL";
        g.drawString(lvlLabel, 14, labelBaseY);

        g.setFont(gf(Font.BOLD, 24));
        FontMetrics numFm    = g.getFontMetrics();
        String lvlNum        = String.valueOf((snapshot.score() / 5) + 1);
        int lvlBaseY         = footerY + (height - numFm.getHeight()) / 2 + numFm.getAscent();
        g.drawString(lvlNum, 14 + labelFm.stringWidth(lvlLabel) + 6, lvlBaseY);

        // ---- CENTER: retro segmented stamina bar ----
        final int SEG_W   = 10;
        final int SEG_GAP = 2;
        int barW          = STAMINA_SEGMENTS * SEG_W + (STAMINA_SEGMENTS - 1) * SEG_GAP;
        int barH          = 8;
        int barX          = (width - barW) / 2;
        int barY          = footerY + (height - barH) / 2 - 5;

        g.setFont(gf(Font.PLAIN, 9));
        FontMetrics stFm  = g.getFontMetrics();
        String stLabel    = "STAMINA";
        g.setColor(GB_DARKEST);
        g.drawString(stLabel, (width - stFm.stringWidth(stLabel)) / 2, barY - 2);

        int  filledSegments = (int) Math.round(dashStamina / MAX_STAMINA * STAMINA_SEGMENTS);
        boolean blinkOn     = (animationTick / 5) % 2 == 0;

        for (int i = 0; i < STAMINA_SEGMENTS; i++) {
            int sx      = barX + i * (SEG_W + SEG_GAP);
            boolean filled = i < filledSegments;
            if (filled) {
                g.setColor(GB_DARKEST);
                g.fillRect(sx, barY, SEG_W, barH);
            } else {
                g.setColor(GB_DARK);
                g.fillRect(sx, barY, SEG_W, barH);
                g.setColor(GB_LIGHTEST);
                g.fillRect(sx + 1, barY + 1, SEG_W - 2, barH - 2);
            }
        }

        if (dashStamina == 0 && blinkOn) {
            g.setColor(GB_DARK);
            g.setStroke(new BasicStroke(1));
            g.drawRect(barX - 1, barY - 1, barW + 2, barH + 2);
        }

        g.setStroke(new BasicStroke(1));

        // ---- RIGHT: "COMBO" label + multiplier ----
        boolean comboOn  = snapshot.combo() > 1 && blinkOn;

        g.setFont(gf(Font.BOLD, 24));
        numFm             = g.getFontMetrics();
        String comboNum   = cachedComboStr;
        int    comboNumW  = numFm.stringWidth(comboNum);
        int    comboNumX  = width - 14 - comboNumW;
        int comboNumBaseY = footerY + (height - numFm.getHeight()) / 2 + numFm.getAscent();
        g.setColor(comboOn ? GB_DARK : GB_DARKEST);
        g.drawString(comboNum, comboNumX, comboNumBaseY);

        g.setFont(gf(Font.BOLD, 12));
        labelFm            = g.getFontMetrics();
        String comboLabel  = "COMBO";
        int comboLabelX    = comboNumX - labelFm.stringWidth(comboLabel) - 4;
        int comboLabelBaseY = footerY + (height - labelFm.getHeight()) / 2 + labelFm.getAscent();
        g.setColor(GB_DARKEST);
        g.drawString(comboLabel, comboLabelX, comboLabelBaseY);
    }

    // =========================================================================
    // Draw: Juice (particles + floating text)
    // =========================================================================

    private void drawJuice(Graphics2D g) {
        java.awt.Composite originalComposite = g.getComposite();
        for (int i = 0; i < particlePool.length; i++) {
            Particle p = particlePool[i];
            if (!p.active) continue;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    Math.max(0.0f, Math.min(1.0f, p.alpha))));
            g.setColor(p.color);
            g.fillRect((int) p.x, (int) p.y, 3, 3);
        }
        g.setComposite(originalComposite);

        g.setFont(gf(Font.BOLD, 16));
        for (FloatingText ft : floatingTexts) {
            if (ft.isCombo && (animationTick / 2) % 2 == 0)
                g.setColor(GB_LIGHTEST);
            else
                g.setColor(GB_DARKEST);

            int jitterX = ft.isCombo ? visualRandom.nextInt(3) - 1 : 0;
            int jitterY = ft.isCombo ? visualRandom.nextInt(3) - 1 : 0;
            g.drawString(ft.text, (int) ft.x + jitterX, (int) ft.y + jitterY);
        }
    }

    // =========================================================================
    // Draw: Title Screen (main menu)
    // =========================================================================

    /**
     * Premium main menu screen.
     *
     * <pre>
     *  [chrome: bezel + dot grid]
     *
     *  ┌──────────────────────────────────────┐  floating panel
     *  │                                      │
     *  │         SNAKE  (72pt, shadowed)      │
     *  │                                      │
     *  │    ▶  PRESS ENTER TO PLAY  ◀  (blink)│
     *  │    ─────────────────────────         │
     *  │  ■  PLAY                             │
     *  │     SETTINGS                         │
     *  │                                      │
     *  │  [W/S ↑↓ navigate · ENTER confirm]  │
     *  └──────────────────────────────────────┘
     * </pre>
     *
     * Navigation cycles {@link #titleSelection}: 0 = PLAY, 1 = SETTINGS.
     */
    private void drawTitleScreen(Graphics2D g, int vW, int vH, int bezelW, int bezelH) {
        drawMenuChrome(g, vW, vH, bezelW, bezelH);

        final int MENU_PAD = 24;
        int menuW = Math.min(vW - MARGIN * 4, 500);
        int menuH = 360;
        int menuX = (vW - menuW) / 2;
        int menuY = (vH - menuH) / 2;

        // ---- Floating panel ----
        g.setColor(GB_LIGHTEST);
        g.fillRect(menuX, menuY, menuW, menuH);
        g.setColor(GB_DARKEST);
        g.setStroke(new BasicStroke(4));
        g.drawRect(menuX, menuY, menuW, menuH);
        g.setStroke(new BasicStroke(1));

        // ---- "SNAKE" title with 2px drop shadow ----
        g.setFont(gf(Font.BOLD, 72));
        FontMetrics titleFm = g.getFontMetrics();
        String title        = "SNAKE";
        int titleW          = titleFm.stringWidth(title);
        int titleX          = menuX + (menuW - titleW) / 2;
        int titleBaseY      = menuY + MENU_PAD + titleFm.getAscent();

        g.setColor(GB_DARK);    // shadow
        g.drawString(title, titleX + 2, titleBaseY + 2);
        g.setColor(GB_DARKEST); // real
        g.drawString(title, titleX, titleBaseY);

        // ---- Blinking prompt ----
        int curY = titleBaseY + titleFm.getDescent() + 18;
        g.setFont(gf(Font.BOLD, 15));
        FontMetrics promptFm = g.getFontMetrics();
        if ((animationTick / 30) % 2 == 0) {
            String prompt = "\u25b6  PRESS ENTER TO PLAY  \u25c0";
            int promptX   = menuX + (menuW - promptFm.stringWidth(prompt)) / 2;
            g.setColor(GB_DARKEST);
            g.drawString(prompt, promptX, curY + promptFm.getAscent());
        }
        curY += promptFm.getHeight() + 14;

        // ---- Horizontal divider ----
        g.setColor(GB_DARK);
        g.drawLine(menuX + MENU_PAD, curY, menuX + menuW - MENU_PAD, curY);
        curY += 20;

        // ---- Menu rows ----
        final String[] LABELS = { "PLAY", "SETTINGS" };
        final int ROW_H       = 38;
        int cursorX           = menuX + MENU_PAD;
        int labelX            = menuX + MENU_PAD + 20;

        g.setFont(gf(Font.BOLD, 20));
        FontMetrics rowFm = g.getFontMetrics();

        for (int i = 0; i < LABELS.length; i++) {
            int rowTop   = curY + i * ROW_H;
            int rowBaseY = rowTop + (ROW_H - rowFm.getHeight()) / 2 + rowFm.getAscent();
            boolean sel  = (titleSelection == i);

            if (sel) {
                g.setColor(GB_DARKEST);
                // Filled-square cursor
                int sqY = rowTop + (ROW_H - 10) / 2;
                g.fillRect(cursorX, sqY, 10, 10);
                g.drawString(LABELS[i], labelX, rowBaseY);
            } else {
                g.setColor(GB_DARK);
                g.drawString(LABELS[i], labelX, rowBaseY);
            }
        }

        // ---- Footer hint ----
        g.setFont(gf(Font.PLAIN, 11));
        FontMetrics hintFm = g.getFontMetrics();
        String hint = "W/S \u00b7 \u2191\u2193  navigate     ENTER  confirm     ESC  quit";
        int hintX   = menuX + (menuW - hintFm.stringWidth(hint)) / 2;
        g.setColor(GB_DARK);
        g.drawString(hint, hintX, menuY + menuH - MENU_PAD);
    }

    // =========================================================================
    // Draw: Settings Screen
    // =========================================================================

    /**
     * Premium settings screen with two-column layout and banner row highlighting.
     *
     * <pre>
     *  ┌──────────────────────────────────────────────────┐
     *  │  SETTINGS                                        │
     *  │  ──────────────────────────────────────────────  │
     *  │  ████ ■ DIFFICULTY               < NORMAL >  ███ │  ← selected
     *  │       BOARD                        MEDIUM        │
     *  │       ZOOM                         2.0x          │
     *  │  ──────────────────────────────────              │
     *  │       RESET HI-SCORE                             │
     *  │       RETURN                                     │
     *  │  [W/S navigate · ENTER confirm · ←→ toggle]     │
     *  └──────────────────────────────────────────────────┘
     * </pre>
     *
     * Toggleable rows (0–6) show {@code < VALUE >} when selected.
     * Action rows (7–8) show no value column.
     * LEFT / RIGHT arrows also call {@link #triggerSelectedSetting}.
     */
    private void drawSettingsScreen(Graphics2D g, int vW, int vH, int bezelW, int bezelH) {
        drawMenuChrome(g, vW, vH, bezelW, bezelH);

        final int MENU_PAD = 20;
        final int ROW_H    = 34;
        int menuW = Math.min(vW - MARGIN * 4, 500);
        int menuH = 492;
        int menuX = (vW - menuW) / 2;
        int menuY = (vH - menuH) / 2;

        // ---- Floating panel ----
        g.setColor(GB_LIGHTEST);
        g.fillRect(menuX, menuY, menuW, menuH);
        g.setColor(GB_DARKEST);
        g.setStroke(new BasicStroke(4));
        g.drawRect(menuX, menuY, menuW, menuH);
        g.setStroke(new BasicStroke(1));

        // ---- Panel header ----
        g.setFont(gf(Font.BOLD, 22));
        FontMetrics hdrFm = g.getFontMetrics();
        int hdrBaseY      = menuY + MENU_PAD + hdrFm.getAscent();
        g.setColor(GB_DARKEST);
        g.drawString("SETTINGS", menuX + MENU_PAD, hdrBaseY);

        // Divider under header title
        int divY = hdrBaseY + hdrFm.getDescent() + 8;
        g.setColor(GB_DARK);
        g.drawLine(menuX + MENU_PAD, divY, menuX + menuW - MENU_PAD, divY);

        // ---- Row data ----
        String[] names = {
            "ZOOM", "BOARD", "DIFFICULTY",
            "DASH", "HITSTOP", "MUSIC",
            "RESET HI-SCORE", "RETURN"
        };
        boolean[] isAction = { false, false, false, false, false, false, true, true };
        String[] values = {
            game.getScale() + "x",
            game.getBoardSize().name(),
            game.getDifficulty().name(),
            game.isDashEnabled()    ? "ON" : "OFF",
            game.isHitstopEnabled() ? "ON" : "OFF",
            game.isMusicEnabled()   ? "ON" : "OFF",
            "", ""
        };

        g.setFont(gf(Font.BOLD, 15));
        FontMetrics rowFm  = g.getFontMetrics();
        int firstRowY      = divY + 14;
        int nameColX       = menuX + MENU_PAD + 16; // past cursor square
        int valueRightX    = menuX + menuW - MENU_PAD;

        for (int i = 0; i < names.length; i++) {
            // Separator line before action rows
            if (i == 6) {
                int sepY = firstRowY + i * ROW_H - 6;
                g.setColor(GB_DARK);
                g.drawLine(menuX + MENU_PAD, sepY, menuX + menuW - MENU_PAD, sepY);
            }

            int rowTop   = firstRowY + i * ROW_H;
            int rowBaseY = rowTop + (ROW_H - rowFm.getHeight()) / 2 + rowFm.getAscent();
            boolean sel  = (settingsSelection == i);

            if (sel) {
                // GB_DARK banner spanning the full panel width
                g.setColor(GB_DARK);
                g.fillRect(menuX + 2, rowTop, menuW - 4, ROW_H);

                // Filled-square cursor in GB_LIGHTEST
                g.setColor(GB_LIGHTEST);
                int sqY = rowTop + (ROW_H - 10) / 2;
                g.fillRect(menuX + MENU_PAD, sqY, 10, 10);

                // Name + value in GB_LIGHTEST for contrast against the dark banner
                g.drawString(names[i], nameColX, rowBaseY);

                if (!isAction[i]) {
                    String val = "< " + values[i] + " >";
                    int    valW = rowFm.stringWidth(val);
                    g.drawString(val, valueRightX - valW, rowBaseY);
                }
            } else {
                // Name in GB_DARKEST
                g.setColor(GB_DARKEST);
                g.drawString(names[i], nameColX, rowBaseY);

                // Value in GB_DARK (de-emphasised)
                if (!isAction[i]) {
                    g.setColor(GB_DARK);
                    int valW = rowFm.stringWidth(values[i]);
                    g.drawString(values[i], valueRightX - valW, rowBaseY);
                }
            }
        }

        // ---- Footer hint ----
        g.setFont(gf(Font.PLAIN, 11));
        FontMetrics hintFm = g.getFontMetrics();
        String hint = "W/S  navigate     ENTER  confirm     \u2190\u2192  toggle";
        int hintX   = menuX + (menuW - hintFm.stringWidth(hint)) / 2;
        g.setColor(GB_DARK);
        g.drawString(hint, hintX, menuY + menuH - MENU_PAD);
    }

    // =========================================================================
    // Draw: Game entities
    // =========================================================================

    /**
     * Draws the apple with a 2px offset GB_DARK drop shadow.
     * The shadow is rendered first (at +2,+2), then the real apple on top.
     */
    private void drawApple(Graphics2D g, SnakeGame.RenderSnapshot snapshot) {
        java.awt.Point apple = snapshot.applePosition();
        if (apple == null) return;
        int x = apple.x * TILE_SIZE;
        int y = apple.y * TILE_SIZE;

        AffineTransform oldTrans = g.getTransform();

        // Shadow pass
        g.translate(x + TILE_SIZE / 2.0 + 2, y + TILE_SIZE / 2.0 + 2);
        g.scale(appleScale, appleScale);
        g.setColor(GB_DARK);
        drawAppleShape(g);
        g.setTransform(oldTrans);

        // Real apple pass
        g.translate(x + TILE_SIZE / 2.0, y + TILE_SIZE / 2.0);
        g.scale(appleScale, appleScale);
        g.setColor(GB_DARKEST);
        drawAppleShape(g);
        g.setTransform(oldTrans);
    }

    /** Renders the pixel-art apple body centered at the current transform origin. */
    private void drawAppleShape(Graphics2D g) {
        g.fillRect(-7, -4,  15, 12);
        g.fillRect(-5, -6,  11,  2);
        g.fillRect(-5,  8,  11,  2);
        g.fillRect( 0, -10,  2,  4);
        g.fillRect( 2,  -8,  4,  2);
    }

    /**
     * Draws the snake for the given player.
     * A single-pass GB_DARK shadow (offset +2,+2) is rendered for every segment
     * before the real snake. Shadow is suppressed during the death-flash invert.
     */
    private void drawSnake(Graphics2D g, SnakeGame.RenderSnapshot snapshot) {
        java.util.List<java.awt.Point> snake = snapshot.bodyPositions();
        if (snake.isEmpty()) return;

        boolean invert    = (deathFlashFrames > 0 && (deathFlashFrames / 6) % 2 == 0);
        Color   headColor = invert ? GB_LIGHTEST : GB_DARKEST;
        Color   eyeColor  = invert ? GB_DARKEST  : GB_LIGHTEST;
        Color   bodyColor = invert ? GB_LIGHTEST  : GB_DARKEST;

        java.awt.Point head = snake.get(0);
        int hx = head.x * TILE_SIZE;
        int hy = head.y * TILE_SIZE;

        int dx = 1, dy = 0;
        if (snake.size() > 1) {
            java.awt.Point neck = snake.get(1);
            dx = head.x - neck.x;
            dy = head.y - neck.y;
        }

        // ---- Shadow pass ----
        if (!invert) {
            g.setColor(GB_DARK);
            g.fillRect(hx + 4, hy + 4, TILE_SIZE - 4, TILE_SIZE - 4);
            for (int i = 1; i < snake.size(); i++) {
                java.awt.Point part = snake.get(i);
                g.fillRect(part.x * TILE_SIZE + 4,
                           part.y * TILE_SIZE + 4,
                           TILE_SIZE - 4, TILE_SIZE - 4);
            }
        }

        // ---- Head ----
        g.setColor(headColor);
        g.fillRect(hx + 2, hy + 2, TILE_SIZE - 4, TILE_SIZE - 4);

        g.setColor(eyeColor);
        if (dx == 1) {
            g.fillRect(hx + 16, hy + 4,  4, 4);
            g.fillRect(hx + 16, hy + 17, 4, 4);
        } else if (dx == -1) {
            g.fillRect(hx + 5, hy + 4,  4, 4);
            g.fillRect(hx + 5, hy + 17, 4, 4);
        } else if (dy == 1) {
            g.fillRect(hx + 4,  hy + 16, 4, 4);
            g.fillRect(hx + 17, hy + 16, 4, 4);
        } else if (dy == -1) {
            g.fillRect(hx + 4,  hy + 5, 4, 4);
            g.fillRect(hx + 17, hy + 5, 4, 4);
        }

        // ---- Body ----
        g.setColor(bodyColor);
        for (int i = 1; i < snake.size(); i++) {
            java.awt.Point part = snake.get(i);
            g.fillRect(part.x * TILE_SIZE + 2,
                       part.y * TILE_SIZE + 2,
                       TILE_SIZE - 4, TILE_SIZE - 4);
        }
    }

    // =========================================================================
    // Draw: Grid
    // =========================================================================

    /**
     * Draws a subtle dot grid: a single 1×1 pixel at every interior tile-corner
     * intersection. Far less visually noisy than full-width solid lines.
     */
    private void drawGrid(Graphics2D g, int boardWidth, int boardHeight) {
        g.setColor(GB_DARK);
        int cols = boardWidth / TILE_SIZE;
        int rows = boardHeight / TILE_SIZE;
        for (int xi = 1; xi < cols; xi++) {
            for (int yi = 1; yi < rows; yi++) {
                g.fillRect(xi * TILE_SIZE, yi * TILE_SIZE, 1, 1);
            }
        }
    }

    // =========================================================================
    // Draw: Overlay (pause / game over)
    // =========================================================================

    private void drawOverlay(Graphics2D g, String title, String sub, int gridWidth, int gridHeight) {
        int boxWidth  = 280;
        int boxHeight = 100;
        int boxX = (gridWidth  - boxWidth)  / 2;
        int boxY = (gridHeight - boxHeight) / 2;

        g.setColor(GB_LIGHTEST);
        g.fillRect(boxX, boxY, boxWidth, boxHeight);
        g.setColor(GB_DARKEST);
        g.setStroke(new BasicStroke(4));
        g.drawRect(boxX, boxY, boxWidth, boxHeight);
        g.setStroke(new BasicStroke(1));

        g.setFont(gf(Font.BOLD, 40));
        g.drawString(title, (gridWidth - g.getFontMetrics().stringWidth(title)) / 2, gridHeight / 2 - 5);
        g.setFont(gf(Font.BOLD, 16));
        if ((animationTick / 30) % 2 == 0)
            g.drawString(sub, (gridWidth - g.getFontMetrics().stringWidth(sub)) / 2, gridHeight / 2 + 35);
    }
}