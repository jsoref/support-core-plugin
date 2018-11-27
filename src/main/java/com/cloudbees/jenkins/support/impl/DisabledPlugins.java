package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.PrintedContent;
import hudson.PluginWrapper;

import java.io.IOException;
import java.io.PrintWriter;

class DisabledPlugins extends PrintedContent {
    private final Iterable<PluginWrapper> plugins;

    public DisabledPlugins(Iterable<PluginWrapper> plugins) {
        super("plugins/disabled.txt");
        this.plugins = plugins;
    }

    @Override
    protected void printTo(PrintWriter out) throws IOException {
        for (PluginWrapper w : plugins) {
            out.println(w.getShortName() + ":" + w.getVersion() + ":" + (w.isPinned() ? "pinned" : "not-pinned"));
        }
    }

    @Override
    public boolean shouldBeFiltered() {
        return false;
    }
}
