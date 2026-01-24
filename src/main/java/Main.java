import java.io.File;
import java.io.PrintStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        CommandHandler commandHandler = new CommandHandler();
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("$ ");
                String input = scanner.nextLine();
                commandHandler.handleCommand(input);
            }
        }
    }
}

class RedirectionContext {
    File stdoutFile;
    boolean appendStdout;
    File stderrFile;
    boolean appendStderr;

    RedirectionContext() {
        this.stdoutFile = null;
        this.appendStdout = false;
        this.stderrFile = null;
        this.appendStderr = false;
    }
}

interface Command {
    // void execute(String arguments); // default implementation where stdoutFile is
    // null or not necessary
    // void execute(String arguments, File stdoutFile, File stderrFile);
    void execute(String arguments, RedirectionContext context);

    // default void execute(String arguments) {
    // execute(arguments, null);
    // }

    default void writestdoutFile(String content, RedirectionContext context) {
        if (context.stdoutFile != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(context.stdoutFile, context.appendStdout))) {
                writer.println(content);
            } catch (IOException e) {
                System.err.println("Error redirecting output: " + e.getMessage());
            }
        } else {
            System.out.println(content);
        }
    }

    // rigth now not needed but can be used later.
    default void writestderrFile(String content, RedirectionContext context) {
        if (context.stderrFile != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(context.stderrFile, context.appendStderr))) {
                writer.println(content);
            } catch (IOException e) {
                System.err.println("Error redirecting output: " + e.getMessage());
            }
        } else {
            System.out.println(content);
        }
    }
}

// ShellState encapsulates mutable shell-wide state like the current working
// directory
class ShellState {
    private File currentDirectory;

    ShellState(File initial) {
        this.currentDirectory = initial;
    }

    public File getCurrentDirectory() {
        return currentDirectory;
    }

    public void setCurrentDirectory(File newDir) {
        this.currentDirectory = newDir;
    }
}

class CommandHandler {
    private final Map<String, Command> commands = new HashMap<>();
    private final PathSearcher pathSearcher = new PathSearcher();
    private final ExternalCommandExecutor externalCommandExecutor = new ExternalCommandExecutor();
    private final ShellState shellState = new ShellState(new File(System.getProperty("user.dir")));

    public CommandHandler() {
        commands.put("echo", new EchoCommand());
        commands.put("exit", new ExitCommand());
        commands.put("type", new TypeCommand(pathSearcher));
        commands.put("pwd", new PwdCommand(shellState));
        commands.put("cd", new CdCommand(shellState));
    }

    public String[] parseQuote(String str) {
        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            if (inSingleQuote) {
                if (c == '\'') {
                    inSingleQuote = false;
                } else {
                    currentArg.append(c);
                }
            } else if (inDoubleQuote) {
                if (c == '\"') {
                    inDoubleQuote = false;
                } else if (c == '\\') {
                    if (i + 1 < str.length()) {
                        char next = str.charAt(i + 1);
                        if (next == '\"' || next == '\\') {
                            currentArg.append(next);
                            i++;
                            // continue;
                        } else {
                            currentArg.append(c);
                        }
                    } else {
                        currentArg.append(c);
                    }
                } else {
                    currentArg.append(c);
                }
            } else {
                if (c == '\\') {
                    if (i + 1 < str.length()) {
                        char next = str.charAt(i + 1);
                        currentArg.append(next);
                        i++;
                        continue;
                    }
                } else if (c == '\'') {
                    inSingleQuote = true;
                } else if (c == '\"') {
                    inDoubleQuote = true;
                } else if (Character.isWhitespace(c)) {
                    if (currentArg.length() > 0) {
                        args.add(currentArg.toString());
                        currentArg.setLength(0);
                    }
                } else {
                    currentArg.append(c);
                }
            }
        }

        if (currentArg.length() > 0) {
            args.add(currentArg.toString());
        }
        // return String.join(" ", args);
        return args.toArray(new String[0]);
    }

    public void handleCommand(String input) {
        // parse(input);
        // String[] parts = input.split(" ", 2);
        String[] parts = parseQuote(input);
        String commandName = parts[0];

        // String[] parsedArgList = parts.length > 1 ? Arrays.copyOfRange(parts, 1,
        // parts.length) : new String[0];

        List<String> argParam = new ArrayList<>();
        // File stdoutFile = null;
        // File stderrFile = null;
        // File appendStdoutFile;

        // boolean append = false;

        RedirectionContext context = new RedirectionContext();

        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals(">") || parts[i].equals("1>")) {
                if (i + 1 < parts.length) {
                    context.stdoutFile = new File(parts[i + 1]);
                    i++;
                }
            } else if (parts[i].equals("2>")) {
                if (i + 1 < parts.length) {
                    context.stderrFile = new File(parts[i + 1]);
                    i++;
                }
            } else if (parts[i].equals(">>") || parts[i].equals("1>>")) {
                if (i + 1 < parts.length) {
                    context.stdoutFile = new File(parts[i + 1]);
                    context.appendStdout = true;
                    i++;
                }
            } else if (parts[i].equals("2>>")) {
                if (i + 1 < parts.length) {
                    context.stderrFile = new File(parts[i + 1]);
                    context.appendStderr = true;
                    i++;
                }
            } else {
                argParam.add(parts[i]);
            }
        }

        String[] cleanedParts = argParam.toArray(new String[0]);
        String[] parsedArgList = cleanedParts.length > 1 ? Arrays.copyOfRange(cleanedParts, 1, cleanedParts.length)
                : new String[0];

        String parsedArg = String.join(" ", parsedArgList);

        Command command = commands.get(commandName);
        if (command != null) {
            // Ensure redirection files are created even if the command doesn't write to
            // them
            if (context.stdoutFile != null) {
                try {
                    new FileWriter(context.stdoutFile, context.appendStdout).close();
                } catch (IOException e) {
                    System.err.println("Error creating output file: " + e.getMessage());
                }
            }
            if (context.stderrFile != null) {
                try {
                    new FileWriter(context.stderrFile, context.appendStderr).close();
                } catch (IOException e) {
                    System.err.println("Error creating error file: " + e.getMessage());
                }
            }
            // if (appendStdoutFile != null) {
            // try {
            // new FileWriter(appendStdoutFile, true).close();
            // } catch (IOException e) {
            // System.err.println("Error creating error file: " + e.getMessage());
            // }
            // }
            command.execute(parsedArg, context);
        } else if (!commandName.isEmpty()) {
            // externalCommandExecutor.execute(commandName, parsedArgList, pathSearcher,
            // shellState);
            externalCommandExecutor.execute(commandName, parsedArgList, pathSearcher, shellState, context);
        } else {
            System.out.println(commandName + ": command not found");
        }
    }
}

class ExternalCommandExecutor {
    public void execute(String commandName, String[] argList, PathSearcher pathSearcher, ShellState shellState,
            RedirectionContext context) {
        // Support direct path execution if command contains a '/'
        if (commandName.contains(File.separator)) {
            File direct = new File(commandName);
            if (!direct.isAbsolute()) {
                direct = new File(shellState.getCurrentDirectory(), commandName);
            }
            if (direct.exists() && direct.canExecute() && direct.isFile()) {
                runProcess(commandName, direct.getAbsolutePath(), argList, shellState, context);
                return;
            } else {
                System.out.println(commandName + ": command not found");
                // writestderrFile(commandName + ": command not found", stderrFile);
                return;
            }
        }

        List<File> executableFiles = pathSearcher.search(commandName);
        if (executableFiles.isEmpty()) {
            // System.out.println(commandName + ": command not found");
            writestderrFile(commandName + ": command not found", context);
            return;
        }

        File executable = executableFiles.get(0); // Use the first found executable
        runProcess(commandName, executable.getAbsolutePath(), argList, shellState, context);
    }

    private void writestderrFile(String content, RedirectionContext context) {
        if (context.stderrFile != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(context.stderrFile))) {
                writer.println(content);
            } catch (IOException e) {
                System.err.println("Error redirecting output: " + e.getMessage());
            }
        } else {
            System.out.println(content);
        }
    }

    private void runProcess(String commandName, String executablePath, String[] argList, ShellState shellState,
            RedirectionContext context) {
        try {
            List<String> commandWithArgs = new ArrayList<>(1 + argList.length);
            // commandWithArgs.add(executablePath); // absolute path prevents ambiguity //
            // commenting it out as the test is failing
            commandWithArgs.add(commandName); // demands the restriction of command name and its args only, no other
                                              // param
            for (String a : argList) {
                if (!a.isEmpty())
                    commandWithArgs.add(a);
            }

            ProcessBuilder pb = new ProcessBuilder(commandWithArgs);
            // set working directory to current shell directory (emulated cd)
            pb.directory(shellState.getCurrentDirectory());
            // pb.redirectErrorStream(true); // merge stderr into stdout for now (simpler)
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);

            if (context.stdoutFile != null) {
                // pb.redirectOutput(ProcessBuilder.Redirect.to(context.stdoutFile));
                pb.redirectOutput(context.appendStdout ? ProcessBuilder.Redirect.appendTo(context.stdoutFile)
                        : ProcessBuilder.Redirect.to(context.stdoutFile));
            } else {
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }

            if (context.stderrFile != null) {
                // pb.redirectError(context.stderrFile);
                pb.redirectError(context.appendStderr ? ProcessBuilder.Redirect.appendTo(context.stderrFile)
                        : ProcessBuilder.Redirect.to(context.stderrFile));
            } else {
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            Process process = pb.start();

            // Stream output predictably (avoid inheritIO race conditions explained earlier)
            try (Scanner outputScanner = new Scanner(process.getInputStream())) {
                while (outputScanner.hasNextLine()) {
                    System.out.println(outputScanner.nextLine());
                }
            }

            // Wait for completion (exit code suppressed unless needed for future logic)
            process.waitFor();
        } catch (Exception e) {
            System.err.println("Error executing command: " + e.getMessage());
        }
    }
}

class PathSearcher {
    private final String path = System.getenv("PATH");
    private final String[] directories = path == null ? new String[0] : path.split(File.pathSeparator);

    public List<File> search(String commandName) {
        List<File> foundFiles = new ArrayList<>();
        for (String dir : directories) {
            File file = new File(dir, commandName);
            if (file.exists() && file.canExecute()) {
                foundFiles.add(file);
            }
        }
        return foundFiles;
    }
}

class EchoCommand implements Command {
    @Override
    public void execute(String arguments, RedirectionContext context) {
        // String[] parsedArgList = arguments.isEmpty() ? new String[0] :
        // CommandHandler.parse(arguments);
        // System.out.println(arguments);
        writestdoutFile(arguments, context);
    }
}

class ExitCommand implements Command {
    @Override
    public void execute(String arguments, RedirectionContext context) {
        System.exit(0);
    }
}

class TypeCommand implements Command {
    private static final Set<String> BUILTIN_COMMANDS = new HashSet<>();
    private final PathSearcher pathSearcher;

    static {
        BUILTIN_COMMANDS.add("echo");
        BUILTIN_COMMANDS.add("exit");
        BUILTIN_COMMANDS.add("type");
        BUILTIN_COMMANDS.add("pwd");
        BUILTIN_COMMANDS.add("cd");
    }

    public TypeCommand(PathSearcher pathSearcher) {
        this.pathSearcher = pathSearcher;
    }

    @Override
    public void execute(String arguments, RedirectionContext context) {
        if (BUILTIN_COMMANDS.contains(arguments)) {
            // System.out.println(arguments + " is a shell builtin");
            writestdoutFile(arguments + " is a shell builtin", context);
        } else {
            List<File> executableFiles = pathSearcher.search(arguments);
            if (executableFiles.isEmpty()) {
                // System.out.println(arguments + ": not found");
                // writestdoutFile(arguments + ": not found", stdoutFile);
                writestderrFile(arguments + ": not found", context);
            } else {
                // System.out.println(arguments + " is " + executableFiles.get(0));
                writestdoutFile(arguments + " is " + executableFiles.get(0), context);
            }
        }
    }
}

class PwdCommand implements Command {
    private final ShellState shellState;

    PwdCommand(ShellState shellState) {
        this.shellState = shellState;
    }

    @Override
    public void execute(String arguments, RedirectionContext context) {
        // Print emulated current directory (not System.getProperty once cd used)
        // System.out.println(shellState.getCurrentDirectory().getAbsolutePath());
        writestdoutFile(shellState.getCurrentDirectory().getAbsolutePath(), context);
    }
}

class CdCommand implements Command {
    private final ShellState shellState;

    CdCommand(ShellState shellState) {
        this.shellState = shellState;
    }

    @Override
    public void execute(String arguments, RedirectionContext context) {
        String targetRaw = arguments.trim();
        if (targetRaw.isEmpty()) {
            // No args -> HOME
            String home = System.getenv("HOME");
            if (home == null || home.isEmpty()) {
                // stay put silently (some shells print an error if HOME unset)
                return;
            }
            changeDirectory(new File(home), home);
            // changeDirectory(new File(home), home, stdoutFile);
            return;
        }

        if ("~".equals(targetRaw)) {
            String home = System.getenv("HOME");
            if (home != null) {
                changeDirectory(new File(home), home);
                // changeDirectory(new File(home), home, stdoutFile);
            } else {
                // System.out.println("cd: ~: HOME not set");
                // writestdoutFile("cd: ~: HOME not set", stdoutFile);
                writestderrFile("cd: ~: HOME not set", context);
            }
            return;
        }

        File target = new File(targetRaw);
        if (!target.isAbsolute()) {
            target = new File(shellState.getCurrentDirectory(), targetRaw);
        }

        // Normalize path (resolve .. and .)
        try {
            target = target.getCanonicalFile();
        } catch (IOException ignored) {
            /* fallback to original */ }

        // these are later stages tasks
        if (!target.exists()) {
            // System.out.println("cd: " + targetRaw + ": No such file or directory");
            // writestdoutFile("cd: " + targetRaw + ": No such file or directory",
            // stdoutFile);
            writestderrFile("cd: " + targetRaw + ": No such file or directory", context);
            return;
        }
        if (!target.isDirectory()) {
            // System.out.println("cd: " + targetRaw + ": Not a directory");
            // writestdoutFile("cd: " + targetRaw + ": Not a directory", stdoutFile);
            writestderrFile("cd: " + targetRaw + ": Not a directory", context);
            return;
        }
        if (!target.canRead()) { // simplistic permission check
            // System.out.println("cd: " + targetRaw + ": Permission denied");
            // writestdoutFile("cd: " + targetRaw + ": Permission denied", stdoutFile);
            writestderrFile("cd: " + targetRaw + ": Permission denied", context);
            return;
        }

        shellState.setCurrentDirectory(target);
        // Do not print anything on success (usual shell behavior)
    }

    private void changeDirectory(File dir, String display) {
        if (dir.exists() && dir.isDirectory() && dir.canRead()) {
            shellState.setCurrentDirectory(dir);
        } else {
            System.out.println("cd: " + display + ": Unable to change directory");
            // writestdoutFile("cd: " + display + ": Unable to change directory",
            // stdoutFile);
        }
    }
}