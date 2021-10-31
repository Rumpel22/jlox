package ch.pfft.jlox;

class Token {
    final TokenType type;
    final String lexeme;
    final Object literal;
    final int line;

    public Token(TokenType type, String lexeme, Object literal, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
    }

    public String toString() {
        return type + " " + lexeme + " " + literal;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof Token)) {
            return false;
        }

        Token c = (Token) o;

        // Compare the data members and return accordingly
        return type.compareTo(c.type) == 0 && lexeme.equals(c.lexeme);
    }
}
