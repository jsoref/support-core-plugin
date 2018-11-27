package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.PrintedContent;
import hudson.PluginManager;
import hudson.PluginWrapper;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;

class Dockerfile extends PrintedContent {
    private final List<PluginWrapper> activated;
    private final List<PluginWrapper> disabled;

    public Dockerfile(List<PluginWrapper> activated, List<PluginWrapper> disabled) {
        super("docker/Dockerfile");
        this.activated = activated;
        this.disabled = disabled;
    }

    @Override
    protected void printTo(PrintWriter out) throws IOException {

        PluginManager pluginManager = Jenkins.getInstance().getPluginManager();
        String fullVersion = Jenkins.VERSION;
        int s = fullVersion.indexOf(' ');
        if (s > 0 && fullVersion.contains("CloudBees")) {
            out.println("FROM cloudbees/jenkins:" + fullVersion.substring(0, s));
        } else {
            out.println("FROM jenkins:" + fullVersion);
        }
        if (pluginManager.getPlugin("nectar-license") != null) { // even if atop an OSS WAR
            out.println("ENV JENKINS_UC http://jenkins-updates.cloudbees.com");
        }

        out.println("RUN mkdir -p /usr/share/jenkins/ref/plugins/");

        out.println("RUN curl \\");
        Iterator<PluginWrapper> activatedIT = activated.iterator();
        while (activatedIT.hasNext()) {
            PluginWrapper w = activatedIT.next();
            out.print("\t-L $JENKINS_UC/download/plugins/" + w.getShortName() + "/" + w.getVersion() + "/" + w.getShortName() + ".hpi"
                    + " -o /usr/share/jenkins/ref/plugins/" + w.getShortName() + ".jpi");
            if (activatedIT.hasNext()) {
                out.println(" \\");
            }
        }
        out.println();

            /* waiting for official docker image update
            out.println("COPY plugins.txt /plugins.txt");
            out.println("RUN /usr/local/bin/plugins.sh < plugins.txt");
            */

        if (!disabled.isEmpty()) {
            out.println("RUN touch \\");
            Iterator<PluginWrapper> disabledIT = disabled.iterator();
            while (disabledIT.hasNext()) {
                PluginWrapper w = disabledIT.next();
                out.print("\n\t/usr/share/jenkins/ref/plugins/" + w.getShortName() + ".jpi.disabled");
                if (disabledIT.hasNext()) {
                    out.println(" \\");
                }
            }
            out.println();
        }

        out.println();

    }

    @Override
    public boolean shouldBeFiltered() {
        return false;
    }
}
