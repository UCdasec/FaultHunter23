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
    private final ParsedResults output;
    private final ArrayList<String> codeLines;

    private final ArrayList<String> varNames = new ArrayList<String>();
    private final ArrayList<Integer> values = new ArrayList<Integer>();
    private int foundConditionals = 0;
    private int rootConditionalEnd = 0;

    public DoubleCheck(ParsedResults output, ArrayList<String> codeLines) {
        this.output = output;
        this.codeLines = codeLines;
    }

    // ------------------------------------------ Listener Overrides ---------------------------------------------------
    @Override
    public void enterSelectionStatement(CParser.SelectionStatementContext ctx) {
        currentlyInIfStatement = true;
        // Inside if
        if (ctx.If() != null) {
            // Check how many ifs there are inside this one:
            int start = ctx.start.getLine();
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

    @Override
    public void exitSelectionStatement(CParser.SelectionStatementContext ctx) {
        int startLine = ctx.start.getLine();
        int endLine = ctx.stop.getLine();

        if (!complementFound) {
            // Send out result... [VULNERABLE CONDITIONAL]
            this.output.appendResult(new ResultLine(ResultLine.SPANNING_RESULT,"double_check","Recommended addition of complement check regarding condition at "+startLine+" to "+endLine+". See replacements! ",startLine,endLine));
        }

        // Out of root
        if (endLine >= rootConditionalEnd) {
            values.clear();
            varNames.clear();
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

            if (ctxes.size() > 1) {

                if (rootConditionFound) {
                    // check for complement
                    // Grab root value, then get the complement, check if that is either on left or right.
                    if (isIntegerOrHex(ctxes.get(0).getText())) {
                        // left is int or hex
                        String value = ctxes.get(0).getText();
                        int pComplement;

                        if (value.startsWith("0x")) pComplement = (Integer.parseInt(value.substring(2), 16));
                        else pComplement = (Integer.parseInt(value));

                        // Check complement here
                        if (isComplement(values.get(0), pComplement)) {
                            complementFound = true;
                            return;
                        }

                    } else {
                        String value = ctxes.get(1).getText();
                        int pComplement;

                        if (value.startsWith("0x")) pComplement = (Integer.parseInt(value.substring(2), 16));
                        else pComplement = (Integer.parseInt(value));

                        if (isComplement(values.get(0), pComplement)) {
                            complementFound = true;
                            return;
                        }
                    }
                } else {
                    // Grab root conditional information
                    // Check if left or right are decimal, then assign value/varname respectively.
                    if (isIntegerOrHex(ctxes.get(0).getText())) {
                        // left is int or hex
                        varNames.add(ctxes.get(1).getText());
                        String value = ctxes.get(0).getText();
                        if (value.startsWith("0x")) values.add(Integer.parseInt(value.substring(2), 16));
                        else values.add(Integer.parseInt(value));

                    } else {
                        varNames.add(ctxes.get(0).getText());
                        String value = ctxes.get(1).getText();
                        if (value.startsWith("0x")) values.add(Integer.parseInt(value.substring(2), 16));
                        else values.add(Integer.parseInt(value));
                    }

                    rootConditionFound = true;
                }
            }
        }

    }
    // -------------------------------------------- Helper Functions ---------------------------------------------------
    @Override
    public void runAtEnd () {
        // Nothing currently needed for DoubleCheck
    }

    private boolean isIntegerOrHex(String str) {
        return str.matches("^-?\\d+$") || str.matches("^0[xX][0-9a-fA-F]+$");

    }

    private boolean isLineConditional(String str) {
        return str.matches("^\\s*if\\s*\\(.*$");
    }

    private boolean isComplement(int original, int candidate) {
        return candidate == (~original & 0xFFFF);
    }

}