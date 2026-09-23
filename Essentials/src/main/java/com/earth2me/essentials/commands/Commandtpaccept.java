package com.earth2me.essentials.commands;

import com.earth2me.essentials.AsyncTeleport;
import com.earth2me.essentials.IUser;
import com.earth2me.essentials.Trade;
import com.earth2me.essentials.User;
import com.sk89q.worldguard.bukkit.WGBukkit;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.ess3.api.TranslatableException;
import net.essentialsx.api.v2.events.TeleportRequestResponseEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Commandtpaccept extends EssentialsCommand {

    public Commandtpaccept() {
        super("tpaccept");
    }

    @Override
    public void run(final Server server, final User user, final String commandLabel, final String[] args) throws Exception {
        final boolean acceptAll;
        if (args.length > 0) {
            acceptAll = args[0].equals("*") || args[0].equalsIgnoreCase("all");
        } else {
            acceptAll = false;
        }

        if (!user.hasPendingTpaRequests(true, acceptAll)) {
            throw new TranslatableException("noPendingRequest");
        }

        if (args.length > 0) {
            if (acceptAll) {
                acceptAllRequests(user, commandLabel);
                throw new NoChargeException();
            }
            user.sendTl("requestAccepted");
            handleTeleport(user, user.getOutstandingTpaRequest(getPlayer(server, user, args, 0).getName(), true), commandLabel);
        } else {
            user.sendTl("requestAccepted");
            handleTeleport(user, user.getNextTpaRequest(true, false, false), commandLabel);
        }
        throw new NoChargeException();
    }

    private void acceptAllRequests(final User user, final String commandLabel) throws Exception {
        IUser.TpaRequest request;
        int count = 0;
        while ((request = user.getNextTpaRequest(true, true, true)) != null) {
            try {
                handleTeleport(user, request, commandLabel);
                count++;
            } catch (Exception e) {
                ess.showError(user.getSource(), e, commandLabel);
            } finally {
                user.removeTpaRequest(request.getName());
            }
        }
        user.sendTl("requestAcceptedAll", count);
    }

    @Override
    protected List<String> getTabCompleteOptions(Server server, User user, String commandLabel, String[] args) {
        if (args.length == 1) {
            final List<String> options = new ArrayList<>(user.getPendingTpaKeys());
            options.add("*");
            return options;
        } else {
            return Collections.emptyList();
        }
    }

    private void handleTeleport(final User user, final IUser.TpaRequest request, String commandLabel) throws Exception {
        if (request == null) {
            throw new TranslatableException("noPendingRequest");
        }
        final User requester = ess.getUser(request.getRequesterUuid());

        if (!requester.getBase().isOnline()) {
            user.removeTpaRequest(request.getName());
            throw new TranslatableException("noPendingRequest");
        }

        if (request.isHere() && ((!requester.isAuthorized("essentials.tpahere") && !requester.isAuthorized("essentials.tpaall")) || (user.getWorld() != requester.getWorld() && ess.getSettings().isWorldTeleportPermissions() && !user.isAuthorized("essentials.worlds." + user.getWorld().getName())))) {
            throw new TranslatableException("noPendingRequest");
        }

        if (!request.isHere() && (!requester.isAuthorized("essentials.tpa") || (user.getWorld() != requester.getWorld() && ess.getSettings().isWorldTeleportPermissions() && !user.isAuthorized("essentials.worlds." + requester.getWorld().getName())))) {
            throw new TranslatableException("noPendingRequest");
        }

        final TeleportRequestResponseEvent event = new TeleportRequestResponseEvent(user, requester, request, true);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            if (ess.getSettings().isDebug()) {
                ess.getLogger().info("TPA accept cancelled by API for " + user.getName() + " (requested by " + requester.getName() + ")");
            }
            return;
        }

        final Trade charge = new Trade(this.getName(), ess);
        requester.sendTl("requestAcceptedFrom", user.getDisplayName());

        final CompletableFuture<Boolean> future = getNewExceptionFuture(requester.getSource(), commandLabel);
        future.exceptionally(e -> {
            user.sendTl("pendingTeleportCancelled");
            return false;
        });
        if (request.isHere()) {
            final Location loc = requester.getBase().getLocation();
            boolean preventTeleportedByRestrictedRegion = false;
            ApplicableRegionSet regionSet = WGBukkit.getRegionManager(loc.getWorld()).getApplicableRegions(loc);
            if (regionSet.getRegions() != null && !regionSet.getRegions().isEmpty()) {
                for (ProtectedRegion region : regionSet.getRegions()) {
                    if (region.getFlag(DefaultFlag.SLEEP) == StateFlag.State.DENY) {
                        preventTeleportedByRestrictedRegion = true;
                        break;
                    }
                }
            }

            final Location loc2 = user.getBase().getLocation();
            ApplicableRegionSet regionSet2 = WGBukkit.getRegionManager(loc2.getWorld()).getApplicableRegions(loc2);
            if (regionSet2.getRegions() != null && !regionSet2.getRegions().isEmpty()) {
                for (ProtectedRegion region : regionSet2.getRegions()) {
                    if (region.getFlag(DefaultFlag.SLEEP) == StateFlag.State.DENY) {
                        preventTeleportedByRestrictedRegion = true;
                        break;
                    }
                }
            }

            if (preventTeleportedByRestrictedRegion) {
                requester.sendMessage("§cVocê não pode ser teleportado para essa região.");
                user.sendMessage("§cO jogador não pode ser teleportado para essa região.");

                for (Player staff : Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission("essentials.god")).collect(Collectors.toList())) {
                    staff.sendMessage("§4[!] §cO jogador " + requester.getName() + " tentou ser teleportado para uma região restrita por " + user.getName() + ".");
                }

                return;
            }
            final AsyncTeleport teleport = requester.getAsyncTeleport();
            teleport.setTpType(AsyncTeleport.TeleportType.TPA);
            future.thenAccept(success -> {
                if (success) {
                    requester.sendTl("teleporting", loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                }
            });
            teleport.teleportPlayer(user, loc, charge, TeleportCause.COMMAND, future);
        } else {
            final AsyncTeleport teleport = requester.getAsyncTeleport();
            teleport.setTpType(AsyncTeleport.TeleportType.TPA);
            boolean preventTeleportedByRestrictedRegion = false;
            final Location loc = requester.getBase().getLocation();
            ApplicableRegionSet regionSet = WGBukkit.getRegionManager(loc.getWorld()).getApplicableRegions(loc);
            if (regionSet.getRegions() != null && !regionSet.getRegions().isEmpty()) {
                for (ProtectedRegion region : regionSet.getRegions()) {
                    if (region.getFlag(DefaultFlag.SLEEP) == StateFlag.State.DENY) {
                        preventTeleportedByRestrictedRegion = true;
                        break;
                    }
                }
            }

            final Location loc2 = user.getBase().getLocation();
            ApplicableRegionSet regionSet2 = WGBukkit.getRegionManager(loc2.getWorld()).getApplicableRegions(loc2);
            if (regionSet2.getRegions() != null && !regionSet2.getRegions().isEmpty()) {
                for (ProtectedRegion region : regionSet2.getRegions()) {
                    if (region.getFlag(DefaultFlag.SLEEP) == StateFlag.State.DENY) {
                        preventTeleportedByRestrictedRegion = true;
                        break;
                    }
                }
            }

            if (preventTeleportedByRestrictedRegion) {
                requester.sendMessage("§cVocê não pode ser teleportado para essa região.");
                user.sendMessage("§cO jogador não pode ser teleportado para essa região.");

                for (Player staff : Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission("essentials.god")).collect(Collectors.toList())) {
                    staff.sendMessage("§4[!] §cO jogador " + requester.getName() + " tentou ser teleportado para uma região restrita por " + user.getName() + ".");
                }

                return;
            }
            teleport.teleport(user.getBase(), charge, TeleportCause.COMMAND, future);
        }
        user.removeTpaRequest(request.getName());
    }
}
