// Copyright (C) 2021 DreiMu
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published
// by the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

package de.dreimu.minecraft.velocity.serverstarter;

import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.util.Favicon;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import com.google.common.io.ByteArrayDataOutput;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.util.Favicon;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "serverstarter",
        name = "Server Starter",
        version = "1.0-SNAPSHOT",
        description = "This is a plugin for Velocity on a Pterodactyl Server to let you start servers if somebody tries to join an empty server"
)
public class Main {

    private final ProxyServer server;
    private final Logger logger;
    private final Toml toml;
    private final Toml servers;
    private final Toml motds;
    private final Toml api;
    private final Toml messages;
    private final Path folder;

    @Inject
    public Main(ProxyServer server, Logger logger, @DataDirectory final Path folder) {
        this.server = server;
        this.logger = logger;
        this.folder = folder;

        this.toml = loadConfig();
        this.servers = this.toml.getTable("servers");
        this.motds = this.toml.getTable("motds");
        this.api = this.toml.getTable("api");
        this.messages = this.toml.getTable("messages");

        Pterodactyl.toml = this.toml;

        // this.server.getChannelRegistrar().register(new LuckPermsChannelIdentifier());

        this.server.sendMessage(Component.text("Hi"));

        logger.info("Hello there! (Server running!)");
    }

    private String getImage(String name) {
        File folder = this.folder.toFile();
        File image = new File(folder, "image/"+name+".png");
        if(!image.getParentFile().exists()) {
            image.getParentFile().mkdirs();
            return "";
        }

        if(!image.exists()) {
            return "";
        }

        try {
            FileInputStream fileInputStreamReader = new FileInputStream(image);
            byte[] bytes = new byte[(int)image.length()];
            fileInputStreamReader.read(bytes);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            return "";
        }
    }

    private Toml loadConfig() {
        File folder = this.folder.toFile();
        File file = new File(folder, "config.toml");
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        if (!file.exists()) {
            try (InputStream input = getClass().getResourceAsStream("/" + file.getName())) {
                if (input != null) {
                    Files.copy(input, file.toPath());
                } else {
                    file.createNewFile();
                }
            } catch (IOException exception) {
                return null;
            }
        }

        return new Toml().read(file);
    }

    @Subscribe
    public void onPing(ProxyPingEvent event) {

        final String serverAddress = event.getConnection().getVirtualHost().get().getHostString();
        final String serverGroup = this.servers.getString("\""+serverAddress+"\"");

        final Component description;
        final Favicon favicon;
        final ServerPing.Players players;

        if(serverGroup != null) {
            if(this.motds.getTable(serverGroup).getString("motd") == "passthrough") {
                description = event.getPing().getDescriptionComponent();
            } else {
                description = Component.text(this.motds.getTable(serverGroup).getString("motd"));
            }
            if(this.motds.getTable(serverGroup).getString("favicon") == "passthrough") {
                if(event.getPing().getFavicon().isPresent()) {
                    favicon = event.getPing().getFavicon().get();
                } else {
                    favicon = null;
                }
            } else {
                favicon = new Favicon("data:image/png;base64,"+getImage(this.motds.getTable(serverGroup).getString("favicon")));
            }
        } else {
            description = event.getPing().getDescriptionComponent();

            if(event.getPing().getFavicon().isPresent()) {
                favicon = event.getPing().getFavicon().get();
            } else {
                favicon = null;
            }

        }

        if(event.getPing().getPlayers().isPresent()) {
            players = event.getPing().getPlayers().get();
        } else {
            players = null;
        }

        final ServerPing ping = new ServerPing(
                event.getPing().getVersion(),
                players,
                description,
                favicon
        );

        event.setPing(ping);
    }

    @Subscribe
    public void onServerPreConnectEvent(ServerPreConnectEvent event) {
        if(event.getResult().getServer().isPresent()) {
            String name = event.getResult().getServer().get().getServerInfo().getName();
            if(this.motds.contains(name)) {
                try {

                    boolean running = Pterodactyl.getServerOnline(this.motds.getTable(name).getString("id"));

                    if(running) {
                        event.setResult(event.getResult());
                        return;
                    }

                    if(!event.getPlayer().hasPermission("dreimu.startserver."+name)) {
                        event.getPlayer().sendMessage(Component.text(this.messages.getString("offline_noperms")));
                        event.setResult(ServerPreConnectEvent.ServerResult.denied());
                        return;
                    }

                    if(!this.server.getServer("lobby").isPresent()) {event.setResult(ServerPreConnectEvent.ServerResult.denied()); return;}

                    Pterodactyl.startServer(this.motds.getTable(name).getString("id"));

                    Component message = Component.text(this.messages.getString("redirect_timer"));

                    event.getPlayer().sendMessage(message);
                    TimeUnit.SECONDS.sleep(30);
                    RegisteredServer server = this.server.getServer(name).get();
                    event.setResult(ServerPreConnectEvent.ServerResult.allowed(server));
                    return;

                } catch (Exception e) {
                    e.printStackTrace();
                }
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                return;
            }
        }
        event.setResult(event.getResult());
    }

    @Subscribe
    public void onServerChooseEvent(PlayerChooseInitialServerEvent event) {
        if(!event.getInitialServer().isPresent()) {
            return;
        }

        String name = event.getInitialServer().get().getServerInfo().getName();

        boolean serverRunning = Pterodactyl.getServerOnline(this.motds.getTable(name).getString("id"));

        if(!serverRunning) {

            if(!event.getPlayer().hasPermission("dreimu.startserver."+name)) {
                event.getPlayer().sendMessage(Component.text(this.messages.getString("offline_noperms")));
                event.setInitialServer(this.server.getServer("lobby").get());
                return;
            }

            Pterodactyl.startServer(this.motds.getTable(name).getString("id"));
            if(!this.server.getServer("lobby").isPresent()) {
                return;
            }
            event.setInitialServer(this.server.getServer("lobby").get());
            Thread redirect = new Thread(() -> {
                try {
                    event.getPlayer().sendMessage(Component.text(this.messages.getString("redirect_timer")));
                    Player player = event.getPlayer();
                    RegisteredServer server = this.server.getServer(name).get();
                    TimeUnit.SECONDS.sleep(30);
                    player.createConnectionRequest(server).connect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            redirect.start();
            return;
        }

        event.setInitialServer(event.getInitialServer().get());

    }
}