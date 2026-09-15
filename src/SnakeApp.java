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

        
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            setUndecorated(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);

        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            setUndecorated(true);
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

            if (gd.isFullScreenSupported()) {
                gd.setFullScreenWindow(this);
            } else {
                setExtendedState(JFrame.MAXIMIZED_BOTH);
            }

        } else if (os.contains("mac")) {
            setUndecorated(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);

        } else {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new SnakeApp().setVisible(true);
        });
    }
}
