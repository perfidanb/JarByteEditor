package vn.perfidanb.jarbe.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import vn.perfidanb.jarbe.diff.DiffEngine;
import vn.perfidanb.jarbe.model.JarEntryData;
import vn.perfidanb.jarbe.model.JarProject;
import vn.perfidanb.jarbe.model.ProjectStats;
import vn.perfidanb.jarbe.model.SearchResult;
import vn.perfidanb.jarbe.search.SearchEngine;
import vn.perfidanb.jarbe.search.SearchType;
import vn.perfidanb.jarbe.service.CallGraphService;
import vn.perfidanb.jarbe.service.JarProjectService;
import vn.perfidanb.jarbe.service.StatisticsEngine;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "jarbe",
        mixinStandardHelpOptions = true,
        version = "JarByteEditor 1.0.0",
        description = "Desktop and CLI bytecode editor for JAR archives.",
        subcommands = {
                JarbeCli.ListCommand.class,
                JarbeCli.DisasmCommand.class,
                JarbeCli.AsmCommand.class,
                JarbeCli.ReplaceStringCommand.class,
                JarbeCli.SearchCommand.class,
                JarbeCli.StatsCommand.class,
                JarbeCli.DiffCommand.class,
                JarbeCli.CallGraphCommand.class
        }
)
public final class JarbeCli implements Callable<Integer> {
    @Override
    public Integer call() {
        System.out.println("Use --help to see commands. Run without arguments to open the GUI.");
        return 0;
    }

    @Command(name = "list", description = "List entries inside a jar or zip archive.")
    static final class ListCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Input .jar or .zip")
        Path input;

        @Override
        public Integer call() throws Exception {
            JarProject project = new JarProjectService().open(input);
            System.out.println(project.displayName());
            for (JarEntryData entry : project.sortedEntries()) {
                if (!entry.directory()) {
                    System.out.printf("  %-15s %8d  %s%n", entry.type(), entry.size(), entry.path());
                } else {
                    System.out.printf("  %-15s %8s  %s%n", entry.type(), "", entry.path());
                }
            }
            return 0;
        }
    }

    @Command(name = "disasm", description = "Export classes as .class plus editable .jasm files and resources.")
    static final class DisasmCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Input .jar or .zip")
        Path input;

        @Parameters(index = "1", description = "Output project directory")
        Path output;

        @Override
        public Integer call() throws Exception {
            JarProjectService service = new JarProjectService();
            JarProject project = service.open(input);
            service.exportProject(project, output);
            System.out.println("Exported project to " + output);
            return 0;
        }
    }

    @Command(name = "asm", description = "Assemble an exported project directory back into a jar.")
    static final class AsmCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Project directory produced by disasm/export")
        Path projectDir;

        @Parameters(index = "1", description = "Output jar")
        Path output;

        @Option(names = "--target", description = "Target Java version: 8, 11, 17, 21, 25. Defaults to original class version.")
        Integer target;

        @Override
        public Integer call() throws Exception {
            new JarProjectService().assembleExportedProject(projectDir, output, target);
            System.out.println("Built " + output);
            return 0;
        }
    }

    @Command(name = "replace-string", description = "Replace a string in class LDC constants, annotation values, and text resources.")
    static final class ReplaceStringCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Input jar")
        Path input;

        @Parameters(index = "1", description = "Find text")
        String find;

        @Parameters(index = "2", description = "Replacement text")
        String replacement;

        @Parameters(index = "3", description = "Output jar")
        Path output;

        @Option(names = "--target", description = "Target Java version. Defaults to original class version.")
        Integer target;

        @Override
        public Integer call() throws Exception {
            JarProjectService service = new JarProjectService();
            JarProject project = service.open(input);
            int changed = service.replaceString(project, find, replacement, target);
            service.saveAsJar(project, output);
            System.out.println("Changed entries: " + changed);
            System.out.println("Built " + output);
            return 0;
        }
    }

    @Command(name = "search", description = "Search string, class, method, field, annotation, or opcode.")
    static final class SearchCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Input jar")
        Path input;

        @Parameters(index = "1", description = "Query")
        String query;

        @Option(names = "--type", description = "ALL, STRING, CLASS, METHOD, FIELD, ANNOTATION, OPCODE", defaultValue = "ALL")
        SearchType type;

        @Override
        public Integer call() throws Exception {
            JarProject project = new JarProjectService().open(input);
            List<SearchResult> results = new SearchEngine().search(project, query, type);
            if (results.isEmpty()) {
                System.out.println("No results");
            } else {
                results.forEach(result -> System.out.println(result.compact()));
            }
            return 0;
        }
    }

    @Command(name = "stats", description = "Print jar statistics.")
    static final class StatsCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Input jar")
        Path input;

        @Override
        public Integer call() throws Exception {
            JarProject project = new JarProjectService().open(input);
            ProjectStats stats = new StatisticsEngine().calculate(project);
            System.out.println(stats.toDisplayString());
            return 0;
        }
    }

    @Command(name = "diff", description = "Compare two jars.")
    static final class DiffCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Old jar")
        Path oldJar;

        @Parameters(index = "1", description = "New jar")
        Path newJar;

        @Override
        public Integer call() throws Exception {
            JarProjectService service = new JarProjectService();
            System.out.print(new DiffEngine().diff(service.open(oldJar), service.open(newJar)).toDisplayString());
            return 0;
        }
    }

    @Command(name = "callgraph", description = "Print a simple method call graph.")
    static final class CallGraphCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Input jar")
        Path input;

        @Override
        public Integer call() throws Exception {
            JarProject project = new JarProjectService().open(input);
            System.out.print(new CallGraphService().toDisplayString(project));
            return 0;
        }
    }
}
