package player;
import board.Board;
import piece.Piece;
import player.strategy.WinStrategy;
import rules.RuleEngine;

import java.util.List;

public class YellowPlayer extends Player {
    public YellowPlayer(List<Piece> pieces) {
        super("YELLOW", pieces, new WinStrategy());
    }

    @Override
    protected Piece choosePieceToMove(List<Piece> pieces, int diceValue,
                                      Board board, RuleEngine ruleEngine) {
        return strategy.choosePieceToMove(pieces, diceValue, board, ruleEngine);
    }
}
