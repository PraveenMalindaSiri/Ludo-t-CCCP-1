package player.strategy;

import board.Board;
import piece.Piece;
import rules.RuleEngine;

import java.util.List;

public class WinStrategy implements IPlayerStrategy{
    @Override
    public Piece choosePieceToMove(List<Piece> validPieces, int diceValue, Board board, RuleEngine ruleEngine) {
        return null;
    }

    @Override
    public boolean shouldMoveFromBase(List<Piece> pieces, int diceValue, Board board) {
        return false;
    }
}
