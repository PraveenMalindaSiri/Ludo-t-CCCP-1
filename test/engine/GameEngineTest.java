package engine;

import board.Board;
import dice.ICoinToss;
import dice.IDice;
import event.IGameEventListener;
import factory.BoardFactory;
import mystery.MysteryManager;
import org.junit.jupiter.api.Test;
import piece.Piece;
import player.*;
import player.strategy.IPlayerStrategy;
import rules.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class GameEngineTest {

    @Test
    void startGameSelectsHighestInitialRollAndPublishesFinalPlacements() {
        // Arrange
        Board board = BoardFactory.createBoard();
        BlockHandler blockHandler = new BlockHandler(board);
        CaptureHandler captureHandler = new CaptureHandler(board);
        RuleEngine ruleEngine = new RuleEngine(board, blockHandler, captureHandler);
        MysteryManager mysteryManager = new MysteryManager(board, new Random(1));

        FakeDice dice = new FakeDice(
                2, 6, 4, 1, // initial rolls: GREEN is highest
                1, 1, 1, 1  // simple turn rolls
        );

        List<Player> players = List.of(
                new AutoFinishingPlayer("RED"),
                new AutoFinishingPlayer("GREEN"),
                new AutoFinishingPlayer("YELLOW"),
                new AutoFinishingPlayer("BLUE")
        );

        GameEngine engine = new GameEngine(
                board,
                players,
                ruleEngine,
                captureHandler,
                blockHandler,
                mysteryManager,
                dice,
                new FixedCoinToss("HEADS")
        );

        RecordingListener listener = new RecordingListener();
        engine.addEventListener(listener);

        // Act
        engine.startGame();

        // Assert
        assertEquals("GREEN", listener.firstPlayer);
        assertEquals(List.of("GREEN", "YELLOW", "BLUE", "RED"), listener.turnOrder);
        assertEquals(List.of("GREEN", "YELLOW", "BLUE", "RED"), listener.winners);
        assertEquals(List.of("GREEN", "YELLOW", "BLUE", "RED"), listener.finalPlacements);
    }

    private static class AutoFinishingPlayer extends Player {
        private int hasWonCalls;

        AutoFinishingPlayer(String color) {
            super(color, color, List.of(), new NoMoveStrategy());
        }

        @Override
        protected Piece choosePieceToMove(List<Piece> pieces, int diceValue,
                                          Board board, RuleEngine ruleEngine) {
            return null;
        }

        @Override
        public boolean hasWon() {
            hasWonCalls++;
            return hasWonCalls >= 2;
        }
    }

    private static class NoMoveStrategy implements IPlayerStrategy {
        @Override
        public Piece choosePieceToMove(List<Piece> validMoves, int diceValue,
                                       Board board, RuleEngine ruleEngine) {
            return null;
        }

        @Override
        public boolean shouldMoveFromBase(List<Piece> pieces, int diceValue,
                                          Board board, RuleEngine ruleEngine) {
            return false;
        }
    }

    private static class FakeDice implements IDice {
        private final int[] values;
        private int index;

        FakeDice(int... values) {
            this.values = values;
        }

        @Override
        public int roll() {
            if (index >= values.length) {
                return 1;
            }

            return values[index++];
        }
    }

    private static class FixedCoinToss implements ICoinToss {
        private final String result;

        FixedCoinToss(String result) {
            this.result = result;
        }

        @Override
        public String toss() {
            return result;
        }
    }

    private static class RecordingListener implements IGameEventListener {
        private String firstPlayer;
        private List<String> turnOrder = new ArrayList<>();
        private final List<String> winners = new ArrayList<>();
        private List<String> finalPlacements = new ArrayList<>();

        @Override
        public void onPlayerInfo(String color, List<String> pieceNames) {
        }

        @Override
        public void onInitialRoll(String color, int value) {
        }

        @Override
        public void onDiceRolled(String color, int value) {
        }

        @Override
        public void onFirstPlayer(String color) {
            this.firstPlayer = color;
        }

        @Override
        public void onTurnOrder(List<String> colors) {
            this.turnOrder = new ArrayList<>(colors);
        }

        @Override
        public void onPieceEnteredBoard(String color, String pieceName,
                                        int boardCount, int baseCount) {
        }

        @Override
        public void onPieceMoved(String color, String pieceName,
                                 int from, int to, int value, String direction) {
        }

        @Override
        public void onPieceBlocked(String color, String pieceName,
                                   int from, int to,
                                   String blockingColor, String blockingName) {
        }

        @Override
        public void onNoOtherPieces(String color) {
        }

        @Override
        public void onMovedBeforeBlock(String color, String pieceName, int stoppedAt) {
        }

        @Override
        public void onPieceCaptured(String capturerColor, String capturerName,
                                    int cell,
                                    String capturedColor, String capturedName,
                                    int boardCount, int baseCount) {
        }

        @Override
        public void onMysteryLanding(String color, String pieceName, String destination) {
        }

        @Override
        public void onTeleportEffect(String color, String pieceName, String effect) {
        }

        @Override
        public void onDirectionChanged(String color, String pieceName,
                                       String oldDirection, String newDirection) {
        }

        @Override
        public void onMysteryCellSpawned(int position, int duration) {
        }

        @Override
        public void onRoundEnd(List<Player> players) {
        }

        @Override
        public void onGameWon(String color) {
            winners.add(color);
        }

        @Override
        public void onFinalPlacements(List<Player> finishOrder) {
            finalPlacements = finishOrder.stream()
                    .map(Player::getColor)
                    .toList();
        }
    }
}