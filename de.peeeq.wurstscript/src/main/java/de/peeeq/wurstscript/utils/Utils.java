package de.peeeq.wurstscript.utils;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.io.Files;
import de.peeeq.wurstio.Pjass;
import de.peeeq.wurstscript.ast.*;
import de.peeeq.wurstscript.attributes.names.NameLink;
import de.peeeq.wurstscript.jassIm.JassImElementWithName;
import de.peeeq.wurstscript.parser.WPos;
import org.eclipse.jdt.annotation.Nullable;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class Utils {

    @SuppressWarnings("rawtypes")
    public static int size(Iterable<?> i) {
        if (i instanceof Collection) {
            return ((Collection) i).size();
        }
        int size = 0;
        for (@SuppressWarnings("unused")
                Object o : i) {
            size++;
        }
        return size;
    }

    public static void printIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append("\t");
        }
    }


    @SafeVarargs
    public static <T> List<T> list(T... args) {
        List<T> result = new NotNullList<>();
        Collections.addAll(result, args);
        return result;
    }

    public static <T> List<T> removedDuplicates(List<T> list) {
        List<T> result = new NotNullList<>();
        for (T t : list) {
            if (!result.contains(t)) {
                result.add(t);
            }
        }
        return result;

    }

    // public static void visitPostOrder(SortPos p, Function<SortPos, Void>
    // func) {
    // p = p.postOrderStart();
    // while (p != null) {
    // func.apply(p);
    // p = p.postOrder();
    // }
    // }


    public static <T> void printSep(StringBuilder sb, String seperator, T[] args) {
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(seperator);
            }
            sb.append(args[i].toString());
        }
    }


    public static int parseInt(String yytext) {
        if (yytext.startsWith("0")) {
            return Integer.parseInt(yytext, 8);
        } else {
            return Integer.parseInt(yytext);
        }
    }

    public static int parseAsciiInt(String yytext) throws NumberFormatException {
        if (yytext.length() == 3) {
            // 'x' can directly return
            return yytext.charAt(1);
        }
        int result = 0;
        int i = 1;
        int chars = 0;
        for (; i < yytext.length() - 1; i++) {
            if (yytext.charAt(i) == '\\') {
                i++;
            }
            result = result * 256 + yytext.charAt(i);
            chars++;
        }
        if (chars != 1 && chars != 4) {
            throw new NumberFormatException("Ascii ints must have 4 or 1 characters but this one has " + chars + " characters.");
        }
        return result;
    }


    public static int parseHexInt(String yytext, int offset) {
        return (int) Long.parseLong(yytext.substring(offset), 16);
    }

    public static String printSep(String sep, String[] args) {
        StringBuilder sb = new StringBuilder();
        printSep(sb, sep, args);
        return sb.toString();
    }

    /**
     * is a piece of code jass code?
     */
    public static boolean isJassCode(@Nullable Element pos) {
        while (pos != null) {
            if (pos instanceof WPackage) {
                return false; // code is inside package -> wurstscript code
            }
            pos = pos.getParent();
        }
        return true; // no package found -> jass code
    }


    public static <T> String join(Iterable<T> hints, String seperator) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (T s : hints) {
            if (!first) {
                result.append(seperator);
            }
            result.append(s);
            first = false;
        }
        return result.toString();
    }

    public static <T> String join(T[] arguments, String seperator) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (T s : arguments) {
            if (!first) {
                result.append(seperator);
            }
            result.append(s);
            first = false;
        }
        return result.toString();
    }


    /**
     * sorts a list with partitial ordering topologically. If a > b then a will
     * appear before b in the result list
     *
     * @param items       items to sort
     * @param biggerItems a function to get all the bigger items for a given item
     * @return a sorted list
     * @throws TopsortCycleException if there exist items a,b so that a > b and b > a
     */
    public static <T> List<T> topSort(Collection<T> items,
                                      java.util.function.Function<T, ? extends Collection<T>> biggerItems)
            throws TopsortCycleException {
        Set<T> visitedItems = new HashSet<>();
        List<T> result = new ArrayList<>(items.size());
        LinkedList<T> activeItems = Lists.newLinkedList();
        for (T t : items) {
            if (t == null)
                throw new IllegalArgumentException();
            topSortHelper(result, visitedItems, activeItems, biggerItems::apply, t);
        }
        return result;
    }


    private static <T> void topSortHelper(List<T> result, Set<T> visitedItems,
                                          LinkedList<T> activeItems,
                                          Function<T, ? extends Collection<T>> biggerItems, T item)
            throws TopsortCycleException {
        if (visitedItems.contains(item)) {
            return;
        }
        if (activeItems.contains(item)) { // This is not constant time, could be
            // more efficient
            while (activeItems.get(0) != item) {
                activeItems.remove(0);
            }
            throw new TopsortCycleException(activeItems);
        }
        activeItems.add(item);
        visitedItems.add(item);
        for (T t : biggerItems.apply(item)) {
            if (t == null)
                throw new IllegalArgumentException();
            topSortHelper(result, visitedItems, activeItems, biggerItems, t);
        }
        result.add(item);
        activeItems.removeLast();
    }

    @SafeVarargs
    public static <T> boolean oneOf(T obj, T... ts) {
        for (T t : ts) {
            if (t.equals(obj)) {
                return true;
            }
        }
        return false;
    }


    public static <T> T getFirst(Iterable<T> ts) {
        for (T t : ts) {
            return t;
        }
        throw new Error("collection has no first element");
    }


    private static <T> void topSortHelperIgnoreCycles(List<T> result,
                                                      Set<T> visitedItems,
                                                      java.util.function.Function<T, ? extends Collection<T>> biggerItems, T item) {
        if (visitedItems.contains(item)) {
            return;
        }
        visitedItems.add(item);
        for (T t : biggerItems.apply(item)) {
            if (t == null)
                throw new IllegalArgumentException();
            topSortHelperIgnoreCycles(result, visitedItems, biggerItems, t);
        }
        result.add(item);
    }


    public static String printElement(@Nullable Element e) {
        if (e == null) {
            return "null";
        }
        String type = makeReadableTypeName(e);
        String name = "";
        if (e instanceof ExprFunctionCall) {
            ExprFunctionCall fc = (ExprFunctionCall) e;
            return "function call " + fc.getFuncName() + "()";
        } else if (e instanceof FuncDef) {
            FuncDef fd = (FuncDef) e;
            return "function " + fd.getName();
        } else if (e instanceof OnDestroyDef) {
            return "destroy function for "
                    + e.attrNearestStructureDef().getName();
        } else if (e instanceof ConstructorDef) {
            return "constructor for " + e.attrNearestStructureDef().getName();
        } else if (e instanceof LocalVarDef) {
            LocalVarDef l = (LocalVarDef) e;
            return "local variable " + l.getName();
        } else if (e instanceof VarDef) {
            VarDef l = (VarDef) e;
            return "variable " + l.getName();
        } else if (e instanceof AstElementWithNameId) {
            name = ((AstElementWithNameId) e).getNameId().getName();
        } else if (e instanceof WImport) {
            WImport wImport = (WImport) e;
            return "import " + wImport.getPackagename();
        } else if (e instanceof TypeExprSimple) {
            TypeExprSimple t = (TypeExprSimple) e;
            name = t.getTypeName();
            if (t.getTypeArgs().size() > 0) {
                name += "{";
                boolean first = true;
                StringBuilder nameBuilder = new StringBuilder(name);
                for (TypeExpr ta : t.getTypeArgs()) {
                    if (!first) {
                        nameBuilder.append(", ");
                    }
                    nameBuilder.append(printElement(ta));
                    first = false;
                }
                name = nameBuilder.toString();
                name += "}";
            }
            type = "type";
        }
        return type + " " + name;
    }

    private static String makeReadableTypeName(Element e) {
        String type = e.getClass().getName()
                .replaceAll("de.peeeq.wurstscript.ast.", "")
                .replaceAll("Impl$", "").replaceAll("Def$", "").toLowerCase();
        if (type.equals("wpackage")) {
            type = "package";
        }
        return type;
    }

    public static int inBorders(int min, int x, int max) {
        return Math.max(min, Math.min(max, x));
    }

    public static String printStackTrace(StackTraceElement[] stackTrace) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement s : stackTrace) {
            sb.append(s.toString());
            sb.append("\n");
        }
        return sb.toString();
    }

    public static String printExceptionWithStackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t);
        sb.append("\n");
        for (; ; ) {
            for (StackTraceElement s : t.getStackTrace()) {
                sb.append(s.toString());
                sb.append("\n");
            }
            t = t.getCause();
            if (t == null) {
                break;
            }
            sb.append("Caused by: ").append(t.toString()).append("\n");
        }
        return sb.toString();
    }


    /**
     * calculates the transient closure of a multimap
     */
    public static <T> Multimap<T, T> transientClosure(Multimap<T, T> start) {
        Multimap<T, T> result = HashMultimap.create();
        result.putAll(start);
        Multimap<T, T> changes = HashMultimap.create();
        boolean changed;
        do {
            changes.clear();
            for (Entry<T, T> e1 : result.entries()) {
                for (T t : result.get(e1.getValue())) {
                    changes.put(e1.getKey(), t);
                }
            }
            changed = result.putAll(changes);

        } while (changed);

        return result;
    }

    public static Element getAstElementAtPos(Element elem,
                                             int caretPosition, boolean usesMouse) {
        List<Element> betterResults = Lists.newArrayList();
        for (int i = 0; i < elem.size(); i++) {
            Element e = elem.get(i);
            if (elementContainsPos(e, caretPosition, usesMouse)) {
                betterResults.add(getAstElementAtPos(e, caretPosition, usesMouse));
            }
        }
        if (betterResults.size() == 0) {
            if (elem instanceof Identifier) {
                return elem.getParent();
            }
            return elem;
        } else {
            return bestResult(betterResults);
        }
    }

    public static Element getAstElementAtPos(Element elem, int line, int column, boolean usesMouse) {
//		System.out.println("get element " + Utils.printElement(elem)  
//			+ "(" + elem.attrSource().getLeftPos() + " - " + elem.attrSource().getRightPos() + ")");
        List<Element> betterResults = Lists.newArrayList();
        for (int i = 0; i < elem.size(); i++) {
            Element e = elem.get(i);
            if (elementContainsPos(e, line, column, usesMouse) || e.attrSource().isArtificial()) {
                betterResults.add(getAstElementAtPos(e, line, column, usesMouse));
            }
        }
        Element bestResult = bestResult(betterResults);
        if (bestResult == null) {
            if (elem instanceof Identifier) {
                return elem.getParent();
            }
            return elem;
        } else {
            return bestResult;
        }
    }


    public static Element getAstElementAtPosIgnoringLists(Element elem,
                                                          int caretPosition, boolean usesMouse) {
        Element r = getAstElementAtPos(elem, caretPosition, usesMouse);
        while (r instanceof List<?>) {
            r = r.getParent();
        }
        return r;
    }

    /**
     * return the element with the smallest size
     */
    private static Element bestResult(List<Element> betterResults) {
        int minSize = Integer.MAX_VALUE;
        Element min = null;
        for (Element e : betterResults) {
            WPos source = e.attrSource();
            int size = source.isArtificial() ?
                    Integer.MAX_VALUE
                    : source.getRightPos() - source.getLeftPos();
            if (size < minSize) {
                minSize = size;
                min = e;
            }
        }
        return min;
    }

    public static boolean elementContainsPos(Element e, int pos, boolean usesMouse) {
        return e.attrSource().getLeftPos() <= pos
                && e.attrSource().getRightPos() >= pos + (usesMouse ? 1 : 0);
    }

    private static boolean elementContainsPos(Element e, int line, int column, boolean usesMouse) {
        WPos pos = e.attrSource();
        if (pos.getLine() > line) {
            return false;
        }
        if (pos.getEndLine() < line) {
            return false;
        }
        return (pos.getLine() < line || pos.getStartColumn() <= column)
                && (pos.getEndLine() > line || pos.getEndColumn() >= column);
    }


    public static <T extends NameDef> List<T> sortByName(
            Collection<T> c) {
        List<T> r = Lists.newArrayList(c);
        r.sort(Comparator.comparing(NameDef::getName));
        return r;
    }

    public static String printPos(WPos source) {
        return source.getFile() + ", line " + source.getLine();
    }

    public static boolean isEmptyCU(@Nullable CompilationUnit cu) {
        return (cu == null)
                || (cu.getJassDecls().size() + cu.getPackages().size() == 0);
    }

    public static String printElementWithSource(Element e) {
        WPos src = e.attrSource();
        return printElement(e) + " (" + src.getFile() + ":"
                + src.getLine() + ")";
    }

    public static int[] copyArray(int[] ar) {
        int[] r = new int[ar.length];
        System.arraycopy(ar, 0, r, 0, ar.length);
        return r;
    }

    public static String toFirstUpper(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    public static @Nullable VarDef getParentVarDef(@Nullable Element node) {
        while (node != null) {
            if (node instanceof VarDef) {
                return (VarDef) node;
            }
            node = node.getParent();
        }
        return null;
    }


    public static String printAlternatives(Collection<? extends NameLink> alternatives) {
        List<String> result = Lists.newArrayList();
        for (NameLink a : alternatives) {
            WPos source = a.getDef().attrSource();
            String s = Utils.printElement(a.getDef()) + " defined in line "
                    + source.getLine() + " (" + source.getFile() + ")";
            result.add(s);
        }
        return " * " + Utils.join(result, "\n * ");
    }

    public static <T, S> Multimap<T, S> inverse(Multimap<S, T> orig) {
        Multimap<T, S> result = HashMultimap.create();
        for (Entry<S, T> e : orig.entries()) {
            result.put(e.getValue(), e.getKey());
        }
        return result;
    }


    public static boolean isSubsequenceIgnoreCase(String a, String b) {
        int bPos = -1;
        for (int i = 0; i < a.length(); i++) {
            char c = Character.toLowerCase(a.charAt(i));
            do {
                bPos++;
                if (bPos >= b.length()) {
                    return false;
                }
            } while (Character.toLowerCase(b.charAt(bPos)) != c);
        }
        return true;
    }

    public static boolean isSubsequence(String a, String b) {
        int bPos = -1;
        for (int i = 0; i < a.length(); i++) {
            char c = a.charAt(i);
            do {
                bPos++;
                if (bPos >= b.length()) {
                    return false;
                }
            } while (b.charAt(bPos) != c);
        }
        return true;
    }


    public static List<Integer> subsequenceLengthes(String a, String b) {
        List<Integer> subseqLength = Lists.newArrayList();
        while (!a.isEmpty()) {
            int prefixlen = a.length();
            while (prefixlen > 0 && !containsPrefix(b, a, prefixlen)) {
                prefixlen--;
            }
            if (prefixlen == 0) {
                subseqLength.add(0);
                break;
            }
            subseqLength.add(prefixlen);
            String found = a.substring(0, prefixlen);
            b = b.substring(prefixlen + b.indexOf(found));
            a = a.substring(prefixlen);

        }
        return subseqLength;
    }

    /**
     * checks if b contains the first n characters of a as a substring
     */
    private static boolean containsPrefix(String b, String a, int n) {
        // TODO performance
        return b.contains(a.substring(0, n));
    }

    private static double average(List<Integer> l) {
        Preconditions.checkArgument(l.size() > 0);
        return sum(l) * 1. / l.size();
    }

    private static int sum(List<Integer> l) {
        int sum = 0;
        for (int i : l) {
            sum += i;
        }
        return sum;
    }

    public static <T> T getFirstAndOnly(Collection<T> c) {
        if (c.size() != 1) {
            throw new Error("Size must be 1 but was " + c.size());
        }
        return getFirst(c);
    }

    public static String escapeString(String v) {
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\"':
                    sb.append("\\\"");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                default:
                    sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    public static String escapeHtml(String s) {
        // TODO could use apache commons library?
        s = s.replace("<", "&lt;");
        s = s.replace(">", "&gt;");
        return s;
    }

    /**
     * returns the filename from the given path
     */
    public static String fileName(String path) {
        int pos = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (pos > 0) {
            return path.substring(pos + 1);
        }
        return path;
    }

    public static String printException(Throwable e) {
        return e + "\n" + Utils.printExceptionWithStackTrace(e);
    }


    public static String stripHtml(String s) {
        return s.replaceAll("<.*?>", "");
    }


    public static <T> Iterable<T> iterateReverse(final List<T> elements) {
        return () -> new Iterator<T>() {

            ListIterator<T> it = elements.listIterator(elements.size());

            @Override
            public boolean hasNext() {
                return it.hasPrevious();
            }

            @Override
            public T next() {
                return it.previous();
            }

            @Override
            public void remove() {
                it.remove();
            }
        };
    }


    public static String readWholeStream(BufferedReader r) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    public static String readWholeStream(InputStream inputStream) throws IOException {
        return readWholeStream(new BufferedReader(new InputStreamReader(inputStream)));
    }


    @SuppressWarnings("unchecked")
    public static <T extends Element> Optional<T> getNearestByType(@Nullable Element e, Class<T> clazz) {
        while (e != null) {
            if (clazz.isInstance(e)) {
                return Optional.of((T) e);
            }
            e = e.getParent();
        }
        return Optional.empty();
    }


    public static <T extends JassImElementWithName> Comparator<T> compareByNameIm() {
        return Comparator.comparing(JassImElementWithName::getName);
    }

    public static String getParameterListText(AstElementWithParameters f) {
        StringBuilder descr = new StringBuilder();
        for (WParameter p : f.getParameters()) {
            if (descr.length() > 0) {
                descr.append(", ");
            }
            descr.append(p.attrTyp()).append(" ").append(p.getName());
        }
        return descr.toString();
    }

    public static <T> List<T> subList(List<T> l, int start) {
        return subList(l, start, l.size() - 1);
    }

    public static <T> List<T> subList(List<T> l, int start, int stop) {
        List<T> result = Lists.newArrayListWithCapacity(stop - start);
        for (int i = start; i <= stop; i++) {
            result.add(l.get(i));
        }
        return result;
    }

    public static <K, V> ImmutableMap<K, V> mergeMaps(
            ImmutableMap<K, V> a,
            ImmutableMap<K, V> b,
            Function2<V, V, V> mergeFunc) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;

        ImmutableMap.Builder<K, V> builder = ImmutableMap.builder();

        for (Entry<K, V> e : a.entrySet()) {
            K key = e.getKey();
            if (b.containsKey(key)) {
                builder.put(key, mergeFunc.apply(e.getValue(), b.get(key)));
            } else {
                builder.put(e);
            }
        }

        for (Entry<K, V> e : b.entrySet()) {
            K key = e.getKey();
            if (!a.containsKey(key)) {
                builder.put(e);
            }
        }

        return builder.build();
    }

    public static <K, V> ImmutableSetMultimap<K, V> mergeMultiMaps(
            ImmutableSetMultimap<K, V> a, ImmutableSetMultimap<K, V> b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;

        ImmutableSetMultimap.Builder<K, V> builder = ImmutableSetMultimap.builder();
        builder.putAll(a);
        builder.putAll(b);
        return builder.build();
    }

    public static <T> ImmutableSet<T> mergeSets(ImmutableSet<T> a, ImmutableSet<T> b) {
        ImmutableSet.Builder<T> builder = ImmutableSet.<T>builder();
        builder.addAll(a).addAll(b);
        return builder.build();
    }

    public static <T> T[] joinArrays(T[] a, T[] b) {
        T[] res = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, res, a.length, b.length);
        return res;
    }

    public static <K, V> void removeValuesFromMap(Map<K, V> map, Collection<V> removed) {
        map.entrySet().removeIf(e -> removed.contains(e.getValue()));
    }

    public static <T> ImmutableList<T> emptyList() {
        return ImmutableList.of();
    }

    public static <T>
    Collector<T, ?, ImmutableList<T>> toImmutableList() {
        return new Collector<T, Builder<T>, ImmutableList<T>>() {

            @Override
            public Supplier<Builder<T>> supplier() {
                return ImmutableList::builder;
            }

            @Override
            public BiConsumer<Builder<T>, T> accumulator() {
                return Builder::add;
            }

            @Override
            public BinaryOperator<Builder<T>> combiner() {
                return (a, b) -> a.addAll(b.build());
            }

            @Override
            public java.util.function.Function<Builder<T>, ImmutableList<T>> finisher() {
                return Builder::build;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Collections.emptySet();
            }
        };
    }

    public static MouseListener onClickDo(Consumer<MouseEvent> onclick) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(@Nullable MouseEvent e) {
                Preconditions.checkNotNull(e);
                onclick.accept(e);
            }
        };
    }

    public static boolean isWurstFile(File f) {
        return isWurstFile(f.getName());
    }

    public static boolean isWurstFile(String fileName) {
        return fileName.endsWith(".wurst") || fileName.endsWith(".jurst");
    }

    public static String getLibName(File f) {
        return f.getName().replaceAll("\\.[jw]urst$", "");
    }


    public static String repeat(char c, int size) {
        StringBuilder result = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            result.append(c);
        }
        return result.toString();
    }

    public static boolean elementContained(Element e, Element in) {
        while (e != null) {
            if (e == in) {
                return true;
            }
            e = e.getParent();
        }
        return false;
    }

    /**
     * Executes a shell command in a given folder and returns the output of the executed command
     */
    public static String exec(File folder, String... cmds) {
        try {
            Process p = new ProcessBuilder(cmds)
                    .directory(folder)
                    .start();
            int res = p.waitFor();
            if (res != 0) {
                throw new RuntimeException("Could not execute " + Utils.join(Arrays.asList(cmds), " ")
                        + "\nErrors:\n"
                        + convertStreamToString(p.getErrorStream())
                        + "\nOutput:\n"
                        + convertStreamToString(p.getInputStream()));
            }
            return convertStreamToString(p.getInputStream());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts an input stream to a string
     * <p>
     * see http://stackoverflow.com/questions/309424/read-convert-an-inputstream-to-a-string
     */
    public static String convertStreamToString(InputStream is) {
        Scanner s = new Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    public static byte[] convertStreamToBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[16384];

        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();

        return buffer.toByteArray();
    }

    /**
     * join lines
     */
    public static String string(String... lines) {
        return Arrays.stream(lines).collect(Collectors.joining("\n")) + "\n";
    }

    /**
     * Extracts a resource from the jar, stores it in a temp file and returns the abolute path to the tempfile
     */
    public static synchronized String getResourceFile(String name) {
        try {
            File f = resourceMap.get(name);
            if (f != null && f.exists()) {
                return f.getAbsolutePath();
            }
            String[] parts = name.split("\\.");
            f = File.createTempFile(parts[0], parts[1]);
            f.deleteOnExit();
            try (InputStream is = Pjass.class.getClassLoader().getResourceAsStream(name)) {
                if (is == null) {
                    throw new RuntimeException("Could not find resource file " + name);
                }
                byte[] bytes = Utils.convertStreamToBytes(is);
                Files.write(bytes, f);
                resourceMap.put(name, f);
                return f.getAbsolutePath();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, File> resourceMap = new HashMap<>();

    public static String elementNameWithPath(AstElementWithNameId n) {
        StringBuilder result = new StringBuilder(n.getNameId().getName());
        Element e = n.getParent();
        while (e != null) {
            if (e instanceof AstElementWithNameId) {
                result.insert(0, ((AstElementWithNameId) e).getNameId().getName() + "_");
            }
            e = e.getParent();
        }
        return result.toString();
    }
}
