package net.schwarzbaer.java.tools.steaminspector;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.GeneralIcons;
import net.schwarzbaer.java.lib.gui.ImageView;
import net.schwarzbaer.java.lib.gui.ProgressDialog;
import net.schwarzbaer.java.lib.gui.StandardDialog;
import net.schwarzbaer.java.lib.gui.StandardMainWindow;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.java.lib.system.Settings.DefaultAppSettings.SplitPaneDividersDefinition;
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

	private static final Icon ICON_DELETE_DISABLED = GeneralIcons.GrayCommandIcons.Delete_Dis.getIcon();
	private static final Icon ICON_DELETE          = GeneralIcons.GrayCommandIcons.Delete.getIcon();
	private static final Icon ICON_RELOAD_DISABLED = GeneralIcons.GrayCommandIcons.Reload_Dis.getIcon();
	private static final Icon ICON_RELOAD          = GeneralIcons.GrayCommandIcons.Reload.getIcon();
	
	private final boolean isStandAlone;
	private final Window mainWindow;
	private final JFileChooser folderChooser;
	private File generalScreenshotFolder;
	private final HashMap<Integer,HashMap<String,File>> generalScreenshots;
	private final GuiVariant<? extends Window> gui;
	private final HashSet<Integer> gamesWithScreenshots;
	private final ImageListPanel imageListPanel;
	private JSplitPane rightPanel;
	private GamesPanel leftPanel;

	SteamScreenshotsCleanUp(Window parent) {
		//ImageViewPanel.ImageVariant.test();
		isStandAlone = parent==null;
		
		generalScreenshotFolder = null;
		generalScreenshots = new HashMap<>();
		gamesWithScreenshots = new HashSet<>();
		
		if (isStandAlone) gui = new GuiVariant.StandAlone(    "SteamScreenshots CleanUp");
		else              gui = new GuiVariant.Dialog(parent, "SteamScreenshots CleanUp");
		mainWindow = gui.getWindow();
		
		folderChooser = new JFileChooser("./");
		folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		folderChooser.setMultiSelectionEnabled(false);
		
		int orientation = SteamInspector.settings.getInt(ValueKey.SSCU_RightSplitPaneOrientation, JSplitPane.VERTICAL_SPLIT);
		rightPanel = new JSplitPane(orientation, true);
		ButtonGroup bg = new ButtonGroup();
		String titleV = "below table"; // "Vertical Split";
		String titleH = "right of table"; // "Horizontal Split";
		Component[] extraOptions = new Component[] {
				SteamInspector.createRadioButton(titleV, orientation==JSplitPane.VERTICAL_SPLIT  , true, bg, b->setRightPanelOrientation(JSplitPane.VERTICAL_SPLIT  )),
				SteamInspector.createRadioButton(titleH, orientation==JSplitPane.HORIZONTAL_SPLIT, true, bg, b->setRightPanelOrientation(JSplitPane.HORIZONTAL_SPLIT))
		};
		
		ImageViewPanel imageViewPanel = new ImageViewPanel(extraOptions);
		imageListPanel = new ImageListPanel(this, imageViewPanel);
		
		rightPanel.setTopComponent   (imageListPanel);
		rightPanel.setBottomComponent(imageViewPanel);
		
		leftPanel = new GamesPanel(this, 500, 800);
		
		leftPanel     .setBorder(BorderFactory.createTitledBorder("Games"));
		imageListPanel.setBorder(BorderFactory.createTitledBorder("Screenshots"));
		imageViewPanel.setBorder(BorderFactory.createTitledBorder("Image"));
		
		JSplitPane contentPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
		contentPane.setLeftComponent (leftPanel);
		contentPane.setRightComponent(rightPanel);
		
		gui.createGUI(contentPane);
		SteamInspector.settings.registerExtraWindow(mainWindow, ValueKey.SSCU_WindowX, ValueKey.SSCU_WindowY, ValueKey.SSCU_WindowWidth, ValueKey.SSCU_WindowHeight);
		SteamInspector.settings.registerSplitPaneDividers(
				new SplitPaneDividersDefinition<>(mainWindow, ValueKey.class)
				.add(contentPane, ValueKey.SSCU_MainSplitPaneDivider)
				.add(rightPanel, ValueKey.SSCU_RightSplitPaneDivider),
				false
		);
	}
	
	private void setRightPanelOrientation(int value) {
		rightPanel.setOrientation(value);
		SteamInspector.settings.putInt(ValueKey.SSCU_RightSplitPaneOrientation, value);
	}

	private void showGUI() {
		gui.showGUI();
	}
	
	SteamScreenshotsCleanUp initialize() {
		loadData("Initialize", false);
		return this;
	}

	private void loadData(String title, boolean forceReload)
	{
		ProgressDialog.runWithProgressDialog(mainWindow, title, 300, pd->{
			generalScreenshotFolder = getFolder(pd, "Determine General Screenshot Folder", "Select General Screenshot Folder", ValueKey.SSCU_GeneralScreenshotFolder);
			if (generalScreenshotFolder==null) return;
			
			SteamInspector.showIndeterminateTask(pd, "Load Game Data");
			if (Data.isEmpty() || (forceReload && isStandAlone))
				Data.loadData();
			gamesWithScreenshots.clear();
			Data.games.forEach((gameID,game)->{
				if (!game.screenShots.isEmpty())
					gamesWithScreenshots.add(game.appID);
			});
			
			SteamInspector.showIndeterminateTask(pd, "Scan General Screenshot Folder");
			scanGeneralScreenshotFolder();
			
			SteamInspector.showIndeterminateTask(pd, "Update GUI", ()->{
				leftPanel.updateContent();
			});
		});
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
	
	private File getFolder(ProgressDialog pd, String taskTitle, String dialogTitle, ValueKey valueKey) {
		SteamInspector.showIndeterminateTask(pd, taskTitle);
		File folder = SteamInspector.settings.getFile(valueKey, null);
		if (folder==null || !folder.isDirectory()) {
			folder = null;
			folderChooser.setDialogTitle(dialogTitle);
			if (folderChooser.showOpenDialog(mainWindow)==JFileChooser.APPROVE_OPTION) {
				folder = folderChooser.getSelectedFile();
				SteamInspector.settings.putFile(valueKey, folder);
			}
		}
		return folder;
	}

	private static void addExtraOptionsToToolbar(Component[][] extraOptions, JToolBar optionsPanel) {
		if (extraOptions!=null)
			for (Component[] group : extraOptions )
				if (group!=null && group.length>0) {
					optionsPanel.addSeparator();
					for (Component option : group )
						optionsPanel.add(option);
				}
	}

	private Vector<Integer> getGameIDs_sorted()
	{
		
		HashSet<Integer> gameIDs = new HashSet<>(gamesWithScreenshots);
		gameIDs.addAll(generalScreenshots.keySet());
		
		Vector<Integer> gameIDs_sorted = new Vector<>(gameIDs);
		gameIDs_sorted.sort(Comparator
				.<Integer,String>comparing(Data::getGameTitle)
				.thenComparing(Comparator.naturalOrder()));
		
		return gameIDs_sorted;
	}

	private static String getSizeStr(long size)
	{
		double sizeD = size;
		if (sizeD < 1024) return String.format(Locale.ENGLISH, "%d B", size);
		sizeD = sizeD / 1024;
		if (sizeD < 1024) return String.format(Locale.ENGLISH, "%1.2f kB", sizeD);
		sizeD = sizeD / 1024;
		if (sizeD < 1024) return String.format(Locale.ENGLISH, "%1.2f MB", sizeD);
		sizeD = sizeD / 1024;
		if (sizeD < 1024) return String.format(Locale.ENGLISH, "%1.2f GB", sizeD);
		sizeD = sizeD / 1024;
		return String.format(Locale.ENGLISH, "%1.2f TB", sizeD);
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
			@Override public void createGUI(JComponent contentPane) {
				contentPane.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
				mainWindow.startGUI(contentPane);
			}
			@Override public void showGUI() {}
		}
		static class Dialog extends GuiVariant<StandardDialog> {
			Dialog(Window parent, String title) { super(new StandardDialog(parent, title)); }
			@Override void createGUI(JComponent contentPane) {
				mainWindow.createGUI(contentPane, SteamInspector.createButton("Close", true, e->mainWindow.closeDialog())); }
			@Override void showGUI() { mainWindow.showDialog(); }
		}
	}

	private static class ImageViewPanel extends JPanel {
		private static final long serialVersionUID = -4026791525658983256L;
		
		private final JComboBox<ImageVariant> comboBox;
		private final ImageView imageView;
		private boolean selectionIsCausedByApp;
		private boolean dontChangeImageView;
		private ImageVariant preferredVariant;

		private File generalScreenshot;
		private HashMap<Long, ScreenShot> gameScreenShots;

		ImageViewPanel(Component... extraOptions) {
			this(new Component[][] { extraOptions });
		}
		ImageViewPanel(Component[][] extraOptions) {
			super(new BorderLayout());
			preferredVariant = null;
			selectionIsCausedByApp = false;
			dontChangeImageView = false;
			
			imageView = new ImageView(300, 200);
			
			comboBox = new JComboBox<>();
			comboBox.addActionListener(e->{
				int index = comboBox.getSelectedIndex();
				ImageVariant selectedVariant = index<0 ? null : comboBox.getItemAt(index);
				if (!selectionIsCausedByApp)
					preferredVariant = selectedVariant;
				if (!dontChangeImageView)
					imageView.setImage(getImage(selectedVariant));
			});
			
			JToolBar options = new JToolBar();
			options.setFloatable(false);
			options.add(comboBox);
			
			addExtraOptionsToToolbar(extraOptions, options);
			
			add(options, BorderLayout.PAGE_START);
			add(imageView, BorderLayout.CENTER);
			
		}
		
		private static class ImageVariant {
			
			private final boolean isGeneralScreenshot;
			private final Long playerID;
			private final String playerName;
		
			ImageVariant(boolean isGeneralScreenshot, Long playerID) {
				this.isGeneralScreenshot = isGeneralScreenshot;
				this.playerID = playerID;
				playerName = this.playerID==null ? null : Data.getPlayerName(this.playerID);
			}

			@Override
			public int hashCode() {
				int part1 = (playerID==null ? -1 : (int) (playerID & 0x7FFFFFFF))<<1;
				int part2 = isGeneralScreenshot ? 1 : 0;
				return part1 | part2;
			}

			@Override
			public boolean equals(Object obj) {
				if (obj instanceof ImageVariant) {
					ImageVariant other = (ImageVariant) obj;
					if (this.isGeneralScreenshot && other.isGeneralScreenshot) return true;
					if (this.isGeneralScreenshot != other.isGeneralScreenshot) return false;
					if (this.playerID==null && other.playerID==null) return true;
					if (this.playerID!=null && other.playerID!=null)
						return this.playerID.longValue()==other.playerID.longValue();
				}
				return false;
			}

			@Override
			public String toString() {
				if (isGeneralScreenshot) return "General Screenshot";
				if (playerName!=null) return playerName; 
				if (playerID!=null) return String.format("Player %d",playerID);
				return "?????";
			}

			ImageVariant findSimilar(Vector<ImageVariant> variants) {
				if (variants.contains(this))
					return this;
				
				if (!isGeneralScreenshot && playerID!=null) {
					for (ImageVariant v : variants) {
						if (!v.isGeneralScreenshot && v.playerID!=null)
							return v;
					}
				}
				
				if (variants.isEmpty()) return null;
				return variants.firstElement();
			}
			
			@SuppressWarnings("unused")
			private static void test() {
				Vector<ImageVariant> variants = new Vector<>();
				variants.add(new ImageVariant(true, null));
				testSimilar(variants,new ImageVariant(false, 2L));
				variants.add(new ImageVariant(false, 1L));
				variants.add(new ImageVariant(false, 2L));
				testSimilar(variants,new ImageVariant(false, 2L));
				testSimilar(variants,new ImageVariant(false, 3L));
				testSimilar(variants,new ImageVariant(true, 3L));
				
				variants.clear();
				variants.add(new ImageVariant(false, 1L));
				variants.add(new ImageVariant(false, 2L));
				
				testSimilar(variants,new ImageVariant(false, 2L));
				testSimilar(variants,new ImageVariant(false, 3L));
				testSimilar(variants,new ImageVariant(true, 3L));
				testSimilar(variants,new ImageVariant(true, null));
			}

			private static void testSimilar(Vector<ImageVariant> variants, ImageVariant base) {
				Function<ImageVariant, String> toString = iv->iv==null ? "<null>" : String.format("ImageVariant(%s,%d)", iv.isGeneralScreenshot, iv.playerID);
				Iterable<String> it = ()->variants.stream().map(toString).iterator();
				System.out.println();
				System.out.printf("Variants:%n    %s%n", String.join(",\r\n    ", it));
				System.out.printf("Base    :%n    %s%n", toString.apply(base));
				System.out.printf("Similar :%n    %s%n", toString.apply(base.findSimilar(variants)));
			}
		}

		private BufferedImage getImage(ImageVariant selectedVariant) {
			if (selectedVariant==null) return null;
			
			if (selectedVariant.isGeneralScreenshot) {
				if (generalScreenshot==null) return null;
				if (!generalScreenshot.isFile()) return null;
				return readImage(generalScreenshot);
			}
			
			if (selectedVariant.playerID!=null) {
				if (gameScreenShots==null) return null;
				ScreenShot screenShot = gameScreenShots.get(selectedVariant.playerID);
				if (screenShot==null) return null;
				if (screenShot.image==null) return null;
				if (!screenShot.image.isFile()) return null;
				return readImage(screenShot.image);
			}
			
			return null;
		}

		private BufferedImage readImage(File file) {
			try {
				return ImageIO.read(file);
			} catch (IOException e) {
				System.err.printf("IOException while reading image \"%s\": %s", file.getAbsolutePath(), e.getMessage());
				return null;
			}
		}

		void clearData() {
			setData(null, null);
		}

		void setData(File generalScreenshot, HashMap<Long, ScreenShot> gameScreenShots) {
			this.generalScreenshot = generalScreenshot;
			this.gameScreenShots = gameScreenShots;
			
			Vector<ImageVariant> variants = new Vector<>();
			
			if (this.generalScreenshot!=null && this.generalScreenshot.isFile())
				variants.add(new ImageVariant(true,null));
			
			if (this.gameScreenShots!=null)
				this.gameScreenShots.forEach((playerID,screenShot)->{
					if (screenShot.image.isFile())
						variants.add(new ImageVariant(false,playerID));
				});
			
			setCombobox(variants);
		}

		private void setCombobox(Vector<ImageVariant> variants) {
			ImageVariant selectedVariant;
			if (preferredVariant==null)
				selectedVariant = variants.isEmpty() ? null : variants.firstElement();
			
			else
				selectedVariant = preferredVariant.findSimilar(variants);
			
			selectionIsCausedByApp = true;
			dontChangeImageView = true;
			comboBox.setModel(new DefaultComboBoxModel<>(variants));
			dontChangeImageView = false;
			comboBox.setSelectedItem(selectedVariant);
			selectionIsCausedByApp = false;
		}
	}

	private static class ImageListPanel extends JPanel {
		private static final long serialVersionUID = 7167176806130750722L;

		enum ViewType {
			Details  ("Details"   , ViewContainer.Details::new),
			ImageGrid("Image Grid", null),
			;
			private final Function<ImageListPanel,ViewContainer> createVC;
			private final String label;
			ViewType(String label, Function<ImageListPanel,ViewContainer> createVC) { this.label = label; this.createVC = createVC;}
			ViewContainer createViewContainer(ImageListPanel main) { return createVC==null ? null : createVC.apply(main); }
		}
		
		private final SteamScreenshotsCleanUp main;
		private final ImageViewPanel imageViewPanel;
		private final JScrollPane scrollPane;
		private int currentGameID;
		private ViewType currentViewType;
		private HashMap<String, File> generalScreenshots;
		private HashMap<Long, ScreenShotList> gameScreenshots;
		private ViewContainer currentViewContainer;
		
		ImageListPanel(SteamScreenshotsCleanUp main, ImageViewPanel imageViewPanel, Component... extraOptions) {
			this(main, imageViewPanel, new Component[][] { extraOptions });
		}
		ImageListPanel(SteamScreenshotsCleanUp main, ImageViewPanel imageViewPanel, Component[][] extraOptions) {
			super(new BorderLayout());
			this.main = main;
			this.imageViewPanel = imageViewPanel;
			currentGameID = -1;
			
			generalScreenshots = null;
			gameScreenshots = null;
			currentViewType = SteamInspector.settings.getEnum(ValueKey.SSCU_ViewType, ViewType.Details, ViewType.class);
			currentViewContainer = null;
			
			JToolBar options = new JToolBar();
			options.setFloatable(false);
			
			//options.add( SteamInspector.createButton("Reload Data", ICON_RELOAD, ICON_RELOAD_DISABLED, true, false, e->{
			//	// TO/DO
			//}) );
			options.add(SteamInspector.createButton("Remove marked images", ICON_DELETE, ICON_DELETE_DISABLED, true, false, e->removeMarkedImages()));
			options.addSeparator();
			
			ButtonGroup bg = new ButtonGroup();
			for (ViewType vt : ViewType.values())
				options.add(SteamInspector.createRadioButton(vt.label, currentViewType==vt, vt.createVC!=null, bg, b->setViewType(vt)));
			
			addExtraOptionsToToolbar(extraOptions, options);
			
			scrollPane = new JScrollPane();
			
			add(options, BorderLayout.PAGE_START);
			add(scrollPane, BorderLayout.CENTER);
			buildView();
		}
		
		private void removeMarkedImages()
		{
			if (currentViewContainer==null) return;
			if (!currentViewContainer.hasMarkedScreenshots()) return;
			
			String msg = "Do you really want to delete the marked screenshots?";
			String title = "Are you sure?";
			if (JOptionPane.YES_OPTION!=JOptionPane.showConfirmDialog(main.mainWindow, msg, title, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE))
				return;
			
			currentViewContainer.forEachMarkedScreenshot(new ImageListPanel.ViewContainer.MarkedScreenshotAction() {
				@Override public boolean processGeneralScreenshot(File screenshot) {
					boolean success = screenshot.isFile() ? screenshot.delete() : true;
					if (success) return false;
					else         return true;
				}
				@Override public boolean processGameScreenshots(HashMap<Long, ScreenShot> screenshots) {
					boolean allSuccessful = true;
					for (ScreenShot screenshot : screenshots.values()) {
						if (screenshot.image.isFile()) {
							boolean success = screenshot.image.delete();
							if (!success) allSuccessful = false;
						}
					}
					if (allSuccessful) return false;
					else               return true;
				}
			});
			currentViewContainer.updateView();
			main.leftPanel.updateGame(currentGameID);
		}
		private void setViewType(ViewType viewType) {
			boolean changeAllowed = checkUnsavedChanges();
			if (changeAllowed) {
				currentViewType = viewType;
				SteamInspector.settings.putEnum(ValueKey.SSCU_ViewType, viewType);
				buildView();
			}
		}

		void setData(int gameID) {
			boolean changeAllowed = checkUnsavedChanges();
			if (changeAllowed) {
				this.currentGameID = gameID;
				generalScreenshots = main.generalScreenshots.get(gameID);
				Game game = Data.games.get(gameID);
				gameScreenshots = game==null ? null : game.screenShots;
				buildView();
			}
		}

		void clearData() {
			boolean changeAllowed = checkUnsavedChanges();
			if (changeAllowed) {
				generalScreenshots = null;
				gameScreenshots = null;
				buildView();
			}
		}

		private boolean checkUnsavedChanges() {
			if (currentViewContainer==null) return true;
			if (!currentViewContainer.hasMarkedScreenshots()) return true;
			
			String[] msg = new String[] {
					"There are unsaved changes in current view?",
					"Do you really want to change the view without saving them?"
			};
			String title = "Unsaved Changes";
			return JOptionPane.YES_OPTION==JOptionPane.showConfirmDialog(main.mainWindow, msg, title, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		}
		
		private void buildView() {
			currentViewContainer = currentViewType.createViewContainer(this);
			if (currentViewContainer!=null) {
				scrollPane.setViewportView( currentViewContainer.createComponent());
				imageViewPanel.clearData();
			}
		}
		
		private static abstract class ViewContainer {
			protected final ImageListPanel parent;
			ViewContainer(ImageListPanel parent) {
				this.parent = parent;
			}
			abstract void updateView();
			abstract void forEachMarkedScreenshot(MarkedScreenshotAction action);
			abstract boolean hasMarkedScreenshots();
			abstract Component createComponent();
			
			interface MarkedScreenshotAction {
				boolean processGeneralScreenshot(File screenshot);
				boolean processGameScreenshots  (HashMap<Long, ScreenShot> screenshots);
			}
			
			private static class Details extends ViewContainer {

				private DetailsTableModel tableModel;
				private JTable table;

				Details(ImageListPanel parent) {
					super(parent);
					table = null;
					tableModel = null;
				}

				@Override
				void updateView() {
					tableModel.fireTableUpdate();
				}

				@Override
				void forEachMarkedScreenshot(MarkedScreenshotAction action) {
					tableModel.forEachMarkedScreenshot(action);
				}

				@Override boolean hasMarkedScreenshots() {
					return tableModel.hasMarkedScreenshots();
				}

				@Override
				Component createComponent() {
					tableModel = new DetailsTableModel(parent.generalScreenshots, parent.gameScreenshots);
					
					table = new JTable(tableModel);
					table.setRowSorter(new Tables.SimplifiedRowSorter(tableModel));
					table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
					table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
					table.getSelectionModel().addListSelectionListener(e->{
						int rowCount = table.getSelectedRowCount();
						if (rowCount != 1)
							parent.imageViewPanel.clearData();
						else {
							DetailsTableModel.Row row = getSelectedRow(table, tableModel);
							if (row==null)
								parent.imageViewPanel.clearData();
							else
								parent.imageViewPanel.setData(row.generalScreenshot, row.gameScreenShots);
						}
					});
					//table.addKeyListener(createTestKeyListener());
					table.addKeyListener(new TableKeyListener(tableModel, table));
					
					tableModel.setTable(table);
					tableModel.setColumnWidths(table);
					tableModel.setDefaultCellEditorsAndRenderers();
					
					new TableContextMenu(table, tableModel);
					
					return table;
				}
				
			}
		}

		private static DetailsTableModel.Row getSelectedRow(JTable table, DetailsTableModel tableModel) {
			int selectedRowV = table.getSelectedRow();
			int selectedRowM = selectedRowV<0 ? -1 : table.convertRowIndexToModel(selectedRowV);
			return selectedRowM<0 ? null : tableModel.getRow(selectedRowM);
		}

		@SuppressWarnings("unused")
		private KeyListener createTestKeyListener() {
			return new KeyListener() {
				
				@Override public void keyTyped   (KeyEvent e) { print("Typed"   ,e); }
				@Override public void keyReleased(KeyEvent e) { print("Released",e); }
				@Override public void keyPressed (KeyEvent e) { print("Pressed" ,e); }

				private void print(String label, KeyEvent e) {
					System.out.printf("Key%s: %s%n", label, toString(e));
				}
				private String toString(KeyEvent e) {
					StringBuilder sb = new StringBuilder();
					sb.append(String.format("ID=%d[%s], ", e.getID(), getIDstr(e.getID())));
					sb.append(String.format("Ch=%s[%d], ", e.getKeyChar(), (int)e.getKeyChar()));
					sb.append(String.format("KC=%d[%s]%s, ", e.getKeyCode(), KeyEvent.getKeyText(e.getKeyCode()), e.getKeyCode()==KeyEvent.VK_1));
					sb.append(String.format("ExKC=%d[%s]%s, ", e.getExtendedKeyCode(), KeyEvent.getKeyText(e.getExtendedKeyCode()), e.getExtendedKeyCode()==KeyEvent.VK_1));
					return sb.toString();
				}
				private String getIDstr(int id) {
					switch (id) {
					case KeyEvent.KEY_TYPED   : return "KEY_TYPED";
					case KeyEvent.KEY_PRESSED : return "KEY_PRESSED";
					case KeyEvent.KEY_RELEASED: return "KEY_RELEASED";
					}
					return String.format("KeyID[%d]", id);
				}
			};
		}
		
		private static class TableKeyListener implements KeyListener {
			private enum Target { General, Game }
			
			private final DetailsTableModel tableModel;
			private final JTable table;
		
			private TableKeyListener(DetailsTableModel tableModel, JTable table) {
				this.tableModel = tableModel;
				this.table = table;
			}
		
			@Override public void keyTyped(KeyEvent e) {}
			@Override public void keyPressed(KeyEvent e) {}
			@Override public void keyReleased(KeyEvent e) {
				if (e.isConsumed()) return;
				
				switch (e.getKeyCode()) {
				case KeyEvent.VK_1: toggleMarker(e,Target.General); break;
				case KeyEvent.VK_2: toggleMarker(e,Target.Game   ); break;
				}
			}
		
			private void toggleMarker(KeyEvent e, Target target) {
				
				int rowCount = table.getSelectedRowCount();
				if (rowCount!=1) return;
				
				int selectedRowV = table.getSelectedRow();
				int selectedRowM = selectedRowV<0 ? -1 : table.convertRowIndexToModel(selectedRowV);
				DetailsTableModel.Row row = selectedRowM<0 ? null : tableModel.getRow(selectedRowM);
				if (row==null) return;
				
				switch (target) {
				case General:
					setValues(
							e, selectedRowM,
							row::canSetDeleteGeneralScreenshot,
							row::isDeleteGeneralScreenshot,
							row::setDeleteGeneralScreenshot,
							DetailsTableModel.ColumnID.DeleteGeneral);
					break;
					
				case Game:
					setValues(
							e, selectedRowM,
							row::canSetDeleteGameScreenshot,
							row::isDeleteGameScreenshot,
							row::setDeleteGameScreenshot,
							DetailsTableModel.ColumnID.DeleteGame);
					break;
				}
			}

			private void setValues(KeyEvent e, int selectedRowM, Supplier<Boolean> can, Supplier<Boolean> is, Consumer<Boolean> set, DetailsTableModel.ColumnID columnID) {
				if (can.get()) {
					//System.out.printf("SetDeleteScreenshot(%d, %s, %s->%s)%n", selectedRowM, columnID, is.get(), !is.get());
					TableCellEditor cellEditor = table.getCellEditor();
					if (cellEditor!=null) cellEditor.cancelCellEditing();
					e.consume();
					set.accept(!is.get());
					tableModel.fireTableCellUpdate(selectedRowM, columnID);
				} 
			}
		}

		private static class TableContextMenu extends ContextMenu {
			private static final long serialVersionUID = 6571205991872222664L;
			private int[] selectedRows;
			private final JTable table;
			private final DetailsTableModel tableModel;

			TableContextMenu(JTable table, DetailsTableModel tableModel) {
				this.table = table;
				this.tableModel = tableModel;
				selectedRows = null;
				
				add(SteamInspector.createMenuItem("Set deletion marker for general screenshots in selection", true, e->{
					forEachSelectedRow(row->row.setDeleteGeneralScreenshot(true), DetailsTableModel.ColumnID.DeleteGeneral);
				}));
				
				add(SteamInspector.createMenuItem("Remove deletion marker for general screenshots in selection", true, e->{
					forEachSelectedRow(row->row.setDeleteGeneralScreenshot(false), DetailsTableModel.ColumnID.DeleteGeneral);
				}));
				
				add(SteamInspector.createMenuItem("Set deletion marker for game screenshots in selection", true, e->{
					forEachSelectedRow(row->row.setDeleteGameScreenshot(true), DetailsTableModel.ColumnID.DeleteGame);
				}));
				
				add(SteamInspector.createMenuItem("Remove deletion marker for game screenshots in selection", true, e->{
					forEachSelectedRow(row->row.setDeleteGameScreenshot(false), DetailsTableModel.ColumnID.DeleteGame);
				}));
				
				addContextMenuInvokeListener((comp, x, y) -> {
					selectedRows = this.table.getSelectedRows();
				});
				
				addTo(table);
			}

			private void forEachSelectedRow(Consumer<DetailsTableModel.Row> action, DetailsTableModel.ColumnID column) {
				if (selectedRows==null) return;
				DetailsTableModel.Row[] rows = getRows(selectedRows);
				for (DetailsTableModel.Row row : rows)
					if (row != null) action.accept(row);
				tableModel.fireTableColumnUpdate(column);
			}

			private DetailsTableModel.Row[] getRows(int[] rowIndexesV) {
				DetailsTableModel.Row[] rows = new DetailsTableModel.Row[rowIndexesV==null ? 0 : rowIndexesV.length];
				if (rowIndexesV!=null)
					for (int i=0; i<rowIndexesV.length; i++) {
						int rowIndexV = rowIndexesV[i];
						int rowIndexM = rowIndexV<0 ? -1 : table.convertRowIndexToModel(rowIndexV);
						rows[i] = rowIndexM<0 ? null : tableModel.getRow(rowIndexM);
					}
				return rows;
			}
			
		}
		
		private static class DetailsTableCellRenderer implements TableCellRenderer {

			private static final Color COLOR_DELETED_ITEM = new Color(0xFF5A5A);
			private static final Color COLOR_EDITABLE     = new Color(0xFFF9ED);
			private final DetailsTableModel tableModel;
			private final Tables.LabelRendererComponent labelComp;
			private final Tables.CheckBoxRendererComponent checkboxComp;
			
			private DetailsTableCellRenderer(DetailsTableModel tableModel) {
				this.tableModel = tableModel;
				labelComp    = new Tables.LabelRendererComponent();
				checkboxComp = new Tables.CheckBoxRendererComponent();
				labelComp   .setHorizontalAlignment(SwingConstants.LEFT);
				checkboxComp.setHorizontalAlignment(SwingConstants.CENTER);
			}

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowV, int columnV) {
				int    rowM =    rowV<0 ? -1 : table.   convertRowIndexToModel(   rowV);
				int columnM = columnV<0 ? -1 : table.convertColumnIndexToModel(columnV);
				DetailsTableModel.Row  row = rowM<0 ? null : tableModel.getRow(rowM);
				DetailsTableModel.ColumnID columnID = columnM<0 ? null : tableModel.getColumnID(columnM);
				
				Supplier<Color> getCustomBackground = ()->{
					if (row==null) return null;
					Color bgColor = null;
					if (columnID!=null)
						switch (columnID)
						{
							case Name:
								break;
							
							case InGeneral:
							case DeleteGeneral:
								if (row.isDeleteGeneralScreenshot())
									bgColor = COLOR_DELETED_ITEM;
								break;
							
							case InGame:
							case Player:
							case DeleteGame:
								if (row.isDeleteGameScreenshot())
									bgColor = COLOR_DELETED_ITEM;
								break;
						}
					if (bgColor==null && tableModel.isCellEditable(rowM, columnM, columnID))
						bgColor = COLOR_EDITABLE;
					return bgColor;
				};
				Component rendererComp;
				
				if (value instanceof Boolean) {
					Boolean bValue = (Boolean) value;
					checkboxComp.configureAsTableCellRendererComponent(table, bValue, null, isSelected, hasFocus, null, getCustomBackground);
					rendererComp = checkboxComp;
					
				} else {
					String valueStr = value==null ? null : value.toString();
					labelComp.configureAsTableCellRendererComponent(table, null, valueStr, isSelected, hasFocus, getCustomBackground, null);
					rendererComp = labelComp;
				}
				
				return rendererComp;
			}
			
		}

		private static class DetailsTableModel extends Tables.SimplifiedTableModel<DetailsTableModel.ColumnID> {

			enum ColumnID implements Tables.SimplifiedColumnIDInterface {
				Name         ("File Name"        , String .class, 110),
				InGeneral    ("In General"       , Boolean.class,  65),
				InGame       ("In Game"          , Boolean.class,  55),
				Player       ("By Player"        , String .class, 100),
				DeleteGeneral("Delete in General", Boolean.class, 100),
				DeleteGame   ("Delete in Game"   , Boolean.class, 100),
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
						for (ScreenShot screenShot : list) {
							String name = screenShot.image.getName();
							int pos = name.lastIndexOf(".");
							if (pos>0) name = name.substring(0, pos);
							Row row = rowMap.get(name);
							if (row==null) rowMap.put(name, row = new Row(name));
							row.gameScreenShots.put(playerID,screenShot);
						}
					});
				}
				
				rows = new Vector<>(rowMap.values());
				rows.sort(Comparator.<Row,String>comparing(row->row.filename));
			}
			
			public void forEachMarkedScreenshot(ViewContainer.MarkedScreenshotAction action) {
				for (Row row : rows) {
					if (row.isDeleteGeneralScreenshot()) {
						boolean result = action.processGeneralScreenshot(row.generalScreenshot);
						row.setDeleteGeneralScreenshot(result);
					}
					if (row.isDeleteGameScreenshot()) {
						boolean result = action.processGameScreenshots(row.gameScreenShots);
						row.setDeleteGameScreenshot(result);
					}
				}
			}

			boolean hasMarkedScreenshots() {
				for (Row row : rows) {
					if (row.isDeleteGeneralScreenshot()) return true;
					if (row.isDeleteGameScreenshot   ()) return true;
				}
				return false;
			}

			void setDefaultCellEditorsAndRenderers() {
				DetailsTableCellRenderer tcr = new DetailsTableCellRenderer(this);
				setAllDefaultRenderers(columnClass->tcr);
			}

			private static class Row {
				
				final String filename;
				File generalScreenshot;
				final HashMap<Long, ScreenShot> gameScreenShots;
				private boolean deleteGeneralScreenshot;
				private boolean deleteGameScreenshot;
				
				Row(String filename) {
					this.filename = filename;
					generalScreenshot = null;
					gameScreenShots = new HashMap<>();
					deleteGeneralScreenshot = false;
					deleteGameScreenshot    = false;
				}
				
				boolean hasAGeneralScreenshot() {
					if (generalScreenshot==null) return false;
					if (!generalScreenshot.isFile()) return false;
					return true;
				}
				
				boolean hasAGameScreenshot() {
					if (gameScreenShots.isEmpty()) return false;
					for (ScreenShot screenShot : gameScreenShots.values())
						if (screenShot.image!=null && screenShot.image.isFile())
							return true;
					return false;
				}

				boolean canSetDeleteGeneralScreenshot() { return hasAGeneralScreenshot(); }
				boolean canSetDeleteGameScreenshot   () { return hasAGameScreenshot(); }
				boolean isDeleteGeneralScreenshot    () { return deleteGeneralScreenshot; }
				boolean isDeleteGameScreenshot       () { return deleteGameScreenshot; }

				void setDeleteGeneralScreenshot(boolean deleteGeneralScreenshot) {
					if (canSetDeleteGeneralScreenshot())
						this.deleteGeneralScreenshot = deleteGeneralScreenshot;
					else
						this.deleteGeneralScreenshot = false;
				}

				void setDeleteGameScreenshot(boolean deleteGameScreenshot) {
					if (canSetDeleteGameScreenshot())
						this.deleteGameScreenshot = deleteGameScreenshot;
					else
						this.deleteGameScreenshot = false;
				}

				String getPlayerNames() {
					Iterable<String> it = ()->gameScreenShots.keySet().stream().map(Data::getPlayerName).sorted().iterator();
					return String.join(", ", it);
				}
			}
			
			

			@Override
			public void fireTableColumnUpdate(ColumnID columnID) {
				super.fireTableColumnUpdate(columnID);
			}

			@Override
			public void fireTableCellUpdate(int rowIndex, ColumnID columnID) {
				super.fireTableCellUpdate(rowIndex, columnID);
			}

			@Override public int getRowCount() {
				return rows.size();
			}

			@Override public Object getValueAt(int rowIndex, int columnIndex, ColumnID columnID) {
				Row row = getRow(rowIndex);
				if (row==null) return null;
				
				switch (columnID) {
				case Name     : return row.filename;
				case InGeneral: return row.hasAGeneralScreenshot();
				case InGame   : return row.hasAGameScreenshot();
				case Player   : return row.getPlayerNames();
				case DeleteGeneral: return !row.canSetDeleteGeneralScreenshot() ? null : row.isDeleteGeneralScreenshot();
				case DeleteGame   : return !row.canSetDeleteGameScreenshot   () ? null : row.isDeleteGameScreenshot   ();
				}
				return null;
			}

			@Override
			protected boolean isCellEditable(int rowIndex, int columnIndex, ColumnID columnID) {
				Row row = getRow(rowIndex);
				if (row==null) return false;
				
				if (columnID==ColumnID.DeleteGame    && row.canSetDeleteGameScreenshot   ()) return true;
				if (columnID==ColumnID.DeleteGeneral && row.canSetDeleteGeneralScreenshot()) return true;
				return false;
			}

			@Override
			protected void setValueAt(Object aValue, int rowIndex, int columnIndex, ColumnID columnID) {
				Row row = getRow(rowIndex);
				if (row==null) return;
				
				switch (columnID) {
				case InGame: case InGeneral: case Name: case Player: break;
				case DeleteGeneral: setMarker(aValue, rowIndex, columnID, row::setDeleteGeneralScreenshot); break;
				case DeleteGame   : setMarker(aValue, rowIndex, columnID, row::setDeleteGameScreenshot   ); break;
				}
			}
			
			private void setMarker(Object aValue, int rowIndex, ColumnID columnID, Consumer<Boolean> setValue) {
				if (aValue instanceof Boolean) {
					boolean value = ((Boolean) aValue).booleanValue();
					//System.out.printf("DetailsTableModel.setMarker(%d, %s, ??->%s)%n", rowIndex, columnID, value);
					setValue.accept(value);
				}
			}

			private Row getRow(int rowIndex) {
				if (rowIndex<0) return null;
				if (rowIndex>=rows.size()) return null;
				return rows.get(rowIndex);
			}
			
		}
	}
	
	private static class GamesPanel extends JPanel
	{
		private static final long serialVersionUID = -7570230016344351538L;
		interface GamesPanelContent
		{
			void updateGame(int gameID);
			Component getComponent();
			void updateContent();
		}
		
		enum Variant
		{
			Table("Table", GamesTable::new),
			Tree ("Tree" , GamesTree::new),
			;
			private final Function<SteamScreenshotsCleanUp, GamesPanelContent> createGuiComp;
			Variant(String label, Function<SteamScreenshotsCleanUp, GamesPanelContent> createGuiComp)
			{
				this.createGuiComp = createGuiComp;
			}
		}
	
		private final SteamScreenshotsCleanUp main;
		private final EnumMap<GamesPanel.Variant, GamesPanelContent> variants;
		//private final JLabel statusOut;

		GamesPanel(SteamScreenshotsCleanUp main, int width, int height) {
			super(new BorderLayout());
			this.main = main;
			
			Variant selected = SteamInspector.settings.getEnum(ValueKey.SSCU_GamesPanelView, Variant.Tree, Variant.class);
			
			JScrollPane scrollPane = new JScrollPane();
			scrollPane.setPreferredSize(new Dimension(width, height));
			
			JToolBar options = new JToolBar();
			options.setFloatable(false);
			
			options.add( SteamInspector.createButton("Reload Data", ICON_RELOAD, ICON_RELOAD_DISABLED, true, false, e->{
				this.main.loadData("Reload Data", true);
			}) );
			options.addSeparator();
			
			variants = new EnumMap<>(GamesPanel.Variant.class);
			addVariants(this.main, variants, selected, scrollPane, options);
			
			//statusOut = new JLabel();
			//statusOut.setBorder(BorderFactory.createLoweredBevelBorder());
			//options.add( statusOut );
			
			add(options, BorderLayout.PAGE_START);
			add(scrollPane, BorderLayout.CENTER);
			
			GamesPanelContent content = variants.get(selected);
			if (content!=null)
				scrollPane.setViewportView(content.getComponent());
			
			//fillStatusOut();
		}

		private static void addVariants(SteamScreenshotsCleanUp main, EnumMap<Variant, GamesPanelContent> variants, Variant selected, JScrollPane scrollPane, JToolBar options)
		{
			ButtonGroup bg = new ButtonGroup();
			for (Variant variant : Variant.values())
			{
				GamesPanelContent content = variant.createGuiComp == null ? null : variant.createGuiComp.apply(main);
				if (content != null)
					variants.put(variant, content);
				
				options.add(SteamInspector.createRadioButton(
					variant.toString(),
					selected==variant,
					content!=null, bg,
					b->{
						if (b && content!=null)
						{
							SteamInspector.settings.putEnum(ValueKey.SSCU_GamesPanelView, variant);
							scrollPane.setViewportView(content.getComponent());
						}
					}
				));
			}
		}
		
		/*
		private void fillStatusOut()
		{
			int  generalScreenshotsCount = 0;
			long generalScreenshotsSize = 0;
			int  gameScreenshotsCount = 0;
			long gameScreenshotsSize = 0;
			
			for (HashMap<String, File> generalScreenshots : main.generalScreenshots.values())
				if (generalScreenshots!=null)
					for (File file : generalScreenshots.values())
						if (file!=null && file.exists())
						{
							generalScreenshotsCount++;
							generalScreenshotsSize += file.length();
						}
			
			for (Data.Game game : Data.games.values())
			{
				HashMap<Long, ScreenShotList> gameScreenshots = game==null ? null : game.screenShots;
				if (gameScreenshots!=null)
					for (ScreenShotList screenshots : gameScreenshots.values())
						if (screenshots!=null)
							for (ScreenShot screenshot : screenshots)
								if (screenshot!=null && screenshot.image!=null && screenshot.image.exists())
								{
									gameScreenshotsCount++;
									gameScreenshotsSize += screenshot.image.length();
								}
			}
			
			statusOut.setText(String.format(
					"In General: %d, %s | In Games: %d, %s"
					, generalScreenshotsCount
					, getSizeStr(generalScreenshotsSize) 
					, gameScreenshotsCount   
					, getSizeStr(gameScreenshotsSize)    
			));
		}
		*/

		void updateGame(int gameID)
		{
			variants.forEach((variant, content)->{
				content.updateGame(gameID);
			});
			//fillStatusOut();
		}

		void updateContent()
		{
			variants.forEach((variant, content)->{
				content.updateContent();
			});
			//fillStatusOut();
		}
	}

	private static class GamesTree extends JTree implements GamesPanel.GamesPanelContent
	{
		private static final long serialVersionUID = -7883640944049248020L;
		private final SteamScreenshotsCleanUp main;
	
		GamesTree(SteamScreenshotsCleanUp main)
		{
			super((TreeModel)null);
			this.main = main;
			setRootVisible(false);
			setCellRenderer(new SteamInspector.BaseTreeNodeRenderer());
			getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
			addTreeSelectionListener(e->{
				TreePath path = e.getNewLeadSelectionPath();
				if (path==null) return;
				Object treeNodeObj = path.getLastPathComponent();
				if (treeNodeObj instanceof GameNode) {
					GameNode gameTreeNode = (GameNode) treeNodeObj;
					this.main.imageListPanel.setData(gameTreeNode.gameID);
				} else
					this.main.imageListPanel.clearData();
			});
		}
	
		@Override public Component getComponent() { return this; }
		@Override public void updateGame(int gameID) { updateContent(); }
		@Override public void updateContent() { setModel(new DefaultTreeModel(new RootNode())); }

		private class RootNode extends SteamInspector.BaseTreeNode<TreeNode, TreeNode> {
		
			RootNode() {
				super(null, "<Root>", true, false);
			}
		
			@Override protected Vector<TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				children.add(new GamesGroupNode(this));
				return children;
			}
			
		}
	
		private class GamesGroupNode extends SteamInspector.BaseTreeNode<RootNode, GameNode> {
			
			protected GamesGroupNode(RootNode parent) {
				super(parent, "Games", true, false);
			}
		
			@Override protected Vector<GameNode> createChildren() {
				Vector<GameNode> children = new Vector<>();
				Vector<Integer> gameIDs_sorted = main.getGameIDs_sorted();
				
				for (Integer gameID : gameIDs_sorted)
					if (gameID!=null)
						children.add(new GameNode(this, gameID));
				
				return children;
			}
		}
	
		private class GameNode extends SteamInspector.BaseTreeNode<GamesGroupNode, TreeNode> {
			
			private final int gameID;
		
			protected GameNode(GamesGroupNode parent, int gameID) {
				super(parent, Data.getGameTitle(gameID), false, true, Data.getGameIcon(gameID, TreeIcons.Folder));
				this.gameID = gameID;
			}
		
			@Override protected Vector<? extends TreeNode> createChildren() {
				throw new UnsupportedOperationException();
			}
		}
	}

	private static class GamesTable extends JTable implements GamesPanel.GamesPanelContent
	{
		private static final long serialVersionUID = -7887162543386461974L;
		private final SteamScreenshotsCleanUp main;
		private final GamesTableModel tableModel;

		GamesTable(SteamScreenshotsCleanUp main)
		{
			this.main = main;
			
			tableModel = new GamesTableModel();
			setModel(tableModel);
			setRowSorter(new Tables.SimplifiedRowSorter(tableModel));
			setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
			getSelectionModel().addListSelectionListener(e->{
				int rowV = getSelectedRow();
				int rowM = rowV<0 ? -1 : convertRowIndexToModel(rowV);
				
				Game game = tableModel.getRow(rowM);
				if (game!=null)
					this.main.imageListPanel.setData(game.gameID);
				else
					this.main.imageListPanel.clearData();
			});
			
			tableModel.setTable(this);
			tableModel.setColumnWidths(this);
			tableModel.setDefaultCellEditorsAndRenderers();
		}

		@Override public Component getComponent() { return this; }
		@Override public void updateGame(int gameID) { tableModel.rebuildGame(gameID); }
		@Override public void updateContent() { tableModel.rebuildData(); }

		private class Game
		{
			final int gameID;
			final Data.Game game;
			final String name;
			final Icon icon;
			
			int  generalScreenshotsCount;
			long generalScreenshotsSize;
			int  gameScreenshotsCount;
			long gameScreenshotsSize;
			
			Game(int gameID)
			{
				this.gameID = gameID;
				name = Data.getGameTitle(gameID, false);
				game = Data.games.get(gameID);
				icon = Data.getGameIcon(gameID, null);
				
				generalScreenshotsCount = 0;
				generalScreenshotsSize = 0;
				gameScreenshotsCount = 0;
				gameScreenshotsSize = 0;
				
				HashMap<String, File> generalScreenshots = main.generalScreenshots.get(gameID);
				if (generalScreenshots!=null)
					generalScreenshots.forEach((name,file)->{
						if (file!=null && file.exists())
						{
							generalScreenshotsCount++;
							generalScreenshotsSize += file.length();
						}
					});
				
				HashMap<Long, ScreenShotList> gameScreenshots = game==null ? null : game.screenShots;
				if (gameScreenshots!=null)
				{
					gameScreenshots.forEach((playerID, screenshots)->{
						if (screenshots!=null)
							screenshots.forEach(screenshot->{
								if (screenshot!=null && screenshot.image!=null && screenshot.image.exists())
								{
									gameScreenshotsCount++;
									gameScreenshotsSize += screenshot.image.length();
								}
							});
					});
				}
			}
		}
		
		private static class GamesTableCellRenderer implements TableCellRenderer {

			private final GamesTableModel tableModel;
			private final Tables.LabelRendererComponent labelComp;
			
			private GamesTableCellRenderer(GamesTableModel tableModel) {
				this.tableModel = tableModel;
				labelComp    = new Tables.LabelRendererComponent();
			}

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowV, int columnV) {
				//int    rowM =    rowV<0 ? -1 : table.   convertRowIndexToModel(   rowV);
				int columnM = columnV<0 ? -1 : table.convertColumnIndexToModel(columnV);
				// Game  row = rowM<0 ? null : tableModel.getRow(rowM);
				GamesTableModel.ColumnID columnID = columnM<0 ? null : tableModel.getColumnID(columnM);
				
				String valueStr = value==null ? null : value.toString();
				Icon icon = null;
				
				if (value instanceof Long valueL && (columnID == GamesTableModel.ColumnID.InGeneralSize || columnID == GamesTableModel.ColumnID.InGameSize))
					valueStr = getSizeStr(valueL);
				
				if (value instanceof Icon)
				{
					icon = (Icon) value;
					valueStr = null;
				}
				
				labelComp.configureAsTableCellRendererComponent(table, icon, valueStr, isSelected, hasFocus);
				
				int alignment = SwingConstants.LEFT;
				if (value instanceof Number)
					alignment = SwingConstants.RIGHT;
				labelComp.setHorizontalAlignment(alignment);
				
				return labelComp;
			}
		}

		private class GamesTableModel extends Tables.SimplifiedTableModel<GamesTableModel.ColumnID> {

			enum ColumnID implements Tables.SimplifiedColumnIDInterface {
				ID           ("ID"          , Integer.class,  55),
				Icon         ("I"           , Icon   .class,  20),
				Name         ("Name"        , String .class, 280),
				InGeneral    ("In General"  , Integer.class,  65),
				InGeneralSize("(Size)"      , Long   .class,  65),
				InGame       ("In Game"     , Integer.class,  55),
				InGameSize   ("(Size)"      , Long   .class,  65),
				;
				private final SimplifiedColumnConfig cfg;
				ColumnID(String name, Class<?> columnClass, int width) { cfg = new SimplifiedColumnConfig(name, columnClass, 20, -1, width, width); }
				@Override public SimplifiedColumnConfig getColumnConfig() { return cfg; }
				
			}

			private Vector<Game> data;

			GamesTableModel()
			{
				super(ColumnID.values());
				data = null;
				rebuildData();
			}
			
			void rebuildGame(int gameID)
			{
				if (data==null) return;
				for (int i=0; i<data.size(); i++)
					if (data.get(i).gameID==gameID)
					{
						data.set(i, new Game(gameID));
						fireTableRowUpdate(i);
					}
			}

			void rebuildData()
			{
				data = new Vector<>();
				for (int gameID : main.getGameIDs_sorted())
					data.add(new Game(gameID));
				fireTableUpdate();
			}

			void setDefaultCellEditorsAndRenderers() {
				GamesTableCellRenderer tcr = new GamesTableCellRenderer(this);
				setAllDefaultRenderers(columnClass->tcr);
			}

			@Override public int getRowCount() { return data==null ? 0 : data.size(); }
			
			Game getRow(int rowIndex)
			{
				if (data==null) return null;
				if (rowIndex<0 || rowIndex>=data.size()) return null;
				return data.get(rowIndex);
			}

			@Override
			public Object getValueAt(int rowIndex, int columnIndex, ColumnID columnID)
			{
				Game row = getRow(rowIndex);
				if (row==null) return null;
				switch (columnID)
				{
					case ID  : return row.gameID;
					case Icon: return row.icon;
					case Name: return row.name;
					case InGeneral    : return row.generalScreenshotsCount;
					case InGeneralSize: return row.generalScreenshotsSize;
					case InGame       : return row.gameScreenshotsCount;
					case InGameSize   : return row.gameScreenshotsSize;
				}
				return null;
			}
		}
	}

}
