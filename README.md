# 🧠 2048 AI – Advanced Autonomous 2048 Solver in Java

Welcome to my 2048 AI, an advanced autonomous Java-based AI agent that plays the popular game [2048](https://play2048.co/) with remarkable intelligence.

This project is not a basic move-bot. It uses cutting-edge AI search techniques and board evaluation strategies inspired by real-world decision theory and game AI. The AI can reach the **2048 tile** and sometimes achieve much higher (4096, 8192), depending on randomness and depth constraints.

---

## 📌 Features

- Fully autonomous gameplay with AWT GUI
- Smart **iterative deepening Expectimax** search algorithm
- Multi-factor **heuristic evaluation function**:
  - Empty tiles
  - Smoothness
  - Monotonicity
  - Merge potential
  - Gradient positioning
  - Corner lock (bottom-left bias)
  - Penalties for tile instability
- **Zobrist hashing** for transposition table caching
- Parallel Expectimax evaluations using `ExecutorService`
- ⏱Time-limited AI moves (default: 5000 ms)
- Clean AWT rendering of the 2048 game board

--- 
## 🔧 How the AI Works

### 🧠 1. Expectimax Algorithm

The core of the AI is the **Expectimax search**, a variation of Minimax used when outcomes are probabilistic:

- **Player Nodes** (AI's turn): Choose the move with the highest expected value.
- **Chance Nodes** (random tile spawn): Average out the outcome of all possible spawns weighted by their probabilities (0.9 for 2, 0.1 for 4).

#### Iterative Deepening

Expectimax is run using **iterative deepening**:
- Searches start at depth 1 and increase up to 9 depending on board state and time.
- This allows:
  - Best-move discovery before timeout
  - Flexible depth based on tile sparsity

#### Time Management

Each move is constrained to **5000ms** (`TIME_LIMIT_MS`). If time runs out during Expectimax:
- The current best move is selected.
- A fallback to evaluation is used to prevent stalling.

---

### 📏 2. Heuristic Evaluation Function

When Expectimax reaches its depth limit or hits a timeout, a **custom evaluation function** scores the board.

This function combines multiple weighted features:

#### 📐 2.1. Empty Cells

```java
EMPTY_WEIGHT * countEmptyCells(board)
````

* Encourages keeping more options open.
* AI avoids early fill-up.

#### ➿ 2.2. Smoothness

```java
SMOOTH_WEIGHT * smoothness(board, logBoard)
```

* Penalizes adjacent tiles with sharp differences.
* Rewards groupings of similar-valued tiles for merging potential.

#### 📈 2.3. Monotonicity

```java
MONO_WEIGHT * monotonicity(board, logBoard)
```

* Encourages tiles to increase/decrease consistently along rows or columns.
* Helps create merge-friendly lines.

#### 📊 2.4. Merge Potential

```java
MERGE_POTENTIAL_WEIGHT * mergePotential(board)
```

* Rewards boards where tiles can soon be merged.
* Helps avoid wasteful spreads.

#### 📌 2.5. Corner Lock Bonus

```java
cornerLockBonus(board)
```

* Adds a **huge bonus** if the **max tile is in the bottom-left** and tiles around it decrease as expected.
* Prevents losing the high tile to the center.

#### 💠 2.6. Gradient Score

```java
GRADIENT_WEIGHT * gradientScore(board)
```

The board is scored with a **gradient matrix**:

```
[15, 14, 13, 12]
[ 8,  9, 10, 11]
[ 7,  6,  5,  4]
[ 0,  1,  2,  3]
```

* Encourages placing high tiles in the bottom-left corner.
* This biases tile movement to keep "heavy" tiles locked.

#### 🚫 2.7. Instability Penalty

```java
-instabilityPenalty(board)
```

* Penalizes tiles that are positioned higher than their neighbors.
* Prevents forming a fragile structure.

#### 📉 2.8. Big Tile Distance Penalty

```java
-bigTileDistancePenalty(board)
```

* Measures Manhattan distance of large tiles (≥64) from the bottom-left corner.
* Encourages clustering.

#### 🧮 2.9. Max Tile Log Scaling

```java
MAX_WEIGHT * log2(maxTile)
```

* Rewards high tiles.
* Uses logarithmic scaling for diminishing returns.

#### 📏 2.10. Directional Monotonicity

```java
directionalMonotonicity(board)
```

* Evaluates monotonic trends left-right, right-left, top-bottom, bottom-top.
* Selects the strongest.

---

### 🧪 3. Zobrist Hashing + Transposition Table

To **avoid redundant board evaluations**, we:

* Use a precomputed `ZOBRIST_TABLE[16][12]` for 4x4 tiles up to 4096.
* XOR encode each tile based on its position and value.
* Use this as a **cache key** in `transpositionTable`.

Result:

* Speeds up Expectimax massively.
* Enables reuse of evaluation results for the same board encountered through different move sequences.

---

### 🧵 4. Parallel Evaluation

During the **AI’s move selection**, we:

* Launch a thread pool using `ExecutorService`
* Evaluate child boards in parallel
* Combine their results into the best-move decision

---

## 🧪 AI Design Insights

The AI is heavily optimized for:

* **Control**: Keeping tiles locked in one corner
* **Safety**: Maximizing future flexibility (empty tiles)
* **Power**: Growing the largest tile as efficiently as possible
* **Stability**: Avoiding scattered large tiles

It mimics the way a strong human player strategizes:

* One corner to store the big tile
* Push smaller tiles toward it
* Avoid random scattered merges

---

## 🖼️ GUI

* Drawn using Java AWT
* Each tile has a distinct background color
* Displays "Game Over!" if no moves remain
* Displays "You Win!" when 2048 is reached

---

## 📦 Getting Started

### 🔧 Requirements

* Java 8+
* Any IDE or terminal

### 🧪 Run the Game

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

Pull requests are welcome. For major changes, open an issue first to discuss what you would like to change or improve.
