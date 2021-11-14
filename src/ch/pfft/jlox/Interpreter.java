package ch.pfft.jlox;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import ch.pfft.jlox.Expr.Assign;
import ch.pfft.jlox.Expr.Binary;
import ch.pfft.jlox.Expr.Call;
import ch.pfft.jlox.Expr.Get;
import ch.pfft.jlox.Expr.Grouping;
import ch.pfft.jlox.Expr.Lambda;
import ch.pfft.jlox.Expr.Literal;
import ch.pfft.jlox.Expr.Logical;
import ch.pfft.jlox.Expr.Set;
import ch.pfft.jlox.Expr.Ternary;
import ch.pfft.jlox.Expr.This;
import ch.pfft.jlox.Expr.Unary;
import ch.pfft.jlox.Expr.Variable;
import ch.pfft.jlox.Stmt.Block;
import ch.pfft.jlox.Stmt.Class;
import ch.pfft.jlox.Stmt.Expression;
import ch.pfft.jlox.Stmt.For;
import ch.pfft.jlox.Stmt.Function;
import ch.pfft.jlox.Stmt.If;
import ch.pfft.jlox.Stmt.Print;
import ch.pfft.jlox.Stmt.Return;
import ch.pfft.jlox.Stmt.Var;

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    private static class Break extends RuntimeException {
    }

    private static class Continue extends RuntimeException {
    }

    final Environment globals = new Environment();
    private Environment environment = globals;
    private final Map<Expr, Integer> locals = new HashMap<>();

    Interpreter() {
        globals.define("clock", new LoxCallable() {

            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double) System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() {
                return "<native fn>";
            }

        });
    }

    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    @Override
    public Object visitBinaryExpr(Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
        case STAR:
            checkNumberOperands(expr.operator, left, right);
            return (double) left * (double) right;
        case SLASH:
            checkNumberOperands(expr.operator, left, right);
            if ((double) right == 0) {
                throw new RuntimeError(expr.operator, "Division by zero.");
            }
            return (double) left / (double) right;
        case MINUS:
            checkNumberOperands(expr.operator, left, right);
            return (double) left - (double) right;
        case PLUS:
            if (left instanceof String && right instanceof String)
                return (String) left + (String) right;
            if (left instanceof Double && right instanceof Double)
                return (Double) left + (Double) right;
            if (left instanceof String && right instanceof Double)
                return (String) left + stringify(right);
            if (left instanceof Double && right instanceof String)
                return stringify(left) + (String) right;
            throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings");
        case GREATER:
            checkNumberOperands(expr.operator, left, right);
            return (double) left > (double) right;
        case GREATER_EQUAL:
            checkNumberOperands(expr.operator, left, right);
            return (double) left >= (double) right;
        case LESS:
            checkNumberOperands(expr.operator, left, right);
            return (double) left < (double) right;
        case LESS_EQUAL:
            checkNumberOperands(expr.operator, left, right);
            return (double) left <= (double) right;
        case BANG_EQUAL:
            return !isEqual(left, right);
        case EQUAL_EQUAL:
            return isEqual(left, right);
        case COMMA:
            return right;
        default:
            break;

        }
        return null;
    }

    @Override
    public Object visitCallExpr(Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren, "Can only call functions and classes.");
        }

        LoxCallable function = (LoxCallable) callee;
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren,
                    "Expected " + function.arity() + "arguments but got " + arguments.size() + ".");
        }
        return function.call(this, arguments);
    }

    @Override
    public Object visitGetExpr(Get expr) {
        Object object = evaluate(expr.object);
        if (object instanceof LoxInstance) {
            return ((LoxInstance) object).get(expr.name);
        }
        throw new RuntimeError(expr.name, "Only instances have properties.");
    }

    @Override
    public Object visitGroupingExpr(Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitLambdaExpr(Lambda expr) {
        return new LoxFunction(expr.function, environment, false);
    }

    @Override
    public Object visitLiteralExpr(Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Logical expr) {
        Object left = evaluate(expr.left);
        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) {
                return left;
            }
        } else {
            if (!isTruthy(left)) {
                return left;
            }
        }
        return evaluate(expr.right);
    }

    @Override
    public Object visitSetExpr(Set expr) {
        Object object = evaluate(expr.object);

        if (!(object instanceof LoxInstance)) {
            throw new RuntimeError(expr.name, "Only instances have fields");
        }

        Object value = evaluate(expr.value);
        ((LoxInstance) object).set(expr.name, value);
        return value;
    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
        return lookUpVariable(expr.keyword, expr);
    }

    @Override
    public Object visitUnaryExpr(Unary expr) {
        Object right = evaluate(expr.right);
        switch (expr.operator.type) {
        case MINUS:
            checkNumberOperand(expr.operator, right);
            return -(double) right;
        case BANG:
            return !isTruthy(right);
        default:
            break;
        }
        return null;
    }

    @Override
    public Object visitTernaryExpr(Ternary expr) {
        Object condition = evaluate(expr.condition);
        if (isTruthy(condition)) {
            return evaluate(expr.first);
        } else {
            return evaluate(expr.second);
        }
    }

    private Object evaluate(Expr expression) {
        return expression.accept(this);
    }

    private void execute(Stmt statement) {
        statement.accept(this);
    }

    public void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    @Override
    public Void visitBreakStmt(ch.pfft.jlox.Stmt.Break stmt) {
        throw new Break();
    }

    @Override
    public Void visitContinueStmt(ch.pfft.jlox.Stmt.Continue stmt) {
        throw new Continue();
    }

    @Override
    public Void visitBlockStmt(Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitClassStmt(Class stmt) {
        environment.define(stmt.name.lexeme, null);

        Map<String, LoxFunction> methods = new HashMap<>();
        for (Stmt.Function method : stmt.methods) {
            LoxFunction function = new LoxFunction(method, environment, method.name.lexeme.equals("init"));
            methods.put(method.name.lexeme, function);
        }

        LoxClass klass = new LoxClass(stmt.name.lexeme, methods);
        environment.assign(stmt.name, klass);
        return null;
    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;

            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    @Override
    public Void visitExpressionStmt(Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Function stmt) {
        LoxFunction function = new LoxFunction(stmt, environment, false);
        environment.define(stmt.name.lexeme, function);

        return null;
    }

    @Override
    public Void visitIfStmt(If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitReturnStmt(Return stmt) {
        Object value = null;
        if (stmt.value != null) {
            value = evaluate(stmt.value);
        }

        throw new ch.pfft.jlox.Return(value);
    }

    @Override
    public Void visitVarStmt(Var stmt) {
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }
        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Void visitForStmt(For stmt) {
        if (stmt.initializer != null) {
            execute(stmt.initializer);
        }
        while (stmt.condition != null ? isTruthy(evaluate(stmt.condition)) : true) {
            try {
                execute(stmt.body);
            } catch (Break b) {
                return null;
            } catch (Continue c) {
                // Intentionally left blank, do nothing and go ahead to the increment step
            }
            if (stmt.increment != null) {
                evaluate(stmt.increment);
            }
        }
        return null;
    }

    @Override
    public Object visitAssignExpr(Assign expr) {
        Object value = evaluate(expr.value);

        Integer distance = locals.get(expr);
        if (distance != null) {
            environment.assignAt(distance, expr.name, value);
        } else {
            globals.assign(expr.name, value);
        }
        return value;
    }

    @Override
    public Object visitVariableExpr(Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Token name, Expr expr) {
        Integer distance = locals.get(expr);
        if (distance != null) {
            return environment.getAt(distance, name.lexeme);
        } else {
            return globals.get(name);
        }
    }

    private boolean isTruthy(Object object) {
        if (object == null)
            return false;
        if (object instanceof Boolean)
            return (boolean) object;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null)
            return true;
        if (a == null)
            return false;
        return a.equals(b);
    }

    private String stringify(Object object) {
        if (object == null)
            return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }
        return object.toString();
    }

    private void checkNumberOperand(Token operator, Object right) {
        if (right instanceof Double)
            return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double)
            return;
        throw new RuntimeError(operator, "Operands must be numbers.");
    }
}
