package event;

import mystery.MysteryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GameLoggerTest {
    private PrintStream originalOut;
    private ByteArrayOutputStream out;
    private GameLogger logger;

    @BeforeEach
    void setUp() {
        originalOut = System.out;
        out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        logger = new GameLogger(mock(MysteryManager.class));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    void printsRequiredPlayerInfoMessage() {
        logger.onPlayerInfo("RED", List.of("R1", "R2", "R3", "R4"));

        assertTrue(output().contains("The red player has four (04) pieces named R1, R2, R3, and R4."));
    }

    @Test
    void printsDiceRollAndMovementMessages() {
        logger.onDiceRolled("BLUE", 6);
        logger.onPieceMoved("BLUE", "B1", 13, 19, 6, "CLOCKWISE");

        String output = output();
        assertTrue(output.contains("Blue player rolled 6."));
        assertTrue(output.contains("Blue moves piece B1 from location 13 to 19 by 6 units in clockwise direction."));
    }

    @Test
    void printsCaptureMessageAndCapturedPlayerCounts() {
        logger.onPieceCaptured("RED", "R1", 14, "BLUE", "B2", 2, 2);

        String output = output();
        assertTrue(output.contains("Red piece R1 lands on square 14, captures Blue piece B2, and returns it to the base."));
        assertTrue(output.contains("Blue player now has 2/4 on pieces on the board and 2/4 pieces on the base."));
    }

    @Test
    void printsMysteryAndWinMessages() {
        logger.onMysteryLanding("GREEN", "G1", "Alpha");
        logger.onTeleportEffect("GREEN", "G1", "feels energized, and movement speed doubles.");
        logger.onGameWon("GREEN");

        String output = output();
        assertTrue(output.contains("Green player lands on a mystery cell and is teleported to Alpha."));
        assertTrue(output.contains("Green piece G1 teleported to Alpha."));
        assertTrue(output.contains("Green piece G1 feels energized, and movement speed doubles."));
        assertTrue(output.contains("Green player wins!!!"));
    }

    private String output() {
        return out.toString();
    }
}
