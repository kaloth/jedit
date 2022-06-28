/*
 * SnoutOptionPane.java
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

import java.io.File;
import java.io.IOException;
import java.awt.Font;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.*;
import java.awt.*;
import java.io.*;

import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.AbstractOptionPane;
import org.gjt.sp.jedit.gui.FontSelector;

import org.gjt.sp.jedit.browser.VFSBrowser;


public class SnoutOptionPane extends AbstractOptionPane
			implements ActionListener
{
	private File dirlist = new File(jEdit.getSettingsDirectory() + "/snout/dirlist.txt");
	private File excludelist = new File(jEdit.getSettingsDirectory() + "/snout/exclude.txt");
	
	private JCheckBox showPath;
	private JTextField pathName;
	private JTextArea dirs;
	private JTextArea excludes;

	public SnoutOptionPane()
	{
		super(SnoutPlugin.NAME);
	}

	public void _init()
	{

		pathName = new JTextField(jEdit.getProperty(
			SnoutPlugin.OPTION_PREFIX + "ctagspath"));
		JButton pickPath = new JButton(jEdit.getProperty(
			SnoutPlugin.OPTION_PREFIX + "choose-file"));
		pickPath.addActionListener(this);

		JPanel pathPanel = new JPanel(new BorderLayout(0, 0));
		pathPanel.add(pathName, BorderLayout.CENTER);
		pathPanel.add(pickPath, BorderLayout.EAST);

		addComponent(jEdit.getProperty(
			SnoutPlugin.OPTION_PREFIX + "choose-file.title"),
			pathPanel);
			
		String strdirs = "";
		
		try{
			BufferedReader buffy = new BufferedReader(new InputStreamReader(new FileInputStream(dirlist)));
			String line;
			while((line = buffy.readLine()) != null){
				strdirs += line + "\r\n";
			}
			buffy.close();
		}catch(Exception e){
			e.printStackTrace();
		}
		
		dirs = new JTextArea(strdirs);
		JScrollPane drscroll = new JScrollPane(dirs);
		drscroll.setPreferredSize(new Dimension(300,150));
		
		addComponent(jEdit.getProperty(
			SnoutPlugin.OPTION_PREFIX + "dirlist.title"),
			drscroll);
			
		String strexcludes = "";
		
		try{
			BufferedReader buffy = new BufferedReader(new InputStreamReader(new FileInputStream(excludelist)));
			String line;
			while((line = buffy.readLine()) != null){
				strexcludes += line + "\r\n";
			}
			buffy.close();
		}catch(Exception e){
			e.printStackTrace();
		}
		
		excludes = new JTextArea(strexcludes);
		JScrollPane exscroll = new JScrollPane(excludes);
		exscroll.setPreferredSize(new Dimension(300,150));
		
		addComponent(jEdit.getProperty(
			SnoutPlugin.OPTION_PREFIX + "excludelist.title"),
			exscroll);
	}

	public void _save()
	{
		jEdit.setProperty(SnoutPlugin.OPTION_PREFIX + "ctagspath",
			pathName.getText());
			
		String strdirs = dirs.getText();
		
		try{
			dirlist.getParentFile().mkdirs();
			OutputStreamWriter spike = new OutputStreamWriter(new FileOutputStream(dirlist));
			spike.write(strdirs, 0, strdirs.length());
			spike.close();
		}catch(Exception e){
			e.printStackTrace();
		}
		
		String strexcludes = excludes.getText();
		
		try{
			excludelist.getParentFile().mkdirs();
			OutputStreamWriter spike = new OutputStreamWriter(new FileOutputStream(excludelist));
			spike.write(strexcludes, 0, strexcludes.length());
			spike.close();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	// end AbstractOptionPane implementation

	// begin ActionListener implementation
	public void actionPerformed(ActionEvent evt)
	{
		String currentPath = pathName.getText();
		if (currentPath.trim().length() == 0)
		{
			currentPath = null;
		}
		
		String[] paths = GUIUtilities.showVFSFileDialog(null,
			currentPath,VFSBrowser.OPEN_DIALOG,false);
		
		if(paths != null)
		{
			pathName.setText(paths[0]);
		}
	}
}

