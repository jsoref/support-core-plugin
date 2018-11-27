package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.PrintedContent;
import com.cloudbees.jenkins.support.util.Markdown;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.remoting.Launcher;
import hudson.remoting.VirtualChannel;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

class NodesContent extends PrintedContent {
    private static final Logger logger = Logger.getLogger(AboutJenkins.class.getName());

    private final WeakHashMap<Node,String> slaveVersionCache = new WeakHashMap<Node, String>();

    private final WeakHashMap<Node,String> javaInfoCache = new WeakHashMap<Node, String>();

    NodesContent() {
        super("nodes.md");
    }
    private String getLabelString(Node n) {
        return Markdown.prettyNone(n.getLabelString());
    }
    @Override protected void printTo(PrintWriter out) throws IOException {
        final Jenkins jenkins = Jenkins.getInstance();
        SupportPlugin supportPlugin = SupportPlugin.getInstance();
        if (supportPlugin != null) {
            out.println("Node statistics");
            out.println("===============");
            out.println();
            out.println("  * Total number of nodes");
            AboutJenkins.printHistogram(out, supportPlugin.getJenkinsNodeTotalCount());
            out.println("  * Total number of nodes online");
            AboutJenkins.printHistogram(out, supportPlugin.getJenkinsNodeOnlineCount());
            out.println("  * Total number of executors");
            AboutJenkins.printHistogram(out, supportPlugin.getJenkinsExecutorTotalCount());
            out.println("  * Total number of executors in use");
            AboutJenkins.printHistogram(out, supportPlugin.getJenkinsExecutorUsedCount());
            out.println();
        }
        out.println("Build Nodes");
        out.println("===========");
        out.println();
        out.println("  * master (Jenkins)");
        out.println("      - Description:    _" +
                Markdown.escapeUnderscore(Util.fixNull(jenkins.getNodeDescription())) + "_");
        out.println("      - Executors:      " + jenkins.getNumExecutors());
        out.println("      - FS root:        `" +
                Markdown.escapeBacktick(jenkins.getRootDir().getAbsolutePath()) + "`");
        out.println("      - Labels:         " + getLabelString(jenkins));
        out.println("      - Usage:          `" + jenkins.getMode() + "`");
        out.println("      - Slave Version:  " + Launcher.VERSION);
        out.print(new AboutJenkins.GetJavaInfo("      -", "          +").call());
        out.println();
        for (Node node : jenkins.getNodes()) {
            out.println("  * `" + Markdown.escapeBacktick(node.getNodeName()) + "` (" +Markdown.getDescriptorName(node) +
                    ")");
            out.println("      - Description:    _" +
                    Markdown.escapeUnderscore(Util.fixNull(node.getNodeDescription())) + "_");
            out.println("      - Executors:      " + node.getNumExecutors());
            FilePath rootPath = node.getRootPath();
            if (rootPath != null) {
                out.println("      - Remote FS root: `" + Markdown.escapeBacktick(rootPath.getRemote()) + "`");
            } else if (node instanceof Slave) {
                out.println("      - Remote FS root: `" +
                        Markdown.escapeBacktick(Slave.class.cast(node).getRemoteFS()) + "`");
            }
            out.println("      - Labels:         " + Markdown.escapeUnderscore(getLabelString(node)));
            out.println("      - Usage:          `" + node.getMode() + "`");
            if (node instanceof Slave) {
                Slave slave = (Slave) node;
                out.println("      - Launch method:  " + Markdown.getDescriptorName(slave.getLauncher()));
                out.println("      - Availability:   " + Markdown.getDescriptorName(slave.getRetentionStrategy()));
            }
            VirtualChannel channel = node.getChannel();
            if (channel == null) {
                out.println("      - Status:         off-line");
            } else {
                out.println("      - Status:         on-line");
                try {
                    out.println("      - Version:        " +
                            AsyncResultCache.get(node, slaveVersionCache, new AboutJenkins.GetSlaveVersion(),
                                    "slave.jar version", "(timeout with no cache available)"));
                } catch (IOException e) {
                    logger.log(Level.WARNING,
                            "Could not get slave.jar version for " + node.getNodeName(), e);
                }
                try {
                    final String javaInfo = AsyncResultCache.get(node, javaInfoCache,
                            new AboutJenkins.GetJavaInfo("      -", "          +"), "Java info");
                    if (javaInfo == null) {
                        logger.log(Level.FINE,
                                "Could not get Java info for {0} and no cached value available",
                                node.getNodeName());
                    } else {
                        out.print(javaInfo);
                    }
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Could not get Java info for " + node.getNodeName(), e);
                }
            }
            out.println();
        }
    }
}
