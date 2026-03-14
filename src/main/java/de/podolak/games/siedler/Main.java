package de.podolak.games.siedler;

import de.podolak.games.siedler.client.ClientApplication;
import de.podolak.games.siedler.server.ServerApplication;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        switch (args[0].toLowerCase()) {
            case "server" -> ServerApplication.main(sliceArgs(args));
            case "client" -> ClientApplication.main(sliceArgs(args));
            default -> printUsage();
        }
    }

    private static String[] sliceArgs(String[] args) {
        if (args.length <= 1) {
            return new String[0];
        }

        String[] sliced = new String[args.length - 1];
        System.arraycopy(args, 1, sliced, 0, sliced.length);
        return sliced;
    }

    private static void printUsage() {
        System.out.println("Usage: java ... de.podolak.games.siedler.Main <server|client>");
    }
}
