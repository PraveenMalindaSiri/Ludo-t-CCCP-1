package player.strategy;

import board.Board;
import mystery.MysteryManager;
import piece.Piece;
import rules.RuleEngine;
import util.CyclicIterator;

import java.util.ArrayList;
import java.util.List;

public class RandomStrategy implements IPlayerStrategy {
    private final CyclicIterator<Piece> pieceIterator;
    private final MysteryManager mysteryManager;

    public RandomStrategy(List<Piece> pieces, MysteryManager mysteryManager) {
        this.pieceIterator = new CyclicIterator<>(new ArrayList<>(pieces));
        this.mysteryManager = mysteryManager;
    }

    @Override
    public Piece choosePieceToMove(List<Piece> validPieces, int diceValue, Board board, RuleEngine ruleEngine) {
        return null;
    }

    @Override
    public boolean shouldMoveFromBase(List<Piece> pieces, int diceValue, Board board) {
        return false;
    }
}
