/*
 * Snacky.java
 * part of the Snacky plugin for the jEdit text editor
 * Copyright (C) 2007 Christopher Plant
 * mulletwarriroextreem@hotmail.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

// from Java:
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.awt.datatransfer.*;

// from Swing:
import javax.swing.*;
import javax.swing.event.*;

// from jEdit:
import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.gui.*;
import org.gjt.sp.jedit.io.*;
import org.gjt.sp.jedit.msg.PropertiesChanged;
import org.gjt.sp.jedit.msg.ViewUpdate;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.msg.EditPaneUpdate;
import org.gjt.sp.util.Log;

import org.gjt.sp.jedit.search.*;
import org.gjt.sp.jedit.textarea.*;
import org.gjt.sp.jedit.syntax.*;
import org.gjt.sp.util.*;


public class Snacky extends JPanel implements EBComponent, SnackyActions, DefaultFocusComponent
{
	public static final int SORT_BY_DIR = 0, SORT_BY_NAME = 1;
	public static final String p4_lock = new String("_P4LOCK_");
	
	public static String getSortTypeName(String type){
		return getSortTypeName(Integer.parseInt(type));
	}
	
	public static String getSortTypeName(int type){
		switch(type)
		{
			case SORT_BY_DIR:
				return "Directory";
			case SORT_BY_NAME:
				return "File";
		}
		
		return "Unknown";
	}
	
	public static int getSortType(String type){
		if(type.equals("Directory")){
				return SORT_BY_DIR;
		}else if(type.equals("File")){
				return SORT_BY_NAME;
		}
		
		return SORT_BY_DIR;
	}
	
	private static Hashtable colours = new Hashtable();
	private static Color defaultcolour = new Color(0,0,0);
	private static Color background_colour = new Color(255,255,255);
	private static Color foreground_colour = new Color(0,0,0);
	private static Color highlight_colour = new Color(180,180,180);
	private static Color highlight2_colour = new Color(100,100,180);
	
	private String workdir = jEdit.getSettingsDirectory() + "/snacky";
	private File marker_file = new File(workdir + "/markers.txt");
	
	private View view;
	private boolean floating;
	
	private int SortType = SORT_BY_DIR;
	private boolean DisplayVersionNumbers = false;
	
	private JScrollPane flist = new JScrollPane();
	private JPanel filtbox = new JPanel(new BorderLayout());
	private JTextField filtbox_name = new JTextField();
	private JComboBox filtbox_marker;
	
	private MouseAdapter mouseAdapter_Buffer = null;
	private MouseAdapter mouseAdapter_Dir = null;
	
	private Component viewportView = null;
	
	private Stack update_queue = new Stack();
	private Hashtable<String, ImageIcon> marker_cache = new Hashtable();
	
	private ImageIcon status_none;
	private ImageIcon status_open;
	private ImageIcon status_add;
	private ImageIcon status_locked;
	private ImageIcon status_lockconflict;
	private ImageIcon status_openconflict;
	
	private ImageIcon marker_none;
	private ImageIcon marker_red;
	private ImageIcon marker_green;
	private ImageIcon marker_blue;
	private ImageIcon marker_cyan;
	private ImageIcon marker_purple;
	private ImageIcon marker_yellow;
	
	private JPopupMenu menu;
	
	private JTextArea history = new JTextArea("");
	private StringBuffer comhistory = new StringBuffer();
	private JDialog historyDiag = new JDialog((Frame)null, "Snacky Command History", false);
	
	private String logindir = null;
	private JDialog loginDiag = new JDialog((Frame)null, "Perforce Password Required", false);

	private Hashtable<String, ImageIcon> marker_map = new Hashtable();
	
	private ImageIcon createMarkerIcon(Color colour, String name)
	{
		BufferedImage img;
		Graphics g;
		
		img = new BufferedImage(12,12,BufferedImage.TYPE_INT_ARGB);
		g = img.getGraphics();
		g.setColor(colour.darker());
		g.fillRoundRect(2,2,8,8,2,2);
		g.setColor(colour);
		g.drawRoundRect(2,2,8,8,2,2);
		
		ImageIcon icon =  new ImageIcon(img, name);
		marker_map.put(name, icon);
		
		return icon;
	}
	
	private ImageIcon createStatusIcon(Color colour, String name)
	{
		BufferedImage img;
		Graphics g;
		
		img = new BufferedImage(12,12,BufferedImage.TYPE_INT_ARGB);
		g = img.getGraphics();
		g.setColor(colour.darker());
		g.fillOval(4,4,4,4);
		g.setColor(colour);
		g.drawOval(4,4,4,4);
		
		return new ImageIcon(img, name);
	}
	
	public Snacky(View view, String position)
	{
		super(new BorderLayout());
		
		if(!marker_file.exists()){
			try{
				// This is the first time we have been run...
				// Create the working folder and initial file contents...
				marker_file.getParentFile().mkdirs();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		
		BufferedImage img;
		Graphics g;
		
		buildCommandHistory();
		buildLoginDiag();
		
		img = new BufferedImage(12,12,BufferedImage.TYPE_INT_ARGB);
		status_none = new ImageIcon(img);
		marker_none = status_none;
		
		status_open = createStatusIcon(Color.WHITE, "open");
		status_add = createStatusIcon(Color.YELLOW, "add");
		status_lockconflict = createStatusIcon(Color.RED.darker(), "lockconflict");
		status_locked = createStatusIcon(Color.GREEN.darker(), "locked");
		status_openconflict = createStatusIcon(Color.CYAN.brighter(), "openconflict");
		
		marker_red = createMarkerIcon(Color.RED, "red");
		marker_green = createMarkerIcon(Color.GREEN, "green");
		marker_blue = createMarkerIcon(Color.BLUE.brighter(), "blue");
		marker_cyan = createMarkerIcon(Color.CYAN, "cyan");
		marker_purple = createMarkerIcon(new Color(180, 0, 240), "purple");
		marker_yellow = createMarkerIcon(Color.YELLOW, "yellow");
		
		filtbox_marker = new JComboBox(new ImageIcon[]{
			marker_none, marker_red, marker_green, marker_blue,
			marker_cyan, marker_purple, marker_yellow
		});
		
		buildBufferMenu();
		loadMarkers();
		
		flist.getVerticalScrollBar().setUnitIncrement(15);
		
		mouseAdapter_Buffer = (new MouseAdapter(){
				public void mouseReleased(final MouseEvent e){
					Component c = (Component)e.getSource();
					
					while(!(c instanceof ResultPanel))
					{
						c = c.getParent();
						if(c == null)
						{
							return;
						}
					}
					
					final ResultPanel rp = (ResultPanel) c;
					
					if(e.getButton() == e.BUTTON1)
					{
						if(e.isControlDown())
						{
							// Mark the buffer as selected...
							if(!rp.getBackground().equals(highlight2_colour))
							{
								rp.setBackground(highlight2_colour);
							}
							else
							{
								rp.setBackground(null);
							}
						}
						else
						{
							if(!e.isControlDown())
							{
								// Clear the previously highlighted buffers...
								highlightCurrentBuffer(false, false);
							}
							
							// Change to the indicated buffer...
							SwingUtilities.invokeLater(new Thread(){
									public void run(){
										jEdit.getActiveView().setBuffer(rp.buf);
									}
							});
						}
					}
					else
					{
						// Mark the buffer as selected...
						rp.setBackground(highlight2_colour);
						
						SwingUtilities.invokeLater(new Thread(){
							public void run(){
								// Show the menu...
								showBufferMenu(rp, e.getX(), e.getY());
							}
						});
					}
				}
		});
		
		mouseAdapter_Dir = (new MouseAdapter(){
				public void mouseReleased(final MouseEvent e){
					Component c = (Component)e.getSource();
					
					while(!(c instanceof DirPanel))
					{
						c = c.getParent();
						if(c == null)
						{
							return;
						}
					}
					
					final DirPanel dp = (DirPanel) c;
					
					if(e.getButton() == e.BUTTON1)
					{
						// highlight all the buffers in this directory...
						highlightBuffersInDir(dp.path, e.isControlDown());
					}
				}
		});
		
		addComponentListener(new ComponentAdapter()
		{
			public void componentResized(ComponentEvent e)
			{
				JPanel panel = (JPanel)flist.getViewport().getView();
				
				Component[] rlabels = panel.getComponents();
				
				for(int n = 0; n < rlabels.length; n++)
				{
					if(rlabels[n] instanceof DirPanel)
					{
						((DirPanel)rlabels[n]).calcName();
					}
				}
			}
		});
		
		filtbox_name.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// refresh the list and apply a filter..
				highlightCurrentBuffer(true, true);
			}
		});
		
		filtbox_marker.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// refresh the list and apply a filter..
				highlightCurrentBuffer(true, true);
			}
		});
		
		this.view = view;
		this.floating  = position.equals(DockableWindowManager.FLOATING);
		
		applyEditorScheme();
		buildGUI();
		
		(new Thread()
		{
			public void run()
			{
				while(true)
				{
					try
					{
						while(update_queue.size() > 0)
						{
							ResultPanel rp = (ResultPanel)update_queue.pop();
							rp.updateDetails();
						}
						
						sleep(100);
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
				}
			}
		}).start();
	}
	
	private void saveMarkers()
	{
		try {
			FileWriter fw = new FileWriter(marker_file);
			
			for (Map.Entry<String, ImageIcon> marker : marker_cache.entrySet())
			{
				fw.write(marker.getKey() + " " + marker.getValue().getDescription() + "\n");
			}
			
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void loadMarkers()
	{
		marker_cache.clear();
		try
		{
			BufferedReader buffy = new BufferedReader(new InputStreamReader(new FileInputStream(marker_file)));
			String line;
			while((line = buffy.readLine()) != null)
			{
				line = line.strip();
				int i = line.lastIndexOf(' ');
				String path = line.substring(0, i);
				String marker = line.substring(i+1);
				marker_cache.put(path, marker_map.get(marker));
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	private void LogMsg(int urgency, java.lang.Object source, java.lang.Object message)
	{
		Log.log(urgency, source, message);
		//Log.flushStream();
	}
	
	private void LogMsg(int urgency, java.lang.Object source, java.lang.Object message, Exception e)
	{
		Log.log(urgency, source, message, e);
		//Log.flushStream();
	}
	
	public void focusOnDefaultComponent()
	{
		
	}
	
	/**
	* EditBus message handling.
	*/
	public void handleMessage(EBMessage msg) {
		if (msg instanceof BufferUpdate) {
			BufferUpdate bu = (BufferUpdate) msg;
            
			if (bu.getWhat() == BufferUpdate.CREATED) {
				highlightCurrentBuffer(true, true);
			}
			else if (bu.getWhat() == BufferUpdate.CLOSED) {
				highlightCurrentBuffer(true, true);
			}
			else if (bu.getWhat() == BufferUpdate.DIRTY_CHANGED) {
				updateBufferInfo(bu.getBuffer(), true);
			}
			else if (bu.getWhat() == BufferUpdate.SAVED) {
				updateBufferInfo(bu.getBuffer(), true);
			}
			else if (bu.getWhat() == BufferUpdate.LOADED) {
				updateBufferInfo(bu.getBuffer(), true);
			}
			else if (bu.getWhat() == BufferUpdate.PROPERTIES_CHANGED) {
				updateBufferInfo(bu.getBuffer(), true);
			}
		} else if (msg instanceof EditPaneUpdate) {
			EditPaneUpdate epu = (EditPaneUpdate) msg;
			if (epu.getWhat() == EditPaneUpdate.BUFFER_CHANGED) {
				highlightCurrentBuffer(false, true);
			}
		} else if (msg instanceof ViewUpdate) {
			ViewUpdate vu = (ViewUpdate) msg;
			if (vu.getWhat() == ViewUpdate.ACTIVATED) {
				highlightCurrentBuffer(false, true);
			}
			else if (vu.getWhat() == ViewUpdate.EDIT_PANE_CHANGED) {
				highlightCurrentBuffer(false, true);
			}
		} else if (msg instanceof PropertiesChanged) {
			propertiesChanged();
		}
	}

	// These JComponent methods provide the appropriate points
	// to subscribe and unsubscribe this object to the EditBus

	public void addNotify()
	{
		super.addNotify();
		EditBus.addToBus(this);
		propertiesChanged();
	}


	public void removeNotify()
	{
		super.removeNotify();
		EditBus.removeFromBus(this);
	}


	//
	// SnackyActions implementation
	//

	public void next(){
		Buffer curr = jEdit.getActiveView().getBuffer();
		Buffer bufs[] = getSortedBufs();
		
		for(int i = 0; i < bufs.length-1; i++)
		{
			if(bufs[i] == curr)
			{
				jEdit.getActiveView().setBuffer(bufs[i+1]);
			}
		}
	}
	
	public void prev(){
		Buffer curr = jEdit.getActiveView().getBuffer();
		Buffer bufs[] = getSortedBufs();
		
		for(int i = 1; i < bufs.length; i++)
		{
			if(bufs[i] == curr)
			{
				jEdit.getActiveView().setBuffer(bufs[i-1]);
			}
		}
	}
	
	public Buffer[] getSortedBufs(){
		Buffer bufs_unsorted[] = jEdit.getActiveView().getBuffers();
		Buffer bufs[] = new Buffer[bufs_unsorted.length];
		
		// Todo: Sort the bufs...
		switch(SortType)
		{
			case SORT_BY_NAME:
			{
				for(int j = 0; j < bufs_unsorted.length; j++)
				{
					Buffer nw = bufs_unsorted[j];
					String nwName = nw.getName();
					
					for(int n = 0; n < bufs.length; n++)
					{
						if(bufs[n] == null){
							bufs[n] = nw;
							break;
						}else{
							String olName = (String)bufs[n].getName();
							
							int cmp = nwName.compareToIgnoreCase(olName);
							
							if(cmp < 0){
								for(int i = bufs.length-1; i > n; i--){
									bufs[i] = bufs[i-1];
								}
								bufs[n] = nw;
								break;
							}
						}
					}
				}
				break;
			}
			default:
			case SORT_BY_DIR:
			{
				for(int j = 0; j < bufs_unsorted.length; j++)
				{
					Buffer nw = bufs_unsorted[j];
					String nwDir = nw.getDirectory();
					String nwName = nw.getName();
					
					for(int n = 0; n < bufs.length; n++)
					{
						if(bufs[n] == null){
							bufs[n] = nw;
							break;
						}else{
							String olName = (String)bufs[n].getName();
							String olDir = (String)bufs[n].getDirectory();
							
							int cmp = nwDir.compareToIgnoreCase(olDir);
							
							if(cmp == 0)
							{
								cmp = nwName.compareToIgnoreCase(olName);
							}
							
							if(cmp < 0){
								for(int i = bufs.length-1; i > n; i--){
									bufs[i] = bufs[i-1];
								}
								bufs[n] = nw;
								break;
							}
						}
					}
				}
				break;
			}
		}
		
		return bufs;
	}
	
	public synchronized void refresh(){
		Buffer bufs[] = getSortedBufs();
		
		JPanel panel = (JPanel)flist.getViewport().getView();
		Hashtable oldresults = new Hashtable();
		int pos = -1;
		if(panel != null)
		{
			pos = flist.getVerticalScrollBar().getValue();
			Component[] oldrlabels = panel.getComponents();
			
			for(int n = 0; n < oldrlabels.length; n++)
			{
				if(oldrlabels[n] instanceof ResultPanel)
				{
					ResultPanel rp = (ResultPanel)oldrlabels[n];
					panel.remove(rp);
					oldresults.put(rp.buf.getPath(), rp);
				}
			}
		}
		
		panel = new JPanel();
		panel.setBackground(background_colour);
		GridBagLayout gbl = new GridBagLayout();
		GridBagConstraints gblc = new GridBagConstraints();
		gblc.gridwidth = GridBagConstraints.REMAINDER;
		gblc.fill = GridBagConstraints.HORIZONTAL;
		gblc.anchor = GridBagConstraints.WEST;
		panel.setLayout(gbl);
		
		String dir = "";
		String filter = filtbox_name.getText().trim().toLowerCase();
		ImageIcon filter_mark = (ImageIcon)filtbox_marker.getSelectedItem();
		if(filter.length() == 0)
		{
			filter = null;
		}
		
		Pattern pattern = null;
		if (filter != null)
		{
			pattern = Pattern.compile(filter);
		}
		
		for(int n = 0; n < bufs.length; n++)
		{
			Buffer buf = bufs[n];
			
			if(pattern != null)
			{
				Matcher matcher = pattern.matcher(buf.getPath().toLowerCase());
				if(!matcher.find())
				{
					continue;
				}
			}
			
			if (filter_mark != null && filter_mark != marker_none)
			{
				if (filter_mark != (ImageIcon)marker_cache.get(buf.getPath()))
				{
					continue;
				}
			}
			
			if((dir.compareToIgnoreCase(buf.getDirectory()) != 0) && SortType == SORT_BY_DIR)
			{
				dir = buf.getDirectory();
				
				DirPanel dlabel = new DirPanel(dir);
				gbl.setConstraints(dlabel, gblc);
				panel.add(dlabel, gblc);
				dlabel.setBackground(background_colour);
				dlabel.setForeground((Color)colours.get("f"));
			}
			
			ResultPanel rlabel = (ResultPanel)oldresults.get(buf.getPath());
			
			if(rlabel == null)
			{
				rlabel = new ResultPanel(buf);
			}
			
			gbl.setConstraints(rlabel, gblc);
			panel.add(rlabel, gblc);
			rlabel.setBackground(background_colour);
			rlabel.setForeground(foreground_colour);
		}
		
		flist.setViewportView(panel);
		
		if(pos != -1)
		{
			flist.getVerticalScrollBar().setValue(pos);
		}
		
		System.gc();
	}
	
	//
	// Private Functions...
	//
	
	private void updateBufferInfo(Buffer buf, boolean force)
	{
		JPanel panel = (JPanel)flist.getViewport().getView();
		
		Component[] rlabels = panel.getComponents();
		
		for(int n = 0; n < rlabels.length; n++)
		{
			if(rlabels[n] instanceof ResultPanel)
			{
				ResultPanel rp = (ResultPanel)rlabels[n];
				if(buf == rp.buf)
				{
					if(rp.requestUpdateDetails(force))
					{
						highlightCurrentBuffer(false, false);
					}
					return;
				}
			}
		}
	}
	
	private boolean isMKSVersionLocked(Buffer buffer, String version)
	{
		try
		{
			String fcom[] = new String[]{"rlog", "-r" + version, buffer.getPath()};
			Process p = Runtime.getRuntime().exec(fcom);
			
			BufferedReader buffy = new BufferedReader(new InputStreamReader(p.getInputStream()));
			
			String line;
			while((line = buffy.readLine()) != null){
				if(line.startsWith("revision " + version))
				{
					if(line.indexOf("locked by:") != -1)
					{
						buffy.close();
						return true;
					}
				}
			}
			
			buffy.close();
		}
		catch(Exception e)
		{
			LogMsg(Log.ERROR, Snacky.class,
				"Exception while running rlog on: " + buffer.getPath(), e);
		}
		
		return false;
	}
	
	private String getMKSVersionNumber(Buffer buffer)
	{
		if(!DisplayVersionNumbers)
		{
			return null;
		}
		
		String version = null;
		
		try{
			for(int n = 0; n < Math.min(buffer.getLineCount(), 100); n++){
				int pos;
				String line = buffer.getLineText(n);
				if((pos = line.indexOf("$Revision: ")) != -1)
				{
					pos += "$Revision: ".length();
					int pos2 = line.indexOf("$", pos);
					if(pos2 != -1)
					{
						version = line.substring(pos, pos2).trim();
						break;
					}
				}
				else if((pos = line.indexOf("Revision ")) != -1)
				{
					pos += "Revision ".length();
					int pos2 = line.indexOf(" ", pos);
					if(pos2 != -1)
					{
						version = line.substring(pos, pos2).trim();
						break;
					}
				}
			}
		}
		catch(Exception e)
		{
			LogMsg(Log.ERROR, Snacky.class,
				"Exception while discovering MKS version for: " + buffer.getPath(), e);
		}
		
		return version;
	}
	
	private void showBufferMenu(ResultPanel rp, int x, int y)
	{
		menu.show(rp, x, y);
	}
	
	private void buildBufferMenu()
	{
		menu = new JPopupMenu();
		
		menu.addPopupMenuListener(new PopupMenuListener()
		{
			public void popupMenuCanceled(PopupMenuEvent e){
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
			
			public void popupMenuWillBecomeVisible(PopupMenuEvent e){}
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e){}
		});
		
		/**
		 * Basic options..
		 */
		JMenuItem save = new JMenuItem("Save");
		save.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Save all the highlighted buffers...
				saveHighlightedBuffers();
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
		});
		menu.add(save);
		
		JMenuItem close = new JMenuItem("Close");
		close.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Close all the highlighted buffers...
				closeHighlightedBuffers();
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(true, false);
			}
		});
		menu.add(close);
		
		JMenuItem diff = new JMenuItem("Diff");
		diff.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// run a diff program on the highlighted buffer(s).
				diffHighlightedBuffers();
			}
		});
		menu.add(diff);
		
		JMenuItem cp_path = new JMenuItem("Copy Path");
		cp_path.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// copy the buffer path to the clipboard
				copyBufferPathToClipboard();
			}
		});
		menu.add(cp_path);
		
		menu.add(new JPopupMenu.Separator());
		
		/**
		 * P4 options..
		 */
		JMenu submenu = new JMenu("Perforce");
		
		JMenuItem p4_open = new JMenuItem("Open");
		p4_open.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Open all the highlighted buffers...
				p4_openHighlightedBuffers();
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
		});
		submenu.add(p4_open);
		
		JMenuItem p4_revert = new JMenuItem("Close");
		p4_revert.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Revert all the highlighted buffers...
				p4_revertHighlightedBuffers(false);
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
		});
		submenu.add(p4_revert);
		
		p4_revert = new JMenuItem("Revert");
		p4_revert.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Revert all the highlighted buffers...
				p4_revertHighlightedBuffers(true);
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
		});
		submenu.add(p4_revert);
		
		JMenuItem p4_add = new JMenuItem("Add");
		p4_add.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Add all the highlighted buffers...
				p4_addHighlightedBuffers();
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
		});
		submenu.add(p4_add);
		
		submenu.add(new JPopupMenu.Separator());
		
		JMenuItem p4_diff = new JMenuItem("Diff (have)");
		p4_diff.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Revert all the highlighted buffers...
				p4_diffHighlightedBuffers("#have");
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
		});
		submenu.add(p4_diff);
		
		p4_diff = new JMenuItem("Diff (head)");
		p4_diff.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Revert all the highlighted buffers...
				p4_diffHighlightedBuffers("#head");
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
		});
		submenu.add(p4_diff);
		
		p4_diff = new JMenuItem("Diff (prev)");
		p4_diff.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Revert all the highlighted buffers...
				p4_diffHighlightedBuffers("#prev");
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
		});
		submenu.add(p4_diff);
		
		submenu.add(new JPopupMenu.Separator());
		
		JMenuItem p4_lock = new JMenuItem("Lock");
		p4_lock.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Revert all the highlighted buffers...
				p4_lockHighlightedBuffers();
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
		});
		submenu.add(p4_lock);
		
		JMenuItem p4_unlock = new JMenuItem("Unlock");
		p4_unlock.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Revert all the highlighted buffers...
				p4_unlockHighlightedBuffers();
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
		});
		submenu.add(p4_unlock);
		
		submenu.add(new JPopupMenu.Separator());
		
		JMenuItem p4_changes = new JMenuItem("Changes");
		p4_changes.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Display changes for all the highlighted buffers...
				p4_changesHighlightedBuffers();
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
		});
		submenu.add(p4_changes);
		
		JMenuItem p4_timeline = new JMenuItem("Timeline");
		p4_timeline.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Display changes for all the highlighted buffers...
				p4_timelineHighlightedBuffers();
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
		});
		submenu.add(p4_timeline);
		
		menu.add(submenu);
		menu.add(new JPopupMenu.Separator());
		
		/**
		 * Marker options..
		 */
		submenu = new JMenu("Markers");
		
		JMenuItem mark_none = new JMenuItem("Clear Marker", marker_none);
		mark_none.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Clear marks on all the highlighted buffers...
				markHighlightedBuffers(marker_none);
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
		});
		submenu.add(mark_none);
		submenu.add(new JPopupMenu.Separator());
		
		JMenuItem mark_red = new JMenuItem("Mark Red", marker_red);
		mark_red.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Mark all the highlighted buffers...
				markHighlightedBuffers(marker_red);
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
		});
		submenu.add(mark_red);
		
		JMenuItem mark_green = new JMenuItem("Mark Green", marker_green);
		mark_green.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Mark all the highlighted buffers...
				markHighlightedBuffers(marker_green);
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
		});
		submenu.add(mark_green);
		
		JMenuItem mark_blue = new JMenuItem("Mark Blue", marker_blue);
		mark_blue.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Mark all the highlighted buffers...
				markHighlightedBuffers(marker_blue);
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
		});
		submenu.add(mark_blue);
		
		JMenuItem mark_cyan = new JMenuItem("Mark Cyan", marker_cyan);
		mark_cyan.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Mark all the highlighted buffers...
				markHighlightedBuffers(marker_cyan);
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
		});
		submenu.add(mark_cyan);
		
		JMenuItem mark_purple = new JMenuItem("Mark Purple", marker_purple);
		mark_purple.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Mark all the highlighted buffers...
				markHighlightedBuffers(marker_purple);
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
		});
		submenu.add(mark_purple);
		
		JMenuItem mark_yellow = new JMenuItem("Mark Yellow", marker_yellow);
		mark_yellow.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Mark all the highlighted buffers...
				markHighlightedBuffers(marker_yellow);
				
				// Un-highlight all buffers...
				highlightCurrentBuffer(false, false);
			}
		});
		submenu.add(mark_yellow);
		
		menu.add(submenu);
		menu.add(new JPopupMenu.Separator());
		
		/**
		 * Debug options..
		 */
		JMenuItem hist = new JMenuItem("History");
		hist.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				showCommandHistory();
			}
		});
		menu.add(hist);
	}
	
	private void showCommandHistory()
	{
		history.setText(comhistory.toString());
		historyDiag.setVisible(true);
	}
	
	private void buildCommandHistory()
	{
		historyDiag.setLayout(new BorderLayout());
		historyDiag.add(new JScrollPane(history));
		historyDiag.setSize(640,480);
	}
	
	private void applyEditorScheme()
	{
		highlight_colour = jEdit.getColorProperty("view.lineHighlightColor", Color.GRAY);
		background_colour = jEdit.getColorProperty("view.bgColor", Color.WHITE);
		foreground_colour = jEdit.getColorProperty("view.fgColor", Color.BLACK);
		highlight2_colour = jEdit.getColorProperty("view.selectionColor", Color.BLUE);
		
		defaultcolour = foreground_colour;
		
		String family = jEdit.getProperty("view.font");
		int size = jEdit.getIntegerProperty("view.fontsize",12);
		
		colours.put("m", org.gjt.sp.util.SyntaxUtilities.parseStyle(jEdit.getProperty("view.style.function"), family, size, true).getForegroundColor());
		colours.put("f", org.gjt.sp.util.SyntaxUtilities.parseStyle(jEdit.getProperty("view.style.comment1"), family, size, true).getForegroundColor());
		colours.put("c", org.gjt.sp.util.SyntaxUtilities.parseStyle(jEdit.getProperty("view.style.comment2"), family, size, true).getForegroundColor());
		colours.put("d", org.gjt.sp.util.SyntaxUtilities.parseStyle(jEdit.getProperty("view.style.comment4"), family, size, true).getForegroundColor());
		colours.put("l", org.gjt.sp.util.SyntaxUtilities.parseStyle(jEdit.getProperty("view.style.label"), family, size, true).getForegroundColor());
		colours.put("t", org.gjt.sp.util.SyntaxUtilities.parseStyle(jEdit.getProperty("view.style.literal1"), family, size, true).getForegroundColor());
		colours.put("v", org.gjt.sp.util.SyntaxUtilities.parseStyle(jEdit.getProperty("view.style.literal2"), family, size, true).getForegroundColor());
		colours.put("n", org.gjt.sp.util.SyntaxUtilities.parseStyle(jEdit.getProperty("view.style.literal2"), family, size, true).getForegroundColor());
		
		flist.setBackground(background_colour);
	}

	private void propertiesChanged()
	{
		SortType = Integer.parseInt(jEdit.getProperty(
			SnackyPlugin.OPTION_PREFIX + "sorttype"));
		DisplayVersionNumbers = Boolean.parseBoolean(jEdit.getProperty(
			SnackyPlugin.OPTION_PREFIX + "versions"));
		
		applyEditorScheme();
		highlightCurrentBuffer(true, false);
	}
	
	private void highlightBuffersInDir(String path, Boolean isControlDown)
	{
		JPanel panel = (JPanel)flist.getViewport().getView();
		
		Component[] rlabels = panel.getComponents();
		
		for(int n = 0; n < rlabels.length; n++)
		{
			if(rlabels[n] instanceof ResultPanel)
			{
				if(path.compareToIgnoreCase(((ResultPanel)rlabels[n]).buf.getDirectory()) == 0)
				{
					if(!rlabels[n].getBackground().equals(highlight2_colour))
					{
						rlabels[n].setBackground(highlight2_colour);
					}
					else
					{
						rlabels[n].setBackground(null);
					}
				}
				else if(!isControlDown)
				{
					rlabels[n].setBackground(null);
				}
			}
		}
	}
	
	private void diffHighlightedBuffers(){
		JPanel panel = (JPanel)flist.getViewport().getView();
		
		Component[] rlabels = panel.getComponents();
		
		Buffer first = null;
		Buffer second = null;
		
		for(int n = 0; n < rlabels.length; n++)
		{
			if(rlabels[n] instanceof ResultPanel)
			{
				if(rlabels[n].getBackground().equals(highlight2_colour))
				{
					Buffer buf = ((ResultPanel)rlabels[n]).buf;
					if(first == null)
					{
						first = buf;
					} else if (second == null) {
						second = buf;
						break;
					}
				}
			}
		}
		
		if (first != null)
		{
			try{
				if (second == null)
				{
					/* only one buffer selected so diff against it's ~version
					 * if that exists
					 */
					File prev = new File(first.getPath() + "~");
					if (prev.exists()) {
						String fcom[] = new String[]{"bcomp", first.getPath(), prev.getPath()};
						Process p = Runtime.getRuntime().exec(fcom);
						//p.waitFor();
					} else {
						// error..?
					}
				} else {
					/* two buffers selected, diff them */
					String fcom[] = new String[]{"bcomp", first.getPath(), second.getPath()};
					Process p = Runtime.getRuntime().exec(fcom);
					//p.waitFor();
				}
			} catch (Exception e) {
				LogMsg(Log.ERROR, Snacky.class,
					"Exception while running diff", e);
			}
		}
	}
	
	private void saveHighlightedBuffers(){
		JPanel panel = (JPanel)flist.getViewport().getView();
		
		Component[] rlabels = panel.getComponents();
		
		for(int n = 0; n < rlabels.length; n++)
		{
			if(rlabels[n] instanceof ResultPanel)
			{
				if(rlabels[n].getBackground().equals(highlight2_colour))
				{
					Buffer buf = ((ResultPanel)rlabels[n]).buf;
					if(buf.isDirty())
					{
						buf.save(jEdit.getActiveView(), buf.getPath());
					}
				}
			}
		}
	}
	
	private void closeHighlightedBuffers(){
		JPanel panel = (JPanel)flist.getViewport().getView();
		
		Component[] rlabels = panel.getComponents();
		
		Stack stack = new Stack();
		
		for(int n = 0; n < rlabels.length; n++)
		{
			if(rlabels[n] instanceof ResultPanel)
			{
				if(rlabels[n].getBackground().equals(highlight2_colour))
				{
					Buffer buf = ((ResultPanel)rlabels[n]).buf;
					stack.push(buf);
				}
			}
		}
		
		while(stack.size() > 0)
		{
			Buffer buf = (Buffer)stack.pop();
			jEdit.closeBuffer(jEdit.getActiveView(), buf);
		}
	}
	
	private void copyBufferPathToClipboard(){
		JPanel panel = (JPanel)flist.getViewport().getView();
		
		Component[] rlabels = panel.getComponents();
		
		for(int n = 0; n < rlabels.length; n++)
		{
			if(rlabels[n] instanceof ResultPanel)
			{
				if(rlabels[n].getBackground().equals(highlight2_colour))
				{
					Buffer buf = ((ResultPanel)rlabels[n]).buf;
					
					String path = buf.getPath();
					
					StringSelection stringSelection = new StringSelection(path);
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					clipboard.setContents(stringSelection, null);
					
					// stop at the first one.
					return;
				}
			}
		}
	}
	
	private void markHighlightedBuffers(ImageIcon icon){
		JPanel panel = (JPanel)flist.getViewport().getView();
		
		Component[] rlabels = panel.getComponents();
		
		for(int n = 0; n < rlabels.length; n++)
		{
			if(rlabels[n] instanceof ResultPanel)
			{
				if(rlabels[n].getBackground().equals(highlight2_colour))
				{
					((ResultPanel)rlabels[n]).setMarker(icon);
				}
			}
		}
	}
	
	public void appendCommandHistory(String dir, String command)
	{
		comhistory.insert(0, dir + "\n> " + command + "\n");
		comhistory.setLength(4096);
		if(historyDiag.isVisible()){
			history.setText(comhistory.toString());
		}
	}
	
	public boolean runCommand(String command, String dir, final StringBuffer buf, final boolean wait){
		try{
			File fdir = new File(dir);
			
			if(!fdir.exists()){
				return false;
			}
			
			appendCommandHistory(dir, command);
			final Process p = Runtime.getRuntime().exec(command, null, fdir);
			
			/*BufferedReader buffy = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line;
			while((line = buffy.readLine()) != null){
				buf.append(line + "\n");
			}
			buffy.close();*/
			
			(new Thread(){
					public void run(){
						try{
							BufferedReader buffy = new BufferedReader(new InputStreamReader(p.getInputStream()));
							String line;
							while((line = buffy.readLine()) != null){
								if(buf != null)
								{
									buf.append(line + "\n");
								}
							}
						}catch(Exception e){
							e.printStackTrace();
						}
					}
			}).start();
			
			(new Thread(){
					public void run(){
						try{
							BufferedReader buffy = new BufferedReader(new InputStreamReader(p.getErrorStream()));
							String line;
							while((line = buffy.readLine()) != null){
								if(buf != null)
								{
									buf.append(line + "\n");
								}
							}
						}catch(Exception e){
							e.printStackTrace();
						}
					}
			}).start();
			
			if(wait)
			{
				p.waitFor();
			}
			
			return (p.exitValue() == 0);
		}catch(Exception e){
			LogMsg(Log.ERROR, Snacky.class,
				"Exception while running command: " + command, e);
			return false;
		}
	}
	
	private void buildLoginDiag()
	{
		final String command = "p4 login";
		final StringBuffer buf = new StringBuffer();
		
		final JPasswordField pass = new JPasswordField(30);
		final JButton ok = new JButton("OK");
		final JButton cancel = new JButton("Cancel");
		
		pass.addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				if(e.getKeyCode() == e.VK_ENTER)
				{
					ok.doClick();
				}
			}
		});
		
		ActionListener al = (new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				if(e.getSource() == ok)
				{
					File fdir = new File(logindir);
					
					appendCommandHistory(logindir, command);
					
					try{
						Process p = Runtime.getRuntime().exec(command, null, fdir);
						PrintStream out = new PrintStream(p.getOutputStream());
						out.print(new String(pass.getPassword()));
						out.close();
						p.waitFor();
					}
					catch(Exception ex)
					{
						LogMsg(Log.ERROR, Snacky.class,
								"Exception while running p4 login: " + command, ex);
					}
				}
				
				loginDiag.setVisible(false);
			}
		});
		ok.addActionListener(al);
		cancel.addActionListener(al);
		
		loginDiag.setLayout(new BorderLayout());
		loginDiag.add(new JLabel("Please enter the password:"), BorderLayout.NORTH);
		
		JPanel main = new JPanel();
		main.setLayout(new FlowLayout());
		main.add(pass);
		
		loginDiag.add(main, BorderLayout.CENTER);
		
		JPanel buttons = new JPanel();
		buttons.setLayout(new FlowLayout());
		buttons.add(ok);
		buttons.add(cancel);
		
		loginDiag.add(buttons, BorderLayout.SOUTH);
		
		loginDiag.pack();
	}
	
	private boolean runP4Command(String command, String dir, final StringBuffer buf)
	{
		boolean result;
		
		synchronized(p4_lock)
		{
			result = runCommand(command, dir, buf, true);
			
			//appendCommandHistory(buf.toString(), command);
			
			if(!result && 
				(buf.toString().startsWith("Perforce password (P4PASSWD) invalid or unset.") ||
				buf.toString().startsWith("Your session has expired, please login again.")))
			{
				if(!loginDiag.isVisible())
				{
					logindir = dir;
					loginDiag.setVisible(true);
				}
				/*
				while(loginDiag.isVisible())
				{
					try
					{
						Thread.currentThread().sleep(100);
					}
					catch(Exception e)
					{
						// do nothing?
					}
				}
				*/
				
				result = runCommand(command, dir, buf, true);
			}
		}
		
		return result;
	}
	
	private Hashtable getP4FStat(Buffer buffer)
	{
		Hashtable hsh = new Hashtable();
		
		if(!DisplayVersionNumbers)
		{
			return null;
		}
		
		StringBuffer buf = new StringBuffer();
		if(!runP4Command("p4 fstat \"" + buffer.getName() + "\"", buffer.getDirectory(), buf))
		{
			return null;
		}
		
		try
		{
			BufferedReader buffy = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(String.valueOf(buf).getBytes())));
			
			String line;
			while((line = buffy.readLine()) != null)
			{
				int stok = -1;
				
				if(line.startsWith("... ... "))
				{
					stok = 2;
				}
				else if(line.startsWith("... "))
				{
					stok = 1;
				}
				
				if(stok != -1)
				{
					String[] toks = line.split(" ");
					
					if(toks.length == stok+2)
					{
						hsh.put(toks[stok], toks[stok+1]);
					}
					else if(toks.length == stok+1)
					{
						hsh.put(toks[stok], "");
					}
				}
			}
			
			buffy.close();
		}catch(Exception e){
			LogMsg(Log.ERROR, Snacky.class,
				"Exception while running p4 fstat on: " + buffer.getPath(), e);
		}
		
		return hsh;
	}
	
	private void p4_changesHighlightedBuffers(){
		JTextArea output_display = new JTextArea();
		output_display.setEditable(false);
		
		String output = "";
		
		JPanel panel = (JPanel)flist.getViewport().getView();
		
		Component[] rlabels = panel.getComponents();
		
		for(int n = 0; n < rlabels.length; n++)
		{
			if(rlabels[n] instanceof ResultPanel)
			{
				if(rlabels[n].getBackground().equals(highlight2_colour))
				{
					Buffer buf = ((ResultPanel)rlabels[n]).buf;
					
					StringBuffer sbuf = new StringBuffer();
					
					runP4Command("p4 changes -m 20 \"" + buf.getName() + "\"", buf.getDirectory(), sbuf);
					
					output = output + buf.getPath() + "\n" + sbuf.toString() + "\n\n";
				}
			}
		}
		
		output_display.setText(output);
		
		JDialog diag = new JDialog(jEdit.getActiveView(), "Snacky P4 Changes..", false);
		diag.setLayout(new BorderLayout());
		diag.add(new JScrollPane(output_display));
		diag.setSize(800,480);
		
		diag.setVisible(true);
	}
	
	private void p4_addHighlightedBuffers(){
		JPanel panel = (JPanel)flist.getViewport().getView();
		
		Component[] rlabels = panel.getComponents();
		
		for(int n = 0; n < rlabels.length; n++)
		{
			if(rlabels[n] instanceof ResultPanel)
			{
				if(rlabels[n].getBackground().equals(highlight2_colour))
				{
					Buffer buf = ((ResultPanel)rlabels[n]).buf;
					
					runP4Command("p4 add \"" + buf.getName() + "\"", buf.getDirectory(), null);
					buf.reload(jEdit.getActiveView());
					
					updateBufferInfo(buf, true);
				}
			}
		}
	}
	
	private void p4_openHighlightedBuffers(){
		JPanel panel = (JPanel)flist.getViewport().getView();
		
		Component[] rlabels = panel.getComponents();
		
		for(int n = 0; n < rlabels.length; n++)
		{
			if(rlabels[n] instanceof ResultPanel)
			{
				if(rlabels[n].getBackground().equals(highlight2_colour))
				{
					Buffer buf = ((ResultPanel)rlabels[n]).buf;
					
					runP4Command("p4 edit \"" + buf.getName() + "\"", buf.getDirectory(), null);
					buf.reload(jEdit.getActiveView());
					
					updateBufferInfo(buf, true);
				}
			}
		}
	}
	
	private void p4_revertHighlightedBuffers(boolean clobber){
		JPanel panel = (JPanel)flist.getViewport().getView();
		
		Component[] rlabels = panel.getComponents();
		
		for(int n = 0; n < rlabels.length; n++)
		{
			if(rlabels[n] instanceof ResultPanel)
			{
				if(rlabels[n].getBackground().equals(highlight2_colour))
				{
					Buffer buf = ((ResultPanel)rlabels[n]).buf;
					
					if(clobber)
					{
						Hashtable p4 = getP4FStat(buf);
						if(p4.get("action") == null)
						{
							/*
							 * If we're reverting a file that isn't open then we need to open it first..
							 */
							runP4Command("p4 edit \"" + buf.getName() + "\"", buf.getDirectory(), null);
						}
						
						runP4Command("p4 revert \"" + buf.getName() + "\"", buf.getDirectory(), null);
					}
					else
					{
						runP4Command("p4 revert -k \"" + buf.getName() + "\"", buf.getDirectory(), null);
					}
					buf.reload(jEdit.getActiveView());
					
					updateBufferInfo(buf, true);
				}
			}
		}
	}
	
	private void p4_diffHighlightedBuffers(String rev){
		JPanel panel = (JPanel)flist.getViewport().getView();
		Boolean bPrev = rev.equals("#prev");
		
		Component[] rlabels = panel.getComponents();
		
		for(int n = 0; n < rlabels.length; n++)
		{
			if(rlabels[n] instanceof ResultPanel)
			{
				if(rlabels[n].getBackground().equals(highlight2_colour))
				{
					Buffer buf = ((ResultPanel)rlabels[n]).buf;
					
					if(bPrev)
					{
						Hashtable p4 = getP4FStat(buf);
						rev = "#" + ((Integer.parseInt((String)p4.get("haveRev")))-1);
					}
					
					runCommand("p4 diff -f \"" + buf.getName() + "\"" + rev, buf.getDirectory(), null, false);
				}
			}
		}
	}
	
	private void p4_showBufferTimeline(Buffer buf){
		StringBuffer sbuf = new StringBuffer();
		runP4Command("p4 changes -m 20 \"" + buf.getName() + "\"", buf.getDirectory(), sbuf);
		
		String changes[] = sbuf.toString().split("\n");
		JList list = new JList(changes);
		
		JDialog diag = new JDialog(jEdit.getActiveView(), "Snacky P4 Timeline..", false);
		diag.setLayout(new BorderLayout());
		diag.add(new JScrollPane(list));
		diag.setSize(800,600);
		
		diag.setVisible(true);
	}
	
	private void p4_timelineHighlightedBuffers(){
		JPanel panel = (JPanel)flist.getViewport().getView();
		
		Component[] rlabels = panel.getComponents();
		
		for(int n = 0; n < rlabels.length; n++)
		{
			if(rlabels[n] instanceof ResultPanel)
			{
				if(rlabels[n].getBackground().equals(highlight2_colour))
				{
					Buffer buf = ((ResultPanel)rlabels[n]).buf;
					
					// show timeline GUI..
					p4_showBufferTimeline(buf);
				}
			}
		}
	}
	
	private void p4_lockHighlightedBuffers(){
		JPanel panel = (JPanel)flist.getViewport().getView();
		
		Component[] rlabels = panel.getComponents();
		
		for(int n = 0; n < rlabels.length; n++)
		{
			if(rlabels[n] instanceof ResultPanel)
			{
				if(rlabels[n].getBackground().equals(highlight2_colour))
				{
					Buffer buf = ((ResultPanel)rlabels[n]).buf;
					
					runP4Command("p4 lock \"" + buf.getName() + "\"", buf.getDirectory(), null);
					buf.reload(jEdit.getActiveView());
					
					updateBufferInfo(buf, true);
				}
			}
		}
	}
	
	private void p4_unlockHighlightedBuffers(){
		JPanel panel = (JPanel)flist.getViewport().getView();
		
		Component[] rlabels = panel.getComponents();
		
		for(int n = 0; n < rlabels.length; n++)
		{
			if(rlabels[n] instanceof ResultPanel)
			{
				if(rlabels[n].getBackground().equals(highlight2_colour))
				{
					Buffer buf = ((ResultPanel)rlabels[n]).buf;
					
					runP4Command("p4 unlock \"" + buf.getName() + "\"", buf.getDirectory(), null);
					buf.reload(jEdit.getActiveView());
					
					updateBufferInfo(buf, true);
				}
			}
		}
	}
	
	private void highlightCurrentBuffer(boolean allowrefresh, boolean scroll){
		// Refresh now incase we switched to a different buffer...
		if(allowrefresh) refresh();
		
		JPanel panel = (JPanel)flist.getViewport().getView();
		
		Component[] rlabels = panel.getComponents();
		
		Buffer activeBuffer = jEdit.getActiveView().getBuffer();
		ResultPanel trp = null;
		
		for(int n = 0; n < rlabels.length; n++)
		{
			if(rlabels[n] instanceof ResultPanel)
			{
				if(((ResultPanel)rlabels[n]).buf == activeBuffer)
				{
					rlabels[n].setBackground(highlight_colour);
					
					trp = (ResultPanel)rlabels[n];
				}
				else
				{
					rlabels[n].setBackground(background_colour);
				}
			}
		}
		
		if(scroll)
		{
			final ResultPanel rp = trp;
			SwingUtilities.invokeLater(new Thread(){
					public void run()
					{
						try{
							if(rp != null)
							{
								Point flp = flist.getLocationOnScreen();
								Point p = rp.getLocationOnScreen();
								Rectangle r = rp.getBounds();
								Rectangle location = new Rectangle(p.x-flp.x, p.y-flp.y, r.width, r.height);
								
								flist.getViewport().scrollRectToVisible(location);
							}
							flist.repaint();
						}catch(IllegalComponentStateException ex){}
					}
			});
		}
	}
	
	public void buildGUI(){
		filtbox.add(filtbox_name, BorderLayout.CENTER);
		filtbox.add(filtbox_marker, BorderLayout.EAST);
		
		if(floating){
			this.setPreferredSize(new Dimension(500, 500));
			
			add(flist, BorderLayout.CENTER);
			add(filtbox, BorderLayout.SOUTH);
			
			setSize(400, 400);
		}else{
			add(flist, BorderLayout.CENTER);
			add(filtbox, BorderLayout.SOUTH);
		}
		
		doLayout();
	}
	
	private class ResultPanel extends JPanel
	{
		public JLabel status, name, version;
		public Buffer buf;
		private String dir;
		private ImageIcon marker;
		private MultiImageIcon icon = new MultiImageIcon();
		private long fmod = 0;
		
		public ResultPanel(Buffer buf){
			this.buf = buf;
			name = new JLabel(buf.getName());
			dir = buf.getDirectory();
			
			marker = (ImageIcon)marker_cache.get(buf.getPath());
			if(marker == null)
			{
				marker = marker_none;
			}
			
			icon.setImage(marker.getImage());
			status = new JLabel();
			status.setIcon(icon);
			
			version = new JLabel("", JLabel.RIGHT);
			version.setForeground((Color)colours.get("l"));
			version.addMouseListener(mouseAdapter_Buffer);
			
			if(buf.getName().endsWith(".pj"))
			{
				name.setForeground((Color)colours.get("m"));
			}
			else
			{
				name.setForeground((Color)colours.get("c"));
			}
			
			name.addMouseListener(mouseAdapter_Buffer);
			addMouseListener(mouseAdapter_Buffer);
			
			setLayout(new BorderLayout());
			add(status, BorderLayout.WEST);
			add(name, BorderLayout.CENTER);
			add(version, BorderLayout.EAST);
			
			version.setForeground((Color)colours.get("l"));
			
			requestUpdateDetails(true);
		}
		
		public boolean requestUpdateDetails(boolean force)
		{
			if(force)
			{
				fmod = 0;
			}
			
			name.setText(buf.getName());
			name.setIcon(buf.getIcon());
			
			update_queue.push(this);
			
			return (!name.getText().equals(buf.getName()) || !dir.equals(buf.getDirectory()));
		}
		
		public void updateDetails()
		{
			long nfmod = buf.getLastModified();
			String ver = null;
			
			if(fmod != nfmod)
			{
				Hashtable p4 = getP4FStat(buf);
				if(p4 != null)
				{
					String action = (String)p4.get("action");
					String lock = (String)p4.get("ourLock");
					String olock = (String)p4.get("otherLock");
					String oopen = (String)p4.get("otherOpen");
					
					if((action != null))
					{
						if(action.equals("edit"))
						{
							if(olock != null)
							{
								icon.setOverlay(status_lockconflict);
							}
							else
							{
								if(lock != null)
								{
									icon.setOverlay(status_locked);
								}
								else
								{
									if(oopen != null)
									{
										icon.setOverlay(status_openconflict);
									}
									else
									{
										icon.setOverlay(status_open);
									}
								}
							}
						}
						else if(action.equals("add"))
						{
							icon.setOverlay(status_add);
						}
					}
					else
					{
						icon.setOverlay(null);
					}
					status.setIcon(marker_none);
					status.setIcon(icon);
					
					String haveRev = (String)p4.get("haveRev");
					String headRev = (String)p4.get("headRev");
					
					if((haveRev != null) && (headRev != null))
					{
						ver = "(" + haveRev + "/" + headRev + ")";
						if(haveRev.equals(headRev))
						{
							version.setForeground((Color)colours.get("l"));
						}
						else
						{
							version.setForeground((Color)colours.get("d"));
						}
					}
				}
				
				if(ver == null && (fmod != nfmod))
				{
					ver = getMKSVersionNumber(buf);
					version.setForeground((Color)colours.get("l"));
				}
				
				if(ver != null)
				{
					version.setText(ver);
				}
			}
			
			fmod = nfmod;
		}
		
		public void setMarker(ImageIcon marker)
		{
			this.marker = marker;
			
			if (marker != marker_none)
			{
				marker_cache.put(buf.getPath(), marker);
			} else {
				marker_cache.remove(buf.getPath());
			}
			icon.setImage(marker.getImage());
			
			saveMarkers();
			
			status.setIcon(marker_none);
			status.setIcon(icon);
		}
	}
	
	private class DirPanel extends JPanel
	{
		public JLabel name;
		public String path;
		
		public DirPanel(String path){
			this.path = path;
			
			name = new JLabel(" ");
			
			name.addMouseListener(mouseAdapter_Dir);
			addMouseListener(mouseAdapter_Dir);
			
			name.setBackground(background_colour);
			name.setForeground((Color)colours.get("f"));
			
			setLayout(new BorderLayout());
			add(name, BorderLayout.CENTER);
			
			calcName();
		}
		
		public void calcName()
		{
			String dname;
			
			FontMetrics fm = Toolkit.getDefaultToolkit().getFontMetrics(getFont());
			int width = flist.getViewport().getExtentSize().width - flist.getVerticalScrollBar().getWidth() - 15;
			
			if((width > 0) && fm.stringWidth(path) > width){
				dname = ""+path;
				
				while(fm.stringWidth(dname) > width)
				{
					int pos = dname.indexOf("\\");
					if(pos == -1){
						pos = dname.indexOf("/");
					}
					
					if(pos != -1){
						dname = dname.substring(pos+1, dname.length());
					}
					else
					{
						break;
					}
				}
			}
			else
			{
				dname = path;
			}
			
			name.setText(dname);
		}
	}
	
	private class MultiImageIcon extends ImageIcon
	{
		private ImageIcon overlay = null;
		
		public void setOverlay(ImageIcon overlay)
		{
			this.overlay = overlay;
		}
		
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			super.paintIcon(c, g, x, y);
			
			if(overlay != null)
			{
				g.drawImage(overlay.getImage(), x, y, c);
			}
		}
	}
}

