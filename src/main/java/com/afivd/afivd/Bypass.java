package com.afivd.afivd;

import org.antlr.v4.runtime.Token;
public class Bypass extends CBaseListener implements FaultPattern {

    private boolean is_conditionalExpression = false;
    private String condition_text;
    private final ParsedResults output;
    public Bypass(ParsedResults output) {
        this.output = output;
    }

    @Override
    public void enterExpression(CParser.ExpressionContext ctx) {
        if (ctx.getParent() instanceof CParser.SelectionStatementContext) { // confirms whether this is a conditional expression within an if statement or not
            is_conditionalExpression = true;
            condition_text = ctx.getText();
        }
    }

    @Override
    public void exitExpression(CParser.ExpressionContext ctx) {
        is_conditionalExpression = false; 
    }

    @Override
    public void enterPostfixExpression(CParser.PostfixExpressionContext ctx) {
        if (is_conditionalExpression && ctx.getText().matches("^(?!\\().*\\)$")) { // to determine if it's a function or not
            this.output.appendResult(new ResultLine(ResultLine.SINGLE_LINE, "bypass", "The condition " + condition_text + " contains a function " + ctx.getText() + ", which may be " +
                    "bypassed.", ctx.start.getLine()));
        }
    }

    public void runAtEnd() {
        // placeholder
    }

}
