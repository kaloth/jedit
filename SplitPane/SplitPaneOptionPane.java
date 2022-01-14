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

import org.gjt.sp.jedit.*;

import org.gjt.sp.jedit.Macros.*;


public class SplitPaneOptionPane extends AbstractOptionPane
{
	private JButton add;
	private JButton remove;
	private JComboBox<String> profile;
	
	public SplitPaneOptionPane()
	{
		super(SplitPanePlugin.NAME);
	}

	private void updateProfileList()
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
		
		updateProfileList();
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
		
		updateProfileList();
	}
	
	private void showNewProfileDialog()
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
		final JTextField profile_tf = new JTextField();
		panel.add(new JLabel("Profile Name"));
		panel.add(profile_tf);
		
		// Todo: make this a drop down?
		final JTextField plugin1_tf = new JTextField();
		panel.add(new JLabel("Plugin One"));
		panel.add(plugin1_tf);
		
		// Todo: make this a drop down?
		final JTextField plugin2_tf = new JTextField();
		panel.add(new JLabel("Plugin Two"));
		panel.add(plugin2_tf);
		
		button_panel.setLayout(new FlowLayout());
		JButton ok = new JButton("Ok");
		ok.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				String name = profile_tf.getText().trim();
				String plugin1 = plugin1_tf.getText().trim();
				String plugin2 = plugin2_tf.getText().trim();
				
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
				showNewProfileDialog();
			}
		});
		
		profile = new JComboBox();
		
		updateProfileList();
		
		addComponent(jEdit.getProperty(
			SplitPanePlugin.OPTION_PREFIX + "choose-profile.title"),
			profile);
		
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

