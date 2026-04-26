# Banco Distribuído — Sistema de Operações Financeiras via Sockets TCP

Projeto da disciplina de Redes de Computadores. Sistema cliente-servidor implementado em Java utilizando sockets TCP, executado em máquinas virtuais distintas.

---

## Tecnologias utilizadas

- **Linguagem:** Java (OpenJDK 25)
- **Protocolo:** TCP (Sockets)
- **Virtualização:** VirtualBox com Ubuntu Server 26.04 LTS
- **Comunicação entre VMs:** Rede Interna (192.168.1.x)

---

## Estrutura do projeto

```
banco-sockets/
├── servidor/
│   └── Servidor.java
├── cliente/
│   └── Cliente.java
└── README.md
```

---

## Infraestrutura das VMs

| VM | IP (Rede Interna) | Função |
|---|---|---|
| Servidor | 192.168.1.1 | Gerencia as contas bancárias |
| Cliente | 192.168.1.2 | Interface do usuário |

Cada VM possui dois adaptadores de rede:
- **Adaptador 1 (enp0s3):** Rede Interna — comunicação entre as VMs
- **Adaptador 2 (enp0s8):** Host-Only — transferência de arquivos via SCP

---

## Como compilar

### Na VM do Servidor (192.168.1.1)
```bash
javac Servidor.java
```

### Na VM do Cliente (192.168.1.2)
```bash
javac Cliente.java
```

---

## Como executar

### 1. Configurar os IPs nas VMs (necessário após reiniciar)

**Na VM do Servidor:**
```bash
sudo ip addr add 192.168.1.1/24 dev enp0s3
```

**Na VM do Cliente:**
```bash
sudo ip addr add 192.168.1.2/24 dev enp0s3
```

### 2. Iniciar o Servidor

Na VM do Servidor:
```bash
java Servidor
```

Quando solicitado, informe:
```
IP: 192.168.1.1
Porta: 12345
```

### 3. Iniciar o Cliente

Na VM do Cliente:
```bash
java Cliente
```

Quando solicitado, informe:
```
IP do servidor: 192.168.1.1
Porta: 12345
```

---

## Protocolo de comandos

| Comando | Formato | Descrição |
|---|---|---|
| Criar conta | `CRIAR <id>` | Cria uma nova conta com saldo zero |
| Ver saldo | `SALDO <id>` | Retorna o saldo atual da conta |
| Depositar | `DEPOSITAR <id> <valor>` | Adiciona valor ao saldo da conta |
| Sacar | `SACAR <id> <valor>` | Remove valor se saldo for suficiente |
| Encerrar | `/exit` | Encerra a conexão com o servidor |

---

## Exemplos de uso

```
> CRIAR 101
Servidor > [SUCESSO] Conta 101 criada com saldo R$ 0,00

> DEPOSITAR 101 500.00
Servidor > [SUCESSO] Depósito de R$ 500,00 realizado. Saldo atual: R$ 500,00

> SALDO 101
Servidor > [SUCESSO] Saldo da conta 101: R$ 500,00

> SACAR 101 200.00
Servidor > [SUCESSO] Saque de R$ 200,00 realizado. Saldo atual: R$ 300,00

> SACAR 101 1000.00
Servidor > [FALHA] Saldo insuficiente. Saldo atual: R$ 300,00

> SALDO 999
Servidor > [FALHA] Conta 999 não encontrada.

> /exit
Servidor > [SUCESSO] Conexão encerrada. Até mais!
```

---

## Opcionais implementados

### Persistência de dados
- Ao encerrar o servidor (`Ctrl+C`), o estado de todas as contas é salvo automaticamente na pasta `contas/`, um arquivo `.txt` por conta.
- Ao iniciar, o servidor carrega automaticamente os arquivos existentes e restaura o estado das contas.

### Log de transações
- Todas as operações realizadas são registradas no arquivo `transacoes.log`.
- Cada linha contém: data/hora, ID da conta, operação, valor e resultado.

Exemplo de log:
```
2026-04-26 00:30:12 | Conta: 101 | Operação: CRIAR      | Valor: R$       0,00 | SUCESSO
2026-04-26 00:30:25 | Conta: 101 | Operação: DEPOSITAR  | Valor: R$     500,00 | SUCESSO — saldo: 500,00
2026-04-26 00:30:38 | Conta: 101 | Operação: SACAR      | Valor: R$     200,00 | SUCESSO — saldo: 300,00
```

---

## Como transferir os arquivos para as VMs (via SCP)

Com o SSH ativo nas VMs e o adaptador Host-Only configurado:

```bash
# Transferir Servidor.java para a VM Servidor
scp -o StrictHostKeyChecking=no servidor\Servidor.java servidor@192.168.56.101:~/

# Transferir Cliente.java para a VM Cliente
scp -o StrictHostKeyChecking=no cliente\Cliente.java cliente@192.168.56.102:~/
```

---

## Observações

- Os IPs da rede interna (`192.168.1.x`) são temporários e precisam ser reconfigurados após reiniciar as VMs.
- O servidor suporta múltiplos clientes simultâneos (cada cliente é tratado em uma thread separada).
- O `ConcurrentHashMap` garante thread-safety no acesso às contas.
