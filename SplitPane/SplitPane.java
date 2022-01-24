/*
 * SplitPane.java
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

// from Java:
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

// from Swing:
import javax.swing.*;
import javax.swing.event.*;

// from jEdit:
import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.gui.*;
import org.gjt.sp.jedit.msg.*;

import org.gjt.sp.util.Log;


public class SplitPane extends JPanel implements SplitPaneActions, EBComponent
{
	private String name;
	private View view;
	private String position;
	private boolean floating;
	
	private PluginLoader pluginLoader;
	private JSplitPane jsp = new JSplitPane();
	
	private JComboBox<String> profile = new JComboBox();
	
	private Object[] plugins;
	private String[] names;
	
	public SplitPane(View view, String position, String name)
	{
		super(new BorderLayout());
		
		this.name = name;
		this.view = view;
		this.position = position;
		this.floating = position.equals(DockableWindowManager.FLOATING);
		
		pluginLoader = new PluginLoader(view, position);
		
		buildGUI();
		propertiesChanged();
	}
	
	public void start()
	{
		
	}
	
	public void stop()
	{
		
	}
	
	public Object getDockable(String name)
	{
		if(name.equals(names[0])) return plugins[0];
		if(name.equals(names[1])) return plugins[1];
		return null;
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
	
	/**
	* EditBus message handling.
	*/
	public void handleMessage(EBMessage msg) {
		if (msg instanceof PropertiesChanged) {
			propertiesChanged();
		}
	}

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
	
	private void updateProfileList()
	{
		String profilename = jEdit.getProperty(SplitPanePlugin.OPTION_PREFIX + this.name + "_current-profile");
		
		profile.removeAllItems();
		
		String profiles = jEdit.getProperty(
			SplitPanePlugin.OPTION_PREFIX + "profiles");
		
		LogMsg(Log.DEBUG, SplitPane.class,
				this.name + " Fetching Profile List: " + profiles);
		
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
		
		if(profilename != null)
		{
			LogMsg(Log.DEBUG, SplitPane.class,
				this.name + " Restoring Current Profile (updateProfileList): " + profilename);
			profile.setSelectedItem(profilename);
		}
		else if(profile.getItemCount() > 0)
		{
			// first time? Just load the first profile available.
			profilename = profile.getItemAt(0);
			profile.setSelectedItem(profilename);
		}
	}

	private void propertiesChanged()
	{
		updateProfileList();
	}
	
	private void loadProfile(String profilename)
	{
		LogMsg(Log.DEBUG, SplitPane.class,
				this.name + " Loading Profile: " + profilename);
		
		jEdit.setProperty(SplitPanePlugin.OPTION_PREFIX + this.name + "_current-profile", profilename);
		
		String profile = jEdit.getProperty(SplitPanePlugin.OPTION_PREFIX + "profile-" + profilename);
		if(profile != null)
		{
			names = profile.split("\\|");
			
			if(names.length == 2)
			{
				plugins = loadPlugins(names[0], names[1]);
			}
		}
	}
	
	private Object[] loadPlugins(String plug1, String plug2)
	{
		Component plugin1 = pluginLoader.getInstanceOf(plug1);
		Component plugin2 = pluginLoader.getInstanceOf(plug2);
		
		if(plugin1 == null)
		{
			plugin1 = new JLabel("Failed to load \"" + plug1 + "\".");
		}
		
		if(plugin2 == null)
		{
			plugin2 = new JLabel("Failed to load \"" + plug2 + "\".");
		}
		
		int loc = jsp.getDividerLocation();
		jsp.setLeftComponent(plugin1);
		jsp.setRightComponent(plugin2);
		jsp.setDividerLocation(loc);
		
		return new Object[]{plugin1, plugin2};
	}
	
	private void buildGUI(){
		if(floating){
			this.setPreferredSize(new Dimension(500, 500));
			
			add(profile, BorderLayout.NORTH);
			
			setSize(400, 400);
		}else{
			add(profile, BorderLayout.NORTH);
		}
		
		if(floating || position.equals(DockableWindowManager.RIGHT) || position.equals(DockableWindowManager.LEFT))
		{
			jsp = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		}
		else
		{
			jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		}
		
		jsp.setResizeWeight(0.5);
		add(jsp, BorderLayout.CENTER);
		
		doLayout();
		
		jsp.setDividerLocation(0.5);
		
		profile.addItemListener(new ItemListener()
		{
			public void itemStateChanged(ItemEvent e)
			{
				if(e.getStateChange() == e.SELECTED)
				{
					loadProfile((String)profile.getSelectedItem());
				}
			}
		});
	}
}

