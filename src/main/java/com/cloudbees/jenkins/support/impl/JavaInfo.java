package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.util.Markdown;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.Util;
import hudson.util.IOUtils;
import jenkins.security.MasterToSlaveCallable;

import java.io.File;
import java.io.IOException;
import java.lang.management.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class JavaInfo extends MasterToSlaveCallable<String, RuntimeException> {
    private static final Logger logger = Logger.getLogger(AboutJenkins.class.getName());

    private static final long serialVersionUID = 1L;
    private final String maj;
    private final String min;

    protected JavaInfo(String majorBullet, String minorBullet) {
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
        result.append(min).append(" Maximum memory:   ").append(AboutJenkins.humanReadableSize(maxMem)).append("\n");
        result.append(min).append(" Allocated memory: ").append(AboutJenkins.humanReadableSize(allocMem))
                .append("\n");
        result.append(min).append(" Free memory:      ").append(AboutJenkins.humanReadableSize(freeMem)).append("\n");
        result.append(min).append(" In-use memory:    ").append(AboutJenkins.humanReadableSize(allocMem - freeMem)).append("\n");

        for(MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
            if (bean.getName().toLowerCase().contains("perm gen")) {
                MemoryUsage currentUsage = bean.getUsage();
                result.append(min).append(" PermGen used:     ").append(AboutJenkins.humanReadableSize(currentUsage.getUsed())).append("\n");
                result.append(min).append(" PermGen max:      ").append(AboutJenkins.humanReadableSize(currentUsage.getMax())).append("\n");
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

