package com.cloudbees.jenkins.support.util;

import hudson.console.HyperlinkNote;
import hudson.model.Describable;
import javafx.scene.control.Hyperlink;
import org.junit.Test;

import static org.junit.Assert.*;

public class MarkdownTest {

    @Test
    public void escapeUnderscore() {
        assertEquals("a&#95;b", Markdown.escapeUnderscore("a_b"));
        assertEquals("a`b", Markdown.escapeUnderscore("a`b"));
    }

    @Test
    public void escapeBacktick() {
        assertEquals("a&#96;b", Markdown.escapeBacktick("a`b"));
        assertEquals("a_b", Markdown.escapeBacktick("a_b"));
    }

    @Test
    public void prettyNone() {
        assertEquals(Markdown.NONE_STRING, Markdown.prettyNone(null));
        assertEquals(Markdown.NONE_STRING, Markdown.prettyNone(""));
        assertEquals("a", Markdown.prettyNone("a"));
    }

    @Test
    public void getDescriptorName() {
        assertEquals(Markdown.NONE_STRING, Markdown.getDescriptorName(null));
        assertEquals("`hudson.console.HyperlinkNote`", Markdown.getDescriptorName(new HyperlinkNote("http://example.com", 0)));
    }
}
