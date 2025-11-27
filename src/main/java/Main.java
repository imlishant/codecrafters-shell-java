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

    public CommandHandler() {
        commands.put("echo", new EchoCommand());
        commands.put("exit", new ExitCommand());
        commands.put("type", new TypeCommand());
    }

    public void handleCommand(String input) {
        String[] parts = input.split(" ", 2);
        String commandName = parts[0];
        String arguments = parts.length > 1 ? parts[1] : "";

        Command command = commands.get(commandName);
        if (command != null) {
            command.execute(arguments);
        } else {
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

    @Override
    public void execute(String arguments) {
        if (BUILTIN_COMMANDS.contains(arguments)) {
            System.out.println(arguments + " is a shell builtin");
        } else {
            System.out.println(arguments + ": not found");
        }
    }
}
