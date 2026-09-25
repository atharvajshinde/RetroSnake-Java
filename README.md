<div align="center">
  <!-- REPLACE THE SRC LINK BELOW WITH YOUR ACTUAL LOGO URL OR LOCAL PATH (e.g., assets/logo.png) -->
  <img src="~/Pictures/assets/SNAKELOGO.png" alt="RetroSnake Logo" width="150" height="150">
  
  # RETROSNAKE
  
  **Production Build: v1.0 — The First Release!**[cite: 1]
  
  [![Java](https://img.shields.io/badge/Java-21+-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](#)
  [![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS-lightgrey?style=for-the-badge)](#)
  [![Build](https://img.shields.io/badge/Build-Passing-brightgreen?style=for-the-badge)](#)

  *One of the best journeys OAT! Welcome to RetroSnake!*[cite: 1]
  
  <!-- REPLACE THE SRC LINK BELOW WITH A .GIF OF YOUR GAMEPLAY -->
  <img src="https://via.placeholder.com/600x350/000000/32CD32?text=Drop+Your+Gameplay.gif+Here" alt="Gameplay Animation" width="600">
</div>

---

## Engine Architecture & Performance (Changelog)

The UI has been completely restructured into a thick hardware frame featuring a top Header, right Sidebar, and bottom Footer to truly mimic a retro arcade cabinet[cite: 1]. The engine has been completely overhauled for maximum performance:

*   **Decoupled Fixed-Timestep Game Loop:** The game engine has been moved off the Swing Event Dispatch Thread onto a dedicated 60 TPS physics thread, providing perfectly deterministic timing without frame stutter[cite: 1].
*   **Zero-Allocation Pipeline & Object Pooling:** Eliminated GC-induced microstutters by utilizing a pre-allocated object pool for particles and caching formatted HUD strings[cite: 1].
*   **Double-Buffered Snapshots:** Implemented a thread-safe `RenderSnapshot` pattern that safely hands game state to the UI renderer without lock contention[cite: 1].
*   **O(1) Collision Detection:** Replaced linear `LinkedList` collision checks with an instantaneously updated 2D boolean grid[cite: 1].
*   **Pre-computed Audio Engine:** Audio clips are now pre-synthesized at startup into memory buffers, guaranteeing zero audio lag or thread-creation churn[cite: 1].
*   **Asynchronous Saves:** High scores are now written to disk via a background daemon thread, preventing disk I/O latency from stalling gameplay[cite: 1].
*   **Cinematic Boot Sequence:** The game now boots into a 1.5-second cinematic splash screen featuring "atharvajshinde studios"[cite: 1].

## Controls

| Action | General / UI | Player 1 (1P / 2P) | Player 2 (2P Mode) |
| :--- | :--- | :--- | :--- |
| **Move** | <kbd>W</kbd> <kbd>S</kbd> (Navigate)[cite: 1] | <kbd>W</kbd> <kbd>A</kbd> <kbd>S</kbd> <kbd>D</kbd>[cite: 1] | <kbd>↑</kbd> <kbd>↓</kbd> <kbd>←</kbd> <kbd>→</kbd>[cite: 1] |
| **Dash** | - | <kbd>Q</kbd> / <kbd>L-Shift</kbd>[cite: 1] | <kbd>R-Ctrl</kbd>[cite: 1] |
| **Start / Select** | <kbd>Space</kbd> / <kbd>Enter</kbd>[cite: 1] | - | - |
| **Pause / Resume**| <kbd>P</kbd>[cite: 1] | - | - |
| **Restart Game** | <kbd>R</kbd> (on Game Over)[cite: 1] | - | - |
| **Quit** | <kbd>ESC</kbd>[cite: 1] | - | - |

> *Note: In 1-Player mode, Arrow Keys can also be used to control Player 1!*[cite: 1]

## How to Run the Game

All files can be downloaded through the **Releases** section of this repo![cite: 1] Choose the method that works best for you:

1.  **Windows Installer (Highly Recommended!):** Download the `RetroSnake-Windows-Installer.exe` file from the Releases page[cite: 1]. Simply double-click the setup file to install the game; it will automatically set up RetroSnake on your computer and create an easy-to-use shortcut[cite: 1].
2.  **Native Executable (Recommended for Linux):** If you downloaded a native build, you don't need to install Java or touch the terminal![cite: 1] Simply extract the folder and double-click the **RetroSnake** application to play[cite: 1].
3.  **Run the Executable JAR:** If you have Java installed on your system, you can run the pre-packaged executable archive[cite: 1]. Open your terminal in the project folder and run: `java -jar libs/RetroSnake.jar`[cite: 1].
4.  **Compile from Source (For Developers):** Open your terminal in the root project folder[cite: 1]. Compile the code: `javac -d bin src/*.java`[cite: 1]. Run the game: `java -cp bin SnakeApp`[cite: 1].

## The Game Manual (Spoilers Ahead!)

<details>
<summary><b>Click to reveal advanced mechanics & game juice</b></summary>
<br>

*Warning: It is highly recommended to play the game blind first to experience the surprises yourself!*[cite: 1]

*   **Dynamic Scaling & Boards:** Use the settings menu to adjust your screen zoom (1.0x to 3.0x) and choose between Small (16x16), Medium (24x24), and Large (32x32) grids[cite: 1]. Larger boards increase the snake's base speed![cite: 1]
*   **Local 2-Player Versus:** Switch to `2-PLAYER` mode in settings![cite: 1] You can crash into the opponent's body to win, or crash head-to-head for a Draw[cite: 1]. Watch out for wall collisions![cite: 1]
*   **The Dash System:** Hold the dash key to consume Stamina and move 3x faster![cite: 1] Use this to cut off your opponent or snag the apple first[cite: 1]. Stamina regenerates over time[cite: 1].
*   **The Combo System:** Eating apples in quick succession triggers an escalating combo multiplier (x2, x3, x4) tracked by a draining HUD combo bar[cite: 1].
*   **The Leveling System:** For every 5 apples you eat, the game levels up, permanently increasing the snake's speed[cite: 1].
*   **Tactile Feedback:** Experience 60FPS fluid motion featuring screen shake on death, hit-stop micro-pauses on apple bites, rolling pinball score counters, and ghost trails at high combos[cite: 1].

</details>

---
<div align="center">
  <i>Created by atharvajshinde</i>
</div>