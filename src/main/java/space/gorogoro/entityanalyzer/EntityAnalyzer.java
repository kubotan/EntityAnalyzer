package space.gorogoro.entityanalyzer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/*
 * EntityAnalyzer
 * @license    LGPLv3
 * @copyright  Copyright gorogoro.space 2017
 * @author     kubotan
 * @see        <a href="http://blog.gorogoro.space">Kubotan's blog.</a>
 */
public class EntityAnalyzer extends JavaPlugin {

  public Map<String, Long> checkedMap = new HashMap<>();

  /**
   * JavaPlugin method onEnable.
   */
  @Override
  public void onEnable() {
    try {
      getLogger().info("The Plugin Has Been Enabled!");

      // If there is no setting file, it is created
      if(!getDataFolder().exists()){
        getDataFolder().mkdir();
      }
      File configFile = new File(getDataFolder() + "/config.yml");
      if(!configFile.exists()){
        saveDefaultConfig();
      }

      Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
        @Override
        public void run() {
          if(!getConfig().getBoolean("notice")) {
            return;
          }
          for (World w : getServer().getWorlds()) {
            for (Player p : w.getPlayers()) {
              for (Chunk c : p.getWorld().getLoadedChunks()) {
                if (c.getTileEntities().length < getConfig().getInt("limit_entity") && c.getEntities().length < getConfig().getInt("limit_entity")) {
                  continue;
                }
                if (getDistance(p.getLocation().getChunk().getX(), p.getLocation().getChunk().getZ(),
                    c.getX(), c.getZ()) > getConfig().getInt("detect_chunk_range")) {
                  continue;
                }
                String checkedKey = c.getX() + "_" + c.getZ();
                if (checkedMap.get(checkedKey) == null
                    || System.currentTimeMillis() - checkedMap.get(checkedKey) > 60 * 60 * 1000d) {
                  String title = "注意";
                  String subtitle = "多数のエンティティーを検知しました。(x:" + (c.getX() * 16) + ",z:"
                      + (c.getZ() * 16) + ")";
                  p.sendTitle(title, subtitle, 10, 300, 20);
                  checkedMap.put(checkedKey, System.currentTimeMillis());
                  String msg = " " + (getConfig().getInt("detect_chunk_range") * 16) + "ブロック以内にエンティティーまたはタイルエンティティーが"
                      + getConfig().getInt("limit_entity") + "以上のチャンクあります。ホッパー、チェスト、額縁等を分散するか整理をお願いします(x:"
                      + (c.getX() * 16) + ",z:" + (c.getZ() * 16) + ")";
                  p.sendMessage(ChatColor.DARK_GRAY + msg);
                  getLogger().info(msg + " " + p.getName());
                }
              }
            }
          }
        }
      }, 0L, 20L);
    } catch (Exception e) {
      EntityAnalyzerUtility.logStackTrace(e);
    }
  }

  /**
   * JavaPlugin method onCommand.
   */
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    try {
      if (command.getName().equals("eastatus")) {
        if (!sender.isOp()) {
          EntityAnalyzerUtility.sendMessage(sender, "権限がありません。");
          return false;
        }

        if (args.length < 2) {
          List<World> wlist = new ArrayList<World>();
          if(args.length == 1) {
            wlist.add(getServer().getWorld(args[0]));
          }else {
            wlist = getServer().getWorlds();
          }
          for (World world : wlist) {
            Map<String, Integer> erank = new HashMap<>();
            Map<String, Integer> trank = new HashMap<>();

            // 全エンティティー類を取得
            for (Chunk c : world.getLoadedChunks()) {
              // エンティティー種類ごとのカウント
              for (Entity e: c.getEntities()) {
                String key = e.getType().toString();
                if(!erank.containsKey(key)) {
                  erank.put(key, 0);
                }
                int cnt = erank.get(key);
                cnt++;
                erank.put(key, cnt);
              }

              // タイルエンティティー種類ごとのカウント
              for (BlockState t: c.getTileEntities()) {
                String key = t.getType().toString();
                if(!trank.containsKey(key)) {
                  trank.put(key, 0);
                }
                int cnt = trank.get(key);
                cnt++;
                trank.put(key,  cnt);
              }
            }

            int printNum = 0;
            // エンティティー結果表示
            String msg = "========== Entity Type Rank WORLD:" + world.getName() + " ==========";
            EntityAnalyzerUtility.sendMessage(sender, msg);
            getServer().getLogger().info(msg);
            for (Entry<String, Integer> s : getEntrySortedList(erank)) {
              printNum++;
              String emsg = "  ENTITY:" + s.getKey() + " COUNT:" + String.valueOf(s.getValue());
              EntityAnalyzerUtility.sendMessage(sender, emsg);
              getServer().getLogger().info(emsg);
              if(printNum >= 10) {
                break;
              }
            }

            printNum = 0;
            // タイルエンティティー結果表示
            msg = "========== TileEntity Type Rank WORLD:" + world.getName() + " ==========";
            EntityAnalyzerUtility.sendMessage(sender, msg);
            getServer().getLogger().info(msg);
            for (Entry<String, Integer> s : getEntrySortedList(trank)) {
              printNum++;
              String tmsg = "  TILE_ENTITY:" + s.getKey() + " COUNT:" + String.valueOf(s.getValue());
              EntityAnalyzerUtility.sendMessage(sender, tmsg);
              getServer().getLogger().info(tmsg);
              if(printNum >= 10) {
                break;
              }
            }

            EntityAnalyzerUtility.sendMessage(sender, "========================================================================");
            getServer().getLogger().info("========================================================================");
          }
        } else {
          World world = getServer().getWorld(args[0]);
          String target = args[1];

          Map<String, Integer> erank = new HashMap<>();
          Map<String, Integer> trank = new HashMap<>();
          String key;

          for (Chunk c : world.getLoadedChunks()) {
            key = String.valueOf(c.getX() * 16) + "," + String.valueOf(c.getZ() * 16);

            if (!erank.containsKey(key)) {
              erank.put(key, 0);
            }
            if (!trank.containsKey(key)) {
              trank.put(key, 0);
            }

            int ecnt = 0;
            for(Entity e: c.getEntities()) {
              if (e.getType().toString().equals(target)) {
                ecnt++;
              }
            }
            erank.put(key, erank.get(key) + ecnt);
            int tcnt = 0;
            for(BlockState t: c.getTileEntities()) {
              if (t.getType().toString().equals(target)) {
                tcnt++;
              }
            }
            trank.put(key, trank.get(key) + tcnt);
          }

          int printNum = 0;
          String msg = "========== Entity Type Rank TYPE:" + target + " ==========";
          EntityAnalyzerUtility.sendMessage(sender, msg);
          List<Map.Entry<String, Integer>> eresult = getEntrySortedList(erank);
          int esum = 0;
          for (Entry<String, Integer> s : eresult) {
            if(s.getValue() <= 0) {
              continue;
            }
            msg = "  POSITION:" + s.getKey() + " ENTITY_COUNT:" + String.valueOf(s.getValue());
            getServer().getLogger().info(msg);
            EntityAnalyzerUtility.sendMessage(sender, msg);
            esum = esum + s.getValue();
            printNum++;
            if(printNum >= 10) {
              break;
            }
          }

          printNum = 0;
          List<Map.Entry<String, Integer>> tresult = getEntrySortedList(trank);
          int tsum = 0;
          for (Entry<String, Integer> s : tresult) {
            if(s.getValue() <= 0) {
              continue;
            }
            msg = "  POSITION:" + s.getKey() + " TILE_ENTITY_COUNT:" + String.valueOf(s.getValue());
            getServer().getLogger().info(msg);
            EntityAnalyzerUtility.sendMessage(sender, msg);
            tsum = tsum + s.getValue();
            printNum++;
            if(printNum >= 10) {
              break;
            }
          }
          msg = "esum:" + String.valueOf(esum) + " tsum:" + String.valueOf(tsum);
          getServer().getLogger().info(msg);
          EntityAnalyzerUtility.sendMessage(sender, msg);
        }
      } else if (command.getName().equals("eatwrank")) {
        Map<String, Integer> rank = new HashMap<>();
        List<World> wlist = this.getServer().getWorlds();
        for (World world : wlist) {
          int cSize = 0;
          for (Chunk c : world.getLoadedChunks()) {
            cSize += c.getTileEntities().length;
          }
          rank.put(world.getName(), cSize);
        }
        // List 生成 (ソート用)
        List<Map.Entry<String, Integer>> entries = getEntrySortedList(rank);
        // 内容を表示
        for (Entry<String, Integer> s : entries) {
          EntityAnalyzerUtility.sendMessage(sender,
              "WORLD:" + s.getKey() + " ENTITY_COUNT:" + String.valueOf(s.getValue()));
        }
      } else if (command.getName().equals("eawrank")) {
        Map<String, Integer> rank = new HashMap<>();
        List<World> wlist = this.getServer().getWorlds();
        for (World world : wlist) {
          rank.put(world.getName(), world.getEntities().size());
        }
        // List 生成 (ソート用)
        List<Map.Entry<String, Integer>> entries = getEntrySortedList(rank);
        // 内容を表示
        for (Entry<String, Integer> s : entries) {
          EntityAnalyzerUtility.sendMessage(sender,
              "WORLD:" + s.getKey() + " ENTITY_COUNT:" + String.valueOf(s.getValue()));
        }

      } else if (command.getName().equals("earank")) {
        if (args.length != 2) {
          return false;
        }
        Integer limit = Integer.parseInt(args[1]);
        Map<String, Integer> rank = new HashMap<>();
        World world = getServer().getWorld(args[0]);
        Chunk[] clist = world.getLoadedChunks();
        String key;
        Integer value;
        for (Chunk c : clist) {
          key = String.valueOf(c.getX() * 16) + "," + String.valueOf(c.getZ() * 16);
          value = c.getEntities().length;
          if (rank.get(key) != null) {
            value = value + rank.get(key);
          }
          rank.put(key, value);
        }
        // List 生成 (ソート用)
        List<Map.Entry<String, Integer>> entries = getEntrySortedList(rank);
        // 内容を表示
        EntityAnalyzerUtility.sendMessage(sender, "LOADED CHUNK COUNT:" + clist.length);
        Integer n = 0;
        for (Entry<String, Integer> s : entries) {
          n++;
          if (n > limit) {
            break;
          }
          EntityAnalyzerUtility.sendMessage(sender,
              "POSITION:" + s.getKey() + " ENTITY_COUNT:" + String.valueOf(s.getValue()));
        }
      } else if (command.getName().equals("eatrank")) {
        if (args.length != 2) {
          return false;
        }
        Integer limit = Integer.parseInt(args[1]);
        Map<String, Integer> rank = new HashMap<>();
        World world = getServer().getWorld(args[0]);
        Chunk[] clist = world.getLoadedChunks();
        String key;
        Integer value;
        for (Chunk c : clist) {
          key = String.valueOf(c.getX() * 16) + "," + String.valueOf(c.getZ() * 16);
          value = c.getTileEntities().length;
          if (rank.get(key) != null) {
            value = value + rank.get(key);
          }
          rank.put(key, value);
        }
        // List 生成 (ソート用)
        List<Map.Entry<String, Integer>> entries = getEntrySortedList(rank);
        // 内容を表示
        EntityAnalyzerUtility.sendMessage(sender, "LOADED CHUNK COUNT:" + clist.length);
        Integer n = 0;
        for (Entry<String, Integer> s : entries) {
          n++;
          if (n > limit) {
            break;
          }
          EntityAnalyzerUtility.sendMessage(sender,
              "POSITION:" + s.getKey() + " TILE_ENTITY_COUNT:" + String.valueOf(s.getValue()));
        }
      } else if (command.getName().equals("eathrank")) {
        if (args.length != 2) {
          return false;
        }
        Integer limit = Integer.parseInt(args[1]);
        Map<String, Integer> rank = new HashMap<>();
        World world = getServer().getWorld(args[0]);
        Chunk[] clist = world.getLoadedChunks();
        String key;
        Integer value;
        for (Chunk c : clist) {
          key = String.valueOf(c.getX() * 16) + "," + String.valueOf(c.getZ() * 16);
          Integer cntTe = 0;
          for (BlockState te : c.getTileEntities()) {
            if (te.getType() == Material.HOPPER || te.getType() == Material.HOPPER_MINECART) {
              cntTe++;
            }
          }
          value = cntTe;
          if (rank.get(key) != null) {
            value = value + rank.get(key);
          }
          rank.put(key, value);
        }
        // List 生成 (ソート用)
        List<Map.Entry<String, Integer>> entries = getEntrySortedList(rank);
        // 内容を表示
        EntityAnalyzerUtility.sendMessage(sender, "LOADED CHUNK COUNT:" + clist.length);
        Integer n = 0;
        for (Entry<String, Integer> s : entries) {
          n++;
          if (n > limit) {
            break;
          }
          EntityAnalyzerUtility.sendMessage(sender,
              "POSITION:" + s.getKey() + " TILE_ENTITY_COUNT:" + String.valueOf(s.getValue()));
        }
      } else if (command.getName().equals("ealimits")) {
        FileConfiguration config = getConfig();
        String le = config.getString("limit_entity");
        String dcr = config.getString("detect_chunk_range");
        EntityAnalyzerUtility.sendMessage(sender, "Limit Entity: " + le + " Detect Chunk Range: " + dcr);
      } else if (command.getName().equals("eanear")) {
        if (!sender.isOp()) {
          EntityAnalyzerUtility.sendMessage(sender, "権限がありません。");
          return false;
        }
        if (!(sender instanceof Player)) {
          EntityAnalyzerUtility.sendMessage(sender, "Console can't this command.");
          return false;
        }
        FileConfiguration config = getConfig();
        Integer limit_entity = config.getInt("limit_entity");
        Integer detectChunkRange = config.getInt("detect_chunk_range");
        Player p = (Player) sender;
        for (Chunk c : p.getWorld().getLoadedChunks()) {
          if (c.getTileEntities().length < limit_entity && c.getEntities().length < limit_entity) {
            continue;
          }
          if (getDistance(p.getLocation().getChunk().getX(), p.getLocation().getChunk().getZ(), c.getX(),
              c.getZ()) > detectChunkRange) {
            continue;
          }
          String title = "注意";
          String subtitle = "多数のエンティティーを検知しました。(x:" + (c.getX() * 16) + ",z:" + (c.getZ() * 16) + ")";
          p.sendTitle(title, subtitle, 10, 300, 20);
          String msg = " " + (detectChunkRange * 16) + "ブロック以内にエンティティーまたはタイルエンティティーが" + limit_entity
              + "以上のチャンクあります。ホッパー、チェスト、額縁等を分散するか整理をお願いします(x:" + (c.getX() * 16) + ",z:" + (c.getZ() * 16)
              + ")";
          p.sendMessage(ChatColor.DARK_GRAY + msg);
          getLogger().info(msg + " " + p.getName());
        }
      } else if (command.getName().equals("ealimitset")) {
        if (!sender.isOp()) {
          EntityAnalyzerUtility.sendMessage(sender, "権限がありません。");
          return false;
        }
        if (args.length != 2) {
          return false;
        }
        FileConfiguration config = getConfig();
        String set_target = String.valueOf(args[0]);
        if (!set_target.equals("limit") && !set_target.equals("chunk")) {
          EntityAnalyzerUtility.sendMessage(sender, "First arg is limit or chunk. (" + set_target + ")");
          return false;
        }
        Integer limit = Integer.parseInt(args[1]);
        if (set_target.equals("limit")) {
          Integer upper_limit = config.getInt("lower_limit_entity");
          // 小さすぎる値を排除。過剰反応防止の為
          if (limit < upper_limit) {
            EntityAnalyzerUtility.sendMessage(sender,
                "Second arg over upper limit! limit is : " + String.valueOf(upper_limit));
            return false;
          }
          EntityAnalyzerUtility.sendMessage(sender, "Update Limit Entity. -> (" + limit + ")");
          config.set("limit_entity", limit);
        } else if (set_target.equals("chunk")) {
          Integer upper_limit = config.getInt("upper_limit_detect_chunk_range");
          // 大きすぎる値を排除。過剰探索防止の為
          if (limit > upper_limit) {
            EntityAnalyzerUtility.sendMessage(sender,
                "Second arg over upper limit! limit is : " + String.valueOf(upper_limit));
            return false;
          }
          EntityAnalyzerUtility.sendMessage(sender, "Update Detect Chunk Range. -> (" + limit + ")");
          config.set("detect_chunk_range", limit);
        }
        // コンフィグリロード
        saveConfig();
        reloadConfig();
      }
      return true;
    } catch (Exception e) {
      EntityAnalyzerUtility.logStackTrace(e);
    }
    return false;
  }

  public List<Map.Entry<String, Integer>> getEntrySortedList(Map<String, Integer> rank) {
    List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(rank.entrySet());
    Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
      @Override
      public int compare(
          Entry<String, Integer> entry1, Entry<String, Integer> entry2) {
        return ((Integer) entry2.getValue()).compareTo((Integer) entry1.getValue());
      }
    });
    return entries;
  }

  public int getDistance(int x1, int y1, int x2, int y2) {
    return (int) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
  }

  public double getDistance(double x1, double z1, double x2, double z2) {
    return Math.sqrt(Math.pow((x2 - x1), 2) + Math.pow((z2 - z1), 2));
  }

  /**
   * JavaPlugin method onDisable.
   */
  @Override
  public void onDisable() {
    try {
      getLogger().info("The Plugin Has Been Disabled!");
    } catch (Exception e) {
      EntityAnalyzerUtility.logStackTrace(e);
    }
  }
}
