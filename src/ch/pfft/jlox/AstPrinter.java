package ch.pfft.jlox;

import java.util.stream.Collectors;

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

public class AstPrinter implements Expr.Visitor<String> {

    String print(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public String visitAssignExpr(Assign expr) {
        return paranthesize("=", expr, expr.value);
    }

    @Override
    public String visitBinaryExpr(Binary expr) {
        return paranthesize(expr.operator.lexeme, expr.left, expr.right);
    }

    @Override
    public String visitGroupingExpr(Grouping expr) {
        return paranthesize("group", expr.expression);
    }

    @Override
    public String visitLambdaExpr(Lambda expr) {
        var params = expr.function.params.stream().map(token -> token.lexeme).collect(Collectors.joining(", "));

        return paranthesize("lambda", new Expr.Literal(params));
    }

    @Override
    public String visitLiteralExpr(Literal expr) {
        if (expr.value == null) {
            return "nil";
        }
        return expr.value.toString();
    }

    @Override
    public String visitUnaryExpr(Unary expr) {
        return paranthesize(expr.operator.lexeme, expr.right);
    }

    @Override
    public String visitTernaryExpr(Ternary expr) {
        return paranthesize(":?", expr.condition, expr.first, expr.second);
    }

    @Override
    public String visitVariableExpr(Variable expr) {
        return paranthesize("var", expr);
    }

    @Override
    public String visitLogicalExpr(Logical expr) {
        return paranthesize(expr.operator.lexeme, expr.left, expr.right);
    }

    @Override
    public String visitCallExpr(Call expr) {
        return paranthesize("fn", expr);
    }

    private String paranthesize(String name, Expr... exprs) {
        StringBuilder builder = new StringBuilder();
        builder.append("(").append(name);
        for (Expr expr : exprs) {
            builder.append(" ");
            builder.append(expr.accept(this));
        }
        builder.append(")");

        return builder.toString();
    }

    public static void main(String[] args) {
        Expr expression = new Expr.Binary(
                new Expr.Unary(new Token(TokenType.MINUS, "-", null, 1), new Expr.Literal(123)),
                new Token(TokenType.STAR, "*", null, 1), new Expr.Grouping(new Expr.Literal(45.67)));

        System.out.println(new AstPrinter().print(expression));
    }

    @Override
    public String visitGetExpr(Get expr) {
        return paranthesize(expr.name.lexeme, expr.object);
    }

    @Override
    public String visitSetExpr(Set expr) {
        return paranthesize(expr.name.lexeme, expr.object);
    }

    @Override
    public String visitThisExpr(This expr) {
        return "this";
    }

}
