import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * NeonTicTacToeFull.java
 * Full-screen neon Tic-Tac-Toe with PvP and PvC (Impossible AI minmax + alpha-beta).
 *
 * Save file as NeonTicTacToeFull.java and run in IntelliJ.
 */
public class NeonTicTacToeFull extends JFrame {

    // UI components
    private final NeonCell[] cells = new NeonCell[9];
    private final JLabel titleLabel = new JLabel("Neon Tic Tac Toe", SwingConstants.CENTER);
    private final JButton pvpBtn = new JButton("Player vs Player");
    private final JButton pvcBtn = new JButton("Player vs Computer");
    private final JButton restartBtn = new JButton("Restart Game");
    private final JButton exitBtn = new JButton("Exit");
    private final JLabel turnLabel = new JLabel("Current Turn: X", SwingConstants.CENTER);

    // Game state
    private final String[] board = new String[9]; // "", "X", "O"
    private String currentPlayer = "X";
    private boolean gameActive = true;
    private boolean pvcMode = false;

    // Score
    private int xWins = 0, oWins = 0, draws = 0;
    private final JLabel scoreLabel = new JLabel("X: 0   O: 0   Draws: 0", SwingConstants.CENTER);

    // AI
    private final ScheduledExecutorService aiExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Random rand = new Random();

    // Visuals
    private Color bgColor = Color.decode("#000000");
    private Color neonAccent = Color.decode("#00fff7");
    private Color neonX = new Color(255, 0, 64);
    private Color neonO = new Color(0, 255, 255);
    private Color cellBg = Color.decode("#111111");

    // win patterns
    private final int[][] winPatterns = {
            {0,1,2},{3,4,5},{6,7,8},
            {0,3,6},{1,4,7},{2,5,8},
            {0,4,8},{2,4,6}
    };

    // animation timers map
    private final Map<Integer, javax.swing.Timer> pulseTimers = new HashMap<>();

    // particles for win effect
    private final List<Particle> particles = Collections.synchronizedList(new ArrayList<>());
    private final javax.swing.Timer repaintTimer;

    public NeonTicTacToeFull() {
        setTitle("Neon Tic Tac Toe - Demo");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH); // maximize to fullscreen
        // keep window decoration so Exit works reliably
        setLayout(null);
        getContentPane().setBackground(bgColor);

        // initialize board strings
        Arrays.fill(board, "");

        initUI();

        // Repaint timer for particle animations and smooth visuals
        repaintTimer = new javax.swing.Timer(33, e -> {
            updateParticles();
            repaint();
        });
        repaintTimer.start();

        resetGame();
        setVisible(true);
    }

    private void initUI() {
        int w = Toolkit.getDefaultToolkit().getScreenSize().width;

        // Title
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 48));
        titleLabel.setForeground(neonAccent);
        titleLabel.setBounds((w-700)/2, 20, 700, 70);
        add(titleLabel);

        // Buttons
        pvpBtn.setBounds((w-700)/2, 110, 320, 48);
        pvcBtn.setBounds((w-700)/2 + 360, 110, 320, 48);
        styleControl(pvpBtn);
        styleControl(pvcBtn);
        add(pvpBtn);
        add(pvcBtn);

        // Grid panel
        JPanel gridHolder = new JPanel();
        gridHolder.setOpaque(false);
        int gridSize = 520;
        gridHolder.setBounds((w-gridSize)/2, 190, gridSize, gridSize);
        gridHolder.setLayout(new GridLayout(3,3,20,20));
        add(gridHolder);

        for (int i = 0; i < 9; i++) {
            NeonCell c = new NeonCell(i);
            cells[i] = c;
            gridHolder.add(c);
        }

        // Turn, score, restart, exit
        turnLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        turnLabel.setForeground(Color.GREEN);
        turnLabel.setBounds((w-400)/2, 740, 400, 30);
        add(turnLabel);

        scoreLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        scoreLabel.setForeground(neonAccent);
        scoreLabel.setBounds((w-400)/2, 770, 400, 24);
        add(scoreLabel);

        restartBtn.setBounds((w-300)/2, 820, 160, 44);
        exitBtn.setBounds((w-300)/2 + 180, 820, 120, 44);
        styleControl(restartBtn);
        styleControl(exitBtn);
        add(restartBtn);
        add(exitBtn);

        // Actions
        pvpBtn.addActionListener(e -> {
            pvcMode = false;
            resetGame();
            clickBeep();
        });
        pvcBtn.addActionListener(e -> {
            pvcMode = true;
            resetGame();
            clickBeep();
        });
        restartBtn.addActionListener(e -> {
            animateRestart();
            clickBeep();
        });
        exitBtn.addActionListener(e -> {
            aiExecutor.shutdownNow();
            repaintTimer.stop();
            dispose();
            System.exit(0);
        });
    }

    private void styleControl(AbstractButton b) {
        b.setFocusPainted(false);
        b.setForeground(neonAccent);
        b.setBackground(bgColor);
        b.setFont(new Font("Segoe UI", Font.BOLD, 16));
        b.setBorder(new LineBorder(neonAccent, 2, true));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                b.setBackground(new Color(10, 10, 10));
            }
            public void mouseExited(MouseEvent e) {
                b.setBackground(bgColor);
            }
        });
    }

    private void resetGame() {
        Arrays.fill(board, "");
        currentPlayer = "X";
        gameActive = true;
        turnLabel.setText("Current Turn: " + currentPlayer);
        stopAllPulses();
        particles.clear();

        for (int i = 0; i < 9; i++) {
            cells[i].setState("");
            cells[i].setEnabled(true);
            cells[i].setBackground(cellBg);
            cells[i].setBorderColor(neonAccent, 4);
        }
    }

    private void animateRestart() {
        javax.swing.Timer t = new javax.swing.Timer(80, null);
        final int[] step = {0};
        t.addActionListener(e -> {
            step[0]++;
            if (step[0] % 2 == 0) {
                getContentPane().setBackground(new Color(8,8,8));
                for (NeonCell c : cells) c.setBackground(new Color(18,18,18));
            } else {
                getContentPane().setBackground(bgColor);
                for (NeonCell c : cells) c.setBackground(cellBg);
            }
            if (step[0] >= 6) {
                t.stop();
                resetGame();
            }
        });
        t.start();
    }

    private void makeMove(int index, String player) {
        if (!gameActive) return;
        if (!board[index].equals("")) return;

        board[index] = player;
        cells[index].setState(player);
        cells[index].setEnabled(false);
        startPulse(index);

        // play click
        clickBeep();

        if (checkWin(player)) {
            gameActive = false;
            if ("X".equals(player)) xWins++; else oWins++;
            updateScore();
            turnLabel.setText(player + " Wins!");
            winParticles(player);
            animateWinHighlight(player);
            victoryBeep();
            return;
        }

        if (checkDraw()) {
            gameActive = false;
            draws++;
            updateScore();
            turnLabel.setText("It's a Draw!");
            animateDraw();
            clickBeep();
            return;
        }

        currentPlayer = currentPlayer.equals("X") ? "O" : "X";
        turnLabel.setText("Current Turn: " + currentPlayer);

        if (pvcMode && "O".equals(currentPlayer) && gameActive) {
            turnLabel.setText("Computer thinking...");
            scheduleAiMove();
        }
    }

    private void scheduleAiMove() {
        aiExecutor.schedule(() -> {
            int move = minimaxBestMove("O", "X");
            if (move == -1) move = fallbackMove();
            final int m = move;
            SwingUtilities.invokeLater(() -> makeMove(m, "O"));
        }, 420, TimeUnit.MILLISECONDS);
    }

    private int fallbackMove() {
        for (int i=0;i<9;i++) if (board[i].equals("")) return i;
        return -1;
    }

    // Minimax with alpha-beta
    private int minimaxBestMove(String ai, String human) {
        int bestScore = Integer.MIN_VALUE;
        int bestMove = -1;
        for (int i = 0; i < 9; i++) {
            if (board[i].equals("")) {
                board[i] = ai;
                int score = minimax(0, false, ai, human, Integer.MIN_VALUE, Integer.MAX_VALUE);
                board[i] = "";
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = i;
                }
            }
        }
        return bestMove;
    }

    private int minimax(int depth, boolean isMax, String ai, String human, int alpha, int beta) {
        if (isLineWin(ai)) return 10 - depth;
        if (isLineWin(human)) return depth - 10;
        if (isBoardFull()) return 0;

        if (isMax) {
            int maxEval = Integer.MIN_VALUE;
            for (int i = 0; i < 9; i++) if (board[i].equals("")) {
                board[i] = ai;
                int eval = minimax(depth + 1, false, ai, human, alpha, beta);
                board[i] = "";
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break;
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (int i = 0; i < 9; i++) if (board[i].equals("")) {
                board[i] = human;
                int eval = minimax(depth + 1, true, ai, human, alpha, beta);
                board[i] = "";
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break;
            }
            return minEval;
        }
    }

    private boolean isLineWin(String player) {
        for (int[] p : winPatterns) {
            if (board[p[0]].equals(player) && board[p[1]].equals(player) && board[p[2]].equals(player)) return true;
        }
        return false;
    }

    private boolean isBoardFull() {
        for (String s : board) if (s.equals("")) return false;
        return true;
    }

    // Check win for current board
    private boolean checkWin(String player) {
        return isLineWin(player);
    }

    private boolean checkDraw() {
        return isBoardFull();
    }

    private void animateWinHighlight(String player) {
        for (int[] p : winPatterns) {
            if (board[p[0]].equals(player) && board[p[1]].equals(player) && board[p[2]].equals(player)) {
                // pulsate these three cells
                for (int idx : p) {
                    final int i = idx;
                    javax.swing.Timer t = new javax.swing.Timer(180, null);
                    final int[] step = {0};
                    t.addActionListener(ev -> {
                        step[0]++;
                        if (step[0] % 2 == 0) cells[i].setBackground(brighter(cellBg, player.equals("X") ? neonX : neonO));
                        else cells[i].setBackground(cellBg);
                        if (step[0] >= 12) {
                            t.stop();
                            cells[i].setBackground(cellBg);
                        }
                    });
                    t.start();
                }
                break;
            }
        }
    }

    private void animateDraw() {
        javax.swing.Timer t = new javax.swing.Timer(110, null);
        final int[] step = {0};
        t.addActionListener(e -> {
            step[0]++;
            if (step[0] % 2 == 0) {
                for (NeonCell c : cells) c.setBackground(new Color(40,40,40));
            } else {
                for (NeonCell c : cells) c.setBackground(cellBg);
            }
            if (step[0] >= 8) {
                t.stop();
                for (NeonCell c : cells) c.setBackground(cellBg);
            }
        });
        t.start();
    }

    private Color brighter(Color base, Color glow) {
        return new Color(
                Math.min(255, base.getRed() + glow.getRed()/6),
                Math.min(255, base.getGreen() + glow.getGreen()/6),
                Math.min(255, base.getBlue() + glow.getBlue()/6)
        );
    }

    private void updateScore() {
        scoreLabel.setText(String.format("X: %d   O: %d   Draws: %d", xWins, oWins, draws));
    }

    // Pulsate effect on a cell using javax.swing.Timer
    private void startPulse(int index) {
        if (pulseTimers.containsKey(index)) {
            javax.swing.Timer existing = pulseTimers.get(index);
            if (existing.isRunning()) existing.stop();
        }
        final int[] step = {0};
        javax.swing.Timer t = new javax.swing.Timer(90, null);
        t.addActionListener(e -> {
            step[0]++;
            int thickness = 3 + (step[0] % 6);
            cells[index].setBorderColor(neonAccent, thickness);
            if (step[0] >= 12) {
                t.stop();
                cells[index].setBorderColor(neonAccent, 3);
            }
        });
        pulseTimers.put(index, t);
        t.start();
    }

    private void stopAllPulses() {
        for (javax.swing.Timer t : pulseTimers.values()) if (t.isRunning()) t.stop();
        pulseTimers.clear();
    }

    // Win particle effects
    private void winParticles(String player) {
        Color c = player.equals("X") ? neonX : neonO;
        for (int i=0;i<60;i++) {
            particles.add(new Particle(getWidth()/2, getHeight()/2, rand.nextDouble()*360, c));
        }
    }

    private void updateParticles() {
        synchronized (particles) {
            Iterator<Particle> it = particles.iterator();
            while (it.hasNext()) {
                Particle p = it.next();
                p.update();
                if (p.life <= 0) it.remove();
            }
        }
    }

    // Simple click and victory beeps
    private void clickBeep() {
        Toolkit.getDefaultToolkit().beep();
    }
    private void victoryBeep() {
        Toolkit.getDefaultToolkit().beep();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        Toolkit.getDefaultToolkit().beep();
    }

    // Custom painted cell component
    private class NeonCell extends JComponent {
        private String state = ""; // "", "X", "O"
        private int index;
        private Color borderColor = neonAccent;
        private int borderThickness = 3;

        NeonCell(int idx) {
            this.index = idx;
            setOpaque(true);
            setBackground(cellBg);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createLineBorder(borderColor, borderThickness, true));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (gameActive && state.equals("")) setBorderColor(neonAccent, borderThickness+2);
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    if (gameActive && state.equals("")) setBorderColor(neonAccent, borderThickness);
                }
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!gameActive) return;
                    if (!state.equals("")) return;
                    makeMove(index, currentPlayer);
                }
            });
        }

        void setState(String s) {
            this.state = s;
            repaint();
        }

        void setBorderColor(Color c, int thickness) {
            this.borderColor = c;
            this.borderThickness = thickness;
            setBorder(BorderFactory.createLineBorder(borderColor, borderThickness, true));
            repaint();
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            // background
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // soft inner glow
            for (int i=5;i>=1;i--) {
                int a = (int)(12 + (6-i)*18);
                g2.setColor(new Color(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), Math.min(200,a)));
                g2.fillRoundRect(3-i, 3-i, w-6+(i*2), h-6+(i*2), 18, 18);
            }

            // draw inner panel (dark)
            g2.setColor(getBackground());
            g2.fillRoundRect(6,6,w-12,h-12,14,14);

            // draw symbol
            if ("X".equals(state)) drawNeonX(g2,w,h);
            else if ("O".equals(state)) drawNeonO(g2,w,h);

            g2.dispose();
        }

        private void drawNeonX(Graphics2D g2, int w, int h) {
            int pad = Math.min(w,h)/6;
            // glow layers
            for (int glow = 16; glow >= 4; glow -= 3) {
                g2.setStroke(new BasicStroke(glow, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(neonX.getRed(), neonX.getGreen(), neonX.getBlue(), 30));
                g2.drawLine(pad, pad, w-pad, h-pad);
                g2.drawLine(w-pad, pad, pad, h-pad);
            }
            // main X
            g2.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(neonX);
            g2.drawLine(pad, pad, w-pad, h-pad);
            g2.drawLine(w-pad, pad, pad, h-pad);
        }

        private void drawNeonO(Graphics2D g2,int w,int h) {
            int size = Math.min(w,h) - w/4;
            int x = (w-size)/2;
            int y = (h-size)/2;
            // glow rings
            for (int glow=18; glow>=6; glow-=3) {
                g2.setStroke(new BasicStroke(glow, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(neonO.getRed(), neonO.getGreen(), neonO.getBlue(), 24));
                g2.drawOval(x-(glow/4), y-(glow/4), size+(glow/2), size+(glow/2));
            }
            g2.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(neonO);
            g2.drawOval(x,y,size,size);
        }
    }

    // Particle for win effect
    private class Particle {
        double x, y, vx, vy;
        int life = 80;
        Color color;
        Particle(double x,double y,double angle, Color c) {
            this.x = x; this.y = y;
            double rad = Math.toRadians(angle);
            this.vx = Math.cos(rad) * (2 + rand.nextDouble()*6);
            this.vy = Math.sin(rad) * (2 + rand.nextDouble()*6);
            this.color = c;
        }
        void update() {
            x += vx; y += vy;
            vx *= 0.98; vy *= 0.98;
            life--;
        }
    }

    // Utility: update board cell states (call when painting)
    @Override
    public void paint(Graphics g) {
        super.paint(g);
        // draw particles on top layer
        Graphics2D g2 = (Graphics2D) g.create();
        synchronized (particles) {
            for (Particle p : particles) {
                int alpha = (int) (255.0 * Math.max(0, p.life / 80.0));
                g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), Math.max(0, alpha)));
                g2.fillOval((int)p.x, (int)p.y, 6, 6);
            }
        }
    }

    // Mouse & keyboard handling delegated to components (cells) - no extra listeners here

    // Helper to check winner used by minimax
    private char getWinner(char[] arrBoard) {
        // arrBoard length 9, indexes 0..8
        for (int[] p : winPatterns) {
            String a = String.valueOf(arrBoard[p[0]]);
            String b = String.valueOf(arrBoard[p[1]]);
            String c = String.valueOf(arrBoard[p[2]]);
            if (a.charAt(0) != ' ' && a.equals(b) && b.equals(c)) return a.charAt(0);
        }
        return ' ';
    }

    private char getWinner(String[] sboard) {
        for (int[] p : winPatterns) {
            if (!sboard[p[0]].equals("") && sboard[p[0]].equals(sboard[p[1]]) && sboard[p[1]].equals(sboard[p[2]])) {
                return sboard[p[0]].charAt(0);
            }
        }
        return ' ';
    }

    // Minimax helper works on char[][] version; convert when calling
    private int minimaxBoard(char[] b, int depth, boolean isMax) {
        char w = getWinner(b);
        if (w == 'O') return 10 - depth;
        if (w == 'X') return depth - 10;
        boolean full = true;
        for (char c : b) if (c == ' ') { full = false; break; }
        if (full) return 0;

        if (isMax) {
            int best = Integer.MIN_VALUE;
            for (int i = 0; i < 9; i++) if (b[i] == ' ') {
                b[i] = 'O';
                best = Math.max(best, minimaxBoard(b, depth+1, false));
                b[i] = ' ';
            }
            return best;
        } else {
            int best = Integer.MAX_VALUE;
            for (int i = 0; i < 9; i++) if (b[i] == ' ') {
                b[i] = 'X';
                best = Math.min(best, minimaxBoard(b, depth+1, true));
                b[i] = ' ';
            }
            return best;
        }
    }

    // Minimax wrapper used earlier expects char[][]; below convenient conversion and call
    private int minimax(char[] boardFlat, int depth, boolean isMaximizing) {
        return minimaxBoard(boardFlat, depth, isMaximizing);
    }

    // convert String[] board to char[] flatten
    private char[] flattenBoard(String[] sb) {
        char[] out = new char[9];
        for (int i=0;i<9;i++) out[i] = sb[i].equals("") ? ' ' : sb[i].charAt(0);
        return out;
    }

    // main entry for minimax-based move earlier uses minimaxBestMove above

    // MAIN
    public static void main(String[] args) {
        SwingUtilities.invokeLater(NeonTicTacToeFull::new);
    }
}