package ch.pfft.jlox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import ch.pfft.jlox.Expr.Get;
import ch.pfft.jlox.Expr.Lambda;
import ch.pfft.jlox.Expr.Set;
import ch.pfft.jlox.Expr.Super;
import ch.pfft.jlox.Expr.Ternary;
import ch.pfft.jlox.Expr.This;
import ch.pfft.jlox.Stmt.Break;
import ch.pfft.jlox.Stmt.Class;
import ch.pfft.jlox.Stmt.Continue;
import ch.pfft.jlox.Stmt.Function;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    private final Interpreter interpreter;
    private final Stack<Map<String, Boolean>> scopes = new Stack<>();
    private FunctionType currentFunction = FunctionType.NONE;
    private boolean inLoop = false;
    private final Stack<ArrayList<Token>> unusedLocalVariables = new Stack<>();

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    private enum FunctionType {
        NONE, FUNCTION, INITIALIZER, METHOD, STATIC
    }

    private enum ClassType {
        NONE, CLASS,
    }

    private ClassType currentClass = ClassType.NONE;

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitClassStmt(Class stmt) {
        ClassType enclosingClass = currentClass;
        currentClass = ClassType.CLASS;

        declare(stmt.name);
        define(stmt.name);

        if (stmt.superclass != null && stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
            Lox.error(stmt.superclass.name, "A class can't' inherit from itself.");
        }
        if (stmt.superclass != null) {
            resolve(stmt.superclass);
        }

        if (stmt.superclass != null) {
            beginScope();
            scopes.peek().put("super", true);
        }

        beginScope();
        scopes.peek().put("this", true);

        for (Stmt.Function method : stmt.methods) {
            FunctionType declaration = FunctionType.METHOD;
            if (method.name.lexeme.equals("init")) {
                declaration = FunctionType.INITIALIZER;
            }
            resolveFunction(method, declaration);
        }
        for (Stmt.Function method : stmt.statics) {
            resolveFunction(method, FunctionType.STATIC);
        }
        endScope();

        if (stmt.superclass != null) {
            endScope();
        }

        currentClass = enclosingClass;
        return null;
    }

    @Override
    public Void visitBreakStmt(Break stmt) {
        if (!inLoop) {
            Lox.error(stmt.keyword, "break statement only allowed within a loop.");
        }

        return null;
    }

    @Override
    public Void visitContinueStmt(Continue stmt) {
        if (!inLoop) {
            Lox.error(stmt.keyword, "continue statement only allowed within a loop.");
        }

        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);

        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) {
            resolve(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Can't return from top-level-code.");
        }

        if (stmt.value != null) {
            if (currentFunction == FunctionType.INITIALIZER) {
                Lox.error(stmt.keyword, "Can't return a value from init().");
            }
            resolve(stmt.value);
        }
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitForStmt(Stmt.For stmt) {
        boolean currentInLoop = inLoop;
        inLoop = true;
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        if (stmt.condition != null) {
            resolve(stmt.condition);
        }
        if (stmt.increment != null) {
            resolve(stmt.increment);
        }
        resolve(stmt.body);

        inLoop = currentInLoop;
        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);

        for (Expr argument : expr.arguments) {
            resolve(argument);
        }
        return null;
    }

    @Override
    public Void visitGetExpr(Get expr) {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLambdaExpr(Lambda expr) {
        resolve(expr.function);

        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitSetExpr(Set expr) {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitSuperExpr(Super expr) {
        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitThisExpr(This expr) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Can't use 'this' outside of a class.");
            return null;
        }
        if (currentFunction == FunctionType.STATIC) {
            Lox.error(expr.keyword, "Can't use 'this' in a static method.");
            return null;
        }

        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitTernaryExpr(Ternary expr) {
        resolve(expr.condition);
        resolve(expr.first);
        resolve(expr.second);

        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if (!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) {
            Lox.error(expr.name, "Can't read local variable in its own initializer.");
        }

        resolveLocal(expr, expr.name);
        return null;
    }

    void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    private void resolve(Stmt statement) {
        statement.accept(this);
    }

    private void resolve(Expr expression) {
        expression.accept(this);
    }

    private void resolveFunction(Function stmt, FunctionType type) {
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;

        beginScope();
        if (stmt.params != null) {
            for (Token name : stmt.params) {
                declare(name);
                define(name);
            }
        }
        resolve(stmt.body);
        endScope();

        currentFunction = enclosingFunction;
    }

    private void beginScope() {
        scopes.push(new HashMap<String, Boolean>());
        unusedLocalVariables.push(new ArrayList<Token>());
    }

    private void endScope() {
        scopes.pop();
        var unusedVars = unusedLocalVariables.pop();
        for (var unusedVar : unusedVars) {
            Lox.error(unusedVar, "Variable not used");
        }
    }

    private void declare(Token name) {
        if (scopes.isEmpty()) {
            return;
        }

        Map<String, Boolean> scope = scopes.peek();
        if (scope.containsKey(name.lexeme)) {
            Lox.error(name, "Already a variable with this name in this scope.");
        }
        scope.put(name.lexeme, false);
        unusedLocalVariables.peek().add(name);
    }

    private void define(Token name) {
        if (scopes.isEmpty()) {
            return;
        }

        scopes.peek().put(name.lexeme, true);
    }

    private void resolveLocal(Expr expr, Token name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name.lexeme)) {
                unusedLocalVariables.get(i).remove(name);

                interpreter.resolve(expr, scopes.size() - 1 - i);
                return;
            }
        }
    }
}
