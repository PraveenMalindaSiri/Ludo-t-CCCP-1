package player.strategy;

import board.Board;
import piece.Piece;
import rules.*;

import java.util.List;


/**
 * Prioritizes capturing opponents
 * Captures opponent closest to its own home, Keep one piece.
 * Only get new pieces if one can capture any with 6, avoid block
 */
public class AggressiveStrategy implements IPlayerStrategy {
    private final CaptureHandler captureHandler;
    private final BlockHandler blockHandler;

    public AggressiveStrategy(CaptureHandler captureHandler, BlockHandler blockHandler) {
        this.captureHandler = captureHandler;
        this.blockHandler = blockHandler;
    }

    @Override
    public Piece choosePieceToMove(List<Piece> validMoves, int diceValue,
                                   Board board, RuleEngine ruleEngine) {
        // capture opponent closest to its home
        Piece capturingPiece = findBestCapture(validMoves, diceValue, board);
        if (capturingPiece != null) return capturingPiece;

        // move the single piece already on the board
        for (Piece piece : validMoves) {
            if (piece.isOnBoard() && !piece.isInBase()) return piece;
        }

        return validMoves.getFirst();
    }

    // find the piece that can capture opponent close to their home
    private Piece findBestCapture(List<Piece> validMoves, int diceValue, Board board) {
        Piece bestPiece = null;
        int minDistance = Integer.MAX_VALUE;

        for (Piece piece : validMoves) {
            if (piece.isInBase() || piece.isAtHome() || piece.isInHomeStraight()) continue;

            List<Piece> targets = captureHandler.getCapturableOpponents(piece, diceValue);
            for (Piece target : targets) {
                int distance = blockHandler.distanceFromApproach(target);
                if (distance < minDistance) {
                    minDistance = distance;
                    bestPiece = piece;
                }
            }
        }
        return bestPiece;
    }

    @Override
    public boolean shouldMoveFromBase(List<Piece> pieces, int diceValue, Board board) {
        // Only move from base if no piece is on the board
        boolean hasPieceOnBoard = pieces.stream()
                .anyMatch(p -> p.isOnBoard() && !p.isInBase() && !p.isAtHome());

        if (!hasPieceOnBoard) return true;

        // If board has piece, bring another if that can't capture
        for (Piece piece : pieces) {
            if (!piece.isOnBoard() || piece.isInBase() || piece.isAtHome()) continue;
            List<Piece> targets = captureHandler.getCapturableOpponents(piece, 6);
            if (!targets.isEmpty()) return false;
        }
        return true;
    }
}
