package player;

import board.Board;
import piece.Piece;
import player.strategy.BlockStrategy;
import rules.RuleEngine;

import java.util.List;

public class GreenPlayer extends Player {
    public GreenPlayer(List<Piece> pieces) {
        super("GREEN", pieces, new BlockStrategy());
    }

    @Override
    protected Piece choosePieceToMove(List<Piece> pieces, int diceValue,
                                      Board board, RuleEngine ruleEngine) {
        return strategy.choosePieceToMove(pieces, diceValue, board, ruleEngine);
    }
}
