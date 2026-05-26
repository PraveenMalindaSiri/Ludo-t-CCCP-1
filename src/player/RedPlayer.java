package player;

import board.Board;
import piece.Piece;
import player.strategy.AggressiveStrategy;
import rules.RuleEngine;

import java.util.List;

public class RedPlayer extends Player {
    public RedPlayer(List<Piece> pieces) {
        super("RED", pieces, new AggressiveStrategy());
    }

    @Override
    protected Piece choosePieceToMove(List<Piece> pieces, int diceValue,
                                      Board board, RuleEngine ruleEngine) {
        return strategy.choosePieceToMove(pieces, diceValue, board, ruleEngine);
    }
}
