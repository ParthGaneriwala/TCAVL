package edu.fit.hiai.lvca.translator.soar;

import edu.fit.hiai.lvca.translator.gen.SoarBaseVisitor;
import edu.fit.hiai.lvca.translator.gen.SoarParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.*;


/**
 * Created by mstafford on 8/4/16.
 *
 * Get all identifiers used in the Soar agent
 */
class SymbolVisitor extends SoarBaseVisitor<edu.fit.hiai.lvca.translator.soar.SymbolTree>
{
    private Set<String> stringSymbols = new HashSet<>();
    private Set<String> booleanSymbols = new HashSet<>();
    private edu.fit.hiai.lvca.translator.soar.SymbolTree currentWorkingMemoryTree;

    private Map<String, edu.fit.hiai.lvca.translator.soar.SymbolTree> workingMemoryStructure = new HashMap<>();

    private Map<String, String> currentVariableDictionary;
    private Map<String, List<String>> currentDisjunctionMap;
    private String nestedVariableName;
    private final Map<String, Map<String, String>> globalVariableDictionary = new HashMap<>();
    private final Map<String, Map<String, List<String>>> globalDisjunctionDictionary = new HashMap<>();
    private Set<String> groupingVariableNames = new HashSet<>();   //holds variable names that are used for intermediate groupings with "{}"
    private Map<String, Set<String>> referenceMap = new HashMap<>();

    public SymbolVisitor(SoarParser.SoarContext ctx)
    {
        System.out.println("---------------------------------------------------------------------------------------");
        System.out.println("Running symbol visitor to get working memory tree, variables to constants");
        System.out.println("---------------------------------------------------------------------------------------");

        ctx.soar_production().forEach(sp -> sp.accept(this));

        workingMemoryStructure.forEach((prod_name,wmt) -> stringSymbols.addAll(wmt.getAllPaths()));

        HashSet<String> booleanPaths = new HashSet<>();
        booleanSymbols.forEach(attr -> {
            workingMemoryStructure.forEach((proName, wmt) -> {
                if ((wmt.pathTo(attr) != null)){
                    booleanPaths.add(wmt.pathTo(attr));
                }
            });
        });
        booleanSymbols = booleanPaths;


        stringSymbols.removeAll(booleanSymbols);
        stringSymbols.remove("true");
        stringSymbols.remove("false");
    }

    Set<String> getStringSymbols()
    {
        return stringSymbols;
    }

    edu.fit.hiai.lvca.translator.soar.SymbolTree getTree(SoarParser.Soar_productionContext ctx)
    {
        String prodName = ctx.sym_constant().getText();
        edu.fit.hiai.lvca.translator.soar.SymbolTree tree = workingMemoryStructure.get(prodName);
        if (tree == null){      //if the tree is not already created
            tree = new edu.fit.hiai.lvca.translator.soar.SymbolTree("State"); //create a new tree
            workingMemoryStructure.put(prodName, tree);
        }
        return tree;
    }

    Set<String> getBooleanSymbols()
    {
        return booleanSymbols;
    }

    Map<String, Map<String, String>> getGlobalVariableDictionary()
    {
        return globalVariableDictionary;
    }

    Map<String, Map<String, List<String>>> getGlobalDisjunctionDictionary() { return globalDisjunctionDictionary; }

    Map<String, Set<String>> getReferences() {return referenceMap; }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitSoar(SoarParser.SoarContext ctx)
    {

        ctx.soar_production().forEach(p -> p.accept(this));

        //CJ: not sure what this return is for as it is not reached when debugging with breakpoints on this line
        return currentWorkingMemoryTree;
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitSoar_production(SoarParser.Soar_productionContext ctx)
    {
        currentVariableDictionary = new HashMap<>();
        currentDisjunctionMap = new HashMap<>();
        currentWorkingMemoryTree = getTree(ctx);
        System.out.println("Symbol visiting production name " + ctx.sym_constant().getText());

        System.out.println("    Symbol visiting condition side");
        ctx.condition_side().accept(this);

        System.out.println("current variable dictionary: " + currentVariableDictionary.entrySet());

        System.out.println("    Symbol visiting action side");
        ctx.action_side().accept(this);

        // globalVariableDictionary: production name -> variable id -> variable path

        Map<String, String> variablePaths = new HashMap<>();

        //System.out.println("current variable dictionary: " + currentVariableDictionary.entrySet());
        for (HashMap.Entry<String, String> varToValue : currentVariableDictionary.entrySet()) {
            //System.out.println(varToValue.getKey() + " = " + varToValue.getValue());
            //System.out.println("vartoValue path: " + currentWorkingMemoryTree.pathTo(varToValue));
            //if variable name is just a grouping, then do not traverse entire tree, but return just the variable name
            if (groupingVariableNames.contains(varToValue.getKey())) {
                variablePaths.put(varToValue.getKey(), (varToValue.getValue()));
            } else {
                variablePaths.put(varToValue.getKey(), currentWorkingMemoryTree.pathTo(varToValue));

            }
        }
        globalVariableDictionary.put(ctx.sym_constant().getText(), variablePaths);
        System.out.println("current disjunction dictionary: " + currentDisjunctionMap);
        globalDisjunctionDictionary.put(ctx.sym_constant().getText(), currentDisjunctionMap);
        System.out.println("global disjunction dictionary: " + globalDisjunctionDictionary);
        System.out.println();
        return null;
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitCondition_side(SoarParser.Condition_sideContext ctx)
    {
        ctx.state_imp_cond().accept(this);
        ctx.cond().forEach(c -> c.accept(this));
        return null;
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitState_imp_cond(SoarParser.State_imp_condContext ctx)
    {
        //add current state variable to variable dictionary, eg <s> -> "State"
        currentVariableDictionary.put(ctx.id_test().getText(), currentWorkingMemoryTree.name);

        //add subsequent attribute value tests to the working memory tree of this rule
        ctx.attr_value_tests().forEach(avt -> currentWorkingMemoryTree.addChild(avt.accept(this)));

        return null;
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitCond(SoarParser.CondContext ctx)
    {
        ctx.positive_cond().accept(this);
        return null;
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitPositive_cond(SoarParser.Positive_condContext ctx)
    {
        ctx.conds_for_one_id().accept(this);
        ctx.cond().forEach(c -> c.accept(this));
        return null;
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitConds_for_one_id(SoarParser.Conds_for_one_idContext ctx)
    {
        edu.fit.hiai.lvca.translator.soar.SymbolTree attachPoint = ctx.id_test().accept(this);

        for(SoarParser.Attr_value_testsContext avt : ctx.attr_value_tests())
        {
            edu.fit.hiai.lvca.translator.soar.SymbolTree avtTree = avt.accept(this);

            //if a single value or disjunction, then value is used to validate that the right
            // avtTree is gotten (in a case where multiple avts have the same name) symbolTree.pathTo(Hashmap entry)
            if(avt.value_test().get(0).test().conjunctive_test() == null){
                avtTree.value = avt.value_test().get(0).getText();
            }
            attachPoint.addChild(avtTree);
        }

        return null;
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitId_test(SoarParser.Id_testContext ctx)
    {
        return ctx.test().accept(this);
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitAttr_value_tests(SoarParser.Attr_value_testsContext ctx)
    {
        System.out.println("        Symbol visiting avt " + ctx.getText());
        edu.fit.hiai.lvca.translator.soar.SymbolTree subtree = getTreeFromList(ctx.attr_test());

        nestedVariableName = null;
        ctx.attr_test().forEach(at -> at.accept(this));
        ctx.value_test().forEach(vt -> vt.accept(this));

        if (nestedVariableName != null && !currentVariableDictionary.containsKey(nestedVariableName))
        {
            currentVariableDictionary.put(nestedVariableName, getFirstLeaf(subtree));
        }

        if (ctx.value_test().size() > 0 &&
                (  ctx.value_test(0).getText().equals("true")
                        || ctx.value_test(0).getText().equals("false")))
        {
            booleanSymbols.add(subtree.name);
        }
        return subtree;
    }

    private String getFirstLeaf(edu.fit.hiai.lvca.translator.soar.SymbolTree subtree)
    {
        edu.fit.hiai.lvca.translator.soar.SymbolTree t = subtree;
        while (t.getChildren().size() > 0)
        {
            t = t.getChildren().get(0);
        }
        return t.name;
    }

    private edu.fit.hiai.lvca.translator.soar.SymbolTree getTreeFromList(List<? extends ParserRuleContext> ctxs)
    {
        if (ctxs.size() == 1)
        {

            String contextText = ctxs.get(0).getText();

            if (contextText.startsWith("{")){
                //attribute is either a conjunction or a grouping
                SoarParser.Attr_testContext attrTestCtx = (SoarParser.Attr_testContext) ctxs.get(0);
                SoarParser.Conjunctive_testContext conjCtx = attrTestCtx.test().conjunctive_test();
                if ((conjCtx.simple_test().size() == 2) && (conjCtx.simple_test(1).relational_test().relation() == null)) {      //condition for when {} is used to group variables
                    if (conjCtx.simple_test(0).getText() != null ){

                        contextText =  conjCtx.simple_test(0).getText();        //assign values for the group to contextText
                        System.out.println("        Grouping of " + contextText + " to variable " + conjCtx.simple_test(1).getText());
                        groupingVariableNames.add(conjCtx.simple_test(1).getText());    //recognize that this variable is only used for grouping
                    }

                }
            }
            else if(contextText.matches("<[A-Za-z]+>")){
                //if it is a variable, get the constant equivalent of the variable
                if (currentVariableDictionary.get(contextText) != null){
                    contextText = currentVariableDictionary.get(contextText);

                }

            }
            return new edu.fit.hiai.lvca.translator.soar.SymbolTree(contextText);
        }
        else
        {
            edu.fit.hiai.lvca.translator.soar.SymbolTree t = new edu.fit.hiai.lvca.translator.soar.SymbolTree(ctxs.get(0).getText());
            t.addChild(getTreeFromList(ctxs.subList(1, ctxs.size())));
            return t;
        }
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitAttr_test(SoarParser.Attr_testContext ctx)
    {

        return ctx.test().accept(this);
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitValue_test(SoarParser.Value_testContext ctx)
    {
        ctx.test().accept(this);
        return null;
    }

    /*
    07/13/2022 Changed visitTest to actually use conjunctive tests when present
    07/20/2022 Added structure to check for and use disjunction tests and multi-value tests
     */
    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitTest(SoarParser.TestContext ctx)
    {

        if (ctx.getText().startsWith("{") && ctx.getText().endsWith("}")) {
            return ctx.conjunctive_test().accept(this);
        }else if (ctx.getText().startsWith("<<") && ctx.getText().endsWith(">>")) {
            return ctx.simple_test().disjunction_test().accept(this);
        }
        else if (ctx.getText().startsWith("[") && ctx.getText().endsWith("]"))
            return ctx.multi_value_test().accept(this);
        else return ctx.simple_test().accept(this);
    }

    /*
    07/21/2022 Modified to handle disjunction tests
     */
    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitSimple_test(SoarParser.Simple_testContext ctx)
    {

        if (ctx.disjunction_test() != null)
            return ctx.disjunction_test().accept(this);
        else return ctx.relational_test().accept(this);
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitRelational_test(SoarParser.Relational_testContext ctx)
    {

        return ctx.single_test().accept(this);
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitSingle_test(SoarParser.Single_testContext ctx)
    {
        return ctx.children.get(0).accept(this);
    }

    /*
    07/13/2022 Begin implementing conjunctive (AND) tests to translator output
     */
    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitConjunctive_test(SoarParser.Conjunctive_testContext ctx) {

        ctx.simple_test().forEach(this::visitSimple_test);
        return null;

    }

    /*
    07/20/2022 Prep for implementing disjunction tests to translator
     */
    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitDisjunction_test(SoarParser.Disjunction_testContext ctx) {
        List<String> possibleConstants = new LinkedList<>();
        ctx.constant().forEach(c -> possibleConstants.add(c.getText()));
        currentDisjunctionMap.put(ctx.getText(), possibleConstants);
        ctx.constant().forEach(c -> c.accept(this));
        return null;
    }

    /*
    07/20/2022 Prep for implementing Multi-value tests
     */

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitMulti_value_test(SoarParser.Multi_value_testContext ctx) {
        ctx.Int_constant().forEach(c -> c.accept(this));
        return null;
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitVariable(SoarParser.VariableContext ctx)
    {
        nestedVariableName = ctx.getText();

        try
        {
            String variableName = currentVariableDictionary.get(nestedVariableName);
            if (variableName != null)
            {
                return currentWorkingMemoryTree.getSubtree(variableName);


            }
        }
        catch (NoSuchElementException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitConstant(SoarParser.ConstantContext ctx)
    {
        String result = ctx.getText();

        if (ctx.sym_constant() != null)
        {
            stringSymbols.add(ctx.getText());
        }
        else if (ctx.Print_string() != null)
        {
            result = edu.fit.hiai.lvca.translator.soar.UPPAALCreator.LITERAL_STRING_PREFIX + ctx.Print_string().getText().split("|")[1];
            stringSymbols.add(result);
        }
        return new edu.fit.hiai.lvca.translator.soar.SymbolTree(result);
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitAction_side(SoarParser.Action_sideContext ctx)
    {
        ctx.action().forEach(a -> a.accept(this));
        return null;
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitAction(SoarParser.ActionContext ctx)
    {
        if (ctx.attr_value_make() != null && ctx.variable() != null)
        {
            edu.fit.hiai.lvca.translator.soar.SymbolTree attachPoint = ctx.variable().accept(this);
            System.out.println("        Action attach point is at " + attachPoint.name);
            ctx.attr_value_make().forEach(avm ->{
                edu.fit.hiai.lvca.translator.soar.SymbolTree avmTree = avm.accept(this, attachPoint.name);
                //System.out.println("            Value is " + avm.value_make().value().getText());
                avmTree.value = avm.value_make().value().getText();
                attachPoint.addChild(avmTree);
            } );
        }
        return null;
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitValue(SoarParser.ValueContext ctx)
    {
        //System.out.println("visitValue: " + ctx.getText());
        ParseTree node = ctx.children.get(0);


        if (node instanceof SoarParser.VariableContext)
        {
            nestedVariableName = node.getText();
            return null;
        }
        else
        {
            return node.accept(this);
        }
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitAttr_value_make(SoarParser.Attr_value_makeContext ctx, String varName)
    {
        System.out.println("------------visitAVM: " + ctx.getText());
        edu.fit.hiai.lvca.translator.soar.SymbolTree subtree = getTreeFromList(ctx.variable_or_sym_constant());
        nestedVariableName = null;
        edu.fit.hiai.lvca.translator.soar.SymbolTree rightHandTree = ctx.value_make().accept(this);

        //System.out.println("            nestedVariableName: " + nestedVariableName);
        if (nestedVariableName != null)
        {
            if (rightHandTree == null)
            {
                //System.out.println("            current var dic: " + currentVariableDictionary.entrySet());
                //System.out.print("            testing: " + nestedVariableName);
                if (!currentVariableDictionary.containsKey(nestedVariableName))
                {
                    //System.out.println("    var not in dictionary");
                    currentVariableDictionary.put(nestedVariableName, subtree.name);
                    //System.out.println("            added: " + nestedVariableName + "->" + subtree.name);
                }
                else
                {
                    // A reference was detected
                    System.out.println("*********** reference detected at: " + nestedVariableName);
                    // Determines what the reference label is (key)
                    String refKey = currentWorkingMemoryTree.pathTo(varName);
                    for (SoarParser.Variable_or_sym_constantContext a : ctx.variable_or_sym_constant()) {
                        String currentAttr = a.getText();
                        if (currentAttr.contains("<") && currentAttr.contains(">")) { currentAttr = currentVariableDictionary.get(currentAttr); }
                        refKey += "_" + currentAttr;
                    }
                    // Determines what the reference refers to (value)
                    String refValue = currentWorkingMemoryTree.pathTo(currentVariableDictionary.get(nestedVariableName));
                    // Calls the method to store the reference
                    System.out.println("                refKey: " + refKey);
                    System.out.println("                refValue: " + refValue);
                    expandAndStoreReferences(refKey, refValue);
                }
            }
            else
            {
                //System.out.println("            rightHandTree: " + rightHandTree.name);
                ctx.value_make().accept(this).getChildren()
                        .forEach(subtree::addChild);
            }
        }
        List<edu.fit.hiai.lvca.translator.soar.SymbolTree> subtreeChildren =  subtree.getChildren();

        //System.out.println(currentVariableDictionary);
        return subtree;
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitVariable_or_sym_constant(SoarParser.Variable_or_sym_constantContext ctx)
    {
        return new edu.fit.hiai.lvca.translator.soar.SymbolTree(ctx.getText());
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitValue_make(SoarParser.Value_makeContext ctx)
    {
        return ctx.value().accept(this);
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree defaultResult()
    {
        return null;
    }

    // Expands all the common disjunctions between the reference key-value pair and passes them to the reference storage algorithm
    private void expandAndStoreReferences(String refKey, String refValue)
    {
        Queue<String> expandedKeys = new LinkedList<>();
        Queue<String> expandedValues = new LinkedList<>();

        Queue<String> disjunctionKeys = new LinkedList<>();
        Queue<String> disjunctionValues = new LinkedList<>();
        disjunctionKeys.add(refKey);
        disjunctionValues.add(refValue);
        while (!disjunctionKeys.isEmpty() && !disjunctionValues.isEmpty()) {
            String currentKey = disjunctionKeys.remove();
            String currentValue = disjunctionValues.remove();
            if (currentKey.contains("<<") && currentKey.contains(">>")) {
                String disjunction = currentKey.substring(currentKey.indexOf("<<"), currentKey.indexOf(">>") + 2);
                if (currentValue.contains(disjunction)) {
                    if (currentDisjunctionMap.containsKey(disjunction)) {
                        for (String constant : currentDisjunctionMap.get(disjunction)) {
                            disjunctionKeys.add(currentKey.replace(disjunction, constant));
                            disjunctionValues.add(currentValue.replace(disjunction, constant));
                        }
                    } else {
                        throw new NoSuchElementException("Could not find values for disjunction: " + disjunction);
                    }
                }
            } else {
                expandedKeys.add(currentKey);
                expandedValues.add(currentValue);
            }
        }
        while (!expandedKeys.isEmpty() && !expandedValues.isEmpty()) {
            storeReferences(expandedKeys.remove(), expandedValues.remove());
        }
    }

    // Stores the specified references and expands any remaining disjunctions
    private void storeReferences (String refKey, String refValue)
    {
        Set<String> allLocalKeys = unravelDisjunctions(refKey);
        Set<String> allLocalValues = unravelDisjunctions(refValue);
        for (String key : allLocalKeys) {

            // If the specified key is already in the HashMap, then add the new values to the set of existing ones
            if (referenceMap.containsKey(key)) {
                Set<String> updatedValues = referenceMap.get(key);
                updatedValues.addAll(allLocalValues);
                referenceMap.replace(key, updatedValues);

            // Else create a new element in the HashMap
            } else {
                referenceMap.put(key, allLocalValues);
            }
        }
    }

    private Set<String> unravelDisjunctions(String variable) {
        Set<String> expandedStrings = new HashSet<>();
        Queue<String> variables = new LinkedList<>();
        variables.add(variable);
        while (!variables.isEmpty()) {
            String current = variables.remove();
            if (current.contains("<<") && current.contains(">>")) {
                String disjunction = current.substring(current.indexOf("<<"), current.indexOf(">>") + 2);
                if (currentDisjunctionMap.containsKey(disjunction)) {
                    for (String constant : currentDisjunctionMap.get(disjunction)) {
                        variables.add(current.replace(disjunction, constant));
                    }
                } else {
                    throw new NoSuchElementException("Could not find values for disjunction: " + disjunction);
                }
            } else {
                expandedStrings.add(current);
            }
        }
        return expandedStrings;
    }
}
