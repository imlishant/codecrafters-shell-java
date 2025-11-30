import java.io.File;
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

class CommandHandler {
    private final Map<String, Command> commands = new HashMap<>();
    private final PathSearcher pathSearcher = new PathSearcher();
    private final ExternalCommandExecutor externalCommandExecutor = new ExternalCommandExecutor();

    public CommandHandler() {
        commands.put("echo", new EchoCommand());
        commands.put("exit", new ExitCommand());
        commands.put("type", new TypeCommand(pathSearcher));
        commands.put("pwd", new PwdCommand());
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
            externalCommandExecutor.execute(commandName, argList, pathSearcher);
        } else {
            System.out.println(commandName + ": command not found");
        }
    }
}

class ExternalCommandExecutor {
    public void execute(String commandName, String[] argList, PathSearcher pathSearcher) {
        List<File> executableFiles = pathSearcher.search(commandName);
        if (executableFiles.isEmpty()) {
            System.out.println(commandName + ": command not found");
            return;
        }

        // File executable = executableFiles.get(0); // Use the first found executable
        try {
            String[] commandWithArgs = new String[argList.length + 1];
            // commandWithArgs[0] = executable.getAbsolutePath();
            commandWithArgs[0] = commandName;
            System.arraycopy(argList, 0, commandWithArgs, 1, argList.length);

            ProcessBuilder pb = new ProcessBuilder(commandWithArgs);
            // Process process = pb.start();

            // this is still the best way to capture output if needed as it provides access to streams for further processing and filtering. and no known issue of buffering and concurrency.
            // try (Scanner outputScanner = new Scanner(process.getInputStream())) {
            //     while (outputScanner.hasNextLine()) {
            //         System.out.println(outputScanner.nextLine());
            //     }
            // }

            pb.inheritIO();
            Process process = pb.start();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("Command exited with code: " + exitCode);
            }
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
    @Override
    public void execute(String arguments) {
        System.out.println(System.getProperty("user.dir"));
    }
}