import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

public class SoundManager {
    
    // 8-Bit UI Navigation Bleep
    public void playBlip() { playTone(880, 40, false, 0.1); }
    
    // Satisfying crunch when eating (White noise burst)
    public void playCrunch() { 
        playTone(300, 60, true, 0.3); 
        playTone(400, 40, true, 0.2); 
    }
    
    // Massive retro death explosion
    public void playExplosion() { playTone(100, 500, true, 0.5); }
    
    // Frantic high-pitched ticking for the panicked apple
    public void playTick() { playTone(1200, 15, false, 0.05); }
    
    // Start game chime
    public void playStart() { 
        playTone(440, 100, false, 0.1); 
        playTone(660, 150, false, 0.1); 
    }

    /**
     * A custom built-in synthesizer that generates PCM byte data on the fly.
     * hz: Frequency of the note
     * msecs: How long the sound plays
     * noise: If true, generates static white-noise. If false, generates a pure retro square-wave.
     * vol: Volume multiplier (0.0 to 1.0)
     */
    private void playTone(double hz, int msecs, boolean noise, double vol) {
        new Thread(() -> {
            try {
                float sampleRate = 44100;
                byte[] buf = new byte[(int)(msecs * sampleRate / 1000)];
                
                for (int i = 0; i < buf.length; i++) {
                    double angle = i / (sampleRate / hz) * 2.0 * Math.PI;
                    // Generate either static white noise, or a harsh 8-bit square wave
                    double wave = noise ? (Math.random() - 0.5) * 2.0 : (Math.sin(angle) > 0 ? 1.0 : -1.0);
                    buf[i] = (byte)(wave * vol * 127);
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