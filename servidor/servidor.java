import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class Servidor {

    // ── Armazenamento de contas (thread-safe) ──────────────────────────────────
    private static final Map<Integer, Double> contas = new ConcurrentHashMap<>();

    // ── Arquivo de log de transações ───────────────────────────────────────────
    private static final String LOG_FILE   = "transacoes.log";
    private static final String CONTAS_DIR = "contas";

    // ── Formatação de datas para o log ─────────────────────────────────────────
    private static final SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // ── Shutdown hook para persistência ───────────────────────────────────────
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SERVIDOR] Encerrando — salvando contas em disco...");
            salvarContas();
            System.out.println("[SERVIDOR] Contas salvas com sucesso. Até logo!");
        }));
    }

   //main
    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);

        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║        BANCO DISTRIBUÍDO — SERVIDOR      ║");
        System.out.println("╚══════════════════════════════════════════╝");

        System.out.print("Informe o IP para escuta (ex: 192.168.1.1): ");
        String ip = scanner.nextLine().trim();

        System.out.print("Informe a porta (ex: 12345): ");
        int porta = Integer.parseInt(scanner.nextLine().trim());

        // Carrega contas persistidas anteriormente
        carregarContas();

        InetAddress endereco = InetAddress.getByName(ip);
        ServerSocket serverSocket = new ServerSocket(porta, 50, endereco);

        System.out.println("[SERVIDOR] Escutando em " + ip + ":" + porta);
        System.out.println("[SERVIDOR] Aguardando conexões... (Ctrl+C para encerrar)\n");

        // Loop principal: aceita um cliente por vez em thread separada
        while (true) {
            Socket clienteSocket = serverSocket.accept();
            System.out.println("[SERVIDOR] Novo cliente conectado: "
                    + clienteSocket.getInetAddress().getHostAddress());
            Thread t = new Thread(new ManipuladorCliente(clienteSocket));
            t.setDaemon(true);
            t.start();
        }
    }

    
    // PERSISTÊNCIA — salvar contas
    private static synchronized void salvarContas() {
        File dir = new File(CONTAS_DIR);
        if (!dir.exists()) dir.mkdirs();

        for (Map.Entry<Integer, Double> entry : contas.entrySet()) {
            File arquivo = new File(dir, "conta_" + entry.getKey() + ".txt");
            try (PrintWriter pw = new PrintWriter(new FileWriter(arquivo))) {
                pw.println("id=" + entry.getKey());
                pw.printf("saldo=%.2f%n", entry.getValue());
            } catch (IOException e) {
                System.err.println("[ERRO] Falha ao salvar conta " + entry.getKey());
            }
        }
    }

    
    // PERSISTÊNCIA — carregar contas
    private static void carregarContas() {
        File dir = new File(CONTAS_DIR);
        if (!dir.exists()) {
            System.out.println("[SERVIDOR] Nenhuma conta anterior encontrada.");
            return;
        }

        File[] arquivos = dir.listFiles((d, name) ->
                name.startsWith("conta_") && name.endsWith(".txt"));

        if (arquivos == null || arquivos.length == 0) {
            System.out.println("[SERVIDOR] Nenhuma conta anterior encontrada.");
            return;
        }

        for (File arquivo : arquivos) {
            try (BufferedReader br = new BufferedReader(new FileReader(arquivo))) {
                int id     = -1;
                double saldo = 0.0;
                String linha;
                while ((linha = br.readLine()) != null) {
                    if (linha.startsWith("id="))    id    = Integer.parseInt(linha.substring(3).trim());
                    if (linha.startsWith("saldo=")) saldo = Double.parseDouble(linha.substring(6).trim().replace(',', '.'));
                }
                if (id != -1) {
                    contas.put(id, saldo);
                    System.out.printf("[SERVIDOR] Conta %d restaurada. Saldo: R$ %.2f%n", id, saldo);
                }
            } catch (IOException | NumberFormatException e) {
                System.err.println("[ERRO] Falha ao carregar " + arquivo.getName());
            }
        }
        System.out.println("[SERVIDOR] " + contas.size() + " conta(s) carregada(s).\n");
    }

    
    // LOG DE TRANSAÇÕES
    static synchronized void registrarLog(int idConta, String operacao,
                                          double valor, String resultado) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             PrintWriter pw = new PrintWriter(fw)) {

            String timestamp = sdf.format(new Date());
            pw.printf("%s | Conta: %d | Operação: %-10s | Valor: R$ %10.2f | %s%n",
                    timestamp, idConta, operacao, valor, resultado);

        } catch (IOException e) {
            System.err.println("[ERRO] Falha ao escrever no log: " + e.getMessage());
        }
    }

    
    // MANIPULADOR DE CLIENTE (Runnable — executa em thread separada)
    static class ManipuladorCliente implements Runnable {

        private final Socket socket;

        ManipuladorCliente(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            String clienteAddr = socket.getInetAddress().getHostAddress();

            try (
                BufferedReader entrada =
                        new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter saida =
                        new PrintWriter(socket.getOutputStream(), true)
            ) {
                saida.println("Bem-vindo ao Banco Distribuído! Digite um comando.");
                saida.println("Comandos: CRIAR <id> | SALDO <id> | DEPOSITAR <id> <valor> | SACAR <id> <valor> | /exit");

                String linha;
                while ((linha = entrada.readLine()) != null) {
                    linha = linha.trim();
                    if (linha.isEmpty()) continue;

                    System.out.println("[" + clienteAddr + "] Recebido: " + linha);

                    String resposta = processarComando(linha);
                    saida.println(resposta);
                    System.out.println("[" + clienteAddr + "] Enviado: " + resposta);

                    if (linha.equalsIgnoreCase("/exit")) break;
                }

            } catch (IOException e) {
                System.out.println("[SERVIDOR] Conexão encerrada abruptamente: " + clienteAddr);
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
                System.out.println("[SERVIDOR] Cliente desconectado: " + clienteAddr);
            }
        }

        // ── Dispatcher de comandos ─────────────────────────────────────────────
        private String processarComando(String linha) {
            String[] partes = linha.split("\\s+");
            String comando  = partes[0].toUpperCase();

            switch (comando) {
                case "CRIAR":      return cmdCriar(partes);
                case "SALDO":      return cmdSaldo(partes);
                case "DEPOSITAR":  return cmdDepositar(partes);
                case "SACAR":      return cmdSacar(partes);
                case "/EXIT":      return "[SUCESSO] Conexão encerrada. Até mais!";
                default:
                    return "[FALHA] Comando desconhecido: " + partes[0]
                         + ". Use CRIAR, SALDO, DEPOSITAR, SACAR ou /exit.";
            }
        }

        // ── CRIAR <id> ────────────────────────────────────────────────────────
        private String cmdCriar(String[] partes) {
            if (partes.length < 2)
                return "[FALHA] Uso correto: CRIAR <id>";

            int id;
            try { id = Integer.parseInt(partes[1]); }
            catch (NumberFormatException e) { return "[FALHA] ID inválido: " + partes[1]; }

            if (contas.containsKey(id))
                return "[FALHA] Conta " + id + " já existe.";

            contas.put(id, 0.0);
            registrarLog(id, "CRIAR", 0.0, "SUCESSO");
            return String.format("[SUCESSO] Conta %d criada com saldo R$ 0,00", id);
        }

        // ── SALDO <id> ────────────────────────────────────────────────────────
        private String cmdSaldo(String[] partes) {
            if (partes.length < 2)
                return "[FALHA] Uso correto: SALDO <id>";

            int id;
            try { id = Integer.parseInt(partes[1]); }
            catch (NumberFormatException e) { return "[FALHA] ID inválido: " + partes[1]; }

            if (!contas.containsKey(id))
                return "[FALHA] Conta " + id + " não encontrada.";

            double saldo = contas.get(id);
            registrarLog(id, "SALDO", saldo, "SUCESSO");
            return String.format("[SUCESSO] Saldo da conta %d: R$ %.2f", id, saldo);
        }

        // ── DEPOSITAR <id> <valor> ────────────────────────────────────────────
        private String cmdDepositar(String[] partes) {
            if (partes.length < 3)
                return "[FALHA] Uso correto: DEPOSITAR <id> <valor>";

            int id;
            double valor;
            try { id    = Integer.parseInt(partes[1]); }
            catch (NumberFormatException e) { return "[FALHA] ID inválido: " + partes[1]; }
            try { valor = Double.parseDouble(partes[2].replace(',', '.')); }
            catch (NumberFormatException e) { return "[FALHA] Valor inválido: " + partes[2]; }

            if (!contas.containsKey(id))
                return "[FALHA] Conta " + id + " não encontrada.";
            if (valor <= 0)
                return "[FALHA] O valor do depósito deve ser positivo.";

            // Atualização atômica
            contas.compute(id, (k, saldoAtual) -> saldoAtual + valor);
            double novoSaldo = contas.get(id);

            registrarLog(id, "DEPOSITAR", valor, "SUCESSO — saldo: " + String.format("%.2f", novoSaldo));
            return String.format("[SUCESSO] Depósito de R$ %.2f realizado. Saldo atual: R$ %.2f",
                    valor, novoSaldo);
        }

        // ── SACAR <id> <valor> ────────────────────────────────────────────────
        private String cmdSacar(String[] partes) {
            if (partes.length < 3)
                return "[FALHA] Uso correto: SACAR <id> <valor>";

            int id;
            double valor;
            try { id    = Integer.parseInt(partes[1]); }
            catch (NumberFormatException e) { return "[FALHA] ID inválido: " + partes[1]; }
            try { valor = Double.parseDouble(partes[2].replace(',', '.')); }
            catch (NumberFormatException e) { return "[FALHA] Valor inválido: " + partes[2]; }

            if (!contas.containsKey(id))
                return "[FALHA] Conta " + id + " não encontrada.";
            if (valor <= 0)
                return "[FALHA] O valor do saque deve ser positivo.";

            // synchronized garante atomicidade na verificação + subtração
            synchronized (contas) {
                double saldoAtual = contas.get(id);
                if (saldoAtual < valor) {
                    registrarLog(id, "SACAR", valor, "FALHA — saldo insuficiente");
                    return String.format("[FALHA] Saldo insuficiente. Saldo atual: R$ %.2f", saldoAtual);
                }
                contas.put(id, saldoAtual - valor);
                double novoSaldo = contas.get(id);
                registrarLog(id, "SACAR", valor, "SUCESSO — saldo: " + String.format("%.2f", novoSaldo));
                return String.format("[SUCESSO] Saque de R$ %.2f realizado. Saldo atual: R$ %.2f",
                        valor, novoSaldo);
            }
        }
    }
}
