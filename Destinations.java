import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.function.Consumer;

class Destinations {

    /**
     * Copyright (c) 2022 Roman Böhm. Subject to the Apache License 2.0.
     * See also https://github.com/romanboehm/jsonwheel.
     */
    @SuppressWarnings({"unchecked"})
    private static class JsonWheel {

        static class JsonWheelException extends RuntimeException {
            JsonWheelException(String msg) {
                super(msg);
            }
        }

        static class WheelNode {
            Object inner;

            WheelNode setInner(Object inner) {
                this.inner = inner;
                return this;
            }

            List<WheelNode> elements() {
                List<WheelNode> list = new ArrayList<>();
                for (Object o : ((List<Object>) inner)) {
                    list.add(new WheelNode().setInner(o));
                }
                return list;
            }

            WheelNode get(String key) {
                return new WheelNode().setInner(((Map<String, Object>) inner).get(key));
            }

            <T> T val(Class<T> clazz) {
                return inner == null ? null : clazz.cast(inner);
            }
        }

        static class Deserializer {
            private static final List<Character> NUMBER_CHARS = Arrays.asList('+', '-', '.', 'e', 'E');
            private static final Map<Character, Character> ESCAPE_LOOKUP = new HashMap<>();

            static {
                ESCAPE_LOOKUP.put('n', '\n');
                ESCAPE_LOOKUP.put('b', '\b');
                ESCAPE_LOOKUP.put('t', '\t');
                ESCAPE_LOOKUP.put('f', '\f');
                ESCAPE_LOOKUP.put('r', '\r');
                ESCAPE_LOOKUP.put('/', '/');
                ESCAPE_LOOKUP.put('\\', '\\');
                ESCAPE_LOOKUP.put('"', '"');
            }

            private final char[] chars;

            Deserializer(char[] chars) {
                this.chars = chars;
            }

            WheelNode readInternal() {
                WheelNode wheelNode = new WheelNode();
                readValue(o -> wheelNode.setInner(o), 0);
                return wheelNode;
            }

            private int readValue(Consumer<Object> valueConsumer, int from) {
                switch (chars[from]) {
                    case '{':
                        Map<String, Object> map = new HashMap<>();
                        valueConsumer.accept(map);
                        return readObjectValue(map, from);
                    case '[':
                        List<Object> list = new ArrayList<>();
                        valueConsumer.accept(list);
                        return readArrayValue(list, from);
                    case '"':
                        int closingQuote = next('"', from + 1);
                        valueConsumer.accept(parseString(from + 1, closingQuote - 1));
                        return closingQuote;
                    case 'n':
                        int nullEnd = readLiteral(from, "null");
                        valueConsumer.accept(null);
                        return nullEnd;
                    case 't':
                        int trueEnd = readLiteral(from, "true");
                        valueConsumer.accept(true);
                        return trueEnd;
                    case 'f':
                        int falseEnd = readLiteral(from, "false");
                        valueConsumer.accept(false);
                        return falseEnd;
                    default:
                        int numberEnd = readNumber(from);
                        valueConsumer.accept(parseNumber(from, numberEnd));
                        return numberEnd;
                }
            }

            private int readObjectValue(Map<String, Object> map, int from) {
                int next = next(from + 1);

                // Check if empty object literal.
                if (chars[next] == '}') {
                    return next;
                }

                // Consume object literal's fields.
                int delim = from;
                do {
                    int keyStart = next('"', delim) + 1;
                    int keyEnd = next('"', keyStart) - 1;
                    String key = parseString(keyStart, keyEnd);
                    int colon = next(':', keyEnd);
                    int valueStart = next(colon + 1);
                    int valueEnd = readValue(v -> map.put(key, v), valueStart);
                    delim = next(valueEnd + 1);
                } while (chars[delim] == ',');

                return delim;
            }

            private int readArrayValue(List<Object> list, int from) {
                int next = next(from + 1);

                // Check if empty array literal.
                if (chars[next] == ']') {
                    return next;
                }

                // Consume array literal's fields.
                int delim = from;
                do {
                    int valueEnd = readValue(v -> list.add(v), next(delim + 1));
                    delim = next(valueEnd + 1);
                } while (chars[delim] == ',');

                return delim;
            }

            private int readNumber(int from) {
                while (from < chars.length && (Character.isDigit(chars[from]) || NUMBER_CHARS.contains(chars[from]))) {
                    from++;
                }
                return from - 1;
            }

            private int readLiteral(int from, String expected) {
                int to = from;
                while (to < chars.length && Character.isLetter(chars[to])) {
                    to++;
                }
                String literal = new String(Arrays.copyOfRange(chars, from, to));
                if (!literal.equals(expected)) {
                    throw new JsonWheelException("Invalid literal '" + literal + "' at " + from);
                }
                return to - 1;
            }

            private int next(char c, int from) {
                char prev = '\0';
                boolean isEscaped = false;
                for (; from < chars.length; from++) {
                    isEscaped = prev == '\\' && !isEscaped; // This handles strings like "\\".
                    char current = chars[from];
                    if (!isEscaped && c == current) {
                        return from;
                    }
                    prev = current;
                }
                throw new JsonWheelException("Could not find " + c + ", checking from " + from);
            }

            private int next(int from) {
                for (; from < chars.length; from++) {
                    if (!Character.isWhitespace(chars[from])) {
                        return from;
                    }
                }
                throw new JsonWheelException("Could not find non-whitespace, checking from " + from);
            }


            private Number parseNumber(int from, int to) {
                String n = new String(Arrays.copyOfRange(chars, from, to + 1));
                try {
                    if (n.contains(".") || n.toLowerCase().contains("e")) {
                        BigDecimal bd = new BigDecimal(n);
                        double dv = bd.doubleValue();
                        if (dv != Double.POSITIVE_INFINITY && dv != Double.NEGATIVE_INFINITY && bd.compareTo(BigDecimal.valueOf(dv)) == 0) { // n within 64 bit precision?
                            return Double.parseDouble(n);
                        }
                        return bd; // Use arbitrary precision
                    }
                    BigInteger bi = new BigInteger(n);
                    if (bi.compareTo(BigInteger.valueOf(bi.intValue())) == 0) { // n within 32 bit precision?
                        return Integer.parseInt(n);
                    }
                    if (bi.compareTo(BigInteger.valueOf(bi.longValue())) == 0) { // n within 64 bit precision?
                        return Long.parseLong(n);
                    }
                    return bi; // Use arbitrary precision
                } catch (NumberFormatException nfe) {
                    throw new JsonWheelException("Invalid number literal " + n + " at " + from + ": " + nfe.getMessage());
                }
            }

            private String parseString(int from, int to) {
                if (from < 0 || to >= chars.length) {
                    throw new JsonWheelException("Out of bounds building String from " + from + " to " + to);
                }
                StringBuilder builder = new StringBuilder();
                while (from <= to) {
                    if (chars[from] == '\\' && from + 1 <= to) {
                        from++; // Skip backslash. Then check
                        // a) codepoint in u-syntax, or ...
                        if (chars[from] == 'u') {
                            int cpStart = from + 1; // Skip "u".
                            int cpEnd = cpStart + 3;
                            if (cpEnd > to) {
                                throw new JsonWheelException("Invalid codepoint at " + from);
                            }
                            builder.appendCodePoint(Integer.parseInt(new String(Arrays.copyOfRange(chars, cpStart, cpEnd + 1)), 16));
                            from = cpEnd;
                            // b) other escaped characters for which we can use the lookup table.
                        } else {
                            Character escapeLookup = ESCAPE_LOOKUP.get(chars[from]);
                            if (escapeLookup == null) {
                                throw new JsonWheelException("Invalid escape sequence at " + from + ": " + chars[from]);
                            }
                            builder.append(escapeLookup);
                        }
                    } else {
                        builder.append(chars[from]);
                    }
                    from++;
                }
                return builder.toString();
            }
        }

        static WheelNode read(String json) {
            char[] chars = json.toCharArray();
            return new Deserializer(chars).readInternal();
        }
    }

    private static final String BAHN_GURU_URI_TEMPLATE = "https://api.direkt.bahn.guru/%s?localTrainsOnly=%b&v=4";

    private final List<String> destinationCodes;
    private final boolean considerStartingPoints;
    private final boolean localTrainsOnly;
    private final int maxDuration;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private Destinations(List<String> destinationCodes, boolean considerStartingPoints, boolean localTrainsOnly, int maxDuration) {
        this.destinationCodes = destinationCodes;
        this.considerStartingPoints = considerStartingPoints;
        this.localTrainsOnly = localTrainsOnly;
        this.maxDuration = maxDuration;
    }

    private static Destinations fromArgs(String... args) {
        boolean considerStartingPoints = false;
        boolean localTrainsOnly = false;
        int maxDuration = 60;
        var destinationCodes = new ArrayList<String>();

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--consider-start") || args[i].startsWith("-s")) {
                considerStartingPoints = true;
                continue;
            }
            if (args[i].startsWith("--local-trains-only") || args[i].startsWith("-l")) {
                localTrainsOnly = true;
                continue;
            }
            if (args[i].startsWith("--max-duration") || args[i].startsWith("-m")) {
                maxDuration = Integer.parseInt(args[i + 1]);
                i++;
                continue;
            }
            destinationCodes.add(args[i]);
        }

        return new Destinations(destinationCodes, considerStartingPoints, localTrainsOnly, maxDuration);
    }

    public static void main(String... args) {
        if (args.length <= 1) {
            var help = """
                    Calculate "common" destinations reachable through direct train connections.
                    Usage: `java Destinations.java [OPTIONS] location-1 location-2 ... location-n`
                        --consider-start | -s           Consider starting points as destinations
                        --local-trains-only | -l        Exclude IC(E) connections
                        --max-duration <int> | -m <int> Maximum duration in minutes
                        --help | -h                     Get help""";
            System.out.println(help);
            System.exit(1);
        }

        try {
            var destinations = Destinations.fromArgs(args);

            destinations.calculate();

            System.exit(0);
        } catch (RuntimeException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void calculate() {
        var maps = destinationCodes.stream()
                .map(this::destinationsToDuration)
                .toList();

        var result = findCommonDestinations(maps);

        result.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getValue().duration))
                .forEachOrdered(e -> System.out.printf("%s: %d%n", e.getValue().name, e.getValue().duration));
    }

    private Map<String, Destination> findCommonDestinations(List<Map<String, Destination>> maps) {
        return maps.stream().reduce(maps.get(0), this::intersect);
    }

    private Map<String, Destination> intersect(Map<String, Destination> thisMap, Map<String, Destination> otherMap) {
        var intersection = new HashMap<String, Destination>();

        for (var thisEntry : thisMap.entrySet()) {
            var thisId = thisEntry.getKey();
            var thisDestination = thisEntry.getValue();

            var otherDestination = otherMap.get(thisId);
            if (otherDestination != null) {
                var destinationWithLongestDuration = thisDestination.duration > otherDestination.duration ? thisDestination : otherDestination;
                intersection.put(thisId, destinationWithLongestDuration);
            }
        }

        return intersection;
    }

    private Map<String, Destination> destinationsToDuration(String destinationCode) {
        var req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BAHN_GURU_URI_TEMPLATE.formatted(destinationCode, localTrainsOnly)))
                .build();
        try {
            var response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            var map = new HashMap<String, Destination>();
            if (considerStartingPoints) {
                map.put(destinationCode, new Destination(destinationCode, null, 0));
            }

            JsonWheel.read(response.body()).elements().stream()
                    .filter(wn -> wn.get("duration").val(Integer.class) <= maxDuration)
                    .forEach(wn -> map.put(
                            wn.get("id").val(String.class),
                            new Destination(
                                    wn.get("id").val(String.class),
                                    wn.get("name").val(String.class),
                                    wn.get("duration").val(Integer.class)
                            )
                    ));

            return map;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private record Destination(String id, String name, int duration) {
    }
}
