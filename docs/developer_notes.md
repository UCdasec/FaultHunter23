# Fault Hunter Development

- [Fault Hunter Development](#fault-hunter-development)
  - [Project Structure](#project-structure)
    - [Entrypoint](#entrypoint)
    - [Scene](#scene)
      - [Controller](#controller)
      - [What should I look for in the controller?](#what-should-i-look-for-in-the-controller)
    - [Analyzer](#analyzer)
    - [Fault Patterns (Branch, ConstantCoding, DefaultFail \& LoopCheck)](#fault-patterns-branch-constantcoding-defaultfail--loopcheck)
      - [Extends](#extends)
      - [Implements](#implements)
    - [Listener Overrides](#listener-overrides)
    - [Contexts (ctx)](#contexts-ctx)

For cloning information, please refer to the root README.

## Project Structure

As you may know, Fault Hunter (FH) utilizes ANTLR to parse through C source files. This powerful tool will allow for great flexibility when parsing.

### Entrypoint

`Main.java` is the program's entrypoint. All this file does is set the application's window settings and initialize JavaFX.

### Scene

In JavaFX, a scene represents a single "screen" or "window" in a graphical user interface (GUI) application. It acts as a container that holds all the visual elements, such as buttons, labels, and images, that make up the user interface for that particular screen. A scene defines the root node of the scene graph, which is a hierarchical structure of nodes that represents the visual components of the GUI.

#### Controller

A scene controller, often referred to as a controller class, is a class responsible for handling the logic and behavior associated with a specific scene. It acts as an intermediary between the graphical components defined in the scene and the underlying application logic.

The scene controller class allows you to define event handlers for user interactions, such as button clicks, and perform actions based on those events. It provides access to the visual components defined in the scene through the use of `@FXML` annotations, which allow you to bind the components defined in the `FXML` file to corresponding fields or methods in the controller class.

> The `FXML` file is in the `resources` directory.

#### What should I look for in the controller?

By far, the most important lines for new developers in the project is the start of the `runButton` method. Line 97 at the writing of this section.

### Analyzer

The Analyzer class holds all fault patterns and applied them to the parsed data. These are defined and added to an array in the `runFaultPatterns` method (Line 52).

There's a specific for loop that handles applying the fault patterns onto the parsed data:

```java
for(FaultPattern faultPattern : faultPatterns){
    ParseTreeWalker.DEFAULT.walk((ParseTreeListener) faultPattern,parseTree);
    faultPattern.runAtEnd();
}
```

In my opinion, trying to understand what `ParseTreeWalker.DEFAULT.walk` does, apart from knowing that is used to walk the parsed tree, is a waste of time if you don't understand the concepts of trees or don't get it while reading ANTLR documentation.

Just know that this is how patterns are applied and ran on the data.

### Fault Patterns (Branch, ConstantCoding, DefaultFail & LoopCheck)

This is by far the most important concept to understand in the codebase.

Each pattern is a class that extends `CBaseListener`. We need this because it is what `ParseTreeWalker.DEFAULT.walk` expects apart from the parsed data. The pattern class will also implement the FaultPattern interface.

#### Extends

When a class `extends` another class, it means that the derived class (also known as the subclass) inherits the properties and behaviors of the base class (also known as the superclass). The subclass can access and use the public and protected members (fields and methods) of the superclass. By extending a class, you can create a more specialized version of the base class, adding or overriding functionality as needed. In Java, a class can only extend one superclass, as Java doesn't support multiple inheritance for classes (though multiple interfaces can be implemented, as explained next).

Ex:

```java
public class Superclass {
    // superclass members
}

public class Subclass extends Superclass {
    // subclass members
}

```

#### Implements

When a class `implements` an interface, it means that the class agrees to fulfill the contract defined by the interface. An interface represents a set of abstract methods that a class implementing the interface must provide concrete implementations for. By implementing an interface, a class can declare that it supports specific behaviors or capabilities. A class can implement multiple interfaces, allowing it to exhibit the behaviors defined by each interface.

Ex:

```java
public interface InterfaceA {
    void methodA();
}

public interface InterfaceB {
    void methodB();
}

public class MyClass implements InterfaceA, InterfaceB {
    // implementation of methodA() and methodB()
}
```

### Listener Overrides

Based on our current structure and ANTLR configuration, we need to override certain methods when we create a new pattern. For instance, let's look at `Branch`.

```java
public class Branch extends CBaseListener implements FaultPattern
```

The Branch class checks for trivial constants in if-expressions to better safeguard against fault injection attacks

Consequently, the class needs to know when an if-statement is entered. This information is known/passed down because the class is a `CBaseListener` subclass.

The listeners are constantly watching the parsed data for certain things to happen, hence the name `listeners`. They simply listen for something and give an appropriate response back depending on what was listened.

For `Branch`, we can tell that we entered an `if-statement` because we have access to the method:

```java
public void enterSelectionStatement(CParser.SelectionStatementContext ctx)
```

And we can also tell when we exit the `if-statement` because we have:

```java
public void exitSelectionStatement(CParser.SelectionStatementContext ctx)
```

By default, nothing happens when we enter or exit an `if-statement`, or `SelectionStatement`. In order for us to control or program what we want to happen when we enter or exit an `if-statement` we need to override the method. That means using the `@Override` annotation.

> the @Override annotation is used to indicate that a method in a subclass is intended to override a method with the same signature in its superclass. It is an optional annotation but recommended to use for clarity and to catch potential errors at compile-time.

### Contexts (ctx)

The easiest way to understand context is by taking it literally. Think of `ctx` as information about the current event listened to. For instance:

```java
@Override
public void enterSelectionStatement(CParser.SelectionStatementContext ctx) {
    if (ctx.If() != null) {currentlyInIfStatement = true;}
    // Clear TempResults for use if the if-statement has OR or AND statements
    tempResults.clear();
}
```

Here we use one of the methods in the context to tell whether or not we are in an `If-statement` (`.If()`)

Context also has other useful methods to know what is currently happening in the parsed data. We could get:

```java
Token token = ctx.getStart();
int lineNumber = token.getLine();
```

Which could then be used for:

```java
// Get contexts for the relational expression (if (expression))
List<CParser.RelationalExpressionContext> ctxes = ctx.relationalExpression();
// If we more than one expression (expression && expression)
if (ctxes.size() > 1 && !inForCondition) {
    // If we're measuring equality
    if (ctx.Equal() != null && currentlyInIfStatement) {
        if (ctxes.get(1).getText().equalsIgnoreCase("true") ||
                ctxes.get(1).getText().equalsIgnoreCase("false")) {
            if(inOrExpression&&!inAndExpression){
                // add results (What will be displayed in Fault Hunter)
                tempResults.add(new TempResult(true,TempResult.OR_FLAG,new ResultLine(ResultLine.SINGLE_LINE,"branch","\""+ctx.getText()+"\""+" Using trivial bool in branch statement.",lineNumber)));
            }else if (!inOrExpression&&inAndExpression){
                tempResults.add(new TempResult(true,TempResult.AND_FLAG,new ResultLine(ResultLine.SINGLE_LINE,"branch","\""+ctx.getText()+"\""+" Using trivial bool in branch statement.",lineNumber)));
            }else if (inOrExpression&&inAndExpression){
                tempResults.add(new TempResult(true,TempResult.AND_FLAG,new ResultLine(ResultLine.SINGLE_LINE,"branch","\""+ctx.getText()+"\""+" Using trivial bool in branch statement.",lineNumber)));
            }else{
                // Else, the if statement does not use an AND or an OR operator
                output.appendResult(new ResultLine(ResultLine.SINGLE_LINE,"branch","\""+ctx.getText()+"\""+" Using trivial bool in branch statement.",lineNumber));
            }
        } else if (isInteger(ctxes.get(1).getText())) {
            if(inOrExpression&&!inAndExpression){
                tempResults.add(new TempResult(true,TempResult.OR_FLAG,new ResultLine(ResultLine.SINGLE_LINE,"branch","\""+ctx.getText()+"\""+" Using explicit integer instead of variable in branch.",lineNumber)));
            }else if (!inOrExpression&&inAndExpression){
                tempResults.add(new TempResult(true,TempResult.AND_FLAG,new ResultLine(ResultLine.SINGLE_LINE,"branch","\""+ctx.getText()+"\""+" Using explicit integer instead of variable in branch.",lineNumber)));
            }else if (inOrExpression&&inAndExpression){
                tempResults.add(new TempResult(true,TempResult.AND_FLAG,new ResultLine(ResultLine.SINGLE_LINE,"branch","\""+ctx.getText()+"\""+" Using explicit integer instead of variable in branch.",lineNumber)));
            }else{
                // Else, the if statement does not use an AND or an OR operator
                output.appendResult(new ResultLine(ResultLine.SINGLE_LINE,"branch","\""+ctx.getText()+"\""+" Using explicit integer instead of variable in branch.",lineNumber));
            }
        } else {
            // Else, the statement is deemed not trivial, so if we are in an AND or OR statement, report in the negative
            // ResultLines will be null since we won't use them anyways
            if(inOrExpression&&!inAndExpression){
                tempResults.add(new TempResult(false,TempResult.OR_FLAG,null));
            }else if (!inOrExpression&&inAndExpression){
                tempResults.add(new TempResult(false,TempResult.AND_FLAG,null));
            }else if (inOrExpression&&inAndExpression){
                tempResults.add(new TempResult(false,TempResult.AND_FLAG,null));
            }
            // Else, it is a simple, non-trivial if-statement without ORs or ANDs.
        }
    }
}
```

Don't worry if you can't fully understand that codeblock yet. The whole idea here is to understand that context is extremely useful and needed to get information about the current parsed data. For instance, what lines are we analyzing? Are we in an if-statement? Are we in a for-loop? How many variables are there? What are the values of the variables? What is the line number that we are on? etc... I think you get the point by now. Super useful!
