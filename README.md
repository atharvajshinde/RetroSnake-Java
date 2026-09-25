<div align="center">

<img src="assets/SNAKELOGO.png" alt="Snake Logo" width="320">
<br><br>
<img src="assets/RELEASELOGO.png" alt="1.0 - Release" width="280">

---

[![Java 21+](https://img.shields.io/badge/Java-21+-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](#)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS-lightgrey?style=for-the-badge)](#)

*A ground-up rebuild of the arcade classic, engineered for a deterministic 60 TPS core, zero-allocation rendering, and the same but a different feel of playing Snake for the first time.*
*Been a long time in beta, even though I kind of did not mention it like in any update lol.*

<img src="assets/gameplay.gif" alt="Snake Gameplay Preview" width="700">

[Engine](#engine-architecture--performance) &nbsp;|&nbsp; [Controls](#controls) &nbsp;|&nbsp; [Installation](#how-to-run-the-game) &nbsp;|&nbsp; [Game Manual](#the-game-manual)

</div>

<hr>

## Engine Architecture & Performance

Snake's interface has been rebuilt into a thick hardware-style frame with a top **Header**, right-hand **Sidebar**, and bottom **Footer**, built to mimic the feel of a genuine retro arcade cabinet. Underneath, the engine has been overhauled from the ground up for maximum performance (it can probably run smoothly on a potato!):

| Feature | Description |
| :--- | :--- |
| **Decoupled 60 TPS Game Loop** | Physics run on a dedicated thread, fully separated from the Swing Event Dispatch Thread, for deterministic timing with zero frame stutter. |
| **Zero-Allocation Pipeline** | Pre-allocated object pools for particles and cached HUD strings keep the garbage collector from ever causing a microstutter. |
| **Double-Buffered Snapshots** | A thread-safe `RenderSnapshot` pattern hands game state to the renderer without any lock contention. |
| **O(1) Collision Detection** | A live 2D boolean grid replaces legacy linear `LinkedList` checks for instant lookups. |
| **Pre-Computed Audio Engine** | Every clip is pre-synthesized at startup into memory buffers, so there's zero audio lag and no runtime thread-creation churn. |
| **Asynchronous Saves** | High scores are written to disk on a background daemon thread, so disk I/O never stalls gameplay. |
| **Cinematic Boot Sequence** | Every session opens with a 1.5-second animated splash screen bearing the atharvajshinde studios mark. |

<hr>

## Controls

| Action | Primary Keys | Alternate Keys |
| :--- | :---: | :---: |
| Move | <kbd>W</kbd> <kbd>A</kbd> <kbd>S</kbd> <kbd>D</kbd> | <kbd>↑</kbd> <kbd>↓</kbd> <kbd>←</kbd> <kbd>→</kbd> |
| Dash | <kbd>Q</kbd> | <kbd>L-Shift</kbd> |
| Start / Select | <kbd>Space</kbd> | <kbd>Enter</kbd> |
| Pause / Resume | <kbd>P</kbd> | — |
| Restart *(on Game Over)* | <kbd>R</kbd> | — |
| Quit | <kbd>ESC</kbd> | — |

<hr>

## How to Run the Game

All builds are available from the **Releases** section of this repository. Pick whichever method suits your setup:

| Method | Instructions |
| :--- | :--- |
| **Windows Installer** *(recommended)* | Download `Snake-Windows-Installer.exe` from Releases and run it. Setup is automatic and a shortcut is created for you. |
| **Native Executable** *(Linux / macOS)* | Extract the downloaded folder and double-click the **Snake** application. No Java installation needed. |
| **Executable JAR** | With Java installed, open a terminal in the project folder and run `java -jar libs/Snake.jar`. |
| **Compile from Source** | From the project root, run `javac -d bin src/*.java`, then launch with `java -cp bin SnakeApp`. |

<hr>

## The Game Manual

<details>
<summary><b>Click to reveal advanced mechanics and game juice</b></summary>
<br>

*Spoiler warning — it's highly recommended to play the game blind first to experience the surprises for yourself. Well, there aren't many... but hey its there for a reason! :)*

- **Dynamic Scaling & Boards** — Adjust the screen zoom from 1.0x to 3.0x and choose between Small (16×16), Medium (24×24), and Large (32×32) grids. Larger boards raise the snake's base speed.
- **The Dash System** — Hold the dash key to burn Stamina and move 3x faster, letting you beat your own reflexes to the next apple. Stamina regenerates over time.
- **The Combo System** — Eating apples in quick succession builds an escalating combo multiplier (x2, x3, x4), tracked by a draining HUD combo bar.
- **The Leveling System** — Every 5 apples eaten levels the game up, permanently increasing the snake's speed.

</details>

<hr>

<div align="center">

<sub>Crafted with <3 by <b>atharvajshinde</b></sub>

</div>
