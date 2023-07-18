package com.afivd.afivd;

import org.antlr.v4.runtime.Token;
public class DoubleCheck extends CBaseListener implements FaultPattern {
    private final ParsedResults output;

    public DoubleCheck(ParsedResults output) { this.output = output; }

    // ------------------------------------------ Listener Overrides ---------------------------------------------------
    @Override
    public void enterSelectionStatement(CParser.SelectionStatementContext ctx) {
        // Inside if
        if (ctx.If() != null) {
            // Grab expression

            // Need to check if it's a simple or a complex/multi-expression
            String test = ctx.statement().toString();
            int si = ctx.statement().size();

            int start = ctx.start.getLine();
            int end = ctx.stop.getLine();

        }
    }


    // -------------------------------------------- Helper Functions ---------------------------------------------------
    @Override
    public void runAtEnd () {
        // Nothing currently needed for DefaultFail
    }
}