package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.PrintedContent;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

class ItemsContent extends PrintedContent {
    ItemsContent() {
        super("items.md");
    }
    @Override protected void printTo(PrintWriter out) throws IOException {
        final Jenkins jenkins = Jenkins.getInstance();
        Map<String,Integer> containerCounts = new TreeMap<String,Integer>();
        Map<String, Stats> jobStats = new HashMap<String,Stats>();
        Stats jobTotal = new Stats();
        Map<String, Stats> containerStats = new HashMap<String, Stats>();
        // RunMap.createDirectoryFilter protected, so must do it by hand:
        DateFormat BUILD_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        // historically did not use a consistent time zone, so use default
        for (Item i : jenkins.getAllItems()) {
            String key = i.getClass().getName();
            Integer cnt = containerCounts.get(key);
            containerCounts.put(key, cnt == null ? 1 : cnt + 1);
            if (i instanceof Job) {
                Job<?,?> j = (Job) i;
                // too expensive: int builds = j.getBuilds().size();
                int builds = 0;
                // protected access: File buildDir = j.getBuildDir();
                File buildDir = jenkins.getBuildDirFor(j);
                boolean newFormat = new File(buildDir, "legacyIds").isFile(); // JENKINS-24380
                File[] buildDirs = buildDir.listFiles();
                if (buildDirs != null) {
                    for (File d : buildDirs) {
                        String name = d.getName();
                        if (newFormat) {
                            try {
                                Integer.parseInt(name);
                                if (d.isDirectory()) {
                                    builds++;
                                }
                            } catch (NumberFormatException x) {
                                // something else
                            }
                        } else /* legacy format */if (AboutJenkins.mayBeDate(name)) {
                            // check for real
                            try {
                                BUILD_FORMAT.parse(name);
                                if (d.isDirectory()) {
                                    builds++;
                                }
                            } catch (ParseException x) {
                                // symlink etc., ignore
                            }
                        }
                    }
                }
                jobTotal.add(builds);
                Stats s = jobStats.get(key);
                if (s == null) {
                    jobStats.put(key, s = new Stats());
                }
                s.add(builds);
            }
            if (i instanceof ItemGroup) {
                Stats s = containerStats.get(key);
                if (s == null) {
                    containerStats.put(key, s = new Stats());
                }
                s.add(((ItemGroup) i).getItems().size());
            }
        }
        out.println("Item statistics");
        out.println("===============");
        out.println();
        for (Map.Entry<String,Integer> entry : containerCounts.entrySet()) {
            String key = entry.getKey();
            out.println("  * `" + key + "`");
            out.println("    - Number of items: " + entry.getValue());
            Stats s = jobStats.get(key);
            if (s != null) {
                out.println("    - Number of builds per job: " + s);
            }
            s = containerStats.get(key);
            if (s != null) {
                out.println("    - Number of items per container: " + s);
            }
        }
        out.println();
        out.println("Total job statistics");
        out.println("======================");
        out.println();
        out.println("  * Number of jobs: " + jobTotal.n());
        out.println("  * Number of builds per job: " + jobTotal);
    }

    protected static class Stats {
        private int s0 = 0;
        private long s1 = 0;
        private long s2 = 0;

        public synchronized void add(int x) {
            s0++;
            s1 += x;
            s2 += x * (long) x;
        }

        public synchronized double x() {
            return s1 / (double) s0;
        }

        private static double roundToSigFig(double num, int sigFig) {
            if (num == 0) {
                return 0;
            }
            final double d = Math.ceil(Math.log10(num < 0 ? -num : num));
            final int pow = sigFig - (int) d;
            final double mag = Math.pow(10, pow);
            final long shifted = Math.round(num * mag);
            return shifted / mag;
        }

        public synchronized double s() {
            if (s0 >= 2) {
                double v = Math.sqrt((s0 * (double) s2 - s1 * (double) s1) / s0 / (s0 - 1));
                if (s0 <= 100) {
                    return roundToSigFig(v, 1); // 0.88*SD to 1.16*SD
                }
                if (s0 <= 1000) {
                    return roundToSigFig(v, 2); // 0.96*SD to 1.05*SD
                }
                return v;
            } else {
                return Double.NaN;
            }
        }

        public synchronized String toString() {
            if (s0 == 0) {
                return "N/A";
            }
            if (s0 == 1) {
                return Long.toString(s1) + " [n=" + s0 + "]";
            }
            return Double.toString(x()) + " [n=" + s0 + ", s=" + s() + "]";
        }

        public synchronized int n() {
            return s0;
        }
    }
}
