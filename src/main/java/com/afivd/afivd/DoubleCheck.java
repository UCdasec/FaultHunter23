package com.afivd.afivd;

import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * The DoubleCheck class checks for decisions upon a single test as a risk in terms of fault injection attacks.
 * Currently, double check sensitive conditions, preferably not identical, but complementary.
 * Covers Fault.DoubleCheck
 */
public class DoubleCheck extends CBaseListener implements FaultPattern {

    private boolean currentlyInIfStatement = false;
    private boolean rootConditionFound = false;
    private boolean complementFound = false;
    private boolean inForCondition = false;
    private boolean inWhileCondition = false;

    private final ParsedResults output;
    private final ArrayList<String> codeLines;

    private final ArrayList<String> varNames = new ArrayList<String>();
    private final ArrayList<String> values = new ArrayList<String>();
    private final ArrayList<String> relations = new ArrayList<String>();
    private final ArrayList<Integer> ifStartPositions = new ArrayList<Integer>();
    private final ArrayList<Integer> indentationPoints = new ArrayList<Integer>();
    private int foundConditionals = 0;
    private int rootConditionalEnd = 0;
    private static final String[][] relationalPairs = {
            {"<", ">="},
            {">", "<="},
            {"<=", ">"},
            {">=", "<"},
            {"==", "!="},
            {"!=", "=="}
    };

    public DoubleCheck(ParsedResults output, ArrayList<String> codeLines) {
        this.output = output;
        this.codeLines = codeLines;
    }

    // ------------------------------------------ Listener Overrides ---------------------------------------------------
    // Records whether the parse tree is inside a for-condition, if so ignore branches
    @Override
    public void enterForCondition(CParser.ForConditionContext ctx) {this.inForCondition = true;}
    @Override
    public void exitForCondition(CParser.ForConditionContext ctx) {this.inForCondition = false;}

    @Override
    public void enterIterationStatement(CParser.IterationStatementContext ctx) {
        inWhileCondition = currentlyInIfStatement;
        currentlyInIfStatement = false;
    }
    @Override
    public void exitIterationStatement(CParser.IterationStatementContext ctx) {
        currentlyInIfStatement = inWhileCondition;
        inWhileCondition = false;
    }

    @Override
    public void enterSelectionStatement(CParser.SelectionStatementContext ctx) {
        if (!inForCondition) {
            currentlyInIfStatement = true;
            // Inside if
            if (ctx.If() != null) {
                // Check how many ifs there are inside this one:
                int start = ctx.start.getLine();
                int startChar = ctx.start.getCharPositionInLine();
                indentationPoints.add(startChar);
                int end = ctx.stop.getLine();

                if (rootConditionalEnd < end) rootConditionalEnd = end;

                if (foundConditionals <= 0) {
                    for (int i = 0; i < end - start; i++) {
                        if (isLineConditional(codeLines.get(start + i))) {
                            // Increase conditional counter
                            foundConditionals++;
                        }
                    }
                }

                return;
            }
        }
    }

    @Override
    public void exitSelectionStatement(CParser.SelectionStatementContext ctx) {
        int startLine = ctx.start.getLine();
        int endLine = ctx.stop.getLine();

        // Out of root
        if (endLine >= rootConditionalEnd) {

            // TODO: insert double check for complement in printing replacements if necessary

            String finishedInsertion;

            if (!varNames.isEmpty() && !values.isEmpty() && !relations.isEmpty()) {
                for (int j = 0; j < ifStartPositions.size(); j++) {
                    String leftHandExpression = varNames.get(j);

                    //we need to check whether leftHandExpression is a boolean or non-boolean variable. If it is a boolean
                    //we prefix with ! or else we use ~ only if it doesn't already start with it
                    if (!leftHandExpression.startsWith("~")) {
                        if (!leftHandExpression.startsWith("!")) {
                            for (int k = ifStartPositions.get(j); k >= 0; k--) {
                                String codeLine = codeLines.get(k);
                                if (codeLine.contains("bool " + leftHandExpression)) {
                                    leftHandExpression = "!" + leftHandExpression;
                                    break;
                                } else if (k == 0) {
                                    leftHandExpression = "~" + leftHandExpression;
                                }
                            }
                        }

                        // if left-hand expression is equal to the variable name of the next list in item and the same
                        // holds for the relation and the values, we know there is a complement check right after
                        complementFound = false;
                        // if last if statement, it obviously does not have a double check or if the LHS and RHS both don't match
                        if (j == ifStartPositions.size() - 1 || !leftHandExpression.equals(varNames.get(j + 1)) || !isComplement(values.get(j), values.get(j+1))) {
                            this.output.appendResult(new ResultLine(ResultLine.SPANNING_RESULT, "double_check", "Recommended addition of complement check regarding condition at " + ifStartPositions.get(j) + ". See replacements! ", ifStartPositions.get(j), endLine));

                            String comparisonExpression = findCorrespondingPair(relations.get(j));
                            String rightHandExpression = String.valueOf(parseComplement(values.get(j)));
                            //we also need to check whether rightHandExpression is a boolean or non-boolean variable if in case rightHandExpression is returned
                            //as the same, we verify it here.
                            if (rightHandExpression == values.get(j)) {
                                for (int k = ifStartPositions.get(j); k >= 0; k--) {
                                    String codeLine = codeLines.get(k);
                                    if(codeLine.contains("bool " + rightHandExpression)) {
                                        rightHandExpression = "!" + rightHandExpression;
                                        break;
                                    }
                                    else if (k == 0) {
                                        rightHandExpression = "~" + rightHandExpression;
                                    }
                                }
                            }
//                            String indentation = createIndentation(indentationPoints.get(j));
                            // TODO: Not important, but fix indentation on formatting
                            finishedInsertion = "\tif(" + leftHandExpression + " " + comparisonExpression + " " + rightHandExpression + "){\n" +
                                    "\t\tfaultDetect();\n\t}";

                            // finding the opening curly brace and adding to that line
                            for (int i = ifStartPositions.get(j); i < endLine; i++) {
                                String currentLine = codeLines.get(i - 1);
                                if (currentLine.endsWith("{")) {
                                    codeLines.set(i - 1, currentLine + "\n" + finishedInsertion);
                                    break;
                                }
                            }
                        }
                        else complementFound = true; // no need to append anything

                    }
                    else {
                        complementFound = true;
                        // else we assume this is the double check and ignore
                    }
                }
            }

            values.clear();
            varNames.clear();
            relations.clear();
            ifStartPositions.clear();
            foundConditionals--;
            currentlyInIfStatement = false;
            rootConditionFound = false;
        }


    }

    @Override
    public void enterEqualityExpression(CParser.EqualityExpressionContext ctx) {
        // Needs to be if statement
        if (currentlyInIfStatement) {
            String si = ctx.getText();

            int start = ctx.start.getLine();
            List<CParser.RelationalExpressionContext> ctxes = ctx.relationalExpression();
            if (ctx.getChildCount() >= 3) relations.add(ctx.getChild(1).getText());

            if (ctxes.size() > 1) {

                // Grab root conditional information
                // Check if left or right are decimal or true/false, then assign value/varname respectively.
                if (isIntegerOrHex(ctxes.get(0).getText()) || isTrueOrFalse(ctxes.get(0).getText())) {
                    // left is int or hex
                    varNames.add(ctxes.get(1).getText());
                    String value = ctxes.get(0).getText();
                    addParsedInteger(value);
                    ifStartPositions.add(start);

                } else if (isIntegerOrHex(ctxes.get(1).getText()) || isTrueOrFalse(ctxes.get(1).getText())) {
                    varNames.add(ctxes.get(0).getText());
                    String value = ctxes.get(1).getText();
                    addParsedInteger(value);
                    ifStartPositions.add(start);

                } else {
                    // both are variables
                    varNames.add(ctxes.get(0).getText());
                    String value = ctxes.get(1).getText();
                    addParsedInteger(value);
                    ifStartPositions.add(start);
                }
            }
        }

    }
    // -------------------------------------------- Helper Functions ---------------------------------------------------
    @Override
    public void runAtEnd () {
        // nothing to run at end for DoubleCheck
    }

    private void addParsedInteger(String str) {
        try {
            if (str.startsWith("0x")) values.add(String.valueOf(Integer.parseInt(str.substring(2), 16)));
            else if (str.matches("^(true|false)$")) values.add(str);
            else values.add(str);
        } catch (Exception e) {
            System.out.println("An error occurred: " + e.getMessage());
        }
    }

    private String parseComplement(String str) {
        if (str.startsWith("0x")) return String.valueOf(~Integer.parseInt(str.substring(2), 16));
        else if (str.matches("^(true|false)$")) return String.valueOf(!Boolean.valueOf(str));
        else  if (str.matches("^-?\\d+$")) return String.valueOf(~Integer.parseInt(str));
        //else its another variable, so return and verify whether its a boolean variable
        else return (str);
    }

    private String findCorrespondingPair(String comparisonExpression) {
        for (String[] pair : relationalPairs) {
            if (pair[0].equals(comparisonExpression)) {
                return pair[1];
            }
        }
        return null; // Expression not found
    }

    public static String createIndentation(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(" ");
        }
        return sb.toString();
    }

    private boolean isIntegerOrHex(String str) {
        return str.matches("^-?\\d+$") || str.matches("^0[xX][0-9a-fA-F]+$");
    }

    private boolean isTrueOrFalse(String str) {

        return str.matches("^(true|false)$");
    }

    private boolean isLineConditional(String str) {
        return str.matches("^\\s*if\\s*\\(.*$");
    }

    private boolean isComplement(String original, String candidate) {
        if (isIntegerOrHex(original) && isIntegerOrHex(candidate)) {
            Integer int_original = Integer.parseInt(original);
            Integer int_candidate = Integer.parseInt(candidate);
            int_original = ~int_original;
            return int_candidate == int_original;
        }
        else if (isTrueOrFalse(original) && isTrueOrFalse(candidate)) {
            Boolean bool_original = Boolean.parseBoolean(original);
            Boolean bool_candidate = Boolean.parseBoolean(candidate);
            return bool_candidate != bool_original;
        }
        else return false;
    }

}