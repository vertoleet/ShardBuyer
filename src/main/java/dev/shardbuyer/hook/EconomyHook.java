package dev.shardbuyer.hook;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Logger;

/** Обёртка над Vault Economy (провайдер ищется лениво — на случай поздней регистрации). */
public final class EconomyHook {

    private final Logger log;
    private Economy economy;

    public EconomyHook(Logger log) {
        this.log = log;
    }

    public Economy get() {
        if (economy == null) {
            try {
                RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
                if (rsp != null) {
                    economy = rsp.getProvider();
                }
            } catch (Throwable t) {
                // Vault отсутствует
            }
        }
        return economy;
    }

    public boolean available() {
        return get() != null;
    }

    public String name() {
        Economy e = get();
        return e == null ? "none" : e.getName();
    }

    public boolean deposit(OfflinePlayer p, double amount) {
        if (amount <= 0) {
            return true;
        }
        Economy e = get();
        if (e == null) {
            log.warning("No economy (Vault + economy plugin): selling is impossible!");
            return false;
        }
        try {
            EconomyResponse r = e.depositPlayer(p, amount);
            return r != null && r.transactionSuccess();
        } catch (Throwable t) {
            log.warning("Economy error: " + t.getMessage());
            return false;
        }
    }

    public double balance(OfflinePlayer p) {
        Economy e = get();
        if (e == null) {
            return 0;
        }
        try {
            return e.getBalance(p);
        } catch (Throwable t) {
            return 0;
        }
    }
}
