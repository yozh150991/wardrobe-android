package com.familywardrobe.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Host-only helper for tools/check_qa_regression.py: prints the phone verdicts for a Unicode corpus so the
 * script can compare them with the family server rules (client/server parity, technique EP/EG).
 * Input: one string per line as space-separated hex code points. Output per line, tab-separated:
 * hasVisibleText, serverEmail, CategoryRules.clean as hex, CategoryRules.validName(clean).
 */
public final class QaParityDump {
    private QaParityDump() {}

    public static void main(String[] args) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (String line; (line = in.readLine()) != null; ) {
            StringBuilder text = new StringBuilder();
            for (String hex : line.trim().split(" ")) if (!hex.isEmpty()) text.appendCodePoint(Integer.parseInt(hex, 16));
            String value = text.toString();
            String clean = CategoryRules.clean(value);
            out.append(TextLimits.hasVisibleText(value)).append('\t')
               .append(hex(AuthRules.serverEmail(value))).append('\t')
               .append(hex(clean)).append('\t')
               .append(CategoryRules.validName(clean)).append('\n');
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }

    private static String hex(String value) {
        StringBuilder out = new StringBuilder("-");
        value.codePoints().forEach(c -> out.append(' ').append(Integer.toHexString(c)));
        return out.toString();
    }
}
