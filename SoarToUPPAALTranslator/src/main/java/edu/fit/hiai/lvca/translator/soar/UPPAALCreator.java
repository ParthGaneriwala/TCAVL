package edu.fit.hiai.lvca.translator.soar;

import edu.fit.hiai.lvca.translator.gen.SoarBaseVisitor;
import edu.fit.hiai.lvca.translator.gen.SoarParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.tree.DefaultDocumentType;
import org.dom4j.tree.DefaultElement;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by mstafford on 8/10/16.
 *
 * Generate a UPPAAL project with corresponding templates for each Soar production.
 */



public class UPPAALCreator extends SoarBaseVisitor<Element>
{

    static final String LITERAL_STRING_PREFIX = "literal_string__";
    record CreatorEdge(List<Guard> guards, List<Update> updates) {}
    record Guard(String leftTerm, String symbol, String rightTerm) {
        @Override
        public String toString() { return leftTerm + " " + symbol + " " + rightTerm; }
    }
    record Update(String leftTerm, String rightTerm) {
        @Override
        public String toString() { return leftTerm + " = " + rightTerm; }
    }

    private class UPPAALElementWithXY extends DefaultElement
    {

        UPPAALElementWithXY(String s)
        {
            super(s);
            List<String> XY_coordinates = getNextXYPosition();
            this.addAttribute("x", XY_coordinates.get(0));
            this.addAttribute("y", XY_coordinates.get(1));

        }

    }

    private final Set<String> _booleanGlobals;
    private final Map<String, Map<String, String>> _variableDictionary;
    private final Document _doc = DocumentHelper.createDocument();
    private final Set<String> _templateNames = new HashSet<>();
    private Integer _locationCounter = 0;
    private Integer _x_axis = 0;
    private List<Integer> _y_axis_values = List.of(0, 100, -100);
    private Integer _y_axis_index = 0;
    private final Set<String> _globals;
    private HashMap<String, List<String>> _referenceMap = new HashMap<>();
    private final List<CreatorEdge> _edges = new LinkedList<>();
    private SoarParser.Soar_productionContext _goalProductionContext;

    public UPPAALCreator(Set<String> stringAttributeNames, SoarParser.SoarContext soar, Map<String, Map<String, String>> variablesPerProductionContext, Set<String> boolAttributeNames, HashMap<String, List<String>> referenceMap)
    {

        System.out.println("\n\n---------------------------------------------------------------------------------------");
        System.out.println("Running UPPAAL Creator to create uppaal elements and construct system with them");
        System.out.println("---------------------------------------------------------------------------------------");
        _globals = stringAttributeNames;
        _booleanGlobals = boolAttributeNames;
        _doc.setXMLEncoding("utf-8");
        _doc.setDocType(new DefaultDocumentType("nta", "-//Uppaal Team//DTD Flat System 1.1//EN", "http://www.it.uu.se/research/group/darts/uppaal/flat-1_2.dtd"));
        _variableDictionary = variablesPerProductionContext;
        _referenceMap = referenceMap;
        _doc.setRootElement(soar.accept(this));
    }

    public String getXML()
    {
        return _doc.asXML();
    }

    private Element getDeclarationElement()
    {
        Element decl = new DefaultElement("declaration");

        _globals.remove("nil"); // added later so that nil always equals 0

        String vars = "";

        //starting atomicInteger at 2 cause 0 is reserved for "nil",
        // and 1 is reserved for "proposed" in the context of operator proposal
        final AtomicInteger i = new AtomicInteger(2);

        vars += _globals
                .stream()
                .filter(var -> var.startsWith("State"))
                .map(this::simplifiedString)
                .map(var -> "int " + var + "; \n")
                .collect(Collectors.joining());

        vars += _booleanGlobals
                .stream()
                .map(this::simplifiedString)
                .map(var -> "bool "+ var + "; \n")
                .collect(Collectors.joining());

        vars += "const int nil = 0;\n";

        vars += _globals
                .stream()
                .filter(var -> !var.startsWith("State"))
                .map(this::simplifiedString)
                .map(var -> "const int " + var + " = " + i.getAndIncrement() + "; \n")
                .collect(Collectors.joining());

        //add in unique declarations for each global variable
        vars += _globals
                .stream()
                .filter(var -> var.startsWith("State"))
                .map(this::simplifiedString)
                .map(var -> "const int init_" + var + " = " + i.getAndIncrement() + "; \n" )
                .collect(Collectors.joining());


        decl.addText("\n" + vars);
        decl.addText("broadcast chan Run_Rule;\n");

        return decl;
    }

    private Element getScheduler()
    {
        resetXYPosition();          //reset the XY coordinates of node positioning to 0,0 when creating a new template
        Element template = new DefaultElement("template");
        template.add(new DefaultElement("name").addText("scheduler"));

        String checkID = getCounter();
        String runID = getCounter();
        String startID = getCounter();

        template.add(getLocationWithNameElement(checkID, "Check"));

        Element runLocation = getLocationWithNameElement(runID, "Run");
        runLocation.add(new DefaultElement("committed"));
        template.add(runLocation);

        template.add(getLocationWithNameElement(startID, "Start"));
        template.add(new DefaultElement("init").addAttribute("ref", "id" + startID));
        template.add(getTransitionWithText(checkID, runID, "synchronisation", "Run_Rule!"));
        template.add(getTransitionWithText(runID, checkID, "guard", "!(" + _goalProductionContext.condition_side().accept(this).getText() + ")"));
        template.add(getTransitionWithText(startID, runID, "synchronisation", "Run_Rule!"));

        return template;
    }

    private String getCounter()
    {
        String i = _locationCounter.toString();
        _locationCounter++;
        return i;
    }

    private List<String> getNextXYPosition(){

        String current_x = String.valueOf(_x_axis);
        _x_axis += 100;

        String current_y = String.valueOf(_y_axis_values.get(_y_axis_index));
        _y_axis_index = (_y_axis_index + 1) % _y_axis_values.size();

        return List.of(current_x, current_y);
    }
    private void resetXYPosition(){
        _x_axis = 0;
        _y_axis_index = 0;
    }

    private Element getLocationWithNameElement(String num, String nameText)
    {
        Element runLocation = new UPPAALElementWithXY("location").addAttribute("id", "id" + num);
        //Use same position for location names as well as locations
        _x_axis -= 100;
        _y_axis_index = (_y_axis_index +(_y_axis_values.size() - 1)) % _y_axis_values.size();
        Element runName = new UPPAALElementWithXY("name").addText(nameText);

        runLocation.add(runName);

        return runLocation;
    }

    private Element getTransitionWithText(String sourceID, String targetID, String kind, String text)
    {
        resetXYPosition();
        Element startToRun = new DefaultElement("transition");

        Element source = new DefaultElement("source").addAttribute("ref", "id" + sourceID);

        Element target = new DefaultElement("target").addAttribute("ref", "id" + targetID);

        Element label = new UPPAALElementWithXY("label").addAttribute("kind", kind).addText(text);

        startToRun.add(source);
        startToRun.add(target);
        startToRun.add(label);

        return startToRun;
    }

    private List<Element> getAttrValueTestWithDisjunctions (SoarParser.Soar_productionContext ctx){

        List<Element> attrValueTestWithDisjunctions = new LinkedList<>();

        for ( SoarParser.Attr_value_testsContext attrTest : ctx.condition_side().state_imp_cond().attr_value_tests()) {
            Element attributeElement = attrTest.accept(this);
            //if the attribute test involves multiple attributes (assuming a disjunction of attributes)
            if (attributeElement.attributeValue("size") != null) {
                attrValueTestWithDisjunctions.add(attributeElement);
            }
        }

        ctx.condition_side().cond().forEach(condContext ->{
            for (SoarParser.Attr_value_testsContext attrTest : condContext.positive_cond().conds_for_one_id().attr_value_tests()){
                Element attributeElement = attrTest.accept(this);
                //if the attribute test involves multiple attributes (assuming a disjunction of attributes)
                if (attributeElement.attributeValue("size") != null) {
                    attrValueTestWithDisjunctions.add(attributeElement);
                }
            }

        });

        return attrValueTestWithDisjunctions;
    }
    private  List<Element> getAllSTartToRunTransitions (String runStateID, String startStateID, SoarParser.Soar_productionContext ctx){

        List<Element> startToRuns = new LinkedList<>();

        List<String> replacedGuardStrings = new LinkedList<>();
        List<String> replacedUpdateStrings = new LinkedList<>();

        String productionName = ctx.sym_constant().getText();
        Map<String, String> localVariableDictionary = _variableDictionary.get(productionName);
        //System.out.println("Printing all: " + _variableDictionary);
        //System.out.println("Printing locals: " + localVariableDictionary);

        String id = ctx.condition_side().state_imp_cond().id_test().getText();
        //System.out.println("    Printing id: " + id);

        String basicGuards = ctx.condition_side().accept(this).getText();
        String stateDisjunctionGuards =  innerVisitConditionVisit(ctx.condition_side().state_imp_cond().attr_value_tests(), localVariableDictionary, id, true);
        String condDisjunctionGuards = ctx.condition_side().cond().stream().map(c -> innerVisitConditionVisit(c.positive_cond().conds_for_one_id().attr_value_tests(), localVariableDictionary, c.positive_cond().conds_for_one_id().id_test().getText(), true))
                .filter(c -> c != null && !c.equals(""))
                .collect(Collectors.joining(" && "));
        //System.out.println("        Basic Guards: " + basicGuards);
        //System.out.println("        State Dis Guards: " + stateDisjunctionGuards);
        //System.out.println("        Cond Dis Guards: " + condDisjunctionGuards);


        if (stateDisjunctionGuards.isEmpty() && condDisjunctionGuards.isEmpty()) {
            replacedGuardStrings.add(basicGuards);
        } else if (stateDisjunctionGuards.isEmpty()) {
            replacedGuardStrings.add(basicGuards + " && " + condDisjunctionGuards);
        } else if (condDisjunctionGuards.isEmpty()) {
            replacedGuardStrings.add(basicGuards + " && " +stateDisjunctionGuards);
        } else {
            replacedGuardStrings.add(basicGuards + " && " + stateDisjunctionGuards + " && " + condDisjunctionGuards);
        }

        String baseUpdateStatement = ctx.action_side().accept(this).getText();
        //System.out.println("        Printing baseUpdateStatement: " + baseUpdateStatement);
        replacedUpdateStrings.add(baseUpdateStatement);
        //System.out.println("        basic guard strings: " + replacedGuardStrings);
        //System.out.println("        basic update strings: " + replacedUpdateStrings);

        //stack implementation to get all combination of attribute disjunction values
        List<Element> attrValueTestWithDisjunctions = getAttrValueTestWithDisjunctions(ctx);
        //System.out.println();
        //System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
        //System.out.println("Recursive trace of " + productionName);
        for (Element attrElement : attrValueTestWithDisjunctions){
            System.out.println("    Entering for loop for attr: " + attrElement.getText());
            List<String> replacedGuardsPopped = new LinkedList<>();           //stores the intermediate guard results after each attribute element replacement
            List<String> replacedUpdatesPopped = new LinkedList<>();           //stores the intermediate updates results after each attribute element replacement

            while (!replacedGuardStrings.isEmpty()){
                String guardString = replacedGuardStrings.remove(0);
                String updateString = replacedUpdateStrings.remove(0);
                //System.out.println("        Entering while loop");
                //System.out.println("        guardString: " + guardString);
                //System.out.println("        updateString: " + updateString);
                int size = Integer.parseInt(attrElement.attributeValue("size"));
                for (int j = 0; j < size; j++){
                    //replace the allValues string present in the guard with one value
                    replacedGuardsPopped.add(guardString.replace(attrElement.attributeValue("allValues"), attrElement.attributeValue("const"+j)));
                    replacedUpdatesPopped.add(updateString.replace(attrElement.attributeValue("allValues"), attrElement.attributeValue("const"+j)));
                    //System.out.println("            Adding guard: " + guardString.replace(attrElement.attributeValue("allValues"), attrElement.attributeValue("const"+j)));
                    //System.out.println("            Adding update: " + updateString.replace(attrElement.attributeValue("allValues"), attrElement.attributeValue("const"+j)));
                }
            }
            //System.out.println("    Exiting while loop");
            //System.out.println("        updates popped: " + replacedUpdatesPopped);
            //System.out.println("        guards popped: " + replacedGuardsPopped);
            replacedUpdateStrings.addAll(replacedUpdatesPopped);
            replacedGuardStrings.addAll(replacedGuardsPopped);
        }
        //System.out.println("Exiting for loop");
        //System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");

        // Organizes all the StartToRun transitions
        for (int i = 0; i<replacedGuardStrings.size(); i++){
            String[] individualGuards = replacedGuardStrings.get(i).split(" && ");
            String[] individualUpdates = replacedUpdateStrings.get(i).split(", ");
            List<Guard> localEdgeGuards = new LinkedList<>();
            List<Update> localEdgeUpdates = new LinkedList<>();

            for (String individualGuard : individualGuards) {
                String[] guardParts = individualGuard.split(" ");
                localEdgeGuards.add(new Guard(guardParts[0], guardParts[1], guardParts[2]));
            }
            for (String individualUpdate : individualUpdates) {
                String[] updateParts = individualUpdate.split(" = ");
                System.out.println("updateParts: " + individualUpdate);
                if (updateParts.length > 1) { localEdgeUpdates.add(new Update(updateParts[0], updateParts[1])); }
            }
            _edges.add(new CreatorEdge(localEdgeGuards, localEdgeUpdates));
        }

        // Checks if any of the variables in guards or updates contain references
        for(CreatorEdge creatorEdge : _edges){
            // Call reference checking algorithm
        }

        for (CreatorEdge creatorEdge : _edges){

            Element startToRunElement = new DefaultElement("transition");
            Element source = new DefaultElement("source").addAttribute("ref", "id" + startStateID);
            Element target = new DefaultElement("target").addAttribute("ref", "id" + runStateID);
            startToRunElement.add(source);
            startToRunElement.add(target);

            Element guard = new UPPAALElementWithXY("label")
                    .addAttribute("kind", "guard")
                    .addText(String.join(" && ", creatorEdge.guards().stream().map(Guard::toString).collect(Collectors.joining())));

            startToRunElement.add(guard);

            Element update = new UPPAALElementWithXY("label")
                    .addAttribute("kind", "assignment")
                    .addText(String.join(", ", creatorEdge.updates().stream().map(Update::toString).collect(Collectors.joining())));

            startToRunElement.add(update);


            // add run rule sync label
            Element syncLabel = new UPPAALElementWithXY("label").addAttribute("kind", "synchronisation").addText("Run_Rule?");
            startToRunElement.add(syncLabel);

            startToRuns.add((startToRunElement));

        }

        return startToRuns;
    }

    private Element getSystemElement()
    {
        final Element system = new DefaultElement("system").addText("\n");
        List<String[]> compoundNames = _templateNames.stream().map(name -> new String[]{name + "_0", name}).collect(Collectors.toList());
        String goalTemplateName = simplifiedString(_goalProductionContext.sym_constant().getText());

        system.addText(compoundNames.stream().map(name -> name[0] + " = " + name[1] + "(); \n").collect(Collectors.joining()));
        system.addText("schd = scheduler();\n");
        system.addText("goal = " + goalTemplateName + "(); \n");

        String instantiation = "system " + compoundNames.stream().map(cName -> cName[0]).collect(Collectors.joining(", ")) + ", goal, schd;";

        system.addText(instantiation);
        return system;
    }

    private Element getNameElement(String text)
    {
        return new UPPAALElementWithXY("name").addText(simplifiedString(text));
    }

    //Function to remove variables with <<>> and replace them with each disjunction having its own variable
    //Eg. State_<<trackfield>> is expanded to State_track and State_field
    private void expandDisjunctionStringVariables(SoarParser.Soar_productionContext ctx){
        List<Element>  attrValueTestWithDisjunctions = getAttrValueTestWithDisjunctions(ctx);
        List<String> newlyExpandedStrings = new LinkedList<>();

        //System.out.println();
        //System.out.println("current globals: " + _globals);
        Iterator<String> iterator = _globals.iterator();
        while (iterator.hasNext()) {
            String variable = iterator.next();
            //System.out.println("    checking this variable for disjunctions: " + variable);

            if (variable.contains("<<")){    //Check current variable contains a disjunction
                //System.out.println("        variable contains disjunction(s):");
                Queue<String> variables = new LinkedList<>();
                variables.add(variable);

                for (Element avt : attrValueTestWithDisjunctions) {  //Check disjunction exists in the Soar file
                    //System.out.println("        " + avt.attributeValue("allValues"));
                    if (variable.contains(avt.attributeValue("allValues"))){
                       // System.out.println("            true");
                        Queue<String> expandedStrings = new LinkedList<>();

                        //replace with expanded list of disjunction options
                        while (!variables.isEmpty()){
                            String v = variables.remove();
                            int size = Integer.parseInt(avt.attributeValue("size"));
                            for (int i = 0; i < size; i++) {
                                String expanded = v.replace(avt.attributeValue("allValues"), avt.attributeValue("const" + i));
                                //System.out.println("                new interm string: " + expanded);
                                expandedStrings.add(expanded);
                            }
                        }
                        variables.addAll(expandedStrings);

                        //replace with expanded list of disjunction options
                        //int size = Integer.parseInt(avt.attributeValue("size"));
                        //for (int i = 0; i < size; i++){
                        //    String expandedString = variable.replace(avt.attributeValue("allValues"), avt.attributeValue("const"+i));
                        //    System.out.println("                new string: " + expandedString);
                        //    newlyExpandedStrings.add(expandedString);
                        //}
                    }
                }
                //System.out.println("Variables to be added: " + variables);
                newlyExpandedStrings.addAll(variables);
                iterator.remove(); // Safe removal of the element
            }
        }
        //System.out.println("newlyExpandedStrings: " + newlyExpandedStrings);
        _globals.addAll(newlyExpandedStrings);
        //System.out.println("Updated globals: " + _globals);
        //System.out.println();
    }

    @Override
    public Element visitSoar(SoarParser.SoarContext ctx)
    {
        ctx.soar_production().forEach(sp -> sp.accept(this));

        //System.out.println("Expanding all disjunctions");
        ctx.soar_production().forEach(this::expandDisjunctionStringVariables);

        final Element nta = new DefaultElement("nta");
        nta.add(getDeclarationElement());

        // Add template object for each production
        ctx.soar_production().stream().map(sp -> sp.accept(this)).filter(sp -> sp != null).forEach(nta::add);

        // Add scheduler to call 'Run Rule'
        nta.add(getScheduler());

        // Add system tag to instantiate all rules
        nta.add(getSystemElement());

        return nta;
    }

    @Override
    public Element visitSoar_production(SoarParser.Soar_productionContext ctx)
    {
        System.out.println();
        System.out.println("Enterring Soar Production: " + ctx.sym_constant().getText());
        resetXYPosition();
        System.out.println("References: " + _referenceMap);
        if (ctx.getText().contains("(halt)"))
        {
            _goalProductionContext = ctx;
        }

        String runStateID = getCounter();
        String startStateID = getCounter();

        Element productionElement = new DefaultElement("template");
        Element templateElement = getNameElement(ctx.sym_constant().getText());
        _templateNames.add(templateElement.getText());
        productionElement.add(templateElement);
        productionElement.add(new DefaultElement("declaration"));

        productionElement.add(getLocationWithNameElement(runStateID, "Run"));

        Element startState = getLocationWithNameElement(startStateID, "Start");
        startState.add(new DefaultElement("committed"));
        productionElement.add(startState);

        Element init = new DefaultElement("init");
        init.addAttribute("ref", "id" + startStateID);
        productionElement.add(init);

        productionElement.add(getTransitionWithText(runStateID, startStateID, "synchronisation", "Run_Rule?"));

        List<Element> transitions = getAllSTartToRunTransitions(runStateID, startStateID, ctx);
        for (Element transition : transitions) {
            productionElement.add(transition);
        }

        return productionElement;
    }

    @Override
    public Element visitFlags(SoarParser.FlagsContext ctx)
    {
        return null;
    }

    @Override
    public Element visitCondition_side(SoarParser.Condition_sideContext ctx)
    {
        System.out.println("    Entering Condition side");
        // get guards from conditions
        List<String> guards = new LinkedList<>();
        guards.add(ctx.state_imp_cond().accept(this).getText());
        guards.addAll(ctx.cond().stream().map(c -> c.accept(this).getText()).collect(Collectors.toList()));
        Element conditions = new DefaultElement("");

        conditions.addText(guards.stream().filter(g -> !g.equals("")).collect(Collectors.joining(" && ")));
        return conditions;

    }

    /**
     * Gets dependencies and wraps result of inner visit
     *
     * @param condCtx
     * @return
     */
    @Override
    public Element visitState_imp_cond(SoarParser.State_imp_condContext condCtx)
    {
        String productionName = ((SoarParser.Soar_productionContext) condCtx.parent.parent).sym_constant().getText();
        String idTest = condCtx.id_test().getText();
        System.out.println("        Entering State conditions for " + idTest);
        Map<String, String> localVariableDictionary = _variableDictionary.get(productionName);

        return new DefaultElement("").addText(innerVisitConditionVisit(condCtx.attr_value_tests(), localVariableDictionary, idTest, false));
    }

    /*
    07/5/2024 Added booolean attributeDisjunction parameter to be able to separate between
     getting the basic guards and those with attribute disjunctions, as all other guards need to be iterated over those
     */
    private String innerVisitConditionVisit(List<SoarParser.Attr_value_testsContext> attrValueTestsCtxs, Map<String, String> localVariableDictionary, String idTest, boolean attributeDisjunctions)
    {

        List<String> stateVariableComparisons = new LinkedList<>();

        // Variable in left hand side
        if (localVariableDictionary.containsKey(idTest))
        {
            String variablePath = localVariableDictionary.get(idTest);

            // Build the comparisons
            for (SoarParser.Attr_value_testsContext attributeCtx : attrValueTestsCtxs)
            {
                String attrPath = attributeCtx.attr_test().stream().map(RuleContext::getText).collect(Collectors.joining("_"));
                if (attrPath.contains("{")){
                    //conjunction of attributes or grouping
                    if (attributeCtx.attr_test().size() == 1){
                        SoarParser.Conjunctive_testContext conjCtx= attributeCtx.attr_test(0).test().conjunctive_test();
                        if ((conjCtx.simple_test().size() == 2) && (conjCtx.simple_test(1).relational_test().relation() == null)) {      //condition for when {} is used to group variables
                            attrPath = conjCtx.simple_test(0).getText();
                        }
                    }
                }
                String leftTerm = variablePath + "_" + attrPath;
                //System.out.println("            left term before: " + leftTerm);
                //leftTerm = unravelReference(leftTerm);
                //System.out.println("            left term after: " + leftTerm);

                //Depending on if we are getting basic guards or guards with attribute disjunctions,
                // continue to next iteration if we come across a basic guard (without"<<") and we are looking for attributeDisjunctions and viceVersa
                if ((!leftTerm.contains("<<") && attributeDisjunctions) || (leftTerm.contains("<<") && !attributeDisjunctions)){
                    //Disjunctions of attributes are handled later in getAllStartToRunTransitions
                    //This way, none disjunction attributes can be duplicated and disjunctions options are added to each transition
                    continue;
                }


                if (attributeCtx.getText().startsWith("-^"))
                {
                    stateVariableComparisons.add(leftTerm + " == nil");
                }
                else
                {
                    /*
                    07/21/2022 Conditional logic to identify types of tests on Soar condition-side
                     */
                    int numberOfValues;
                    List <? extends ParserRuleContext> contexts = null;
                    List <TerminalNode> constants = null;
                    String logicOperationString = "";
                    if (attributeCtx.value_test(0).test().conjunctive_test() != null) {
                        numberOfValues = attributeCtx.value_test(0).test().conjunctive_test().simple_test().size();
                        contexts = attributeCtx.value_test(0).test().conjunctive_test().simple_test();
                    } else if (attributeCtx.value_test(0).test().simple_test().disjunction_test() != null) {
                        numberOfValues = attributeCtx.value_test(0).test().simple_test().disjunction_test().constant().size();
                        contexts = attributeCtx.value_test(0).test().simple_test().disjunction_test().constant();
                        logicOperationString = " || ";

                    } else if (attributeCtx.value_test(0).test().multi_value_test() != null) {
                        numberOfValues = attributeCtx.value_test(0).test().multi_value_test().Int_constant().size();
                        constants = attributeCtx.value_test(0).test().multi_value_test().Int_constant();
                    } else {
                        // There would be a risk of NullPointer here, but numberOfValues will always be 1 within this block
                        try {
                            numberOfValues = attributeCtx.value_test().size();

                        } catch (NullPointerException e) {
                            System.out.println("                There is no value test in the current context.");
                            numberOfValues = 0;
                        }

                    }

                    if (numberOfValues == 1)
                    {
                        Element relationAndRightTerm = attributeCtx.value_test(0).accept(this);

                        String relation = relationAndRightTerm.attributeValue("rel");
                        String rightTerm;

                        if (relation.equals("="))
                        {
                            relation = "==";
                        }

                        if (relationAndRightTerm.attribute("var") != null)
                        {
                            rightTerm = localVariableDictionary.get(relationAndRightTerm.attributeValue("var"));
                        }
                        else
                        {
                            rightTerm = relationAndRightTerm.attributeValue("const");
                        }


                        if (rightTerm == null)
                        {
                            break;
                        }
                        else if (rightTerm.equals("true") && relation.equals("=="))
                        {
                            stateVariableComparisons.add(leftTerm);
                        }
                        else if (rightTerm.equals("false") && relation.equals("=="))
                        {
                            stateVariableComparisons.add("!"+(leftTerm));
                        }
                        else if (!rightTerm.equals(leftTerm))
                        {
                            stateVariableComparisons.add(leftTerm + " " + relation + " " + rightTerm);
                        }
                        //if right term equals left term, then soar is trying to say "if that attribute has been initialized before"
                        else if (rightTerm.equals(leftTerm)){
                            stateVariableComparisons.add(leftTerm + " != nil");
                        }

                    }
                    /*
                    07/21/2022 Handles parsing of multi-valued tests, like conjunctive, disjunction, or multi-value
                     */
                    else if (contexts != null)
                    {

                        List<String> conditionComparisons = new LinkedList<>();
                        for (ParserRuleContext test:contexts) {
                            Element relationAndRightTerm = test.accept(this);


                            String relation = relationAndRightTerm.attributeValue("rel");
                            String rightTerm;

                            if (relation == null || relation.equals("="))
                            {
                                relation = "==";
                            }

                            if (relationAndRightTerm.attribute("var") != null)
                            {
                                rightTerm = localVariableDictionary.get(relationAndRightTerm.attributeValue("var"));
                            }
                            else
                            {
                                rightTerm = relationAndRightTerm.attributeValue("const");
                            }

                            if (rightTerm == null)
                            {
                                break;
                            }
                            else if (rightTerm.equals("true") && relation.equals("=="))
                            {
                                conditionComparisons.add(leftTerm);
                            }
                            else if (rightTerm.equals("false") && relation.equals("=="))
                            {
                                conditionComparisons.add("!"+leftTerm);
                            }
                            else if (!rightTerm.equals(leftTerm))
                            {
                                conditionComparisons.add(leftTerm + " " + relation + " " + rightTerm);
                            }
                        }
                        String collectedString = conditionComparisons.stream()
                                .filter(c -> c != null && !c.equals(""))
                                .collect(Collectors.joining(logicOperationString));
                        collectedString = "(" + collectedString + ")";
                        stateVariableComparisons.add(collectedString);

                    } else {
                        for (TerminalNode constant:constants) {
                            Element relationAndRightTerm = constant.accept(this);
                            System.out.println("This is the current constant being checked: " + constant.getText());

                            String relation = relationAndRightTerm.attributeValue("rel");
                            String rightTerm;

                            if (relation == null || relation.equals("="))
                            {
                                relation = "==";
                            }

                            if (relationAndRightTerm.attribute("var") != null)
                            {
                                rightTerm = localVariableDictionary.get(relationAndRightTerm.attributeValue("var"));
                            }
                            else
                            {
                                rightTerm = relationAndRightTerm.attributeValue("const");
                            }

                            if (rightTerm == null)
                            {
                                break;
                            }
                            else if (rightTerm.equals("true") && relation.equals("=="))
                            {
                                stateVariableComparisons.add(leftTerm);
                            }
                            else if (rightTerm.equals("false") && relation.equals("=="))
                            {
                                stateVariableComparisons.add("!"+leftTerm);
                            }
                            else if (!rightTerm.equals(leftTerm))
                            {
                                stateVariableComparisons.add(leftTerm + " " + relation + " " + rightTerm);
                            }
                        }
                    }
                }
            }
        }

        //System.out.println(stateVariableComparisons);
        return stateVariableComparisons
                .stream()
                .filter(c -> c != null && !c.equals(""))
                .collect(Collectors.joining(" && "));
    }

    @Override
    public Element visitCond(SoarParser.CondContext ctx)
    {
        System.out.println("        Entering conditions for " + ctx.getText());
        return ctx.positive_cond().accept(this);
    }

    @Override
    public Element visitPositive_cond(SoarParser.Positive_condContext ctx)
    {
        return ctx.conds_for_one_id().accept(this);
    }

    @Override
    public Element visitConds_for_one_id(SoarParser.Conds_for_one_idContext ctx)
    {
        String productionName = ((SoarParser.Soar_productionContext) ctx.parent.parent.parent.parent).sym_constant().getText();
        String idTest = ctx.id_test().getText();
        Map<String, String> localVariableDictionary = _variableDictionary.get(productionName);

        return new DefaultElement("").addText(innerVisitConditionVisit(ctx.attr_value_tests(), localVariableDictionary, idTest, false));
    }

    @Override
    public Element visitId_test(SoarParser.Id_testContext ctx)
    {
        return null;
    }

    @Override
    public Element visitAttr_value_tests(SoarParser.Attr_value_testsContext ctx)
    {
        return ctx.attr_test(0).accept(this);

    }

    @Override
    public Element visitAttr_test(SoarParser.Attr_testContext ctx)
    {
        return ctx.children.get(0).accept(this);
    }

    @Override
    public Element visitValue_test(SoarParser.Value_testContext ctx)
    {
        return ctx.children.get(0).accept(this);
    }

    @Override
    public Element visitTest(SoarParser.TestContext ctx)
    {
        return ctx.children.get(0).accept(this);
    }

    @Override
    public Element visitConjunctive_test(SoarParser.Conjunctive_testContext ctx)
    {
        System.out.print("These are the subtests of this conjunctive test: ");
        ctx.simple_test().forEach(t -> System.out.print(t.getText() + " "));
        System.out.println();

        return ctx.simple_test(0).accept(this);
    }

    @Override
    public Element visitSimple_test(SoarParser.Simple_testContext ctx)
    {
        return ctx.children.get(0).accept(this);
    }

    /*
    07/20/2022 Prep for implementing Multi-value tests
     */
    @Override
    public Element visitMulti_value_test(SoarParser.Multi_value_testContext ctx)
    {
        System.out.print("These are the proposed values for attribute " + ctx.getParent().getParent().getText() + ": ");
        ctx.children.forEach(c -> System.out.print(c.getText() + " "));
        System.out.println();
        return null;
    }

    /*
    07/20/2022 Prep for implementing Disjunction tests
     */
    @Override
    public Element visitDisjunction_test(SoarParser.Disjunction_testContext ctx)
    {


        Element disjunctionsElement = new DefaultElement("").addAttribute("size", String.valueOf(ctx.constant().size()));

        //allValues contains a concatenated string of all value options for the disjunction
        disjunctionsElement.addAttribute("allValues", ctx.getText());
        int num = 0;
        //for each constant in the list of disjunction constants
        for ( SoarParser.ConstantContext constCtx : ctx.constant()){
            //assign all options for disjunction to the returned element eg const1 -> up, const2 -> down...
            disjunctionsElement.addAttribute( "const"+num, constCtx.sym_constant().getText());
            num += 1;
        }
        return disjunctionsElement;

    }

    @Override
    public Element visitRelational_test(SoarParser.Relational_testContext ctx)
    {
        String relation = "==";

        if (ctx.relation() != null)
        {
            relation = ctx.relation().getText();

            if (relation.equals("<>"))
            {
                relation = "!=";
            }
        }
        return ctx.single_test().accept(this).addAttribute("rel", relation);
    }

    @Override
    public Element visitRelation(SoarParser.RelationContext ctx)
    {
        return null;
    }

    @Override
    public Element visitSingle_test(SoarParser.Single_testContext ctx)
    {
        return ctx.children.get(0).accept(this);
    }

    @Override
    public Element visitVariable(SoarParser.VariableContext ctx)
    {
        String var = ctx.getText();
        return new DefaultElement("").addAttribute("var", ctx.getText());
    }

    @Override
    public Element visitConstant(SoarParser.ConstantContext ctx)
    {
        String result = ctx.getText();

        if (ctx.Print_string() != null)
        {
            result = LITERAL_STRING_PREFIX + ctx.Print_string().getText().split("|")[1];
        }

        return new DefaultElement("").addAttribute("const", result);
    }

    @Override
    public Element visitAction_side(SoarParser.Action_sideContext ctx)
    {
        System.out.println("    Entering action side");
        // get assignments from actions
        return new UPPAALElementWithXY("label")
                .addAttribute("kind", "assignment")
                .addText(ctx.action()
                        .stream()
                        .map(c -> c.accept(this).getText())
                        .filter(t -> t != null && !t.equals(""))
                        .collect(Collectors.joining(", ")));
    }

    @Override
    public Element visitAction(SoarParser.ActionContext ctx)
    {
        String productionName = ((SoarParser.Soar_productionContext) ctx.parent.parent).sym_constant().getText();
        Map<String, String> localDictionary = _variableDictionary.get(productionName);
        String prefix = localDictionary.get(ctx.variable().getText());
        return new DefaultElement("").addText(innerVisitAction(prefix, ctx.attr_value_make()));
    }

    private String innerVisitAction(String prefix, List<SoarParser.Attr_value_makeContext> ctxs)
    {
        String productionName = ((SoarParser.Soar_productionContext) ctxs.get(0).parent.parent.parent).sym_constant().getText();
        Map<String, String> localDictionary = _variableDictionary.get(productionName);
        Map<String, String[]> stateAssignments = new HashMap<>();
        for (SoarParser.Attr_value_makeContext attrCtx : ctxs)
        {
            /*
            07/21/2022 Modified action-side assignment name generation to better handle variables
             */
            String suffix = "";
            for (SoarParser.Variable_or_sym_constantContext word:attrCtx.variable_or_sym_constant()) {
                //try assigning the meaning of the variable if it exists in the dictionary
                try {
                    suffix = suffix.concat(localDictionary.get(word.variable().getText()) + "_");
                } catch (NullPointerException e) {
                    suffix = suffix.concat(word.sym_constant().getText() + "_");
                }
            }

            if (suffix.endsWith("_")) suffix = suffix.substring(0, suffix.length()-1);

            String leftSide = prefix + "_" + suffix;

            Element rightSideElement = attrCtx.value_make().accept(this);

            String[] rightSide = determineAssignment(leftSide, rightSideElement, stateAssignments);


            if (rightSide != null)
            {
                stateAssignments.put(leftSide, rightSide);

            }
        }
        return stateAssignments.entrySet().stream()
                .map(e -> e.getKey() + " = " + e.getValue()[0])
                .collect(Collectors.joining(", "));
    }

    private String[] determineAssignment(String leftSide, Element rightSideElement, Map<String, String[]> stateAssignments)
    {
        if (rightSideElement == null)
        {
            return null;
        }

        String rightSide = null;
        String prefs;
        if (rightSideElement.attributeValue("pref") == null){
            //If the action is not an operator proposal, then assignment is fine
            if (rightSideElement.attributeValue("const") != null)
            {
                rightSide = rightSideElement.attributeValue("const");
            }
            else if (rightSideElement.attributeValue("expr") != null)
            {
                rightSide = rightSideElement.attributeValue("expr");
            }
            else
            {
                return null;
            }
            prefs = "+";
        }
        else
        {
            prefs = rightSideElement.attributeValue("pref");
            if(prefs.contains("-")){    //if the pref field contains a reject (-), set operator to nil
                rightSide = "nil";
            }
            else if(prefs.contains("+")){
                rightSide = "1";
            }
        }

        //rightSide is equal to left side if an initialization action is encountered,
        //hence, initialize the variable to its init_value which is a unique initialization identifier

        if(Objects.equals(leftSide, rightSide)){
            rightSide = "init_" + rightSide;
        }


        /*
        07/21/2022 Modified to expand rightSide array and include multiple values
         */
        //7/18/24 modified again to replace old assignment if the new assignment is not a reject (-) in soar
        //which means to remove a working memory element from memory.

        if (stateAssignments.containsKey(leftSide))
        {

            if (!prefs.contains("-"))
            {
                return new String[]{rightSide, prefs};
            }
            else
            {
                return null;
            }
        }
        else
        {
            return new String[]{rightSide, prefs};
        }
    }

    private String getBestPreference(String pref1, String pref2)
    {
        if (pref1.contains("~") && !pref2.contains("~"))
        {
            return pref2;
        }
        else if (pref2.contains("~") && !pref1.contains("~"))
        {
            return pref1;
        }
        else
        {
            String orderedPreferences = "!>+=<";
            for (int i = 0; i < orderedPreferences.length(); i++)
            {
                String nextPref = orderedPreferences.substring(i,i);

                if (pref1.contains(nextPref) && !pref2.contains(nextPref))
                {
                    return pref1;
                }
            }
        }
        return pref1;
    }

    @Override
    public Element visitPrint(SoarParser.PrintContext ctx)
    {
        return null;
    }

    @Override
    public Element visitFunc_call(SoarParser.Func_callContext ctx)
    {
        String productionName = ((SoarParser.Soar_productionContext) ctx.parent.parent.parent.parent.parent.parent).sym_constant().getText();
        Map<String, String> localDictionary = _variableDictionary.get(productionName);

        SoarParser.ValueContext leftContext = ctx.value(0);
        SoarParser.ValueContext rightContext = ctx.value(1);

        String leftSide = leftContext.variable() == null ? leftContext.constant().getText() : localDictionary.get(leftContext.getText());

        String result;

        if (ctx.func_name().getText().equals("-") && rightContext == null)
        {
            result = "0 - " + leftSide;
        }
        else
        {
            String rightSide = rightContext.variable() == null ? rightContext.constant().getText() : localDictionary.get(rightContext.getText());
            String funcName = ctx.func_name().getText();

            if ("+-/*".contains(ctx.func_name().getText()))
            {
                result = leftSide + " " + funcName + " " + rightSide;
            }
            else
            {
                result = "";
            }
        }

        return new DefaultElement("").addAttribute("expr", result);
    }

    @Override
    public Element visitFunc_name(SoarParser.Func_nameContext ctx)
    {
        return null;
    }

    @Override
    public Element visitValue(SoarParser.ValueContext ctx)
    {
        String productionName = ((SoarParser.Soar_productionContext) ctx.parent.parent.parent.parent.parent).sym_constant().getText();
        Map<String, String> localDictionary = _variableDictionary.get(productionName);
        Element resultantElement =  ctx.children.get(0).accept(this);


        if (resultantElement.attributeValue("var") != null)
        {
            String variableToValue = localDictionary.get(resultantElement.attributeValue("var"));

            resultantElement.addAttribute("const",variableToValue);
        }

        return resultantElement;
    }

    @Override
    public Element visitAttr_value_make(SoarParser.Attr_value_makeContext ctx, String varName)
    {
        String leftSide = ctx.variable_or_sym_constant()
                .stream()
                .map(RuleContext::getText)
                .collect(Collectors.joining("_"));

        Element rightSide = ctx.value_make().accept(this);

        if (rightSide == null)
        {
            return new DefaultElement("");
        }
        else
        {
            return new DefaultElement("").addText(leftSide + " = " + rightSide.getText());
        }
    }


    /*
    07/21/2022 Modified to get variable text without special characters (< or >)
     */
    @Override
    public Element visitVariable_or_sym_constant(SoarParser.Variable_or_sym_constantContext ctx)
    {

        return null;
    }

    @Override
    public Element visitValue_make(SoarParser.Value_makeContext ctx)
    {
        Element resultantElement = ctx.value().accept(this);


        long preferences = ctx.pref_specifier().size();

        if (preferences > 0)
        {
            String concatenatedPreferences = ctx.pref_specifier()
                    .stream()
                    .map(RuleContext::getText)
                    .collect(Collectors.joining());

            resultantElement.addAttribute("pref", concatenatedPreferences);
        }
        return resultantElement;
    }

    @Override
    public Element visitPref_specifier(SoarParser.Pref_specifierContext ctx)
    {
        return null;
    }

    @Override
    public Element visitUnary_pref(SoarParser.Unary_prefContext ctx)
    {
        return null;
    }

    @Override
    public Element visitUnary_or_binary_pref(SoarParser.Unary_or_binary_prefContext ctx)
    {
        return null;
    }

    @Override
    public Element visit(ParseTree parseTree)
    {
        return null;
    }

    @Override
    public Element visitChildren(RuleNode ruleNode)
    {
        return null;
    }

    @Override
    public Element visitTerminal(TerminalNode terminalNode)
    {
        return null;
    }

    @Override
    public Element visitErrorNode(ErrorNode errorNode)
    {
        return null;
    }

    // Recursively unravels each string by checking at each layer if the prefix is a reference to another string
    // **Only works for references with a single value
    private String unravelReference (String reference) {
        String[] layers = reference.split("_");
        String current = layers[0];
        for (int i = 1; i < layers.length; i++) {
            if (!_referenceMap.containsKey(current)) {
                current += "_" + layers[i];
            } else {
                current = unravelReference(_referenceMap.get(current).get(0)) + "_" + layers[i];
            }
        }

        // This is the final iteration which is outside the main for loop to prevent indexing out of bounds
        if (!_referenceMap.containsKey(current)) {
            return current;
        } else {
            return unravelReference(_referenceMap.get(current).get(0));
        }
    }

    private String simplifiedString(String str)
    {
        return str.replace("-", "_").replace("*", "_");
    }
}
