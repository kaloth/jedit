/*
 * SnoutKick.java
 * part of the SnoutKick plugin for the jEdit text editor
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
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

// from Swing:
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

// from jEdit:
import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.gui.*;
import org.gjt.sp.jedit.io.*;
import org.gjt.sp.jedit.msg.*;
import org.gjt.sp.util.Log;

import org.gjt.sp.jedit.search.*;
import org.gjt.sp.jedit.textarea.*;
import org.gjt.sp.jedit.syntax.*;


public class SnoutKick extends JPanel implements EBComponent, SnoutKickActions, DefaultFocusComponent, CaretListener, ListSelectionListener
{
	public static final int MAX_SEARCH_RESULTS = 5000;
	
	public static final int SORT_BY_LINE = 0, SORT_BY_NAME = 1, SORT_BY_TYPE = 2, SORT_BY_STRUCTURE = 3;
	
	public static String getSortTypeName(String type){
		return getSortTypeName(Integer.parseInt(type));
	}
	
	public static String getSortTypeName(int type){
		switch(type)
		{
			case SORT_BY_LINE:
				return "Line";
			case SORT_BY_NAME:
				return "Name";
			case SORT_BY_TYPE:
				return "Type";
			case SORT_BY_STRUCTURE:
				return "Structure";
		}
		
		return "Unknown";
	}
	
	public static int getSortType(String type){
		if(type.equals("Line")){
				return SORT_BY_LINE;
		}else if(type.equals("Name")){
				return SORT_BY_NAME;
		}else if(type.equals("Type")){
				return SORT_BY_TYPE;
		}else if(type.equals("Structure")){
				return SORT_BY_STRUCTURE;
		}
		
		return SORT_BY_STRUCTURE;
	}
	
	private static Hashtable icons = new Hashtable();
	private static Hashtable colours = new Hashtable();
	private static Color defaultcolour = new Color(0,0,0);
	private static Color background_colour = new Color(255,255,255);
	private static Color foreground_colour = new Color(0,0,0);
	private static Color highlight_colour = new Color(180,180,180);
	
	private View view;
	private boolean floating;
	private boolean running = true, needs_refresh = true;
	
	private int SortType = SORT_BY_STRUCTURE;
	
	private Thread workThread = null;
	private JEditTextArea jtxttarget = null;
	
	private JList flist = new JList();
	private JScrollPane flist_scroll = new JScrollPane(flist);
	private JLabel label = new JLabel(" ");
	private JTextField filtbox = new JTextField();
	
	private String oldpath = "#~ NOT A FILENAME ~#";
	private int oldline = -1;
	
	private ResultSet currentResults = null;
	private Hashtable resultCache = new Hashtable();
	
	private String[] languageList = new String[]{};
	
	private String[] command = new String[]{
				"ctags",
				"--excmd=n",
				"-f",
				"-"};
	
	private javax.swing.Timer label_timer = null;

	//
	// Constructor
	//

	public SnoutKick(View view, String position)
	{
		super(new BorderLayout());
		
		setLabelMsg("");
		
		flist_scroll.getVerticalScrollBar().setUnitIncrement(15);
		flist.setCellRenderer(new SnoutKickCellRenderer());
		
		flist.addListSelectionListener(this);
		flist.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent e)
			{
				if(flist.getModel().getSize() == 1)
				{
					int index = flist.locationToIndex(e.getPoint());
					if (index != -1)
					{
						valueChanged(new ListSelectionEvent(flist, index, index, false));
					}
				}
			}
		});
		flist.setFixedCellHeight(15);
		
		this.view = view;
		this.floating  = position.equals(DockableWindowManager.FLOATING);
		
		filtbox.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// refresh the list and apply a filter..
				refresh(true);
			}
		});
		
		loadProperties();
		applyEditorScheme();
		buildGUI();
		fetchLanguageList();
		
		listenToTextArea(jEdit.getActiveView().getTextArea());
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
	
	private void setLabelMsg(String msg)
	{
		setLabelMsg(msg, 0);
	}
	
	private void setLabelMsg(String msg, int clearAfter)
	{
		if (msg == null || msg.length() == 0)
		{
			label.setVisible(false);
			label.setText("");
		}
		else
		{
			label.setText(msg);
			label.setVisible(true);
			
			if (clearAfter > 0)
			{
				if (label_timer != null)
				{
					label_timer.stop();
					label_timer.setDelay(clearAfter);
					label_timer.setInitialDelay(clearAfter);
					label_timer.start();
				}
				else
				{
					label_timer = new javax.swing.Timer(clearAfter, new ActionListener()
					{
						public void actionPerformed(ActionEvent e)
						{
							label.setVisible(false);
							label.setText("");
							label_timer = null;
						}
					});
					label_timer.setRepeats(false);
					label_timer.start();
				}
			}
		}
	}
	
	public void valueChanged(ListSelectionEvent e)
	{
		if (flist.getSelectedIndex() == -1 || e.getValueIsAdjusting())
		{
			return;
		}
		
		SnoutKickListItem item = (SnoutKickListItem)flist.getModel().getElementAt(flist.getSelectedIndex());
		
		Hashtable res = item.res;
		
		if(res == null){
			LogMsg(Log.ERROR, SnoutKick.class,
					"Couldn't find this result entry, something is broken: " + item);
			return;
		}
		
		int lineNum = ((res.get("lineNum")!=null)?((Integer)res.get("lineNum")).intValue():-1);
		
		if(lineNum != -1)
		{
			lineNum = lineNum - 1;
			jEdit.getActiveView().getTextArea().scrollTo(lineNum, 0, false);
			jEdit.getActiveView().getTextArea().setCaretPosition(jEdit.getActiveView().getTextArea().getLineStartOffset(lineNum));
			jEdit.getActiveView().getTextArea().setSelection(new Selection.Range(jEdit.getActiveView().getTextArea().getLineStartOffset(lineNum), jEdit.getActiveView().getTextArea().getLineEndOffset(lineNum)));
		}
	}
	
	public void start(){
		listenToTextArea(jEdit.getActiveView().getTextArea());
		
		highlightCurrentTag();
	}
	
	public void stop(){
		// Remove all our listeners...
		if(jtxttarget != null)
		{
			jtxttarget.removeCaretListener(this);
		}
		jtxttarget = null;
	}
	
	private void applyEditorScheme()
	{
		highlight_colour = jEdit.getColorProperty("view.lineHighlightColor", Color.GRAY);
		background_colour = jEdit.getColorProperty("view.bgColor", Color.WHITE);
		foreground_colour = jEdit.getColorProperty("view.fgColor", Color.BLACK);
		
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
		flist.setSelectionBackground(highlight_colour);
	}
	
	public void focusOnDefaultComponent()
	{
		
	}
	
	public void caretUpdate(CaretEvent e){
		highlightCurrentTag();
	}
	
	/**
     * EditBus message handling.
     */
    public void handleMessage(EBMessage msg) {
        Buffer buffer = null;
        if (msg instanceof BufferUpdate) {

            BufferUpdate bu = (BufferUpdate) msg;
            buffer = bu.getBuffer();
			if(buffer == jEdit.getActiveView().getBuffer())
			{
				if (bu.getWhat() == BufferUpdate.CREATED) {
					highlightCurrentTag();
				}
				else if (bu.getWhat() == BufferUpdate.CLOSED) {
					resultCache.remove(bu.getBuffer().getPath());
				}
				else if (bu.getWhat() == BufferUpdate.DIRTY_CHANGED) {
					
				}
				else if (bu.getWhat() == BufferUpdate.LOADED) {
					highlightCurrentTag();
				}
				else if (bu.getWhat() == BufferUpdate.SAVED) {
					oldpath = "#~ NOT A FILENAME ~#";
					highlightCurrentTag();
					resultCache.remove(bu.getBuffer().getPath());
				}
			}
			else
			{
				if (bu.getWhat() == BufferUpdate.CREATED) {
					resultCache.remove(bu.getBuffer().getPath());
				}
				else if (bu.getWhat() == BufferUpdate.CLOSED) {
					resultCache.remove(bu.getBuffer().getPath());
				}
				else if (bu.getWhat() == BufferUpdate.DIRTY_CHANGED) {
					
				}
				else if (bu.getWhat() == BufferUpdate.LOADED) {
					resultCache.remove(bu.getBuffer().getPath());
				}
				else if (bu.getWhat() == BufferUpdate.SAVED) {
					resultCache.remove(bu.getBuffer().getPath());
				}
			}
        } else if (msg instanceof EditPaneUpdate) {
            EditPaneUpdate epu = (EditPaneUpdate) msg;
            if (epu.getWhat() == EditPaneUpdate.BUFFER_CHANGED) {
				highlightCurrentTag();
            }
        } else if (msg instanceof ViewUpdate) {
			ViewUpdate vu = (ViewUpdate) msg;
			if (vu.getWhat() == ViewUpdate.ACTIVATED) {
				highlightCurrentTag();
			}
			else if (vu.getWhat() == ViewUpdate.EDIT_PANE_CHANGED) {
				listenToTextArea(jEdit.getActiveView().getTextArea());
				
				highlightCurrentTag();
			}
		} else if (msg instanceof PropertiesChanged) {
			propertiesChanged();
		}
    }

	private synchronized void listenToTextArea(JEditTextArea jtxt){
		if(jtxttarget != null)
		{
			jtxttarget.removeCaretListener(this);
		}
		
		jtxt.addCaretListener(this);
		
		jtxttarget = jtxt;
	}
	
	private void loadProperties()
	{
		command[0] = jEdit.getProperty(
			SnoutKickPlugin.OPTION_PREFIX + "ctagspath");
		
		SortType = Integer.parseInt(jEdit.getProperty(
			SnoutKickPlugin.OPTION_PREFIX + "sorttype"));
	}

	private void propertiesChanged()
	{
		loadProperties();
		
		fetchLanguageList();
		
		oldpath = "#~ NOT A FILENAME ~#";
		highlightCurrentTag();
		
		applyEditorScheme();
	}
	
	private void fetchLanguageList()
	{
		/* Run ctags to generate the supported language list... */
		String fcom = command[0] + " --list-languages";
		
		try{
			Process p = Runtime.getRuntime().exec(fcom);
			
			/* Parse the output to create new ones.. */
			BufferedReader buffy = new BufferedReader(new InputStreamReader(p.getInputStream()));
			
			Vector v = new Vector();
			String line;
			while((line = buffy.readLine()) != null && v.size() < MAX_SEARCH_RESULTS-1){
				line = line.trim();
				if (!line.endsWith("[disabled]"))
				{
					v.addElement(line);
				}
			}
			buffy.close();
			
			LogMsg(Log.DEBUG, SnoutKick.class,
					"Ctags supports " + v.size() + " languages.");
			
			languageList = new String[v.size()];
			v.toArray(languageList);
		}catch(Exception e){
			setLabelMsg("FAILED: ctags did not run properly. Check it's location in the options menu.");
			LogMsg(Log.ERROR, SnoutKick.class,
					"FAILED: ctags did not run properly. Check it's location in the options menu.", e);
		}
	}

	// These JComponent methods provide the appropriate points
	// to subscribe and unsubscribe this object to the EditBus

	public void addNotify()
	{
		super.addNotify();
		EditBus.addToBus(this);
	}


	public void removeNotify()
	{
		running = false;
		super.removeNotify();
		EditBus.removeFromBus(this);
	}


	//
	// SnoutKickActions implementation
	//
	public synchronized void next(){
		setLabelMsg("Moving to next tag...");
		flist.setSelectedIndex(flist.getSelectedIndex()+1);
	}
	
	public synchronized void prev(){
		setLabelMsg("Moving to previous tag...");
		flist.setSelectedIndex(Math.max(0, flist.getSelectedIndex()-1));
	}
	
	public synchronized boolean refresh(boolean force){
		// If we haven't changed file then just return now...
		if(!force && oldpath.equals(jEdit.getActiveView().getBuffer().getPath())){
			return true;
		}
		
		// Is there a filter?
		String filter = filtbox.getText().trim().toLowerCase();
		if(filter.length() == 0)
		{
			filter = null;
		}
		
		// Look for cached results...
		ResultSet cached = (ResultSet)resultCache.get(jEdit.getActiveView().getBuffer().getPath());
		if(cached != null && cached.filter == filter)
		{
			currentResults = cached;
			setLabelMsg("Restoring Cached Results...");
			LogMsg(Log.DEBUG, SnoutKick.class,
				"Restoring Cached Results: " + jEdit.getActiveView().getBuffer().getPath());
			flist.setListData(currentResults.v);
			oldline = -1;
			oldpath = jEdit.getActiveView().getBuffer().getPath();
			setLabelMsg("");
			return true;
		}
		
		boolean failed = false;
		
		setLabelMsg("Building Command...");
		
		/* Create the ctags command... */
		Vector vcom = new Vector();
		for(int n = 0; n < command.length; n++)
		{
			vcom.addElement(command[n]);
		}
		
		/* Manually specify the language, if found */
		Mode mode = jEdit.getActiveView().getBuffer().getMode();
		String override = null;
		if(mode != null)
		{ 
			for(int n = 0; n < languageList.length; n++)
			{
				if (languageList[n].equalsIgnoreCase(mode.getName()))
				{
					override = languageList[n];
					break;
				}
			}
		}
		
		LogMsg(Log.DEBUG, SnoutKick.class,
					"Ctags language " + mode + " " + override);
		
		if (override != null)
		{
			vcom.addElement("--language-force=" + override);
		}
		
		/* Add that path to the current file... */
		vcom.addElement(jEdit.getActiveView().getBuffer().getPath());
		oldpath = jEdit.getActiveView().getBuffer().getPath();
		
		String[] fcom = new String[vcom.size()];
		vcom.copyInto(fcom);
		
		setLabelMsg("Running CTags...");
		LogMsg(Log.DEBUG, SnoutKick.class,
				"Running CTags: " + jEdit.getActiveView().getBuffer().getPath());
		
		/* Run ctags to generate the index... */
		try{
			Process p = Runtime.getRuntime().exec(fcom);
			
			BufferedReader buffy = new BufferedReader(new InputStreamReader(p.getInputStream()));
			Vector v = new Vector();
			String line;
			while((line = buffy.readLine()) != null && v.size() < MAX_SEARCH_RESULTS-1){
				v.addElement(line);
			}
			buffy.close();
			
			try{
				failed = !setSearchResults(v, filter);
			}catch(Exception e){
				//setLabelMsg("FAILED: An internal error occurred.");
				LogMsg(Log.ERROR, SnoutKick.class,
					"FAILED: An internal error occurred.", e);
				failed = true;
				
				flist.removeAll();
			}
		}catch(Exception e){
			setLabelMsg("FAILED: ctags did not run properly. Check it's location in the options menu.");
			LogMsg(Log.ERROR, SnoutKick.class,
					"FAILED: ctags did not run properly. Check it's location in the options menu.", e);
			failed = true;
			
			flist.removeAll();
		}
		
		if(!failed){
			setLabelMsg("");
		}
		
		System.gc();
		return !failed;
	}
	
	//
	// Private Functions...
	//
	private synchronized void highlightCurrentTag(){
		needs_refresh = true;
		
		if(workThread == null)
		{
			final SnoutKick me = this;
			
			running = true;
			workThread = (new Thread(){
					public void run(){
						while(running)
						{
							try{
								if(needs_refresh)
								{
									// If an error happened stop here...
									if(!refresh(false))
									{
										needs_refresh = false;
										// wait for a bit to stop the work thread being thrashed...
										try{
											sleep(100);
										}catch(Exception e){}
										
										continue;
									}
									
									if(currentResults != null && currentResults.lines.length > 0)
									{
										int line = jEdit.getActiveView().getTextArea().getCaretLine();
										
										if(line != oldline)
										{
											// Find out which one the user is near...
											int num = currentResults.lines.length-1;
											for(int n = 0; n < currentResults.lines.length; n++){
												if(((Integer)currentResults.lines[n].get("lineNum")).intValue()-1 > line)
												{
													num = Math.max(0,n-1);
													break;
												}
											}
											
											/*LogMsg(Log.DEBUG, SnoutKick.class,
													"Highlighting " + num + " line " + line);*/
											
											final SnoutKickListItem item = (SnoutKickListItem)currentResults.labelTable.get(currentResults.lines[num]);
											if(item != null)
											{
												Runnable gotoRes = (new Runnable(){
													public void run()
													{
														// select it from the list...
														flist.removeListSelectionListener(me);
														flist.clearSelection();
														flist.setSelectedValue(item, true);
														flist.addListSelectionListener(me);
														
														/*LogMsg(Log.DEBUG, SnoutKick.class,
																"Highlighted.");*/
													}
												});
												
												SwingUtilities.invokeLater(gotoRes);
											}
											
											oldline = line;
										}
										needs_refresh = false;
									}
								}
								
								// wait for a bit to stop the work thread being thrashed...
								sleep(100);
							}
							catch(Exception e)
							{
								// fixme: this error reporting is causing the huge JEdit logs.
								StringWriter sw = new StringWriter();
								e.printStackTrace(new PrintWriter(sw));
								LogMsg(Log.ERROR, SnoutKick.class,
									"WT Exception: " + sw.toString());
							}
						}
					}
			});
			
			workThread.start();
		}
	}
	
	private synchronized void clearResults()
	{
		currentResults = null;
		flist.removeAll();
	}
	
	private SnoutKickListItem addResult(ResultSet newResults, Hashtable res)
	{
		String tag = (String)res.get("tag");
		String type = (String)res.get("type");
		
		SnoutKickListItem item = new SnoutKickListItem(type, tag, res);
		
		newResults.resultTable.put(item, res);
		newResults.labelTable.put(res, item);
		if(type.equals("c") || type.equals("s") || type.equals("g") || type.equals("u") || type.equals("n")){
			newResults.classTable.put(tag, res);
		}
		
		return item;
	}
	
	public synchronized boolean setSearchResults(Vector results, String filter){
		if(results.size() < MAX_SEARCH_RESULTS)
		{
			ResultSet newResults = new ResultSet();
			newResults.filter = filter;
			
			setLabelMsg("Building result table...");
			clearResults();
			JPanel panel = new JPanel();
			panel.setBackground(background_colour);
			GridBagLayout gbl = new GridBagLayout();
			GridBagConstraints gblc = new GridBagConstraints();
			gblc.gridwidth = GridBagConstraints.REMAINDER;
			gblc.fill = GridBagConstraints.HORIZONTAL;
			panel.setLayout(gbl);
			
			Pattern pattern = null;
			if (filter != null)
			{
				pattern = Pattern.compile(filter);
			}
			
			for(int n = 0; n < results.size(); n++)
			{
				String line = (String)results.elementAt(n);
				
				if(n % 10 == 0){
					setLabelMsg("Building result table [" + (n+1) + "/" + results.size() + "]");
				}
				
				Hashtable res = new Hashtable();
				
				int p = line.lastIndexOf(";\"\t");
				String stTags[] = line.substring(0, p).split("\\t", 3);
				String exTags[] = line.substring(p+3, line.length()).split("\\t");
				
				res.put("tag", stTags[0]);
				if(pattern != null)
				{
					Matcher matcher = pattern.matcher(((String)res.get("tag")).toLowerCase());
					if(!matcher.find())
					{
						continue;
					}
				}
				
				File file = new File(stTags[1]);
				res.put("file", file);
				if(file.exists()){
					Buffer buffy = jEdit._getBuffer(file.getPath());
					if(buffy != null){
						res.put("buffy", buffy);
					}
				}
				
				/* can be a line number or search pattern */
				try
				{
					Integer ln = Integer.parseInt(stTags[2]);
					res.put("lineNum", ln);
				} catch(NumberFormatException nfe) {
					res.put("blurb", stTags[2]);
				}
				
				/* Start of EXTENDED tags */
				
				/* look for a type identifier... */
				res.put("type", exTags[0]);
				
				/* then all the rest are named key:value pairs */
				for(int i = 1; i < exTags.length; i++)
				{
					String pair[] = exTags[i].split(":", 2);
					if (pair.length == 2) {
						res.put(pair[0], pair[1]);
					}
				}
				
				if(res.containsKey("class"))
				{
					res.put("parent", res.get("class"));
				} else if(res.containsKey("enum"))
				{
					res.put("parent", res.get("enum"));
				} else if(res.containsKey("struct"))
				{
					res.put("parent", res.get("struct"));
				} else if(res.containsKey("union"))
				{
					res.put("parent", res.get("union"));
				} else if(res.containsKey("namespace"))
				{
					res.put("parent", res.get("namespace"));
				}
				
				addResult(newResults, res);
			}
			setLabelMsg("Building result table [" + results.size() + "/" + results.size() + "]");
			
			// Create a list of the tags sorted by line number...
			newResults.lines = new Hashtable[results.size()];
			Enumeration e = newResults.resultTable.elements();
			while(e.hasMoreElements()){
				Hashtable nw = (Hashtable)e.nextElement();
				Integer temp = (Integer)nw.get("lineNum");
				int nwLineNum = temp.intValue();
				
				for(int n = 0; n < newResults.lines.length; n++)
				{
					if(newResults.lines[n] == null){
						newResults.lines[n] = nw;
						break;
					}else{
						temp = (Integer)newResults.lines[n].get("lineNum");
						int olLineNum = temp.intValue();
						if(nwLineNum < olLineNum){
							for(int i = newResults.lines.length-1; i > n; i--){
								newResults.lines[i] = newResults.lines[i-1];
							}
							newResults.lines[n] = nw;
							break;
						}
					}
				}
			}
			
			newResults.v = new Vector();
			
			if(SortType == SORT_BY_LINE)
			{
				for(int n = 0; n < newResults.lines.length; n++)
				{
					if(newResults.lines[n] != null)
					{
						SnoutKickListItem item = (SnoutKickListItem)newResults.labelTable.get(newResults.lines[n]);
						newResults.v.addElement(item);
					}
				}
			}
			else if(SortType == SORT_BY_STRUCTURE)
			{
				/* first pass is to find all the non-class items and also
				 * the names of all the classes..
				 */
				ArrayList<SnoutKickListItem> parents = new ArrayList<SnoutKickListItem>();
				for(int n = 0; n < newResults.lines.length; n++)
				{
					if(newResults.lines[n] != null)
					{
						SnoutKickListItem item = (SnoutKickListItem)newResults.labelTable.get(newResults.lines[n]);
						String parent_name = item.getParentName();
						SnoutKickListItem parent_item = item.getParent(newResults);
						if(parent_name.equals("")) {
							newResults.v.addElement(item);
						} else if(parent_item == null || !parents.contains(parent_item)) {
							if (parent_item == null)
							{
								/* the parent isn't defined in the current file
								 * so insert a dummy entry
								 */
								Hashtable res = (Hashtable)item.res.clone();
								res.put("tag", parent_name);
								if(res.containsKey("class"))
								{
									res.put("type", "c");
								} else if(res.containsKey("enum"))
								{
									res.put("type", "g");
								} else if(res.containsKey("struct"))
								{
									res.put("type", "s");
								} else if(res.containsKey("union"))
								{
									res.put("type", "u");
								} else if(res.containsKey("namespace"))
								{
									res.put("type", "n");
								}
								res.remove("parent");
								parent_item = addResult(newResults, res);
								newResults.v.addElement(parent_item);
							}
							parents.add(parent_item);
						}
					}
				}
				/* Second pass inserts the class members */
				for(SnoutKickListItem parent : parents)
				{
					int pos = newResults.v.indexOf(parent);
					
					for(int n = 0; n < newResults.lines.length; n++)
					{
						if(newResults.lines[n] != null)
						{
							SnoutKickListItem item = (SnoutKickListItem)newResults.labelTable.get(newResults.lines[n]);
							if (item != parent && item.getParent(newResults) == parent)
							{
								newResults.v.insertElementAt(item, ++pos);
							}
						}
					}
				}
				/* Third pass resolves indents */
				for (int n = 0; n < newResults.v.size(); n++)
				{
					SnoutKickListItem item = (SnoutKickListItem)newResults.v.elementAt(n);
					SnoutKickListItem next = null;
					if (n+1 < newResults.v.size())
					{
						next = (SnoutKickListItem)newResults.v.elementAt(n+1);
					}
					item.resolveIndent(newResults, next, true);
				}
			}
			else if(SortType == SORT_BY_TYPE)
			{
				Hashtable sortedlines[] = new Hashtable[newResults.lines.length];
				
				e = newResults.resultTable.elements();
				while(e.hasMoreElements()){
					Hashtable nw = (Hashtable)e.nextElement();
					String nwType = (String)nw.get("type");
					String nwName = (String)nw.get("tag");
					
					for(int n = 0; n < sortedlines.length; n++)
					{
						if(sortedlines[n] == null){
							sortedlines[n] = nw;
							break;
						}else{
							String olType = (String)sortedlines[n].get("type");
							String olName = (String)sortedlines[n].get("tag");
							int cmp = nwType.compareToIgnoreCase(olType);
							
							if(cmp == 0)
							{
								cmp = nwName.compareToIgnoreCase(olName);
							}
							
							if(cmp < 0){
								for(int i = sortedlines.length-1; i > n; i--){
									sortedlines[i] = sortedlines[i-1];
								}
								sortedlines[n] = nw;
								break;
							}
						}
					}
				}
				
				for(int n = 0; n < sortedlines.length; n++)
				{
					SnoutKickListItem item = (SnoutKickListItem)newResults.labelTable.get(sortedlines[n]);
					newResults.v.addElement(item);
				}
			}
			else if(SortType == SORT_BY_NAME)
			{
				Hashtable sortedlines[] = new Hashtable[newResults.lines.length];
				
				e = newResults.resultTable.elements();
				while(e.hasMoreElements()){
					Hashtable nw = (Hashtable)e.nextElement();
					String nwName = (String)nw.get("tag");
					
					for(int n = 0; n < sortedlines.length; n++)
					{
						if(sortedlines[n] == null){
							sortedlines[n] = nw;
							break;
						}else{
							String olName = (String)sortedlines[n].get("tag");
							int cmp = nwName.compareToIgnoreCase(olName);
							
							if(cmp < 0){
								for(int i = sortedlines.length-1; i > n; i--){
									sortedlines[i] = sortedlines[i-1];
								}
								sortedlines[n] = nw;
								break;
							}
						}
					}
				}
				
				for(int n = 0; n < sortedlines.length; n++)
				{
					SnoutKickListItem item = (SnoutKickListItem)newResults.labelTable.get(sortedlines[n]);
					newResults.v.addElement(item);
				}
			}
			
			flist.setListData(newResults.v);
			currentResults = newResults;
			oldline = -1;
			resultCache.put(jEdit.getActiveView().getBuffer().getPath(), newResults);
		}
		else if(results.size() >= MAX_SEARCH_RESULTS)
		{
			setLabelMsg("ERROR - Too many results!");
			
			currentResults = null;
			flist.removeAll();
			return false;
		}
		
		return true;
	}
	
	public void buildGUI(){
		if(floating){
			this.setPreferredSize(new Dimension(500, 500));
			
			add(label, BorderLayout.NORTH);
			add(flist_scroll, BorderLayout.CENTER);
			add(filtbox, BorderLayout.SOUTH);
			
			setSize(400, 400);
		}else{
			add(label, BorderLayout.NORTH);
			add(flist_scroll, BorderLayout.CENTER);
			add(filtbox, BorderLayout.SOUTH);
		}
		
		doLayout();
	}
	
	private class SnoutKickListItem
	{
		public Icon icon;
		public String name;
		public String type;
		public Hashtable res;
		public int indent = 0;
		public String indent_text = "";
		
		public SnoutKickListItem(String type, String name, Hashtable res){
			this.type = type;
			this.name = name;
			this.res = res;
			icon = getIconForType(type);
		}
		
		public String getParentFullName()
		{
			String cl = (String)res.get("parent");
			if (cl != null) {
				return cl;
			} else {
				return "";
			}	
		}
		
		public String getParentName()
		{
			String cl = (String)res.get("parent");
			if (cl != null) {
				String toks[] = cl.split("\\.");
				if (toks.length==1){
					toks = cl.split("::");
				}
				return toks[toks.length-1];
			} else {
				return "";
			}
		}
		
		public SnoutKickListItem getParent(ResultSet resSet)
		{
			String parent = getParentName();
			
			if(resSet.classTable.containsKey(parent))
			{
				Hashtable cl_res = (Hashtable)resSet.classTable.get(parent);
				SnoutKickListItem cl_item = (SnoutKickListItem)resSet.labelTable.get(cl_res);
				
				return cl_item;
			}
			
			return null;
		}
		
		public int resolveIndent(ResultSet resSet, SnoutKickListItem next, boolean createText)
		{
			if(res.containsKey("parent"))
			{
				String next_cl = "";
				if(next != null)
				{
					next_cl = next.getParentFullName();
				}
				
				String cl = getParentFullName();
				
				setLabelMsg(name + " " + cl);
				
				SnoutKickListItem cl_item = getParent(resSet);
				
				if(cl_item != null)
				{
					indent = 1+cl_item.resolveIndent(resSet, null, false);
					
					if (createText)
					{
						indent_text = "";
						while (indent_text.length() < indent)
						{
							if(indent_text.length() == indent-1)
							{
								if (/*cl.startsWith(next_cl) || */next_cl.startsWith(cl)) {
									indent_text += (char)(0x2500 + 0x1C);
								} else {
									indent_text += (char)(0x2500 + 0x14);
								}
							} else {
								indent_text += (char)(0x2500 + 0x02);
							}
						}
					}
				} else {
					indent = 0;
				}
				
				return indent;
			}
			
			return 0;
		}
	}
	
	/*
	 * todo: new fancy version that doesn't work
	 */
	/*
	private class SnoutKickCellRenderer implements ListCellRenderer
	{
		public JPanel panel = new JPanel(){
			@Override
			public Dimension getMinimumSize() {
				return new Dimension(100, 20);
			}
	
			@Override
			public Dimension getPreferredSize() {
				return getMinimumSize();
			}
		};
		public JLabel name_label = new JLabel();
		public JLabel indent_label = new JLabel();
		
		public SnoutKickCellRenderer()
		{
			panel.setLayout(new BorderLayout());
			panel.add(indent_label, BorderLayout.WEST);
			panel.add(name_label, BorderLayout.CENTER);
			panel.add(new JTextArea(10,5), BorderLayout.EAST);
			panel.setOpaque(true);
			panel.setVisible(true);
		}
		
		public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus)
		{
			SnoutKickListItem item = (SnoutKickListItem)value;
			
			panel.setComponentOrientation(list.getComponentOrientation());
			if (isSelected)
			{
				panel.setBackground(list.getSelectionBackground());
				panel.setForeground(list.getSelectionForeground());
			}
			else
			{
				panel.setBackground(list.getBackground());
				panel.setForeground(list.getForeground());
			}
			panel.setEnabled(list.isEnabled());
			
			Color colour = (Color)colours.get(item.type);
			if(colour == null) colour = defaultcolour;
			
			indent_label.setForeground(Color.WHITE);
			indent_label.setIcon(item.icon);
			indent_label.setText(item.indent_text);
			indent_label.setFont(list.getFont());
			
			name_label.setForeground(colour);
			name_label.setText(item.name);
			name_label.setFont(list.getFont());
			
			return panel;
		}
	}
	*/
	
	private class SnoutKickCellRenderer implements ListCellRenderer
	{
		public JLabel label = new JLabel();
		
		public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus)
		{
			SnoutKickListItem item = (SnoutKickListItem)value;
			
			label.setComponentOrientation(list.getComponentOrientation());
			if (isSelected)
			{
				label.setBackground(list.getSelectionBackground());
				label.setForeground(list.getSelectionForeground());
			}
			else
			{
				label.setBackground(list.getBackground());
				label.setForeground(list.getForeground());
			}
			label.setEnabled(list.isEnabled());
			
			Color colour = (Color)colours.get(item.type);
			if(colour == null) colour = defaultcolour;
			
			label.setForeground(colour);
			label.setIcon(item.icon);
			label.setText(item.indent_text + " " + item.name);
			label.setFont(list.getFont());
			label.setOpaque(true);
			
			return label;
		}
	}
	
	private class ResultSet
	{
		public Hashtable resultTable = new Hashtable();
		public Hashtable labelTable = new Hashtable();
		public Hashtable classTable = new Hashtable();
		public Hashtable[] lines;
		public Vector v;
		public String filter;
	}
	
	private static Icon getIconForType(String type)
	{
		Icon res = (Icon)icons.get(type);
		
		if(res == null)
		{
			Image img = new BufferedImage(15,15,BufferedImage.TYPE_INT_ARGB);
			Graphics g = img.getGraphics();
			FontMetrics fm = Toolkit.getDefaultToolkit().getFontMetrics(g.getFont());
			g.setColor(defaultcolour);
			g.drawString(type, 0, fm.getHeight()-3);
			res = new ImageIcon(img);
			icons.put(type, res);
		}
		
		return res;
	}
}

