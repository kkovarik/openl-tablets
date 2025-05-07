package org.openl.rules.lang.xls;

import org.openl.grammar.bexgrammar.BExGrammar;
import org.openl.grammar.bexgrammar.ParseException;
import org.openl.grammar.bexgrammar.Token;
import org.openl.grammar.bexgrammar.TokenMgrError;
import org.openl.syntax.exception.SyntaxNodeException;
import org.openl.util.text.TextInterval;

class BExGrammarWithParsingHelp extends BExGrammar {

    private static final String ENCOUNTERED_PREFIX_EMPTY = "Encountered \"\"";
    private static final String WAS_EXPECTING = "Was expecting:";
    private static final String WAS_EXPECTING_ONE_OF = "Was expecting one of:";

    private static String addEscapes(String str) {
        StringBuilder retval = new StringBuilder();
        char ch;
        for (int i = 0; i < str.length(); i++) {
            switch (str.charAt(i)) {
                case 0:
                    break;
                case '\b':
                    retval.append("\\b");
                    break;
                case '\t':
                    retval.append("\\t");
                    break;
                case '\n':
                    retval.append("\\n");
                    break;
                case '\f':
                    retval.append("\\f");
                    break;
                case '\r':
                    retval.append("\\r");
                    break;
                default:
                    if ((ch = str.charAt(i)) < 0x20 || ch > 0x7e) {
                        String s = "0000" + Integer.toString(ch, 16);
                        retval.append("\\u").append(s.substring(s.length() - 4));
                    } else {
                        retval.append(ch);
                    }
            }
        }
        return retval.toString();
    }

    @Override
    public void parseTopNode(String type) {
        try {
            switch (type) {
                case "method.body":
                    parseTopNodeInternal();
                    break;
                case "method.header":
                    parseMethodHeader();
                    break;
                case "module":
                    parseModuleInternal();
                    break;
                case "param.declaration":
                    parseParamDeclaration();
                    break;
                case "type":
                    parseType();
                    break;
            }
        } catch (ParseException pe) {
            SyntaxNodeException sne = reparseTokens();
            if (sne == null) {
                String msg = pe.getMessage();
                TextInterval pos = pos(pe.currentToken);
                if (msg.startsWith(ENCOUNTERED_PREFIX_EMPTY) && pe.currentToken.next != null) {
                    msg = "Encountered \"" + pe.currentToken.next + "\"" + msg
                            .substring(ENCOUNTERED_PREFIX_EMPTY.length());
                    pos = pos(pe.currentToken.next);
                } else if (msg.contains(WAS_EXPECTING) || msg.contains(WAS_EXPECTING_ONE_OF)) {
                    pos = pos(pe.currentToken.next);
                }
                sne = new SyntaxNodeException(msg, null, pos, module);
            }
            syntaxError = sne;
        } catch (TokenMgrError err) {
            TextInterval loc = pos(err.getMessage(), token);

            syntaxError = new SyntaxNodeException(err.getMessage(), null, loc, module);
        } catch (Exception e) {
            syntaxError = new SyntaxNodeException("Failed to parse an expression.", e, pos(token), module);
        }
    }

    private SyntaxNodeException reparseTokens() {

        BExGrammar be = new BExGrammar();

        be.setModule(module);
        be.ReInit(module.getCharacterStream());

        BracketMatcher bm = new BracketMatcher();

        while (true) {

            Token t;
            try {
                t = be.getNextToken();
            } catch (TokenMgrError err) {
                TextInterval loc = pos(err.getMessage(), token);

                return new SyntaxNodeException(err.getMessage(), null, loc, module);
            }
            if (t.kind == EOF) {
                break;
            }

            BracketMatcher.BracketsStackObject bso = bm.addToken(t.image, t);
            if (bso != null) {
                String message;
                switch (bso.getErrorCode()) {
                    case UNEXPECTED:
                        message = String.format("Unexpected bracket '%s'", addEscapes(t.image));

                        return new SyntaxNodeException(message, null, pos(t), module);
                    case MISMATCHED:
                        Token t2 = (Token) bso.getId();

                        message = String.format("Mismatched: opened with '%s' and closed with '%s'",
                                addEscapes(t2.image.substring(0, 1)),
                                addEscapes(t.image));
                        return new SyntaxNodeException(message, null, pos(t2, t), module);
                    case UNMATCHED:
                        throw new IllegalStateException("UNMATCHED error type shouldn't appear here");
                    default:
                        throw new IllegalStateException("Unknown BracketMatchError = " + bso.getErrorCode());
                }

            }

        }

        BracketMatcher.BracketsStackObject bso = bm.checkAtTheEnd();
        if (bso != null) {
            Token t = (Token) bso.getId();

            String message = String.format("Need to close '%s'", addEscapes(t.image));

            return new SyntaxNodeException(message, null, pos(t), module);

        }

        return null;
    }

}
