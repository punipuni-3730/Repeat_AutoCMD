package prj.salmon.repeat_autocmd;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;

public class Repeat_autocmd implements ClientModInitializer {
    public static final String MOD_ID = "repeat_autocmd";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static Timer commandTimer;
    private static RepeatingTask currentRepeatingTask;

    /**
     * 繰り返し実行されるタスクの詳細を保持するクラス
     */
    private static class RepeatingTask {
        final String commandToExecute;
        final int intervalSeconds;
        TimerTask task;

        RepeatingTask(String commandToExecute, int intervalSeconds) {
            this.commandToExecute = commandToExecute;
            this.intervalSeconds = intervalSeconds;
        }

        void start(MinecraftClient client) {
            if (task != null) {
                task.cancel();
            }
            task = new TimerTask() {
                @Override
                public void run() {
                    client.execute(() -> {
                        if (client.getNetworkHandler() == null || client.player == null) {
                            stopRepeatingCommandInternal("サーバー接続が切断されました。");
                            return;
                        }
                        client.getNetworkHandler().sendChatCommand(commandToExecute);
                        LOGGER.info("繰り返しコマンドを送信: '{}'", commandToExecute);
                    });
                }
            };

            if (commandTimer == null) {
                commandTimer = new Timer(MOD_ID + "-Timer", true);
            }
            commandTimer.scheduleAtFixedRate(task, intervalSeconds * 1000L, intervalSeconds * 1000L);
        }

        void stop() {
            if (task != null) {
                task.cancel();
                task = null;
            }
        }
    }

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(this::registerCommands);
    }

    private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(ClientCommandManager.literal("repeat")
                .then(ClientCommandManager.argument("interval", IntegerArgumentType.integer(1))
                        .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                                .executes(this::executeStartRepeating)
                        )
                )
                .then(ClientCommandManager.literal("stop")
                        .executes(this::executeStopRepeating)
                )
        );
    }

    private int executeStartRepeating(CommandContext<FabricClientCommandSource> context) {
        int interval = IntegerArgumentType.getInteger(context, "interval");
        String commandStr = StringArgumentType.getString(context, "command");
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null) {
            return 0;
        }

        if (currentRepeatingTask != null) {
            currentRepeatingTask.stop();
            if (commandTimer != null) {
                commandTimer.purge();
            }
            client.player.sendMessage(Text.literal("現在の繰り返しコマンドを停止しました。"), false);
        }

        try {
            currentRepeatingTask = new RepeatingTask(commandStr, interval);
            currentRepeatingTask.start(client);
            client.player.sendMessage(Text.literal("コマンド '" + commandStr + "' を " + interval + " 秒ごとに繰り返します。停止するには '/repeat stop' を使用してください。"), false);
            LOGGER.info("繰り返しコマンドを開始: '{}' ({}秒ごと)", commandStr, interval);
            return 1;
        } catch (Exception e) {
            client.player.sendMessage(Text.literal("繰り返しコマンドの開始に失敗: " + e.getMessage()), false);
            LOGGER.error("繰り返しコマンド開始に失敗: {}", e.getMessage(), e);
            return 0;
        }
    }

    private int executeStopRepeating(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return 0;
        }

        if (currentRepeatingTask != null) {
            currentRepeatingTask.stop();
            currentRepeatingTask = null;
            if (commandTimer != null) {
                commandTimer.purge();
            }
            client.player.sendMessage(Text.literal("繰り返しコマンドを停止しました。"), false);
            LOGGER.info("繰り返しコマンドが停止されました。");
            return 1;
        } else {
            client.player.sendMessage(Text.literal("現在繰り返されているコマンドはありません。"), false);
            return 0;
        }
    }

    private static void stopRepeatingCommandInternal(String reason) {
        if (currentRepeatingTask != null) {
            currentRepeatingTask.stop();
            currentRepeatingTask = null;
            if (commandTimer != null) {
                commandTimer.purge();
            }
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal(reason), false);
            }
            LOGGER.info(reason);
        }
    }
}