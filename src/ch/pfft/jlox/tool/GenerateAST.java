package ch.pfft.jlox.tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GenerateAST {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage generate_ast <output_directory");
            System.exit(64);
        }
        String outputDir = args[0];
        defineAst(outputDir, "Expr", Arrays.asList("Assign     : Token name, Expr value",
                "Binary     : Expr left, Token operator, Expr right",
                "Call       : Expr callee, Token paren, List<Expr> arguments", "Get        : Expr object, Token name",
                "Grouping   : Expr expression", "Lambda     : Stmt.Function function", "Literal    : Object value",
                "Logical    : Expr left, Token operator, Expr right",
                "Set        : Expr object, Token name, Expr value", "This       : Token keyword",
                "Unary      : Token operator, Expr right",
                "Ternary    : Expr condition, Token operator, Expr first, Expr second", "Variable   : Token name"));
        defineAst(outputDir, "Stmt", Arrays.asList("Break          : Token keyword",
                "Block          : List<Stmt> statements", "Class          : Token name, List<Stmt.Function> methods",
                "Continue       : Token keyword", "Expression     : Expr expression",
                "Function       : Token name, List<Token> params, List<Stmt> body",
                "For            : Stmt initializer, Expr condition, Expr increment, Stmt body",
                "If             : Expr condition, Stmt thenBranch, Stmt elseBranch", "Print          : Expr expression",
                "Return         : Token keyword, Expr value", "Var            : Token name, Expr initializer"));
    }

    private static void defineAst(String outputDir, String baseName, List<String> types) throws IOException {
        String path = outputDir + "/" + baseName + ".java";
        PrintWriter writer = new PrintWriter(path, "UTF-8");

        writer.println("package ch.pfft.jlox;\n");
        writer.println("import java.util.List;\n");
        writer.println("abstract class " + baseName + "{");

        defineVisitor(writer, baseName, types);

        for (String type : types) {
            String className = type.split(":")[0].trim();
            String fields = type.split(":")[1].trim();
            defineType(writer, baseName, className, fields);
        }

        writer.println("\n  abstract <R> R accept(Visitor<R> visitor);");

        writer.println("}");
        writer.close();
    }

    private static void defineVisitor(PrintWriter writer, String baseName, List<String> types) {

        writer.println("  interface Visitor<R> {");
        for (String type : types) {
            String typeName = type.split(":")[0].trim();
            writer.println("    R visit" + typeName + baseName + "(" + typeName + " " + baseName.toLowerCase() + ");");
        }

        writer.println("  }");
    }

    private static void defineType(PrintWriter writer, String baseName, String className, String fieldList) {
        writer.println("  static class " + className + " extends " + baseName + "{");

        writer.println("    " + className + "(" + fieldList + ") {");
        String fields[] = fieldList.split(", ");
        for (String field : fields) {
            if (field.isEmpty()) {
                continue;
            }
            String name = field.split(" ")[1];
            writer.println("    this." + name + " = " + name + ";");
        }
        writer.println("    }\n");

        writer.println("\n    @Override");
        writer.println("    <R> R accept(Visitor<R> visitor) {");
        writer.println("      return visitor.visit" + className + baseName + "(this);");
        writer.println("    }\n");

        for (String field : fields) {
            if (field.isEmpty()) {
                continue;
            }
            writer.println("    final " + field + ";");
        }

        writer.println("  }");
    }
}
