package org.argcc.string_function;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

public class StringFunctionHandler {

    private String prefix = "";
    private String argStart = "(";
    private String argEnd = ")";

    private Map<String, Map<String, AbstractMap.SimpleEntry<Method, Object>>> functionCache = new HashMap<>();

    private List<Object> functions;

    public StringFunctionHandler() {
    }

    /**
     * Getting list of objects whose methods are used as functions
     * @return
     */
    public List<Object> getFunctions() {
        return functions;
    }

    /**
     * Sets list of objects whose methods are used as functions
     * @param functions
     * @return
     */
    public StringFunctionHandler setFunctions(List<Object> functions) {
        if (functions != null) {
            this.functions = functions.stream().filter(Objects::nonNull).collect(Collectors.toList());
        } else {
            this.functions = null;
        }
        functionCache.clear();
        return this;
    }

    /**
     * Sets an array of objects whose methods are used as functions
     * @param functions
     * @return
     */
    public StringFunctionHandler setFunctions(Object... functions) {
        if (functions != null) {
            this.functions = Arrays.stream(functions).filter(Objects::nonNull).collect(Collectors.toList());
        } else {
            this.functions = null;
        }
        functionCache.clear();
        return this;
    }

    /**
     * Sets an object whose methods are used as functions
     * @param functions
     * @return
     */
    public StringFunctionHandler setFunctions(Object functions) {
        if (functions != null) {
            this.functions = new ArrayList<>();
            this.functions.add(functions);
        } else {
            this.functions = null;
        }
        functionCache.clear();
        return this;
    }

    /**
     * Specifies the semantics of a function call. Examples: "name(args)", "$name<[args]>", "#name{args}"
     * @param template
     * @return
     */
    public StringFunctionHandler setTemplate(String template) {
        if (template == null || template.isEmpty()) {
            throw new RuntimeException("template should not be empty");
        }

        int name = template.indexOf("name");
        if (name < 0) {
            throw new RuntimeException("template should contain word 'name'");
        }

        int args = template.indexOf("args");
        if (args < 0) {
            throw new RuntimeException("template should contain word 'args'");
        }
        if (name > args) {
            throw new RuntimeException("word 'args' should be placed after word 'name'");
        }

        prefix = template.substring(0, name).trim();
        argStart = template.substring(name + 4, args).trim();
        if (argStart.isEmpty()) {
            throw new RuntimeException("arguments start symbols should not be empty");
        }
        if (argStart.startsWith(",")) {
            throw new RuntimeException("arguments start symbol should not be comma");
        }
        if (containsQuote(argStart)) {
            throw new RuntimeException("arguments start symbol should not be quote");
        }
        if (argStart.charAt(0) >= '0' && argStart.charAt(0) <= 9) {
            throw new RuntimeException("arguments start symbol should not be digit");
        }

        argEnd = template.substring(args + 4).trim();
        if (argEnd.isEmpty()) {
            throw new RuntimeException("arguments end symbols should not be empty");
        }
        if (argEnd.startsWith(",")) {
            throw new RuntimeException("arguments end symbol should not be comma");
        }
        if (containsQuote(argEnd)) {
            throw new RuntimeException("arguments end symbol should not be quote");
        }
        if (argEnd.charAt(0) >= '0' && argEnd.charAt(0) <= 9) {
            throw new RuntimeException("arguments end symbol should not be digit");
        }
        functionCache.clear();
        return this;
    }

    /**
     * Processes a string, replacing function calls with the result of calling the appropriate methods.
     * @param text
     * @return
     */
    public String handle(String text) {
        if (text == null || text.isEmpty() || functions == null || functions.isEmpty()) {
            return text;
        }

        if (functionCache.isEmpty()) {
            obtainCache();
        }
        StringBuilder str = new StringBuilder(text.length() * 2).append(text);

        for (String funcName : functionCache.keySet()) {
            int start = str.indexOf(funcName);
            while (start >= 0) {
                start = handleFunc(str, start, funcName, functionCache.get(funcName));
                start = str.indexOf(funcName, start);
            }
        }

        return str.toString();
    }

    private void obtainCache() {
        functionCache.clear();
        if (functions != null) {
            StringBuilder strBuilder = new StringBuilder(68);
            for (Object obj : functions) {
                Class cls = obj.getClass();
                while (cls != null && cls != Object.class) {
                    methodsFor:
                    for (Method method : cls.getDeclaredMethods()) {
                        if (!method.getReturnType().equals(Void.TYPE)
                                && Modifier.isPublic(method.getModifiers())) {
                            strBuilder.delete(0, strBuilder.length());
                            for (Class<?> paramType : method.getParameterTypes()) {
                                if (String.class.isAssignableFrom(paramType)
                                        || Integer.class.isAssignableFrom(paramType)
                                        || Long.class.isAssignableFrom(paramType)
                                        || Double.class.isAssignableFrom(paramType)
                                        || Float.class.isAssignableFrom(paramType)) {
                                    strBuilder.append(paramType.getSimpleName().charAt(0));
                                } else {
                                    continue methodsFor;
                                }
                            }

                            String name = method.isAnnotationPresent(FuncName.class)
                                    ? method.getAnnotation(FuncName.class).value()
                                    : method.getName();
                            functionCache
                                    .computeIfAbsent(prefix + name + argStart, k1 -> new HashMap<>())
                                    .computeIfAbsent(strBuilder.toString(), k2 -> new AbstractMap.SimpleEntry<>(method, obj));
                        }
                    }
                    cls = cls.getSuperclass();
                }
            }
        }
    }

    private int handleFunc(StringBuilder str,
                            int start,
                            String funcName,
                            Map<String, AbstractMap.SimpleEntry<Method, Object>> variants) {
        int arg = start + funcName.length();
        ArgsParser parser = new ArgsParser(arg, str);
        parser.skipSpace();
        if (parser.isEnd()) {
            return start + 1;
        }

        //single string parameter may have no quotes
        if (variants.size() == 1 && variants.containsKey("S")) {
            if (!parser.isQuote()) {
                int end = str.indexOf(argEnd, arg);
                if (end < 0) {
                    return start + 1;
                }
                String literal = str.substring(arg, end);
                AbstractMap.SimpleEntry<Method, Object> v = variants.get("S");
                String result = call(v.getKey(), v.getValue(), literal);
                str.replace(start, end + argEnd.length(), result);
                return start + result.length();
            }
        }

        List<Object> parameters = parser.parseParameters();
        if (parameters == null) {
            return start + 1;
        }
        List<String> filteredVariants = new ArrayList<>(variants.keySet());
        filteredVariants.removeIf(v -> v.length() != parameters.size());
        for (int i = 0; i < parameters.size(); i++) {
            Integer idx = i;
            if (parameters.get(i) instanceof String) {
                filteredVariants.removeIf(v -> v.charAt(idx) != 'S');
            } else {
                filteredVariants.removeIf(v -> v.charAt(idx) == 'S');
            }
        }

        if (filteredVariants.isEmpty()) {
            return start + 1;
        }

        if (filteredVariants.size() > 1) {
            filteredVariants.sort(new ArgWeightComparator(parameters));
        }

        String variant = filteredVariants.get(0);
        adjustParameters(parameters, variant);
        AbstractMap.SimpleEntry<Method, Object> v = variants.get(variant);
        String result = call(v.getKey(), v.getValue(), parameters.toArray());
        str.replace(start, parser.getIndex(), result);
        return start + result.length();
    }

    private static void adjustParameters(List<Object> parameters, String args) {
        for (int i = 0; i < parameters.size(); i++) {
            Object obj = parameters.get(i);
            char ch = args.charAt(i);
            if (obj.getClass().getSimpleName().charAt(0) != ch) {
                if (obj instanceof Number) {
                    Number num = (Number) obj;
                    switch (ch) {
                        case 'D':
                            parameters.set(i, num.doubleValue());
                            break;
                        case 'F':
                            parameters.set(i, num.floatValue());
                            break;
                        case 'I':
                            parameters.set(i, num.intValue());
                            break;
                        case 'L':
                            parameters.set(i, num.longValue());
                            break;
                        default:
                    }
                }
            }
        }
    }

    private static class ArgWeightComparator implements Comparator<String> {

        private List<Object> parameters;

        public ArgWeightComparator(List<Object> parameters) {
            this.parameters = parameters;
        }

        public int getParamWeight(Object obj, char ch) {
            if (obj.getClass().getSimpleName().charAt(0) == ch) {
                return 3;
            }

            if (obj instanceof Float || obj instanceof Double) {
                switch (ch) {
                    case 'D':
                    case 'F':
                        return 2;
                    case 'I':
                    case 'L':
                        return 1;
                    default:
                }
            }

            if (obj instanceof Long || obj instanceof Integer) {
                switch (ch) {
                    case 'I':
                    case 'L':
                        return 2;
                    case 'F':
                    case 'D':
                        return 1;
                    default:
                }
            }
            return 0;
        }

        public int getWeight(String s) {
            int w = 0;
            for (int i = 0; i < parameters.size(); i++) {
                w += getParamWeight(parameters.get(i), s.charAt(i));
            }
            return w;
        }

        @Override
        public int compare(String o1, String o2) {
            return getWeight(o2) - getWeight(o1);
        }
    }

    private String call(Method method, Object obj, Object... args) {
        try {
            Object result = method.invoke(obj, args);
            if (result == null) {
                return "null";
            }
            if (result instanceof String) {
                return (String) result;
            }
            return result.toString();
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    private static boolean isQuote(char ch) {
        return ch == '"' || ch == '\'' || ch == '`';
    }

    private static boolean containsQuote(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (isQuote(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private class ArgsParser {
        int index;
        StringBuilder str;

        public ArgsParser(int index, StringBuilder str) {
            this.index = index;
            this.str = str;
        }

        public int getIndex() {
            return index;
        }

        public char get() {
            return str.charAt(index);
        }

        public void step() {
            index++;
        }

        public boolean isEnd() {
            return index >= str.length();
        }

        public boolean isNoMoreArguments() {
            return str.indexOf(argEnd, index) == index;
        }

        public void skipSpace() {
            if (!isEnd()) {
                char ch = get();
                while (ch == ' ' || ch == '\r' || ch == '\n' || ch == '\t' || ch == ' ') {
                    step();
                    if (isEnd()) {
                        return;
                    }
                    ch = get();
                }
            }
        }

        public boolean isQuote() {
            return StringFunctionHandler.isQuote(get());
        }

        public String parseString() {
            skipSpace();
            if (isEnd()) {
                return null;
            }

            if (!isQuote()) {
                return null;
            }
            char quote = get();
            step();
            StringBuilder ret = new StringBuilder();

            while (true) {
                if (isEnd()) {
                    return null;
                }
                char ch = get();
                if (ch == quote) {
                    step();
                    if (isEnd() || get() != quote) {
                        return ret.toString();
                    }
                }
                ret.append(ch);
                step();
            }
        }

        public List<Object> parseParameters() {
            List<Object> ret = new ArrayList<>();
            skipSpace();
            if (isEnd()) {
                return null;
            }
            if (isNoMoreArguments()) {
                index += argEnd.length();
                return ret;
            }
            while (true) {
                Object obj = parseParameter();
                if (obj == null) {
                    return null;
                }
                ret.add(obj);
                skipSpace();
                if (isEnd()) {
                    return null;
                }
                if (isNoMoreArguments()) {
                    index += argEnd.length();
                    return ret;
                }
                if (get() != ',') {
                    return null;
                }
                step();
            }
        }

        private Object parseParameter() {
            skipSpace();
            if (isEnd()) {
                return null;
            }
            if (isQuote()) {
                return parseString();
            }

            int endComma = str.indexOf(",", index);
            int endArg = str.indexOf(argEnd, index);
            if (endComma == -1 && endArg == -1) {
                return null;
            }
            int end;
            if (endComma < 0) {
                end = endArg;
            } else if (endArg < 0) {
                end = endComma;
            } else {
                end = Math.min(endComma, endArg);
            }

            String lit = str.substring(index, end).trim().toLowerCase();
            index = end;
            try {
                if (lit.contains("f")) {
                    return Float.parseFloat(lit);
                }

                if (lit.contains("d") || lit.contains("e") || lit.contains(".")) {
                    return Double.parseDouble(lit);
                }

                if (lit.contains("l")) {
                    return Long.parseLong(lit);
                }

                return Integer.parseInt(lit);
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
    }
}
