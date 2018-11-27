package com.cloudbees.jenkins.support.util;

import hudson.model.Describable;

import javax.annotation.CheckForNull;

public final class Markdown {
    public static final String NONE_STRING = "(none)";

    /** Not instantiable. */
    private Markdown() {
        throw new AssertionError("Not instantiable");
    }

    public static String escapeUnderscore(String raw) {
        return raw.replaceAll("_", "&#95;");
    }
    public static String escapeBacktick(String raw) {
        return raw.replaceAll("`", "&#96;");
    }

    public static String prettyNone(String raw) { return raw != null && !raw.isEmpty() ? raw : NONE_STRING; }

    public static String getDescriptorName(@CheckForNull Describable<?> d) {
        if (d == null) {
            return Markdown.NONE_STRING;
        }
        return "`" + Markdown.escapeBacktick(d.getClass().getName()) + "`";
    }
}
