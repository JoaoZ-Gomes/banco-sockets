import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Cliente {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║        BANCO DISTRIBUÍDO — CLIENTE       ║");
        System.out.println("╚══════════════════════════════════════════╝");

        System.out.print("Informe o IP do servidor (ex: 192.168.1.1): ");
        String ip = scanner.nextLine().trim();

        System.out.print("Informe a porta (ex: 12345): ");
        int porta = Integer.parseInt(scanner.nextLine().trim());

        System.out.println("\nConectando a " + ip + ":" + porta + "...");

        try (
            Socket socket          = new Socket(ip, porta);
            BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter saida      = new PrintWriter(socket.getOutputStream(), true);
            // scanner para o teclado já foi criado acima
        ) {
            System.out.println("Conectado!\n");

            // ── Mensagem de boas-vindas do servidor ───────────────────────────
            System.out.println(entrada.readLine());
            System.out.println(entrada.readLine());
            System.out.println();

            // ── Thread para receber respostas do servidor de forma assíncrona ──
            Thread receptor = new Thread(() -> {
                try {
                    String resposta;
                    while ((resposta = entrada.readLine()) != null) {
                        System.out.println("Servidor > " + resposta);
                        // Quando o servidor confirma /exit, encerramos
                        if (resposta.contains("Conexão encerrada")) {
                            System.out.println("\nDesconectado do servidor.");
                            System.exit(0);
                        }
                    }
                } catch (IOException e) {
                    // Conexão encerrada pelo servidor
                    System.out.println("\n[INFO] Servidor encerrou a conexão.");
                    System.exit(0);
                }
            });
            receptor.setDaemon(true);
            receptor.start();

            // ── Loop principal: lê comandos do teclado e envia ao servidor ────
            System.out.println("Digite um comando (ou /exit para sair):");
            while (true) {
                System.out.print("> ");
                String comando = scanner.nextLine();

                if (comando == null || comando.trim().isEmpty()) continue;

                saida.println(comando.trim());

                // Se o usuário digitou /exit, aguarda o servidor responder
                // e a thread receptora encerra o processo
                if (comando.trim().equalsIgnoreCase("/exit")) {
                    Thread.sleep(800); // espera a resposta chegar
                    break;
                }
            }

        } catch (ConnectException e) {
            System.err.println("[ERRO] Não foi possível conectar ao servidor em "
                    + ip + ":" + porta + ". Verifique se o servidor está rodando.");
        } catch (IOException e) {
            System.err.println("[ERRO] Problema de comunicação: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            scanner.close();
        }
    }
}
