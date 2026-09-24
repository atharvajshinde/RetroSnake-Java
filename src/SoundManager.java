
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;
import java.util.Random;

//Pretty proud of SoundManager, did a lot of effort to get it working!
public class SoundManager {

    private volatile boolean isBgmPlaying = false;
    private Thread bgmThread;
    private int bgmNoteIndex = 0;

    private static final AudioFormat FORMAT = new AudioFormat(44100, 8, 1, true, false);

    // Pre-computed audio clips (initialized once, reused on every playback)
    private Clip blipClip;
    private Clip tickClip;
    private Clip crunchClip1;
    private Clip crunchClip2;
    private Clip explosionClip;
    private Clip startClip1;
    private Clip startClip2;

    private static final double[][] BGM_NOTES = {
        {659, 250}, {0, 30}, {494, 120}, {0, 30}, {523, 120}, {0, 30}, {587, 250}, {0, 30}, {523, 120}, {0, 30}, {494, 120}, {0, 30},
        {440, 250}, {0, 30}, {440, 120}, {0, 30}, {523, 120}, {0, 30}, {659, 250}, {0, 30}, {587, 120}, {0, 30}, {523, 120}, {0, 30},
        {494, 400}, {0, 50}, {523, 120}, {0, 30}, {587, 250}, {0, 30}, {659, 250}, {0, 30}, {523, 250}, {0, 30}, {440, 250}, {0, 30},
        {440, 550}, {0, 50}, {659, 250}, {0, 30}, {494, 120}, {0, 30}, {523, 120}, {0, 30}, {587, 250}, {0, 30}, {523, 120}, {0, 30},
        {494, 120}, {0, 30}, {440, 250}, {0, 30}, {440, 120}, {0, 30}, {523, 120}, {0, 30}, {659, 250}, {0, 30}, {587, 120}, {0, 30},
        {523, 120}, {0, 30}, {494, 400}, {0, 50}, {523, 120}, {0, 30}, {587, 250}, {0, 30}, {659, 250}, {0, 30}, {523, 250}, {0, 30},
        {440, 250}, {0, 30}, {440, 550}, {0, 50}, {587, 400}, {0, 50}, {698, 120}, {0, 30}, {880, 250}, {0, 30}, {784, 120}, {0, 30},
        {698, 120}, {0, 30}, {659, 400}, {0, 50}, {523, 120}, {0, 30}, {659, 250}, {0, 30}, {587, 120}, {0, 30}, {523, 120}, {0, 30},
        {494, 400}, {0, 50}, {523, 120}, {0, 30}, {587, 250}, {0, 30}, {659, 250}, {0, 30}, {523, 250}, {0, 30}, {440, 250}, {0, 30},
        {440, 550}, {0, 50}, {587, 400}, {0, 50}, {698, 120}, {0, 30}, {880, 250}, {0, 30}, {784, 120}, {0, 30}, {698, 120}, {0, 30},
        {659, 400}, {0, 50}, {523, 120}, {0, 30}, {659, 250}, {0, 30}, {587, 120}, {0, 30}, {523, 120}, {0, 30}, {494, 400}, {0, 50},
        {523, 120}, {0, 30}, {587, 250}, {0, 30}, {659, 250}, {0, 30}, {523, 250}, {0, 30}, {440, 250}, {0, 30}, {440, 550}, {0, 50}
    };

    public SoundManager() {
        // Pre-synthesize all sound effects into Clip instances (zero allocation at playback time)
        try { blipClip      = createClip(synthesizeTone(880,  40, false, 0.1));  } catch (Exception ignored) {}
        try { tickClip      = createClip(synthesizeTone(1200, 15, false, 0.05)); } catch (Exception ignored) {}
        try { crunchClip1   = createClip(synthesizeTone(300,  60, true,  0.3));  } catch (Exception ignored) {}
        try { crunchClip2   = createClip(synthesizeTone(400,  40, true,  0.2));  } catch (Exception ignored) {}
        try { explosionClip = createClip(synthesizeTone(100, 500, true,  0.5));  } catch (Exception ignored) {}
        try { startClip1    = createClip(synthesizeTone(440, 100, false, 0.1));  } catch (Exception ignored) {}
        try { startClip2    = createClip(synthesizeTone(660, 150, false, 0.1));  } catch (Exception ignored) {}

        // Release audio lines when the JVM exits
        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));
    }

    // =========================================================================
    // Audio Synthesis (runs once at init time, never during gameplay)
    // =========================================================================

    /**
     * Pre-synthesizes a PCM waveform into a byte array.
     * hz:    Frequency of the note
     * msecs: How long the sound plays
     * noise: If true, generates static white-noise. If false, generates a pure
     *        retro square-wave.
     * vol:   Volume multiplier (0.0 to 1.0)
     */
    private byte[] synthesizeTone(double hz, int msecs, boolean noise, double vol) {
        float sampleRate = FORMAT.getSampleRate();
        byte[] buf = new byte[(int) (msecs * sampleRate / 1000)];
        Random noiseRng = new Random(42); // fixed seed for deterministic noise
        for (int i = 0; i < buf.length; i++) {
            double angle = i / (sampleRate / hz) * 2.0 * Math.PI;
            double wave = noise ? (noiseRng.nextDouble() - 0.5) * 2.0
                                : (Math.sin(angle) > 0 ? 1.0 : -1.0);
            buf[i] = (byte) (wave * vol * 127);
        }
        return buf;
    }

    /**
     * Wraps raw PCM data into a ready-to-play Clip.
     */
    private Clip createClip(byte[] pcm) throws Exception {
        Clip clip = AudioSystem.getClip();
        AudioInputStream ais = new AudioInputStream(
                new ByteArrayInputStream(pcm), FORMAT, pcm.length);
        clip.open(ais);
        return clip;
    }

    // =========================================================================
    // Clip Playback (zero allocation, zero thread spawn)
    // =========================================================================

    /**
     * Triggers a pre-loaded clip: stops any current playback, rewinds, and starts.
     */
    private void triggerClip(Clip clip) {
        if (clip == null) return;
        clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    // 8-Bit UI Navigation Bleep
    public void playBlip() {
        triggerClip(blipClip);
    }

    // Satisfying crunch when eating (White noise burst)
    public void playCrunch() {
        triggerClip(crunchClip1);
        triggerClip(crunchClip2);
    }

    // Massive retro death explosion
    public void playExplosion() {
        triggerClip(explosionClip);
    }

    // Frantic high-pitched ticking for the panicked apple
    public void playTick() {
        triggerClip(tickClip);
    }

    // Start game chime
    public void playStart() {
        triggerClip(startClip1);
        triggerClip(startClip2);
    }

    // =========================================================================
    // BGM (thread-based note sequencer with reusable SourceDataLine)
    // =========================================================================

    public void playBGM() {
        if (isBgmPlaying) return;
        isBgmPlaying = true;

        bgmThread = new Thread(() -> {
            SourceDataLine line = null;
            try {
                line = AudioSystem.getSourceDataLine(FORMAT);
                line.open(FORMAT);
                line.start();

                while (isBgmPlaying) {
                    double[] note = BGM_NOTES[bgmNoteIndex];
                    double hz = note[0];
                    int msecs = (int) note[1];

                    if (hz > 0) {
                        byte[] buf = synthesizeTone(hz, msecs, false, 0.05);
                        line.write(buf, 0, buf.length);
                    }

                    try {
                        Thread.sleep(msecs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    bgmNoteIndex = (bgmNoteIndex + 1) % BGM_NOTES.length;
                }
            } catch (Exception e) {
                // Fail silently if the computer has no audio device
            } finally {
                if (line != null) {
                    line.stop();
                    line.close();
                }
            }
        });
        bgmThread.setDaemon(true);
        bgmThread.start();
    }

    public void pauseBGM() {
        isBgmPlaying = false;
        if (bgmThread != null) {
            bgmThread.interrupt();
            bgmThread = null;
        }
    }

    public void stopBGM() {
        pauseBGM();
        bgmNoteIndex = 0;
    }

    // =========================================================================
    // Resource Cleanup
    // =========================================================================

    /**
     * Releases all pre-loaded audio resources.
     * Called automatically via shutdown hook on JVM exit.
     */
    private void cleanup() {
        isBgmPlaying = false;
        if (bgmThread != null) bgmThread.interrupt();
        Clip[] clips = { blipClip, tickClip, crunchClip1, crunchClip2,
                         explosionClip, startClip1, startClip2 };
        for (Clip clip : clips) {
            if (clip != null) {
                try { clip.close(); } catch (Exception ignored) {}
            }
        }
    }
}