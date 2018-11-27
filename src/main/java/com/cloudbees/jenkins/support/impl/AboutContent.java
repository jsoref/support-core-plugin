package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.PrintedContent;
import com.cloudbees.jenkins.support.api.SupportProvider;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import com.cloudbees.jenkins.support.util.Markdown;
import hudson.PluginWrapper;
import hudson.lifecycle.Lifecycle;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.kohsuke.stapler.Stapler;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

class AboutContent extends PrintedContent {
    private static final Logger logger = Logger.getLogger(AboutJenkins.class.getName());

    private final Iterable<PluginWrapper> plugins;

    @Override protected void printTo(PrintWriter out) throws IOException {
        final Jenkins jenkins = Jenkins.get();
        out.println("Jenkins");
        out.println("=======");
        out.println();
        out.println("Version details");
        out.println("---------------");
        out.println();
        out.println("  * Version: `" + Markdown.escapeBacktick(Jenkins.VERSION) + "`");
        File jenkinsWar = Lifecycle.get().getHudsonWar();
        if (jenkinsWar == null) {
            out.println("  * Mode:    Webapp Directory");
        } else {
            out.println("  * Mode:    WAR");
        }
        final JenkinsLocationConfiguration jlc = JenkinsLocationConfiguration.get();
        out.println("  * Url:     " + (jlc != null ? jlc.getUrl() : "No JenkinsLocationConfiguration available"));
        try {
            final ServletContext servletContext = Stapler.getCurrent().getServletContext();
            out.println("  * Servlet container");
            out.println("      - Specification: " + servletContext.getMajorVersion() + "." + servletContext
                    .getMinorVersion());
            out.println(
                    "      - Name:          `" + Markdown.escapeBacktick(servletContext.getServerInfo()) + "`");
        } catch (NullPointerException e) {
            // pity Stapler.getCurrent() throws an NPE when outside of a request
        }
        out.print(new AboutJenkins.GetJavaInfo("  *", "      -").call());
        out.println();
        out.println("Important configuration");
        out.println("---------------");
        out.println();
        out.println("  * Security realm: " + Markdown.getDescriptorName(jenkins.getSecurityRealm()));
        out.println("  * Authorization strategy: " + Markdown.getDescriptorName(jenkins.getAuthorizationStrategy()));
        out.println("  * CSRF Protection: "  + jenkins.isUseCrumbs());
        out.println("  * Initialization Milestone: " + jenkins.getInitLevel());
        out.println("  * Support bundle anonymization: " + ContentFilters.get().isEnabled());
        out.println();
        out.println("Active Plugins");
        out.println("--------------");
        out.println();

        for (PluginWrapper w : plugins) {
            if (w.isActive()) {
                out.println("  * " + w.getShortName() + ":" + w.getVersion() + (w.hasUpdate()
                        ? " *(update available)*"
                        : "") + " '" + w.getLongName() + "'");
            }
        }
        SupportPlugin supportPlugin = SupportPlugin.getInstance();
        if (supportPlugin != null) {
            SupportProvider supportProvider = supportPlugin.getSupportProvider();
            if (supportProvider != null) {
                out.println();
                try {
                    supportProvider.printAboutJenkins(out);
                } catch (Throwable e) {
                    logger.log(Level.WARNING, null, e);
                }
            }
        }
    }
    AboutContent(Iterable<PluginWrapper> plugins) {
        super("about.md");
        this.plugins = plugins;
    }
}
