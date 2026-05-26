package rules;

import board.Board;
import board.Cell;
import config.GameConfig;
import piece.Piece;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles all capture logic.
 */
public class CaptureHandler {
    private final Board board;
    private final GameConfig config;

    public CaptureHandler(Board board) {
        this.board = board;
        this.config = GameConfig.getInstance();
    }

    // single piece capture ------------------------------------------------------------------------------------------

    // Check if landing is capturing
    public boolean isCapturePossible(Piece movingPiece, int destination) {
        Cell cell = board.getCellAt(destination);
        List<Piece> piecesOnCell = cell.getPieces();

        if (piecesOnCell.isEmpty()) return false;
        if (piecesOnCell.size() > 1) return false;

        Piece target = piecesOnCell.get(0);
        return !target.getColor().equalsIgnoreCase(movingPiece.getColor());
    }

    public Piece getCapturedPieceAt(int position, String capturerColor) {
        Cell cell = board.getCellAt(position);
        List<Piece> piecesOnCell = cell.getPieces();

        if (piecesOnCell.size() != 1) return null;

        Piece target = piecesOnCell.get(0);
        if (target.getColor().equalsIgnoreCase(capturerColor)) return null;

        return target;
    }

    // capture
    public void handleCapture(Piece capturerPiece, Piece capturedPiece) {
        int capturedPosition = capturedPiece.getPosition();
        Cell cell = board.getCellAt(capturedPosition);

        cell.removePiece(capturedPiece);
        capturedPiece.capture(); // Rule T-9: full reset
        board.getBaseCell(capturedPiece.getColor()).addPiece(capturedPiece);
        capturerPiece.incrementCaptureCount();
    }

    // Capture scan to get all capturable pieces of another piece -------------------------------------------------

    public List<Piece> getCapturableOpponents(Piece piece, int diceValue) {
        List<Piece> capturable = new ArrayList<>();

        if (piece.isInBase() || piece.isAtHome() || piece.isInHomeStraight()) {
            return capturable;
        }

        int cellCount = config.getStandardCellCount();
        int effective = piece.getEffectiveMovement(diceValue);
        int destination;

        if ("CLOCKWISE".equals(piece.getDirection())) {
            destination = (piece.getPosition() + effective) % cellCount;
        } else {
            destination = (piece.getPosition() - effective + cellCount) % cellCount;
        }

        Cell destCell = board.getCellAt(destination);
        List<Piece> piecesAtDest = destCell.getPieces();

        if (piecesAtDest.size() == 1
                && !piecesAtDest.get(0).getColor().equalsIgnoreCase(piece.getColor())) {
            capturable.add(piecesAtDest.get(0));
        }

        return capturable;
    }
}
