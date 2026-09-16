# RETROSNAKE
**Production Build:** `1.1.0 Release`  
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
Download the `RetroSnake-Windows-Installer.exe` file from the Releases page.
*   Simply double-click the setup file to install the game!
*   It will automatically set up RetroSnake on your computer and create an easy-to-use shortcut.

### 2. Native Executable (Recommended for Linux)
If you downloaded a native build (like the Linux App-Image), you don't need to install Java or touch the terminal! 
*   Simply extract the folder and double-click the **RetroSnake** application to play.

### 3. Run the Executable JAR
If you have Java installed on your system, you can run the pre-packaged executable archive.
*   Open your terminal in the project folder.
*   Run: `java -jar libs/RetroSnake.jar`

### 4. Compile from Source (For Developers)
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
