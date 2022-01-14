/*
 * SnackyOptionPane.java
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

import java.io.File;
import java.io.IOException;
import java.awt.Font;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.*;
import java.awt.*;
import java.io.*;

import org.gjt.sp.jedit.*;

import org.gjt.sp.jedit.Macros.*;


public class SnackyOptionPane extends AbstractOptionPane
{
	private JComboBox<String> sortType, displayVersions;
	private JTextField diffCommand;

	public SnackyOptionPane()
	{
		super(SnackyPlugin.NAME);
	}

	public void _init()
	{
		sortType = new JComboBox<String>();
		sortType.addItem("Directory");
		sortType.addItem("File");
		sortType.setSelectedItem(Snacky.getSortTypeName(jEdit.getProperty(
			SnackyPlugin.OPTION_PREFIX + "sorttype")));
			
		addComponent(jEdit.getProperty(
			SnackyPlugin.OPTION_PREFIX + "choose-sort.title"),
			sortType);
		
		displayVersions = new JComboBox<String>();
		displayVersions.addItem("Yes");
		displayVersions.addItem("No");
		displayVersions.setSelectedItem((jEdit.getBooleanProperty(
			SnackyPlugin.OPTION_PREFIX + "versions")?"Yes":"No"));
		
		addComponent(jEdit.getProperty(
			SnackyPlugin.OPTION_PREFIX + "choose-versions.title"),
			displayVersions);
		
		diffCommand = new JTextField(jEdit.getProperty(
			SnackyPlugin.OPTION_PREFIX + "diffcmd"));
		addComponent(jEdit.getProperty(
			SnackyPlugin.OPTION_PREFIX + "diff-command.title"),
			diffCommand);
	}

	public void _save()
	{
		jEdit.setProperty(SnackyPlugin.OPTION_PREFIX + "sorttype",
			"" + Snacky.getSortType((String)sortType.getSelectedItem()));
		
		jEdit.setProperty(SnackyPlugin.OPTION_PREFIX + "versions",
			"" + displayVersions.getSelectedItem().equals("Yes"));
		
		jEdit.setProperty(SnackyPlugin.OPTION_PREFIX + "diffcmd",
			diffCommand.getText());
	}
}

