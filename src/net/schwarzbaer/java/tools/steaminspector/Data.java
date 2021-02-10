package net.schwarzbaer.java.tools.steaminspector;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.imageio.ImageIO;
import javax.swing.Icon;

import net.schwarzbaer.gui.IconSource;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.JSON_Array;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.JSON_Object;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.TraverseException;
import net.schwarzbaer.java.lib.jsonparser.JSON_Parser;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.MainTreeContextMenue.FilterOption;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.FileNameNExt;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.TreeIcons;
import net.schwarzbaer.java.tools.steaminspector.VDFParser.VDFTreeNode;

class Data {

	@SuppressWarnings("unused")
	private static class DevHelper {
		
		static class ExtHashMap<TypeType> extends HashMap<String,HashSet<TypeType>> {
			private static final long serialVersionUID = -3042424737957471534L;
			DevHelper.ExtHashMap<TypeType> add(String name, TypeType type) {
				HashSet<TypeType> hashSet = get(name);
				if (hashSet==null) put(name,hashSet = new HashSet<>());
				hashSet.add(type);
				return this;
			}
			boolean contains(String name, TypeType type) {
				HashSet<TypeType> hashSet = get(name);
				return hashSet!=null && hashSet.contains(type);
			}
		}
		static class KnownJsonValues extends DevHelper.ExtHashMap<JSON_Data.Value.Type> {
			private static final long serialVersionUID = 875837641187739890L;
			@Override DevHelper.KnownJsonValues add(String name, JSON_Data.Value.Type type) { super.add(name, type); return this; }
		}
		static class KnownVdfValues extends DevHelper.ExtHashMap<VDFTreeNode.Type> {
			private static final long serialVersionUID = -8137083046811709725L;
			@Override DevHelper.KnownVdfValues add(String name, VDFTreeNode.Type type) { super.add(name, type); return this; }
		}
		
		static final DevHelper.OptionalValues optionalValues = new OptionalValues();
		static class OptionalValues extends HashMap<String,HashMap<String,HashSet<JSON_Data.Value.Type>>> {
			private static final long serialVersionUID = 3844179176678445499L;
	
			void scan(JSON_Object<Data.NV,Data.V> object, String prefixStr) {
				
				HashMap<String, HashSet<JSON_Data.Value.Type>> valueMap = get(prefixStr);
				boolean valueMapIsNew = false;
				if (valueMap==null) {
					valueMapIsNew = true;
					put(prefixStr, valueMap=new HashMap<>());
				}
				
				valueMap.forEach((name,types)->{
					JSON_Data.Value<Data.NV, Data.V> value = object.getValue(name);
					if (value==null) types.add(null);
				});
				
				for (JSON_Data.NamedValue<Data.NV,Data.V> nvalue:object) {
					HashSet<JSON_Data.Value.Type> types = valueMap.get(nvalue.name);
					if (types==null) {
						valueMap.put(nvalue.name, types=new HashSet<>());
						if (!valueMapIsNew) types.add(null);
					}
					types.add(nvalue.value.type);
				}
			}
			
			void show(PrintStream out) {
				if (isEmpty()) return;
				Vector<String> prefixStrs = new Vector<>(keySet());
				prefixStrs.sort(null);
				out.printf("Optional Values: [%d blocks]%n", prefixStrs.size());
				for (String prefixStr:prefixStrs) {
					HashMap<String, HashSet<JSON_Data.Value.Type>> valueMap = get(prefixStr);
					Vector<String> names = new Vector<>(valueMap.keySet());
					names.sort(null);
					out.printf("   Block \"%s\" [%d]%n", prefixStr, names.size());
					for (String name:names) {
						HashSet<JSON_Data.Value.Type> typeSet = valueMap.get(name);
						Vector<JSON_Data.Value.Type> types = new Vector<>(typeSet);
						types.sort(Comparator.nullsLast(Comparator.naturalOrder()));
						for (JSON_Data.Value.Type type:types)
							out.printf("      %s%s%n", name, type==null ? " == <null>" : ":"+type);
					}
				}
					
			}
		}
		
		static void scanUnexpectedValues(JSON_Object<Data.NV,Data.V> object, DevHelper.KnownJsonValues knownValues, String prefixStr) {
			for (JSON_Data.NamedValue<Data.NV,Data.V> nvalue:object)
				if (!knownValues.contains(nvalue.name, nvalue.value.type))
					//DevHelper.unknownValues.add(prefixStr+"."+nvalue.name+" = "+nvalue.value.type+"...");
					unknownValues.add(prefixStr,nvalue.name,nvalue.value.type);
		}
		static void scanUnexpectedValues(VDFTreeNode node, DevHelper.KnownVdfValues knownValues, String prefixStr) {
			node.forEach((subNode,t,n,v) -> {
				if (!knownValues.contains(n,t))
					unknownValues.add(prefixStr, n, t);
			});
		}
	
		static final DevHelper.UnknownValues unknownValues = new UnknownValues();
		static class UnknownValues extends HashSet<String> {
			private static final long serialVersionUID = 7229990445347378652L;
			
			void add(String baseLabel, String name, VDFTreeNode.Type type) {
				if (name==null) add(String.format("[VDF]%s:%s"   , baseLabel,       type==null?"<null>":type));
				else            add(String.format("[VDF]%s.%s:%s", baseLabel, name, type==null?"<null>":type));
			}
			void add(String baseLabel, String name, JSON_Data.Value.Type type) {
				if (name==null) add(String.format("[JSON]%s:%s"   , baseLabel,       type==null?"<null>":type));
				else            add(String.format("[JSON]%s.%s:%s", baseLabel, name, type==null?"<null>":type));
			}
			void show(PrintStream out) {
				if (isEmpty()) return;
				Vector<String> vec = new Vector<>(this);
				out.printf("Unknown Labels: [%d]%n", vec.size());
				vec.sort(null);
				for (String str:vec)
					out.printf("   \"%s\"%n", str);
			}
		}
		
		static void scanVdfStructure(VDFTreeNode node, String nodeLabel) {
			node.forEach((node1,t,n,v) -> {
				unknownValues.add(nodeLabel, n, t);
				if (t==VDFTreeNode.Type.Array)
					scanVdfStructure(node1, nodeLabel+"."+n);
			});
		}
		
		static void scanJsonStructure(JSON_Data.Value<Data.NV, Data.V> value, String valueLabel) {
			if (value==null) { unknownValues.add(valueLabel+" = <null>"); return; }
			unknownValues.add(valueLabel+":"+value.type);
			switch (value.type) {
			case Bool: case Float: case Integer: case Null: case String: break;
			case Object:
				JSON_Data.ObjectValue<Data.NV,Data.V> objectValue = value.castToObjectValue();
				if (objectValue==null)
					unknownValues.add(valueLabel+":"+value.type+" is not instance of JSON_Data.ObjectValue");
				else 
					scanJsonStructure(objectValue.value, valueLabel);
				break;
			case Array:
				JSON_Data.ArrayValue<Data.NV,Data.V> arrayValue = value.castToArrayValue();
				if (arrayValue==null)
					unknownValues.add(valueLabel+":"+value.type+" is not instance of JSON_Data.ArrayValue");
				else
					scanJsonStructure(arrayValue.value, valueLabel);
				break;
			}
		}
	
		static void scanJsonStructure(JSON_Object<Data.NV,Data.V> object, String valueLabel) {
			if (object==null)
				unknownValues.add(valueLabel+" (JSON_Object == <null>)");
			else
				for (JSON_Data.NamedValue<Data.NV, Data.V> nval:object)
					scanJsonStructure(nval.value, valueLabel+"."+(nval.name==null?"<null>":nval.name));
		}
	
		static void scanJsonStructure(JSON_Array<Data.NV,Data.V> array, String valueLabel) {
			if (array==null)
				unknownValues.add(valueLabel+" (JSON_Array == <null>)");
			else
				for (JSON_Data.Value<Data.NV,Data.V> val:array)
					scanJsonStructure(val, valueLabel+"[]");
		}
	
		static void scanJsonStructure_OAO(
				JSON_Data.Value<Data.NV,Data.V> baseValue,
				String baseValueLabel,
				String subArrayName,
				Vector<String> knownValueNames,
				Vector<String> knownSubArrayValueNames,
				String errorPrefix,
				File file
		) {
			JSON_Object<Data.NV,Data.V> object = null;
			try { object = JSON_Data.getObjectValue(baseValue, errorPrefix); }
			catch (TraverseException e) { Data.showException(e, file); }
			if (object!=null) {
				for (JSON_Data.NamedValue<Data.NV,Data.V> nvalue:object) {
					String valueStr = nvalue.value.type+"...";
					if (!knownValueNames.contains(nvalue.name)) valueStr = nvalue.value.toString();
					unknownValues.add(baseValueLabel+"."+nvalue.name+" = "+valueStr);
					if (subArrayName.equals(nvalue.name)) {
						JSON_Array<Data.NV,Data.V> array = null;
						try { array = JSON_Data.getArrayValue(nvalue.value, errorPrefix+"."+subArrayName); }
						catch (TraverseException e) { Data.showException(e, file); }
						if (array!=null) {
							for (int i=0; i<array.size(); i++) {
								JSON_Object<Data.NV, Data.V> object1 = null;
								try { object1 = JSON_Data.getObjectValue(array.get(i), errorPrefix+"."+subArrayName+"["+i+"]"); }
								catch (TraverseException e) { Data.showException(e, file); }
								if (object1!=null) {
									for (JSON_Data.NamedValue<Data.NV, Data.V> nvalue1:object1) {
										valueStr = nvalue1.value.type+"...";
										if (!knownSubArrayValueNames.contains(nvalue1.name)) valueStr = nvalue1.value.toString();
										unknownValues.add(baseValueLabel+"."+"rgCards."+nvalue1.name+" = "+valueStr);
									}
								}
							}
						}
					}
				}
			}
		}
	
		static Vector<String> strList(String...strings) {
			return new Vector<>(Arrays.asList(strings));
		}
	}
	
	static class NV extends JSON_Data.NamedValueExtra.Dummy {}
	static class V implements JSON_Data.ValueExtra {
		
		public final JSON_Data.Value.Type type;
		public JSON_Data.Value<Data.NV,Data.V> host;
		public boolean wasProcessed;
		public Boolean hasUnprocessedChildren;
		
		public V(JSON_Data.Value.Type type) {
			this.type = type;
			this.host = null; 
			wasProcessed = false;
			hasUnprocessedChildren = type!=null && type.isSimple ? false : null;
		}
		void setHost(JSON_Data.Value<Data.NV,Data.V> host) {
			this.host = host;
			if (this.host==null) new IllegalArgumentException("Host must not be <null>");
			if (this.host.type!=type) new IllegalArgumentException("Host has wrong type: "+this.host.type+"!="+type);
		}
		
		@Override public void markAsProcessed() {
			wasProcessed = true;
		}
		
		public boolean hasUnprocessedChildren() {
			// ArrayValue   @Override public boolean hasUnprocessedChildren() { return JSON_Data.hasUnprocessedChildren(this,this.value, v-> v      ); }
			// ObjectValue  @Override public boolean hasUnprocessedChildren() { return JSON_Data.hasUnprocessedChildren(this,this.value,nv->nv.value); }
			if (type==JSON_Data.Value.Type.Array ) {
				if (host==null)
					throw new IllegalStateException();
				if (host.castToArrayValue()==null)
					throw new IllegalStateException();
				return hasUnprocessedChildren(host,host.castToArrayValue ().value, v-> v      );
			}
			if (type==JSON_Data.Value.Type.Object) {
				if (host==null)
					throw new IllegalStateException();
				if (host.castToObjectValue()==null)
					throw new IllegalStateException();
				return hasUnprocessedChildren(host,host.castToObjectValue().value,nv->nv.value);
			}
			return false;
		}
		
		private static <ChildType> boolean hasUnprocessedChildren(JSON_Data.Value<Data.NV,Data.V> baseValue, Vector<ChildType> children, Function<ChildType,JSON_Data.Value<Data.NV,Data.V>> getValue) {
			if (baseValue.extra.hasUnprocessedChildren!=null) return baseValue.extra.hasUnprocessedChildren;
			baseValue.extra.hasUnprocessedChildren=false;
			for (ChildType child:children) {
				JSON_Data.Value<Data.NV,Data.V> childValue = getValue.apply(child);
				if (!childValue.extra.wasProcessed || childValue.extra.hasUnprocessedChildren()) {
					baseValue.extra.hasUnprocessedChildren=true;
					break;
				}
			}
			return baseValue.extra.hasUnprocessedChildren;
		}
	}
	
	private static class VDFTraverseException extends Exception {
		private static final long serialVersionUID = -7150324499542307039L;
		VDFTraverseException(String format, Object...args) {
			super(String.format(Locale.ENGLISH, format, args));
		}
	}

	static void showException(JSON_Data.TraverseException e, File file) { showException("JSON_Data.TraverseException", e, file); }
	static void showException(JSON_Parser.ParseException  e, File file) { showException("JSON_Parser.ParseException", e, file); }
	static void showException(VDFParser.ParseException    e, File file) { showException("VDFParser.ParseException", e, file); }
	static void showException(Data.VDFTraverseException        e, File file) { showException("VDFTraverseException", e, file); }

	static void showException(String prefix, Throwable e, File file) {
		String str = String.format("%s: %s%n", prefix, e.getMessage());
		if (file!=null) str += String.format("   in File \"%s\"%n", file.getAbsolutePath());
		System.err.print(str);
	}
	
	static final Data.KnownGameTitles knownGameTitles = new KnownGameTitles();
	static class KnownGameTitles extends HashMap<Integer,String> {
		private static final long serialVersionUID = 2599578502526459790L;
		
		void readFromFile() {
			try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(SteamInspector.KNOWN_GAME_TITLES_INI), StandardCharsets.UTF_8))) {
				
				clear();
				String line;
				while ( (line=in.readLine())!=null ) {
					int pos = line.indexOf('=');
					if (pos>0) {
						String gameIDStr = line.substring(0, pos);
						String gameTitle = line.substring(pos+1);
						Integer gameID = parseNumber(gameIDStr);
						if (gameID!=null)
							put(gameID,gameTitle);
					}
				}
				
			} catch (FileNotFoundException e) {
			} catch (IOException e) {
				System.err.printf("IOException while reading KnownGameTitles from file: %s%n", SteamInspector.KNOWN_GAME_TITLES_INI);
				e.printStackTrace();
			}
		}
		void writeToFile() {
			try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(SteamInspector.KNOWN_GAME_TITLES_INI), StandardCharsets.UTF_8))) {
				
				Vector<Integer> gameIDs = new Vector<>(this.keySet());
				gameIDs.sort(null);
				for (Integer gameID:gameIDs) {
					String title = get(gameID);
					if (gameID!=null && title!=null)
						out.printf("%s=%s%n", gameID, title);
				}
				
			} catch (FileNotFoundException e) {}
		}
	}

	static final HashMap<Integer,Data.Game> games = new HashMap<>();
	static final HashMap<Long,Data.Player> players = new HashMap<>();
	static void loadData() {
		DevHelper.unknownValues.clear();
		DevHelper.optionalValues.clear();
		Data.knownGameTitles.readFromFile();
		
		File folder = SteamInspector.KnownFolders.getSteamClientSubFolder(SteamInspector.KnownFolders.SteamClientSubFolders.APPCACHE_LIBRARYCACHE);
		Data.GameImages gameImages = null;
		if (folder!=null && folder.isDirectory())
			gameImages = new GameImages(folder);
		
		HashMap<Integer,Data.AppManifest> appManifests = new HashMap<>();
		SteamInspector.KnownFolders.forEachSteamAppsFolder((i,f)->{
			if (f!=null && f.isDirectory()) {
				File[] files = f.listFiles(file->AppManifest.getAppIDFromFile(file)!=null);
				for (File file:files) {
					Integer appID = AppManifest.getAppIDFromFile(file);
					appManifests.put(appID, new AppManifest(appID,file));
				}
			}
		});
		
		players.clear();
		folder = SteamInspector.KnownFolders.getSteamClientSubFolder(SteamInspector.KnownFolders.SteamClientSubFolders.USERDATA);
		if (folder!=null) {
			File[] files = folder.listFiles(file->file.isDirectory() && parseLongNumber(file.getName())!=null);
			for (File playerFolder:files) {
				Long playerID = parseLongNumber(playerFolder.getName());
				if (playerID==null) throw new IllegalStateException();
				players.put(playerID, new Player(playerID,playerFolder));
			}
		}
		
		// collect all AppIDs
		HashSet<Integer> idSet = new HashSet<>();
		idSet.addAll(appManifests.keySet());
		if (gameImages!=null)
			idSet.addAll(gameImages.getGameIDs());
		players.forEach((playerID,player)->{
			idSet.addAll(player.steamCloudFolders.keySet());
			if (player.screenShots!=null)
				idSet.addAll(player.screenShots.keySet());
		});
		
		
		games.clear();
		for (Integer appID:idSet) {
			HashMap<String, File> imageFiles = gameImages==null ? null : gameImages.getImageFileMap(appID);
			Data.AppManifest appManifest = appManifests.get(appID);
			games.put(appID, new Game(appID, appManifest, imageFiles, players));
		}
		
		for (Data.Game game:games.values())
			if (game.title!=null)
				Data.knownGameTitles.put(game.appID, game.title);
		Data.knownGameTitles.writeToFile();
		
		DevHelper.unknownValues.show(System.err);
		DevHelper.optionalValues.show(System.err);
	}
	static String getPlayerName(Long playerID) {
		if (playerID==null) return "Player ???";
		Data.Player game = players.get(playerID);
		if (game==null) return "Player "+playerID;
		return game.getName();
	}
	static Icon getGameIcon(Integer gameID, TreeIcons defaultIcon) {
		if (gameID==null) return defaultIcon==null ? null : defaultIcon.getIcon();
		Data.Game game = games.get(gameID);
		if (game==null) return defaultIcon==null ? null : defaultIcon.getIcon();
		return game.getIcon();
	}
	static String getGameTitle(Integer gameID) {
		if (gameID==null) return "Game ???";
		Data.Game game = games.get(gameID);
		if (game==null) return "Game "+gameID;
		return game.getTitle();
	}
	static boolean hasGameATitle(Integer gameID) {
		if (gameID==null) return false;
		Data.Game game = games.get(gameID);
		if (game==null) return false;
		return game.hasATitle();
	}
	static Comparator<Integer> createGameIdOrder() {
		//return Comparator.<Integer,Game>comparing(games::get,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Comparator.naturalOrder());
		
		Function<Integer,String> getTitle = gameID->{ Data.Game game = games.get(gameID); return game==null ? null : game.title; };
		return Comparator.<Integer,String>comparing(getTitle,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Comparator.naturalOrder());
		
		//return Comparator.<Integer,Boolean>comparing(id->!hasGameATitle(id)).thenComparing(Comparator.naturalOrder());
	}
	static <ValueType> Comparator<Map.Entry<Integer,ValueType>> createGameIdKeyOrder() {
		return Comparator.comparing(Map.Entry<Integer,ValueType>::getKey, createGameIdOrder());
	}
	static <ValueType> Comparator<Map.Entry<Long,ValueType>> createPlayerIdKeyOrder() {
		return Comparator.comparing(Map.Entry<Long,ValueType>::getKey);
	}
	
	static Integer parseNumber(String name) {
		try {
			int n = Integer.parseInt(name);
			if (name.equals(Integer.toString(n))) return n;
		}
		catch (NumberFormatException e) {}
		return null;
	}
	
	static Long parseLongNumber(String name) {
		try {
			long n = Long.parseLong(name);
			if (name.equals(Long.toString(n))) return n;
		}
		catch (NumberFormatException e) {}
		return null;
	}
	
	static class Player {
		
		final long playerID;
		final File folder;
		final File configFolder;
		final File gameStateFolder;
		final File localconfigFile;
		final HashMap<Integer, File> steamCloudFolders;
		final Data.ScreenShotLists screenShots;
		final VDFTreeNode localconfig;
		final HashMap<Integer,Player.GameInfos> gameInfos;
		final Player.AchievementProgress achievementProgress;
		final Player.FriendList friends;

		Player(long playerID, File folder) {
			this.playerID = playerID;
			this.folder = folder;
			
			File[] gameNumberFolders = folder.listFiles(file->file.isDirectory() && parseNumber(file.getName())!=null);
			steamCloudFolders = new HashMap<Integer,File>();
			for (File subFolder:gameNumberFolders) {
				String name = subFolder.getName();
				if (!name.equals("7") && !name.equals("760")) {
					Integer gameID = parseNumber(name);
					steamCloudFolders.put(gameID, subFolder);
				}
			}
			File subFolder;
			
			// Folders
			subFolder = new File(folder,"760");
			screenShots = !subFolder.isDirectory() ? null : new ScreenShotLists(subFolder);
			
			subFolder = new File(folder,"config");
			if (subFolder.isDirectory()) {
				configFolder = subFolder;
			} else
				configFolder = null;
			
			File localconfigFile = null;
			if (configFolder!=null)
				localconfigFile = new File(configFolder,"localconfig.vdf");
			
			// localconfig
			VDFParser.Data localconfigData = null;
			if (localconfigFile!=null && localconfigFile.isFile()) {
				try { localconfigData = VDFParser.parse(localconfigFile,StandardCharsets.UTF_8); }
				catch (VDFParser.ParseException e) { showException(e, localconfigFile); }
			}
			if (localconfigData!=null) {
				this.localconfig = localconfigData.createVDFTreeNode();
				this.localconfigFile = localconfigFile;
			} else {
				this.localconfig = null;
				this.localconfigFile = null;
			}
			
			Player.FriendList preFriends = null;
			if (localconfig!=null) {
				VDFTreeNode friendsNode = localconfig.getSubNode("UserLocalConfigStore","friends");
				if (friendsNode!=null) {
					try { preFriends = FriendList.parse(friendsNode,playerID); }
					catch (Data.VDFTraverseException e) { showException(e, localconfigFile); }
					if (preFriends==null)
						preFriends = new FriendList(friendsNode);
				}
			}
			friends = preFriends;
			
			gameInfos = new HashMap<>();
			Player.AchievementProgress preAchievementProgress = null;
			if (configFolder!=null) {
				File gameStateFolder = new File(configFolder,"librarycache");
				if (gameStateFolder.isDirectory()) {
					this.gameStateFolder = gameStateFolder;
					File[] files = gameStateFolder.listFiles(file->file.isFile());
					for (File file:files) {
						FileNameNExt fileNameNExt = FileNameNExt.create(file.getName());
						if (fileNameNExt.extension!=null && fileNameNExt.extension.equalsIgnoreCase("json")) {
							Integer gameID;
							if (fileNameNExt.name.equalsIgnoreCase("achievement_progress")) {
								// \config\librarycache\achievement_progress.json
								JSON_Data.Value<Data.NV, Data.V> result = null;
								try { result = JSONHelper.parseJsonFile(file); }
								catch (JSON_Parser.ParseException e) { showException(e, file); }
								if (result!=null) {
									try {
										preAchievementProgress = new AchievementProgress(file,JSON_Data.getObjectValue(result, "AchievementProgress"));
									} catch (TraverseException e) {
										showException(e, file);
										preAchievementProgress = new AchievementProgress(file,result);
									}
								}
								
							} else if ((gameID=parseNumber(fileNameNExt.name))!=null) {
								// \config\librarycache\1465680.json
								JSON_Data.Value<Data.NV, Data.V> result = null;
								try { result = JSONHelper.parseJsonFile(file); }
								catch (JSON_Parser.ParseException e) { showException(e, file); }
								if (result!=null) {
									try {
										gameInfos.put(gameID, new GameInfos(file,result));
									} catch (TraverseException e) {
										showException(e, file);
										gameInfos.put(gameID, GameInfos.createRawData(file,result));
									}
								}
							}
						}
					}
				} else
					this.gameStateFolder = null;
			} else
				this.gameStateFolder = null;
			achievementProgress = preAchievementProgress;
			
		}

		public String getName() {
			if (localconfig != null) {
				VDFTreeNode nameNode = localconfig.getSubNode("UserLocalConfigStore","friends","PersonaName");
				if (nameNode!=null && nameNode.value!=null && !nameNode.value.isEmpty())
					return nameNode.value;
			}
			return "Player "+playerID;
		}
		
		static class FriendList {
			
			//static final HashSet<String> unknownValueNames = new HashSet<>();
			
			final VDFTreeNode rawData;
			final Vector<FriendList.Friend> friends;
			final HashMap<String,String> values;
			
			FriendList() {
				rawData = null;
				friends = new Vector<>();
				values = new HashMap<>();
			}
			
			FriendList(VDFTreeNode rawData) {
				this.rawData = rawData;
				friends = null;
				values = null;
			}

			public static Player.FriendList parse(VDFTreeNode friendsNode, long playerID) throws Data.VDFTraverseException {
				if (friendsNode==null) throw new VDFTraverseException("FriendList: base VDFTreeNode is NULL");
				if (friendsNode.type!=VDFTreeNode.Type.Array) throw new VDFTraverseException("FriendList: base VDFTreeNode is not an Array");
				Player.FriendList friendList = new FriendList();
				friendsNode.forEach((subNode, type, name, value)->{
					switch (type) {
					case Root: System.err.printf("FriendList[Player %d]: Root node as sub node of base VDFTreeNode%n", playerID); break;

					case Array: // Friend
						friendList.friends.add(new Friend(name,subNode));
						break;
						
					case String: // simple value
						friendList.values.put(name, value);
						break;
					}
				});
				return friendList;
			}

			static class Friend {
				private static final DevHelper.KnownVdfValues KNOWN_VDF_VALUES = new DevHelper.KnownVdfValues()
						.add("name"  , VDFTreeNode.Type.String)
						.add("tag"   , VDFTreeNode.Type.String)
						.add("avatar", VDFTreeNode.Type.String)
						.add("NameHistory", VDFTreeNode.Type.Array);
				
				final VDFTreeNode rawData;
				final String idStr;
				final Long id;
				final String name;
				final String tag;
				final String avatar;
				final HashMap<Integer, String> nameHistory;

				public Friend(String idStr, VDFTreeNode node) {
					this.idStr = idStr;
					this.id = parseLongNumber(idStr);
					this.rawData = null;
					//this.rawData = node;
					//DevHelper.scanVdfStructure(node,"Friend");
					
					name   = node.getString("name"  );
					tag    = node.getString("tag"   );
					avatar = node.getString("avatar");
					VDFTreeNode arrayNode = node.getArray("NameHistory");
					if (arrayNode!=null) {
						nameHistory = new HashMap<Integer,String>();
						arrayNode.forEach((subNode,t,n,v) -> {
							if (t==VDFTreeNode.Type.String) {
								Integer index = parseNumber(n);
								if (index!=null && v!=null) {
									nameHistory.put(index,v);
									return;
								}
							}
							DevHelper.unknownValues.add("Friend.NameHistory", n, t);
						});
					} else
						nameHistory = null;
					
					DevHelper.scanUnexpectedValues(node, KNOWN_VDF_VALUES, "TreeNodes.Data.Player.FriendList.Friend");
				}
			}
		}
		
		static class AchievementProgress {
			private static final DevHelper.KnownJsonValues KNOWN_JSON_VALUES = new DevHelper.KnownJsonValues()
					.add("nVersion", JSON_Data.Value.Type.Integer)
					.add("mapCache", JSON_Data.Value.Type.Object);

			final File file;
			final JSON_Data.Value<Data.NV, Data.V> rawData;
			final boolean hasParsedData;
			final JSON_Object<Data.NV, Data.V> sourceData;
			final Long version;
			final HashMap<Integer,AchievementProgress.AchievementProgressInGame> gameStates;
			final Vector<AchievementProgress.AchievementProgressInGame> gameStates_withoutID;

			AchievementProgress(File file, JSON_Data.Value<Data.NV, Data.V> rawData) {
				this.file = file;
				this.rawData = rawData;
				hasParsedData = false;
				sourceData = null;
				version    = null;
				gameStates = null;
				gameStates_withoutID = null;
			}
			AchievementProgress(File file, JSON_Object<Data.NV,Data.V> object) throws TraverseException {
				this.file = file;
				rawData = null;
				hasParsedData = true;
				sourceData = object;
				//DevHelper.scanJsonStructure(object, "AchievementProgress");
				String prefixStr = "AchievementProgress";
				if (object==null) throw new TraverseException("%s == <NULL>", prefixStr);
				version                    = JSON_Data.getIntegerValue(object, "nVersion", prefixStr);
				JSON_Object<Data.NV,Data.V> mapCache = JSON_Data.getObjectValue (object, "mapCache", prefixStr);
				gameStates = new HashMap<>();
				gameStates_withoutID = new Vector<AchievementProgress.AchievementProgressInGame>();
				for (JSON_Data.NamedValue<Data.NV,Data.V> nv:mapCache) {
					AchievementProgress.AchievementProgressInGame progress;
					try {
						progress = new AchievementProgressInGame(nv.name,nv.value);
					} catch (TraverseException e) {
						showException(e, file);
						progress = new AchievementProgressInGame(nv);
					}
					
					Integer gameID = parseNumber(nv.name);
					if (gameID==null && progress.hasParsedData)
						gameID = (int) progress.appID;
					
					if (gameID!=null)
						gameStates.put(gameID, progress);
					else
						gameStates_withoutID.add(progress);
				}
				DevHelper.scanUnexpectedValues(object, KNOWN_JSON_VALUES,"TreeNodes.Data.Player.AchievementProgress");
			}
			
			static class AchievementProgressInGame {
				private static final DevHelper.KnownJsonValues KNOWN_JSON_VALUES = new DevHelper.KnownJsonValues()
						.add("all_unlocked", JSON_Data.Value.Type.Bool)
						.add("appid"       , JSON_Data.Value.Type.Integer)
						.add("cache_time"  , JSON_Data.Value.Type.Integer)
						.add("total"       , JSON_Data.Value.Type.Integer)
						.add("unlocked"    , JSON_Data.Value.Type.Integer)
						.add("percentage"  , JSON_Data.Value.Type.Integer)
						.add("percentage"  , JSON_Data.Value.Type.Float);
				// "AchievementProgress.GameStatus.all_unlocked:Bool"
				// "AchievementProgress.GameStatus.appid:Integer"
				// "AchievementProgress.GameStatus.cache_time:Integer"
				// "AchievementProgress.GameStatus.percentage:Float"
				// "AchievementProgress.GameStatus.percentage:Integer"
				// "AchievementProgress.GameStatus.total:Integer"
				// "AchievementProgress.GameStatus.unlocked:Integer"
				// "AchievementProgress.GameStatus:Object"
				
				final JSON_Data.Value<Data.NV, Data.V> rawData;
				final String name;
				final boolean hasParsedData;
				
				final boolean allUnlocked;
				final long    appID;
				final long    cacheTime;
				final long    total;
				final long    unlocked;
				final double  percentage;

				AchievementProgressInGame(JSON_Data.NamedValue<Data.NV, Data.V> rawData) {
					this.rawData = rawData.value;
					this.name    = rawData.name;
					hasParsedData = false;
					allUnlocked = false;
					appID       = -1;
					cacheTime   = -1;
					total       = -1;
					unlocked    = -1;
					percentage  = Double.NaN;
				}

				AchievementProgressInGame(String name, JSON_Data.Value<Data.NV, Data.V> value) throws TraverseException {
					this.rawData = null;
					this.name    = name;
					hasParsedData = true;
					// DevHelper.scanJsonStructure(value, "AchievementProgress.GameStatus");
					String prefixStr = "AchievementProgress.GameStatus["+name+"]";
					JSON_Object<Data.NV,Data.V> object = JSON_Data.getObjectValue(value, prefixStr);
					allUnlocked = JSON_Data.getBoolValue   (object, "all_unlocked", prefixStr);
					appID       = JSON_Data.getIntegerValue(object, "appid"       , prefixStr);
					cacheTime   = JSON_Data.getIntegerValue(object, "cache_time"  , prefixStr);
					total       = JSON_Data.getIntegerValue(object, "total"       , prefixStr);
					unlocked    = JSON_Data.getIntegerValue(object, "unlocked"    , prefixStr);
					percentage  = JSON_Data.getNumber(object, "percentage"  , prefixStr);
					DevHelper.scanUnexpectedValues(object, KNOWN_JSON_VALUES,"TreeNodes.Data.Player.AchievementProgress.AchievementProgressInGame");
				}

				int getGameID() { return (int) appID; }
			}
		}
		
		static class GameInfos {

			static boolean meetsFilterOption(Player.GameInfos gameInfos, GameInfos.GameInfosFilterOptions option) {
				if (option==null) return true;
				if (gameInfos==null) return true;
				switch (option) {
				case RawData       : return !gameInfos.hasParsedData && gameInfos.rawData       !=null;
				case Badge         : return  gameInfos.hasParsedData && gameInfos.badge         !=null && !gameInfos.badge         .isEmpty();
				case Achievements  : return  gameInfos.hasParsedData && gameInfos.achievements  !=null && !gameInfos.achievements  .isEmpty();
				case UserNews      : return  gameInfos.hasParsedData && gameInfos.userNews      !=null && !gameInfos.userNews      .isEmpty();
				case GameActivity  : return  gameInfos.hasParsedData && gameInfos.gameActivity  !=null && !gameInfos.gameActivity  .isEmpty();
				case AchievementMap: return  gameInfos.hasParsedData && gameInfos.achievementMap!=null && !gameInfos.achievementMap.isEmpty();
				case SocialMedia   : return  gameInfos.hasParsedData && gameInfos.socialMedia   !=null && !gameInfos.socialMedia   .isEmpty();
				case Associations  : return  gameInfos.hasParsedData && gameInfos.associations  !=null && !gameInfos.associations  .isEmpty();
				case AppActivity   : return  gameInfos.hasParsedData && gameInfos.appActivity   !=null && !gameInfos.appActivity   .isEmpty();
				case ReleaseData   : return  gameInfos.hasParsedData && gameInfos.releaseData   !=null && !gameInfos.releaseData   .isEmpty();
				case Friends       : return  gameInfos.hasParsedData && gameInfos.friends       !=null && !gameInfos.friends       .isEmpty();
				case CommunityItems: return  gameInfos.hasParsedData && gameInfos.communityItems!=null && !gameInfos.communityItems.isEmpty();
				}
				return true;
			}

			enum GameInfosFilterOptions implements FilterOption {
				RawData       ("has Raw Data"),
				Badge         ("has Badge"),
				Achievements  ("has Achievements"),
				UserNews      ("has User News"),
				GameActivity  ("has Game Activity"),
				AchievementMap("has Achievement Map"),
				SocialMedia   ("has Social Media"),
				Associations  ("has Associations"),
				AppActivity   ("has App Activity"),
				ReleaseData   ("has Release Data"),
				Friends       ("has Friends"),
				CommunityItems("has Community Items"),
				;
				private final String label;
				
				GameInfosFilterOptions() { this(null); }
				GameInfosFilterOptions(String label) { this.label = label==null ? name() : label;}
				
				@Override public String toString() {
					return label;
				}

				static GameInfos.GameInfosFilterOptions cast(FilterOption obj) {
					//System.out.printf("FilterOptions.cast( [%s] obj=%s )%n", obj==null ? null : obj.getClass(), obj );
					if (obj instanceof GameInfos.GameInfosFilterOptions)
						return (GameInfos.GameInfosFilterOptions) obj;
					return null;
				}
			}

			final File file;
			final JSON_Data.Value<Data.NV, Data.V> rawData;
			final boolean hasParsedData;
			final Vector<GameInfos.Block> blocks;
			
			final String         fullDesc;
			final String         shortDesc;
			final GameInfos.Badge          badge;
			final GameInfos.Achievements   achievements;
			final GameInfos.UserNews       userNews;
			final GameInfos.GameActivity   gameActivity;
			final GameInfos.AchievementMap achievementMap;
			final GameInfos.SocialMedia    socialMedia;
			final GameInfos.Associations   associations;
			final GameInfos.AppActivity    appActivity;
			final GameInfos.ReleaseData    releaseData;
			final GameInfos.Friends        friends;
			final GameInfos.CommunityItems communityItems;

			static Player.GameInfos createRawData(File file, JSON_Data.Value<Data.NV, Data.V> rawData) {
				try { return new GameInfos(file, rawData, true); }
				catch (TraverseException e) { return null; }
			}
			
			GameInfos(File file, JSON_Data.Value<Data.NV, Data.V> fileContent) throws TraverseException {
				this(file, fileContent, false);
			}
			private GameInfos(File file, JSON_Data.Value<Data.NV, Data.V> fileContent, boolean asRawData) throws TraverseException {
				this.file = file;
				this.rawData = fileContent;
				
				if (asRawData) {
					hasParsedData = false;
					blocks        = null;
					
					fullDesc       = null;
					shortDesc      = null;
					badge          = null;
					achievements   = null;
					userNews       = null;
					gameActivity   = null;
					achievementMap = null;
					socialMedia    = null;
					associations   = null;
					appActivity    = null;
					releaseData    = null;
					friends        = null;
					communityItems = null;
					
				} else {
					hasParsedData = true;
					
					JSON_Array<Data.NV, Data.V> array = JSON_Data.getArrayValue(fileContent, "GameInfos");
					
					blocks = new Vector<>();
					for (int i=0; i<array.size(); i++) {
						JSON_Data.Value<Data.NV,Data.V> value = array.get(i);
						try {
							blocks.add(new Block(i,value));
						} catch (TraverseException e) {
							showException(e, file);
							blocks.add(Block.createRawData(i,value));
						}
					}
					
					String         preFullDesc       = null;
					String         preShortDesc      = null;
					GameInfos.Badge          preBadge          = null;
					GameInfos.Achievements   preAchievements   = null;
					GameInfos.UserNews       preUserNews       = null;
					GameInfos.GameActivity   preGameActivity   = null;
					GameInfos.AchievementMap preAchievementMap = null;
					GameInfos.SocialMedia    preSocialMedia    = null;
					GameInfos.Associations   preAssociations   = null;
					GameInfos.AppActivity    preAppActivity    = null;
					GameInfos.ReleaseData    preReleaseData    = null;
					GameInfos.Friends        preFriends        = null;
					GameInfos.CommunityItems preCommunityItems = null;
					
					for (GameInfos.Block block:blocks) {
						String dataValueStr = String.format("GameInfos.Block[%d].dataValue", block.blockIndex);
						//DevHelper.scanJsonStructure(block.dataValue,String.format("GameInfo.Block[\"%s\",V%d].dataValue", block.label, block.version));
						switch (block.label) {
							
						case "badge"          : preBadge          = parseStandardBlock( GameInfos.Badge         ::new, GameInfos.Badge         ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "achievements"   : preAchievements   = parseStandardBlock( GameInfos.Achievements  ::new, GameInfos.Achievements  ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "usernews"       : preUserNews       = parseStandardBlock( GameInfos.UserNews      ::new, GameInfos.UserNews      ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "gameactivity"   : preGameActivity   = parseStandardBlock( GameInfos.GameActivity  ::new, GameInfos.GameActivity  ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "achievementmap" : preAchievementMap = parseStandardBlock( GameInfos.AchievementMap::new, GameInfos.AchievementMap::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "socialmedia"    : preSocialMedia    = parseStandardBlock( GameInfos.SocialMedia   ::new, GameInfos.SocialMedia   ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "associations"   : preAssociations   = parseStandardBlock( GameInfos.Associations  ::new, GameInfos.Associations  ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "appactivity"    : preAppActivity    = parseStandardBlock( GameInfos.AppActivity   ::new, GameInfos.AppActivity   ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "releasedata"    : preReleaseData    = parseStandardBlock( GameInfos.ReleaseData   ::new, GameInfos.ReleaseData   ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "friends"        : preFriends        = parseStandardBlock( GameInfos.Friends       ::new, GameInfos.Friends       ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "community_items": preCommunityItems = parseStandardBlock( GameInfos.CommunityItems::new, GameInfos.CommunityItems::new, block.dataValue, block.version, dataValueStr, file ); break;
							
						case "descriptions":
							JSON_Object<Data.NV, Data.V> object = null;
							try { object = JSON_Data.getObjectValue(block.dataValue, dataValueStr); }
							catch (TraverseException e) { showException(e, file); }
							if (object!=null) {
								try { preFullDesc  = JSON_Data.getStringValue(object, "strFullDescription", dataValueStr); }
								catch (TraverseException e) { showException(e, file); }
								try { preShortDesc = JSON_Data.getStringValue(object, "strSnippet", dataValueStr); }
								catch (TraverseException e) { showException(e, file); }
							}
							break;
							
						case "workshop":
							//DevHelper.scanJsonStructure(block.dataValue,String.format("GameInfo.Block[\"%s\",V%d].dataValue", block.label, block.version));
							break;
							
						default:
							DevHelper.unknownValues.add("GameInfos.Block["+block.label+"] - Unknown Block");
						}
					}
					fullDesc       = preFullDesc      ;
					shortDesc      = preShortDesc     ;
					badge          = preBadge         ;
					achievements   = preAchievements  ;
					userNews       = preUserNews      ;
					gameActivity   = preGameActivity  ;
					achievementMap = preAchievementMap;
					socialMedia    = preSocialMedia   ;
					associations   = preAssociations  ;
					appActivity    = preAppActivity   ;
					releaseData    = preReleaseData   ;
					friends        = preFriends       ;
					communityItems = preCommunityItems;
				}
			}
			
			private static <ClassType> ClassType parseStandardBlock(
					GameInfos.ParseConstructor<ClassType> parseConstructor,
					BiFunction<JSON_Data.Value<Data.NV,Data.V>,Long,ClassType> rawDataConstructor,
					JSON_Data.Value<Data.NV, Data.V> blockDataValue, long version, String dataValueStr, File file
			) {
				try {
					return parseConstructor.parse(blockDataValue, version, dataValueStr, file);
				} catch (TraverseException e) {
					showException(e, file);
					return rawDataConstructor.apply(blockDataValue, version);
				}
			}
			
			interface ParseConstructor<ClassType> {
				ClassType parse(JSON_Data.Value<Data.NV, Data.V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException;
			}
			
			static class ParsedBlock {
				
				final JSON_Data.Value<Data.NV, Data.V> rawData;
				final boolean hasParsedData;
				final long version;
				
				ParsedBlock(JSON_Data.Value<Data.NV, Data.V> rawData, long version, boolean hasParsedData) {
					this.rawData = rawData;
					this.version = version;
					this.hasParsedData = hasParsedData;
				}
				
				boolean isEmpty() {
					return rawData==null;
				}
			}
			// "GameInfo.Block["community_items",V1].dataValue:Array"

			static class CommunityItems extends GameInfos.ParsedBlock {
				
				final Vector<CommunityItems.CommunityItem> items;

				CommunityItems(JSON_Data.Value<Data.NV, Data.V> rawData, long version) {
					super(rawData, version, false);
					items = null;
				}
				CommunityItems(JSON_Data.Value<Data.NV, Data.V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					
					JSON_Array<Data.NV, Data.V> array = JSON_Data.getArrayValue(blockDataValue, dataValueStr);
					items = new Vector<>();
					for (int i=0; i<array.size(); i++) {
						try {
							items.add(new CommunityItem(array.get(i), dataValueStr+"["+i+"]"));
						} catch (TraverseException e) {
							showException(e, file);
							items.add(new CommunityItem(array.get(i)));
						}
					}
					
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData || items.isEmpty());
				}

				static class CommunityItem {
					
					// Block "TreeNodes.Data.Player.GameInfos.CommunityItems.CommunityItem" [18]
					//    active:Bool
					//    appid:Integer
					//    item_class:Integer
					//    item_description:String
					//    item_image_composed:String
					//    item_image_composed == <null>
					//    item_image_composed_foil:String
					//    item_image_composed_foil == <null>
					//    item_image_large:String
					//    item_image_small:String
					//    item_key_values:String
					//    item_key_values == <null>
					//    item_last_changed:Integer
					//    item_movie_mp4:String
					//    item_movie_mp4 == <null>
					//    item_movie_mp4_small:String
					//    item_movie_mp4_small == <null>
					//    item_movie_webm:String
					//    item_movie_webm == <null>
					//    item_movie_webm_small:String
					//    item_movie_webm_small == <null>
					//    item_name:String
					//    item_series:Integer
					//    item_title:String
					//    item_type:Integer
					
					private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues()
							.add("active"                  , JSON_Data.Value.Type.Bool   )
							.add("appid"                   , JSON_Data.Value.Type.Integer)
							.add("item_class"              , JSON_Data.Value.Type.Integer)
							.add("item_description"        , JSON_Data.Value.Type.String )
							.add("item_image_composed"     , JSON_Data.Value.Type.String ) // optional value
							.add("item_image_composed_foil", JSON_Data.Value.Type.String ) // optional value
							.add("item_image_large"        , JSON_Data.Value.Type.String )
							.add("item_image_small"        , JSON_Data.Value.Type.String )
							.add("item_key_values"         , JSON_Data.Value.Type.String ) // optional value
							.add("item_last_changed"       , JSON_Data.Value.Type.Integer)
							.add("item_movie_mp4"          , JSON_Data.Value.Type.String ) // optional value
							.add("item_movie_mp4_small"    , JSON_Data.Value.Type.String ) // optional value
							.add("item_movie_webm"         , JSON_Data.Value.Type.String ) // optional value
							.add("item_movie_webm_small"   , JSON_Data.Value.Type.String ) // optional value
							.add("item_name"               , JSON_Data.Value.Type.String )
							.add("item_series"             , JSON_Data.Value.Type.Integer)
							.add("item_title"              , JSON_Data.Value.Type.String )
							.add("item_type"               , JSON_Data.Value.Type.Integer);
					
					final JSON_Data.Value<Data.NV, Data.V> rawData;
					final boolean hasParsedData;

					final boolean isActive;
					final long    appID;
					final long    itemClass;
					final String  itemDescription;
					final String  itemImageComposed;
					final String  itemImageComposedFoil;
					final String  itemImageLarge;
					final String  itemImageSmall;
					final String  itemKeyValues;
					final long    itemLastChanged;
					final String  itemMovieMp4;
					final String  itemMovieMp4Small;
					final String  itemMovieWebm;
					final String  itemMovieWebmSmall;
					final String  itemName;
					final long    itemSeries;
					final String  itemTitle;
					final long    itemType;
					
					CommunityItem(JSON_Data.Value<Data.NV, Data.V> rawData) {
						this.rawData = rawData;
						hasParsedData = false;
						
						isActive                   = false;
						appID                    = -1;
						itemName                = null;
						itemTitle               = null;
						itemDescription         = null;
						itemClass               = -1;
						itemSeries              = -1;
						itemType                = -1;
						itemLastChanged        = -1;
						itemImageLarge         = null;
						itemImageSmall         = null;
						itemKeyValues          = null;
						itemImageComposed      = null;
						itemImageComposedFoil = null;
						itemMovieMp4           = null;
						itemMovieMp4Small     = null;
						itemMovieWebm          = null;
						itemMovieWebmSmall    = null;
					}
					CommunityItem(JSON_Data.Value<Data.NV, Data.V> value, String dataValueStr) throws TraverseException {
						this.rawData = null;
						hasParsedData = true;
						
						JSON_Object<Data.NV, Data.V> object = JSON_Data.getObjectValue(value, dataValueStr);
						//DevHelper.optionalValues.scan(object, "TreeNodes.Data.Player.GameInfos.CommunityItems.CommunityItem");
						
						isActive                   = JSON_Data.getBoolValue   (object, "active"           , dataValueStr);
						appID                    = JSON_Data.getIntegerValue(object, "appid"            , dataValueStr);
						itemName                = JSON_Data.getStringValue (object, "item_name"        , dataValueStr);
						itemTitle               = JSON_Data.getStringValue (object, "item_title"       , dataValueStr);
						itemDescription         = JSON_Data.getStringValue (object, "item_description" , dataValueStr);
						itemClass               = JSON_Data.getIntegerValue(object, "item_class"       , dataValueStr);
						itemSeries              = JSON_Data.getIntegerValue(object, "item_series"      , dataValueStr);
						itemType                = JSON_Data.getIntegerValue(object, "item_type"        , dataValueStr);
						itemLastChanged        = JSON_Data.getIntegerValue(object, "item_last_changed", dataValueStr);
						itemImageLarge         = JSON_Data.getStringValue (object, "item_image_large" , dataValueStr);
						itemImageSmall         = JSON_Data.getStringValue (object, "item_image_small" , dataValueStr);
						
						itemKeyValues          = JSON_Data.getValue(object, "item_key_values"         , true , JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, false, dataValueStr);
						itemImageComposed      = JSON_Data.getValue(object, "item_image_composed"     , true , JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, false, dataValueStr);
						itemImageComposedFoil = JSON_Data.getValue(object, "item_image_composed_foil", true , JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, false, dataValueStr);
						itemMovieMp4           = JSON_Data.getValue(object, "item_movie_mp4"          , true , JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, false, dataValueStr);
						itemMovieMp4Small     = JSON_Data.getValue(object, "item_movie_mp4_small"    , true , JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, false, dataValueStr);
						itemMovieWebm          = JSON_Data.getValue(object, "item_movie_webm"         , true , JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, false, dataValueStr);
						itemMovieWebmSmall    = JSON_Data.getValue(object, "item_movie_webm_small"   , true , JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, false, dataValueStr);
						
						DevHelper.scanUnexpectedValues(object, KNOWN_VALUES, "TreeNodes.Data.Player.GameInfos.CommunityItems.CommunityItem");
					}
					
					String getURL(String urlPart) {
						if (hasParsedData)
							return String.format("https://cdn.cloudflare.steamstatic.com/steamcommunity/public/images/items/%d/%s", appID, urlPart);
						return "";
					}
					
					public static String getClassLabel(long itemClass) {
						switch ((int)itemClass) {
						case 2: return "Trading Card";
						case 3: return "Profil Background";
						case 4: return "Emoticon";
						default: return null;
						}
					}
				}
				
			}
			static class Friends extends GameInfos.ParsedBlock {
				
				// "GameInfo.Block["friends",V1].dataValue.in_game:Array"
				// "GameInfo.Block["friends",V1].dataValue.in_wishlist:Array"
				// "GameInfo.Block["friends",V1].dataValue.in_wishlist[].steamid:String"
				// "GameInfo.Block["friends",V1].dataValue.in_wishlist[]:Object"
				// "GameInfo.Block["friends",V1].dataValue.owns:Array"
				// "GameInfo.Block["friends",V1].dataValue.owns[].steamid:String"
				// "GameInfo.Block["friends",V1].dataValue.owns[]:Object"
				// "GameInfo.Block["friends",V1].dataValue.played_ever:Array"
				// "GameInfo.Block["friends",V1].dataValue.played_ever[].minutes_played_forever:Integer"
				// "GameInfo.Block["friends",V1].dataValue.played_ever[].steamid:String"
				// "GameInfo.Block["friends",V1].dataValue.played_ever[]:Object"
				// "GameInfo.Block["friends",V1].dataValue.played_recently:Array"
				// "GameInfo.Block["friends",V1].dataValue.played_recently[].minutes_played:Integer"
				// "GameInfo.Block["friends",V1].dataValue.played_recently[].minutes_played_forever:Integer"
				// "GameInfo.Block["friends",V1].dataValue.played_recently[].steamid:String"
				// "GameInfo.Block["friends",V1].dataValue.played_recently[]:Object"
				// "GameInfo.Block["friends",V1].dataValue.your_info.minutes_played:Integer"
				// "GameInfo.Block["friends",V1].dataValue.your_info.minutes_played_forever:Integer"
				// "GameInfo.Block["friends",V1].dataValue.your_info.owned:Bool"
				// "GameInfo.Block["friends",V1].dataValue.your_info:Object"
				// "GameInfo.Block["friends",V1].dataValue:Object"
				
				// "[JSON]TreeNodes.Data.Player.GameInfos.Associations.in_game:Array"
				// "[JSON]TreeNodes.Data.Player.GameInfos.Associations.in_wishlist:Array"
				// "[JSON]TreeNodes.Data.Player.GameInfos.Associations.owns:Array"
				// "[JSON]TreeNodes.Data.Player.GameInfos.Associations.played_ever:Array"
				// "[JSON]TreeNodes.Data.Player.GameInfos.Associations.played_recently:Array"
				// "[JSON]TreeNodes.Data.Player.GameInfos.Associations.your_info:Object"
				
				private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues()
						.add("in_game"        , JSON_Data.Value.Type.Array)
						.add("in_wishlist"    , JSON_Data.Value.Type.Array)
						.add("owns"           , JSON_Data.Value.Type.Array)
						.add("played_ever"    , JSON_Data.Value.Type.Array)
						.add("played_recently", JSON_Data.Value.Type.Array)
						.add("your_info"      , JSON_Data.Value.Type.Object);
				
				final Vector<Friends.Entry> in_game;
				final Vector<Friends.Entry> in_wishlist;
				final Vector<Friends.Entry> owns;
				final Vector<Friends.Entry> played_ever;
				final Vector<Friends.Entry> played_recently;
				final Friends.Entry your_info;
				
				Friends(JSON_Data.Value<Data.NV, Data.V> rawData, long version) {
					super(rawData, version, false);
					in_game         = null;
					in_wishlist     = null;
					owns            = null;
					played_ever     = null;
					played_recently = null;
					your_info       = null;
				}
				Friends(JSON_Data.Value<Data.NV, Data.V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					
					JSON_Object<Data.NV, Data.V> object = JSON_Data.getObjectValue(blockDataValue, dataValueStr);
					
					in_game         = parseArray(object, "in_game"        , dataValueStr, file);
					in_wishlist     = parseArray(object, "in_wishlist"    , dataValueStr, file);
					owns            = parseArray(object, "owns"           , dataValueStr, file);
					played_ever     = parseArray(object, "played_ever"    , dataValueStr, file);
					played_recently = parseArray(object, "played_recently", dataValueStr, file);
					
					JSON_Data.Value<Data.NV, Data.V> your_info_value = object.getValue("your_info");
					if (your_info_value==null) your_info = null;
					else your_info = parseEntry(your_info_value, dataValueStr+".your_info", file);
					
					DevHelper.scanUnexpectedValues(object, KNOWN_VALUES, "TreeNodes.Data.Player.GameInfos.Associations");
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData || (in_game.isEmpty() && in_wishlist.isEmpty() && owns.isEmpty() && played_ever.isEmpty() && played_recently.isEmpty() && your_info==null));
				}
				
				private Vector<Friends.Entry> parseArray(JSON_Object<Data.NV, Data.V> object, String subValueName, String dataValueStr, File file) throws TraverseException {
					JSON_Array<Data.NV, Data.V> array = JSON_Data.getArrayValue(object, subValueName, dataValueStr);
					Vector<Friends.Entry> values = new Vector<>();
					for (int i=0; i<array.size(); i++)
						values.add(parseEntry(array.get(i), dataValueStr+"["+i+"]", file));
					return values;
				}
				
				private Friends.Entry parseEntry(JSON_Data.Value<Data.NV, Data.V> value, String dataValueStr, File file) {
					try {
						return new Entry(value, dataValueStr);
					} catch (TraverseException ex) {
						showException(ex, file);
						return new Entry(value);
					}
				}
				
				static class Entry {
					
					// "[JSON]TreeNodes.Data.Player.GameInfos.Friends.Entry.minutes_played:Integer"
					// "[JSON]TreeNodes.Data.Player.GameInfos.Friends.Entry.minutes_played_forever:Integer"
					// "[JSON]TreeNodes.Data.Player.GameInfos.Friends.Entry.owned:Bool"
					// "[JSON]TreeNodes.Data.Player.GameInfos.Friends.Entry.steamid:String"
					
					private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues()
							.add("minutes_played"        , JSON_Data.Value.Type.Integer)
							.add("minutes_played_forever", JSON_Data.Value.Type.Integer)
							.add("owned"                 , JSON_Data.Value.Type.Bool   )
							.add("steamid"               , JSON_Data.Value.Type.String );

					final JSON_Data.Value<Data.NV, Data.V> rawData;
					final boolean hasParsedData;
					
					final Long minutes_played;
					final Long minutes_played_forever;
					final Boolean owned;
					final String steamid;
					
					Entry(JSON_Data.Value<Data.NV, Data.V> rawData) {
						this.rawData = rawData;
						this.hasParsedData = false;
						minutes_played         = null;
						minutes_played_forever = null;
						owned                  = null;
						steamid                = null;
					}
					Entry(JSON_Data.Value<Data.NV, Data.V> value, String dataValueStr) throws TraverseException {
						this.rawData = null;
						this.hasParsedData = true;
						JSON_Object<Data.NV, Data.V> object = JSON_Data.getObjectValue(value, dataValueStr);
						//DevHelper.optionalValues.scan(object, "TreeNodes.Data.Player.GameInfos.Friends.Entry");
						minutes_played         = JSON_Data.getValue(object, "minutes_played"        , true, JSON_Data.Value.Type.Integer, JSON_Data.Value::castToIntegerValue, false, dataValueStr);
						minutes_played_forever = JSON_Data.getValue(object, "minutes_played_forever", true, JSON_Data.Value.Type.Integer, JSON_Data.Value::castToIntegerValue, false, dataValueStr);
						owned                  = JSON_Data.getValue(object, "owned"                 , true, JSON_Data.Value.Type.Bool   , JSON_Data.Value::castToBoolValue   , false, dataValueStr);
						steamid                = JSON_Data.getValue(object, "steamid"               , true, JSON_Data.Value.Type.String , JSON_Data.Value::castToStringValue , false, dataValueStr);
						DevHelper.scanUnexpectedValues(object, KNOWN_VALUES, "TreeNodes.Data.Player.GameInfos.Friends.Entry");
					}
					
				}
			}

			static class Associations extends GameInfos.ParsedBlock {
				
				// "GameStateInfo.Block["associations",V1].dataValue.rgDevelopers:Array"
				// "GameStateInfo.Block["associations",V1].dataValue.rgDevelopers[].strName:String"
				// "GameStateInfo.Block["associations",V1].dataValue.rgDevelopers[].strURL:Null"
				// "GameStateInfo.Block["associations",V1].dataValue.rgDevelopers[].strURL:String"
				// "GameStateInfo.Block["associations",V1].dataValue.rgDevelopers[]:Object"
				// "GameStateInfo.Block["associations",V1].dataValue.rgFranchises:Array"
				// "GameStateInfo.Block["associations",V1].dataValue.rgFranchises[].strName:String"
				// "GameStateInfo.Block["associations",V1].dataValue.rgFranchises[].strURL:Null"
				// "GameStateInfo.Block["associations",V1].dataValue.rgFranchises[].strURL:String"
				// "GameStateInfo.Block["associations",V1].dataValue.rgFranchises[]:Object"
				// "GameStateInfo.Block["associations",V1].dataValue.rgPublishers:Array"
				// "GameStateInfo.Block["associations",V1].dataValue.rgPublishers[].strName:Null"
				// "GameStateInfo.Block["associations",V1].dataValue.rgPublishers[].strName:String"
				// "GameStateInfo.Block["associations",V1].dataValue.rgPublishers[].strURL:Null"
				// "GameStateInfo.Block["associations",V1].dataValue.rgPublishers[].strURL:String"
				// "GameStateInfo.Block["associations",V1].dataValue.rgPublishers[]:Object"
				// "GameStateInfo.Block["associations",V1].dataValue:Object"
				
				private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues()
						.add("rgDevelopers", JSON_Data.Value.Type.Array)
						.add("rgFranchises", JSON_Data.Value.Type.Array)
						.add("rgPublishers", JSON_Data.Value.Type.Array);
				
				final Vector<Associations.Association> developers;
				final Vector<Associations.Association> franchises;
				final Vector<Associations.Association> publishers;
				
				Associations(JSON_Data.Value<Data.NV, Data.V> rawData, long version) {
					super(rawData, version, false);
					developers = null;
					franchises = null;
					publishers = null;
				}
				Associations(JSON_Data.Value<Data.NV, Data.V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					JSON_Object<Data.NV, Data.V> object = JSON_Data.getObjectValue(blockDataValue, dataValueStr);
					developers = parseArray(object, "rgDevelopers", dataValueStr, file);
					franchises = parseArray(object, "rgFranchises", dataValueStr, file);
					publishers = parseArray(object, "rgPublishers", dataValueStr, file);
					DevHelper.scanUnexpectedValues(object, KNOWN_VALUES, "TreeNodes.Data.Player.GameInfos.Associations");
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData || (developers.isEmpty() && franchises.isEmpty() && publishers.isEmpty())) ;
				}
				
				private Vector<Associations.Association> parseArray(JSON_Object<Data.NV, Data.V> object, String subValueName, String dataValueStr, File file) throws TraverseException {
					JSON_Array<Data.NV, Data.V> array = JSON_Data.getArrayValue(object, subValueName, dataValueStr);
					Vector<Associations.Association> values = new Vector<>();
					for (int i=0; i<array.size(); i++) {
						try {
							values.add(new Association(array.get(i), dataValueStr+"["+i+"]"));
						} catch (TraverseException e) {
							showException(e, file);
							values.add(new Association(array.get(i)));
						}
					}
					return values;
				}
				
				static class Association {
					
					private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues()
							.add("strName", JSON_Data.Value.Type.String)
							.add("strName", JSON_Data.Value.Type.Null  )
							.add("strURL" , JSON_Data.Value.Type.String)
							.add("strURL" , JSON_Data.Value.Type.Null  );
					
					final JSON_Data.Value<Data.NV, Data.V> rawData;
					final boolean hasParsedData;

					final String name;
					final String url;
					
					Association(JSON_Data.Value<Data.NV, Data.V> rawData) {
						this.rawData = rawData;
						hasParsedData = false;
						name = null;
						url  = null;
					}
					Association(JSON_Data.Value<Data.NV, Data.V> value, String dataValueStr) throws TraverseException {
						this.rawData = null;
						hasParsedData = true;
						
						JSON_Object<Data.NV, Data.V> object = JSON_Data.getObjectValue(value, dataValueStr);
						//DevHelper.optionalValues.scan(object, "TreeNodes.Data.Player.GameInfos.Associations.Association");
						
						       JSON_Data.getValue(object, "strName", false, JSON_Data.Value.Type.Null  , JSON_Data.Value::castToNullValue  , true, dataValueStr);
						       JSON_Data.getValue(object, "strURL" , false, JSON_Data.Value.Type.Null  , JSON_Data.Value::castToNullValue  , true, dataValueStr);
						name = JSON_Data.getValue(object, "strName", false, JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, true, dataValueStr);
						url  = JSON_Data.getValue(object, "strURL" , false, JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, true, dataValueStr);
						
						DevHelper.scanUnexpectedValues(object, KNOWN_VALUES, "TreeNodes.Data.Player.GameInfos.Associations.Association");
					}
					
				}
			}
			static class SocialMedia extends GameInfos.ParsedBlock {
				
				// "GameStateInfo.Block["socialmedia",V3].dataValue:Array"
				// "GameStateInfo.Block["socialmedia",V3].dataValue[].eType:Integer"
				// "GameStateInfo.Block["socialmedia",V3].dataValue[].strName:String"
				// "GameStateInfo.Block["socialmedia",V3].dataValue[].strURL:String"
				// "GameStateInfo.Block["socialmedia",V3].dataValue[]:Object"
				
				final Vector<SocialMedia.SocialMediaEntry> entries;
				
				SocialMedia(JSON_Data.Value<Data.NV, Data.V> rawData, long version) {
					super(rawData, version, false);
					entries = null;
				}
				SocialMedia(JSON_Data.Value<Data.NV, Data.V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					
					JSON_Array<Data.NV, Data.V> array = JSON_Data.getArrayValue(blockDataValue, dataValueStr);
					
					entries = new Vector<>();
					for (int i=0; i<array.size(); i++)
						try {
							entries.add(new SocialMediaEntry(array.get(i), dataValueStr+"["+i+"]"));
						} catch (TraverseException e) {
							showException(e, file);
							entries.add(new SocialMediaEntry(array.get(i)));
						}
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData || (entries.isEmpty())) ;
				}
				
				static class SocialMediaEntry {

					private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues()
							.add("eType"  , JSON_Data.Value.Type.Integer)
							.add("strName", JSON_Data.Value.Type.String )
							.add("strURL" , JSON_Data.Value.Type.String );
					
					final JSON_Data.Value<Data.NV, Data.V> rawData;
					final boolean hasParsedData;
					final long type;
					final String name;
					final String url;
					
					SocialMediaEntry(JSON_Data.Value<Data.NV, Data.V> rawData) {
						this.rawData = rawData;
						hasParsedData = false;
						type = -1;
						name = null;
						url  = null;
					}
					SocialMediaEntry(JSON_Data.Value<Data.NV, Data.V> value, String dataValueStr) throws TraverseException {
						this.rawData = null;
						hasParsedData = true;
						JSON_Object<Data.NV, Data.V> object = JSON_Data.getObjectValue(value, dataValueStr);
						type = JSON_Data.getIntegerValue(object, "eType"  , dataValueStr);
						name = JSON_Data.getStringValue (object, "strName", dataValueStr);
						url  = JSON_Data.getStringValue (object, "strURL" , dataValueStr);
						DevHelper.scanUnexpectedValues(object, KNOWN_VALUES, "TreeNodes.Data.Player.GameInfos.SocialMedia.SocialMediaEntry");
					}
				}
			}
			
			static class ReleaseData extends GameInfos.ParsedBlock {
				
				// "GameInfo.Block["releasedata",V1].dataValue = <null>"
				
				ReleaseData(JSON_Data.Value<Data.NV, Data.V> rawData, long version) {
					super(rawData, version, false);
				}
				ReleaseData(JSON_Data.Value<Data.NV, Data.V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					if (blockDataValue!=null) throw new TraverseException("%s != <null>. I have not expected any value.", dataValueStr);
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData) ;
				}
			}
			
			static class AppActivity extends GameInfos.ParsedBlock {
				
				// "GameInfo.Block["appactivity",V3].dataValue:String"
				
				final String value;
				
				AppActivity(JSON_Data.Value<Data.NV, Data.V> rawData, long version) {
					super(rawData, version, false);
					value = null;
				}
				AppActivity(JSON_Data.Value<Data.NV, Data.V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					value = JSON_Data.getStringValue(blockDataValue, dataValueStr);
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData || (value.isEmpty())) ;
				}
			}
			
			static class AchievementMap extends GameInfos.ParsedBlock {
				
				// "GameStateInfo.Block["achievementmap",V2].dataValue:String"
				
				final JSON_Data.Value<Data.NV, Data.V> value;
				
				AchievementMap(JSON_Data.Value<Data.NV, Data.V> rawData, long version) {
					super(rawData, version, false);
					value = null;
				}
				AchievementMap(JSON_Data.Value<Data.NV, Data.V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					
					String jsonText = JSON_Data.getStringValue(blockDataValue, dataValueStr);
					try {
						value = JSONHelper.parseJsonText(jsonText);
					} catch (JSON_Parser.ParseException e) {
						throw new TraverseException("%s isn't a well formed JSON text: %s", dataValueStr, e.getMessage());
					}
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData) ;
				}
				
			}
			
			static class GameActivity extends GameInfos.ParsedBlock {
				
				// "GameStateInfo.Block["gameactivity",V1].dataValue:String"
				// "GameStateInfo.Block["gameactivity",V2].dataValue:Array"
				// "GameStateInfo.Block["gameactivity",V2].dataValue[]:String"
				// "GameStateInfo.Block["gameactivity",V3].dataValue:Array"
				// "GameStateInfo.Block["gameactivity",V3].dataValue[]:String"
				
				final Vector<String> values;
				
				GameActivity(JSON_Data.Value<Data.NV, Data.V> rawData, long version) {
					super(rawData, version, false);
					values = null;
				}
				GameActivity(JSON_Data.Value<Data.NV, Data.V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					values = new Vector<>();
					
					JSON_Array<Data.NV, Data.V> array = JSON_Data.getValue(blockDataValue, JSON_Data.Value.Type.Array, JSON_Data.Value::castToArrayValue, true, dataValueStr);
					if (array!=null) {
						for (int i=0; i<array.size(); i++)
							values.add(JSON_Data.getStringValue(array.get(i), dataValueStr+"["+i+"]"));
						return;
					}
					
					String string = JSON_Data.getValue(blockDataValue, JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, true, dataValueStr);
					if (string!=null) {
						values.add(string);
						return;
					}
					
					throw new TraverseException("%s is neither an ArrayValue nor a StringValue", dataValueStr);
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData || (values.isEmpty())) ;
				}
				
			}
			
			static class UserNews extends GameInfos.ParsedBlock {
				// "GameStateInfo.Block["usernews",V2].dataValue:Array"
				// "GameStateInfo.Block["usernews",V2].dataValue[]:String"
				
				final Vector<String> values;
				
				UserNews(JSON_Data.Value<Data.NV, Data.V> rawData, long version) {
					super(rawData, version, false);
					values = null;
				}
				UserNews(JSON_Data.Value<Data.NV, Data.V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					JSON_Array<Data.NV, Data.V> array = JSON_Data.getArrayValue(blockDataValue, dataValueStr);
					values = new Vector<>();
					for (int i=0; i<array.size(); i++)
						values.add(JSON_Data.getStringValue(array.get(i), dataValueStr+"["+i+"]"));
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData || (values.isEmpty())) ;
				}
			}
			
			static class Achievements extends GameInfos.ParsedBlock {

				private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues()
						.add("nAchieved"        , JSON_Data.Value.Type.Integer)
						.add("nTotal"           , JSON_Data.Value.Type.Integer)
						.add("vecAchievedHidden", JSON_Data.Value.Type.Array  )
						.add("vecUnachieved"    , JSON_Data.Value.Type.Array  )
						.add("vecHighlight"     , JSON_Data.Value.Type.Array  );
			
				final long achieved;
				final long total;
				final Vector<Achievements.Achievement> achievedHidden;
				final Vector<Achievements.Achievement> unachieved;
				final Vector<Achievements.Achievement> highlight;
				
				Achievements(JSON_Data.Value<Data.NV, Data.V> rawData, long version) {
					super(rawData, version, false);
					achieved = -1;
					total    = -1;
					achievedHidden = null;
					unachieved = null;
					highlight = null;
				}

				Achievements(JSON_Data.Value<Data.NV, Data.V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					
					JSON_Object<Data.NV, Data.V> object        = JSON_Data.getObjectValue (blockDataValue, dataValueStr);
					achieved                         = JSON_Data.getIntegerValue(object, "nAchieved"        , dataValueStr);
					total                            = JSON_Data.getIntegerValue(object, "nTotal"           , dataValueStr);
					JSON_Array<Data.NV, Data.V> unachieved     = JSON_Data.getArrayValue  (object, "vecUnachieved"    , dataValueStr);
					JSON_Array<Data.NV, Data.V> highlight      = JSON_Data.getArrayValue  (object, "vecHighlight"     , dataValueStr);
					JSON_Array<Data.NV, Data.V> achievedHidden = JSON_Data.getValue(object, "vecAchievedHidden", true, JSON_Data.Value.Type.Array, JSON_Data.Value::castToArrayValue, false, dataValueStr);
					
					this.achievedHidden = parseArray(achievedHidden, dataValueStr+"."+"vecAchievedHidden", file);
					this.unachieved     = parseArray(unachieved    , dataValueStr+"."+"vecUnachieved"    , file);
					this.highlight      = parseArray(highlight     , dataValueStr+"."+"vecHighlight"     , file);
					
					DevHelper.scanUnexpectedValues(object, KNOWN_VALUES, "TreeNodes.Data.Player.GameInfos.Achievements");
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData || ((achievedHidden==null || achievedHidden.isEmpty()) && unachieved.isEmpty() && highlight.isEmpty() && achieved<=0)) ;
				}

				private Vector<Achievements.Achievement> parseArray(JSON_Array<Data.NV, Data.V> rawArray, String debugOutputPrefixStr, File file) {
					if (rawArray==null) return null;
					Vector<Achievements.Achievement> array = new Vector<>();
					for (int i=0; i<rawArray.size(); i++)
						try {
							array.add(new Achievement(rawArray.get(i), debugOutputPrefixStr+"["+i+"]"));
						} catch (TraverseException e) {
							showException(e, file);
							array.add(new Achievement(rawArray.get(i)));
						}
					return array;
				}
				
				public String getTreeNodeExtraInfo() {
					if (hasParsedData && achieved!=0 && total!=0)
						return String.format("A:%d/%d", achieved, total);
					return "";
				}

				static class Achievement {

					private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues()
							.add("bAchieved"     , JSON_Data.Value.Type.Bool   )
							.add("flAchieved"    , JSON_Data.Value.Type.Float  )
							.add("flAchieved"    , JSON_Data.Value.Type.Integer)
							.add("rtUnlocked"    , JSON_Data.Value.Type.Integer)
							.add("strDescription", JSON_Data.Value.Type.String )
							.add("strID"         , JSON_Data.Value.Type.String )
							.add("strImage"      , JSON_Data.Value.Type.String )
							.add("strName"       , JSON_Data.Value.Type.String );
					
					final JSON_Data.Value<Data.NV, Data.V> rawData;
					final boolean hasParsedData;
					final boolean isAchieved;
					final Double achievedRatio;
					final long unlocked;
					final String description;
					final String id;
					final String image;
					final String name;


					public Achievement(JSON_Data.Value<Data.NV, Data.V> rawData) {
						this.rawData = rawData;
						hasParsedData = false;
						isAchieved    = false;
						achievedRatio = null;
						unlocked      = -1;
						description   = null;
						id            = null;
						image         = null;
						name          = null;
					}

					public Achievement(JSON_Data.Value<Data.NV, Data.V> value, String debugOutputPrefixStr) throws TraverseException {
						rawData = null;
						hasParsedData = true;
						//DevHelper.scanJsonStructure(value,"GameStateInfo.Achievements.Achievement");
						
						JSON_Object<Data.NV, Data.V> object = JSON_Data.getObjectValue(value, debugOutputPrefixStr);
						isAchieved    = JSON_Data.getBoolValue   (object, "bAchieved"     , debugOutputPrefixStr);
						unlocked      = JSON_Data.getIntegerValue(object, "rtUnlocked"    , debugOutputPrefixStr);
						description   = JSON_Data.getStringValue (object, "strDescription", debugOutputPrefixStr);
						id            = JSON_Data.getStringValue (object, "strID"         , debugOutputPrefixStr);
						image         = JSON_Data.getStringValue (object, "strImage"      , debugOutputPrefixStr);
						name          = JSON_Data.getStringValue (object, "strName"       , debugOutputPrefixStr);
						achievedRatio = JSON_Data.getNumber(object, "flAchieved", true, debugOutputPrefixStr);
						
						DevHelper.scanUnexpectedValues(object, KNOWN_VALUES, "TreeNodes.Data.Player.GameInfos.Achievements.Achievement");
					}
					
				}
			}

			static class Badge extends GameInfos.ParsedBlock {
				
				private static final DevHelper.KnownJsonValues KNOWN_JSON_VALUES = new DevHelper.KnownJsonValues()
						.add("strName"         , JSON_Data.Value.Type.String)
						.add("bHasBadgeData"   , JSON_Data.Value.Type.Bool)
						.add("bMaxed"          , JSON_Data.Value.Type.Null)
						.add("nMaxLevel"       , JSON_Data.Value.Type.Integer)
						.add("nLevel"          , JSON_Data.Value.Type.Integer)
						.add("nXP"             , JSON_Data.Value.Type.Integer)
						.add("strNextLevelName", JSON_Data.Value.Type.String)
						.add("nNextLevelXP"    , JSON_Data.Value.Type.Integer)
						.add("strIconURL"      , JSON_Data.Value.Type.String)
						.add("rgCards"         , JSON_Data.Value.Type.Array);
			
				final Vector<Badge.TradingCard> tradingCards;

				final String  name;
				final boolean hasBadgeData;
				final long    maxLevel;
				final long    currentLevel;
				final long    currentXP;
				final String  nextLevelName;
				final long    nextLevelXP;
				final String  iconURL;


				Badge(JSON_Data.Value<Data.NV, Data.V> rawData, long version) {
					super(rawData, version, false);
					tradingCards  = null;
					name          = null;
					hasBadgeData  = false;
					maxLevel      = -1;
					currentLevel  = -1;
					currentXP     = -1;
					nextLevelName = null;
					nextLevelXP   = -1;
					iconURL       = null;
				}

				Badge(JSON_Data.Value<Data.NV, Data.V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					
					JSON_Object<Data.NV, Data.V> object = JSON_Data.getObjectValue (blockDataValue, dataValueStr);
					name          = JSON_Data.getStringValue (object, "strName"         , dataValueStr);
					hasBadgeData  = JSON_Data.getBoolValue   (object, "bHasBadgeData"   , dataValueStr);
					maxLevel      = JSON_Data.getIntegerValue(object, "nMaxLevel"       , dataValueStr);
					currentLevel  = JSON_Data.getIntegerValue(object, "nLevel"          , dataValueStr);
					currentXP     = JSON_Data.getIntegerValue(object, "nXP"             , dataValueStr);
					nextLevelName = JSON_Data.getStringValue (object, "strNextLevelName", dataValueStr);
					nextLevelXP   = JSON_Data.getIntegerValue(object, "nNextLevelXP"    , dataValueStr);
					iconURL       = JSON_Data.getStringValue (object, "strIconURL"      , dataValueStr);
					JSON_Data.getNullValue(object, "bMaxed", dataValueStr);
					
					JSON_Array<Data.NV,Data.V> array = JSON_Data.getArrayValue(object, "rgCards", dataValueStr);
					tradingCards = new Vector<>();
					for (int i=0; i<array.size(); i++) {
						try {
							tradingCards.add(new TradingCard(array.get(i), dataValueStr+".rgCards["+i+"]"));
						} catch (TraverseException e) {
							showException(e, file);
							tradingCards.add(new TradingCard(array.get(i)));
						}
					}
					
					// unexpected values
					DevHelper.scanUnexpectedValues(object, KNOWN_JSON_VALUES,"TreeNodes.Data.Player.GameInfos.Badge");
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData) ;
				}

				String getTreeNodeExtraInfo() {
					if (!hasParsedData) return "";
					
					String str = "";
					if (currentLevel!=0)
						str += (str.isEmpty()?"":", ") + "B:"+currentLevel;
					
					int tcCount = 0;
					for (Badge.TradingCard tc:tradingCards) tcCount += tc.owned;
					if (tcCount>0)
						str += (str.isEmpty()?"":", ") + "TC:"+tcCount;
					
					return str;
				}

				File createTempTradingCardsOverviewHTML() {
					Path htmlPath;
					try { htmlPath = Files.createTempFile(null, ".html"); }
					catch (IOException e) {
						System.err.printf("IOException while createing a temporary file (*.html): %s%n", e.getMessage());
						return null;
					}
					System.out.printf("Create TradingCards Overview HTML: %s%n", htmlPath);
					File htmlFile = htmlPath.toFile();
					try (
						PrintWriter htmlOut = new PrintWriter(new OutputStreamWriter(new FileOutputStream(htmlFile), StandardCharsets.UTF_8));
						BufferedReader templateIn = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/html/templateTradingCardsOverview.html"), StandardCharsets.UTF_8)); 
					) {
						
						String line;
						while ( (line=templateIn.readLine())!=null ) {
							if (line.trim().equals("// write url array here"))
								writeTradingCardsOverviewHTML(htmlOut);
							else
								htmlOut.println(line);
						}
						
					} catch (FileNotFoundException e) {
						System.err.printf("FileNotFoundException while writing in a temporary file (\"%s\"): %s%n", htmlFile, e.getMessage());
					} catch (IOException e) {
						System.err.printf("IOException while writing in a temporary file (\"%s\"): %s%n", htmlFile, e.getMessage());
					}
					return htmlFile;
				}

				private void writeTradingCardsOverviewHTML(PrintWriter htmlOut) {
					if (hasParsedData)
						for (Badge.TradingCard tc:tradingCards) {
							htmlOut.printf("		new TradingCard(%s,%s,%s,%s),%n", toString(tc.name), tc.owned, toString(tc.imageURL), toString(tc.artworkURL));
						}
				}

				private String toString(String str) {
					if (str==null) return "null";
					return "\""+str+"\"";
				}

				static class TradingCard {
					
					private static final DevHelper.KnownJsonValues KNOWN_JSON_VALUES = new DevHelper.KnownJsonValues()
							.add("strName"      , JSON_Data.Value.Type.String)
							.add("strTitle"     , JSON_Data.Value.Type.String)
							.add("nOwned"       , JSON_Data.Value.Type.Integer)
							.add("strArtworkURL", JSON_Data.Value.Type.String)
							.add("strImgURL"    , JSON_Data.Value.Type.String)
							.add("strMarketHash", JSON_Data.Value.Type.String);

					final JSON_Data.Value<Data.NV, Data.V> rawData;
					final boolean hasParsedData;
					final String name;
					final String title;
					final long   owned;
					final String artworkURL;
					final String imageURL;
					final String marketHash;

					
					public TradingCard(JSON_Data.Value<Data.NV, Data.V> rawData) {
						this.rawData = rawData;
						hasParsedData = false;
						
						name  = null;
						title = null;
						owned = -1;
						artworkURL = null;
						imageURL   = null;
						marketHash = null;
					}

					public TradingCard(JSON_Data.Value<Data.NV, Data.V> value, String debugOutputPrefixStr) throws TraverseException {
						rawData = null;
						hasParsedData = true;
						
						JSON_Object<Data.NV,Data.V> object = JSON_Data.getObjectValue(value, debugOutputPrefixStr);
						name       = JSON_Data.getStringValue (object, "strName"      , debugOutputPrefixStr);
						title      = JSON_Data.getStringValue (object, "strTitle"     , debugOutputPrefixStr);
						owned      = JSON_Data.getIntegerValue(object, "nOwned"       , debugOutputPrefixStr);
						artworkURL = JSON_Data.getStringValue (object, "strArtworkURL", debugOutputPrefixStr);
						imageURL   = JSON_Data.getStringValue (object, "strImgURL"    , debugOutputPrefixStr);
						marketHash = JSON_Data.getStringValue (object, "strMarketHash", debugOutputPrefixStr);
						
						DevHelper.scanUnexpectedValues(object, KNOWN_JSON_VALUES,"TreeNodes.Data.Player.GameInfos.Badge.TradingCard");
					}
				}
			}
			
			static class Block {

				final JSON_Data.Value<Data.NV, Data.V> rawData;
				final boolean hasParsedData;
				final int blockIndex;
				final String label;
				final long version;
				final JSON_Data.Value<Data.NV, Data.V> dataValue;

				static GameInfos.Block createRawData(int blockIndex, JSON_Data.Value<Data.NV, Data.V> value) {
					try { return new Block(blockIndex, value, true); }
					catch (TraverseException e) { return null; }
				}

				Block(int blockIndex, JSON_Data.Value<Data.NV, Data.V> value) throws TraverseException {
					this(blockIndex, value, false);
				}
				Block(int blockIndex, JSON_Data.Value<Data.NV, Data.V> value, boolean asRawData) throws TraverseException {
					if (asRawData) {
						this.rawData = value;
						this.hasParsedData = false;
						this.blockIndex = blockIndex;
						this.label = null;
						this.version = -1;
						this.dataValue = null;
						
					} else {
						this.rawData = null;
						this.hasParsedData = true;
						this.blockIndex = blockIndex;
						
						String blockStr     = "GameStateInfo.Block["+blockIndex+"]";
						String labelStr     = blockStr+".value[0:label]";
						String blockdataStr = blockStr+".value[1:blockdata]";
						
						JSON_Array<Data.NV, Data.V> array = JSON_Data.getArrayValue(value, blockStr);
						if (array.size()!=2) throw new TraverseException("%s.value.length(==%d) != 2", blockStr, array.size());
						
											  label = JSON_Data.getStringValue(array.get(0), labelStr);
						JSON_Object<Data.NV,Data.V> blockdata = JSON_Data.getObjectValue(array.get(1), blockdataStr);
						if (blockdata.size()>2)  throw new TraverseException("%s JSON_Object.length(==%d) > 2: Too much values", blockdataStr, blockdata.size());
						if (blockdata.isEmpty()) throw new TraverseException("%s JSON_Object is empty: Too few values", blockdataStr);
						version   = JSON_Data.getIntegerValue(blockdata,"version", blockdataStr+".version");
						dataValue = blockdata.getValue("data");
						if (dataValue==null && blockdata.size()>1) throw new TraverseException("%s.data not found, but there are other values", blockdataStr);
					}
				}
			}
		}
	}

	static class AppManifest {
		
		private static final String prefix = "appmanifest_";
		private static final String suffix = ".acf";
		
		@SuppressWarnings("unused")
		private final int appID;
		final File file;
		private final VDFTreeNode vdfTree;
		
		AppManifest(int appID, File file) {
			this.appID = appID;
			this.file = file;
			
			if (this.file!=null) {
				
				VDFParser.Data vdfData = null;
				try { vdfData = VDFParser.parse(this.file,StandardCharsets.UTF_8); }
				catch (VDFParser.ParseException e) {}
				
				vdfTree = vdfData!=null ? vdfData.createVDFTreeNode() : null;
				
			} else
				vdfTree = null;
		}
	
		static Integer getAppIDFromFile(File file) {
			// appmanifest_275850.acf 
			if (!file.isFile()) return null;
			String name = file.getName();
			if (!name.startsWith(prefix)) return null;
			if (!name.endsWith(suffix)) return null;
			String idStr = name.substring(prefix.length(), name.length()-suffix.length());
			try { return Integer.parseInt(idStr); }
			catch (NumberFormatException e) { return null; }
		}

		String getGameTitle() {
			if (vdfTree!=null) {
				VDFTreeNode appNameNode = vdfTree.getSubNode("AppState","name");
				if (appNameNode!=null) return appNameNode.value;
			}
			return null;
		}
	}
	
	static class Game implements Comparable<Data.Game>{
		
		final int appID;
		private final String title;
		final Data.AppManifest appManifest;
		final HashMap<String, File> imageFiles;
		final HashMap<Long, File> steamCloudFolders;
		final HashMap<Long, ScreenShotLists.ScreenShotList> screenShots;
		final HashMap<Long, Player.GameInfos>  gameInfos;
		final HashMap<Long, Player.AchievementProgress.AchievementProgressInGame>  achievementProgress;
		
		Game(int appID, Data.AppManifest appManifest, HashMap<String, File> imageFiles, HashMap<Long, Data.Player> players) {
			this.appID = appID;
			this.appManifest = appManifest;
			this.imageFiles = imageFiles;
			title = appManifest==null ? null : appManifest.getGameTitle();
			
			steamCloudFolders = new HashMap<>();
			screenShots = new HashMap<>();
			gameInfos = new HashMap<>();
			achievementProgress = new HashMap<>();
			players.forEach((playerID,player)->{
				
				File steamCloudFolder = player.steamCloudFolders.get(appID);
				if (steamCloudFolder!=null)
					steamCloudFolders.put(playerID, steamCloudFolder);
				
				if (player.screenShots!=null) {
					ScreenShotLists.ScreenShotList screenShots = player.screenShots.get(appID);
					if (screenShots!=null && !screenShots.isEmpty())
						this.screenShots.put(playerID, screenShots);
				}
				Player.GameInfos gameStateInfo = player.gameInfos.get(appID);
				if (gameStateInfo!=null)
					gameInfos.put(playerID, gameStateInfo);
				
				if (player.achievementProgress!=null && player.achievementProgress.gameStates!=null) {
					Player.AchievementProgress.AchievementProgressInGame progress = player.achievementProgress.gameStates.get(appID);
					if (progress!=null)
						achievementProgress.put(playerID, progress);
				}
			});
		}

		boolean hasATitle() {
			return title!=null;
		}

		String getTitle() {
			if (title!=null) return title+" ["+appID+"]";
			String storedTitle = knownGameTitles.get(appID);
			if (storedTitle!=null) return storedTitle+" ["+appID+"]";
			return "Game "+appID;
		}

		Icon getIcon() {
			if (imageFiles!=null) {
				File iconImageFile = imageFiles.get("icon");
				if (iconImageFile!=null) {
					try {
						BufferedImage image = ImageIO.read(iconImageFile);
						return IconSource.getScaledIcon(image, 16, 16);
					} catch (IOException e) {}
				}
			}
			return null;
		}

		@Override
		public int compareTo(Data.Game other) {
			if (other==null) return -1;
			if (this.title!=null && other.title==null) return -1;
			if (this.title==null && other.title!=null) return +1;
			if (this.title!=null && other.title!=null) {
				int n = this.title.compareTo(other.title);
				if (n!=0) return n;
			}
			return this.appID-other.appID;
		}
	}

	static class GameImages {
		
		@SuppressWarnings("unused")
		private final File folder;
		final Vector<File> otherFiles;
		final Vector<File> imageFiles;
		private final GameImages.HashMatrix<Integer, String, File> appImages;
	
		GameImages(File folder) {
			this.folder = folder;
			File[] files = TreeNodes.getFilesAndFolders(folder);
			
			otherFiles = new Vector<>();
			imageFiles = new Vector<>();
			appImages = new GameImages.HashMatrix<>();
			
			for (File file:files) {
				if (file.isDirectory()) {
					otherFiles.add(file);
					
				} else if (TreeNodes.isImageFile(file)) {
					GameImages.ImageFileName ifn = ImageFileName.parse(file.getName());
					if (ifn==null || ifn.label==null || ifn.number==null)
						imageFiles.add(file);
					else
						appImages.put(ifn.number, ifn.label, file);
					
				} else
					otherFiles.add(file);
			}
		}
		
		public Collection<? extends Integer> getGameIDs() {
			return appImages.keySet1;
		}

		public Vector<Integer> getSortedGameIDs() {
			Vector<Integer> keySet1 = new Vector<>(getGameIDs());
			keySet1.sort(null);
			return keySet1;
		}
	
		public Vector<String> getSortedImageTypes() {
			Vector<String>  keySet2 = new Vector<>(appImages.keySet2);
			keySet2.sort(null);
			return keySet2;
		}
	
		public File getImageFile(Integer gameID, String ImageType) { return appImages.get(gameID, ImageType); }
		public File getImageFile(String ImageType, Integer gameID) { return appImages.get(gameID, ImageType); }
	
		public HashMap<String, File> getImageFileMap(Integer gameID) {
			return appImages.getMapCopy(gameID);
		}
		
		public File[] getImageFileArrays(Integer gameID) {
			Collection<File> files = appImages.getCollection(gameID);
			return files.toArray(new File[files.size()]);
		}

		static class HashMatrix<KeyType1,KeyType2,ValueType> {
			
			private final HashMap<KeyType1,HashMap<KeyType2,ValueType>> matrix;
			private final HashSet<KeyType1> keySet1;
			private final HashSet<KeyType2> keySet2;
			
			HashMatrix() {
				matrix = new HashMap<>();
				keySet1 = new HashSet<>();
				keySet2 = new HashSet<>();
			}
			
			void put(KeyType1 key1, KeyType2 key2, ValueType value) {
				HashMap<KeyType2, ValueType> map = matrix.get(key1);
				if (map==null) matrix.put(key1, map = new HashMap<>());
				map.put(key2, value);
				keySet1.add(key1);
				keySet2.add(key2);
			}
			
			HashMap<KeyType2, ValueType> getMapCopy(KeyType1 key1) {
				HashMap<KeyType2, ValueType> map = matrix.get(key1);
				if (map==null) return null;
				return new HashMap<>(map);
			}
			
			Collection<ValueType> getCollection(KeyType1 key1) {
				HashMap<KeyType2, ValueType> map = matrix.get(key1);
				return map.values();
			}
			
			ValueType get(KeyType1 key1, KeyType2 key2) {
				HashMap<KeyType2, ValueType> map = matrix.get(key1);
				if (map==null) return null;
				return map.get(key2);
			}
		}

		static class ImageFileName {
		
			private final Integer number;
			private final String label;
		
			public ImageFileName(Integer number, String label) {
				this.number = number;
				this.label = label;
			}
		
			static GameImages.ImageFileName parse(String name) {
				// 1000410_library_600x900.jpg
				int pos = name.lastIndexOf('.');
				if (pos>=0) name = name.substring(0, pos);
				pos = name.indexOf('_');
				if (pos<0) return null;
				String numberStr = name.substring(0, pos);
				String labelStr  = name.substring(pos+1);
				Integer number = parseNumber(numberStr);
				if (number==null) return null;
				return new ImageFileName(number,labelStr);
			}
			
		}
	}
	static class ScreenShotLists extends HashMap<Integer,ScreenShotLists.ScreenShotList>{
		private static final long serialVersionUID = -428703055699412094L;
		
		final File folder;
	
		ScreenShotLists(File folder) { 
			File subFolder = new File(folder,"remote");
			if (subFolder.isDirectory()) {
				this.folder = subFolder;
				File[] folders = subFolder.listFiles(file->file.isDirectory() && parseNumber(file.getName())!=null);
				for (File gameFolder:folders) {
					Integer gameID = parseNumber(gameFolder.getName());
					File imagesFolder = new File(gameFolder,"screenshots");
					if (imagesFolder.isDirectory()) {
						File thumbnailsFolder = new File(imagesFolder,"thumbnails");
						if (!thumbnailsFolder.isDirectory()) thumbnailsFolder = null;
						put(gameID, new ScreenShotList(imagesFolder,thumbnailsFolder));
					}
				}
			} else
				this.folder = null;
		}
		
		static class ScreenShotList extends Vector<Data.ScreenShot> {
			private static final long serialVersionUID = 8285684141839919150L;
			
			final File imagesFolder;
			final File thumbnailsFolder;
			
			public ScreenShotList(File imagesFolder, File thumbnailsFolder) {
				this.imagesFolder = imagesFolder;
				this.thumbnailsFolder = thumbnailsFolder;
				File[] imageFiles = imagesFolder.listFiles(TreeNodes::isImageFile);
				for (File image:imageFiles) {
					File thumbnail = null;
					if (thumbnailsFolder!=null)
						thumbnail = new File(thumbnailsFolder,image.getName());
					add(new ScreenShot(image,thumbnail));
				}
			}
		}
	}
	static class ScreenShot implements Comparable<Data.ScreenShot> {
		final File image;
		final File thumbnail;
		ScreenShot(File image, File thumbnail) {
			this.image = image;
			this.thumbnail = thumbnail;
			if (image==null || !image.isFile())
				throw new IllegalArgumentException();
		}
		@Override public int compareTo(Data.ScreenShot other) {
			if (other==null) return -1;
			return this.image.getAbsolutePath().compareTo(other.image.getAbsolutePath());
		}
	}
}