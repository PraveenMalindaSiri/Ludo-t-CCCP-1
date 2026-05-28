package rules;

import block.Block;
import board.Board;
import board.Cell;
import config.GameConfig;
import piece.Piece;
import player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/***
 * Handles all block-related logic.
 */
public class BlockHandler {
    private final Board board;
    private final GameConfig config;

    private final Map<Integer, Block> activeBlocks;

    public BlockHandler(Board board) {
        this.board = board;
        this.config = GameConfig.getInstance();
        this.activeBlocks = new HashMap<>();
    }

    // piece can be in a block if it is on the standard path and in normal state
    public boolean canBeInBlock(Piece piece) {
        if (piece == null) return false;
        return piece.isOnBoard()
                && !piece.isInBase()
                && !piece.isAtHome()
                && !piece.isInHomeStraight()
                && piece.getPosition() >= 0
                && piece.getPosition() < config.getStandardCellCount()
                && piece.isNormalState();
    }

    // Block detection ------------------------------------------------------------------------------------

    public boolean isBlockedByOpponent(Piece movingPiece, int destination) {
        if (destination < 0 || destination >= config.getStandardCellCount()) return false;
        Cell cell = board.getCellAt(destination);
        List<Piece> piecesOnCell = cell.getPieces();

        if (piecesOnCell.size() < 2) return false;

        String cellColor = piecesOnCell.getFirst().getColor();
        if (cellColor.equalsIgnoreCase(movingPiece.getColor())) return false;

        return piecesOnCell.stream().allMatch(p -> p.getColor().equalsIgnoreCase(cellColor));
    }

    public boolean isSameColorBlock(Piece piece, Cell cell) {
        List<Piece> piecesOnCell = cell.getPieces();
        if (piecesOnCell.size() < 2) return false;
        return piecesOnCell.stream()
                .allMatch(p -> p.getColor().equalsIgnoreCase(piece.getColor()));
    }

    public Block findBlockAt(Cell cell) {
        return activeBlocks.get(cell.getPosition());
    }

    // Block creation ----------------------------------------------------------------------------------------

    public Block createBlock(Piece newPiece, Piece existingPiece, Cell cell) {
        if (!canBeInBlock(newPiece) || !canBeInBlock(existingPiece)) return null;

        Block block = new Block(cell);
        block.addPiece(existingPiece);
        block.addPiece(newPiece);
        activeBlocks.put(cell.getPosition(), block);
        return block;
    }

    public void addToBlock(Piece piece, Block block, Cell cell) {
        if (!canBeInBlock(piece)) return;

        block.addPiece(piece);
        activeBlocks.put(cell.getPosition(), block);
    }

    // After block moves, take any same-color normal pieces at the new cell. skips others
    public void absorbSameColorPieces(Block block) {
        if (block == null || block.isDissolved()) return;

        Cell cell = block.getCell();
        String color = block.getPieces().get(0).getColor();

        for (Piece p : new ArrayList<>(cell.getPieces())) {
            if (block.getPieces().contains(p)) continue;
            if (p.getColor().equalsIgnoreCase(color) && canBeInBlock(p)) {
                block.addPiece(p);
            }
        }

        activeBlocks.put(cell.getPosition(), block);
    }

    // Block movement ----------------------------------------------------------------------------------------

    public void moveBlock(Block block, int diceValue) {
        if (block == null || block.isDissolved()) return;

        int movementPerPiece = getBlockMovementAmount(block, diceValue);
        if (movementPerPiece <= 0) return;

        String moveDirection = resolveBlockDirection(block);
        Cell oldCell = block.getCell();
        int count = config.getStandardCellCount();

        for (Piece piece : block.getPieces()) {
            oldCell.removePiece(piece);
        }

        for (Piece piece : block.getPieces()) {
            piece.setDirection(moveDirection);
            int newPosition = "CLOCKWISE".equals(moveDirection)
                    ? (piece.getPosition() + movementPerPiece) % count
                    : Math.floorMod(piece.getPosition() - movementPerPiece, count);
            piece.moveToPosition(newPosition);
        }

        if (!block.getPieces().isEmpty()) {
            int newPosition = block.getPieces().getFirst().getPosition();
            Cell newCell = board.getCellAt(newPosition);

            for (Piece piece : block.getPieces()) {
                newCell.addPiece(piece);
            }

            activeBlocks.remove(oldCell.getPosition());
            block.setCell(newCell);
            activeBlocks.put(newPosition, block);
        }
    }

    // direction of the piece farthest from home.
    public String resolveBlockDirection(Block block) {
        Piece farthest = null;
        int maxDistance = -1;

        for (Piece piece : block.getPieces()) {
            int distance = distanceFromApproach(piece);
            if (distance > maxDistance) {
                maxDistance = distance;
                farthest = piece;
            }
        }

        return farthest != null ? farthest.getDirection() : "CLOCKWISE";
    }

    // Calculates where the block will land after moving.
    public int calculateBlockDestination(Block block, int diceValue) {
        int movementPerPiece = diceValue / block.getSize();
        String direction = resolveBlockDirection(block);
        int current = block.getPosition();
        int count = config.getStandardCellCount();

        if ("CLOCKWISE".equals(direction)) {
            return (current + movementPerPiece) % count;
        } else {
            return (current - movementPerPiece + count) % count;
        }
    }

    // scan entire block path to check for first enemy block
    public int getFirstOpponentBlockPositionForBlock(Block block, int diceValue) {
        int movementPerPiece = diceValue / block.getSize();
        String direction = resolveBlockDirection(block);
        int current = block.getPosition();
        int count = config.getStandardCellCount();

        // Use first piece as color representative — all pieces share the same color
        Piece representative = block.getPieces().getFirst();

        for (int step = 1; step <= movementPerPiece; step++) {
            int checkPos = "CLOCKWISE".equals(direction)
                    ? (current + step) % count
                    : (current - step + count) % count;

            if (isBlockedByOpponent(representative, checkPos)) {
                return checkPos;
            }
        }
        return -1;
    }

    // cal cells remaining between piece's position and its approach cell.
    public int distanceFromApproach(Piece piece) {
        int current = piece.getPosition();
        int approach = board.getApproachPosition(piece.getColor());
        int count = config.getStandardCellCount();

        if ("CLOCKWISE".equals(piece.getDirection())) {
            return (approach - current + count) % count;
        } else {
            return (current - approach + count) % count;
        }
    }

    public void breakBlock(Piece piece, Block block) {
        block.removePiece(piece);

        if (block.isDissolved()) {
            activeBlocks.remove(block.getCell().getPosition());
            for (Piece remaining : block.getPieces()) {
                remaining.setInBlock(false);
                remaining.setDirection(remaining.getOriginalDirection());
            }
        }
    }

    // Triple six removes all but keep one and move it 6
    public void handleTripleSixBlockBreak(Player player) {
        Block block = findPlayerBlock(player);
        if (block == null) return;

        List<Piece> blockPieces = new ArrayList<>(block.getPieces());
        if (blockPieces.size() < 2) return;

        Piece keepPiece = getClosestToHome(blockPieces);

        for (Piece piece : blockPieces) {
            if (piece == keepPiece) continue;

            breakBlock(piece, block);

            String originalDir = piece.getOriginalDirection();
            piece.setDirection(originalDir);

            Cell oldCell = board.getCellAt(piece.getPosition());
            oldCell.removePiece(piece);

            int sides = config.getDiceSides();

            int newPos;
            if ("CLOCKWISE".equals(originalDir)) {
                newPos = (piece.getPosition() + sides) % config.getStandardCellCount();
            } else {
                newPos = (piece.getPosition() - sides + config.getStandardCellCount())
                        % config.getStandardCellCount();
            }

            piece.moveToPosition(newPos);
            board.getCellAt(newPos).addPiece(piece);
        }
    }

    // Block Capture --------------------------------------------------------------------------------------------------

    public boolean canBlockCaptureBlock(Block attackingBlock, Block defendingBlock) {
        if (attackingBlock == null || defendingBlock == null) return false;
        return attackingBlock.getSize() == defendingBlock.getSize();
    }

    // block capturing another block
    public void handleBlockCapture(Block attackingBlock, Block defendingBlock) {
        Cell defendingCell = defendingBlock.getCell();
        List<Piece> defenders = new ArrayList<>(defendingBlock.getPieces());

        for (Piece defender : defenders) {
            defendingCell.removePiece(defender);
            defender.capture(); // Rule T-9: full reset
            board.getBaseCell(defender.getColor()).addPiece(defender);
        }

        activeBlocks.remove(defendingCell.getPosition());

        for (Piece attacker : attackingBlock.getPieces()) {
            attacker.incrementCaptureCount();
        }
    }

    // a different pieces can move to the cell immediately before the block.
    public int getMaxMoveBeforeBlock(Piece piece, int diceValue) {
        int effective = piece.getEffectiveMovement(diceValue);
        int current = piece.getPosition();
        int count = config.getStandardCellCount();

        for (int step = 1; step <= effective; step++) {
            int checkPos;
            if ("CLOCKWISE".equals(piece.getDirection())) {
                checkPos = (current + step) % count;
            } else {
                checkPos = (current - step + count) % count;
            }

            if (isBlockedByOpponent(piece, checkPos)) {
                if ("CLOCKWISE".equals(piece.getDirection())) {
                    return (current + step - 1 + count) % count;
                } else {
                    return (current - step + 1 + count) % count;
                }
            }
        }

        return -1;
    }

    // Helpers --------------------------------------------------------------------------------------------------

    public Map<Integer, Block> getActiveBlocks() {
        return new HashMap<>(activeBlocks);
    }

    // Returns the position of the first opponent block found anywhere
    public int getFirstOpponentBlockPosition(Piece piece, int diceValue) {
        int effective = piece.getEffectiveMovement(diceValue);
        int current = piece.getPosition();
        int count = config.getStandardCellCount();

        for (int step = 1; step <= effective; step++) {
            int checkPos = "CLOCKWISE".equals(piece.getDirection())
                    ? (current + step) % count
                    : (current - step + count) % count;

            if (isBlockedByOpponent(piece, checkPos)) {
                return checkPos;
            }
        }
        return -1;
    }

    private Block findPlayerBlock(Player player) {
        for (Block block : activeBlocks.values()) {
            if (!block.getPieces().isEmpty()
                    && block.getPieces().getFirst().getColor()
                    .equalsIgnoreCase(player.getColor())) {
                return block;
            }
        }
        return null;
    }

    private Piece getClosestToHome(List<Piece> pieces) {
        Piece closest = pieces.getFirst();
        int minDistance = distanceFromApproach(closest);

        for (int i = 1; i < pieces.size(); i++) {
            int d = distanceFromApproach(pieces.get(i));
            if (d < minDistance) {
                minDistance = d;
                closest = pieces.get(i);
            }
        }
        return closest;
    }

    public int getBlockMovementAmount(Block block, int diceValue) {
        if (block == null || block.getSize() == 0) return 0;
        return diceValue / block.getSize();
    }

    public boolean canBlockMove(Block block, int diceValue) {
        return getBlockMovementAmount(block, diceValue) > 0;
    }

    public void removeFromBlockIfNeeded(Piece piece) {
        piece.setInBlock(false);
    }

}
