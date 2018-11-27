package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.PrintedContent;
import hudson.PluginManager;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

class FailedPlugins extends PrintedContent {
    public FailedPlugins() {
        super("plugins/failed.txt");
    }

    @Override
    protected void printTo(PrintWriter out) throws IOException {
        PluginManager pluginManager = Jenkins.getInstance().getPluginManager();
        List<PluginManager.FailedPlugin> plugins = pluginManager.getFailedPlugins();
        // no need to sort
        for (PluginManager.FailedPlugin w : plugins) {
            out.println(w.name + " -> " + w.cause);
        }
    }

    @Override
    public boolean shouldBeFiltered() {
        return false;
    }
}
