/*
 * SplitPaneOptionPane.java
 * part of the SplitPane plugin for the jEdit text editor
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
import java.util.*;

import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.gui.DockableWindowManager;
import org.gjt.sp.jedit.Macros.*;


public class SplitPaneOptionPane extends AbstractOptionPane
{
	private JButton add, edit, remove;
	private JComboBox<String> profile;
	
	public SplitPaneOptionPane()
	{
		super(SplitPanePlugin.NAME);
	}

	private void updateProfileList(String select)
	{
		profile.removeAllItems();
		
		String profiles = jEdit.getProperty(
			SplitPanePlugin.OPTION_PREFIX + "profiles");
		
		if(profiles != null)
		{
			String profile_list[] = profiles.split("\\|");
			
			for(int n = 0; n < profile_list.length; n++)
			{
				if(!profile_list[n].equals(""))
				{
					profile.addItem(profile_list[n]);
				}
			}
			
			if (select != null)
			{
				profile.setSelectedItem(select);
			}
		}
	}
	
	private void removeProfile(String profilename)
	{
		String profiles = jEdit.getProperty(
			SplitPanePlugin.OPTION_PREFIX + "profiles");
		
		if(profiles == null) profiles = "";
		
		String profile_list[] = profiles.split("\\|");
		profiles = "";
		
		for(int n = 0; n < profile_list.length; n++)
		{
			if(!profile_list[n].equals(profilename))
			{
				profiles = profiles + profile_list[n] + "|";
			}
		}
		
		jEdit.setProperty(
			SplitPanePlugin.OPTION_PREFIX + "profiles", profiles);
		
		jEdit.setProperty(
			SplitPanePlugin.OPTION_PREFIX + "profile-" + profilename, null);
		
		updateProfileList(null);
	}
	
	private void addProfile(String profilename, String plugin1, String plugin2)
	{
		String profiles = jEdit.getProperty(
			SplitPanePlugin.OPTION_PREFIX + "profiles");
		
		if(profiles == null) profiles = "";
		
		String profile_list[] = profiles.split("\\|");
		profiles = "";
		
		for(int n = 0; n < profile_list.length; n++)
		{
			if(!profile_list[n].equals(profilename))
			{
				profiles = profiles + profile_list[n] + "|";
			}
		}
		profiles = profiles + profilename;
		
		jEdit.setProperty(
			SplitPanePlugin.OPTION_PREFIX + "profiles", profiles);
		
		jEdit.setProperty(
			SplitPanePlugin.OPTION_PREFIX + "profile-" + profilename, plugin1 + "|" + plugin2);
		
		updateProfileList(profilename);
	}
	
	private void showEditProfileDialog(String name, String one, String two)
	{
		Component parent = getParent();
		
		while(!(parent instanceof JDialog))
		{
			parent = parent.getParent();
			if(parent == null) break;
		}
		
		final JDialog diag = new JDialog((JDialog)parent, "Enter the names of the 2 plugins that you want to include in this profile...", false);
		diag.setLayout(new BorderLayout());
		JPanel panel = new JPanel();
		JPanel button_panel = new JPanel();
		diag.add(panel, BorderLayout.CENTER);
		diag.add(button_panel, BorderLayout.SOUTH);
		
		panel.setLayout(new GridLayout(3,2));
		final JTextField profile_tf = new JTextField(name);
		panel.add(new JLabel("Profile Name"));
		panel.add(profile_tf);
		
		// Todo: make this a drop down?
		final JComboBox<String> plugin1_choice = new JComboBox();
		panel.add(new JLabel("Plugin One"));
		panel.add(plugin1_choice);
		
		// Todo: make this a drop down?
		final JComboBox<String> plugin2_choice = new JComboBox();
		panel.add(new JLabel("Plugin Two"));
		panel.add(plugin2_choice);
		
		ArrayList<String> dockables = new ArrayList<String>();
		for (String dockable : DockableWindowManager.getRegisteredDockableWindows())
		{
			dockables.add(dockable);
		}
		Collections.sort(dockables, new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {              
				return o1.compareToIgnoreCase(o2);
			}
		});
		for (String dockable : dockables)
		{
			plugin1_choice.addItem(dockable);
			plugin2_choice.addItem(dockable);
		}
		plugin1_choice.setSelectedItem(one);
		plugin2_choice.setSelectedItem(two);
		
		button_panel.setLayout(new FlowLayout());
		JButton ok = new JButton("Ok");
		ok.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				String name = profile_tf.getText().trim();
				String plugin1 = (String)plugin1_choice.getSelectedItem();
				String plugin2 = (String)plugin2_choice.getSelectedItem();
				
				if(!name.equals("") && !plugin1.equals("") && !plugin2.equals(""))
				{
					addProfile(name, plugin1, plugin2);
				}
				
				diag.setVisible(false);
			}
		});
		button_panel.add(ok);
		
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				diag.setVisible(false);
			}
		});
		button_panel.add(cancel);
		
		diag.pack();
		diag.setVisible(true);
	}
	
	public void _init()
	{
		add = new JButton(jEdit.getProperty(
			SplitPanePlugin.OPTION_PREFIX + "add.title"));
		addComponent("", add);
		add.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				showEditProfileDialog("", "", "");
			}
		});
		
		profile = new JComboBox();
		
		updateProfileList(null);
		
		addComponent(jEdit.getProperty(
			SplitPanePlugin.OPTION_PREFIX + "choose-profile.title"),
			profile);
		
		edit = new JButton(jEdit.getProperty(
			SplitPanePlugin.OPTION_PREFIX + "edit.title"));
		addComponent("", edit);
		edit.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				String profilename = (String)profile.getSelectedItem();
				
				String profile = jEdit.getProperty(SplitPanePlugin.OPTION_PREFIX + "profile-" + profilename);
				if(profile != null)
				{
					String[] names = profile.split("\\|");
					
					if(names.length == 2)
					{
						showEditProfileDialog(profilename, names[0], names[1]);
					}
				}
			}
		});
		
		remove = new JButton(jEdit.getProperty(
			SplitPanePlugin.OPTION_PREFIX + "remove.title"));
		addComponent("", remove);
		remove.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				removeProfile((String)profile.getSelectedItem());
			}
		});
	}

	public void _save()
	{
		
	}
}

