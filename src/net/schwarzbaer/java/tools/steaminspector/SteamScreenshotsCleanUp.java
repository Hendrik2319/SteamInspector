package net.schwarzbaer.java.tools.steaminspector;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.schwarzbaer.gui.ProgressDialog;
import net.schwarzbaer.gui.StandardDialog;
import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.gui.Tables;
import net.schwarzbaer.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.java.tools.steaminspector.Data.Game;
import net.schwarzbaer.java.tools.steaminspector.Data.ScreenShot;
import net.schwarzbaer.java.tools.steaminspector.Data.ScreenShotLists.ScreenShotList;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.AppSettings.ValueKey;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.TreeIcons;

class SteamScreenshotsCleanUp {
	
	public static void startStandAlone() {
		new SteamScreenshotsCleanUp(null).initialize().showGUI();
	}

	public static void startAsDialog(Window parent) {
		new SteamScreenshotsCleanUp(parent).initialize().showGUI();
	}

	private final Window mainWindow;
	private final JFileChooser folderChooser;
	private final JTree tree;
	@SuppressWarnings("unused")
	private DefaultTreeModel treeModel;
	private File generalScreenshotFolder;
	private final HashMap<Integer,HashMap<String,File>> generalScreenshots;
	private final GuiVariant<? extends Window> gui;
	private final HashSet<Integer> gamesWithScreenshots;
	private final ImageListPanel imageListPanel;

	SteamScreenshotsCleanUp(Window parent) {
		generalScreenshotFolder = null;
		generalScreenshots = new HashMap<>();
		gamesWithScreenshots = new HashSet<>();
		
		if (parent==null) gui = new GuiVariant.StandAlone(    "SteamScreenshots CleanUp");
		else              gui = new GuiVariant.Dialog(parent, "SteamScreenshots CleanUp");
		mainWindow = gui.getWindow();
		
		folderChooser = new JFileChooser("./");
		folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		folderChooser.setMultiSelectionEnabled(false);
		
		tree = new JTree(treeModel = null);
		tree.setRootVisible(false);
		tree.setCellRenderer(new SteamInspector.BaseTreeNodeRenderer());
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		tree.addTreeSelectionListener(e->{
			TreePath path = e.getNewLeadSelectionPath();
			if (path==null) return;
			showTreeNodeContent(path.getLastPathComponent());
		});
		
		JScrollPane treeScrollPane = new JScrollPane(tree);
		treeScrollPane.setPreferredSize(new Dimension(500, 800));
		
		imageListPanel = new ImageListPanel(this);
		
		JSplitPane contentPane = new JSplitPane();
		contentPane.setLeftComponent (treeScrollPane);
		contentPane.setRightComponent(imageListPanel);
		
		gui.createGUI(contentPane);
		SteamInspector.settings.registerExtraWindow(mainWindow, ValueKey.SSCU_WindowX, ValueKey.SSCU_WindowY, ValueKey.SSCU_WindowWidth, ValueKey.SSCU_WindowHeight);
	}
	
	private void showGUI() {
		gui.showGUI();
	}
	
	private void showTreeNodeContent(Object treeNodeObj) {
		if (treeNodeObj instanceof GamesGroupTreeNode.GameTreeNode) {
			GamesGroupTreeNode.GameTreeNode gameTreeNode = (GamesGroupTreeNode.GameTreeNode) treeNodeObj;
			imageListPanel.setData(gameTreeNode.gameID);
		} else
			imageListPanel.clearData();
	}

	SteamScreenshotsCleanUp initialize() {
		ProgressDialog.runWithProgressDialog(mainWindow, "Initialize", 300, pd->{
			generalScreenshotFolder = getFolder(pd, "Determine General Screenshot Folder", ValueKey.SSCU_GeneralScreenshotFolder);
			if (generalScreenshotFolder==null) return;
			
			SteamInspector.showIndeterminateTask(pd, "Load Game Data");
			if (Data.isEmpty())
				Data.loadData();
			gamesWithScreenshots.clear();
			Data.games.forEach((gameID,game)->{
				if (!game.screenShots.isEmpty())
					gamesWithScreenshots.add(game.appID);
			});
			
			SteamInspector.showIndeterminateTask(pd, "Scan General Screenshot Folder");
			scanGeneralScreenshotFolder();
			
			
			
			SteamInspector.showIndeterminateTask(pd, "Update GUI");
			tree.setModel(treeModel = new DefaultTreeModel(new RootTreeNode()));
			
		});
		return this;
	}

	private void scanGeneralScreenshotFolder() {
		// in general Screenshot Folder: 263280_20191217003048_1.png
		// in Game Screenshot Folder:           20191217003048_1.jpg  
		System.out.printf("Scan \"%s\" ...%n", generalScreenshotFolder.getAbsolutePath());
		File[] files = generalScreenshotFolder.listFiles(File::isFile);
		int counter = 0;
		generalScreenshots.clear();
		for (File file : files) {
			String filename = file.getName();
			
			int pos1 = filename.indexOf("_");
			if (pos1<0) continue;
			
			int gameID;
			try { gameID = Integer.parseInt(filename.substring(0, pos1)); }
			catch (NumberFormatException e) { continue; }
			
			int pos2 = filename.indexOf(".", pos1);
			if (pos2<0) continue;
			String nameWithoutExt = filename.substring(pos1+1,pos2);
			
			HashMap<String, File> gameScreenshots = generalScreenshots.get(gameID);
			if (gameScreenshots==null) generalScreenshots.put(gameID, gameScreenshots=new HashMap<>());
			gameScreenshots.put(nameWithoutExt, file);
			counter++;
		}
		System.out.printf("Found %d images from %d games.%n", counter, generalScreenshots.size());
	}
	
	private File getFolder(ProgressDialog pd, String taskTitle, ValueKey valueKey) {
		SteamInspector.showIndeterminateTask(pd, taskTitle);
		File folder = SteamInspector.settings.getFile(valueKey, null);
		if (folder==null || !folder.isDirectory()) {
			folder = null;
			if (folderChooser.showOpenDialog(mainWindow)==JFileChooser.APPROVE_OPTION) {
				folder = folderChooser.getSelectedFile();
				SteamInspector.settings.putFile(valueKey, folder);
			}
		}
		return folder;
	}

	private static abstract class GuiVariant<MainWindow extends Window> {
		protected final MainWindow mainWindow;
		GuiVariant(MainWindow mainWindow) {
			this.mainWindow = mainWindow;
		}
		Window getWindow() { return mainWindow; }
		abstract void createGUI(JComponent contentPane);
		abstract void showGUI();
		
		static class StandAlone extends GuiVariant<StandardMainWindow> {
			StandAlone(String title) { super(new StandardMainWindow(title)); }
			@Override public void createGUI(JComponent contentPane) { mainWindow.startGUI(contentPane); }
			@Override public void showGUI() {}
		}
		static class Dialog extends GuiVariant<StandardDialog> {
			Dialog(Window parent, String title) { super(new StandardDialog(parent, title)); }
			@Override void createGUI(JComponent contentPane) { mainWindow.createGUI(contentPane, SteamInspector.createButton("Close", true, e->mainWindow.closeDialog())); }
			@Override void showGUI() { mainWindow.showDialog(); }
		}
	}

	private class RootTreeNode extends SteamInspector.BaseTreeNode<TreeNode, TreeNode> {

		RootTreeNode() {
			super(null, "<Root>", true, false);
		}

		@Override protected Vector<TreeNode> createChildren() {
			Vector<TreeNode> children = new Vector<>();
			children.add(new GamesGroupTreeNode(this));
			return children;
		}
		
	}

	private class GamesGroupTreeNode extends SteamInspector.BaseTreeNode<RootTreeNode, GamesGroupTreeNode.GameTreeNode> {
			
			protected GamesGroupTreeNode(RootTreeNode parent) {
				super(parent, "Games", true, false);
			}
	
			@Override protected Vector<GameTreeNode> createChildren() {
				Vector<GameTreeNode> children = new Vector<>();
				
				HashSet<Integer> gameIDs = new HashSet<>(gamesWithScreenshots);
				gameIDs.addAll(generalScreenshots.keySet());
				
				Vector<Integer> gameIDs_sorted = new Vector<>(gameIDs);
				gameIDs_sorted.sort(Comparator
						.comparing(Data::getGameTitle)
						.thenComparing(Comparator.naturalOrder()));
				
				for (Integer gameID : gameIDs_sorted)
					if (gameID!=null)
						children.add(new GameTreeNode(this, gameID));
				
				return children;
			}
	
			private class GameTreeNode extends SteamInspector.BaseTreeNode<GamesGroupTreeNode, TreeNode> {
				
				private final int gameID;
	
				protected GameTreeNode(GamesGroupTreeNode parent, int gameID) {
					super(parent, Data.getGameTitle(gameID), false, true, Data.getGameIcon(gameID, TreeIcons.Folder));
					this.gameID = gameID;
				}
	
				@Override protected Vector<? extends TreeNode> createChildren() {
					throw new UnsupportedOperationException();
				}
			}
		}

	private static class ImageListPanel extends JPanel {
		private static final long serialVersionUID = 7167176806130750722L;

		enum ViewType {
			Details, ImageGrid
		}
		
		private final SteamScreenshotsCleanUp main;
		private final JScrollPane scrollPane;
		private ViewType currentViewType;
		private HashMap<String, File> generalScreenshots;
		private HashMap<Long, ScreenShotList> gameScreenshots;
		
		ImageListPanel(SteamScreenshotsCleanUp main) {
			super(new BorderLayout());
			this.main = main;
			
			generalScreenshots = null;
			gameScreenshots = null;
			currentViewType = SteamInspector.settings.getEnum(ValueKey.SSCU_ViewType, ViewType.Details, ViewType.class);
			
			JPanel viewOptionsPanel = new JPanel(new GridBagLayout());
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.HORIZONTAL;
			c.weightx = 0;
			ButtonGroup bg = new ButtonGroup();
			viewOptionsPanel.add(SteamInspector.createRadioButton("Details"   , currentViewType==ViewType.Details  , true, bg, b->setViewType(ViewType.Details  )), c);
			viewOptionsPanel.add(SteamInspector.createRadioButton("Image Grid", currentViewType==ViewType.ImageGrid, true, bg, b->setViewType(ViewType.ImageGrid)), c);
			c.weightx = 1;
			viewOptionsPanel.add(new JLabel(), c);
			
			scrollPane = new JScrollPane();
			
			add(viewOptionsPanel, BorderLayout.NORTH);
			add(scrollPane, BorderLayout.CENTER);
			buildView();
		}
	
		private void setViewType(ViewType viewType) {
			currentViewType = viewType;
			buildView();
		}

		void setData(int gameID) {
			generalScreenshots = main.generalScreenshots.get(gameID);
			Game game = Data.games.get(gameID);
			gameScreenshots = game==null ? null : game.screenShots;
			buildView();
		}

		void clearData() {
			generalScreenshots = null;
			gameScreenshots = null;
			buildView();
		}

		private void buildView() {
			switch (currentViewType) {
			case Details:
				DetailsTableModel tableModel = new DetailsTableModel(generalScreenshots,gameScreenshots);
				JTable table = new JTable(tableModel);
				scrollPane.setViewportView(table);
				table.setRowSorter(new Tables.SimplifiedRowSorter(tableModel));
				table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
				table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
				
				tableModel.setTable(table);
				tableModel.setColumnWidths(table);
				//tableModel.setDefaultCellEditorsAndRenderers();
				
				//new GUI.ObjectsTableContextMenu(table, tableModel);
				
				break;
			case ImageGrid:
				scrollPane.setViewportView(null);
				// TODO: build ImageGrid
				break;
			}
		}
		
		private static class DetailsTableModel extends Tables.SimplifiedTableModel<DetailsTableModel.ColumnID> {

			enum ColumnID implements Tables.SimplifiedColumnIDInterface {
				Name     ("Name"   , String .class, 150),
				InGeneral("General", Boolean.class,  50),
				InGame   ("Game"   , Boolean.class,  50),
				Player   ("Player" , String .class, 100),
				;
				private final SimplifiedColumnConfig cfg;
				ColumnID(String name, Class<?> columnClass, int width) { cfg = new SimplifiedColumnConfig(name, columnClass, 20, -1, width, width); }
				@Override public SimplifiedColumnConfig getColumnConfig() { return cfg; }
				
			}

			private final Vector<Row> rows;

			DetailsTableModel(HashMap<String,File> generalScreenshots, HashMap<Long, ScreenShotList> gameScreenshots) {
				super(ColumnID.values());

				HashMap<String,Row> rowMap = new HashMap<>();
				
				if (generalScreenshots!=null)
					generalScreenshots.forEach((name,file)->{
						Row row = new Row(name);
						rowMap.put(name, row);
						row.generalScreenshot = file;
					});
				
				if (gameScreenshots!=null) {
					gameScreenshots.forEach((playerID,list)->{
						for (ScreenShot scrsht : list) {
							String name = scrsht.image.getName();
							int pos = name.lastIndexOf(".");
							if (pos>0) name = name.substring(0, pos);
							Row row = rowMap.get(name);
							if (row==null) rowMap.put(name, row = new Row(name));
							row.gameScreenShots.put(playerID,scrsht);
						}
					});
				}
				
				rows = new Vector<>(rowMap.values());
				rows.sort(Comparator.<Row,String>comparing(row->row.filename));
			}
			
			private static class Row {
				
				final String filename;
				File generalScreenshot;
				final HashMap<Long, ScreenShot> gameScreenShots;
				
				Row(String filename) {
					this.filename = filename;
					generalScreenshot = null;
					gameScreenShots = new HashMap<>();
				}
			}

			@Override public int getRowCount() {
				return rows.size();
			}

			@Override public Object getValueAt(int rowIndex, int columnIndex, ColumnID columnID) {
				Row row = getRow(rowIndex);
				if (row==null) return null;
				
				switch (columnID) {
				case Name     : return row.filename;
				case InGeneral: return row.generalScreenshot!=null;
				case InGame   : return !row.gameScreenShots.isEmpty();
				case Player   : return getPlayerNames(row.gameScreenShots.keySet());
				}
				return null;
			}

			private String getPlayerNames(Collection<Long> set) {
				Iterable<String> it = ()->set.stream().map(Data::getPlayerName).sorted().iterator();
				return String.join(", ", it);
			}

			private Row getRow(int rowIndex) {
				if (rowIndex<0) return null;
				if (rowIndex>=rows.size()) return null;
				return rows.get(rowIndex);
			}
			
		}
	}

}
