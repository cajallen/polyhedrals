package net.geryu.polyhedrals.api.parser;

import java.util.ArrayList;
import java.util.List;

public class DiceParser {
    private final String input;
    private int pos = 0;
    private int diceNodeCounter = 0;

    private DiceParser(String input) {
        this.input = input.replace(" ", "").toLowerCase();
    }

    public static ASTNode parse(String expression) {
        DiceParser parser = new DiceParser(expression);
        ASTNode root = parser.parseExpression();
        if (parser.pos < parser.input.length()) {
            throw new IllegalArgumentException("Unexpected '" + parser.input.charAt(parser.pos) + "' at pos " + parser.pos);
        }
        return root;
    }

    private ASTNode parseExpression() {
        ASTNode left = parseTerm();
        while (pos < input.length()) {
            char op = input.charAt(pos);
            if (op == '+' || op == '-') {
                pos++;
                ASTNode right = parseTerm();
                left = new BinaryOpNode(left, op, right);
            } else {
                break;
            }
        }
        return left;
    }

    private ASTNode parseTerm() {
        ASTNode left = parseDiceExpr();
        while (pos < input.length()) {
            char op = input.charAt(pos);
            if (op == '*' || op == '/') {
                pos++;
                ASTNode right = parseDiceExpr();
                left = new BinaryOpNode(left, op, right);
            } else {
                break;
            }
        }
        return left;
    }

    private ASTNode parseDiceExpr() {
        int diceBefore = diceNodeCounter;
        ASTNode count = pos < input.length() && input.charAt(pos) == 'd' ? new NumberNode(1) : parsePrimary();
        if (pos < input.length() && input.charAt(pos) == 'd') {
            pos++;
            ASTNode sides = parsePrimary();
            if (diceNodeCounter != diceBefore) {
                // We can't handle dice dependent formula (yet?). TODO?
                throw new IllegalArgumentException("Dice-dependent dice formula");
            }
            return new DiceNode(++diceNodeCounter, count, sides, parseModifiers());
        }
        return count;
    }

    private List<DiceModifier> parseModifiers() {
        List<DiceModifier> modifiers = new ArrayList<>();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == 'h') {
                pos++;
                modifiers.add(new KeepModifier(true, parseNumberValue()));
            } else if (c == 'l') {
                pos++;
                modifiers.add(new KeepModifier(false, parseNumberValue()));
            } else if (c == 'r') {
                pos++;
                char op = '=';
                if (pos < input.length() && (input.charAt(pos) == '=' || input.charAt(pos) == '<' || input.charAt(pos) == '>')) {
                    op = input.charAt(pos);
                    pos++;
                }
                modifiers.add(new RerollModifier(op, parseNumberValue()));
            } else {
                break;
            }
        }
        return modifiers;
    }

    private int parseNumberValue() {
        if (pos >= input.length() || !Character.isDigit(input.charAt(pos))) {
            throw new IllegalArgumentException("Expected number at pos " + pos);
        }
        int start = pos;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
        return Integer.parseInt(input.substring(start, pos));
    }

    private ASTNode parsePrimary() {
        if (pos < input.length() && input.charAt(pos) == '-') {
            pos++;
            return new BinaryOpNode(new NumberNode(0), '-', parsePrimary());
        }

        if (pos < input.length() && input.charAt(pos) == '(') {
            pos++;
            ASTNode node = parseExpression();
            if (pos < input.length() && input.charAt(pos) == ')') {
                pos++;
            } else {
                throw new IllegalArgumentException("Expected ')' at pos " + pos);
            }
            return node;
        }

        return new NumberNode(parseNumberValue());
    }
}
