package com.example.model.io;

import com.example.model.io.PageContent.FloatingTableContent;

public final class FloatingTableCodec {
    private static final String START = "\uE030TABLE:";
    private static final String END = "\uE031";

    private FloatingTableCodec() {
    }

    public static String encode(FloatingTableContent t) {
        StringBuilder sb = new StringBuilder(START);
        sb.append(t.x).append(',').append(t.y).append('|');
        sb.append(join(t.colWidths)).append('|').append(join(t.rowHeights)).append('|');
        sb.append(joinMatrix(t.offX)).append('|').append(joinMatrix(t.offY)).append('|');
        sb.append(joinMerges(t.merges)).append('|');
        boolean first = true;
        for (String[] row : t.cells) {
            for (String cell : row) {
                if (!first)
                    sb.append('~');
                first = false;
                sb.append(cell);
            }
        }
        return sb.append(END).toString();
    }

    public static boolean isToken(String text) {
        return text != null && text.startsWith(START) && text.endsWith(END);
    }

    public static FloatingTableContent decode(String token) {
        try {
            String body = token.substring(START.length(), token.length() - END.length());
            String[] parts = body.split("\\|", -1);
            if (parts.length != 7)
                return FloatingTableContent.empty(); 
            String[] pos = parts[0].split(",");
            double[] cols = parseDoubles(parts[1]);
            double[] rows = parseDoubles(parts[2]);
            double[][] offX = parseMatrix(parts[3], rows.length, cols.length);
            double[][] offY = parseMatrix(parts[4], rows.length, cols.length);
            int[][] merges = parseMerges(parts[5]);
            String[] flat = parts[6].split("~", -1);

            String[][] cells = new String[rows.length][cols.length];
            for (int r = 0; r < rows.length; r++)
                for (int c = 0; c < cols.length; c++) {
                    int k = r * cols.length + c;
                    cells[r][c] = k < flat.length ? flat[k] : "";
                }
            return new FloatingTableContent(Double.parseDouble(pos[0]), Double.parseDouble(pos[1]),
                    cols, rows, offX, offY, merges, cells);
        } catch (RuntimeException e) {
            return FloatingTableContent.empty();
        }
    }

    private static String join(double[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0)
                sb.append(',');
            sb.append(values[i]);
        }
        return sb.toString();
    }

    private static String joinMatrix(double[][] m) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < m.length; r++) {
            if (r > 0)
                sb.append(';');
            sb.append(join(m[r]));
        }
        return sb.toString();
    }

    private static String joinMerges(int[][] merges) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < merges.length; i++) {
            if (i > 0)
                sb.append(';');
            sb.append(merges[i][0]).append(',').append(merges[i][1]).append(',')
                    .append(merges[i][2]).append(',').append(merges[i][3]);
        }
        return sb.toString();
    }

    private static double[] parseDoubles(String s) {
        String[] p = s.split(",");
        double[] out = new double[p.length];
        for (int i = 0; i < p.length; i++)
            out[i] = Double.parseDouble(p[i]);
        return out;
    }

    private static double[][] parseMatrix(String s, int rows, int cols) {
        double[][] m = new double[rows][cols];
        String[] rs = s.split(";", -1);
        for (int r = 0; r < rs.length && r < rows; r++) {
            String[] vs = rs[r].split(",", -1);
            for (int c = 0; c < vs.length && c < cols; c++)
                if (!vs[c].isEmpty())
                    m[r][c] = Double.parseDouble(vs[c]);
        }
        return m;
    }

    private static int[][] parseMerges(String s) {
        if (s.isEmpty())
            return new int[0][];
        String[] ms = s.split(";");
        int[][] out = new int[ms.length][4];
        for (int i = 0; i < ms.length; i++) {
            String[] v = ms[i].split(",");
            for (int k = 0; k < 4; k++)
                out[i][k] = Integer.parseInt(v[k]);
        }
        return out;
    }
}