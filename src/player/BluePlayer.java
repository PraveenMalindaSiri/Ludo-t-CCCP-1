package player;

import board.Board;
import mystery.MysteryManager;
import piece.Piece;
import player.strategy.RandomStrategy;
import rules.RuleEngine;

import java.util.List;

public class BluePlayer extends Player {
    public BluePlayer(List<Piece> pieces, MysteryManager mysteryManager) {
        super("BLUE", pieces, new RandomStrategy(pieces, mysteryManager));
    }

    @Override
    protected Piece choosePieceToMove(List<Piece> pieces, int diceValue,
                                      Board board, RuleEngine ruleEngine) {
        return strategy.choosePieceToMove(pieces, diceValue, board, ruleEngine);
    }
}
