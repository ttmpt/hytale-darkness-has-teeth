package net.ttmpt;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import java.io.FileWriter;
import java.io.IOException;

public class DarknessHasTeeth extends JavaPlugin {

    public DarknessHasTeeth(@Nonnull JavaPluginInit init) {
        super(init);
        logToFile("[DarkTimes] Plugin constructed");
    }

    @Override
    protected void setup() {
        super.setup();
        logToFile("[DarkTimes] setup() called");

        this.getEntityStoreRegistry().registerSystem(new DarknessMiningSystem());
        logToFile("[DarkTimes] DarkMiningSystem registered");
    }

    public static void logToFile(String message) {
        try (FileWriter fw = new FileWriter("/tmp/darktimes.log", true)) {
            fw.write(message + "\n");
            fw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
