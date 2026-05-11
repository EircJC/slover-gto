package com.solvergto.service.inference;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ActionHistoryParser {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private ActionHistoryParser() {
    }

    public static List<ActionEvent> parse(List<String> lines, String heroPosition, String villainPosition) {
        List<ActionEvent> events = new ArrayList<>();
        if (lines == null) {
            return events;
        }
        Street stage = Street.PREFLOP;
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String normalized = line.trim().toLowerCase(Locale.ROOT);
            stage = inferStreet(normalized, stage);
            Actor actor = inferActor(normalized, heroPosition, villainPosition);
            Action action = inferAction(normalized);
            SizeKind sizeKind = inferSizeKind(normalized, action);
            Double sizeValue = inferSizeValue(normalized, action);
            events.add(new ActionEvent(line.trim(), stage, actor, action, sizeKind, sizeValue));
        }
        return events;
    }

    private static Street inferStreet(String normalized, Street current) {
        if (normalized.contains("river")) {
            return Street.RIVER;
        }
        if (normalized.contains("turn")) {
            return Street.TURN;
        }
        if (normalized.contains("flop")) {
            return Street.FLOP;
        }
        if (normalized.contains("preflop")) {
            return Street.PREFLOP;
        }
        return current;
    }

    private static Actor inferActor(String normalized, String heroPosition, String villainPosition) {
        String hero = normalizePosition(heroPosition);
        String villain = normalizePosition(villainPosition);
        if (containsToken(normalized, "hero") || containsToken(normalized, hero)) {
            return Actor.HERO;
        }
        if (containsToken(normalized, "villain") || containsToken(normalized, villain)) {
            return Actor.VILLAIN;
        }
        throw new IllegalArgumentException("Cannot determine actor from action history line: " + normalized);
    }

    private static Action inferAction(String normalized) {
        if (normalized.contains("all-in") || normalized.contains("all in") || normalized.contains("jam")) {
            return Action.JAM;
        }
        if (normalized.contains("3bet") || normalized.contains("4bet") || normalized.contains("raise") || normalized.contains("open")) {
            return Action.RAISE;
        }
        if (normalized.contains("bet")) {
            return Action.BET;
        }
        if (normalized.contains("call")) {
            return Action.CALL;
        }
        if (normalized.contains("check")) {
            return Action.CHECK;
        }
        if (normalized.contains("fold")) {
            return Action.FOLD;
        }
        throw new IllegalArgumentException("Cannot determine action from action history line: " + normalized);
    }

    private static SizeKind inferSizeKind(String normalized, Action action) {
        if (action == Action.JAM) {
            return SizeKind.STACK;
        }
        if (normalized.contains("%")) {
            return SizeKind.PERCENT_POT;
        }
        if (NUMBER_PATTERN.matcher(normalized).find()) {
            return SizeKind.CHIPS;
        }
        return SizeKind.NONE;
    }

    private static Double inferSizeValue(String normalized, Action action) {
        if (action == Action.JAM) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return null;
    }

    private static boolean containsToken(String text, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return text.contains(token.toLowerCase(Locale.ROOT));
    }

    private static String normalizePosition(String position) {
        if (position == null) {
            return "";
        }
        String normalized = position.trim().toUpperCase(Locale.ROOT);
        if ("SB".equals(normalized)) {
            return "BTN";
        }
        return normalized;
    }

    public enum Actor {
        HERO,
        VILLAIN
    }

    public enum Action {
        RAISE,
        BET,
        CALL,
        CHECK,
        FOLD,
        JAM
    }

    public enum Street {
        PREFLOP,
        FLOP,
        TURN,
        RIVER
    }

    public enum SizeKind {
        NONE,
        CHIPS,
        PERCENT_POT,
        STACK
    }

    public record ActionEvent(
            String rawText,
            Street street,
            Actor actor,
            Action action,
            SizeKind sizeKind,
            Double sizeValue
    ) {
    }
}
