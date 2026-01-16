package net.ttmpt;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import java.io.FileWriter;
import java.io.IOException;

public class DarknessHasTeeth extends JavaPlugin {

    public DarknessHasTeeth(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        super.setup();
        this.getEntityStoreRegistry().registerSystem(new DarknessMiningSystem());
    }
}
