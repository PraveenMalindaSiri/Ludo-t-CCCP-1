package event;

import player.Player;

import java.util.List;

public interface IGameEventListener {
    void onPlayerInfo(String color, List<String> pieceNames);

    void onDiceRolled(String color, int value);

    void onFirstPlayer(String color);

    void onTurnOrder(List<String> colors);

    void onPieceEnteredBoard(String color, String pieceName,
                             int boardCount, int baseCount);

    void onPieceMoved(String color, String pieceName,
                      int from, int to, int value, String direction);

    void onPieceBlocked(String color, String pieceName,
                        int from, int to,
                        String blockingColor, String blockingName);

    void onNoOtherPieces(String color);

    void onMovedBeforeBlock(String color, String pieceName, int stoppedAt);

    void onPieceCaptured(String capturerColor, String capturerName,
                         int cell,
                         String capturedColor, String capturedName);

    void onMysteryLanding(String color, String pieceName, String destination);

    void onTeleportEffect(String color, String pieceName, String effect);

    void onDirectionChanged(String color, String pieceName,
                            String oldDirection, String newDirection);

    void onMysteryCellSpawned(int position, int duration);

    void onRoundEnd(List<Player> players);

    void onGameWon(String color);
}
