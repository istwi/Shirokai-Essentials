package dev.shirokai.essentials.config;
import java.util.*;
public final class MoreMessages {
    public static Map<String,String> defaults() {
        Map<String,String> m = new LinkedHashMap<>();
        m.put("chunk_loading", "Preparando o destino. Permaneça parado.");
        m.put("player_missing", "Jogador não encontrado. Use um nome conhecido ou UUID.");
        m.put("denied", "Você não tem permissão para esta ação.");
        m.put("invalid", "Valor inválido: {value}.");
        m.put("state", "{feature}: {state}.");
        m.put("private_sent", "Você → {player}: {message}"); m.put("private_received", "{player} → você: {message}");
        m.put("private_blocked", "Não foi possível entregar a mensagem.");
        m.put("spy", "[Espião] {sender} → {player}: {message}");
        m.put("reply_missing", "Não há conversa disponível para responder.");
        m.put("ignored", "Preferência de mensagens atualizada para {player}.");
        m.put("mail_sent", "Correio enviado para {player}."); m.put("mail_full", "A caixa de correio está cheia.");
        m.put("mail_wait", "Aguarde {time}s para enviar outro correio.");
        m.put("mail_notice", "Você tem {count} mensagens. Use /mail read.");
        m.put("mail_line", "[{id}] {player}: {message}"); m.put("mail_clear", "Caixa de correio limpa.");
        m.put("afk", "{player}: {state}."); m.put("nick", "Apelido atualizado: {player}.");
        m.put("realname", "{nickname} é {player}."); m.put("players", "Jogadores ({count}): {players}");
        m.put("seen", "{player} | UUID: {uuid} | último acesso: {time}.");
        m.put("whois", "{player} | UUID: {uuid} | mundo: {world} | posição: {position}");
        m.put("broadcast", "[Aviso] {message}"); m.put("emote", "* {player} {message}");
        m.put("public_chat", "{prefix}{player}: {message}");
        m.put("join", "{player} entrou no servidor."); m.put("quit", "{player} saiu do servidor.");
        m.put("first_join", "Bem-vindo ao Shirokai, {player}!"); m.put("death", "{message}");
        m.put("text", "{message}"); m.put("utility", "{feature} atualizado para {player}.");
        m.put("ping", "Latência: {ping}ms."); m.put("memory", "Memória usada: {used} MiB / máximo: {max} MiB. Jogadores: {count}.");
        m.put("protected", "A hierarquia protege este jogador."); m.put("muted", "Você está silenciado: {reason}.");
        m.put("punishment", "{action}: {player}. Motivo: {reason}.");
        m.put("ban_reason", "Você está banido: {reason}."); m.put("kick_reason", "Você foi desconectado: {reason}.");
        m.put("warn_notice", "Você recebeu um aviso: {reason}."); m.put("warnings", "Avisos de {player}: {warnings}");
        m.put("jailed", "Você está preso. Teleportes e interações estão bloqueados."); m.put("released", "Você foi liberado da prisão.");
        m.put("frozen", "Você está congelado.");
        m.put("inventory_title", "Inventário: {player}");m.put("enderchest_title", "Ender Chest");m.put("workbench_title", "Bancada");m.put("anvil_title", "Bigorna");m.put("kit_title", "Kit: {name}");
        m.put("kit_delivery_error", "Entrega {id} interrompida. Não repita a entrega sem conferir o recibo com a administração.");
        m.put("kit_missing", "Kit não encontrado ou sem permissão."); m.put("kit_empty", "Não há itens para criar o kit.");
        m.put("kit_wait", "Aguarde {time}s para receber este kit."); m.put("kit_space", "Seu inventário não tem espaço para o kit inteiro.");
        m.put("kit_received", "Kit {name} recebido."); m.put("kit_saved", "Kit {name} salvo.");
        m.put("kit_pending", "Entrega pendente de conferência: {id}. Contate a administração.");
        m.put("kit_recovered", "Pendência {id} encerrada sem reenviar itens.");
        m.put("import_report", "Migração em modo {mode}. Relatório: {path}.");
        m.put("import_blocked", "Importação bloqueada: {reason}.");
        return m;
    }
}
