package factory;

import board.*;
import config.GameConfig;
import org.junit.jupiter.api.Test;
import piece.Piece;

import static org.junit.jupiter.api.Assertions.*;

class BoardFactoryTest {

    @Test
    void createBoardBuildsFiftyTwoStandardPathCells() {
        Board board = BoardFactory.createBoard();

        assertEquals(GameConfig.getInstance().getStandardCellCount(), board.getStandardPath().size());
        assertThrows(IllegalArgumentException.class, () -> board.getCellAt(-1));
        assertThrows(IllegalArgumentException.class, () -> board.getCellAt(52));
    }

    @Test
    void startingAndApproachCellsArePlacedAtConfiguredPositions() {
        Board board = BoardFactory.createBoard();
        GameConfig config = GameConfig.getInstance();

        assertInstanceOf(StartingCell.class, board.getCellAt(config.getYellowStart()));
        assertInstanceOf(StartingCell.class, board.getCellAt(config.getBlueStart()));
        assertInstanceOf(StartingCell.class, board.getCellAt(config.getRedStart()));
        assertInstanceOf(StartingCell.class, board.getCellAt(config.getGreenStart()));

        assertInstanceOf(ApproachCell.class, board.getCellAt(config.getYellowApproach()));
        assertInstanceOf(ApproachCell.class, board.getCellAt(config.getBlueApproach()));
        assertInstanceOf(ApproachCell.class, board.getCellAt(config.getRedApproach()));
        assertInstanceOf(ApproachCell.class, board.getCellAt(config.getGreenApproach()));

        assertEquals(config.getRedStart(), board.getStartingPosition("RED"));
        assertEquals(config.getRedApproach(), board.getApproachPosition("RED"));
    }

    @Test
    void eachColourHasFiveHomeStraightCells() {
        Board board = BoardFactory.createBoard();

        assertEquals(5, board.getHomeStraight("RED").size());
        assertEquals(5, board.getHomeStraight("GREEN").size());
        assertEquals(5, board.getHomeStraight("YELLOW").size());
        assertEquals(5, board.getHomeStraight("BLUE").size());
        assertEquals(0, board.getHomeStraightCell("RED", 0).getIndex());
        assertEquals(4, board.getHomeStraightCell("RED", 4).getIndex());
    }

    @Test
    void colourSpecificCellsOnlyAcceptTheirOwnColour() {
        Board board = BoardFactory.createBoard();
        Piece red = new Piece("1", "RED");
        Piece blue = new Piece("1", "BLUE");

        assertTrue(board.getBaseCell("RED").canAcceptPiece(red));
        assertFalse(board.getBaseCell("RED").canAcceptPiece(blue));
        assertTrue(board.getHomeCell("RED").canAcceptPiece(red));
        assertFalse(board.getHomeCell("RED").canAcceptPiece(blue));
        assertTrue(board.getHomeStraightCell("RED", 0).canAcceptPiece(red));
        assertFalse(board.getHomeStraightCell("RED", 0).canAcceptPiece(blue));
    }
}
