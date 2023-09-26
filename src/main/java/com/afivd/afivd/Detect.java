package com.afivd.afivd;

import org.antlr.v4.runtime.ParserRuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.BitSet;

public class Detect extends CBaseListener implements FaultPattern{

    private final ParsedResults output;
    private final ArrayList<String> external_variables = new ArrayList<>();
    private final ArrayList<String> external_var_values = new ArrayList<>();
    private final ArrayList<Integer> variable_lines = new ArrayList<>();
    private final ArrayList<Boolean> checksum_found = new ArrayList<>();
    private final ArrayList<String> codeLines;

    //constructor
    public Detect(ParsedResults output, ArrayList<String> codeLines) {
        this.output = output;
        this.codeLines = codeLines;
    }

//    ----------------------------------Overloaded ANTLR4 functions---------------------------------------------------------------------------------

    @Override
    public void enterExternalDeclaration(CParser.ExternalDeclarationContext ctx) {
        // we don't want external declarations that are functions
        if (ctx.declaration() != null && ctx.declaration().initDeclaratorList() != null) {
            String codeline = ctx.declaration().initDeclaratorList().getText();

            // first we collect all the variable names and values, and assume that a checksum is not conducted
            // in case there is more than one variable detected per line
            String[] variables;
            String variable;

            // check if declaration is actually function prototype
            if (!codeline.matches("\\w+\\((?:\\w+|)*\\)")) {
                if (codeline.contains(",")) {
                    variables = codeline.split(",");
                    for (int i = 0; i < variables.length; i++) {
                        if (variables[i].contains("=")) {
                            external_variables.add(variables[i].substring(0, variables[i].indexOf('=')));
                            external_var_values.add(variables[i].substring(variables[i].indexOf('=') + 1));
                            variable_lines.add(ctx.start.getLine());
                            checksum_found.add(false);
                        }
                    }
                } else if (codeline.contains("=")) { // only one variable per line
                    variable = codeline.substring(0, codeline.indexOf('='));
                    external_variables.add(variable);

                    String value = codeline.substring(codeline.indexOf('=') + 1);
                    external_var_values.add(value);
                    variable_lines.add(ctx.start.getLine());
                    checksum_found.add(false);
                }
            }
        }
    }

    @Override
    public void enterExclusiveOrExpression(CParser.ExclusiveOrExpressionContext ctx) {
        List<CParser.AndExpressionContext> ctxes = ctx.andExpression();

        // if the size is greater than 1, an exclusive or operation is actually taking place
        if (ctxes.size() > 1) {
            String lhs = ctxes.get(0).getText();
            String rhs = ctxes.get(1).getText();

            int lhs_index = external_variables.indexOf(lhs);
            int rhs_index = external_variables.indexOf(rhs);

            if (lhs_index != -1) checksum_found.set(lhs_index, true);
            if (rhs_index != -1) checksum_found.set(rhs_index, true);
        }
    }

//    -------------------------- Helper functions --------------------------------------------------------------------------

    public void runAtEnd() {
        //for every external variable that hasn't been found in an exclusive or operation, we assume this to be undetected
        for (int i = 0; i < checksum_found.size(); i++) {
            if (checksum_found.get(i) == false) {
                String variable_name = external_variables.get(i);
                String variable_value = external_var_values.get(i);
                int variable_line_no = variable_lines.get(i);

                this.output.appendResult(new ResultLine(ResultLine.SINGLE_LINE, "detect", "Recommended addition of checksum verification for variable " + variable_name + " = " + variable_value + " in line " + variable_line_no + ". See replacements!", variable_line_no));
            }
        }
    }

    private boolean is_checksum(String lhs, String rhs) {
        //verify data type is integer or hex
        if (isIntegerOrHex(lhs) != 0 && isIntegerOrHex(rhs) != 0){
            int _lhs = 0, _rhs = 0;

            // parse to integer depending on whether
            if (isIntegerOrHex(lhs) == 1) { _lhs = Integer.parseInt(lhs); }
            else if (isIntegerOrHex(lhs) == 2) { _lhs = Integer.parseInt(lhs.substring(2), 16); }

            if (isIntegerOrHex(rhs) == 1) { _rhs = Integer.parseInt(rhs); }
            else if (isIntegerOrHex(rhs) == 2) { _rhs = Integer.parseInt(rhs.substring(2), 16); }

            //convert and store integers as bitsets
            byte[] lhs_byteset = BitSet.valueOf(new long[] { _lhs }).toByteArray();
            byte[] rhs_byteset = BitSet.valueOf(new long[] { _rhs }).toByteArray();

            // they must first match in size
            if (lhs_byteset.length == rhs_byteset.length) {
                // if they match length, perform xor operation
                byte[] xor_result = new byte[lhs_byteset.length];
                boolean isChecksum = false;

                //checking to see if the result is equal to what is expected
                for (int i = 0; i < xor_result.length; i++) {
                    xor_result[i] = (byte) (lhs_byteset[i] ^ rhs_byteset[i]);
                    if (i + 1 < xor_result.length && xor_result[i] == -1) {
                        isChecksum = true;
                    } else if (i == xor_result.length - 1 && (xor_result[i] == 15 || xor_result[i] == -1)) {
                        isChecksum = true;
                    } else {
                        isChecksum = false;
                        break;
                    }
                }

                return isChecksum;

            } else return false;
        }
        else return false;
    }

    private int isIntegerOrHex(String str) {
        if (str.matches("^-?\\d+$")) return 1;
        else if (str.matches("^0[xX][0-9a-fA-F]+$")) return 2;
        else return 0;
    }
}
