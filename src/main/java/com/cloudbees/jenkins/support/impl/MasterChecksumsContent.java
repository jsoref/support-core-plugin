package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.PrintedContent;
import hudson.Util;
import hudson.lifecycle.Lifecycle;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.Stapler;

import javax.servlet.ServletContext;
import java.io.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

class MasterChecksumsContent extends PrintedContent {
    private static final Logger logger = Logger.getLogger(AboutJenkins.class.getName());

    MasterChecksumsContent() {
        super("nodes/master/checksums.md5");
    }
    @Override protected void printTo(PrintWriter out) throws IOException {
        final Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            // Lifecycle.get() depends on Jenkins instance, hence this method won't work in any case
            throw new IOException("Jenkins has not been started, or was already shut down");
        }

        File jenkinsWar = Lifecycle.get().getHudsonWar();
        if (jenkinsWar != null) {
            try {
                out.println(Util.getDigestOf(new FileInputStream(jenkinsWar)) + "  jenkins.war");
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not compute MD5 of jenkins.war", e);
            }
        }
        Stapler stapler = null;
        try {
            stapler = Stapler.getCurrent();
        } catch (NullPointerException e) {
            // the method is not always safe :-(
        }
        if (stapler != null) {
            final ServletContext servletContext = stapler.getServletContext();
            Set<String> resourcePaths = (Set<String>) servletContext.getResourcePaths("/WEB-INF/lib");
            for (String resourcePath : new TreeSet<String>(resourcePaths)) {
                try {
                    out.println(
                            Util.getDigestOf(servletContext.getResourceAsStream(resourcePath)) + "  war"
                                    + resourcePath);
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Could not compute MD5 of war" + resourcePath, e);
                }
            }
            for (String resourcePath : Arrays.asList(
                    "/WEB-INF/slave.jar", // note that as of 2.33 this will not be present (anyway it is the same as war/WEB-INF/lib/remoting-*.jar, printed above)
                    "/WEB-INF/remoting.jar", // ditto
                    "/WEB-INF/jenkins-cli.jar",
                    "/WEB-INF/web.xml")) {
                try {
                    InputStream resourceAsStream = servletContext.getResourceAsStream(resourcePath);
                    if (resourceAsStream == null) {
                        continue;
                    }
                    out.println(
                            Util.getDigestOf(resourceAsStream) + "  war"
                                    + resourcePath);
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Could not compute MD5 of war" + resourcePath, e);
                }
            }
            resourcePaths = (Set<String>) servletContext.getResourcePaths("/WEB-INF/update-center-rootCAs");
            for (String resourcePath : new TreeSet<String>(resourcePaths)) {
                try {
                    out.println(
                            Util.getDigestOf(servletContext.getResourceAsStream(resourcePath)) + "  war"
                                    + resourcePath);
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Could not compute MD5 of war" + resourcePath, e);
                }
            }
        }

        final Collection<File> pluginFiles = FileUtils.listFiles(new File(jenkins.getRootDir(), "plugins"), null, false);
        for (File file : pluginFiles) {
            if (file.isFile()) {
                try {
                    out.println(Util.getDigestOf(new FileInputStream(file)) + "  plugins/" + file
                            .getName());
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Could not compute MD5 of war/" + file, e);
                }
            }
        }
    }
}
