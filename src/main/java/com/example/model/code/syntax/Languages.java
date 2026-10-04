package com.example.model.code.syntax;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Languages {
    private static final Map<String, Language> BY_TAG = new HashMap<>();

    public static final Language GENERIC = new Language("generic")
            .literals("true false null nil none undefined True False None NULL")
            .lineComment("//", "#", "--")
            .blockComment("/*", "*/")
            .string("\"", "\"", false, true)
            .string("'", "'", false, true);

    private static final String C_KEYWORDS = "auto break case const continue default do else enum extern for goto if "
            + "inline register restrict return sizeof static struct switch typedef union volatile while";
    private static final String C_TYPES = "int long short char float double void unsigned signed size_t ssize_t bool "
            + "int8_t int16_t int32_t int64_t uint8_t uint16_t uint32_t uint64_t FILE";

    static {
        register(java(), "java");
        register(python(), "python", "py");
        register(javascript(), "javascript", "js", "jsx", "mjs", "typescript", "ts", "tsx");
        register(c(), "c", "h");
        register(cpp(), "cpp", "c++", "cc", "cxx", "hpp");
        register(csharp(), "csharp", "c#", "cs");
        register(html(), "html", "htm", "xml", "svg");
        register(css(), "css");
        register(json(), "json", "jsonc");
        register(sql(), "sql", "mysql", "postgres", "postgresql", "psql", "sqlite", "plsql", "tsql");
        register(bash(), "bash", "sh", "shell", "zsh");
        register(ocaml(), "ocaml", "ml", "mli");
    }

    private Languages() {
    }

    public static Language forTag(String tag) {
        if (tag == null)
            return GENERIC;
        Language l = BY_TAG.get(tag.trim().toLowerCase(Locale.ROOT));
        return l != null ? l : GENERIC;
    }

    private static void register(Language language, String... tags) {
        for (String t : tags)
            BY_TAG.put(t, language);
    }

    private static Language java() {
        return new Language("java")
                .keywords("abstract assert break case catch class const continue default do else enum extends final "
                        + "finally for goto if implements import instanceof interface native new package private "
                        + "protected public return static strictfp super switch synchronized this throw throws "
                        + "transient try volatile while var record sealed permits yield")
                .types("int long short byte float double char boolean void")
                .literals("true false null")
                .lineComment("//").blockComment("/*", "*/")
                .string("\"\"\"", "\"\"\"", true, true)
                .string("\"", "\"", false, true)
                .string("'", "'", false, true)
                .capitalizedTypes().atWords();
    }

    private static Language python() {
        return new Language("python")
                .keywords("and as assert async await break class continue def del elif else except finally for from "
                        + "global if import in is lambda nonlocal not or pass raise return try while with yield")
                .types("int float str bool list dict set tuple bytes object range type")
                .literals("True False None")
                .lineComment("#")
                .string("\"\"\"", "\"\"\"", true, true)
                .string("'''", "'''", true, true)
                .string("\"", "\"", false, true)
                .string("'", "'", false, true)
                .capitalizedTypes().atWords();
    }

    private static Language javascript() {
        return new Language("javascript")
                .keywords("async await break case catch class const continue debugger default delete do else export "
                        + "extends finally for function if import in instanceof let new of return static super "
                        + "switch this throw try typeof var void while with yield from as interface enum "
                        + "implements namespace declare readonly private public protected abstract keyof satisfies")
                .types("string number boolean any unknown never object symbol bigint")
                .literals("true false null undefined NaN Infinity")
                .lineComment("//").blockComment("/*", "*/")
                .string("`", "`", true, true)
                .string("\"", "\"", false, true)
                .string("'", "'", false, true)
                .capitalizedTypes().atWords();
    }

    private static Language c() {
        return new Language("c")
                .keywords(C_KEYWORDS).types(C_TYPES).literals("NULL true false")
                .lineComment("//").blockComment("/*", "*/")
                .string("\"", "\"", false, true)
                .string("'", "'", false, true)
                .directives();
    }

    private static Language cpp() {
        return new Language("cpp")
                .keywords(C_KEYWORDS + " catch class constexpr const_cast decltype delete dynamic_cast explicit "
                        + "export final friend mutable namespace new noexcept nullptr operator override private "
                        + "protected public reinterpret_cast static_assert static_cast template this throw try "
                        + "typeid typename using virtual")
                .types(C_TYPES + " string wstring vector map set unordered_map unordered_set array pair "
                        + "unique_ptr shared_ptr wchar_t char16_t char32_t")
                .literals("NULL true false")
                .lineComment("//").blockComment("/*", "*/")
                .string("\"", "\"", false, true)
                .string("'", "'", false, true)
                .directives();
    }

    private static Language csharp() {
        return new Language("csharp")
                .keywords("abstract as base break case catch checked class const continue default delegate do else "
                        + "enum event explicit extern finally fixed for foreach goto if implicit in interface "
                        + "internal is lock namespace new operator out override params private protected public "
                        + "readonly ref return sealed sizeof stackalloc static struct switch this throw try typeof "
                        + "unchecked unsafe using virtual volatile while async await record init yield partial")
                .types("bool byte char decimal double float int long object sbyte short string uint ulong ushort "
                        + "void dynamic var")
                .literals("true false null")
                .lineComment("//").blockComment("/*", "*/")
                .string("\"", "\"", false, true)
                .string("'", "'", false, true)
                .capitalizedTypes().directives();
    }

    private static Language html() {
        return new Language("html")
                .blockComment("<!--", "-->")
                .string("\"", "\"", true, false)
                .string("'", "'", true, false)
                .markup().noFunctions();
    }

    private static Language css() {
        return new Language("css")
                .blockComment("/*", "*/")
                .string("\"", "\"", false, true)
                .string("'", "'", false, true)
                .css().atWords();
    }

    private static Language json() {
        return new Language("json")
                .literals("true false null")
                .lineComment("//").blockComment("/*", "*/")
                .string("\"", "\"", false, true)
                .propertyStrings().noFunctions();
    }

    private static Language sql() {
        return new Language("sql").ignoreCase()
                .keywords("select from where insert into values update set delete create table alter drop index "
                        + "view join inner left right full outer cross on as and or not in is like between exists "
                        + "union all distinct group by order having limit offset asc desc case when then else end "
                        + "primary key foreign references default unique check constraint with begin commit "
                        + "rollback transaction truncate add column database if cascade using returning over "
                        + "partition")
                .types("int integer bigint smallint tinyint decimal numeric float real double varchar char text "
                        + "date time timestamp datetime boolean bool blob serial uuid json")
                .literals("null true false")
                .lineComment("--").blockComment("/*", "*/")
                .string("'", "'", false, false)
                .string("\"", "\"", false, false)
                .string("`", "`", false, false);
    }

    private static Language bash() {
        return new Language("bash")
                .keywords("if then else elif fi for while until do done case esac in function select time return "
                        + "exit break continue local export readonly declare unset source alias set shift trap "
                        + "eval exec")
                .literals("true false")
                .lineComment("#")
                .string("\"", "\"", true, true)
                .string("'", "'", true, false)
                .dollarVars().noFunctions();
    }

    private static Language ocaml() {
        return new Language("ocaml")
                .keywords("and as assert begin class constraint do done downto else end exception external for fun "
                        + "function functor if in include inherit initializer lazy let match method module mutable "
                        + "new nonrec object of open or private rec sig struct then to try type val virtual when "
                        + "while with")
                .types("int float string bool char unit list array option ref exn bytes")
                .literals("true false")
                .blockComment("(*", "*)")
                .string("\"", "\"", true, true)
                .nestedComments().primes().capitalizedTypes().noFunctions();
    }
}