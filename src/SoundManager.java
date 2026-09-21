
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

//Pretty proud of SoundManager, did a lot of effort to get it working!
public class SoundManager {

    private volatile boolean isBgmPlaying = false;
    private Thread bgmThread;
    private int bgmNoteIndex = 0;

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

    public void playBGM() {
        if (isBgmPlaying) return;
        isBgmPlaying = true;
        
        bgmThread = new Thread(() -> {
            while (isBgmPlaying) {
                double[] note = BGM_NOTES[bgmNoteIndex];
                double hz = note[0];
                int msecs = (int)note[1];
                
                if (hz > 0) playTone(hz, msecs, false, 0.05);
                
                try {
                    Thread.sleep(msecs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
                bgmNoteIndex = (bgmNoteIndex + 1) % BGM_NOTES.length;
            }
        });
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

    // 8-Bit UI Navigation Bleep
    public void playBlip() {
        playTone(880, 40, false, 0.1);
    }

    // Satisfying crunch when eating (White noise burst)
    public void playCrunch() {
        playTone(300, 60, true, 0.3);
        playTone(400, 40, true, 0.2);
    }

    // Massive retro death explosion
    public void playExplosion() {
        playTone(100, 500, true, 0.5);
    }

    // Frantic high-pitched ticking for the panicked apple
    public void playTick() {
        playTone(1200, 15, false, 0.05);
    }

    // Start game chime
    public void playStart() {
        playTone(440, 100, false, 0.1);
        playTone(660, 150, false, 0.1);
    }

    /**
     * A custom built-in synthesizer that generates PCM byte data on the fly.
     * hz: Frequency of the note
     * msecs: How long the sound plays
     * noise: If true, generates static white-noise. If false, generates a pure
     * retro square-wave.
     * vol: Volume multiplier (0.0 to 1.0)
     */
    private void playTone(double hz, int msecs, boolean noise, double vol) {
        new Thread(() -> {
            try {
                float sampleRate = 44100;
                byte[] buf = new byte[(int) (msecs * sampleRate / 1000)];

                for (int i = 0; i < buf.length; i++) {
                    double angle = i / (sampleRate / hz) * 2.0 * Math.PI;
                    // Generate either static white noise, or a harsh 8-bit square wave
                    double wave = noise ? (Math.random() - 0.5) * 2.0 : (Math.sin(angle) > 0 ? 1.0 : -1.0);
                    buf[i] = (byte) (wave * vol * 127);
                }

                AudioFormat af = new AudioFormat(sampleRate, 8, 1, true, false);
                SourceDataLine sdl = AudioSystem.getSourceDataLine(af);
                sdl.open(af);
                sdl.start();
                sdl.write(buf, 0, buf.length);
                sdl.drain();
                sdl.stop();
                sdl.close();
            } catch (Exception e) {
                // Fail silently if the computer has no audio device
            }
        }).start();
    }
}