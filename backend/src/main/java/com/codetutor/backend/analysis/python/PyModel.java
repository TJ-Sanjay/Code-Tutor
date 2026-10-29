package com.codetutor.backend.analysis.python;

import com.codetutor.backend.analysis.model.AnalysisModel.*;

import java.util.*;

/** Mutable working objects used while the Python analysis is running (turned into immutable ClassInfo/MethodInfo at the end). */
final class PyModel {

    private PyModel() {}

    /** One parsed .py file. */
    static final class PF {
        String path;
        String dotted;
        String app;
        boolean test;
        PyParser.Module mod;
        Cx moduleCx;
        final List<Cx> classes = new ArrayList<>();
        /** alias -> binding of an import (resolved lazily) */
        final Map<String, PyParser.Imp> bindings = new LinkedHashMap<>();
        final List<PyParser.Imp> stars = new ArrayList<>();

        String stem() {
            String n = path.substring(path.lastIndexOf('/') + 1);
            return n.endsWith(".py") ? n.substring(0, n.length() - 3) : n;
        }
    }

    /** A class, or the synthetic "module class" that groups the functions of a module. */
    static final class Cx {
        String id, name, pkg, module, layer = "OTHER", kind = "CLASS", doc;
        boolean test;
        boolean moduleClass;
        int line, endLine;
        PF pf;
        PyParser.Cls cls;
        final List<PM> methods = new ArrayList<>();
        final List<FieldInfo> fields = new ArrayList<>();
        final List<Ann> anns = new ArrayList<>();
        final Map<String, String> attrs = new LinkedHashMap<>();
        final Map<String, Set<String>> deps = new LinkedHashMap<>();
        final List<Cx> bases = new ArrayList<>();
        final List<String> baseTexts = new ArrayList<>();
        final List<Cx> managers = new ArrayList<>();
        Set<String> allBaseNames;
        List<String> imports = new ArrayList<>();
    }

    /** A method or function. */
    static final class PM {
        String id, name, doc;
        PyParser.Func fn;
        Cx owner;
        boolean synthetic;
        int line, endLine, loc;
        final List<Call> calls = new ArrayList<>();
        final List<Ann> anns = new ArrayList<>();
        String endpointId;
    }

    static final class Sett {
        final String value;
        final PF file;
        final int line;
        Sett(String value, PF file, int line) { this.value = value; this.file = file; this.line = line; }
    }
}
