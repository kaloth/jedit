/*
 * Snout.java
 * part of the Snout plugin for the jEdit text editor
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
import java.io.*;
import java.util.*;
import java.util.regex.*;

// from Swing:
import javax.swing.*;
import javax.swing.event.*;

// from jEdit:
import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.gui.*;
import org.gjt.sp.jedit.io.*;
import org.gjt.sp.jedit.msg.PropertiesChanged;
import org.gjt.sp.jedit.msg.ViewUpdate;
import org.gjt.sp.util.Log;

import org.gjt.sp.jedit.search.*;
import org.gjt.sp.jedit.textarea.*;
import org.gjt.sp.jedit.syntax.*;


public class Snout extends JPanel implements EBComponent, SnoutActions, DefaultFocusComponent
{
	public static int MAX_SEARCH_RESULTS = 500;
	private static Hashtable colours = new Hashtable();
	private static Color defaultcolour = new Color(0,0,0);
	private static Color background_colour = new Color(255,255,255);
	private static Color foreground_colour = new Color(0,0,0);
	private static Color highlight_colour = new Color(180,180,180);
	
	private View view;
	private boolean floating;
	
	private Thread workThread = null;
	private Thread gotoThread = null;
	
	private JScrollPane flist = new JScrollPane();
	private JLabel label = new JLabel(" ");
	private JComboBox txt = new JComboBox();
	
	private String ctagsdir = "C:/ctags";
	private String workdir = jEdit.getSettingsDirectory() + "/snout";
	private File index = new File(workdir + "/index.txt");
	private File dirlist = new File(workdir + "/dirlist.txt");
	private File excludelist = new File(workdir + "/exclude.txt");
	
	private Hashtable resultTable = new Hashtable();
	private Hashtable resultTree = new Hashtable();
	private MouseAdapter mouseAdapter = null;
	
	private String[] command = new String[]{
				ctagsdir + "/ctags",
				"-o" + index.getPath(),
				"-R",
				"--excmd=p",
				"--exclude=@" + excludelist.getPath()};
	
	private Icon defaultIcon;
	
	private String searchstr = null;

	//
	// Constructor
	//

	public Snout(View view, String position)
	{
		super(new BorderLayout());
		
		flist.getVerticalScrollBar().setUnitIncrement(15);
		txt.setEditable(true);
		
		if(!index.exists()){
			try{
				// This is the first time we have been run...
				// Create the working folder and initial file contents...
				index.getParentFile().mkdirs();
				excludelist.createNewFile();
				dirlist.createNewFile();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		
		// Setup the listener that will do the file search from the ctags result...
		mouseAdapter = (new MouseAdapter(){
				public void mouseClicked(MouseEvent e){
					Component c = (Component)e.getSource();
					if(c instanceof JLabel) c = c.getParent();
					
					final Hashtable res = (Hashtable)resultTable.get(c);
					
					if(res == null){
						LogMsg(Log.ERROR, Snout.class,
								"Couldn't find this result entry, something is broken: " + c);
						return;
					}
					
					Runnable gotoRes = new Runnable(){
						public void run()
						{
							gotoResult(res);
						}
					};
					SwingUtilities.invokeLater(gotoRes);
				}
		});
		
		this.view = view;
		this.floating  = position.equals(DockableWindowManager.FLOATING);

		if(jEdit.getSettingsDirectory() != null)
		{
			this.ctagsdir = jEdit.getProperty(
				SnoutPlugin.OPTION_PREFIX + "ctagspath");
			if(this.ctagsdir == null || this.ctagsdir.length() == 0)
			{
				this.ctagsdir = "C:\\ctags";
				jEdit.setProperty(
					SnoutPlugin.OPTION_PREFIX + "ctagspath",
					this.ctagsdir);
			}
			else
			{
				command[0] = ctagsdir + "\\ctags";
			}
		}
		
		applyEditorScheme();
		buildGUI();
		
		txt.addItemListener(new ItemListener(){
			String last = "";
			
			public void itemStateChanged(ItemEvent e) {
				String str = (String)txt.getSelectedItem();
				
				if(!last.equals(str)){
					searchIndex(str);
				}
				
				last = str;
			}
		});
		txt.setMaximumRowCount(6);
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
	
	private synchronized void gotoResult(final Hashtable res)
	{
		String path = ((File)res.get("file")).getPath();
		
		Buffer buf = jEdit.getBuffer(path);
		if(buf == null){
			LogMsg(Log.DEBUG, Snout.class,
				"Opening File: " + path);
			label.setText("Opening File: " + path);
			buf = jEdit.openFile(jEdit.getActiveView(), path);
		}else{
			LogMsg(Log.DEBUG, Snout.class,
				"Switching to Buffer: " + path);
			label.setText("Switching to Buffer: " + path);
			jEdit.getActiveView().setBuffer(buf);
		}
		
		if(buf == null){
			label.setText("Error: Couldn't open the file: " + path);
			LogMsg(Log.ERROR, Snout.class,
				"Couldn't open the file: " + path);
			gotoThread = null;
			return;
		}
		
		Runnable gotoRes = new Runnable(){
			public void run()
			{
				LogMsg(Log.DEBUG, Snout.class,
							"Beginning search.");
				
				String name = ((File)res.get("file")).getName();
				String tag = (String)res.get("tag");
				String blurb = ((res.get("blurb")!=null)?(String)res.get("blurb"):null);
				int lineNum = ((res.get("lineNum")!=null)?((Integer)res.get("lineNum")).intValue():-1);
				
				if(blurb != null)
				{
					LogMsg(Log.DEBUG, Snout.class,
							"Using blurb search..");
					
					/* Search for the source extract... */
					SearchAndReplace.setSearchString(blurb);
					SearchAndReplace.setAutoWrapAround(true);
					SearchAndReplace.setReverseSearch(false);
					SearchAndReplace.setIgnoreCase(false);
					SearchAndReplace.setRegexp(false);
					SearchAndReplace.setSearchFileSet(new CurrentBufferSet());
					if(!SearchAndReplace.find(jEdit.getActiveView()))
					{
						label.setText("Error: Couldn't find search target!");
						LogMsg(Log.ERROR, Snout.class,
							"Couldn't find search target!");
						gotoThread = null;
						return;
					}
					else
					{
						label.setText("Done");
					}
				}else if(lineNum > 0){
					LogMsg(Log.DEBUG, Snout.class,
							"Using linenum search..");
					
					lineNum = lineNum - 1;
					jEdit.getActiveView().getTextArea().scrollTo(lineNum, 0, false);
					jEdit.getActiveView().getTextArea().setCaretPosition(jEdit.getActiveView().getTextArea().getLineStartOffset(lineNum));
					jEdit.getActiveView().getTextArea().setSelection(new Selection.Range(jEdit.getActiveView().getTextArea().getLineStartOffset(lineNum), jEdit.getActiveView().getTextArea().getLineEndOffset(lineNum)));
					label.setText("Done");
				}else{
					label.setText("Error: Search data was blank");
					LogMsg(Log.ERROR, Snout.class,
							"Search data was blank");
					gotoThread = null;
					return;
				}
				
				LogMsg(Log.DEBUG, Snout.class,
							"Seach complete.");
			}
		};
		
		SwingUtilities.invokeLater(gotoRes);
	}

	public void focusOnDefaultComponent()
	{
		txt.requestFocus();
	}

	//
	// Attribute methods
	//

	//
	// EBComponent implementation
	//

	public void handleMessage(EBMessage message)
	{
		if (message instanceof PropertiesChanged)
		{
			propertiesChanged();
		}
	}


	private void propertiesChanged()
	{
		ctagsdir = jEdit.getProperty(
			SnoutPlugin.OPTION_PREFIX + "ctagspath");
		
		workdir = ctagsdir + "/snout";
		
		command[0] = ctagsdir + "/ctags";
		
		applyEditorScheme();
		
		searchIndex(searchstr);
	}
	
	private void applyEditorScheme()
	{
		defaultIcon = null;
		
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
		super.removeNotify();
		EditBus.removeFromBus(this);
	}


	//
	// SnoutActions implementation
	//
	
	public void refreshIndex(){
		view.getDockableWindowManager().showDockableWindow("Snout");
		refreshIndex(true);
	}
	
	public void search(){
		view.getDockableWindowManager().showDockableWindow("Snout");
		String search = jEdit.getActiveView().getTextArea().getSelectedText();
		searchIndex(search);
	}
	
	private String[] getEmbeddedVars(String var)
	{
		Vector v = new Vector();
		boolean inVar = false;
		int startpos = 0;
		
		for(int n = 0; n < var.length(); n++)
		{
			char c = var.charAt(n);
			
			if(c =='%')
			{
				if(!inVar)
				{
					startpos = n+1;
					inVar = true;
				}
				else
				{
					if(startpos != n)
					{
						String varname = var.substring(startpos, n);
						v.addElement(varname);
					}
					
					inVar = false;
				}
			}
		}
		
		if(v.size() > 0)
		{
			String[] res = new String[v.size()];
			v.copyInto(res);
			
			return res;
		}
		else
		{
			return null;
		}
	}
	
	private String resolveVars(Hashtable env, String var)
	{
		String res = new String(var);
		String[] varsused = getEmbeddedVars(var);
		
		if(varsused != null)
		{
			for(int n = 0; n < varsused.length; n++)
			{
				String value = (String)env.get(varsused[n]);
				
				if(value != null)
				{
					res = res.replaceAll("\\%" + varsused[n] + "\\%", Matcher.quoteReplacement(value));
				}
			}
		}
		
		return res;
	}
	
	public void addCurrentDirectoryToIndex(){
		String newDir = jEdit.getActiveView().getBuffer().getDirectory();
		Hashtable vcom = new Hashtable();
		
		newDir = newDir.replace('\\', '/');
		if(newDir.endsWith("/")) newDir = newDir.substring(0, newDir.length()-1);
		
		/* Read the user defined src directories... */
		if(dirlist.exists())
		{
			try{
				BufferedReader buffy = new BufferedReader(new InputStreamReader(new FileInputStream(dirlist)));
				String line;
				while((line = buffy.readLine()) != null){
					vcom.put(line, "");
				}
				buffy.close();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		
		/* Add the new one... */
		vcom.put(newDir, "");
		
		int n = 0;
		String[] dirs = new String[vcom.size()];
		
		Enumeration e = vcom.keys();
		while(e.hasMoreElements()){
			dirs[n++] = (String)e.nextElement();
		}
		
		Arrays.sort(dirs);
		
		/* Write the user defined src directories... */
		try{
			PrintWriter spike = new PrintWriter(new FileOutputStream(dirlist));
			
			for(n = 0; n < dirs.length; n++){
				spike.println(dirs[n]);
			}
			spike.close();
		}catch(Exception ex){
			ex.printStackTrace();
		}
		
		label.setText("Added: " + newDir);
	}
	
	//
	// Private Functions...
	//
	
	private void clearResults()
	{
		resultTable.clear();
		resultTree.clear();
	}
	
	private ResultPanel addResult(Hashtable res)
	{
		String path = ((File)res.get("file")).getPath();
		String tag = (String)res.get("tag");
		String blurb = ((res.get("blurb")!=null)?(String)res.get("blurb"):null);
		int lineNum = ((res.get("lineNum")!=null)?((Integer)res.get("lineNum")).intValue():-1);
		Buffer buffy = ((res.get("buffy")!=null)?(Buffer)res.get("buffy"):null);
		String type = (String)res.get("type");
		
		Color colour = (Color)colours.get(type);
		if(colour == null) colour = defaultcolour;
		
		ResultPanel rlabel = new ResultPanel(type, (tag==null?""+lineNum:tag));
		
		if(buffy != null){
			rlabel.type.setIcon(buffy.getIcon());
		}else{
			if(defaultIcon == null){
				Image img = createImage(10,10);
				Graphics g = img.getGraphics();
				g.setColor(background_colour);
				g.fillRect(0,0,10,10);
				defaultIcon = new ImageIcon(img);
			}
			
			rlabel.type.setIcon(defaultIcon);
		}
		
		rlabel.name.setForeground(colour);
		
		rlabel.setBackground(background_colour);
		rlabel.setForeground(foreground_colour);
		
		resultTable.put(rlabel, res);
		return rlabel;
	}
	
	private long getFilePosition(String str)
	{
		return (str.charAt(0) | (str.charAt(1) << 8)) * 8;
	}
	
	private void refreshIndex(boolean force)
	{
		if(index.exists() && !force) return;
		
		if(workThread != null) return;
		
		workThread = (new Thread(){
				public void run(){
					boolean failed = false;
					
					label.setText("Building Command...");
					
					/* Create the ctags command... */
					Vector vcom = new Vector();
					for(int n = 0; n < command.length; n++)
					{
						vcom.addElement(command[n]);
					}
					
					int srccount = 0;
					
					/* Read the user defined src directories... */
					if(dirlist.exists())
					{
						Hashtable fullEnvHash = new Hashtable();
						Map map = System.getenv();
						Object[] keys = map.keySet().toArray();
						
						for(int n = 0; n < keys.length; n++)
						{
							fullEnvHash.put(((String)keys[n]).toUpperCase(), (String)map.get(keys[n]));
						}
						
						try{
							BufferedReader buffy = new BufferedReader(new InputStreamReader(new FileInputStream(dirlist)));
							String line;
							while((line = buffy.readLine()) != null){
								if(!line.startsWith("#")){
									vcom.addElement(resolveVars(fullEnvHash, line));
									srccount++;
								}
							}
							buffy.close();
						}catch(Exception e){
							e.printStackTrace();
						}
					}
					
					if(srccount == 0){
						// No source files specified. Tell the user to enter some in the plugin options.
						label.setText("STOPPED: Please add some source directories in the Snout options menu.");
						workThread = null;
						return;
					}
					
					String[] fcom = new String[vcom.size()];
					vcom.copyInto(fcom);
					
					label.setText("Running CTags...");
					
					/* Run ctags to generate the index file... */
					try{
						Process p = Runtime.getRuntime().exec(fcom);
						while(true){
							try{
								Thread.currentThread().sleep(1000);
								if(index.exists()){
									long fileSize = index.length() / 1024 / 1024;
									label.setText("Running CTags (" + fileSize + "mb)...");
								}
								p.exitValue();
								break; // if we get an exit value then ctags has quit.
							}catch(Exception ex){
								// do nothing as the process hasn't exited yet...
							}
						}
						p.waitFor();
					}catch(Exception e){
						e.printStackTrace();
						label.setText("FAILED: ECtags did not run properly. Check it's location in the options menu.");
						failed = true;
					}
					
					
					if(index.exists() && !failed){
						/*
							Now split the index file into a new file
							for each letter of the alphabet to improve search
							speeds when index is really big...
						*/
						try{
							BufferedReader buffy = new BufferedReader(new InputStreamReader(new FileInputStream(index)));
							RandomAccessFile hash = new RandomAccessFile(new File(index.getParent() + "\\hash.dat"), "rw");
							String line;
							int lineNum = 0;
							long charCount = 0;
							char oldc = ' ';
							
							label.setText("Initialising Index...");
							for(int n = 0; n < 0xFFFF; n++){
								hash.writeLong(0);
							}
							
							label.setText("Optimising Index...");
							while((line = buffy.readLine()) != null)
							{
								char c = line.charAt(0);
								if(c == oldc && lineNum != 0){
									long pos = getFilePosition(line);
									hash.seek(pos);
									long check = hash.readLong();
									if(check == 0){
										hash.seek(pos);
										hash.writeLong(charCount);
									}
								}else{
									label.setText("Optimising '" + c + "'...");
									
									oldc = c;
									
									if(hash != null){
										long pos = getFilePosition(line);
										hash.seek(pos);
										long check = hash.readLong();
										if(check == 0){
											hash.seek(pos);
											hash.writeLong(charCount);
										}
									}
								}
								charCount += line.length() + 2;
								lineNum++;
							}
							
							hash.close();
							buffy.close();
						}catch(Exception e){
							e.printStackTrace();
							label.setText("FAILED: Index optimisation encountered an error: " + e.getMessage());
							failed = true;
						}
					}else{
						label.setText("WARNING: ECtags did not produce any results.");
						failed = true;
					}
					
					if(!failed){
						long fileSize = index.length() / 1024 / 1024;
						label.setText("Indexing Finished. (" + fileSize + "mb)");
					}
					
					workThread = null;
				}
		});
		
		workThread.start();
	}
	
	public void setSearchResults(Vector results){
		clearResults();
		label.setText("Building result table...");
		JPanel panel = new JPanel();
		JPanel fpanel = new JPanel();
		panel.setBackground(background_colour);
		fpanel.setBackground(background_colour);
		GridBagLayout gbl = new GridBagLayout();
		GridBagLayout gbl2 = new GridBagLayout();
		GridBagConstraints gblc = new GridBagConstraints();
		gblc.gridwidth = GridBagConstraints.REMAINDER;
		//gblc.gridheight = 15;
		gblc.fill = GridBagConstraints.HORIZONTAL;
		panel.setLayout(gbl);
		fpanel.setLayout(gbl2);
		if(results.size() > 0)
		{
			for(int n = 0; n < results.size(); n++){
				Hashtable res = new Hashtable();
				res.clear();
				
				if(n % 10 == 0){
					label.setText("Building result table [" + (n+1) + "/" + results.size() + "]");
				}
				
				String line = (String)results.elementAt(n);
				int i, i2;
				i = line.indexOf("\t");
				if(i == -1) continue;
				res.put("tag", line.substring(0, i));
				
				line = line.substring(i+1, line.length());
				
				i = line.indexOf("\t");
				if(i == -1) continue;
				File file = new File(line.substring(0, i));
				res.put("file", file);
				if(file.exists()){
					Buffer buffy = jEdit._getBuffer(file.getPath());
					if(buffy != null){
						res.put("buffy", buffy);
					}
				}
				
				line = line.substring(i+1, line.length());
				
				i = line.indexOf("/^");
				i2 = line.indexOf("$/;\"", i+2);
				if(i2 == -1) i2 = line.indexOf("/;\"", i+2);
				
				String blurb = null;
				if((i != -1) && (i2 != -1) && (i < i2)){
					blurb = line.substring(i+2, i2);
				}else{
					blurb = null;
					/* No blurb? look for a line number... */
					i2 = line.indexOf(";\"");
					if(i2 != -1){
						res.put("lineNum", new Integer(line.substring(0, i2)));
					}else{
						continue;
					}
				}
				if(blurb != null){
					res.put("blurb", blurb);
				}
				
				line = line.substring(i2+2, line.length());
				
				/* look for a type identifier... */
				i = line.indexOf("\t");
				if(i != -1 && i+2 <= line.length()){
					String type = line.substring(i+1, i+2);
					res.put("type", type);
				}
				
				ResultPanel rlabel = addResult(res);
				rlabel.setPreferredSize(new Dimension(rlabel.getPreferredSize().width, 18));
				gbl.setConstraints(rlabel, gblc);
				panel.add(rlabel);
				
				String name, path = file.getPath();
				if(path.length() > 50){
					name = path.substring(path.length()-50, path.length());
					int pos = name.indexOf("\\");
					if(pos == -1){
						pos = name.indexOf("/");
					}
					
					if(pos != -1){
						name = "..." + name.substring(pos, name.length());
					}
				}
				else
				{
					name = path;
				}
				
				JLabel pathlabel = new JLabel(name);
				pathlabel.setPreferredSize(new Dimension(pathlabel.getPreferredSize().width, 18));
				pathlabel.setForeground(foreground_colour);
				
				gbl2.setConstraints(pathlabel, gblc);
				fpanel.add(pathlabel);
			}
			
			label.setText("Building result table [" + results.size() + "/" + results.size() + "]");
		}
		
		JPanel panel2 = new JPanel();
		panel2.setBackground(background_colour);
		panel2.setLayout(new BorderLayout());
		panel2.add(panel, BorderLayout.WEST);
		panel2.add(fpanel, BorderLayout.EAST);
		flist.setViewportView(panel2);
		
		label.setText("Search Complete (" + results.size() + (results.size()==1?" result)":" results)"));
	}
	
	private void searchIndex(final String searchstr)
	{
		if(searchstr == null) return;
		if(searchstr.equals("")) return;
		
		if(searchstr.length() < 2){
			label.setText("Search string must be at least 2 characters long!");
			return;
		}
		
		if(!index.exists()){
			refreshIndex(true);
			
			return;
		}
		
		this.searchstr = searchstr;
		
		if(workThread != null) return;
		
		workThread = (new Thread(){
				public void run(){
					String search = searchstr;
					flist.setVisible(false);
					
					txt.removeItem(search);
					txt.addItem(search);
					txt.setSelectedItem(search);
					
					if(index.exists()){
						label.setText("Searching...");
						Vector results = new Vector();
						
						if(search != null && !search.trim().equals("")){
							search = search.trim();
							// A quick search using hashing...
							
							long pos = getFilePosition(search);
							try{
								RandomAccessFile hash = new RandomAccessFile(new File(index.getParent() + "\\hash.dat"), "rw");
								hash.seek(pos);
								long charCount = hash.readLong();
								hash.close();
								
								BufferedReader buffy = new BufferedReader(new InputStreamReader(new FileInputStream(index)));
								
								buffy.skip(charCount);
								String line;
								while((line = buffy.readLine()) != null && results.size() < MAX_SEARCH_RESULTS)
								{
									if(line.startsWith(search)){
										results.addElement(line);
									}else if(line.compareTo(search) > 0){
										break;
									}
								}
								
								buffy.close();
							}catch(Exception e){
								e.printStackTrace();
							}
						}
						
						setSearchResults(results);
					}else{
						label.setText("ERROR: Index File Not Found");
					}
					
					workThread = null;
					flist.setVisible(true);
					flist.validate();
				}
		});
		
		workThread.start();
	}
	
	public void buildGUI(){
		if(floating){
			this.setPreferredSize(new Dimension(500, 500));
			
			JPanel panel = new JPanel();
			panel.setLayout(new BorderLayout());
			add(label, BorderLayout.NORTH);
			
			panel.add(txt, BorderLayout.CENTER);
			
			add(panel, BorderLayout.SOUTH);
			add(flist, BorderLayout.CENTER);
			
			setSize(400, 400);
			doLayout();
		}else{
			add(label, BorderLayout.NORTH);
			add(txt, BorderLayout.SOUTH);
			
			add(flist, BorderLayout.CENTER);
			doLayout();
		}
	}
	
	private class ResultPanel extends JPanel
	{
		public JLabel type;
		public JLabel name;
		
		public ResultPanel(String type, String name){
			this.type = new JLabel(" " + type + " ");
			this.name = new JLabel(name);
			
			this.name.addMouseListener(mouseAdapter);
			this.type.addMouseListener(mouseAdapter);
			addMouseListener(mouseAdapter);
			
			setLayout(new BorderLayout());
			add(this.type, BorderLayout.WEST);
			add(this.name, BorderLayout.CENTER);
			
			FontMetrics fm = Toolkit.getDefaultToolkit().getFontMetrics(this.type.getFont());
			this.type.setPreferredSize(new Dimension(fm.getMaxAdvance() + 20, 0));
		}
	}
}

