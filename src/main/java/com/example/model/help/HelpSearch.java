package com.example.model.help;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class HelpSearch {
    public record Hit(String id, double score) {
    }

    private static final double MIN_SCORE = 3.0;
    private static final double RELATIVE_CUTOFF = 0.45;
    private static final int MAX_HITS = 3;

    private final Set<String> stop = new HashSet<>();
    private final Map<String, Set<String>> tokensById = new LinkedHashMap<>();
    private final Map<String, List<String>> phrasesById = new HashMap<>();
    private final Map<String, Double> weight = new HashMap<>();

    public HelpSearch(HelpText text, List<String> leafIds) {
        if (text.stopwords != null)
            for (String w : text.stopwords)
                stop.add(normalize(w));

        Map<String, Integer> df = new HashMap<>();
        for (String id : leafIds) {
            HelpText.NodeText nt = text.nodes == null ? null : text.nodes.get(id);
            if (nt == null || nt.keywords == null || nt.keywords.isEmpty())
                continue;

            Set<String> tokens = new HashSet<>();
            List<String> phrases = new ArrayList<>();
            if (nt.title != null)
                tokens.addAll(tokenize(nt.title));
            for (String k : nt.keywords) {
                List<String> tk = tokenize(k);
                tokens.addAll(tk);
                if (tk.size() > 1)
                    phrases.add(" " + String.join(" ", tk) + " ");
            }
            tokensById.put(id, tokens);
            phrasesById.put(id, phrases);
            for (String t : tokens)
                df.merge(t, 1, Integer::sum);
        }

        int n = Math.max(1, tokensById.size());
        for (Map.Entry<String, Integer> e : df.entrySet())
            weight.put(e.getKey(), 1 + Math.log((double) n / e.getValue()));
    }

    public List<Hit> search(String query) {
        List<String> all = tokenize(query);
        List<String> q = new ArrayList<>();
        for (String t : all)
            if (!stop.contains(t))
                q.add(t);
        if (q.isEmpty())
            return List.of();

        String padded = " " + String.join(" ", all) + " ";
        List<Hit> hits = new ArrayList<>();

        for (Map.Entry<String, Set<String>> e : tokensById.entrySet()) {
            double score = 0;
            for (String qt : q) {
                double best = 0;
                for (String t : e.getValue()) {
                    double points = 0;
                    if (qt.equals(t))
                        points = 3;
                    else if (Math.min(qt.length(), t.length()) >= 4 && (qt.startsWith(t) || t.startsWith(qt)))
                        points = 2;
                    if (points > 0)
                        best = Math.max(best, points * weight.getOrDefault(t, 1.0));
                }
                score += best;
            }
            for (String phrase : phrasesById.get(e.getKey()))
                if (padded.contains(phrase))
                    score += 4;
            if (score >= MIN_SCORE)
                hits.add(new Hit(e.getKey(), score));
        }

        hits.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<Hit> out = new ArrayList<>();
        if (hits.isEmpty())
            return out;
        double top = hits.get(0).score();
        for (Hit h : hits) {
            if (out.size() >= MAX_HITS || h.score() < top * RELATIVE_CUTOFF)
                break;
            out.add(h);
        }
        return out;
    }

    static String normalize(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        String n = normalize(s);
        if (n.isEmpty())
            return out;
        for (String t : n.split("\\s+"))
            if (t.length() >= 2)
                out.add(t);
        return out;
    }
}