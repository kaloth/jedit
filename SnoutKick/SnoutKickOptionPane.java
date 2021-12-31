/*
 * SnoutKickOptionPane.java
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


public class SnoutKickOptionPane extends AbstractOptionPane
			implements ActionListener
{
	private JCheckBox showPath;
	private JTextField pathName;
	private Choice sortType;

	public SnoutKickOptionPane()
	{
		super(SnoutKickPlugin.NAME);
	}

	public void _init()
	{

		pathName = new JTextField(jEdit.getProperty(
			SnoutKickPlugin.OPTION_PREFIX + "ctagspath"));
		JButton pickPath = new JButton(jEdit.getProperty(
			SnoutKickPlugin.OPTION_PREFIX + "choose-file"));
		pickPath.addActionListener(this);
		sortType = new Choice();
		sortType.add("Line");
		sortType.add("Name");
		sortType.add("Type");
		sortType.add("Structure");
		sortType.select(SnoutKick.getSortTypeName(jEdit.getProperty(
			SnoutKickPlugin.OPTION_PREFIX + "sorttype")));

		JPanel pathPanel = new JPanel(new BorderLayout(0, 0));
		pathPanel.add(pathName, BorderLayout.CENTER);
		pathPanel.add(pickPath, BorderLayout.EAST);

		addComponent(jEdit.getProperty(
			SnoutKickPlugin.OPTION_PREFIX + "choose-file.title"),
			pathPanel);
			
		addComponent(jEdit.getProperty(
			SnoutKickPlugin.OPTION_PREFIX + "choose-sort.title"),
			sortType);
	}

	public void _save()
	{
		jEdit.setProperty(SnoutKickPlugin.OPTION_PREFIX + "ctagspath",
			pathName.getText());
			
		jEdit.setProperty(SnoutKickPlugin.OPTION_PREFIX + "sorttype",
			"" + SnoutKick.getSortType(sortType.getSelectedItem()));
	}
	// end AbstractOptionPane implementation

	// begin ActionListener implementation
	public void actionPerformed(ActionEvent evt)
	{
		String[] paths = GUIUtilities.showVFSFileDialog(null,
			null,VFSBrowser.CHOOSE_DIRECTORY_DIALOG,false);
		if(paths != null)
		{
			pathName.setText(paths[0]);
		}
	}
}

