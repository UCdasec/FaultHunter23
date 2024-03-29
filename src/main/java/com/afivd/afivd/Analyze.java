package com.afivd.afivd;

import org.antlr.v4.gui.Trees;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Future;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

/**
 * The Analyze class acts as a container to run all of our created Fault patterns and also handle initially parsing
 * the passed C file using ANTLR.
 */
public class    Analyze {

    @FXML
    private CheckBox branchCheckbox;
    @FXML
    private CheckBox bypassCheckbox;
    @FXML
    private CheckBox constantCodingCheckbox;
    @FXML
    private CheckBox defaultFailCheckbox;
    @FXML
    private CheckBox detectCheckbox;
    @FXML
    private CheckBox doubleCheckCheckbox;
    @FXML
    private CheckBox loopCheckCheckbox;
    @FXML
    private ArrayList<CheckBox> fault_patterns;
    private CParser parser;
    private CParser.CompilationUnitContext parseTree;
    private final ArrayList<String> codeLines;

    public Analyze(ArrayList<String> codeLines, ArrayList<CheckBox> fault_patterns){
        this.codeLines = codeLines;
        this.fault_patterns = fault_patterns;
    }

    /**
     * loadAndParseC prepares the generated C parser and parseTree with the C contents of the passed C file
     * @param filePath The file path of the C file
     * @return True if file load was successful, false otherwise
     */
    public boolean loadAndParseC(String filePath) {
        try {
            CharStream charStream    = CharStreams.fromFileName(filePath);      // 0. Load C File
            CLexer lexer             = new CLexer(charStream);                  // 1. Get lexer
            CommonTokenStream tokens = new CommonTokenStream(lexer);            // 2. Get list of matched tokens
            this.parser              = new CParser(tokens);                     // 3. Pass tokens to parser
            this.parseTree           = this.parser.compilationUnit();           // 4. Generate ParseTree to scan through
            return true;
        }catch(IOException e){
            e.printStackTrace();
            return false;
        }
    }

    /**
     * RunFaultPatterns runs each pattern created to generate a set of result for the code file including advice
     * @return A ParsedResults storage object that contains all the results from the fault pattern analysis
     */
    public ParsedResults runFaultPatterns(){

        // Output ParsedResults object that will be passed to each Listener to fill with their results
        ParsedResults results = new ParsedResults();

        // VariableSearcher will be used to collect variables from the code file for use by specific patterns
        VariableSearcher variableSearcher = new VariableSearcher();
        ParseTreeWalker.DEFAULT.walk(variableSearcher,parseTree);
        ArrayList<VariableSearcher.VariableTuple> codeVariables = variableSearcher.getVariables();

        // Hold all FaultPatterns to be run in an array to run in a loop
        ArrayList<FaultPattern> faultPatterns = new ArrayList<>();

        // The code stored in codeLines can be modified without worry that it will affect other patterns, parseTree is
        // already created. Make sure to order Fault Patterns appropriately if the codeLines will be modified

        // matching the checkboxes so they get initialized
        matchCheckboxes();

        // Crypto cryptoListener = new Crypto();
        if (this.constantCodingCheckbox.isSelected()) {
            faultPatterns.add(new ConstantCoding(results, 3));
        }
        // Detect detectListener= new Detect();
        if (this.defaultFailCheckbox.isSelected()) {
            faultPatterns.add(new DefaultFail(results));
        }
        // Flow flowListener = new Flow();
        // DoubleCheck doubleCheckListener = new DoubleCheck();
        if (this.loopCheckCheckbox.isSelected()) {
            faultPatterns.add(new LoopCheck(results, codeVariables, codeLines, 4));
        }
        if (this.branchCheckbox.isSelected()) {
            faultPatterns.add(new Branch(results, 3));
        }
        if (this.doubleCheckCheckbox.isSelected()) {
            faultPatterns.add(new DoubleCheck(results, codeLines));
        }
        if (this.detectCheckbox.isSelected()) {
            faultPatterns.add(new Detect(results, codeLines));
        }
        if (this.bypassCheckbox.isSelected()) {
            faultPatterns.add(new Bypass(results));
        }
        // Respond respondListener = new Respond();
        // Delay delayListener = new Delay();
        // Bypass bypassListener = new Bypass();

        // !!! codeLines used by GUI at end of analyze to display code with added replacements.


        // Now that all Fault Pattern objects have been created, use them in the ParseTreeWalker to have them 'listen'
        // Additionally, run all closing function (which does nothing by default)
        for(FaultPattern faultPattern : faultPatterns){
            ParseTreeWalker.DEFAULT.walk((ParseTreeListener) faultPattern,parseTree);
            faultPattern.runAtEnd();
        }

        return results;
    }

    /**
     * Pops up a Swing JFrame window when called with the created c parse tree
     */
    public void showDebugTree(){
        // Return results to display using Swing
        Future<JFrame> treeWindow = Trees.inspect(parseTree, parser);
        // Make sure window doesn't appear off the screen
        try {treeWindow.get().setLocation(0,0);} catch (Exception ignored) {}
    }

    /**
     * Removes references to the used objects to ensure that they are garbage collected
     */
    public void clearParser(){
        this.parser = null;
        this.parseTree = null;
    }

    public void matchCheckboxes() {
        this.branchCheckbox = this.fault_patterns.get(0);
        this.bypassCheckbox = this.fault_patterns.get(1);
        this.constantCodingCheckbox = this.fault_patterns.get(2);
        this.defaultFailCheckbox = this.fault_patterns.get(3);
        this.detectCheckbox = this.fault_patterns.get(4);
        this.doubleCheckCheckbox = this.fault_patterns.get(5);
        this.loopCheckCheckbox = this.fault_patterns.get(6);
    }
}