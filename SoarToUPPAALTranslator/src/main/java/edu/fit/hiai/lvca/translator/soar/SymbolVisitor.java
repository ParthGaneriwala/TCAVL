package edu.fit.hiai.lvca.translator.soar;

import edu.fit.hiai.lvca.translator.gen.SoarBaseVisitor;
import edu.fit.hiai.lvca.translator.gen.SoarParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.*;
import java.util.stream.Collectors;


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
    private String nestedVariableName;
    private Map<String, Map<String, String>> globalVariableDictionary = new HashMap<>();
    private  Set<String> groupingVariableNames = new HashSet<>();   //holds variable names that are used for intermediate groupings with "{}"

    public SymbolVisitor(SoarParser.SoarContext ctx)
    {
        ctx.soar_production().forEach(sp -> sp.accept(this));
        //stringSymbols.addAll(workingMemoryTree.getAllPaths());

        workingMemoryStructure.forEach((prod_name,wmt) -> stringSymbols.addAll(wmt.getAllPaths()));

        //System.err.println("(SymbolVisitor) workingMemory tree: " + workingMemoryTree.getAllPaths());

        //booleanSymbols = booleanSymbols
        //        .stream()
        //       .map(attr -> workingMemoryTree.pathTo(attr))
        //        .collect(Collectors.toSet());


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

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitSoar(SoarParser.SoarContext ctx)
    {
        ctx.soar_production().forEach(p -> p.accept(this));
        //return workingMemoryTree;

        //CJ: not sure what this return is for as it is not reached when debugging with breakpoints on this line
        return currentWorkingMemoryTree;
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitSoar_production(SoarParser.Soar_productionContext ctx)
    {
        currentVariableDictionary = new HashMap<>();
        currentWorkingMemoryTree = getTree(ctx);

        ctx.condition_side().accept(this);
        ctx.action_side().accept(this);

        // globalVariableDictionary: production name -> variable id -> variable path

        Map<String, String> variablePaths = new HashMap<>();

        for (HashMap.Entry<String, String> varToValue : currentVariableDictionary.entrySet())
        {
            //if variable name is just a grouping, then do not traverse entire tree, but return just the variable name
            if(groupingVariableNames.contains(varToValue.getKey())){
                variablePaths.put(varToValue.getKey(), (varToValue.getValue()));
            }
            else{
                //variablePaths.put(entry.getKey(), workingMemoryTree.pathTo(entry.getValue()));
                variablePaths.put(varToValue.getKey(), currentWorkingMemoryTree.pathTo(varToValue));

            }
        }

        globalVariableDictionary.put(ctx.sym_constant().getText(), variablePaths);
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
        currentVariableDictionary.put(ctx.id_test().getText(), currentWorkingMemoryTree.name);
        //currentVariableDictionary.put(ctx.id_test().getText(), workingMemoryTree.name);

        //ctx.attr_value_tests().forEach(avt -> workingMemoryTree.addChild(avt.accept(this)));
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
            // avtTree is gotten(in a case where multiple avts have the same name) symbolTree.pathTo(Hashmap entry)
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

        //CJ: decompose attribute disjunctions here?
        edu.fit.hiai.lvca.translator.soar.SymbolTree subtree = getTreeFromList(ctx.attr_test());

        nestedVariableName = null;
        ctx.attr_test().forEach(at -> at.accept(this));
        ctx.value_test().forEach(vt -> vt.accept(this));

        if (nestedVariableName != null && !currentVariableDictionary.containsKey(nestedVariableName))
        {
            System.out.println("(visitAttr_value) before: currentVariableDictionary: " + currentVariableDictionary);
            currentVariableDictionary.put(nestedVariableName, getFirstLeaf(subtree));
            System.out.println("(visitAttr_value) after: currentVariableDictionary: " + currentVariableDictionary);
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
                    System.out.println("Grouping occurs: " + conjCtx.getText());
                    if (conjCtx.simple_test(0).getText() != null ){
                        contextText =  conjCtx.simple_test(0).getText();        //values for the group
                        groupingVariableNames.add(conjCtx.simple_test(1).getText());    //recognize that this variable is only used for grouping
                        //currentVariableDictionary.put(conjCtx.simple_test(1).getText(), contextText);
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
            System.out.println("(VisitTest) conjunction here: " + ctx.getText());
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
        System.out.println("This is the conjunctive test context: " + ctx.getText());

       /* if ((ctx.simple_test().size() == 2) && (ctx.simple_test(1).relational_test().relation() == null)){      //condition for when {} is used to group variables
            System.out.println("Grouping occurs: " + ctx.getText());
            String result = ctx.simple_test(0).getText();

            stringSymbols.add(ctx.simple_test(0).getText());
            System.out.println("String symbols " + stringSymbols);
            return new SymbolTree(result);
        }else {

        }*/
        ctx.simple_test().forEach(this::visitSimple_test);
        return null;

    }

    /*
    07/20/2022 Prep for implementing disjunction tests to translator
     */
    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitDisjunction_test(SoarParser.Disjunction_testContext ctx) {
        System.out.println("This is the disjunction test context: " + ctx.getText());
        ctx.constant().forEach(c -> c.accept(this));
        return null;
    }

    /*
    07/20/2022 Prep for implementing Multi-value tests
     */

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitMulti_value_test(SoarParser.Multi_value_testContext ctx) {
        System.out.println("This is the multi-value context: " + ctx.getText());
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

            ctx.attr_value_make().forEach(avm ->{
                edu.fit.hiai.lvca.translator.soar.SymbolTree avmTree = avm.accept(this);
                avmTree.value = avm.value_make().value().getText();
                attachPoint.addChild(avmTree);
            } );
        }
        return null;
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitValue(SoarParser.ValueContext ctx)
    {
        ParseTree node = ctx.children.get(0);
        //System.out.println("(Visit Value)CTX: "+ ctx.children.get(0).getText());


        if (node instanceof SoarParser.VariableContext)
        {
            nestedVariableName = node.getText();
            System.out.println("(visitValue) nestedVariableName: " + nestedVariableName);

            return null;
        }
        else
        {
            return node.accept(this);
        }
    }

    @Override
    public edu.fit.hiai.lvca.translator.soar.SymbolTree visitAttr_value_make(SoarParser.Attr_value_makeContext ctx)
    {
        edu.fit.hiai.lvca.translator.soar.SymbolTree subtree = getTreeFromList(ctx.variable_or_sym_constant());
        System.out.println("Sub: "+ ctx.value_make().getText());
        nestedVariableName = null;
        edu.fit.hiai.lvca.translator.soar.SymbolTree rightHandTree = ctx.value_make().accept(this);


        if (nestedVariableName != null)
        {
            if (rightHandTree == null)
            {
                if (!currentVariableDictionary.containsKey(nestedVariableName))
                {
                    currentVariableDictionary.put(nestedVariableName, subtree.name);
                }
            }
            else
            {
                ctx.value_make().accept(this).getChildren()
                        .forEach(subtree::addChild);
            }
        }
        List<edu.fit.hiai.lvca.translator.soar.SymbolTree> subtreeChildren =  subtree.getChildren();


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
}
