package vn.perfidanb.jarbe.gui;

import org.objectweb.asm.util.Printer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class JasmSyntaxHighlighter {
    private static final Set<String> STRUCTURAL = Set.of(
            "VERSION", "CLASS", "SUPER", "INTERFACE", "FIELD", "METHOD", "END", "LABEL", "LINE"
    );
    private static final Set<String> OPCODES = new HashSet<>();

    static {
        Arrays.stream(Printer.OPCODES)
                .filter(name -> name != null && !name.isBlank())
                .forEach(OPCODES::add);
    }

    private JasmSyntaxHighlighter() {
    }

    public static String toHtml(String text) {
        StringBuilder body = new StringBuilder();
        for (String line : text.replace("\r\n", "\n").split("\n", -1)) {
            body.append(highlightLine(line)).append('\n');
        }
        return """
                <!doctype html>
                <html>
                <head>
                <meta charset="UTF-8">
                <style>
                body { background: #1f2329; color: #d7dae0; margin: 0; }
                pre { font: 13px Consolas, 'JetBrains Mono', monospace; line-height: 1.45; padding: 12px; margin: 0; white-space: pre-wrap; }
                .kw { color: #7cc7ff; font-weight: 700; }
                .op { color: #c792ea; font-weight: 700; }
                .str { color: #a9dc76; }
                .comment { color: #6f7b87; }
                .num { color: #ffcb6b; }
                </style>
                </head>
                <body><pre>""" + body + """
                </pre></body>
                </html>
                """;
    }

    private static String highlightLine(String line) {
        String stripped = line.stripLeading();
        int indentLength = line.length() - stripped.length();
        if (stripped.startsWith(";")) {
            return escape(line, 0, line.length(), false, false);
        }
        StringBuilder out = new StringBuilder();
        out.append(escape(line, 0, indentLength, false, false));
        int index = indentLength;
        while (index < line.length()) {
            char c = line.charAt(index);
            if (c == '"') {
                int end = findStringEnd(line, index + 1);
                out.append("<span class=\"str\">").append(escape(line, index, end, false, false)).append("</span>");
                index = end;
            } else if (Character.isWhitespace(c)) {
                out.append(c);
                index++;
            } else {
                int end = index + 1;
                while (end < line.length() && !Character.isWhitespace(line.charAt(end))) {
                    end++;
                }
                String token = line.substring(index, end);
                if (STRUCTURAL.contains(token)) {
                    out.append("<span class=\"kw\">").append(escape(token)).append("</span>");
                } else if (OPCODES.contains(token)) {
                    out.append("<span class=\"op\">").append(escape(token)).append("</span>");
                } else if (token.matches("-?\\d+[LFD]?")) {
                    out.append("<span class=\"num\">").append(escape(token)).append("</span>");
                } else {
                    out.append(escape(token));
                }
                index = end;
            }
        }
        return out.toString();
    }

    private static int findStringEnd(String line, int start) {
        boolean escaping = false;
        for (int i = start; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaping) {
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else if (c == '"') {
                return i + 1;
            }
        }
        return line.length();
    }

    private static String escape(String value) {
        return escape(value, 0, value.length(), true, false);
    }

    private static String escape(String value, int start, int end, boolean wrapComments, boolean unused) {
        StringBuilder builder = new StringBuilder();
        boolean comment = wrapComments;
        if (!comment && start == 0 && value.stripLeading().startsWith(";")) {
            builder.append("<span class=\"comment\">");
            comment = true;
        }
        for (int i = start; i < end; i++) {
            char c = value.charAt(i);
            switch (c) {
                case '<' -> builder.append("&lt;");
                case '>' -> builder.append("&gt;");
                case '&' -> builder.append("&amp;");
                case '"' -> builder.append("&quot;");
                default -> builder.append(c);
            }
        }
        if (comment) {
            builder.append("</span>");
        }
        return builder.toString();
    }
}
