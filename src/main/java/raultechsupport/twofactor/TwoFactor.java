package raultechsupport.twofactor;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.UUID;

public final class TwoFactor extends JavaPlugin implements Listener {

    private ArrayList<UUID> authlocked;

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(this, this);
        authlocked = new ArrayList<UUID>();

        this.getConfig().options().copyDefaults(true);
        this.saveConfig();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (this.getConfig().contains("authcodes." + player.getUniqueId())) {
            authlocked.add(player.getUniqueId());
            player.sendMessage("§7Welcome " + player.getName() + "! Please run \"/2FA <2FA code>\" to gain access to the server!");
        }
    }

    private boolean playerInputCode(Player player, int code) {
        String secretKey = this.getConfig().getString("authcodes." + player.getUniqueId());

        GoogleAuthenticator gAuth = new GoogleAuthenticator();
        boolean codeIsValid = gAuth.authorize(secretKey, code);

        if (codeIsValid) {
            authlocked.remove(player.getUniqueId());
            return true;
        }

        return false;
    }

    private boolean playerInputVerifyCode(Player player, int code) {
        String secretKey = this.getConfig().getString("authcodesUnverified." + player.getUniqueId());

        GoogleAuthenticator gAuth = new GoogleAuthenticator();
        boolean codeIsValid = gAuth.authorize(secretKey, code);

        if (codeIsValid) {
            authlocked.remove(player.getUniqueId());
            return codeIsValid;
        }

        return codeIsValid;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof  Player)) { return true; }
        Player player = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("2fa")) {
            if (authlocked.contains(player.getUniqueId())) {
                try {
                    Integer code = Integer.parseInt(args[0]);
                    if (playerInputCode(player, code)) {
                        authlocked.remove(player.getUniqueId());
                        player.sendMessage("§22FA Code Verified!");
                    } else {
                        player.sendMessage("§4Invalid 2FA Code! Please try again!");
                    }
                } catch (Exception e) {
                    player.sendMessage("§4Invalid 2FA Code! Please try again!");
                }
            }
            else {
                player.sendMessage("§4You do not need to log in with a 2FA code, or you have already logged in!");
            }
        }
        else if (cmd.getName().equalsIgnoreCase("register")) {
            if (!this.getConfig().contains("authcodes." + player.getUniqueId())) {
                GoogleAuthenticator gAuth = new GoogleAuthenticator();
                GoogleAuthenticatorKey key = gAuth.createCredentials();

                String QRCodeURL = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=otpauth://totp/MCTwoFactor:"+ player.getName() + "?secret=" + key.getKey();

                if (getServer().getPluginManager().getPlugin("ImageOnMap") != null) {
                    Bukkit.dispatchCommand(player, "tomap " + QRCodeURL + " resize 1 1");
                    TextComponent message = new TextComponent("§7You have been given a map with your QR Code!");
                    message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, QRCodeURL));
                    message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§7If you would prefer to enter your code manually, please enter the following code: " + key.getKey())));
                    player.spigot().sendMessage(message);
                } else {
                    getLogger().warning("ImageOnMap not found! Falling back to web based QR code.");
                    TextComponent message = new TextComponent("§7Click to open QR code!");
                    message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, QRCodeURL));
                    message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§7If you would prefer to enter your code manually, please enter the following code: " + key.getKey())));
                    player.spigot().sendMessage(message);
                }

                this.getConfig().set("authcodesUnverified." + player.getUniqueId(), key.getKey());
                this.saveConfig();
                player.sendMessage("§7Please enter the 2FA code generated to confirm your 2FA: /verify <2FA code>");

            } else {
                player.sendMessage("§4You are already registered for 2FA!");
            }

        }
        else if (cmd.getName().equalsIgnoreCase("verify")) {
            try {
                Integer code = Integer.parseInt(args[0]);
                if (playerInputVerifyCode(player, code)) {

                    this.getConfig().set("authcodes." + player.getUniqueId(), this.getConfig().getString("authcodesUnverified." + player.getUniqueId()));
                    this.getConfig().set("authcodesUnverified." + player.getUniqueId(), null);
                    this.saveConfig();

                    player.sendMessage("§22FA Code Verified! Your 2FA has been set up successfully!");
                } else {
                    player.sendMessage("§4Invalid 2FA Code! We could not set up your 2FA, or you have already set up 2FA!");
                }
            } catch (Exception e) {
                player.sendMessage("§4Invalid 2FA Code! We could not set up your 2FA, or you have already set up 2FA!");
            }
        }

        return true;
    }

    //Event handlers to cancel player events that have not completed 2FA.
    @EventHandler
    public void Chat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (authlocked.contains(player.getUniqueId())) {
            player.sendMessage("§4You must login with your 2FA code before sending a message: /2fa <2FA code>");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void Move(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (authlocked.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void BlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (authlocked.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void BlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (authlocked.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void PlayerDropItemEvent(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (authlocked.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void EntityDamageByEntityEvent(EntityDamageByEntityEvent event) {
        Entity player = event.getEntity();
        if (authlocked.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void EntityDamageByBlockEvent(EntityDamageByBlockEvent event) {
        Entity player = event.getEntity();
        if (authlocked.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void PlayerInteractAtEntityEvent(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        if (authlocked.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void preProcessCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (authlocked.contains(player.getUniqueId())) {
            if(!event.getMessage().toLowerCase().startsWith("/2fa")){
                event.setCancelled(true);
                player.sendMessage("§4You must login with your 2FA code before sending a command: /2fa <2FA code>");
            }
        }
    }
}