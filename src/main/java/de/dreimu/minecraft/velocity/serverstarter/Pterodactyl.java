package de.dreimu.minecraft.velocity.serverstarter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moandjiezana.toml.Toml;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class Pterodactyl {

    public static Toml toml;

    public static boolean getServerOnline(String id) {
        try {
            URL url = new URL(Pterodactyl.toml.getTable("api").getString("endpoint") + "/client/servers/" + id + "/resources");
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + Pterodactyl.toml.getTable("api").getString("key"));

            String responseLine = "";

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));

            StringBuilder response = new StringBuilder();
            while ((responseLine = reader.readLine()) != null) {
                response.append(responseLine.trim());
            }

            JsonObject jsonObject = JsonParser.parseString(response.toString()).getAsJsonObject();

            return jsonObject.getAsJsonObject("attributes").get("current_state").getAsString().equals("running");
        } catch (Exception e) {
            return false;
        }
    }

    public static void startServer(String id) {
        try {
            URL startURL = new URL(toml.getTable("api").getString("endpoint") + "/client/servers/" + id + "/power");

            HttpsURLConnection startConnection = (HttpsURLConnection)startURL.openConnection();
            startConnection.setRequestMethod("POST");
            startConnection.setRequestProperty("Authorization", "Bearer " + toml.getTable("api").getString("key"));
            startConnection.setRequestProperty("Content-Type", "application/json; utf-8");
            startConnection.setDoOutput(true);
            try(DataOutputStream wr = new DataOutputStream(startConnection.getOutputStream())) {
                byte[] input = "{\"signal\":\"start\"}".getBytes(StandardCharsets.UTF_8);
                wr.write(input);
            }

            StringBuilder content;

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(startConnection.getInputStream()))) {

                String line;
                content = new StringBuilder();

                while ((line = br.readLine()) != null) {
                    content.append(line);
                    content.append(System.lineSeparator());
                }
            }
        } catch (Exception e) {
            return;
        }
    }

}
