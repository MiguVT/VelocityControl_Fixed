package org.mineacademy.velocitycontrol;

import lombok.Getter;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.mineacademy.velocitycontrol.foundation.Common;
import org.mineacademy.velocitycontrol.listener.OutgoingMessage;
import org.mineacademy.velocitycontrol.listener.VelocityControlListener;
import org.mineacademy.velocitycontrol.model.ChannelMode;
import org.mineacademy.velocitycontrol.model.ProxyPacket;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a cache with data from BungeeCord
 */
@Getter
public final class SyncedCache {

	/**
	 * The internal map
	 * Name : Data
	 */
	private static final Map<String, SyncedCache> cacheMap = new HashMap<>();

	/**
	 * The player name
	 */
	@Getter
	private final String playerName;

	/**
	 * The unique ID
	 */
	@Getter
	private final UUID uniqueId;

	/**
	 * The server where this player is on
	 */
	@Getter
	private String serverName = "";

	/**
	 * His nick if any
	 */
	@Getter
	private String nick;

	/**
	 * Is the player vanished?
	 */
	private boolean vanished;

	/**
	 * Is the player a fucking drunk?
	 */
	private boolean afk;

	/**
	 * Does this plugin not give a damn about private messages?
	 */
	private boolean ignoringPMs;

	/**
	 * Is the player ignoring sound notifications
	 */
	private boolean ignoringSoundNotify;

	/**
	 * List of ignored dudes
	 */
	private Set<UUID> ignoredPlayers = new HashSet<>();;

	/**
	 * Map of channel names and modes this synced man is in
	 */
	@Getter
	private Map<String, ChannelMode> channels = new HashMap<>();

	/**
	 * The player prefix from Vault
	 */
	@Getter
	private String prefix;

	/**
	 * The player group from Vault
	 */
	@Getter
	private String group;

	/*
	 * Create a synced cache from the given data map
	 */
	private SyncedCache(String playerName, UUID uniqueId) {
		this.playerName = playerName;
		this.uniqueId = uniqueId;
	}

	private void loadData(String line) {
		final String[] sections = line.split(".<<");

		for (final String section : sections) {
			final String[] parts = section.split("\\:");

			final String sectionName = parts[0];
			final String sectionValue = Common.joinRange(1, parts.length, parts, ":");

			if ("S".equals(sectionName))
				this.serverName = sectionValue;

			else if ("N".equals(sectionName))
				this.nick = sectionValue.isEmpty() ? null : sectionValue;

			else if ("V".equals(sectionName))
				this.vanished = Builder.parseBoolean(sectionValue);

			else if ("A".equals(sectionName))
				this.afk = Builder.parseBoolean(sectionValue);

			else if ("IM".equals(sectionName))
				this.ignoringPMs = Builder.parseBoolean(sectionValue);

			else if ("IN".equals(sectionName))
				this.ignoringSoundNotify = Builder.parseBoolean(sectionValue);

			else if ("IP".equals(sectionName))
				this.ignoredPlayers = Builder.parseUUIDList(sectionValue);

			else if ("C".equals(sectionName))
				this.channels = Builder.parseChannels(sectionValue);

			else if ("G".equals(sectionName))
				this.group = sectionValue;

			else if ("P".equals(sectionName))
				this.prefix = sectionValue;
		}

	}

	/**
	 * Return a dude's name or nick if set
	 *
	 * @return
	 */
	public String getNameOrNickColorless() {
		return Common.stripColors(this.getNameOrNickColored());
	}

	/**
	 * Return a dude's name or nick if set
	 *
	 * @return
	 */
	public String getNameOrNickColored() {
		return Common.getOrDefaultStrict(this.nick, this.playerName);
	}

	/**
	 * Return true if this dude is ignoring the other dude's unique id
	 * Females not supported
	 *
	 * @param uniqueId
	 * @return
	 */
	public boolean isIgnoringPlayer(UUID uniqueId) {
		return this.ignoredPlayers.contains(uniqueId);
	}

	/**
	 * Return the channel mode if player is in the given channel else null
	 *
	 * @param channelName
	 * @return
	 */
	public ChannelMode getChannelMode(String channelName) {
		return this.channels.get(channelName);
	}

	/**
	 * Is vanished?
	 */
	public boolean isVanished() {
		return this.vanished;
	}

	/**
	 * Is afk?
	 */
	public boolean isAfk() {
		return this.afk;
	}

	/**
	 * Is ignoring pms?
	 */
	public boolean isIgnoringPMs() {
		return this.ignoringPMs;
	}

	/**
	 * Return true if this dude is ignoring sound notifications
	 *
	 * @return
	 */
	public boolean isIgnoringSoundNotifications() {
		return this.ignoringSoundNotify;
	}

	/**
	 * Convert into known variables usable in chat
	 *
	 * @return
	 */
	public HashMap<String, String> toVariables() {
		return new HashMap<>() {{
			put("player_name", getPlayerName());
			put("name", getPlayerName());
			put("player_nick", getNameOrNickColored());
			put("nick", getNameOrNickColored());
			put("player_group", getGroup());
			put("player_prefix", getPrefix());
			put("player_server", getServerName());
			put("player_afk", isAfk() ? "true" : "false");
			put("player_ignoring_pms", isIgnoringPMs() ? "true" : "false");
			put("player_ignoring_sound_notifications", isIgnoringSoundNotify() ? "true" : "false");
			put("player_vanished", isVanished() ? "true" : "false");
		}};
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "SyncedCache{" + this.playerName + ",nick=" + this.nick + "}";
	}

	/* ------------------------------------------------------------------------------- */
	/* Static */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Return true if the given player is connected and synced on Velocity
	 */
	public static boolean isPlayerConnected(String playerName) {
		synchronized (cacheMap) {
			return cacheMap.containsKey(playerName);
		}
	}

	/**
	 * Return true should the given server name match a valid server we know of...
	 */
	public static boolean doesServerExist(String serverName) {
		synchronized (cacheMap) {
			for (final SyncedCache cache : cacheMap.values())
				if (cache.getServerName().equalsIgnoreCase(serverName))
					return true;

			return false;
		}
	}

	/**
	 * Resolve a synced cache from the given player
	 */

	public static SyncedCache fromName(String playerName) {
		synchronized (cacheMap) {
			return cacheMap.get(playerName);
		}
	}

	/**
	 * Return the synced cache (or null) from the player name or nick
	 *
	 * @param nick
	 * @return
	 */
	public static SyncedCache fromNick(String nick) {
		synchronized (cacheMap) {
			for (final SyncedCache cache : cacheMap.values()) {
				String strippedNick = LegacyComponentSerializer.legacySection().deserialize(nick).content();
				String strippedCacheNick = LegacyComponentSerializer.legacySection().deserialize(cache.getNick()).content();
				if (cache.getPlayerName().equalsIgnoreCase(nick) || (cache.getNick() != null && strippedNick.equalsIgnoreCase(strippedCacheNick))) {
					return cache;
				}
			}

			return null;
		}
	}

	/**
	 * Return a set of all known servers on BungeeCord where players are on
	 *
	 * @return
	 */
	public static Set<String> getServers() {
		synchronized (cacheMap) {
			final Set<String> servers = new HashSet<>();

			for (final SyncedCache cache : getCaches())
				servers.add(cache.getServerName());

			return servers;
		}
	}

	/**
	 * Return all caches stored in memory
	 *
	 * @return
	 */
	public static Collection<SyncedCache> getCaches() {
		synchronized (cacheMap) {
			return cacheMap.values();
		}
	}

	/**
	 * Return a list of all network player names
	 *
	 * @return
	 */
	public static Set<String> getPlayerNames() {
		synchronized (cacheMap) {
			final Set<String> names = new HashSet<>();

			for (final SyncedCache cache : cacheMap.values()) {
				names.add(cache.getNameOrNickColorless());
				names.add(cache.getPlayerName());
			}

			return names;
		}
	}

	public static Set<String> getJustNames() {
		synchronized (cacheMap) {
			final Set<String> names = new HashSet<>();

			for (final SyncedCache cache : cacheMap.values())
				names.add(cache.getPlayerName());

			return names;
		}
	}

	/**
	 * Add/remove syncedcaches based on online network players
	 */
	public static void updateForOnlinePlayers() {
		synchronized (cacheMap) {
			final HashMap<String, UUID> onlinePlayers = new HashMap<>();

			// Add non-cached players
			VelocityControl.getServer().getAllPlayers().forEach(player -> {
				final String playerName = player.getUsername();
				final UUID uniqueId = player.getUniqueId();

				if (!cacheMap.containsKey(playerName))
					cacheMap.put(playerName, new SyncedCache(playerName, uniqueId));

				onlinePlayers.put(playerName, uniqueId);
			});

			cacheMap.forEach((playerName, syncedCache) -> {
				//TODO possible race condition if player removed before leave message?
				if (!onlinePlayers.containsKey(playerName))
					cacheMap.remove(playerName);
			});

			final OutgoingMessage message = new OutgoingMessage(ProxyPacket.PLAYERS_CLUSTER_HEADER);
			message.writeMap(onlinePlayers);

			VelocityControl.broadcastPacket(message);
		}
	}

	/**
	 * Retrieve (or create) a sender cache
	 * @param syncType
	 * @param data
	 */
	public static void upload(VelocityControlListener.SyncType syncType, HashMap<String, String> data) {
		synchronized (cacheMap) {

			final Set<String> players = new HashSet<>();

			data.forEach((playerName, dataLine) -> {
				final SyncedCache cache = cacheMap.get(playerName);

				if (cache != null) {
					VelocityControl.getLogger().info("Loading data for " + playerName + " of type " + syncType + " from line " + dataLine);

					cache.loadData(dataLine);
				}
			});
		}
	}

	/**
	 * Force data update for the given dude.
	 *
	 * @param playerName
	 * @param uniqueId
	 * @param line
	 */
	public static void uploadSingle(String playerName, UUID uniqueId, String line) {
		SyncedCache cache = cacheMap.get(playerName);

		if (cache == null)
			cache = new SyncedCache(playerName, uniqueId);

		cache.loadData(line);
		cacheMap.put(playerName, cache);
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	/**
	 * A helper for cost-effective cross-network data sync
	 */
	public static final class Builder {

		/**
		 * Convert a line value into a boolean
		 * 
		 * @param value
		 * @return
		 */
		public static boolean parseBoolean(String value) {
			return value.equals("0") ? false : true;
		}

		/**
		 * Convert a line value into a set of uuids
		 * 
		 * @param value
		 * @return
		 */
		public static Set<UUID> parseUUIDList(String value) {
			return value.isEmpty() ? new HashSet<>() : new HashSet<>(Arrays.stream(value.split("\\|")).map(UUID::fromString).collect(Collectors.toSet()));
		}

		/**
		 * Convert a line value into a map of channel-mode pairs
		 * 
		 * @param value
		 * @return
		 */
		public static Map<String, ChannelMode> parseChannels(String value) {
			final Map<String, ChannelMode> channels = new HashMap<>();
			final String[] channelWithModes = value.split("\\|");

			for (final String channelWithMode : channelWithModes) {
				final String[] parts = channelWithMode.split("\\:");

				final String channelName = parts[0];
				final ChannelMode channelMode = ChannelMode.values()[1];

				channels.put(channelName, channelMode);
			}

			return channels;
		}
	}
}
