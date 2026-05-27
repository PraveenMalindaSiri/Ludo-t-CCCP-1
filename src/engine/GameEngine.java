package engine;

import block.Block;
import board.Board;
import board.Cell;
import config.GameConfig;
import engine.command.*;
import event.IGameEventListener;
import mystery.MysteryManager;
import piece.Piece;
import piece.state.*;
import player.Player;
import rules.*;
import dice.*;

import java.util.ArrayList;
import java.util.List;


/**
 * Has the complete game flow.
 * This will init the game state, determine first player, run  rounds and turns, execute moves.
 */
public class GameEngine {
    private final Board board;
    private final List<Player> players;
    private final TurnManager turnManager;
    private final RuleEngine ruleEngine;
    private final CaptureHandler captureHandler;
    private final BlockHandler blockHandler;
    private final MysteryManager mysteryManager;
    private final IDice dice;
    private final ICoinToss coinToss;
    private final List<IGameEventListener> listeners;
    private final GameConfig config;

    public GameEngine(Board board,
                      List<Player> players,
                      RuleEngine ruleEngine,
                      CaptureHandler captureHandler,
                      BlockHandler blockHandler,
                      MysteryManager mysteryManager,
                      IDice dice,
                      ICoinToss coinToss) {
        this.board = board;
        this.players = new ArrayList<>(players);
        this.turnManager = new TurnManager(this.players);
        this.ruleEngine = ruleEngine;
        this.captureHandler = captureHandler;
        this.blockHandler = blockHandler;
        this.mysteryManager = mysteryManager;
        this.dice = dice;
        this.coinToss = coinToss;
        this.listeners = new ArrayList<>();
        this.config = GameConfig.getInstance();
    }

    public void addEventListener(IGameEventListener listener) {
        listeners.add(listener);
    }

    // Start game --------------------------------------------------------------------------------------------------

    public void startGame() {
        initializePiecesInBase();
        firePlayerInfoEvents();
        determineFirstPlayer();

        List<Player> finishOrder = new ArrayList<>();
        while (finishOrder.size() < players.size()) {
            playRound(finishOrder);
        }
    }

    private void initializePiecesInBase() {
        for (Player player : players) {
            for (Piece piece : player.getPieces()) {
                board.getBaseCell(player.getColor()).addPiece(piece);
            }
        }
    }

    private void firePlayerInfoEvents() {
        for (Player player : players) {
            List<String> names = new ArrayList<>();
            for (Piece piece : player.getPieces()) {
                names.add(piece.getFullName());
            }
            firePlayerInfo(player.getColor(), names);
        }
    }

    // Get first player --------------------------------------------------------------------------------------------------

    private void determineFirstPlayer() {
        int highestRoll = -1;
        Player firstPlayer = null;

        for (Player player : players) {
            int roll = dice.roll();
            fireInitialRoll(player.getColor(), roll);
            if (roll > highestRoll) {
                highestRoll = roll;
                firstPlayer = player;
            }
        }

        fireFirstPlayer(firstPlayer.getColor());

        int startIndex = turnManager.getIndexOf(firstPlayer);
        turnManager.setPlayerOrder(startIndex);

        List<String> order = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            order.add(players.get((startIndex + i) % players.size()).getColor());
        }
        fireTurnOrder(order);
    }

    // Round --------------------------------------------------------------------------------------------------

    private void playRound(List<Player> finishOrder) {
        for (int i = 0; i < players.size(); i++) {
            Player player = turnManager.getNextPlayer();
            if (player.hasWon()) continue;

            playTurn(player);

            if (player.hasWon() && !finishOrder.contains(player)) {
                finishOrder.add(player);
                fireGameWon(player.getColor());
                if (finishOrder.size() == players.size()) return;
            }
        }

        // Mystery cell lifecycle
        boolean piecesOnPath = false;
        for (Player player : players) {
            for (Piece piece : player.getPieces()) {
                if (piece.isOnBoard() && !piece.isInHomeStraight()) {
                    piecesOnPath = true;
                    break;
                }
            }
        }

        int prevPos = mysteryManager.getPosition();
        boolean wasActive = mysteryManager.isActive();
        mysteryManager.updateRound(piecesOnPath);

        if (mysteryManager.isActive()
                && (!wasActive || mysteryManager.getPosition() != prevPos)) {
            fireMysteryCellSpawned(mysteryManager.getPosition(),
                    mysteryManager.getRoundsRemaining());
        }

        // Advance piece states (Energized/Sick/Frozen timers)
        for (Player player : players) {
            for (Piece piece : player.getPieces()) {
                piece.updateState();
            }
        }

        turnManager.incrementRound();
        fireRoundEnd(players);
    }

    // Turn controlling --------------------------------------------------------------------------------------------------

    // Runs single player turn with bonus rolls
    private void playTurn(Player player) {
        boolean keepRolling = true;

        while (keepRolling) {
            keepRolling = false;

            int diceValue = dice.roll();
            fireDiceRolled(player.getColor(), diceValue);

            // Notify all pieces — FrozenState tracks consecutive 3s
            for (Piece piece : player.getPieces()) {
                piece.notifyDiceRoll(diceValue);
            }

            // Check frozen piece teleports (Rule T-13)
            handleFrozenTeleports(player);

            // Consecutive six tracking (Rule 4, Rule T-6)
            if (diceValue == config.getDiceSides()) {
                player.incrementConsecutiveSixes();

                if (ruleEngine.isThirdConsecutiveSix(player.getConsecutiveSixes())) {
                    handleTripleSixIfBlock(player);
                    player.resetConsecutiveSixes();
                    return; // no move, no bonus
                }
                keepRolling = true; // six grants bonus roll (Rule 4)
            } else {
                player.resetConsecutiveSixes();
            }

            boolean captured = false;

            // FIX: get valid moves first, then decide base entry separately
            List<Piece> validMoves = ruleEngine.getValidMoves(player, diceValue);

            if (diceValue == config.getDiceSides()
                    && !player.getPiecesInBase().isEmpty()
                    && player.shouldMoveFromBase(diceValue, board)) {
                // FIX: let strategy pick which base piece to move
                executeBaseEntry(player, validMoves, diceValue);
            } else {
                // FIX: exclude base pieces if player chose not to exit base
                validMoves.removeIf(Piece::isInBase);

                if (!validMoves.isEmpty()) {
                    Piece chosen = player.selectMove(
                            validMoves, diceValue, board, ruleEngine);
                    if (chosen != null) {
                        captured = executeMove(player, chosen, diceValue);
                    }
                }
            }

            // Capture grants bonus roll (Rule T-2)
            if (captured) {
                keepRolling = true;
            }
        }
    }

    // Enter the base --------------------------------------------------------------------------------------------------

    private void executeBaseEntry(Player player,
                                  List<Piece> validMoves, int diceValue) {
        // Filter to base pieces only and let strategy choose
        List<Piece> basePieces = new ArrayList<>();
        for (Piece p : validMoves) {
            if (p.isInBase()) basePieces.add(p);
        }
        if (basePieces.isEmpty()) return;

        // Let strategy choose — falls back to first if strategy returns null
        Piece piece = player.selectMove(basePieces, diceValue, board, ruleEngine);
        if (piece == null) piece = basePieces.getFirst();

        EnterBoardCommand cmd = new EnterBoardCommand(
                piece,
                board.getStartingCell(player.getColor()),
                board.getBaseCell(player.getColor()),
                coinToss
        );
        cmd.execute();

        int boardCount = player.getPiecesOnBoard().size();
        int baseCount = player.getPiecesInBase().size();
        firePieceEnteredBoard(player.getColor(), piece.getFullName(),
                boardCount, baseCount);
    }


    // Enter the Home Straight --------------------------------------------------------------------------------------------------

    private boolean executeHomeStraightEntry(Player player, Piece piece,
                                             int effective, Cell fromCell,
                                             int fromPos, int stepsOverApproach) {
        int homeStraightIndex = stepsOverApproach - 1;

        fromCell.removePiece(piece);

        if (homeStraightIndex >= config.getHomePathLength()) {
            piece.moveToHome();
            board.getHomeCell(piece.getColor()).addPiece(piece);
        } else {
            piece.moveToHomeStraight(homeStraightIndex);
            board.getHomeStraightCell(
                    piece.getColor(), homeStraightIndex).addPiece(piece);
        }

        firePieceMoved(player.getColor(), piece.getFullName(),
                fromPos, piece.getPosition(), effective, piece.getDirection());
        return false;
    }

    // Normal movement ---------------------------------------------------------------------------------------------

    private boolean executeMove(Player player, Piece piece, int diceValue) {

        // ── Block movement (Rule T-4) ─────────────────────────────────────────
        if (piece.isInBlock()) {
            Cell pieceCell = board.getCellAt(piece.getPosition());
            Block block = blockHandler.findBlockAt(pieceCell);
            if (block != null) {
                int oldPos = piece.getPosition();
                blockHandler.moveBlock(block, diceValue);
                firePieceMoved(player.getColor(), piece.getFullName(),
                        oldPos, piece.getPosition(),
                        diceValue, piece.getDirection());
                return false;
            }
        }

        // ── Home straight movement ────────────────────────────────────────────
        if (piece.isInHomeStraight()) {
            executeHomeStraightMove(player, piece, diceValue);
            return false;
        }

        // ── Standard path ─────────────────────────────────────────────────────
        int fromPos = piece.getPosition();
        Cell fromCell = board.getCellAt(fromPos);
        int effective = piece.getEffectiveMovement(diceValue);

        // Check approach / home straight entry
        if (ruleEngine.canPassApproach(piece, diceValue)) {
            // FIX: use blockHandler.distanceFromApproach — no duplicate
            int stepsToApproach = blockHandler.distanceFromApproach(piece);
            int stepsOverApproach = effective - stepsToApproach;

            if (stepsOverApproach > 0) {
                boolean canEnter = ruleEngine.canEnterHomeStraight(piece);

                if ("COUNTERCLOCKWISE".equals(piece.getDirection())) {
                    if (!ruleEngine.canEnterHomeStraightCCW(piece)) {
                        // First CCW pass — mark flag, continue on standard path
                        piece.setHasPassedApproachOnce(true);
                        canEnter = false;
                    }
                }

                if (canEnter) {
                    return executeHomeStraightEntry(
                            player, piece, effective,
                            fromCell, fromPos, stepsOverApproach);
                }
                // Cannot enter — fall through to standard path move
            } else {
                // Lands exactly on approach — mark CCW first pass
                if ("COUNTERCLOCKWISE".equals(piece.getDirection())
                        && !piece.getHasPassedApproachOnce()) {
                    piece.setHasPassedApproachOnce(true);
                }
            }
        }

        // Calculate standard path destination
        int destination = ruleEngine.calculateDestination(piece, diceValue);

        // Check for opponent block (Rule T-3)
        if (blockHandler.isBlockedByOpponent(piece, destination)) {
            return handleBlockedMove(
                    player, piece, diceValue, fromPos, destination, fromCell);
        }

        // Capture check BEFORE moving
        Piece capturedPiece = captureHandler.getCapturedPieceAt(
                destination, player.getColor());

        // Block-vs-block capture check (Rule T-8)
        Block attackingBlock = piece.isInBlock()
                ? blockHandler.findBlockAt(fromCell) : null;
        Block defendingBlock = blockHandler.findBlockAt(
                board.getCellAt(destination));

        // Execute standard path move
        Cell toCell = board.getCellAt(destination);
        new MoveCommand(piece, fromCell, toCell, effective).execute();
        firePieceMoved(player.getColor(), piece.getFullName(),
                fromPos, destination, effective, piece.getDirection());

        // Handle single piece capture (Rule 6, T-9)
        boolean captured = false;
        if (capturedPiece != null) {
            Player capturedPlayer = getPlayerByColor(capturedPiece.getColor());
            new CaptureCommand(piece, capturedPiece, captureHandler).execute();

            int boardCount = capturedPlayer != null
                    ? capturedPlayer.getPiecesOnBoard().size() : 0;
            int baseCount = capturedPlayer != null
                    ? capturedPlayer.getPiecesInBase().size() : 0;

            firePieceCaptured(player.getColor(), piece.getFullName(),
                    destination,
                    capturedPiece.getColor(), capturedPiece.getFullName(),
                    boardCount, baseCount);
            captured = true;
        }

        // Block-vs-block capture (Rule T-8)
        if (!captured && attackingBlock != null && defendingBlock != null) {
            if (blockHandler.canBlockCaptureBlock(attackingBlock, defendingBlock)) {
                blockHandler.handleBlockCapture(attackingBlock, defendingBlock);
                captured = true;
            }
        }

        // Block formation (Rule T-3)
        if (!captured) {
            checkAndFormBlock(piece, toCell);
        }

        // Mystery cell (Rule T-11)
        if (mysteryManager.isOnMysteryCell(destination)) {
            handleMysteryLanding(player, piece);
        }

        return captured;
    }

    // Home straight movement --------------------------------------------------------------------------------------

    private void executeHomeStraightMove(Player player, Piece piece, int diceValue) {
        int fromIndex = piece.getHomeStraightIndex();
        int fromCellPos = piece.getPosition();
        int newIndex = ruleEngine.calculateHomeStraightDestination(piece, diceValue);

        board.getHomeStraightCell(piece.getColor(), fromIndex).removePiece(piece);

        if (newIndex >= config.getHomePathLength()) {
            piece.moveToHome();
            board.getHomeCell(piece.getColor()).addPiece(piece);
        } else {
            piece.moveToHomeStraight(newIndex);
            board.getHomeStraightCell(piece.getColor(), newIndex).addPiece(piece);
        }

        firePieceMoved(player.getColor(), piece.getFullName(),
                fromCellPos, piece.getPosition(),
                piece.getEffectiveMovement(diceValue), piece.getDirection());
    }

    // Block movement --------------------------------------------------------------------------------------------------

    private boolean handleBlockedMove(Player player, Piece piece, int diceValue,
                                      int fromPos, int destination, Cell fromCell) {
        Cell blockCell = board.getCellAt(destination);
        String blockingColor = blockCell.hasPieces()
                ? blockCell.getPieces().getFirst().getColor() : "";
        String blockingName = blockCell.hasPieces()
                ? blockCell.getPieces().getFirst().getFullName() : "";

        firePieceBlocked(player.getColor(), piece.getFullName(),
                fromPos, destination, blockingColor, blockingName);

        int maxMove = blockHandler.getMaxMoveBeforeBlock(piece, diceValue);

        if (maxMove < 0 || maxMove == fromPos) {
            fireNoOtherPieces(player.getColor());
        } else {
            int steps = calculateStepsBetween(piece, fromPos, maxMove);
            Cell beforeBlock = board.getCellAt(maxMove);
            new MoveCommand(piece, fromCell, beforeBlock, steps).execute();
            fireMovedBeforeBlock(player.getColor(), piece.getFullName(), maxMove);
        }
        return false;
    }

    // Frozen tp --------------------------------------------------------------------------------------------------

    private void handleFrozenTeleports(Player player) {
        for (Piece piece : player.getPieces()) {
            IPieceState state = piece.getState();
            if (!(state instanceof FrozenState)) continue;

            FrozenState frozen = (FrozenState) state;
            if (!frozen.shouldTeleportToBase()) continue;

            // Guard: only remove from cell if piece is on standard path
            if (!piece.isInBase() && !piece.isAtHome()
                    && !piece.isInHomeStraight()) {
                board.getCellAt(piece.getPosition()).removePiece(piece);
            }

            // Full reset — Rule T-9 applies
            piece.capture();
            board.getBaseCell(piece.getColor()).addPiece(piece);
            frozen.resetTeleportFlag();

            fireTeleportEffect(player.getColor(), piece.getFullName(),
                    "is movement-restricted and has rolled three consecutively."
                            + " Teleporting piece " + piece.getFullName()
                            + " to base.");
        }
    }

    // Mystery cell --------------------------------------------------------------------------------------------------

    private void handleMysteryLanding(Player player, Piece piece) {
        // MysteryManager removes from mystery cell and applies effect
        mysteryManager.handleLanding(piece);

        int effectIndex = mysteryManager.getLastEffectIndex();
        String destination = getEffectDestinationName(effectIndex);

        fireMysteryLanding(player.getColor(), piece.getFullName(), destination);

        switch (effectIndex) {
            case 0: // Alpha — Energized or Sick (Rule T-12)
                if (mysteryManager.isLastAlphaEnergized()) {
                    fireTeleportEffect(player.getColor(), piece.getFullName(),
                            "feels energized, and movement speed doubles.");
                } else {
                    fireTeleportEffect(player.getColor(), piece.getFullName(),
                            "feels sick, and movement speed halves.");
                }
                break;
            case 1: // Beta — Frozen (Rule T-13)
                fireTeleportEffect(player.getColor(), piece.getFullName(),
                        "attends briefing and cannot move for four rounds.");
                break;
            case 2: // Gamma (Rule T-14)
                if (mysteryManager.isLastGammaCCWToBeta()) {
                    // CCW → redirected to Beta — fire the correct message
                    fireDirectionChanged(player.getColor(), piece.getFullName(),
                            "COUNTERCLOCKWISE", "CLOCKWISE"); // else branch in GameLogger
                } else {
                    // CW → direction changed to CCW
                    fireDirectionChanged(player.getColor(), piece.getFullName(),
                            "CLOCKWISE", "COUNTERCLOCKWISE");
                }
                break;
            // 3=Base, 4=X, 5=Approach — no extra effect messages
        }
    }

    private String getEffectDestinationName(int effectIndex) {
        return switch (effectIndex) {
            case 0 -> "Alpha";
            case 1 -> "Beta";
            case 2 -> "Gamma";
            case 3 -> "Base";
            case 4 -> "X";
            case 5 -> "Approach";
            default -> "Unknown";
        };
    }

    // Turn controlling --------------------------------------------------------------------------------------------------

    private void handleTripleSixIfBlock(Player player) {
        boolean hasBlock = false;
        for (Block block : blockHandler.getActiveBlocks().values()) {
            if (!block.getPieces().isEmpty()
                    && block.getPieces().getFirst().getColor()
                    .equalsIgnoreCase(player.getColor())) {
                hasBlock = true;
                break;
            }
        }
        if (hasBlock) {
            blockHandler.handleTripleSixBlockBreak(player);
        }
    }

    private void checkAndFormBlock(Piece piece, Cell cell) {
        List<Piece> piecesOnCell = cell.getPieces();
        if (piecesOnCell.size() < 2) return;

        boolean allSameColor = true;
        for (Piece p : piecesOnCell) {
            if (!p.getColor().equalsIgnoreCase(piece.getColor())) {
                allSameColor = false;
                break;
            }
        }
        if (!allSameColor) return;

        Block existing = blockHandler.findBlockAt(cell);
        if (existing == null) {
            Piece other = null;
            for (Piece p : piecesOnCell) {
                if (p != piece) {
                    other = p;
                    break;
                }
            }
            if (other != null) {
                blockHandler.createBlock(piece, other, cell);
            }
        } else {
            blockHandler.addToBlock(piece, existing, cell);
        }
    }

    // util --------------------------------------------------------------------------------------------------

    private int calculateStepsBetween(Piece piece, int fromPos, int toPos) {
        int count = config.getStandardCellCount();
        if ("CLOCKWISE".equals(piece.getDirection())) {
            return (toPos - fromPos + count) % count;
        } else {
            return (fromPos - toPos + count) % count;
        }
    }

    private Player getPlayerByColor(String color) {
        for (Player player : players) {
            if (player.getColor().equalsIgnoreCase(color)) return player;
        }
        return null;
    }

    // Events --------------------------------------------------------------------------------------------------

    private void firePlayerInfo(String color, List<String> names) {
        for (IGameEventListener l : listeners) l.onPlayerInfo(color, names);
    }

    private void fireInitialRoll(String color, int value) {
        for (IGameEventListener l : listeners) l.onInitialRoll(color, value);
    }

    private void fireDiceRolled(String color, int value) {
        for (IGameEventListener l : listeners) l.onDiceRolled(color, value);
    }

    private void fireFirstPlayer(String color) {
        for (IGameEventListener l : listeners) l.onFirstPlayer(color);
    }

    private void fireTurnOrder(List<String> colors) {
        for (IGameEventListener l : listeners) l.onTurnOrder(colors);
    }

    private void firePieceEnteredBoard(String color, String pieceName,
                                       int boardCount, int baseCount) {
        for (IGameEventListener l : listeners)
            l.onPieceEnteredBoard(color, pieceName, boardCount, baseCount);
    }

    private void firePieceMoved(String color, String pieceName,
                                int from, int to, int value, String direction) {
        for (IGameEventListener l : listeners)
            l.onPieceMoved(color, pieceName, from, to, value, direction);
    }

    private void firePieceBlocked(String color, String pieceName,
                                  int from, int to,
                                  String blockingColor, String blockingName) {
        for (IGameEventListener l : listeners)
            l.onPieceBlocked(color, pieceName, from, to,
                    blockingColor, blockingName);
    }

    private void fireNoOtherPieces(String color) {
        for (IGameEventListener l : listeners) l.onNoOtherPieces(color);
    }

    private void fireMovedBeforeBlock(String color, String pieceName,
                                      int stoppedAt) {
        for (IGameEventListener l : listeners)
            l.onMovedBeforeBlock(color, pieceName, stoppedAt);
    }

    private void firePieceCaptured(String capturerColor, String capturerName,
                                   int cell,
                                   String capturedColor, String capturedName,
                                   int boardCount, int baseCount) {
        for (IGameEventListener l : listeners)
            l.onPieceCaptured(capturerColor, capturerName, cell,
                    capturedColor, capturedName, boardCount, baseCount);
    }

    private void fireMysteryLanding(String color, String pieceName,
                                    String destination) {
        for (IGameEventListener l : listeners)
            l.onMysteryLanding(color, pieceName, destination);
    }

    private void fireTeleportEffect(String color, String pieceName,
                                    String effect) {
        for (IGameEventListener l : listeners)
            l.onTeleportEffect(color, pieceName, effect);
    }

    private void fireDirectionChanged(String color, String pieceName,
                                      String oldDir, String newDir) {
        for (IGameEventListener l : listeners)
            l.onDirectionChanged(color, pieceName, oldDir, newDir);
    }

    private void fireMysteryCellSpawned(int position, int duration) {
        for (IGameEventListener l : listeners)
            l.onMysteryCellSpawned(position, duration);
    }

    private void fireRoundEnd(List<Player> players) {
        for (IGameEventListener l : listeners) l.onRoundEnd(players);
    }

    private void fireGameWon(String color) {
        for (IGameEventListener l : listeners) l.onGameWon(color);
    }

}
