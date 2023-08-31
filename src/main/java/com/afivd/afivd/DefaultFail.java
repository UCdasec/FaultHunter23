package com.afivd.afivd;

import org.antlr.v4.runtime.Token;

/**
 * Flags else and default blocks in the parsed C code as potentially unsafe (in the terms of fault injection resistance
 * Covers Fault.DEFAULTFAIL
 */
public class DefaultFail extends CBaseListener implements FaultPattern{
    private final ParsedResults output;

    public DefaultFail(ParsedResults output){
        this.output = output;
    }

    // ------------------------------------------ Listener Overrides ---------------------------------------------------
    // Listener to catch 'default:' code blocks with only a break in them
    @Override
    public void enterLabeledStatement(CParser.LabeledStatementContext ctx) {
        Token token = ctx.getStart();
        int lineNumber = token.getLine();
        if(ctx.start.getText().equalsIgnoreCase("default") && ctx.statement().jumpStatement() != null && ctx.statement().jumpStatement().getStart() != null) {
            if (ctx.statement().jumpStatement().getStart().getText().equalsIgnoreCase("return") && ctx.statement().jumpStatement().children.size() == 2) {
                //if it's a return statement without any parameters, it's the same as having nothing at all
                // so, it's all good
            }
            else if (!ctx.statement().jumpStatement().getStart().getText().equalsIgnoreCase("break")) {
                // if it's not a break statement, potentially sensitive code is executed in the default case, resulting in a default fail
                this.output.appendResult(new ResultLine(ResultLine.SINGLE_LINE, "default_fail", ctx.getText() + " uses potentially unsafe default statement. ", lineNumber));
            }
        }
    }

    // Listener to catch 'else{...}' code blocks
    @Override
    public void enterSelectionStatement(CParser.SelectionStatementContext ctx) {
        if(ctx.Else() != null && ctx.statement().get(1)!=null){
            if(ctx.statement().get(1).selectionStatement()!=null){
                // Do nothing, this is an else-if statement
            } else if (ctx.statement().get(1).compoundStatement() != null && ctx.statement().get(1).compoundStatement().blockItemList().children.size() == 1) {
                // This is to check if else case is simply a `return;` statement  which is the same as not having anything at all
                CParser.BlockItemListContext elseLines = ctx.statement().get(1).compoundStatement().blockItemList();
                if (elseLines.blockItem(0).statement().jumpStatement() != null && elseLines.blockItem(0).statement().jumpStatement().expression() == null) {
                    // do nothing; all good
                } else if (elseLines.blockItem(0).statement().expressionStatement() != null) {
                    // in this case, there is no return statement at all
                    this.output.appendResult(new ResultLine(ResultLine.SINGLE_LINE, "default_fail", "\"" + ctx.Else().getText() + "\"" + " uses potentially unsafe else statement. ", ctx.Else().getSymbol().getLine()));
                }

            } else if(ctx.statement().get(1).compoundStatement() != null || ctx.statement().get(1).expressionStatement() != null){
                // At this point we should be inside an else body
                this.output.appendResult(new ResultLine(ResultLine.SINGLE_LINE, "default_fail", "\"" + ctx.Else().getText() + "\"" + " uses potentially unsafe else statement. ", ctx.Else().getSymbol().getLine()));
            }
        }
    }


    // -------------------------------------------- Helper Functions ---------------------------------------------------
    @Override
    public void runAtEnd () {
        // Nothing currently needed for DefaultFail
    }

}