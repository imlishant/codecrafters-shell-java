import java.io.File;
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
    void execute(String arguments);
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

    public void handleCommand(String input) {
        String[] parts = input.split(" ", 2);
        String commandName = parts[0];
        String arguments = parts.length > 1 ? parts[1] : "";
        String[] argList = arguments.isEmpty() ? new String[0] : arguments.split(" ");

        Command command = commands.get(commandName);
        if (command != null) {
            command.execute(arguments);
        } else if (!commandName.isEmpty()) {
            externalCommandExecutor.execute(commandName, argList, pathSearcher, shellState);
        } else {
            System.out.println(commandName + ": command not found");
        }
    }
}

class ExternalCommandExecutor {
    public void execute(String commandName, String[] argList, PathSearcher pathSearcher, ShellState shellState) {
        // Support direct path execution if command contains a '/'
        if (commandName.contains(File.separator)) {
            File direct = new File(commandName);
            if (!direct.isAbsolute()) {
                direct = new File(shellState.getCurrentDirectory(), commandName);
            }
            if (direct.exists() && direct.canExecute() && direct.isFile()) {
                runProcess(commandName,direct.getAbsolutePath(), argList, shellState);
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
        runProcess(commandName, executable.getAbsolutePath(), argList, shellState);
    }

    private void runProcess(String commandName, String executablePath, String[] argList, ShellState shellState) {
        try {
            List<String> commandWithArgs = new ArrayList<>(1 + argList.length);
            commandWithArgs.add(executablePath); // absolute path prevents ambiguity
            commandWithArgs.add(commandName);
            for (String a : argList) {
                if (!a.isEmpty()) commandWithArgs.add(a);
            }

            ProcessBuilder pb = new ProcessBuilder(commandWithArgs);
            // set working directory to current shell directory (emulated cd)
            pb.directory(shellState.getCurrentDirectory());
            pb.redirectErrorStream(true); // merge stderr into stdout for now (simpler)

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
    public void execute(String arguments) {
        System.out.println(arguments);
    }
}

class ExitCommand implements Command {
    @Override
    public void execute(String arguments) {
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
    public void execute(String arguments) {
        if (BUILTIN_COMMANDS.contains(arguments)) {
            System.out.println(arguments + " is a shell builtin");
        } else {
            List<File> executableFiles = pathSearcher.search(arguments);
            if (executableFiles.isEmpty()) {
                System.out.println(arguments + ": not found");
            } else {
                System.out.println(arguments + " is " + executableFiles.get(0));
            }
        }
    }
}

class PwdCommand implements Command {
    private final ShellState shellState;
    PwdCommand(ShellState shellState) { this.shellState = shellState; }
    @Override
    public void execute(String arguments) {
        // Print emulated current directory (not System.getProperty once cd used)
        System.out.println(shellState.getCurrentDirectory().getAbsolutePath());
    }
}

class CdCommand implements Command {
    private final ShellState shellState;
    CdCommand(ShellState shellState) { this.shellState = shellState; }

    @Override
    public void execute(String arguments) {
        String targetRaw = arguments.trim();
        if (targetRaw.isEmpty()) {
            // No args -> HOME
            String home = System.getenv("HOME");
            if (home == null || home.isEmpty()) {
                // stay put silently (some shells print an error if HOME unset)
                return;
            }
            changeDirectory(new File(home), home);
            return;
        }

        if ("~".equals(targetRaw)) {
            String home = System.getenv("HOME");
            if (home != null) {
                changeDirectory(new File(home), home);
            } else {
                System.out.println("cd: ~: HOME not set");
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

        if (!target.exists()) {
            System.out.println("cd: " + targetRaw + ": No such file or directory");
            return;
        }
        if (!target.isDirectory()) {
            System.out.println("cd: " + targetRaw + ": Not a directory");
            return;
        }
        if (!target.canRead()) { // simplistic permission check
            System.out.println("cd: " + targetRaw + ": Permission denied");
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
        }
    }
}