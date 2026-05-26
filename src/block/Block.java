package block;

import board.Cell;
import piece.Piece;

import java.util.ArrayList;
import java.util.List;

/**
 * Same color pieces sharing one cell.
 */
public class Block implements IMovable {

    private final List<Piece> pieces;
    private Cell cell;

    public Block(Cell cell) {
        this.pieces = new ArrayList<>();
        this.cell = cell;
    }

    @Override
    public void move(int steps) {
        int movementPerPiece = pieces.isEmpty() ? 0 : steps / pieces.size();
        String blockDirection = getDirection();

        for (Piece piece : pieces) {
            piece.setDirection(blockDirection);
            piece.move(movementPerPiece);
        }
    }

    @Override
    public int getPosition() {
        return pieces.isEmpty() ? -1 : pieces.get(0).getPosition();
    }

    // get majority pieces directions
    @Override
    public String getDirection() {
        if (pieces.isEmpty()) return "CLOCKWISE";

        // If all pieces share the same direction, use that direction
        long cwCount = pieces.stream()
                .filter(p -> "CLOCKWISE".equals(p.getDirection()))
                .count();

        if (cwCount == pieces.size()) return "CLOCKWISE";
        if (cwCount == 0) return "COUNTERCLOCKWISE";

        return cwCount >= pieces.size() - cwCount ? "CLOCKWISE" : "COUNTERCLOCKWISE";
    }

    // Piece -------------------------------------------------------------------------------------

    public void addPiece(Piece piece) {
        piece.setInBlock(true);
        pieces.add(piece);
    }

    public void removePiece(Piece piece) {
        pieces.remove(piece);
        piece.setInBlock(false);
        piece.setDirection(piece.getOriginalDirection()); // Rule T-5
    }

    public List<Piece> getPieces() {
        return new ArrayList<>(pieces);
    }

    public int getSize() {
        return pieces.size();
    }

    public boolean isDissolved() {
        return pieces.size() < 2;
    }

    public void restoreOriginalDirections() {
        for (Piece piece : pieces) {
            piece.setDirection(piece.getOriginalDirection());
        }
    }

    // Cell -------------------------------------------------------------------------------------

    public Cell getCell() {
        return cell;
    }

    public void setCell(Cell cell) {
        this.cell = cell;
    }

    @Override
    public String toString() {
        return "Block" + pieces + "@cell" + (cell != null ? cell.getPosition() : "?");
    }
}
