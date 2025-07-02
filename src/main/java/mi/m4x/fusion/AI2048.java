package mi.m4x.fusion;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class AI2048 extends Frame implements Runnable {
    private static final int SIZE = 4;
    private int[][] board = new int[SIZE][SIZE];
    private Random random = new Random();
    private boolean moved;
    private boolean gameOver;
    private Thread aiThread;
    double EMPTY_WEIGHT = 300.0;
    double MONO_WEIGHT = 100.0;
    double SMOOTH_WEIGHT = 3.0;
    double CORNER_WEIGHT = 2000.0;
    double MAX_WEIGHT = 2.0;
    private static final int MAX_LOG_TILE = 15;
    private static final long[][] ZOBRIST_TABLE = new long[SIZE * SIZE][MAX_LOG_TILE];
    private boolean gameWon;
    private Image offscreenImage;
    private Graphics offscreenGraphics;

    // Cache for transposition table
    private Map<Long, Double> transpositionTable = new HashMap<>();

    // Expectimax with caching and iterative deepening timeout support
    private long startTime;

    // Time limit per AI move in milliseconds
    private static final long TIME_LIMIT_MS = 5000;

    public AI2048() {
        super("2048 AI");
        setSize(400, 450);
        setResizable(true);
        setVisible(true);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                if (aiThread != null) aiThread.interrupt();
                System.exit(0);
            }
        });

        resetBoard();
        aiThread = new Thread(this);
        aiThread.start();
    }

    public void resetBoard() {
        for (int i = 0; i < SIZE; i++)
            Arrays.fill(board[i], 0);
        addRandomTile();
        addRandomTile();
        gameWon = false;
        gameOver = false;
        repaint();
    }

    private void addRandomTile() {
        List<Point> emptyCells = new ArrayList<>();
        for (int i = 0; i < SIZE; i++)
            for (int j = 0; j < SIZE; j++)
                if (board[i][j] == 0)
                    emptyCells.add(new Point(i, j));
        if (!emptyCells.isEmpty()) {
            Point p = emptyCells.get(random.nextInt(emptyCells.size()));
            board[p.x][p.y] = random.nextDouble() < 0.9 ? 2 : 4;
        }
    }

    private List<int[]> generateOrderedMoves(int[][] boardState) {
        List<int[]> moves = new ArrayList<>();
        for (int dir = 0; dir < 4; dir++) {
            int[][] newBoard = copyBoard(boardState);
            if (moveDirection(newBoard, dir)) {
                double score = evaluateBoard(newBoard);
                moves.add(new int[]{dir, (int)(score * 1000)}); // scale score for sorting
            }
        }
        moves.sort((a, b) -> Integer.compare(b[1], a[1])); // descending order
        return moves;
    }


    private boolean canMove() {
        for (int i = 0; i < SIZE; i++)
            for (int j = 0; j < SIZE; j++) {
                if (board[i][j] == 0) return true;
                if (i < SIZE - 1 && board[i][j] == board[i + 1][j]) return true;
                if (j < SIZE - 1 && board[i][j] == board[i][j + 1]) return true;
            }
        return false;
    }

    private boolean moveLeft() {
        moved = false;
        for (int i = 0; i < SIZE; i++) {
            int[] row = board[i];
            int[] compressed = new int[SIZE];
            int index = 0;

            for (int j = 0; j < SIZE; j++) {
                if (row[j] != 0) {
                    compressed[index++] = row[j];
                }
            }

            for (int j = 0; j < index - 1; j++) {
                if (compressed[j] == compressed[j + 1] && compressed[j] != 0) {
                    compressed[j] *= 2;
                    compressed[j + 1] = 0;
                    j++;
                }
            }

            int[] newRow = new int[SIZE];
            int newIndex = 0;
            for (int j = 0; j < SIZE; j++) {
                if (compressed[j] != 0) {
                    newRow[newIndex++] = compressed[j];
                }
            }

            for (int j = 0; j < SIZE; j++) {
                if (row[j] != newRow[j]) {
                    moved = true;
                    row[j] = newRow[j];
                }
            }
        }
        return moved;
    }

    private void rotateClockwise() {
        int[][] newBoard = new int[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++)
            for (int j = 0; j < SIZE; j++)
                newBoard[j][SIZE - 1 - i] = board[i][j];
        board = newBoard;
    }

    private void rotateCounterClockwise() {
        int[][] newBoard = new int[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++)
            for (int j = 0; j < SIZE; j++)
                newBoard[SIZE - 1 - j][i] = board[i][j];
        board = newBoard;
    }

    private boolean moveRight() {
        rotateClockwise();
        rotateClockwise();
        boolean res = moveLeft();
        rotateClockwise();
        rotateClockwise();
        return res;
    }

    private boolean moveUp() {
        rotateCounterClockwise();
        boolean res = moveLeft();
        rotateClockwise();
        return res;
    }

    private boolean moveDown() {
        rotateClockwise();
        boolean res = moveLeft();
        rotateCounterClockwise();
        return res;
    }

    private boolean moveDirection(int[][] b, int dir) {
        switch (dir) {
            case 0: return moveUp(b);
            case 1: return moveDown(b);
            case 2: return moveLeft(b);
            case 3: return moveRight(b);
        }
        return false;
    }

    private boolean moveLeft(int[][] b) {
        boolean movedLocal = false;
        for (int i = 0; i < SIZE; i++) {
            int[] row = b[i];
            int[] compressed = new int[SIZE];
            int index = 0;

            for (int j = 0; j < SIZE; j++) {
                if (row[j] != 0) compressed[index++] = row[j];
            }
            for (int j = 0; j < index - 1; j++) {
                if (compressed[j] == compressed[j + 1] && compressed[j] != 0) {
                    compressed[j] *= 2;
                    compressed[j + 1] = 0;
                    j++;
                }
            }
            int[] newRow = new int[SIZE];
            int newIndex = 0;
            for (int j = 0; j < SIZE; j++) {
                if (compressed[j] != 0) newRow[newIndex++] = compressed[j];
            }
            for (int j = 0; j < SIZE; j++) {
                if (row[j] != newRow[j]) {
                    movedLocal = true;
                    row[j] = newRow[j];
                }
            }
        }
        return movedLocal;
    }

    private void rotateClockwise(int[][] b) {
        int[][] newBoard = new int[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++)
            for (int j = 0; j < SIZE; j++)
                newBoard[j][SIZE - 1 - i] = b[i][j];
        for (int i = 0; i < SIZE; i++)
            System.arraycopy(newBoard[i], 0, b[i], 0, SIZE);
    }

    private void rotateCounterClockwise(int[][] b) {
        int[][] newBoard = new int[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++)
            for (int j = 0; j < SIZE; j++)
                newBoard[SIZE - 1 - j][i] = b[i][j];
        for (int i = 0; i < SIZE; i++)
            System.arraycopy(newBoard[i], 0, b[i], 0, SIZE);
    }

    private boolean moveRight(int[][] b) {
        rotateClockwise(b);
        rotateClockwise(b);
        boolean res = moveLeft(b);
        rotateClockwise(b);
        rotateClockwise(b);
        return res;
    }

    private boolean moveUp(int[][] b) {
        rotateCounterClockwise(b);
        boolean res = moveLeft(b);
        rotateClockwise(b);
        return res;
    }

    private boolean moveDown(int[][] b) {
        rotateClockwise(b);
        boolean res = moveLeft(b);
        rotateCounterClockwise(b);
        return res;
    }

    private int[][] copyBoard(int[][] src) {
        int[][] copy = new int[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++)
            System.arraycopy(src[i], 0, copy[i], 0, SIZE);
        return copy;
    }

    private int countEmptyCells(int[][] b) {
        int count = 0;
        for (int[] row : b)
            for (int v : row)
                if (v == 0) count++;
        return count;
    }

    private int maxTile(int[][] b) {
        int max = 0;
        for (int[] row : b)
            for (int v : row)
                if (v > max) max = v;
        return max;
    }

    private boolean maxTileInCorner(int[][] b) {
        int max = maxTile(b);
        return b[0][0] == max || b[0][SIZE - 1] == max || b[SIZE - 1][0] == max || b[SIZE - 1][SIZE - 1] == max;
    }

    private double smoothness(int[][] b, int[][] logBoard) {
        double smooth = 0;
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (b[i][j] == 0) continue;
                int value = logBoard[i][j];
                if (j + 1 < SIZE && b[i][j + 1] != 0) {
                    int neighborValue = logBoard[i][j + 1];
                    smooth -= Math.abs(value - neighborValue);
                }
                if (i + 1 < SIZE && b[i + 1][j] != 0) {
                    int neighborValue = logBoard[i + 1][j];
                    smooth -= Math.abs(value - neighborValue);
                }
            }
        }
        return smooth;
    }

    private double monotonicity(int[][] b, int[][] logBoard) {
        double score = 0.0;

        for (int i = SIZE - 1; i >= 0; i--) {
            for (int j = 0; j < SIZE - 1; j++) {
                int current = logBoard[i][j];
                int next = logBoard[i][j + 1];
                if (current >= next) score += next - current;
                else score -= (next - current);
            }
        }

        for (int j = 0; j < SIZE; j++) {
            for (int i = SIZE - 1; i > 0; i--) {
                int current = logBoard[i][j];
                int next = logBoard[i - 1][j];
                if (current >= next) score += next - current;
                else score -= (next - current);
            }
        }

        return score;
    }

    private double evaluateBoard(int[][] b) {
        int[][] logBoard = computeLogBoard(b);
        int max = maxTile(b);
        double logMax = max == 0 ? 0 : Math.log(max) / Math.log(2);

        return EMPTY_WEIGHT * countEmptyCells(b)
                + SMOOTH_WEIGHT * smoothness(b, logBoard)
                + MONO_WEIGHT * monotonicity(b, logBoard)
                + MAX_WEIGHT * logMax
                + CORNER_WEIGHT * (maxTileInCorner(b) ? 1 : -1);
    }

    private boolean canMove(int[][] b) {
        for (int i = 0; i < SIZE; i++)
            for (int j = 0; j < SIZE; j++) {
                if (b[i][j] == 0) return true;
                if (i < SIZE - 1 && b[i][j] == b[i + 1][j]) return true;
                if (j < SIZE - 1 && b[i][j] == b[i][j + 1]) return true;
            }
        return false;
    }

    private long boardToLong(int[][] b) {
        long h = 0;
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                int val = b[i][j];
                if (val == 0) continue;
                int log = log2(val);
                h ^= ZOBRIST_TABLE[i * SIZE + j][log];
            }
        }
        return h;
    }

    private double expectimax(int[][] boardState, int depth, boolean playerTurn) {
        if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) {
            // Timeout: fallback evaluation
            return evaluateBoard(boardState);
        }
        if (depth == 0) return evaluateBoard(boardState);
        if (!canMove(boardState)) return -1000000;

        long hash = boardToLong(boardState);
        if (transpositionTable.containsKey(hash)) {
            return transpositionTable.get(hash);
        }

        double result;
        if (playerTurn) {
            double maxScore = Double.NEGATIVE_INFINITY;

            List<int[]> orderedMoves = generateOrderedMoves(boardState);
            for (int[] movePair : orderedMoves) {
                int dir = movePair[0];
                int[][] newBoard = copyBoard(boardState);
                if (!moveDirection(newBoard, dir)) continue;

                double score = expectimax(newBoard, depth - 1, false);
                if (score > maxScore) {
                    maxScore = score;
                }

                // Optional early pruning: if very strong score found, stop early
                if (score > 100000) break;

                // Timeout check (in case inner call ran long)
                if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) break;
            }

            result = maxScore == Double.NEGATIVE_INFINITY ? -1000000 : maxScore;
        } else {
            List<Point> empties = new ArrayList<>();
            for (int i = 0; i < SIZE; i++)
                for (int j = 0; j < SIZE; j++)
                    if (boardState[i][j] == 0) empties.add(new Point(i, j));
            if (empties.isEmpty()) return evaluateBoard(boardState);

            double expectedScore = 0;
            double prob2 = 0.9;
            double prob4 = 0.1;
            double totalWeight = 0;
            Map<Point, Double> weights = new HashMap<>();
            for (Point pnt : empties) {
                double w = (pnt.x == 0 || pnt.x == 3 || pnt.y == 0 || pnt.y == 3) ? 1.5 : 1.0;
                weights.put(pnt, w);
                totalWeight += w;
            }

            // *** Sample empties to limit branching ***
            List<Point> sampledEmpties = sampleEmptyCells(empties, 3);  // limit to 3 samples

            for (Point pnt : sampledEmpties) {
                double w = weights.get(pnt) / totalWeight;

                int[][] boardWith2 = copyBoard(boardState);
                boardWith2[pnt.x][pnt.y] = 2;
                expectedScore += prob2 * w * expectimax(boardWith2, depth - 1, true);

                int[][] boardWith4 = copyBoard(boardState);
                boardWith4[pnt.x][pnt.y] = 4;
                expectedScore += prob4 * w * expectimax(boardWith4, depth - 1, true);
            }
            result = expectedScore;
        }

        transpositionTable.put(hash, result);
        return result;
    }

    private List<Point> sampleEmptyCells(List<Point> empties, int maxSamples) {
        if (empties.size() <= maxSamples) return empties;
        List<Point> copy = new ArrayList<>(empties);
        Collections.shuffle(copy, random);
        return copy.subList(0, maxSamples);
    }

    private Point findMaxTilePosition(int[][] b) {
        int max = 0;
        Point pos = new Point(0, 0);
        for (int i = 0; i < SIZE; i++)
            for (int j = 0; j < SIZE; j++)
                if (b[i][j] > max) {
                    max = b[i][j];
                    pos = new Point(i, j);
                }
        return pos;
    }

    private boolean isCorner(Point p) {
        return (p.x == 0 && p.y == 0) ||
                (p.x == 0 && p.y == SIZE - 1) ||
                (p.x == SIZE - 1 && p.y == 0) ||
                (p.x == SIZE - 1 && p.y == SIZE - 1);
    }

    private int iterativeDeepeningBestMove() {
        int bestDir = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        int emptyTiles = countEmptyCells(board);
        int maxDepth = emptyTiles >= 6 ? 5 : emptyTiles >= 4 ? 7 : 9;
        startTime = System.currentTimeMillis();

        Point maxTilePos = findMaxTilePosition(board);

        for (int depth = 1; depth <= maxDepth; depth++) {
            transpositionTable.clear();
            int currentBestDir = -1;
            double currentBestScore = Double.NEGATIVE_INFINITY;

            int[] moveOrder = {2, 1, 3, 0}; // left > down > right > up
            for (int dir : moveOrder) {
                int[][] newBoard = copyBoard(board);
                if (!moveDirection(newBoard, dir)) continue;

                Point newMaxPos = findMaxTilePosition(newBoard);

                if (isCorner(maxTilePos) && !isCorner(newMaxPos)) {
                    boolean alternativeExists = false;
                    for (int altDir = 0; altDir < 4; altDir++) {
                        if (altDir == dir) continue;
                        int[][] altBoard = copyBoard(board);
                        if (moveDirection(altBoard, altDir)) {
                            Point altMaxPos = findMaxTilePosition(altBoard);
                            if (isCorner(altMaxPos)) {
                                alternativeExists = true;
                                break;
                            }
                        }
                    }
                    if (alternativeExists) continue;
                }

                double score = expectimax(newBoard, depth - 1, false);

                if (score > currentBestScore) {
                    currentBestScore = score;
                    currentBestDir = dir;
                }

                if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) {
                    // Time limit reached, stop deeper search
                    break;
                }
            }

            if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) {
                // Stop iterative deepening on timeout
                break;
            }

            if (currentBestDir != -1) {
                bestDir = currentBestDir;
                bestScore = currentBestScore;
            }
        }
        return bestDir;
    }

    private int log2(int val) {
        return val == 0 ? 0 : (int)(Math.log(val) / Math.log(2));
    }

    private int[][] computeLogBoard(int[][] b) {
        int[][] logBoard = new int[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                logBoard[i][j] = log2(b[i][j]);
            }
        }
        return logBoard;
    }


    private void performAIMove() {
        int dir = iterativeDeepeningBestMove();
        if (dir == -1) {
            gameOver = true;
            return;
        }

        boolean didMove = false;
        switch (dir) {
            case 0: didMove = moveUp(); break;
            case 1: didMove = moveDown(); break;
            case 2: didMove = moveLeft(); break;
            case 3: didMove = moveRight(); break;
        }

        if (didMove) {
            addRandomTile();

            if (maxTile(board) >= 2048) {
                gameWon = true;
                return; // stop AI after winning
            }

            repaint();
            if (!canMove()) {
                gameOver = true;
            }
        } else {
            gameOver = true;
        }
    }

    @Override
    public void paint(Graphics g) {
        if (offscreenImage == null) {
            offscreenImage = createImage(getWidth(), getHeight());
            offscreenGraphics = offscreenImage.getGraphics();
        }

        // Clear offscreen
        offscreenGraphics.setColor(new Color(0xbbada0));
        offscreenGraphics.fillRect(0, 0, getWidth(), getHeight());

        int tileSize = 80;
        int padding = 15;
        Font font = new Font("Arial", Font.BOLD, 36);
        offscreenGraphics.setFont(font);

        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                int value = board[i][j];
                int x = padding + j * (tileSize + padding);
                int y = padding + i * (tileSize + padding) + 30;
                drawTile(offscreenGraphics, value, x, y, tileSize, tileSize);
            }
        }

        if (gameOver || gameWon) {
            offscreenGraphics.setColor(new Color(255, 255, 255, 180));
            offscreenGraphics.fillRect(0, 0, getWidth(), getHeight());
            offscreenGraphics.setColor(gameWon ? Color.GREEN : Color.RED);
            offscreenGraphics.setFont(new Font("Arial", Font.BOLD, 48));
            offscreenGraphics.drawString(gameWon ? "You Win!" : "Game Over!", 100, getHeight() / 2);
        }

        // Draw offscreen buffer to screen in one operation
        g.drawImage(offscreenImage, 0, 0, this);
    }

    private void drawTile(Graphics g, int value, int x, int y, int w, int h) {
        Color bg;
        Color fg;
        switch (value) {
            case 0: bg = new Color(0xcdc1b4); fg = Color.BLACK; break;
            case 2: bg = new Color(0xeee4da); fg = new Color(0x776e65); break;
            case 4: bg = new Color(0xede0c8); fg = new Color(0x776e65); break;
            case 8: bg = new Color(0xf2b179); fg = Color.WHITE; break;
            case 16: bg = new Color(0xf59563); fg = Color.WHITE; break;
            case 32: bg = new Color(0xf67c5f); fg = Color.WHITE; break;
            case 64: bg = new Color(0xf65e3b); fg = Color.WHITE; break;
            case 128: bg = new Color(0xedcf72); fg = Color.WHITE; break;
            case 256: bg = new Color(0xedcc61); fg = Color.WHITE; break;
            case 512: bg = new Color(0xedc850); fg = Color.WHITE; break;
            case 1024: bg = new Color(0xedc53f); fg = Color.WHITE; break;
            case 2048: bg = new Color(0xedc22e); fg = Color.WHITE; break;
            case 4096, 16384, 8192: bg = new Color(0x3c3a32); fg = Color.WHITE; break;
            default: bg = Color.BLACK; fg = Color.WHITE; break;
        }

        g.setColor(bg);
        g.fillRoundRect(x, y, w, h, 15, 15);

        if (value != 0) {
            g.setColor(fg);
            String s = String.valueOf(value);
            FontMetrics fm = g.getFontMetrics();
            int sw = fm.stringWidth(s);
            int sh = fm.getAscent();
            g.drawString(s, x + (w - sw) / 2, y + (h + sh) / 2 - 5);
        }
    }

    @Override
    public void run() {
        while (!gameOver && !gameWon) {
            performAIMove();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }
        }
        repaint();
    }


    @Override
    public void update(Graphics g) {
        // Override update to avoid clearing screen before paint,
        // which causes flicker
        paint(g);
    }

    static {
        Random rand = new Random();
        for (int i = 0; i < SIZE * SIZE; i++) {
            for (int j = 0; j < MAX_LOG_TILE; j++) {
                ZOBRIST_TABLE[i][j] = rand.nextLong();
            }
        }
    }


    public static void main(String[] args) {
        new AI2048();
    }
}