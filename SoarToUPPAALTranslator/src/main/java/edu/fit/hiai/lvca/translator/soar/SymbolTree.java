package edu.fit.hiai.lvca.translator.soar;

import java.util.*;

/**
 * Created by mstafford on 6/17/16.
 *
 * Designed to capture the identifiers and hierarchy of a Soar agent
 */
public class SymbolTree
{
    final String name;
    String value;
    private final List<SymbolTree> children;

    SymbolTree(String name)
    {
        this.name = name;
        children = new LinkedList<>();
    }

    private boolean isLeaf()
    {
        return children.size() == 0;
    }

    void addChild(SymbolTree childTree)
    {
        if (children.stream().noneMatch(c -> c.name.equals(childTree.name)))
        {
            children.add(childTree);
        }
        //else merge the children into the existing tree, at the point where their names do equal
        else{
            children.forEach(c -> {
                if (c.name.equals(childTree.name)){
                    childTree.children.forEach(ctc -> c.addChild(ctc));
                }

            });
        }

    }

    /**
     * Return the first subtree (depth-first) that has the given name
     *
     * @param treeName
     * @return
     */
    SymbolTree getSubtree (String treeName)
    {
        SymbolTree result = getSubtree0(treeName);

        if (result == null)
        {
            throw new NoSuchElementException("Element "+ treeName + " not in tree");
        }
        return result;
    }

    private SymbolTree getSubtree0 (String treeName)
    {
        if (name.equals(treeName))
        {
            return this;
        }
        else
        {
            for (SymbolTree child : children)
            {
                SymbolTree subtree = child.getSubtree0(treeName);
                if (subtree != null)
                {
                    return subtree;
                }
            }
        }
        return null;
    }

    /**
     * Give a underscore delimited string of parents to the first child of the given name
     * TODO return a list
     * @param treeName
     * @return
     */
    String pathTo (String treeName)
    {
        if (name.equals(treeName))
        {
            return name;
        }

        String suffix = children.stream()
                .map(child -> child.pathTo(treeName))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (suffix != null)
        {
            return name + "_" + suffix;
        }
        else
        {
            return null;
        }
    }
    /**
     * Give a underscore delimited string of parents to the child of the given name that also matches the given value
     * TODO return a list
     * @param entry
     * @return
     */
    String pathTo (HashMap.Entry<String, String> entry)
    {
        if(value != null){
            //if the value check exists, perform a value check to ensure we are at the right node
            if ((name.equals(entry.getValue())) && (value.equals(entry.getKey())))
            {
                return name;
            }
        }
        else{
            if (name.equals(entry.getValue()))
            {
                return name;
            }
        }


        String suffix = children.stream()
                .map(child -> child.pathTo(entry))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (suffix != null)
        {
            return name + "_" + suffix;
        }
        else
        {
            return null;
        }
    }

    /**
     * Return a list of all paths from the root node to a leaf node, delimited by underscores
     * @return
     */
    List<String> getAllPaths()
    {
        if (isLeaf())
        {
            return Collections.singletonList(name);
        }
        else
        {
            List<String> names = new LinkedList<>();
            //Added "names.add(name)"to also get intermediate paths like state and state_operator,
            // even thought they are not leaves in the tree
            names.add(name);

            for (SymbolTree child : children)
            {
                for(String path : child.getAllPaths())
                {
                    names.add(name + "_" + path);
                }
            }

            return names;
        }
    }

    List<SymbolTree> getChildren()
    {
        return children;
    }
}
