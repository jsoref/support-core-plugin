/*
 * Copyright © 2013 CloudBees, Inc.
 * This is proprietary code. All rights reserved.
 */

package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import com.cloudbees.jenkins.support.api.SupportProvider;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import com.cloudbees.jenkins.support.util.Markdown;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Snapshot;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.Util;
import hudson.lifecycle.Lifecycle;
import hudson.model.Describable;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.remoting.Launcher;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import hudson.util.IOUtils;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.Stapler;

import javax.annotation.CheckForNull;
import javax.servlet.ServletContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.security.MasterToSlaveCallable;

/**
 * Contributes basic information about Jenkins.
 *
 * @author Stephen Connolly
 */
@Extension
public class AboutJenkins extends Component {

    private static final Logger logger = Logger.getLogger(AboutJenkins.class.getName());

    private final WeakHashMap<Node,String> slaveDigestCache = new WeakHashMap<Node, String>();

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        // Was originally READ, but a lot of the details here could be considered sensitive:
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return "About Jenkins";
    }

    @Override
    public void addContents(@NonNull Container container) {
        List<PluginWrapper> activePlugins = new ArrayList<PluginWrapper>();
        List<PluginWrapper> disabledPlugins = new ArrayList<PluginWrapper>();

        populatePluginsLists(activePlugins, disabledPlugins);

        container.add(new AboutContent(activePlugins));
        container.add(new ItemsContent());
        container.add(new NodesContent());
        container.add(new ActivePlugins(activePlugins));
        container.add(new DisabledPlugins(disabledPlugins));
        container.add(new FailedPlugins());

        container.add(new Dockerfile(activePlugins, disabledPlugins));

        container.add(new MasterChecksumsContent());
        for (final Node node : Jenkins.getInstance().getNodes()) {
            container.add(new NodeChecksumsContent(node));
        }
    }

    /**
     * A pre-check to see if a string is a build timestamp formatted date.
     *
     * @param s the string.
     * @return {@code true} if it is likely that the string will parse as a build timestamp formatted date.
     */
    static boolean mayBeDate(String s) {
        if (s == null || s.length() != "yyyy-MM-dd_HH-mm-ss".length()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            switch (s.charAt(i)) {
                case '-':
                    switch (i) {
                        case 4:
                        case 7:
                        case 13:
                        case 16:
                            break;
                        default:
                            return false;
                    }
                    break;
                case '_':
                    if (i != 10) {
                        return false;
                    }
                    break;
                case '0':
                case '1':
                    switch (i) {
                        case 4: // -
                        case 7: // -
                        case 10: // _
                        case 13: // -
                        case 16: // -
                            return false;
                        default:
                            break;
                    }
                    break;
                case '2':
                    switch (i) {
                        case 4: // -
                        case 5: // months 0-1
                        case 7: // -
                        case 10: // _
                        case 13: // -
                        case 16: // -
                            return false;
                        default:
                            break;
                    }
                    break;
                case '3':
                    switch (i) {
                        case 0: // year will safely begin with digit 2 for next 800-odd years
                        case 4: // -
                        case 5: // months 0-1
                        case 7: // -
                        case 10: // _
                        case 11: // hours 0-2
                        case 13: // -
                        case 16: // -
                            return false;
                        default:
                            break;
                    }
                    break;
                case '4':
                case '5':
                    switch (i) {
                        case 0: // year will safely begin with digit 2 for next 800-odd years
                        case 4: // -
                        case 5: // months 0-1
                        case 7: // -
                        case 8: // days 0-3
                        case 10: // _
                        case 11: // hours 0-2
                        case 13: // -
                        case 16: // -
                            return false;
                        default:
                            break;
                    }
                    break;
                case '6':
                case '7':
                case '8':
                case '9':
                    switch (i) {
                        case 0: // year will safely begin with digit 2 for next 800-odd years
                        case 4: // -
                        case 5: // months 0-1
                        case 7: // -
                        case 8: // days 0-3
                        case 10: // _
                        case 11: // hours 0-2
                        case 13: // -
                        case 14: // minutes 0-5
                        case 16: // -
                        case 17: // seconds 0-5
                            return false;
                        default:
                            break;
                    }
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    protected static void printHistogram(PrintWriter out, Histogram histogram) {
        out.println("      - Sample size:        " + histogram.getCount());
        Snapshot snapshot = histogram.getSnapshot();
        out.println("      - Average (mean):     " + snapshot.getMean());
        out.println("      - Average (median):   " + snapshot.getMedian());
        out.println("      - Standard deviation: " + snapshot.getStdDev());
        out.println("      - Minimum:            " + snapshot.getMin());
        out.println("      - Maximum:            " + snapshot.getMax());
        out.println("      - 95th percentile:    " + snapshot.get95thPercentile());
        out.println("      - 99th percentile:    " + snapshot.get99thPercentile());
    }

    private static final class GetSlaveDigest extends MasterToSlaveCallable<String, RuntimeException> {
        private static final long serialVersionUID = 1L;
        private final String rootPathName;

        public GetSlaveDigest(FilePath rootPath) {
            this.rootPathName = rootPath.getRemote();
        }

        public String call() {
            StringBuilder result = new StringBuilder();
            final File rootPath = new File(this.rootPathName);
            for (File file : FileUtils.listFiles(rootPath, null, false)) {
                if (file.isFile()) {
                    try {
                        result.append(Util.getDigestOf(new FileInputStream(file)))
                                .append("  ")
                                .append(file.getName()).append('\n');
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
            return result.toString();
        }
    }

    protected static class GetSlaveVersion extends MasterToSlaveCallable<String, RuntimeException> {
        private static final long serialVersionUID = 1L;

        @edu.umd.cs.findbugs.annotations.SuppressWarnings(
                value = {"NP_LOAD_OF_KNOWN_NULL_VALUE"},
                justification = "Findbugs mis-diagnosing closeQuietly's built-in null check"
        )
        public String call() throws RuntimeException {
            InputStream is = null;
            try {
                is = hudson.remoting.Channel.class.getResourceAsStream("/jenkins/remoting/jenkins-version.properties");
                if (is == null) {
                    return "N/A";
                }
                Properties properties = new Properties();
                try {
                    properties.load(is);
                    return properties.getProperty("version", "N/A");
                } catch (IOException e) {
                    return "N/A";
                }
            } finally {
                IOUtils.closeQuietly(is);
            }
        }
    }

    protected static class GetJavaInfo extends MasterToSlaveCallable<String, RuntimeException> {
        private static final long serialVersionUID = 1L;
        private final String maj;
        private final String min;

        protected GetJavaInfo(String majorBullet, String minorBullet) {
            this.maj = majorBullet;
            this.min = minorBullet;
        }

        @SuppressWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
        public String call() throws RuntimeException {
            StringBuilder result = new StringBuilder();
            Runtime runtime = Runtime.getRuntime();
            result.append(maj).append(" Java\n");
            result.append(min).append(" Home:           `").append(
                    Markdown.escapeBacktick(System.getProperty("java.home"))).append("`\n");
            result.append(min).append(" Vendor:           ").append(
                    Markdown.escapeUnderscore(System.getProperty("java.vendor"))).append("\n");
            result.append(min).append(" Version:          ").append(
                    Markdown.escapeUnderscore(System.getProperty("java.version"))).append("\n");
            long maxMem = runtime.maxMemory();
            long allocMem = runtime.totalMemory();
            long freeMem = runtime.freeMemory();
            result.append(min).append(" Maximum memory:   ").append(humanReadableSize(maxMem)).append("\n");
            result.append(min).append(" Allocated memory: ").append(humanReadableSize(allocMem))
                    .append("\n");
            result.append(min).append(" Free memory:      ").append(humanReadableSize(freeMem)).append("\n");
            result.append(min).append(" In-use memory:    ").append(humanReadableSize(allocMem - freeMem)).append("\n");

            for(MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
                if (bean.getName().toLowerCase().contains("perm gen")) {
                    MemoryUsage currentUsage = bean.getUsage();
                    result.append(min).append(" PermGen used:     ").append(humanReadableSize(currentUsage.getUsed())).append("\n");
                    result.append(min).append(" PermGen max:      ").append(humanReadableSize(currentUsage.getMax())).append("\n");
                    break;
                }
            }

            for(MemoryManagerMXBean bean : ManagementFactory.getMemoryManagerMXBeans()) {
                if (bean.getName().contains("MarkSweepCompact")) {
                    result.append(min).append(" GC strategy:      SerialGC\n");
                    break;
                }
                if (bean.getName().contains("ConcurrentMarkSweep")) {
                    result.append(min).append(" GC strategy:      ConcMarkSweepGC\n");
                    break;
                }
                if (bean.getName().contains("PS")) {
                    result.append(min).append(" GC strategy:      ParallelGC\n");
                    break;
                }
                if (bean.getName().contains("G1")) {
                    result.append(min).append(" GC strategy:      G1\n");
                    break;
                }
            }

            result.append(maj).append(" Java Runtime Specification\n");
            result.append(min).append(" Name:    ").append(System.getProperty("java.specification.name")).append("\n");
            result.append(min).append(" Vendor:  ").append(System.getProperty("java.specification.vendor"))
                    .append("\n");
            result.append(min).append(" Version: ").append(System.getProperty("java.specification.version"))
                    .append("\n");
            result.append(maj).append(" JVM Specification\n");
            result.append(min).append(" Name:    ").append(System.getProperty("java.vm.specification.name"))
                    .append("\n");
            result.append(min).append(" Vendor:  ").append(System.getProperty("java.vm.specification.vendor"))
                    .append("\n");
            result.append(min).append(" Version: ").append(System.getProperty("java.vm.specification.version"))
                    .append("\n");
            result.append(maj).append(" JVM Implementation\n");
            result.append(min).append(" Name:    ").append(
                    Markdown.escapeUnderscore(System.getProperty("java.vm.name"))).append("\n");
            result.append(min).append(" Vendor:  ").append(
                    Markdown.escapeUnderscore(System.getProperty("java.vm.vendor"))).append("\n");
            result.append(min).append(" Version: ").append(
                    Markdown.escapeUnderscore(System.getProperty("java.vm.version"))).append("\n");
            result.append(maj).append(" Operating system\n");
            result.append(min).append(" Name:         ").append(
                    Markdown.escapeUnderscore(System.getProperty("os.name"))).append("\n");
            result.append(min).append(" Architecture: ").append(
                    Markdown.escapeUnderscore(System.getProperty("os.arch"))).append("\n");
            result.append(min).append(" Version:      ").append(
                    Markdown.escapeUnderscore(System.getProperty("os.version"))).append("\n");
            File lsb_release = new File("/usr/bin/lsb_release");
            if (lsb_release.canExecute()) {
                try {
                    Process proc = new ProcessBuilder().command(lsb_release.getAbsolutePath(), "--description", "--short").start();
                    String distro = IOUtils.readFirstLine(proc.getInputStream(), "UTF-8");
                    if (proc.waitFor() == 0) {
                        result.append(min).append(" Distribution: ").append(
                                Markdown.escapeUnderscore(distro)).append("\n");
                    } else {
                        logger.fine("lsb_release had a nonzero exit status");
                    }
                    proc = new ProcessBuilder().command(lsb_release.getAbsolutePath(), "--version", "--short").start();
                    String modules = IOUtils.readFirstLine(proc.getInputStream(), "UTF-8");
                    if (proc.waitFor() == 0 && modules != null) {
                        result.append(min).append(" LSB Modules:  `").append(
                                Markdown.escapeUnderscore(modules)).append("`\n");
                    } else {
                        logger.fine("lsb_release had a nonzero exit status");
                    }
                } catch (IOException x) {
                    logger.log(Level.WARNING, "lsb_release exists but could not run it", x);
                } catch (InterruptedException x) {
                    logger.log(Level.WARNING, "lsb_release hung", x);
                }
            }
            RuntimeMXBean mBean = ManagementFactory.getRuntimeMXBean();
            String process = mBean.getName();
            Matcher processMatcher = Pattern.compile("^(-?[0-9]+)@.*$").matcher(process);
            if (processMatcher.matches()) {
                int processId = Integer.parseInt(processMatcher.group(1));
                result.append(maj).append(" Process ID: ").append(processId).append(" (0x")
                        .append(Integer.toHexString(processId)).append(")\n");
            }
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            result.append(maj).append(" Process started: ")
                    .append(f.format(new Date(mBean.getStartTime())))
                    .append('\n');
            result.append(maj).append(" Process uptime: ")
                    .append(Util.getTimeSpanString(mBean.getUptime())).append('\n');
            result.append(maj).append(" JVM startup parameters:\n");
            if (mBean.isBootClassPathSupported()) {
                result.append(min).append(" Boot classpath: `")
                        .append(Markdown.escapeBacktick(mBean.getBootClassPath())).append("`\n");
            }
            result.append(min).append(" Classpath: `").append(Markdown.escapeBacktick(mBean.getClassPath()))
                    .append("`\n");
            result.append(min).append(" Library path: `").append(Markdown.escapeBacktick(mBean.getLibraryPath()))
                    .append("`\n");
            int count = 0;
            for (String arg : mBean.getInputArguments()) {
                result.append(min).append(" arg[").append(count++).append("]: `").append(Markdown.escapeBacktick(arg))
                        .append("`\n");
            }
            return result.toString();
        }

    }

    private static String humanReadableSize(long size) {
        String measure = "B";
        if (size < 1024) {
            return size + " " + measure;
        }
        double number = size;
        if (number >= 1024) {
            number = number / 1024;
            measure = "KB";
            if (number >= 1024) {
                number = number / 1024;
                measure = "MB";
                if (number >= 1024) {
                    number = number / 1024;
                    measure = "GB";
                }
            }
        }
        DecimalFormat format = new DecimalFormat("#0.00");
        return format.format(number) + " " + measure + " (" + size + ")";
    }

    private static class MasterChecksumsContent extends PrintedContent {
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

    private class NodeChecksumsContent extends PrintedContent {
        private final Node node;
        NodeChecksumsContent(Node node) {
            super("nodes/slave/" + node.getNodeName() + "/checksums.md5");
            this.node = node;
        }
        @Override protected void printTo(PrintWriter out) throws IOException {
            try {
                final FilePath rootPath = node.getRootPath();
                String slaveDigest = rootPath == null ? "N/A" :
                        AsyncResultCache.get(node, slaveDigestCache, new GetSlaveDigest(rootPath),
                                "checksums", "N/A");
                out.println(slaveDigest);
            } catch (IOException e) {
                logger.log(Level.WARNING,
                        "Could not compute checksums on slave " + node.getNodeName(), e);
            }
        }
    }

    /**
     * Fixes JENKINS-47779 caused by JENKINS-47713
     * Not using SortedSet because of PluginWrapper doesn't implements equals and hashCode.
     * @return new copy of the PluginManager.getPlugins sorted
     */
    private static Iterable<PluginWrapper> getPluginsSorted() {
        PluginManager pluginManager = Jenkins.getInstance().getPluginManager();
        return getPluginsSorted(pluginManager);
    }

    private static Iterable<PluginWrapper> getPluginsSorted(PluginManager pluginManager) {
        return listToSortedIterable(pluginManager.getPlugins());
    }

    private static <T extends Comparable<T>> Iterable<T> listToSortedIterable(List<T> list) {
        final List<T> sorted = new LinkedList<T>(list);
        Collections.sort(sorted);
        return sorted;
    }

    private static void populatePluginsLists(List<PluginWrapper> activePlugins, List<PluginWrapper> disabledPlugins) {
        for(PluginWrapper plugin : getPluginsSorted()){
            if(plugin.isActive()) {
                activePlugins.add(plugin);
            } else {
                disabledPlugins.add(plugin);
            }
        }
    }
}
