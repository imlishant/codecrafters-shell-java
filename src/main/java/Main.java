import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.HashSet;

public class Main {
    public static void main(String[] args) {
        CommandHandler commandHandler = new CommandHandler();
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("$ ");
                String input = scanner.nextLine();
                // if builtin exists, execute them.
                // else search these external commands in the path dirs.
                // need to find the these and find these if they are executables or now
                // and if they are they need to be executed with all the arguments given, this can be a list of arguments.
                // handle the edge cases gracefully. 
                commandHandler.handleCommand(input);
                // this path search is for the type command and needs to be handle this casees as well along with the current type check
                // and so the type command handles this with the inbuilt commands and check the directories as well to find the file and is it executable or not asa per the directories in the path list. 
                // should handle all the edge cases of invalid path, non executable files to handle all this gracefully. 
            }
        }
    }
}

interface Command {
    void execute(String arguments);
}

class CommandHandler {
    private final Map<String, Command> commands = new HashMap<>();

    public CommandHandler() {
        commands.put("echo", new EchoCommand());
        commands.put("exit", new ExitCommand());
        commands.put("type", new TypeCommand());
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
            executeExternalCommand(commandName, argList);
        } else {
            System.out.println(commandName + ": command not found");
        }
    }

    private void executeExternalCommand(String commandName, String[] argList) {
        String path = System.getenv("PATH");
        String[] dirs = path == null ? new String[0] : path.split(File.pathSeparator);
        boolean found = false;

        for (String dir : dirs) {
            File file = new File(dir, commandName);
            if (file.exists() && file.canExecute()) {
                try {
                    String[] cmdArray = new String[argList.length + 1];
                    cmdArray[0] = commandName;
                    System.arraycopy(argList, 0, cmdArray, 1, argList.length);

                    ProcessBuilder pb = new ProcessBuilder(cmdArray);
                    Process process = pb.start();

                    try (Scanner outputScanner = new Scanner(process.getInputStream())) {
                        while (outputScanner.hasNextLine()) {
                            System.out.println(outputScanner.nextLine());
                        }
                    }

                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        System.err.println("Command exited with code: " + exitCode);
                    }
                } catch (Exception e) {
                    System.err.println("Error executing command: " + e.getMessage());
                }
                found = true;
                break;
            }
        }

        if (!found) {
            System.out.println(commandName + ": command not found");
        }
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

    static {
        BUILTIN_COMMANDS.add("echo");
        BUILTIN_COMMANDS.add("exit");
        BUILTIN_COMMANDS.add("type");
    }

    // need to add path search logic for valid or invalid commands and handle the edge cases gracefully.
    String path = System.getenv("PATH");
    String[] dirs = path == null ? new String[0] : path.split(File.pathSeparator);


    @Override
    public void execute(String arguments) {
        if (BUILTIN_COMMANDS.contains(arguments)) {
            System.out.println(arguments + " is a shell builtin");
        } else if (dirs.length > 0) {
            // Logic to check if the command exists in any of the directories in PATH
            boolean found = false;
            // System.out.println("path dirs length: " + dirs.length);
            // System.out.println("path name: " + path);
            for (String dir : dirs) {
                // print debug for dir 
                // System.out.println("Checking directory: " + dir);
                File file = new File(dir, arguments);
                if (file.exists() && file.canExecute()) {
                    System.out.println(arguments + " is " + file);
                    found = true;
                    break;
                }
            }            
            if (!found) {
                System.out.println(arguments + ": not found");
            }
        } else {
            System.out.println(arguments + ": not found");
        }
    }
}
