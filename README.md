# RETROSNAKE
**Production Build:** `1.0.0 Release`  
**Author:** atharvajshinde

Welcome to **RetroSnake!**

---

## CONTROLS
*   **W A S D** - Move Snake
*   **SPACE** - Start Game
*   **S** - Open Settings (from Title)
*   **T** - Cycle Color Themes (in Settings)
*   **X** - Reset High Score (in Settings)
*   **P** - Pause / Resume
*   **R** - Restart Game (on Game Over)
*   **ESC** - Quit

---

## HOW TO RUN THE GAME

## ALL FILES CAN BE DOWNLOADED THROUGH THE RELEASES SECTION OF THIS REPO!

Choose the method that works best for you:
### 1. Windows Installer (Highly Recommended!)
In this method, you can simply install the setup file, which will automatically setup the entire RetroSnake on your computer!
*   If provided with the full folder along with source code and others, navigate to buils\installer\RetroSnakeInstaller.Executable
*   Now, simply follow and run the entire setup wizard to get playing.
*   You may also be provided with a simple executable, you can also run that to install it!


### 1. Native Executable (Recommended)
If you downloaded a native build (`.exe` for Windows or the Linux App-Image), you don't need to install Java or touch the terminal! 
*   Simply open the `builds/` folder for your OS.
*   Double-click the **RetroSnake** application to play.

### 2. Run the Executable JAR
If you have Java installed on your system, you can run the pre-packaged executable archive.
*   Open your terminal in the project folder.
*   Run: `java -jar libs/RetroSnake.jar`

### 3. Compile from Source (For Developers)
1. Open your terminal in the root project folder.
2. Compile the code: `javac -d bin src/*.java`
3. Run the game: `java -cp bin SnakeApp`

---

## S P O I L E R S  A H E A D
*Warning: The section below contains the Game Manual and explains advanced mechanics. It is highly recommended to play the game blind first to experience the surprises yourself!*

<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>

---

## THE GAME MANUAL

### The Aesthetics (Themes)
You can cycle through 5 distinct visual aesthetics in the Settings menu:
1.  **Classic Retro:** The default. Deep slate grays with smooth, anti-aliased rendering.
2.  **Game Boy:** 4-color olive green palette with pixelated, scaly rendering.
3.  **Synthwave:** Deep neon purples, hot pinks, and cyan grid lines.
4.  **Hacker:** Pure terminal black with high-contrast, glowing neon green.
5.  **Virtual Boy:** Pure black with intense red accents (eye-strain not included).

### Advanced Mechanics
*   **The Leveling System:** For every 5 apples you eat, the game levels up, permanently increasing the snake's speed.
*   **The Panicked Apple:** If an apple sits uneaten for too long, it will begin to pulse. Shortly after, it will actively try to pathfind and run away from your snake's head!
*   **The Phantom Apple:** A rare, glowing blue/gray apple. Eating this grants you "Ghost Mode" for 45 moves. 
    *   *Ghost Mode:* Your snake changes color, and you can safely cross through your own tail without dying. 
    *   *Warning:* When you have 10 moves of invincibility left, your snake will begin to rapidly flicker. Get out of your own body before the timer hits 0!
