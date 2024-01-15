package com.afivd.afivd;

import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The DoubleCheck class checks for decisions upon a single test as a risk in terms of fault injection attacks.
 * Currently, double check sensitive conditions, preferably not identical, but complementary.
 * Covers Fault.DoubleCheck
 */
public class DoubleCheck extends CBaseListener implements FaultPattern {

    private boolean currentlyInIfStatement = false;
    private boolean rootConditionFound = false;
    private boolean doubleCheckFound = false;
    private boolean inForCondition = false;
    private boolean inWhileCondition = false;
    private boolean inLogicalOrExpression = false;
    private boolean inLogicalAndExpression = false;
    private boolean inInitDeclarator = false;
    private boolean inAssignmentExpression = false;
    private boolean inExpressionStatement = false;
    private final ParsedResults output;
    private final ArrayList<String> codeLines;

    private final ArrayList<String> varNames = new ArrayList<String>();
    private final ArrayList<String> values = new ArrayList<String>();
    private final ArrayList<String> relations = new ArrayList<String>();
    private final ArrayList<Integer> ifStartPositions = new ArrayList<Integer>();
    private final ArrayList<Integer> ifEndPositions = new ArrayList<Integer>();
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
    public void enterForCondition(CParser.ForConditionContext ctx) {
        this.inForCondition = true;
    }
    @Override
    public void exitForCondition(CParser.ForConditionContext ctx) {
        this.inForCondition = false;
    }

    // we keep track of logical OR and logical AND nodes to decide whether we throw out an equality expression in an if-statement of if there's more to come
    // only valid if currently in an if statement and OR/AND node is a non-terminal/internal parse-tree node
    @Override
    public void enterLogicalOrExpression(CParser.LogicalOrExpressionContext ctx) {
        if (currentlyInIfStatement && ctx.getChildCount() > 1)
            this.inLogicalOrExpression = true;
    }
    @Override
    public void exitLogicalOrExpression(CParser.LogicalOrExpressionContext ctx) {
        if (ctx.getChildCount() > 1)
            this.inLogicalOrExpression = false;
    }
    @Override
    public void enterLogicalAndExpression(CParser.LogicalAndExpressionContext ctx) {
        if (currentlyInIfStatement && ctx.getChildCount() > 1)
            this.inLogicalAndExpression = true;
    }
    @Override
    public void exitLogicalAndExpression(CParser.LogicalAndExpressionContext ctx) {
        if (ctx.getChildCount() > 1)
            this.inLogicalAndExpression = false;
    }
    // The expression statement, assignment expression and the init declarator, i.e., assignment expression upon
    // declaration is checked because it can spawn equality expression nodes within it
    @Override
    public void enterInitDeclarator(CParser.InitDeclaratorContext ctx) {
        if (ctx.getChildCount() > 1)
            this.inInitDeclarator = true;
    }
    @Override
    public void exitInitDeclarator(CParser.InitDeclaratorContext ctx) {
        if (ctx.getChildCount() > 1)
            this.inInitDeclarator = false;
    }
    @Override
    public void enterAssignmentExpression(CParser.AssignmentExpressionContext ctx) {
        if (ctx.getChildCount() > 1)
            this.inAssignmentExpression = true;
    }
    @Override
    public void exitAssignmentExpression(CParser.AssignmentExpressionContext ctx) {
        if (ctx.getChildCount() > 1)
            this.inAssignmentExpression = false;
    }
    @Override
    public void enterExpressionStatement(CParser.ExpressionStatementContext ctx) {
        if (ctx.getChildCount() > 1) {
            this.inExpressionStatement = true;
        }
    }
    @Override
    public void exitExpressionStatement(CParser.ExpressionStatementContext ctx) {
        if (ctx.getChildCount() > 1) {
            this.inExpressionStatement = false;
        }
    }

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
                // in case there is a mismatch between no. of start and end positions, last end position must be false
                if (ifEndPositions.size() > ifStartPositions.size())
                    ifEndPositions.remove(ifEndPositions.size()-1);
                ifEndPositions.add(end);

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

        // Out of root, we can start checking for double check
        if (endLine >= rootConditionalEnd) {
            if (!varNames.isEmpty() && !values.isEmpty() && !relations.isEmpty()) {
                for (int j = 0; j < ifStartPositions.size(); j++) {
                    String leftHandExpression = varNames.get(j);

                    //we need to check whether leftHandExpression is a boolean or non-boolean variable. If it is a boolean
                    //we prefix with ! or else we use ~ only if it doesn't already start with it
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
                    doubleCheckFound = false;
                    // if last if statement, it obviously does not have a double check or if the LHS and RHS both don't match
                    if (j == ifStartPositions.size() - 1) {
                        this.output.appendResult(new ResultLine(ResultLine.SPANNING_RESULT, "double_check", "Recommended addition of complement check regarding condition at " + ifStartPositions.get(j) + ". See replacements! ", ifStartPositions.get(j), ifEndPositions.get(j)));
                        createFinishedInsertion(leftHandExpression, relations.get(j), values.get(j), ifStartPositions.get(j), ifEndPositions.get(j));
                    }
                    // if not, then check that the next if-statement in list is within scope of current if-statement
                    else if (ifStartPositions.get(j+1) >= ifEndPositions.get(j) || ifEndPositions.get(j+1) >= ifEndPositions.get(j)) {
                        this.output.appendResult(new ResultLine(ResultLine.SPANNING_RESULT, "double_check", "Recommended addition of complement check regarding condition at " + ifStartPositions.get(j) + ". See replacements! ", ifStartPositions.get(j), ifEndPositions.get(j)));
                        createFinishedInsertion(leftHandExpression, relations.get(j), values.get(j), ifStartPositions.get(j), ifEndPositions.get(j));
                    }
                    // if it is within scope, check if the next if-condition does not match directly (no complement; same condition)
                    else if ((!varNames.get(j).equals(varNames.get(j + 1))) && !values.get(j).equals(values.get(j + 1))) {
                        // in case the next if-condition does not match its complement
                        if (!leftHandExpression.equals(varNames.get(j + 1)) || !isComplement(values.get(j), values.get(j + 1))) {
                            this.output.appendResult(new ResultLine(ResultLine.SPANNING_RESULT, "double_check", "Recommended addition of complement check regarding condition at " + ifStartPositions.get(j) + ". See replacements! ", ifStartPositions.get(j), ifEndPositions.get(j)));
                            createFinishedInsertion(leftHandExpression, relations.get(j), values.get(j), ifStartPositions.get(j), ifEndPositions.get(j));
                        }
                        // if it matches the complement, it is secure
                        else {
                            doubleCheckFound = true;
                            j++;
                        }
                    }
                    // It matches the original condition, hence, it is secure. We mark secure and skip
                    else {
                        doubleCheckFound = true;
                        j++;
                    }
                }
            }

            values.clear();
            varNames.clear();
            relations.clear();
            ifStartPositions.clear();
            ifEndPositions.clear();
            foundConditionals--;
            currentlyInIfStatement = false;
        }
    }

    @Override
    public void enterEqualityExpression(CParser.EqualityExpressionContext ctx) {
        // Needs to be if statement
        if (currentlyInIfStatement) {
            int start = ctx.start.getLine();
            String lineText = ctx.getText();
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
                // to account for if-statements with multiple conditions, end position is repeated to match
                if (ifStartPositions.size() - ifEndPositions.size() == 1) {
                    ifEndPositions.add(ifEndPositions.get(ifEndPositions.size()-1));
                }
            }
            // we add end positions earlier in enterSelectionStatement, if we reach here it means that we need to erase it from the list
            // and reset the rootConditionalEnd
            else if(!ifEndPositions.isEmpty() && (!inLogicalOrExpression && !inLogicalAndExpression) &&
                    (!inInitDeclarator && !inAssignmentExpression && !inExpressionStatement) &&
                    !lineText.startsWith("(") && !lineText.endsWith(")")){
                // Only if there was an unnecessary end position added do we erase it
                if (ifStartPositions.size() < ifEndPositions.size()) {
                    ifEndPositions.remove(ifEndPositions.size() - 1);
                    rootConditionalEnd = 0;
                }
            }
        }

    }
    // -------------------------------------------- Helper Functions ---------------------------------------------------
    @Override
    public void runAtEnd () {
        // nothing to run at end for DoubleCheck
    }

    private void createFinishedInsertion(String leftHandExpression, String currentComparison, String CurrentRightHandExpression, int ifStartPosition, int endLine) {

        String finishedInsertion;

        // creating suggested replacement
        String comparisonExpression = findCorrespondingPair(currentComparison);
        String rightHandExpression = String.valueOf(parseComplement(CurrentRightHandExpression));

        //we also need to check whether rightHandExpression is a boolean or non-boolean variable if in case rightHandExpression is returned
        //as the same, we verify it here.
        if (rightHandExpression == CurrentRightHandExpression) {
            for (int k = ifStartPosition; k >= 0; k--) {
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
        // TODO: Not important, but fix indentation on formatting
        finishedInsertion = "\tif(" + leftHandExpression + " " + comparisonExpression + " " + rightHandExpression + "){\n" +
                "\t\tfaultDetect();\n\t}";

        // finding the opening curly brace and adding to that line
        for (int i = ifStartPosition; i < endLine; i++) {
            String currentLine = codeLines.get(i - 1);
            if (currentLine.endsWith("{")) {
                codeLines.set(i - 1, currentLine + "\n" + finishedInsertion);
                break;
            }
        }
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