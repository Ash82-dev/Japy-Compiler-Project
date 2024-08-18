package compiler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.*;

class SymbolTableEntry {
    String name;
    String accessModifier;
    String type;
    List<String> attributes = new ArrayList<>();
    int startLine;
    int stopLine;
    int first_appearance;
    boolean main = false;
    boolean block = false;

    int size;
    ArrayList<Parameter> parametersList = new ArrayList<>();

    SymbolTableEntry(String name, String accessModifier, String type, int startLine, int stopLine) {
        this.name = name;
        this.accessModifier = accessModifier;
        this.type = type;
        this.startLine = startLine;
        this.stopLine = stopLine;
    }

    public void setSize(int number) {
        this.size = number;
    }

    public void setMain() {
        this.main = true;
    }
    public void setBlock() {
        this.block = true;
    }
    public void setFirst_appearance(int first_appearance) {
        this.first_appearance = first_appearance;
    }

    void addAttribute(String attribute) {
        this.attributes.add(attribute);
    }

    void addParameter(Parameter p){
        parametersList.add(p);
    }

    int getStartLine() {
        return startLine;
    }

    int getStopLine() {
        return stopLine;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("(name: ").append(name).append(") ");
        if (block) {
            sb.append("(first_appearance:").append(this.first_appearance).append(") ").append("(type: ").append(type).append(")");
            return sb.toString();
        }

        sb.append("(accessModifier: ").append(accessModifier).append(") ");
        if (Objects.equals(type, "class")) {
            for (String attribute : attributes) {
                sb.append("(").append(attribute).append(") ");
            }

            if (main) sb.append("(main)");
            return sb.toString();
        }

        if (!Objects.equals(type, "method")) sb.append("(type: ").append(type).append(") ");
        for (String attribute : attributes) {
            if (attribute.contains("parameter")) {
                sb.append("\n").append(attribute);
                return sb.toString();
            }
            sb.append("(").append(attribute).append(") ");
        }

        return sb.toString();
    }
}

class SymbolTable {
    Map<String, SymbolTableEntry> table = new LinkedHashMap<>();


    void insert(String key, SymbolTableEntry value) {
        table.put(key, value);
    }

    SymbolTableEntry lookup(String key) {
        return table.get(key);
    }


    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, SymbolTableEntry> entry : table.entrySet()) {
            if (entry.getKey().equals("parameters")) {
                result.append("parameters: ").append(entry.getValue());
                return result.toString();
            }
            if (entry.getKey().contains("if") || entry.getKey().contains("while")) {
                return result.toString();
            }
            result.append("key = ").append(entry.getKey()).append(", value = ").append(entry.getValue().toString()).append("\n");
        }
        return result.toString();
    }
}

class Parameter {
    int index;
    String name;
    String type;

    public Parameter(int index, String name, String type) {
        this.index = index;
        this.name = name;
        this.type = type;
    }
}
