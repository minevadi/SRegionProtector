package Sergey_Dertan.SRegionProtector.Region;

import Sergey_Dertan.SRegionProtector.Provider.Provider;
import Sergey_Dertan.SRegionProtector.Region.Chunk.Chunk;
import Sergey_Dertan.SRegionProtector.Region.Chunk.ChunkManager;
import Sergey_Dertan.SRegionProtector.Region.Flags.FlagList;
import Sergey_Dertan.SRegionProtector.Region.Flags.RegionFlags;
import Sergey_Dertan.SRegionProtector.Utils.Utils;
import cn.nukkit.Player;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3f;
import cn.nukkit.plugin.PluginLogger;
import cn.nukkit.utils.TextFormat;

import java.io.IOException;
import java.util.*;

public final class RegionManager {

    private Provider provider;
    private Map<String, Region> regions;
    private PluginLogger logger;
    private ChunkManager chunkManager;
    private Map<String, List<Region>> owners, members;
    //TODO need update

    public RegionManager(Provider provider, PluginLogger logger) {
        this.provider = provider;
        this.logger = logger;
    }

    public void setChunkManager(ChunkManager chunkManager) {
        this.chunkManager = chunkManager;
    }

    public Map<String, Region> getRegions() {
        return this.regions;
    }

    public boolean regionExists(String name) {
        return this.regions.containsKey(name);
    }

    public void init() {
        this.regions = new HashMap<>();
        this.owners = new HashMap<>();
        this.members = new HashMap<>();
        List<Map<String, Object>> regions = this.provider.loadRegionList();
        for (Map<String, Object> regionData : regions) {
            String name = (String) regionData.get("name");
            String creator = (String) regionData.get("creator");
            String level = (String) regionData.get("level");

            double minX = (double) regionData.get("min_x");
            double minY = (double) regionData.get("min_y");
            double minZ = (double) regionData.get("min_z");

            double maxX = (double) regionData.get("max_x");
            double maxY = (double) regionData.get("max_y");
            double maxZ = (double) regionData.get("max_z");

            String[] owners;
            String[] members;

            try {
                owners = Utils.deserializeArray((String) regionData.get("owners"));
                members = Utils.deserializeArray((String) regionData.get("members"));
            } catch (IOException e) {
                this.logger.warning(TextFormat.YELLOW + "Cant load region " + name + ": " + e.getMessage());
                continue;
            }

            FlagList flagList = RegionFlags.loadFlagList(this.provider.loadFlags(name));

            Region region = new Region(name, creator, level, minX, minY, minZ, maxX, maxY, maxZ, new ArrayList<>(Arrays.asList(owners)), new ArrayList<>(Arrays.asList(members)), flagList);

            this.regions.put(name, region);

            for (String user : owners) this.owners.computeIfAbsent(user, (usr) -> new ArrayList<>()).add(region);

            for (String user : members) this.members.computeIfAbsent(user, (usr) -> new ArrayList<>()).add(region);

            this.owners.computeIfAbsent(region.getCreator(), (usr) -> new ArrayList<>()).add(region);
        }

        this.logger.info(TextFormat.GREEN + "Loaded " + this.regions.size() + " regions.");
    }

    public Region createRegion(String name, String creator, Vector3f pos1, Vector3f pos2, String level) {
        double minX = Math.min(pos1.x, pos2.x);
        double minY = Math.min(pos1.y, pos2.y);
        double minZ = Math.min(pos1.z, pos2.z);

        double maxX = Math.max(pos1.x, pos2.x);
        double maxY = Math.max(pos1.y, pos2.y);
        double maxZ = Math.max(pos1.z, pos2.z);

        Region region = new Region(name, creator, level, minX, minY, minZ, maxX, maxY, maxZ);

        this.chunkManager.getRegionChunks(pos1, pos2, level, true).forEach(chunk -> chunk.addRegion(region));
        this.owners.computeIfAbsent(creator, (s) -> new ArrayList<>()).add(region);
        this.regions.put(name, region);
        return region;
    }

    public void changeRegionOwner(Region region, String newOwner) {
        region.getMembers().forEach(member ->
                {
                    this.members.get(member).remove(region);
                    if (this.members.get(member).size() == 0) this.members.remove(member);
                }
        );

        region.getOwners().forEach(owner ->
                {
                    this.owners.get(owner).remove(region);
                    if (this.owners.get(owner).size() == 0) this.owners.remove(owner);
                }
        );

        this.owners.get(region.getCreator()).remove(region);
        if (this.owners.get(region.getCreator()).size() == 0) this.owners.remove(region.getCreator());

        region.clearUsers();

        this.owners.computeIfAbsent(newOwner, (s) -> new ArrayList<>()).add(region);
        region.setCreator(newOwner);
        region.getFlagList().getSellFlag().state = false;
        region.getFlagList().getSellFlag().price = -1;
    }

    public void removeRegion(Region region) {
        region.getMembers().forEach(member ->
                {
                    this.members.get(member).remove(region);
                    if (this.members.get(member).size() == 0) this.members.remove(member);
                }
        );

        region.getOwners().forEach(owner ->
                {
                    this.owners.get(owner).remove(region);
                    if (this.owners.get(owner).size() == 0) this.owners.remove(owner);
                }
        );

        this.owners.get(region.getCreator()).remove(region);
        if (this.owners.get(region.getCreator()).size() == 0) this.owners.remove(region.getCreator());

        region.getChunks().forEach(chunk -> chunk.removeRegion(region));

        this.regions.remove(region.getName());
        this.provider.removeRegion(region);
    }

    public boolean checkOverlap(Vector3f pos1, Vector3f pos2, String level) {
        double minX = Math.min(pos1.x, pos2.x);
        double minY = Math.min(pos1.y, pos2.y);
        double minZ = Math.min(pos1.z, pos2.z);

        double maxX = Math.max(pos1.x, pos2.x);
        double maxY = Math.max(pos1.y, pos2.y);
        double maxZ = Math.max(pos1.z, pos2.z);

        SimpleAxisAlignedBB bb = new SimpleAxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);

        for (Chunk chunk : this.chunkManager.getRegionChunks(pos1, pos2, level, false)) {
            for (Region region : chunk.getRegions()) {
                if (!region.intersectsWith(bb)) continue;
                return true;
            }
        }
        return false;
    }

    public int getPlayersRegionAmount(Player player, boolean isCreator) {
        if (!isCreator) return this.owners.getOrDefault(player.getName().toLowerCase(), new ArrayList<>()).size();
        List<Region> regions = this.owners.getOrDefault(player.getName().toLowerCase(), new ArrayList<>());
        if (regions.isEmpty()) return 0;
        int amount = 0;
        for (Region region : regions) if (region.isCreator(player.getName().toLowerCase())) ++amount;
        return amount;
    }

    public int getPlayersRegionAmount(Player player) {
        return this.getPlayersRegionAmount(player, true);
    }

    public List<Region> getOwningRegions(Player player, boolean isCreator) {
        if (!isCreator) return this.owners.getOrDefault(player.getName().toLowerCase(), new ArrayList<>());
        List<Region> list = new ArrayList<>();
        for (Region region : this.owners.getOrDefault(player.getName().toLowerCase(), new ArrayList<>())) {
            if (region.isCreator(player.getName().toLowerCase())) list.add(region);
        }
        return list;
    }

    public List<Region> getOwningRegions(Player player) {
        return this.getOwningRegions(player, false);
    }

    public List<Region> getPlayerMemberRegions(Player player) {
        return this.members.getOrDefault(player.getName().toLowerCase(), new ArrayList<>());
    }

    public void addMember(Region region, String target) {
        this.members.computeIfAbsent(target, (usr) -> new ArrayList<>()).add(region);
        region.addMember(target);
    }

    public void addOwner(Region region, String target) {
        this.owners.computeIfAbsent(target, (usr) -> new ArrayList<>()).add(region);
        region.addOwner(target);
    }

    public void removeOwner(Region region, String target) {
        this.owners.get(target).remove(region);
        if (this.owners.get(target).size() == 0) this.owners.remove(target);
        region.removeOwner(target);
    }

    public void removeMember(Region region, String target) {
        this.members.get(target).remove(region);
        if (this.members.get(target).size() == 0) this.members.remove(target);
        region.removeMember(target);
    }

    public Region getRegion(String name) {
        return this.regions.get(name);
    }

    public void save() {
        this.provider.saveRegionList(new ArrayList<>(this.regions.values()));
    }
}
