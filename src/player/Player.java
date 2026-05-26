package player;

import board.Board;
import dice.IDice;
import piece.Piece;
import player.strategy.IPlayerStrategy;
import rules.RuleEngine;

import java.util.ArrayList;
import java.util.List;

public abstract class Player {
    protected final String color;
    protected final List<Piece> pieces;
    protected final IPlayerStrategy strategy;
    protected int consecutiveSixes;
    protected int lastDiceValue;

    protected Player(String color, List<Piece> pieces, IPlayerStrategy strategy) {
        this.color = color;
        this.pieces = new ArrayList<>(pieces);
        this.strategy = strategy;
        this.consecutiveSixes = 0;
        this.lastDiceValue = 0;
    }

    public final Piece takeTurn(IDice dice, Board board, RuleEngine ruleEngine) {
        lastDiceValue = dice.roll();

        if (lastDiceValue == 6) {
            consecutiveSixes++;
        } else {
            consecutiveSixes = 0;
        }

        for (Piece p : pieces) {
            p.notifyDiceRoll(lastDiceValue);
        }

        if (ruleEngine.isThirdConsecutiveSix(consecutiveSixes)) {
            consecutiveSixes = 0;
            return null;
        }

        List<Piece> validMoves = ruleEngine.getValidMoves(this, lastDiceValue);
        if (validMoves.isEmpty()) {
            return null;
        }

        return choosePieceToMove(new ArrayList<>(pieces), lastDiceValue, board, ruleEngine);
    }

    protected abstract Piece choosePieceToMove(
            List<Piece> pieces, int diceValue, Board board, RuleEngine ruleEngine);

    // Queries ------------------------------------------------------------------------------------------
    public String getColor() {
        return color;
    }

    public List<Piece> getPieces() {
        return new ArrayList<>(pieces);
    }

    public List<Piece> getPiecesOnBoard() {
        List<Piece> result = new ArrayList<>();
        for (Piece p : pieces) {
            if (p.isOnBoard() && !p.isAtHome()) result.add(p);
        }
        return result;
    }

    public List<Piece> getPiecesInBase() {
        List<Piece> result = new ArrayList<>();
        for (Piece p : pieces) {
            if (p.isInBase()) result.add(p);
        }
        return result;
    }

    public List<Piece> getPiecesAtHome() {
        List<Piece> result = new ArrayList<>();
        for (Piece p : pieces) {
            if (p.isAtHome()) result.add(p);
        }
        return result;
    }

    public boolean hasWon() {
        for (Piece p : pieces) {
            if (!p.isAtHome()) return false;
        }
        return true;
    }

    public int getLastDiceValue() {
        return lastDiceValue;
    }

    public int getConsecutiveSixes() {
        return consecutiveSixes;
    }

    public void incrementConsecutiveSixes() {
        consecutiveSixes++;
    }

    public void resetConsecutiveSixes() {
        consecutiveSixes = 0;
    }
}
