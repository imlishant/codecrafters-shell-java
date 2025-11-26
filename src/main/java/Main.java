import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("$ ");
                String command = scanner.nextLine();
                if (command.equals("exit")) {
                    System.exit(0);
                } else if (command.startsWith("echo ")) {
                    System.out.println(command.substring(5));
                } else if (command.startsWith("type ")) {
                   String cmd = command.substring(5);
                   if (cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type")) {
                          System.out.println(cmd + " is a shell builtin");
                     } else {
                          System.out.println(cmd + ": not found");
                        //   System.exit(0);
                   } 
                } else {
                    System.out.println(command + ": command not found");
                }
            }
        }
    }
}
