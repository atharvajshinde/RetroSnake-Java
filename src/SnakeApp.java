import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.GraphicsDevice;

public class SnakeApp extends JFrame {

    public SnakeApp() {
        GamePanel gamePanel = new GamePanel();
        add(gamePanel);
        setTitle("Retro Snake");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // --- SMART OS DETECTION & FULLSCREEN LOGIC ---
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            // WINDOWS: Borderless maximized works perfectly here.
            setUndecorated(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);

        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            // LINUX: Swing borderless maximization often breaks in Linux Window Managers.
            // Using the native GraphicsDevice Fullscreen API is much safer and more reliable.
            setUndecorated(true);
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

            if (gd.isFullScreenSupported()) {
                gd.setFullScreenWindow(this);
            } else {
                // Fallback just in case the specific Linux distro doesn't support Exclusive Fullscreen
                setExtendedState(JFrame.MAXIMIZED_BOTH);
            }

        } else if (os.contains("mac")) {
            // MAC: Standard borderless maximization.
            setUndecorated(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);

        } else {
            // FALLBACK for any other unknown OS.
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new SnakeApp().setVisible(true);
        });
    }
}
