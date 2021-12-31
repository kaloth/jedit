/*
 * Spruce.java
 * part of the Spruce plugin for the jEdit text editor
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
import org.gjt.sp.jedit.io.*;
import org.gjt.sp.jedit.msg.PropertiesChanged;
import org.gjt.sp.jedit.msg.ViewUpdate;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.msg.EditPaneUpdate;
import org.gjt.sp.util.Log;

import org.gjt.sp.jedit.search.*;
import org.gjt.sp.jedit.textarea.*;

import org.gjt.sp.jedit.Macros.*;

public class Spruce extends JPanel implements EBComponent, SpruceActions, DefaultFocusComponent, FocusListener
{
	public static final int DEPTH = 500;
	
	private View view;
	private boolean floating;

	private JLabel label = new JLabel(" -- SPRUCE -- ");
	
	private MAMLPanel panel = new MAMLPanel();

	//
	// Constructor
	//

	public Spruce(View view, String position)
	{
		super(new BorderLayout());
		
		this.view = view;
		this.floating  = position.equals(DockableWindowManager.FLOATING);
		
		buildGUI();
		
		propertiesChanged();
	}
	
	public void start(){
		reload();
	}
	
	public void stop(){
		panel.destroy();
	}
	
	public void focusOnDefaultComponent()
	{
		panel.requestFocus();
	}
	
	public void focusGained(FocusEvent e){}
	
	public void focusLost(FocusEvent e){}
	
	/**
     * EditBus message handling.
     */
    public void handleMessage(EBMessage msg) {
        Buffer buffer = null;
        if (msg instanceof BufferUpdate) {

            BufferUpdate bu = (BufferUpdate) msg;
            buffer = bu.getBuffer();
			if (bu.getWhat() == BufferUpdate.CREATED) {
				
			}
			else if (bu.getWhat() == BufferUpdate.CLOSED) {
				
			}
			else if (bu.getWhat() == BufferUpdate.DIRTY_CHANGED) {
				
			}
			else if (bu.getWhat() == BufferUpdate.LOADED) {
				
			}
			else if (bu.getWhat() == BufferUpdate.SAVED) {
				reload();
			}
        } else if (msg instanceof EditPaneUpdate) {
            EditPaneUpdate epu = (EditPaneUpdate) msg;
            if (epu.getWhat() == EditPaneUpdate.BUFFER_CHANGED) {
				reload();
            }
        } else if (msg instanceof PropertiesChanged) {
			propertiesChanged();
		} else if (msg instanceof ViewUpdate) {
			ViewUpdate vu = (ViewUpdate) msg;
			View v = vu.getView();
			Object what = vu.getWhat();
			if (what == vu.CREATED || what == vu.EDIT_PANE_CHANGED)
			{
				reload();
			}
		}
    }

	private void propertiesChanged()
	{
		String docroot = jEdit.getProperty(SprucePlugin.OPTION_PREFIX + "docroot");
		panel.setDocumentBase(docroot);
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
	// SpruceActions implementation
	//
	
	public void reload(){
		String path = jEdit.getActiveView().getBuffer().getPath().trim();
		if(path.endsWith(".maml"))
		{
			path = "file:///" + path;
			label.setText(path);
			
			// Run it in the swing execute list...
			final String url = path;
			SwingUtilities.invokeLater(new Thread(){
				public void run(){
					panel.displayMAMLDocument(url);
				}
			});
		}
	}
	
	//
	// Private Functions...
	//
	
	public void buildGUI(){
		if(floating){
			add(label, BorderLayout.NORTH);
			add(panel, BorderLayout.CENTER);
			doLayout();
		}else{
			add(label, BorderLayout.NORTH);
			add(panel, BorderLayout.CENTER);
			doLayout();
		}
	}
}

