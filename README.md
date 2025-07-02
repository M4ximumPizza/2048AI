# 🧠 AI2048 – Advanced Autonomous 2048 Solver in Java

An intelligent, autonomous Java AI that plays the popular puzzle game [2048](https://play2048.co/) using advanced AI techniques. The AI can reach the **2048 tile** reliably and sometimes even higher tiles depending on randomness and search depth.

---

## 📌 Features

* **Fully autonomous gameplay** with simple AWT GUI
* Implements **Expectimax search** with **iterative deepening** and **time limits**
* **Heuristic evaluation function** combining:

  * Empty tile count
  * Smoothness of tile values
  * Monotonicity of rows and columns
  * Corner bias to keep max tile locked in a corner
  * Logarithmic scaling for max tile values
* **Zobrist hashing** for transposition table caching to avoid redundant evaluations
* Branching pruning by **sampling empty tiles** during chance nodes
* **AI thread cleanly stops on win or game over**, preventing freezes
* Double buffering with offscreen graphics for flicker-free rendering
* Clear “You Win!” and “Game Over!” messages on the GUI

---

## 🔧 How It Works

### 1. Expectimax Algorithm with Iterative Deepening

The core AI uses **Expectimax search**, which handles the probabilistic nature of tile spawns (new 2 or 4 tiles).

* **Player nodes** represent the AI’s move choices.
* **Chance nodes** model random tile spawns with weighted probabilities (90% for 2, 10% for 4).

**Iterative deepening** searches from depth 1 up to a max depth (adjusted dynamically based on empties), ensuring:

* Early move decisions within the time limit
* Deeper searches on simpler boards

### 2. Heuristic Board Evaluation

When the search reaches its depth limit or times out, the board is scored by a heuristic that includes:

* **Empty tiles:** More empty cells means more mobility (weighted positively).
* **Smoothness:** Penalizes large value differences between adjacent tiles.
* **Monotonicity:** Rewards rows and columns that are consistently increasing or decreasing.
* **Max tile position:** Bonus if the max tile is in any corner (locking strategy).
* **Logarithmic max tile value:** Encourages growing bigger tiles with diminishing returns.

### 3. Zobrist Hashing & Transposition Table

Each board state is hashed using a precomputed Zobrist table for fast lookups in a transposition table, speeding up the search by caching evaluated states.

### 4. Time Management & Sampling

* Each AI move has a **5-second time limit**.
* During chance nodes, only up to **3 empty tiles are sampled** to reduce branching and improve speed.

### 5. Rendering & UI

* Tiles are colored based on value, drawn with anti-flicker double buffering.
* Displays "You Win!" when 2048 or above is reached, and stops AI moves.
* Displays "Game Over!" if no moves remain.

---

## 🧪 Running the AI

### Requirements

* Java 8 or later
* No external dependencies

### Build and Run

```bash
javac mi/m4x/fusion/AI2048.java
java mi.m4x.fusion.AI2048
```

---

## 📁 File Structure

```
mi/
 └── m4x/
      └── fusion/
           └── AI2048.java
```

---

## 🤝 Contributing

Feel free to fork and improve the AI! Pull requests are welcome, especially for:

* Improving heuristics
* Optimizing search performance
* Adding new GUI features

---