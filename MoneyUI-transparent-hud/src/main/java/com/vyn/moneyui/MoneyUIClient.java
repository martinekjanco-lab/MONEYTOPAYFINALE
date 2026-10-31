package com.vyn.moneyui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.math.BigInteger;

public class MoneyUIClient implements ClientModInitializer {

    private static boolean pendingJoinMessage = false;

    @Override
    public void onInitializeClient() {
        System.out.println("[MoneyUI] loaded");
        Money.load();

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("moneyui", "money_hud"),
                (ctx, tick) -> renderHud(ctx));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> pendingJoinMessage = true);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingJoinMessage && client.player != null) {
                pendingJoinMessage = false;
                say(client, Text.literal("[MoneyUI] loaded. Use /ui, /setmoney, /pay, /paytoggle")
                        .formatted(Formatting.GREEN));
            }
        });

        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            String[] a = command.trim().split("\\s+");
            if (a.length == 0) return true;
            MinecraftClient mc = MinecraftClient.getInstance();
            switch (a[0].toLowerCase()) {
                case "ui" -> { cmdUi(mc); return false; }
                case "setmoney" -> { cmdSetMoney(mc, a); return false; }
                case "paytoggle" -> { cmdPayToggle(mc); return false; }
                case "pay" -> { cmdPay(mc, a); return false; }
                case "ding" -> { cmdDing(mc, a); return false; }
                default -> { return true; }
            }
        });
    }

    private static void cmdUi(MinecraftClient mc) {
        Money.uiVisible = !Money.uiVisible;
        say(mc, Text.literal("Money UI " + (Money.uiVisible ? "shown" : "hidden")).formatted(Formatting.GRAY));
    }

    private static void cmdSetMoney(MinecraftClient mc, String[] a) {
        if (a.length < 2) { err(mc, "Usage: /setmoney <amount>  (1k, 1m, 1b, 1t, 1q ...)"); return; }
        BigInteger v = Money.parse(a[1]);
        if (v == null) { err(mc, "Invalid amount. Examples: 500, 1k, 1m, 2.5b, 1t, 1q"); return; }
        Money.balance = v;
        Money.save();
        say(mc, Text.literal("Balance set to ").formatted(Formatting.GRAY)
                .append(Text.literal("$ ").formatted(Formatting.GREEN))
                .append(Text.literal(Money.abbreviate(v)).formatted(Formatting.WHITE)));
    }

    private static void cmdPayToggle(MinecraftClient mc) {
        Money.payEnabled = !Money.payEnabled;
        Money.save();
        say(mc, Text.literal("Payments are now ").formatted(Formatting.GRAY)
                .append(Text.literal(Money.payEnabled ? "ENABLED" : "DISABLED")
                        .formatted(Money.payEnabled ? Formatting.GREEN : Formatting.RED)));
    }

    private static void cmdPay(MinecraftClient mc, String[] a) {
        if (!Money.payEnabled) { err(mc, "Payments are disabled. Use /paytoggle to enable."); return; }
        if (a.length < 3) { err(mc, "Usage: /pay <name> <amount>"); return; }
        String name = a[1];
        BigInteger v = Money.parse(a[2]);
        if (v == null || v.signum() <= 0) { err(mc, "Invalid amount. Examples: 500, 1k, 1m, 1b"); return; }
        if (v.compareTo(Money.balance) > 0) { err(mc, "You don't have enough money."); return; }

        Money.balance = Money.balance.subtract(v);
        Money.save();

        // "You paid <name> $ <amount>"
        say(mc, Text.literal("You paid ").formatted(Formatting.WHITE)
                .append(Text.literal(name).formatted(Formatting.WHITE))
                .append(Text.literal(" "))
                .append(Text.literal("$").formatted(Formatting.GREEN))
                .append(Text.literal(" " + v).formatted(Formatting.WHITE)));

        playDing(mc, Money.dingChoice);
    }


    // /ding <1-5> : plays that sound and uses it for /pay
    private static void cmdDing(MinecraftClient mc, String[] a) {
        if (a.length < 2) {
            say(mc, Text.literal("Pick the pay sound: /ding 1 to /ding 5").formatted(Formatting.GRAY));
            return;
        }
        int n;
        try { n = Integer.parseInt(a[1]); } catch (Exception e) { n = 0; }
        if (n < 1 || n > 5) { err(mc, "Use a number from 1 to 5"); return; }
        Money.dingChoice = n;
        Money.save();
        playDing(mc, n);
        say(mc, Text.literal("Pay sound set to " + n).formatted(Formatting.GRAY));
    }

    private static void playDing(MinecraftClient mc, int n) {
        SoundEvent s;
        float pitch;
        switch (n) {
            case 2 -> { s = SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(); pitch = 1.6f; }
            case 3 -> { s = SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(); pitch = 2.0f; }
            case 4 -> { s = SoundEvents.ENTITY_ARROW_HIT_PLAYER; pitch = 1.0f; }
            case 5 -> { s = SoundEvents.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE.value(); pitch = 2.0f; }
            default -> { s = SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP; pitch = 1.0f; }
        }
        // Minecraft caps one sound's volume at 100%, so the XP ding is layered 3x to be louder
        int layers = (n == 1 || n < 1 || n > 5) ? 3 : 1;
        for (int i = 0; i < layers; i++) {
            mc.getSoundManager().play(PositionedSoundInstance.master(s, pitch, 1.0f));
        }
    }

    private static void say(MinecraftClient mc, Text t) {
        if (mc.player != null) mc.player.sendMessage(t, false);
    }

    private static void err(MinecraftClient mc, String s) {
        say(mc, Text.literal(s).formatted(Formatting.RED));
    }

    // Plain text on the right edge, no box: name on top, "$ 300M" under it
    private void renderHud(DrawContext ctx) {
        if (!Money.uiVisible) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;

        TextRenderer tr = mc.textRenderer;
        String name = mc.getSession().getUsername();
        String amount = Money.abbreviate(Money.balance);

        int w = Math.max(tr.getWidth(name), tr.getWidth("$ " + amount));
        int x = ctx.getScaledWindowWidth() - w - 3;
        int y = ctx.getScaledWindowHeight() / 2 - 8;

        ctx.drawTextWithShadow(tr, name, x, y, 0xFFFFFFFF);
        ctx.drawTextWithShadow(tr, "$", x, y + 9, 0xFF55FF55);
        ctx.drawTextWithShadow(tr, amount, x + tr.getWidth("$ "), y + 9, 0xFFFFFFFF);
    }
}
