package compiler;

import gen.japyListener;
import gen.japyParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class ProgramPrinter implements japyListener {
    int indent = 0;
    int scopeCounter = 0;
    SymbolTable globalTable = new SymbolTable();
    Map<String, SymbolTable> classScopes = new LinkedHashMap<>();
    Map<String, SymbolTable> methodScopes = new LinkedHashMap<>();
    Map<String, SymbolTable> blockScopes = new LinkedHashMap<>();
    ArrayList<String> errors = new ArrayList<>();
    int[] whileScopeDetail = new int[2];

    @Override
    public void enterProgram(japyParser.ProgramContext ctx) {
        System.out.println("///////////////////////////////////////////////////////////////////////");
        System.out.println("//                        PHASE 1                                    //");
        System.out.println("///////////////////////////////////////////////////////////////////////");
    }

    @Override
    public void exitProgram(japyParser.ProgramContext ctx) {
        System.out.println();
        System.out.println("///////////////////////////////////////////////////////////////////////");
        System.out.println("//                        PHASE 2                                    //");
        System.out.println("///////////////////////////////////////////////////////////////////////");
        printScope();
        System.out.println();
        System.out.println("///////////////////////////////////////////////////////////////////////");
        System.out.println("//                        PHASE 3                                    //");
        System.out.println("///////////////////////////////////////////////////////////////////////");
        printErrors();
    }

    @Override
    public void enterClassDeclaration(japyParser.ClassDeclarationContext ctx) {
        indentation();
        String className = ctx.className.getText();
        String accessModifier = ctx.access_modifier() != null ? ctx.access_modifier().getText() : "public";
        String inherits = ctx.getText().contains("inherits") ? ctx.classParent.getText() : "";
        printParser("<class '" + className + "'" + enterClassDeclarationHelper(ctx) + ">");
        indent++;

        int startLine = ctx.getStart().getLine();
        int stopLine = ctx.getStop().getLine();


        for (Map.Entry<String, SymbolTableEntry> entry : globalTable.table.entrySet()) {
            if (Objects.equals(entry.getKey(), "class_" + className)) {
                className = className +  "_" + ctx.start.getLine() + "_" + (ctx.start.getCharPositionInLine() + 1);
                break;
            }
        }


        SymbolTableEntry classEntry = new SymbolTableEntry(className, accessModifier, "class", startLine, stopLine);
        if (!inherits.isEmpty()) classEntry.addAttribute("inherits: class_" + inherits);
        if (ctx.getText().contains("main")) classEntry.setMain();
        globalTable.insert("class_" + className, classEntry);
        classScopes.put(className, new SymbolTable());
    }

    @Override
    public void exitClassDeclaration(japyParser.ClassDeclarationContext ctx) {
        indent--;
        indentation();
        printParser("</class>");
    }

    @Override
    public void enterFieldDeclaration(japyParser.FieldDeclarationContext ctx) {
        indentation();
        String className = ((japyParser.ClassDeclarationContext) ctx.getParent()).className.getText();
        String accessModifier = ctx.access_modifier() != null ? ctx.access_modifier().getText() : "public";
        String type = ctx.japyType().getText();
        printParser(fieldToString(ctx) + ": (field, " + accessModifier + ctx.japyType().getText() + ")");

        int startLine = ctx.getStart().getLine();
        int stopLine = ctx.getStop().getLine();
        for (TerminalNode id : ctx.ID()) {
            String name = id.getText();
            if (checkRedundantError(classScopes, name, "field_")){
                errors.add("Error 104: in line [" + ctx.start.getLine() + ":" + (ctx.start.getCharPositionInLine() + 1) + "], field [" + name + "] has been defined already");
                name = name + "_" + ctx.start.getLine() + "_" + (ctx.start.getCharPositionInLine() + 1);
            }
            SymbolTableEntry fieldEntry = new SymbolTableEntry(name, accessModifier, type, startLine, stopLine);
            classScopes.get(className).insert("field_" + name, fieldEntry);
        }
    }

    @Override
    public void enterMethodDeclaration(japyParser.MethodDeclarationContext ctx) {
        indentation();
        String className = ((japyParser.ClassDeclarationContext) ctx.getParent()).className.getText();
        String accessModifier = ctx.access_modifier() != null ? ctx.access_modifier().getText() : "public";
        String methodName = ctx.methodName.getText();
        String returnType = ctx.t.getText();
        printParser("<function '" + methodName + "'" + enterMethodDeclarationHelper(ctx));
        indent++;

        if (checkRedundantError(classScopes, methodName, "function_")){
            methodName = methodName + "_" + ctx.start.getLine() + "_" + (ctx.start.getCharPositionInLine() + 1);
            errors.add("Error 102: in line [" + ctx.start.getLine() + ":" + (ctx.start.getCharPositionInLine() + 1) + "], method [" + methodName + "] has been defined already");
        }
        int startLine = ctx.getStart().getLine();
        int stopLine = ctx.getStop().getLine();
        StringBuilder result = new StringBuilder("[");

        SymbolTableEntry methodEntry = new SymbolTableEntry(methodName, accessModifier, "method", startLine, stopLine);
        methodEntry.addAttribute("return: " + returnType);
        for (int i = 1; i < ctx.ID().size(); i++) {
            if (i > 1) result.append(", ");
            String paramName = ctx.ID(i).getText();
            String paramType = ctx.japyType(i - 1).getText();
            String is_array = paramType.contains("[]") ? ", is_array)" : ")";
            String fullType = paramType;
            if (!is_array.equals(")")) {
                fullType = "(" + paramType.replace("[]", "") + is_array + ")]";
            }
            Parameter p = new Parameter(i - 1, paramName, paramType);
            methodEntry.addParameter(p);
            result.append("[(index:").append(i - 1).append("), (name: ").append(paramName).append("), (type: ").append(fullType);
        }
        if (ctx.ID().size() > 1) result.append(")]]");
        else result.append("]");
        methodEntry.addAttribute("parameter: " + result);
        classScopes.get(className).insert("function_" + ctx.methodName.getText(), methodEntry);

        methodScopes.put(methodName,new SymbolTable());
    }

    @Override
    public void exitMethodDeclaration(japyParser.MethodDeclarationContext ctx) {
        indent--;
        indentation();

        String className = ((japyParser.ClassDeclarationContext) ctx.getParent()).className.getText();
        if (ctx.s != null && ctx.s.s1 != null && ctx.s.s1.s6 != null) {
            printParser("</function return (" + ctx.s.s1.s6.e.e.getText() + ", " + ctx.t.getText() + ")>");
            String type = typeCheck(ctx.s.s1.s6.e.e.getText());
            if (type.equals("var")) {
                if (!Objects.equals(methodScopes.get(ctx.methodName.getText()).lookup("var_" + ctx.s.s1.s6.e.e.getText()).type, ctx.t.getText())){
                    errors.add("Error 210: in line [" + ctx.s.s1.s6.start.getLine() + ":" + (ctx.s.s1.s6.stop.getCharPositionInLine() + 1) + "], ReturnType of this method must be [" + ctx.t.getText()+ "]");
                }
            } else {
                if (!type.equals(ctx.t.getText())) {
                    errors.add("Error 210: in line [" + ctx.s.s1.s6.start.getLine() + ":" + (ctx.s.s1.s6.stop.getCharPositionInLine() + 1) + "], ReturnType of this method must be [" + ctx.t.getText()+ "]");
                }
            }
        } else {
            printParser("</function>");
            String name = classScopes.get(className).lookup("function_" + ctx.methodName.getText()).name;
            errors.add("Error 211: in line [" + ctx.start.getLine() + ":" + (ctx.start.getCharPositionInLine() + 1) + "], return statement missing for method [" + name + "]");
        }
    }

    @Override
    public void enterClosedConditional(japyParser.ClosedConditionalContext ctx) {
        indentation();
        printParser("<if condition: <" + ctx.ifExp.getText() + ">>");
        indent++;

        Map<String, SymbolTable> temp;
        String methodName = findClosestScope(ctx.start.getLine(), ctx.stop.getLine());
        if (methodName.contains("if") || methodName.contains("elif") ||
            methodName.contains("else") || methodName.contains("while")) {
            temp = blockScopes;
        } else temp = methodScopes;
        String blockName = "if_" + scopeCounter++;
        int startLine = ctx.getStart().getLine();
        int stopLine = ctx.ifStat.getStop().getLine();


        SymbolTableEntry ifEntry = new SymbolTableEntry(blockName, "", "if", startLine, stopLine);
        temp.get(methodName).insert(blockName, ifEntry);
        blockScopes.put(blockName, new SymbolTable());


        if (ctx.getText().contains("elif")) {
            blockName = "elif_" + scopeCounter++;
            startLine = ctx.elifExp.start.getLine();
            stopLine = ctx.elifStat.getStop().getLine();
            SymbolTableEntry elifEntry = new SymbolTableEntry(blockName, "", "elif", startLine, stopLine);
            temp.get(methodName).insert(blockName, elifEntry);
            blockScopes.put(blockName, new SymbolTable());
        }

        if (ctx.getText().contains("else")) {
            blockName = "else_" + scopeCounter++;
            startLine = ctx.elseStmt.start.getLine();
            stopLine = ctx.elseStmt.getStop().getLine();
            SymbolTableEntry elseEntry = new SymbolTableEntry(blockName, "", "else", startLine, stopLine);
            temp.get(methodName).insert(blockName, elseEntry);
            blockScopes.put(blockName, new SymbolTable());
        }
    }

    @Override
    public void exitClosedConditional(japyParser.ClosedConditionalContext ctx) {
        indent--;
        indentation();
        if (ctx.getText().contains("else")) {
            printParser("</else>");
        } else {
            printParser("</if>");
        }
    }

    @Override
    public void enterOpenConditional(japyParser.OpenConditionalContext ctx) {
        indentation();
        printParser("<if condition: <" + ctx.ifExp.getText() + ">>");
        indent++;

        Map<String, SymbolTable> temp;
        String methodName = findClosestScope(ctx.start.getLine(), ctx.stop.getLine());
        if (methodName.contains("if") || methodName.contains("elif") ||
                methodName.contains("else") || methodName.contains("while")) {
            temp = blockScopes;
        } else temp = methodScopes;


        String blockName = "if_" + scopeCounter++;
        int startLine = ctx.getStart().getLine();
        int stopLine;
        if (ctx.ifStat != null) stopLine = ctx.ifStat.getStop().getLine();
        else if (ctx.secondIfStat != null) stopLine = ctx.secondIfStat.getStop().getLine();
        else stopLine = ctx.thirdIfStat.getStop().getLine();
        SymbolTableEntry ifEntry = new SymbolTableEntry(blockName, "", "if", startLine, stopLine);
        temp.get(methodName).insert(blockName, ifEntry);
        blockScopes.put(blockName, new SymbolTable());


        if (ctx.getText().contains("elif")) {
            blockName = "elif_" + scopeCounter++;
            if (ctx.elifExp != null) startLine = ctx.elifExp.getStop().getLine();
            else startLine = ctx.lastElifExp.getStop().getLine();
            if (ctx.elifStat != null) stopLine = ctx.elifStat.getStop().getLine();
            else stopLine = ctx.lastElifStmt.getStop().getLine();
            SymbolTableEntry elifEntry = new SymbolTableEntry(blockName, "", "elif", startLine, stopLine);
            temp.get(methodName).insert(blockName, elifEntry);
            blockScopes.put(blockName, new SymbolTable());
        }


        if (ctx.getText().contains("else") && ctx.elseStmt != null) {
            blockName = "else_" + scopeCounter++;
            startLine = ctx.elseStmt.getStart().getLine();
            stopLine = ctx.elseStmt.getStop().getLine();
            SymbolTableEntry elseEntry = new SymbolTableEntry(blockName, "", "else", startLine, stopLine);
            temp.get(methodName).insert(blockName, elseEntry);
            blockScopes.put(blockName, new SymbolTable());
        }
    }

    @Override
    public void exitOpenConditional(japyParser.OpenConditionalContext ctx) {
        indent--;
        indentation();
        printParser("</if>");
    }

    @Override
    public void enterStatementVarDef(japyParser.StatementVarDefContext ctx) {
        indentation();
        printParser(ctx.e1.getText() + " -> (" + ctx.i1.getText() + ", var)");

        Map<String, SymbolTable> temp;
        String scopeName = findClosestScope(ctx.start.getLine(), ctx.stop.getLine());
        if (scopeName.contains("if") || scopeName.contains("elif") ||
                scopeName.contains("else") || scopeName.contains("while")) {
            temp = blockScopes;
        } else temp = methodScopes;

        String accessModifier = "", type = typeCheck(ctx.expression(0).getText());
        if (type.equals("error")) {
            errors.add("Error 609: in line [" + ctx.start.getLine() + ":" + ctx.stop.getLine() + "], invalid index type");
        }

        while (type.equals("var")) {
            type = temp.get(scopeName).lookup("var_" + ctx.expression(0).getText()).type;
        }

        int startLine = ctx.getStart().getLine();
        int stopLine = ctx.getStop().getLine();
        String className = findClosestClassScope(startLine, stopLine);
        if (type.equals("function") && ctx.expression(0).getText().contains(")")) parseMethodDeclaration(ctx.expression(0).getText(), className, temp, scopeName, ctx);


        for (TerminalNode id : ctx.ID()) {
            String name = id.getText();
            SymbolTableEntry fieldEntry = new SymbolTableEntry(name, accessModifier, type, startLine, stopLine);
            fieldEntry.setFirst_appearance(startLine);
            fieldEntry.setBlock();

            if ((type.equals("bool[]") || type.equals("double[]") || type.equals("string[]")) && ctx.getText().contains("]")) {
                int endIndex = ctx.getText().indexOf(']');
                int startIndex = ctx.getText().indexOf('[') + 1;

                int size = Integer.parseInt(ctx.getText().substring(startIndex, endIndex));
                fieldEntry.setSize(size);
            }

            temp.get(scopeName).insert("var_" + name, fieldEntry);
        }
    }

    @Override
    public void enterStatementContinue(japyParser.StatementContinueContext ctx) {
        indentation();
        printParser("Goto " + whileScopeDetail[0]);
    }

    @Override
    public void exitStatementBreak(japyParser.StatementBreakContext ctx) {
        indentation();
        printParser("Goto " + (whileScopeDetail[1] + 1));
    }

    @Override
    public void enterStatementClosedLoop(japyParser.StatementClosedLoopContext ctx) {
        indentation();
        printParser("<while condition: <" + ctx.expression().getText() + ">>");
        whileScopeDetail[0] = ctx.start.getLine();
        whileScopeDetail[1] = ctx.stop.getLine();
        indent++;

        String methodName = findClosestMethodScope(ctx.start.getLine(), ctx.stop.getLine());
        String blockName = "while_" + scopeCounter++;
        int startLine = ctx.getStart().getLine();
        int stopLine = ctx.getStop().getLine();

        SymbolTableEntry whileEntry = new SymbolTableEntry(blockName, "", "while", startLine, stopLine);
        methodScopes.get(methodName).insert(blockName, whileEntry);

        SymbolTable whileScope = new SymbolTable();
        blockScopes.put(blockName, whileScope);
    }

    @Override
    public void exitStatementClosedLoop(japyParser.StatementClosedLoopContext ctx) {
        indent--;
        indentation();
        printParser("</while>");
    }

    @Override
    public void enterStatementAssignment(japyParser.StatementAssignmentContext ctx) {
        indentation();
        printParser(ctx.right.getText() + " -> " + ctx.left.getText());

        Map<String, SymbolTable> temp;
        String scopeName = findClosestScope(ctx.start.getLine(), ctx.stop.getLine());
        if (scopeName.contains("if") || scopeName.contains("elif") ||
                scopeName.contains("else") || scopeName.contains("while")) {
            temp = blockScopes;
        } else temp = methodScopes;
        String var = ctx.left.getText();
        if (ctx.left.getText().endsWith("]")) {
            var = ctx.left.getText().substring(0, ctx.left.getText().indexOf("["));
        }
        String rightVarType = typeCheck(ctx.right.getText());
        while (rightVarType.equals("var")) {
            rightVarType = temp.get(scopeName).lookup("var_" + ctx.right.getText()).type;
        }
        temp.get(scopeName).lookup("var_" + var).type = rightVarType;

        if(ctx.right.getText().indexOf('[') != -1) {
            int endIndex = ctx.right.getText().indexOf(']');
            int startIndex = ctx.right.getText().indexOf('[') + 1;


            try {
                int size = Integer.parseInt(ctx.right.getText().substring(startIndex, endIndex));
                String varName = ctx.right.getText().substring(0, startIndex - 1);
                if(temp.get(scopeName).lookup("var_" + varName).size < size) {
                    errors.add("Error 504 : in line [" + ctx.start.getLine() + ":" + (ctx.stop.getCharPositionInLine() + 1) + "], Out of bound exception");
                }
            } catch (Exception e){
                System.out.println("Error 69: in line [" + ctx.start.getLine() + ":" + ctx.stop.getLine() + "], invalid index type");
            }

        }
    }

    @Override
    public void enterStatementInc(japyParser.StatementIncContext ctx) {
        indentation();
        printParser("1 + " + ctx.lvalExpr.getText() + " -> " + ctx.lvalExpr.getText());
    }

    @Override
    public void enterStatementDec(japyParser.StatementDecContext ctx) {
        indentation();
        printParser(ctx.lvalExpr.getText() + " - 1" + " -> " + ctx.lvalExpr.getText());
    }

    @Override
    public void visitTerminal(TerminalNode terminalNode) {
        if (terminalNode.getText().equals("elif")) {
            indent--;
            indentation();
            printParser("<elif condition: <" + printElifExpression(terminalNode) + ">>");
            indent++;


        } else if (terminalNode.getText().equals("else")) {
            indent--;
            indentation();
            printParser("<else>");
            indent++;
        }
    }

    private String printElifExpression(TerminalNode node) {
        ParserRuleContext parentCtx = (ParserRuleContext) node.getParent();
        for (int i = 0; i < parentCtx.getChildCount(); i++) {
            if (parentCtx.getChild(i) == node) {
                if (i + 3 < parentCtx.getChildCount() &&
                        parentCtx.getChild(i + 1).getText().equals("(") &&
                        parentCtx.getChild(i + 3).getText().equals(")")) {

                    ParseTree expressionNode = parentCtx.getChild(i + 2);
                    return expressionNode.getText();
                }
                break;
            }
        }
        return null;
    }
    /////////////////////////////////////////////////////////////////////
    //                           HELPER CLASSES                        //
    /////////////////////////////////////////////////////////////////////
    void printParser(String s) {
        System.out.println(s);
    }

    private void indentation() {
        for (int i = 0; i < indent; i++) System.out.print("    ");
    }

    private void printScope() {
        globalTable.table.forEach((key, value) -> System.out.println("key = " + key + ", value = " + value));
        System.out.println("--------------------------------------------------------------------------------");

        for (Map.Entry<String, SymbolTable> e : classScopes.entrySet()) {
            String key = e.getKey();
            SymbolTable value = e.getValue();
            SymbolTableEntry entry = globalTable.lookup("class_" + key);
            int start = entry.getStartLine();
            int stop = entry.getStopLine();

            System.out.println("---------------------------------- " + key + ": (" + start + "," + stop + ") ----------------------------------");
            if (value.table.isEmpty() || e.getValue().toString().isEmpty()) System.out.println("                                !NO KEY FOUND!");
            else System.out.println(value);
            System.out.println("--------------------------------------------------------------------------------");
        }

        for (Map.Entry<String, SymbolTable> entry : classScopes.entrySet()) {
            for (Map.Entry<String, SymbolTable> e : methodScopes.entrySet()) {
                SymbolTableEntry methodEntry = entry.getValue().lookup("function_" + e.getKey());
                if (methodEntry == null) break;
                int start = methodEntry.getStartLine();
                int stop = methodEntry.getStopLine();

                System.out.println("---------------------------------- " + e.getKey() + ": (" + start + "," + stop + ") ----------------------------------");
                if (e.getValue().table.isEmpty() || e.getValue().toString().isEmpty()) System.out.println("                                !NO KEY FOUND!");
                else System.out.print(e.getValue());
                System.out.println("--------------------------------------------------------------------------------");
            }
        }

        for (Map.Entry<String, SymbolTable> entry : methodScopes.entrySet()) {
            for (Map.Entry<String, SymbolTable> e : blockScopes.entrySet()) {
                SymbolTableEntry methodEntry;
                methodEntry = entry.getValue().lookup(e.getKey());
                if (methodEntry == null) continue;
                int start = methodEntry.getStartLine();
                int stop = methodEntry.getStopLine();

                System.out.println("---------------------------------- " + e.getKey() + ": (" + start + "," + stop + ") ----------------------------------");
                if (e.getValue().table.isEmpty() || e.getValue().toString().isEmpty()) System.out.println("                                !NO KEY FOUND!");
                else System.out.print(e.getValue());
                System.out.println("--------------------------------------------------------------------------------");
            }
        }

        for (Map.Entry<String, SymbolTable> entry : blockScopes.entrySet()) {
            for (Map.Entry<String, SymbolTable> e : blockScopes.entrySet()) {
                SymbolTableEntry methodEntry;
                methodEntry = entry.getValue().lookup(e.getKey());
                if (methodEntry == null) continue;
                int start = methodEntry.getStartLine();
                int stop = methodEntry.getStopLine();

                System.out.println("---------------------------------- " + e.getKey() + ": (" + start + "," + stop + ") ----------------------------------");
                if (e.getValue().table.isEmpty() || e.getValue().toString().isEmpty()) System.out.println("                                !NO KEY FOUND!");
                else System.out.print(e.getValue());
                System.out.println("--------------------------------------------------------------------------------");
            }
        }
    }

    private void printErrors() {
        for (String s: errors){
            System.out.println(s);
        }
    }

    private String findClosestClassScope(int startLine, int stopLine) {
        for (Map.Entry<String, SymbolTableEntry> entry : globalTable.table.entrySet()) {
            if (entry.getValue().startLine < startLine && entry.getValue().stopLine > stopLine) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String findClosestMethodScope(int startLine, int stopLine) {
        String closestScopeName = null;
        int minDistance = Integer.MAX_VALUE;

        for (Map.Entry<String, SymbolTable> entry : classScopes.entrySet()) {
            for (Map.Entry<String, SymbolTable> e : methodScopes.entrySet()) {
                SymbolTableEntry methodEntry = entry.getValue().lookup("function_" + e.getKey());
                if (methodEntry != null) {
                    int methodStartLine = methodEntry.getStartLine();
                    int methodStopLine = methodEntry.getStopLine();
                    int distance = Math.abs(methodStartLine - startLine);
                    if (distance < minDistance) {
                        minDistance = distance;
                        if (methodStopLine > stopLine) closestScopeName = e.getKey();
                    }
                }
            }
        }
        return closestScopeName;
    }

    private String findClosestScope(int startLine, int stopLine) {
        String closestScopeName = findClosestMethodScope(startLine, stopLine);
        int minDistance = Integer.MAX_VALUE;

        for (Map.Entry<String, SymbolTable> entry : methodScopes.entrySet()) {
            for (Map.Entry<String, SymbolTable> e : blockScopes.entrySet()) {
                SymbolTableEntry methodEntry;

                methodEntry = entry.getValue().lookup(e.getKey());
                if (methodEntry != null) {
                    int methodStartLine = methodEntry.getStartLine();
                    int methodStopLine = methodEntry.getStopLine();
                    int distance = Math.abs(methodStartLine - startLine);
                    if (distance < minDistance) {
                        minDistance = distance;
                        if (methodStopLine > stopLine) closestScopeName = e.getKey();
                    }
                }
            }
        }

        for (Map.Entry<String, SymbolTable> entry : blockScopes.entrySet()) {
            for (Map.Entry<String, SymbolTable> e : blockScopes.entrySet()) {
                SymbolTableEntry methodEntry;

                methodEntry = entry.getValue().lookup(e.getKey());
                if (methodEntry != null) {
                    int methodStartLine = methodEntry.getStartLine();
                    int methodStopLine = methodEntry.getStopLine();
                    int distance = Math.abs(methodStartLine - startLine);
                    if (distance < minDistance) {
                        minDistance = distance;
                        if (methodStopLine > stopLine) closestScopeName = e.getKey();
                    }
                }
            }
        }

        return closestScopeName;
    }

    private boolean checkRedundantError(Map<String, SymbolTable> scope, String name, String type) {
        for (Map.Entry<String, SymbolTable> e : scope.entrySet()){
            if (e.getValue().toString().contains(type + name)){
                return true;
            }
        }
        return false;
    }

    private String typeCheck(String s) {
        if (s.endsWith(")")) {
            return "function";
        }
        if (s.endsWith("]")) {
            int endIndex = s.indexOf(']');
            int startIndex = s.indexOf('[') + 1;

            String size = s.substring(startIndex, endIndex);

            if(!typeCheck(size).equals("double")) {
                return "error";
            }

            if (s.contains("string")) return "string[]";
            if (s.contains("double")) return "double[]";
            if (s.contains("boolean") || s.contains("bool")) return "bool[]";
            return "array";
        }
        if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false")) return "boolean";
        try {
            Double.parseDouble(s);
            return "double";
        } catch (NumberFormatException ignored) {}
        if (s.endsWith("\"")) return "string";
        return "var";
    }

    private void parseMethodDeclaration(String input, String className, Map<String, SymbolTable> scope, String scopeName,japyParser.StatementVarDefContext ctx) {
        String methodName = input.substring(0, input.indexOf("("));
        String paramsStr = input.substring(input.indexOf("(") + 1, input.length() - 1);
        String[] params = paramsStr.split(",");
        int length = params.length;
        if (params[0].isEmpty()) length = 0;
        ArrayList<Parameter> p;

        String s = className.substring(className.indexOf("_") + 1);
        p = classScopes.get(s).lookup("function_" + methodName).parametersList;


        if (length != p.size()) {
            errors.add("Error 199: in line [" + ctx.start.getLine() + ":" + ctx.start.getCharPositionInLine() + "], parameter count must be " + p.size() + " but got " + length);
            return;
        }

        for (int i = 0; i < length; i++) {
            String type = typeCheck(params[i]);
            String type1 = p.get(i).type;
            if (type.equals("boolean")) type = "bool";
            if (type.equals("var")) {
                type = scope.get(scopeName).lookup("var_" + params[i]).type;
                if (type.equals("boolean")) type = "bool";
            }
            if (!Objects.equals(type, type1)) {
                errors.add("Error 200: in line [" + ctx.start.getLine() + ":" + ctx.start.getCharPositionInLine()  + "], " + p.get(i).name + " type must be " + type1 + " but got " + type);
            }
        }
    }

    private String enterClassDeclarationHelper(japyParser.ClassDeclarationContext ctx) {
        String result = ctx.access_modifier() != null ? ", " + ctx.access_modifier().getText() : "";
        if (ctx.getText().contains("inherits")) {
            result += ", inherits '" + ctx.classParent.getText() + "'";
        }
        return result;
    }

    private String enterMethodDeclarationHelper(japyParser.MethodDeclarationContext ctx) {
        String result = ctx.methodAccessModifier != null ? ", " + ctx.methodAccessModifier.getText() : "";
        result += ", parameters: [" + parametersToString(ctx) + "]>";
        return result;
    }

    private String parametersToString(japyParser.MethodDeclarationContext ctx) {
        StringBuilder result = new StringBuilder();
        for (int i = 1; i < ctx.ID().size(); i++) {
            String s = ctx.japyType().get(i - 1).getText().replace("[]", "");
            result.append("(").append(ctx.ID().get(i).getText()).append(":").append(s).append(")");
            if (i != ctx.ID().size() - 1) {
                result.append(", ");
            }
        }
        return result.toString();
    }

    private String fieldToString(japyParser.FieldDeclarationContext ctx) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < ctx.ID().size(); i++) {
            result.append(ctx.ID(i).getText());
            if (i != ctx.ID().size() - 1) {
                result.append(", ");
            }
        }
        return result.toString();
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void enterEntryClassDeclaration(japyParser.EntryClassDeclarationContext ctx) {}
    @Override
    public void exitEntryClassDeclaration(japyParser.EntryClassDeclarationContext ctx) {}
    @Override
    public void exitFieldDeclaration(japyParser.FieldDeclarationContext ctx) {}
    @Override
    public void enterAccess_modifier(japyParser.Access_modifierContext ctx) {}
    @Override
    public void exitAccess_modifier(japyParser.Access_modifierContext ctx) {}
    @Override
    public void enterClosedStatement(japyParser.ClosedStatementContext ctx) {}
    @Override
    public void exitClosedStatement(japyParser.ClosedStatementContext ctx) {}
    @Override
    public void enterOpenStatement(japyParser.OpenStatementContext ctx) {}
    @Override
    public void exitOpenStatement(japyParser.OpenStatementContext ctx) {}
    @Override
    public void enterStatement(japyParser.StatementContext ctx) {}
    @Override
    public void exitStatement(japyParser.StatementContext ctx) {}
    @Override
    public void exitStatementVarDef(japyParser.StatementVarDefContext ctx) {}
    @Override
    public void enterStatementBlock(japyParser.StatementBlockContext ctx) {}
    @Override
    public void exitStatementBlock(japyParser.StatementBlockContext ctx) {}
    @Override
    public void exitStatementContinue(japyParser.StatementContinueContext ctx) {}
    @Override
    public void enterStatementBreak(japyParser.StatementBreakContext ctx) {}
    @Override
    public void enterStatementReturn(japyParser.StatementReturnContext ctx) {}
    @Override
    public void exitStatementReturn(japyParser.StatementReturnContext ctx) {}
    @Override
    public void enterStatementOpenLoop(japyParser.StatementOpenLoopContext ctx) {}
    @Override
    public void exitStatementOpenLoop(japyParser.StatementOpenLoopContext ctx) {}
    @Override
    public void enterStatementWrite(japyParser.StatementWriteContext ctx) {}
    @Override
    public void exitStatementWrite(japyParser.StatementWriteContext ctx) {}
    @Override
    public void exitStatementInc(japyParser.StatementIncContext ctx) {}
    @Override
    public void exitStatementAssignment(japyParser.StatementAssignmentContext ctx) {}
    @Override
    public void exitStatementDec(japyParser.StatementDecContext ctx) {}
    @Override
    public void enterExpression(japyParser.ExpressionContext ctx) {}
    @Override
    public void exitExpression(japyParser.ExpressionContext ctx) {}
    @Override
    public void enterExpressionOr(japyParser.ExpressionOrContext ctx) {}
    @Override
    public void exitExpressionOr(japyParser.ExpressionOrContext ctx) {}
    @Override
    public void enterExpressionOrTemp(japyParser.ExpressionOrTempContext ctx) {}
    @Override
    public void exitExpressionOrTemp(japyParser.ExpressionOrTempContext ctx) {}
    @Override
    public void enterExpressionAnd(japyParser.ExpressionAndContext ctx) {}
    @Override
    public void exitExpressionAnd(japyParser.ExpressionAndContext ctx) {}
    @Override
    public void enterExpressionAndTemp(japyParser.ExpressionAndTempContext ctx) {}
    @Override
    public void exitExpressionAndTemp(japyParser.ExpressionAndTempContext ctx) {}
    @Override
    public void enterExpressionEq(japyParser.ExpressionEqContext ctx) {}
    @Override
    public void exitExpressionEq(japyParser.ExpressionEqContext ctx) {}
    @Override
    public void enterExpressionEqTemp(japyParser.ExpressionEqTempContext ctx) {}
    @Override
    public void exitExpressionEqTemp(japyParser.ExpressionEqTempContext ctx) {}
    @Override
    public void enterExpressionCmp(japyParser.ExpressionCmpContext ctx) {}
    @Override
    public void exitExpressionCmp(japyParser.ExpressionCmpContext ctx) {}
    @Override
    public void enterExpressionCmpTemp(japyParser.ExpressionCmpTempContext ctx) {}
    @Override
    public void exitExpressionCmpTemp(japyParser.ExpressionCmpTempContext ctx) {}
    @Override
    public void enterExpressionAdd(japyParser.ExpressionAddContext ctx) {}
    @Override
    public void exitExpressionAdd(japyParser.ExpressionAddContext ctx) {}
    @Override
    public void enterExpressionAddTemp(japyParser.ExpressionAddTempContext ctx) {}
    @Override
    public void exitExpressionAddTemp(japyParser.ExpressionAddTempContext ctx) {}
    @Override
    public void enterExpressionMultMod(japyParser.ExpressionMultModContext ctx) {}
    @Override
    public void exitExpressionMultMod(japyParser.ExpressionMultModContext ctx) {}
    @Override
    public void enterExpressionMultModTemp(japyParser.ExpressionMultModTempContext ctx) {}
    @Override
    public void exitExpressionMultModTemp(japyParser.ExpressionMultModTempContext ctx) {}
    @Override
    public void enterExpressionUnary(japyParser.ExpressionUnaryContext ctx) {}
    @Override
    public void exitExpressionUnary(japyParser.ExpressionUnaryContext ctx) {}
    @Override
    public void enterExpressionMethods(japyParser.ExpressionMethodsContext ctx) {}
    @Override
    public void exitExpressionMethods(japyParser.ExpressionMethodsContext ctx) {}
    @Override
    public void enterExpressionMethodsTemp(japyParser.ExpressionMethodsTempContext ctx) {}
    @Override
    public void exitExpressionMethodsTemp(japyParser.ExpressionMethodsTempContext ctx) {}
    @Override
    public void enterExpressionOther(japyParser.ExpressionOtherContext ctx) {}
    @Override
    public void exitExpressionOther(japyParser.ExpressionOtherContext ctx) {}
    @Override
    public void enterJapyType(japyParser.JapyTypeContext ctx) {}
    @Override
    public void exitJapyType(japyParser.JapyTypeContext ctx) {}
    @Override
    public void enterSingleType(japyParser.SingleTypeContext ctx) {}
    @Override
    public void exitSingleType(japyParser.SingleTypeContext ctx) {}
    @Override
    public void visitErrorNode(ErrorNode errorNode) {}
    @Override
    public void enterEveryRule(ParserRuleContext parserRuleContext) {}
    @Override
    public void exitEveryRule(ParserRuleContext parserRuleContext) {}
}