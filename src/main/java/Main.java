import java.io.File;
import java.io.PrintStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        // System.out.println("curr dir: " + System.getProperty("user.dir"));
        // System.out.println("curr abs path: " + new File(".").getAbsolutePath());
        CommandHandler commandHandler = new CommandHandler();
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {

                // System.out.println("curr dir: " + System.getProperty("user.dir"));
                // System.out.println("curr abs path: " + new File(".").getAbsolutePath());
                // System.out.println("curr abs canonical path: " + new File(".").getCanonicalPath());

                System.out.print("$ ");
                String input = scanner.nextLine();
                commandHandler.handleCommand(input);
            }
        }
    }
}

interface Command {
    // void execute(String arguments); // default implementation where redirectFile is null or not necessary
    void execute(String arguments, File redirectFile);

    // default void execute(String arguments) {
    //     execute(arguments, null);
    // }

    default void writeOutput(String content, File redirectFile) {
        if (redirectFile != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(redirectFile))) {
                writer.println(content);
            } catch (IOException e) {
                System.err.println("Error redirecting output: " + e.getMessage());
            }
        } else {
            System.out.println(content);
        }
    }
}

// ShellState encapsulates mutable shell-wide state like the current working directory
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

        // String[] parsedArgList = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];

        List<String> argParam = new ArrayList<>();
        File redirectFile = null;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals(">") || parts[i].equals("1>")) {
                if (i+1 < parts.length) {
                    redirectFile = new File(parts[i+1]);
                    // break;
                    i++;
                }
            } else {
                argParam.add(parts[i]);
            }
        }

        String[] cleanedParts = argParam.toArray(new String[0]);
        String[] parsedArgList = cleanedParts.length > 1 ? Arrays.copyOfRange(cleanedParts, 1, cleanedParts.length) : new String[0];

        String parsedArg = String.join(" ", parsedArgList);

        Command command = commands.get(commandName);
        if (command != null) {
            // command.execute(arguments);
            // command.execute(parsedArg);
            command.execute(parsedArg, redirectFile);
        } else if (!commandName.isEmpty()) {
            // externalCommandExecutor.execute(commandName, parsedArgList, pathSearcher, shellState);
            externalCommandExecutor.execute(commandName, parsedArgList, pathSearcher, shellState, redirectFile);
        } else {
            System.out.println(commandName + ": command not found");
        }
    }
}

class ExternalCommandExecutor {
    public void execute(String commandName, String[] argList, PathSearcher pathSearcher, ShellState shellState, File redirectFile) {
        // Support direct path execution if command contains a '/'
        if (commandName.contains(File.separator)) {
            File direct = new File(commandName);
            if (!direct.isAbsolute()) {
                direct = new File(shellState.getCurrentDirectory(), commandName);
            }
            if (direct.exists() && direct.canExecute() && direct.isFile()) {
                runProcess(commandName,direct.getAbsolutePath(), argList, shellState, redirectFile);
                return;
            } else {
                System.out.println(commandName + ": command not found");
                return;
            }
        }

        List<File> executableFiles = pathSearcher.search(commandName);
        if (executableFiles.isEmpty()) {
            System.out.println(commandName + ": command not found");
            return;
        }

        File executable = executableFiles.get(0); // Use the first found executable
        runProcess(commandName, executable.getAbsolutePath(), argList, shellState, redirectFile);
    }

    private void runProcess(String commandName, String executablePath, String[] argList, ShellState shellState, File redirectFile) {
        try {
            List<String> commandWithArgs = new ArrayList<>(1 + argList.length);
            // commandWithArgs.add(executablePath); // absolute path prevents ambiguity // commenting it out as the test is failing
            commandWithArgs.add(commandName); // demands the restriction of command name and its args only, no other param
            for (String a : argList) {
                if (!a.isEmpty()) commandWithArgs.add(a);
            }

            ProcessBuilder pb = new ProcessBuilder(commandWithArgs);
            // set working directory to current shell directory (emulated cd)
            pb.directory(shellState.getCurrentDirectory());
            // pb.redirectErrorStream(true); // merge stderr into stdout for now (simpler)
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);

            if (redirectFile != null) {
                pb.redirectOutput(ProcessBuilder.Redirect.to(redirectFile));
            } else {
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
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
    public void execute(String arguments, File redirectFile) {
        // String[] parsedArgList = arguments.isEmpty() ? new String[0] : CommandHandler.parse(arguments);
        // System.out.println(arguments);
        writeOutput(arguments, redirectFile);
}

class ExitCommand implements Command {
    @Override
    public void execute(String arguments, File redirectFile) {
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
    public void execute(String arguments, File redirectFile) {
        if (BUILTIN_COMMANDS.contains(arguments)) {
            // System.out.println(arguments + " is a shell builtin");
            writeOutput(arguments + " is a shell builtin", redirectFile);
        } else {
            List<File> executableFiles = pathSearcher.search(arguments);
            if (executableFiles.isEmpty()) {
                // System.out.println(arguments + ": not found");
                writeOutput(arguments + ": not found", redirectFile);
            } else {
                // System.out.println(arguments + " is " + executableFiles.get(0));
                writeOutput(arguments + " is " + executableFiles.get(0), redirectFile);
            }
        }
    }
}

class PwdCommand implements Command {
    private final ShellState shellState;
    PwdCommand(ShellState shellState) { this.shellState = shellState; }
    @Override
    public void execute(String arguments, File redirectFile) {
        // Print emulated current directory (not System.getProperty once cd used)
        // System.out.println(shellState.getCurrentDirectory().getAbsolutePath());
        writeOutput(shellState.getCurrentDirectory().getAbsolutePath(), redirectFile);
    }
}

class CdCommand implements Command {
    private final ShellState shellState;
    CdCommand(ShellState shellState) { this.shellState = shellState; }

    @Override
    public void execute(String arguments, File redirectFile) {
        String targetRaw = arguments.trim();
        if (targetRaw.isEmpty()) {
            // No args -> HOME
            String home = System.getenv("HOME");
            if (home == null || home.isEmpty()) {
                // stay put silently (some shells print an error if HOME unset)
                return;
            }
            changeDirectory(new File(home), home);
            // changeDirectory(new File(home), home, redirectFile);
            return;
        }

        if ("~".equals(targetRaw)) {
            String home = System.getenv("HOME");
            if (home != null) {
                changeDirectory(new File(home), home);
                // changeDirectory(new File(home), home, redirectFile);
            } else {
                System.out.println("cd: ~: HOME not set");
                // writeOutput("cd: ~: HOME not set", redirectFile);
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
        } catch (IOException ignored) { /* fallback to original */ }

        // these are later stages tasks
        if (!target.exists()) {
            System.out.println("cd: " + targetRaw + ": No such file or directory");
            // writeOutput("cd: " + targetRaw + ": No such file or directory", redirectFile);
            return;
        }
        if (!target.isDirectory()) {
            System.out.println("cd: " + targetRaw + ": Not a directory");
            // writeOutput("cd: " + targetRaw + ": Not a directory", redirectFile);
            return;
        }
        if (!target.canRead()) { // simplistic permission check
            System.out.println("cd: " + targetRaw + ": Permission denied");
            // writeOutput("cd: " + targetRaw + ": Permission denied", redirectFile);
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
            // writeOutput("cd: " + display + ": Unable to change directory", redirectFile);
        }
    }
}