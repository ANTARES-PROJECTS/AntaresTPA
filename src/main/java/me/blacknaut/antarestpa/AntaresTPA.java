package me.blacknaut.antarestpa;

import net.md_5.bungee.api.chat.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class AntaresTPA extends JavaPlugin implements CommandExecutor {

    private Map<UUID, Long> lastTpaTime = new HashMap<>();
    private Map<UUID, UUID> pendingRequests = new HashMap<>();
    private static final long TPA_COOLDOWN = 30 * 1000;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.getCommand("tpa").setExecutor(this);
        this.getCommand("tpaccept").setExecutor(this);
        this.getCommand("tpdeny").setExecutor(this);
        this.getCommand("tpcancel").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Este comando só pode ser executado por jogadores.");
            return false;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("antares.tps")) {
            player.sendMessage(this.getConfig().getString("plugin.permissao").replace("&", "§"));
            return false;
        }

        long currentTime = System.currentTimeMillis();
        if (cmd.getName().equalsIgnoreCase("tpa")) {
            if (lastTpaTime.containsKey(player.getUniqueId())) {
                long lastTpa = lastTpaTime.get(player.getUniqueId());
                long cooldownTime = lastTpa + TPA_COOLDOWN;
                if (currentTime < cooldownTime) {
                    long timeLeft = (cooldownTime - currentTime) / 1000;
                    player.sendMessage(this.getConfig().getString("plugin.tempo").replace("&", "§").replace("{tempo}", Long.toString(timeLeft)));
                    return false;
                }
            }
        }

        switch (cmd.getName().toLowerCase()) {
            case "tpa":
                if (args.length != 1) {
                    player.sendMessage(this.getConfig().getString("tpa.comando_errado").replace("&", "§"));
                    return false;
                }

                Player target = this.getServer().getPlayer(args[0]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage(this.getConfig().getString("tpa.jogador_off").replace("&", "§"));
                    return false;
                }

                if (target.getName().equals(player.getName())) {
                    player.sendMessage(this.getConfig().getString("tpa.mandar_pedido_para_si").replace("&", "§"));
                    return false;
                }

                if (this.pendingRequests.containsKey(target.getUniqueId())) {
                    player.sendMessage(this.getConfig().getString("tpa.pedido_pendente").replace("&", "§"));
                    return false;
                }

                UUID previousRequest = this.pendingRequests.get(target.getUniqueId());
                if (previousRequest != null) {
                    Player previousRequester = Bukkit.getPlayer(previousRequest);
                    if (previousRequester != null) {
                        previousRequester.sendMessage(this.getConfig().getString("tpa.pedido_cancelado").replace("&", "§").replace("{nick}", target.getName()));
                    }
                    this.pendingRequests.remove(target.getUniqueId());
                }

                this.pendingRequests.put(target.getUniqueId(), player.getUniqueId());
                player.sendMessage(this.getConfig().getString("tpa.pedido_enviado").replace("&", "§").replace("{nick}", target.getName()));

                String targetMsg = this.getConfig().getString("tpa.recebendo_tpa_2").replace("&", "§");
                if (targetMsg.contains("{aceitar}") && targetMsg.contains("{recusar}")) {
                    String acceptButtonText = this.getConfig().getString("tpa.accept_button").replace("&", "§");
                    String denyButtonText = this.getConfig().getString("tpa.deny_button").replace("&", "§");

                    TextComponent acceptComponent = new TextComponent(acceptButtonText);
                    acceptComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept " + player.getName()));

                    TextComponent denyComponent = new TextComponent(denyButtonText);
                    denyComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny " + player.getName()));

                    int acceptIndex = targetMsg.indexOf("{aceitar}");
                    int denyIndex = targetMsg.indexOf("{recusar}");

                    if (acceptIndex != -1 && denyIndex != -1) {
                        List<BaseComponent> components = new ArrayList<>();
                        components.add(new TextComponent(targetMsg.substring(0, acceptIndex)));
                        components.add(acceptComponent);
                        components.add(new TextComponent(targetMsg.substring(acceptIndex + "{aceitar}".length(), denyIndex)));
                        components.add(denyComponent);
                        components.add(new TextComponent(targetMsg.substring(denyIndex + "{recusar}".length())));

                        target.sendMessage(this.getConfig().getString("tpa.recebendo_tpa_1").replace("&", "§").replace("{nick}", player.getName()));
                        target.spigot().sendMessage(components.toArray(new BaseComponent[0]));
                    } else {
                        target.sendMessage("§cError!");
                    }
                } else {
                    target.sendMessage("§cError! AntaresTPA/config.yml não esta configurado direito");
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (pendingRequests.containsKey(target.getUniqueId())) {
                            pendingRequests.remove(target.getUniqueId());
                            player.sendMessage(getConfig().getString("tpa.pedido_expirado_player").replace("&", "§").replace("{nick}", target.getName()));
                            target.sendMessage(getConfig().getString("tpa.pedido_expirado_target").replace("&", "§").replace("{nick}", player.getName()));
                        }
                    }
                }.runTaskLater(this, 600);

                lastTpaTime.put(player.getUniqueId(), currentTime);
                return true;

            case "tpaccept":
                if (!this.pendingRequests.containsKey(player.getUniqueId())) {
                    player.sendMessage(this.getConfig().getString("tpaccept.sem_pedido").replace("&", "§"));
                    return false;
                }

                Player requester = this.getServer().getPlayer(this.pendingRequests.get(player.getUniqueId()));
                if (requester == null || !requester.isOnline()) {
                    player.sendMessage(this.getConfig().getString("tpaccept.jogador_off").replace("&", "§"));
                    return false;
                }

                requester.teleport(player.getLocation());
                requester.sendMessage(this.getConfig().getString("tpaccept.teletransportado").replace("&", "§").replace("{nick}", player.getName()));
                player.sendMessage(this.getConfig().getString("tpaccept.solicitacao_aceita").replace("&", "§").replace("{nick}", requester.getName()));
                this.pendingRequests.remove(player.getUniqueId());
                this.lastTpaTime.remove(requester.getUniqueId());
                return true;

            case "tpdeny":
                if (!this.pendingRequests.containsKey(player.getUniqueId())) {
                    player.sendMessage(this.getConfig().getString("tpdeny.sem_pedido").replace("&", "§"));
                    return false;
                }

                Player requesterDeny = this.getServer().getPlayer(this.pendingRequests.get(player.getUniqueId()));
                if (requesterDeny == null || !requesterDeny.isOnline()) {
                    player.sendMessage(this.getConfig().getString("tpdeny.jogador_off").replace("&", "§"));
                    return false;
                }

                player.sendMessage(this.getConfig().getString("tpdeny.pedido_negado").replace("&", "§").replace("{nick}", requesterDeny.getName()));
                requesterDeny.sendMessage(this.getConfig().getString("tpdeny.solicitacao_negado").replace("&", "§").replace("{nick}", player.getName()));
                this.pendingRequests.remove(player.getUniqueId());
                return true;

            case "tpcancel":
                if (args.length != 1) {
                    player.sendMessage(this.getConfig().getString("tpcancel.comando_errado").replace("&", "§"));
                    return false;
                }
                Player cancelPlayer = this.getServer().getPlayer(args[0]);
                if (cancelPlayer == null || !cancelPlayer.isOnline()) {
                    player.sendMessage(this.getConfig().getString("tpcancel.jogador_off").replace("&", "§"));
                    return false;
                }
                if (!this.pendingRequests.containsKey(cancelPlayer.getUniqueId())) {
                    player.sendMessage(this.getConfig().getString("tpcancel.sem_pedidos").replace("&", "§"));
                    return false;
                }
                this.pendingRequests.remove(cancelPlayer.getUniqueId());
                player.sendMessage(this.getConfig().getString("tpcancel.pedido_cancelado").replace("&", "§").replace("{nick}", cancelPlayer.getName()));
                cancelPlayer.sendMessage(this.getConfig().getString("tpcancel.solicitacao_canelado").replace("&", "§").replace("{nick}", player.getName()));
                return true;

            default:
                return false;
        }
    }
}