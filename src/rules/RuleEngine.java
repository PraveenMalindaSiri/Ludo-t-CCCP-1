package rules;

import board.Board;
import board.Cell;
import config.GameConfig;
import piece.Piece;
import player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates moves and calculates destinations.
 */
public class RuleEngine {
    private final Board board;
    private final GameConfig config;
    private final BlockHandler blockHandler;
    private final CaptureHandler captureHandler;

    public RuleEngine(Board board,
                      BlockHandler blockHandler,
                      CaptureHandler captureHandler) {
        this.board = board;
        this.blockHandler = blockHandler;
        this.captureHandler = captureHandler;
        this.config = GameConfig.getInstance();
    }

    public boolean canMoveFromBase(int diceValue) {
        return diceValue == config.getDiceSides();
    }

    public boolean isThirdConsecutiveSix(int consecutiveSixCount) {
        return consecutiveSixCount >= config.getMaxConsecutiveSixes();
    }

    // Home straight rules ------------------------------------------------------------------------------------
    // Need at least one capture
    public boolean canEnterHomeStraight(Piece piece) {
        return piece.getCaptureCount() >= 1;
    }

    // CCW has to pass approach 2 times
    public boolean canEnterHomeStraightCCW(Piece piece) {
        return piece.getHasPassedApproachOnce();
    }

    // In home straight, need exact roll to go home
    public boolean needsExactRoll(Piece piece, int diceValue) {
        if (!piece.isInHomeStraight()) return false;
        int effective = piece.getEffectiveMovement(diceValue);
        int stepsToHome = config.getHomePathLength() - piece.getHomeStraightIndex();
        return effective == stepsToHome;
    }

    // Rolled more than need in home straight
    public boolean overshotsHome(Piece piece, int diceValue) {
        if (!piece.isInHomeStraight()) return false;
        int effective = piece.getEffectiveMovement(diceValue); // energized=x2, sick=x/2
        int stepsToHome = config.getHomePathLength() - piece.getHomeStraightIndex();
        return effective > stepsToHome;
    }

    public int calculateHomeStraightDestination(Piece piece, int diceValue) {
        int effective = piece.getEffectiveMovement(diceValue);
        return piece.getHomeStraightIndex() + effective;
    }

    public int calculateDestination(Piece piece, int diceValue) {
        int effective = piece.getEffectiveMovement(diceValue);
        if ("CLOCKWISE".equals(piece.getDirection())) {
            return (piece.getPosition() + effective) % config.getStandardCellCount();
        } else {
            return (piece.getPosition() - effective + config.getStandardCellCount())
                    % config.getStandardCellCount();
        }
    }

    // Check approach cell ------------------------------------------------------------------------------------

    public boolean canPassApproach(Piece piece, int diceValue) {
        if (piece.isInHomeStraight() || piece.isInBase() || piece.isAtHome()) {
            return false;
        }

        int effective = piece.getEffectiveMovement(diceValue);
        int current = piece.getPosition();
        int approachPos = board.getApproachPosition(piece.getColor());
        int cellCount = config.getStandardCellCount();

        if ("CLOCKWISE".equals(piece.getDirection())) {
            for (int step = 1; step <= effective; step++) {
                if ((current + step) % cellCount == approachPos) return true;
            }
        } else {
            for (int step = 1; step <= effective; step++) {
                if ((current - step + cellCount) % cellCount == approachPos) return true;
            }
        }
        return false;
    }

    // Validate moving forward ------------------------------------------------------------------------------------

    public boolean isValidMove(Piece piece, int diceValue) {
        if (piece.isAtHome()) return false;
        if (!piece.canMove()) return false;
        if (piece.isInBase()) return canMoveFromBase(diceValue);
        if (piece.isInHomeStraight()) return !overshotsHome(piece, diceValue);
        return true;
    }

    public List<Piece> getValidMoves(Player player, int diceValue) {
        List<Piece> valid = new ArrayList<>();

        for (Piece piece : player.getPieces()) {
            if (piece.isAtHome()) continue;
            if (!piece.canMove()) continue;

            if (piece.isInBase()) {
                if (canMoveFromBase(diceValue)) {
                    valid.add(piece);
                }
                continue;
            }

            if (piece.isInHomeStraight()) {
                if (!overshotsHome(piece, diceValue)) {
                    valid.add(piece);
                }
                continue;
            }

            // On the standard path
            int destination = calculateDestination(piece, diceValue);

            // Check if blocked by same-color piece
            Cell destCell = board.getCellAt(destination);
            if (blockHandler.isSameColorBlock(piece, destCell)) {
                continue;
            }

            valid.add(piece);
        }

        return valid;
    }

    // Same-color check ------------------------------------------------------------------------------------

    public boolean isSameColorAtDestination(Piece piece, int destination) {
        Cell cell = board.getCellAt(destination);
        if (!cell.hasPieces()) return false;
        return cell.getPieces().stream()
                .anyMatch(p -> p.getColor().equalsIgnoreCase(piece.getColor()));
    }

    // if one same-color piece, form a block
    public boolean canFormBlock(Piece piece, int destination) {
        Cell cell = board.getCellAt(destination);
        if (!cell.hasPieces()) return false;
        long sameColor = cell.getPieces().stream()
                .filter(p -> p.getColor().equalsIgnoreCase(piece.getColor()))
                .count();
        return sameColor == 1;
    }
}
