package com.vyn.moneyui;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Money {
    public static BigInteger balance = BigInteger.ZERO;
    public static boolean payEnabled = true;
    public static boolean uiVisible = false;
    public static int dingChoice = 1;

    // suffix -> power of ten (case-insensitive)
    private static final Map<String, Integer> SUFFIXES = new LinkedHashMap<>();
    static {
        SUFFIXES.put("k", 3);   SUFFIXES.put("m", 6);   SUFFIXES.put("b", 9);
        SUFFIXES.put("t", 12);  SUFFIXES.put("q", 15);  SUFFIXES.put("qi", 18);
        SUFFIXES.put("sx", 21); SUFFIXES.put("sp", 24); SUFFIXES.put("oc", 27);
        SUFFIXES.put("no", 30); SUFFIXES.put("dc", 33);
    }

    private static final Pattern P = Pattern.compile("^(\\d+(?:\\.\\d+)?)([a-z]*)$");

    /** "1m", "2.5b", "1q", "500", "1,000k" -> BigInteger. Returns null if invalid. */
    public static BigInteger parse(String in) {
        if (in == null) return null;
        Matcher m = P.matcher(in.trim().toLowerCase().replace(",", "").replace("_", ""));
        if (!m.matches()) return null;
        BigDecimal num = new BigDecimal(m.group(1));
        String suf = m.group(2);
        int pow = 0;
        if (!suf.isEmpty()) {
            Integer p = SUFFIXES.get(suf);
            if (p == null) return null;
            pow = p;
        }
        return num.multiply(BigDecimal.TEN.pow(pow)).toBigInteger();
    }

    /** 300000000 -> "300M" */
    public static String abbreviate(BigInteger v) {
        String best = null;
        int bestPow = 0;
        for (Map.Entry<String, Integer> e : SUFFIXES.entrySet()) {
            if (v.compareTo(BigInteger.TEN.pow(e.getValue())) >= 0) {
                best = e.getKey();
                bestPow = e.getValue();
            }
        }
        if (best == null) return v.toString();
        BigDecimal d = new BigDecimal(v).divide(BigDecimal.TEN.pow(bestPow), 2, RoundingMode.DOWN)
                .stripTrailingZeros();
        return d.toPlainString() + best.toUpperCase();
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("moneyui.properties");
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("balance", balance.toString());
        p.setProperty("payEnabled", Boolean.toString(payEnabled));
        p.setProperty("ding", Integer.toString(dingChoice));
        try (var w = Files.newBufferedWriter(file())) {
            p.store(w, "Fake Money UI");
        } catch (IOException ignored) {}
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        Properties p = new Properties();
        try (var r = Files.newBufferedReader(f)) {
            p.load(r);
            balance = new BigInteger(p.getProperty("balance", "0"));
            payEnabled = Boolean.parseBoolean(p.getProperty("payEnabled", "true"));
            dingChoice = Integer.parseInt(p.getProperty("ding", "1"));
        } catch (Exception ignored) {}
    }
}
