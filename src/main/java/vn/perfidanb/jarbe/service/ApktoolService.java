package vn.perfidanb.jarbe.service;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ApktoolService {

    private static File cachedApktoolJar = null;

    /**
     * Extracts the bundled apktool.jar from the classpath to a temporary file.
     * This allows us to execute it using ProcessBuilder.
     */
    public static synchronized File getApktoolJar() throws Exception {
        if (cachedApktoolJar != null && cachedApktoolJar.exists()) {
            return cachedApktoolJar;
        }

        Path tempJar = Files.createTempFile("jarbe_apktool", ".jar");
        tempJar.toFile().deleteOnExit();

        try (InputStream is = ApktoolService.class.getResourceAsStream("/libs/apktool.jar")) {
            if (is == null) {
                throw new IllegalStateException("apktool.jar not found in classpath (/libs/apktool.jar). Please make sure it is bundled.");
            }
            Files.copy(is, tempJar, StandardCopyOption.REPLACE_EXISTING);
        }

        cachedApktoolJar = tempJar.toFile();
        return cachedApktoolJar;
    }

    /**
     * Prepares a ProcessBuilder for an apktool command.
     */
    public static ProcessBuilder prepareCommand(String... args) throws Exception {
        File apktool = getApktoolJar();
        String[] fullArgs = new String[args.length + 4]; // Added 1 for -Xmx
        fullArgs[0] = "java";
        fullArgs[1] = "-Xmx512m"; // Limit RAM usage for weak machines
        fullArgs[2] = "-jar";
        fullArgs[3] = apktool.getAbsolutePath();
        System.arraycopy(args, 0, fullArgs, 4, args.length);
        
        ProcessBuilder builder = new ProcessBuilder(fullArgs);
        builder.redirectErrorStream(true);
        return builder;
    }

    private static String getOptimalJobs() {
        int cores = Runtime.getRuntime().availableProcessors();
        int jobs = Math.max(1, (int) (cores * 0.5)); // Max 50% CPU
        return String.valueOf(jobs);
    }

    public static ProcessBuilder decode(File apkFile, File outputDir) throws Exception {
        return prepareCommand("d", "-j", getOptimalJobs(), "-f", "-o", outputDir.getAbsolutePath(), apkFile.getAbsolutePath());
    }

    public static ProcessBuilder decodeResourcesOnly(File apkFile, File outputDir) throws Exception {
        return prepareCommand("d", "-s", "-j", getOptimalJobs(), "-f", "-o", outputDir.getAbsolutePath(), apkFile.getAbsolutePath());
    }

    public static ProcessBuilder decodeSourcesOnly(File apkFile, File outputDir) throws Exception {
        return prepareCommand("d", "-r", "-j", getOptimalJobs(), "-f", "-o", outputDir.getAbsolutePath(), apkFile.getAbsolutePath());
    }

    public static ProcessBuilder build(File inputDir, File outputApk) throws Exception {
        return prepareCommand("b", "-j", getOptimalJobs(), "-f", "-o", outputApk.getAbsolutePath(), inputDir.getAbsolutePath());
    }
}
