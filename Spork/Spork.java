/*
 * Spork.java
 * part of the Spork plugin for the jEdit text editor
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


public class Spork extends JPanel implements EBComponent, SporkActions, DefaultFocusComponent, CaretListener, FocusListener
{
	public static final int DEPTH = 500;
	
	private View view;
	private boolean floating;
	
	private Hashtable jtxthsh = new Hashtable();

	private JLabel label = new JLabel(" -- SPORK -- ");
	
	private boolean ignore = false;
	
	private Location currentLocation = null;

	//
	// Constructor
	//

	public Spork(View view, String position)
	{
		super(new BorderLayout());
		
		this.view = view;
		this.floating  = position.equals(DockableWindowManager.FLOATING);
		
		buildGUI();
		
		listenToTextArea(jEdit.getActiveView().getTextArea());
		
		storeLocation();
	}
	
	public void start(){
		listenToTextArea(jEdit.getActiveView().getTextArea());
		
		storeLocation();
	}
	
	public void stop(){
		// Remove all our listeners...
		Enumeration e = jtxthsh.keys();
		while(e.hasMoreElements()){
			JEditTextArea jtxt = (JEditTextArea)e.nextElement();
			jtxt.removeCaretListener(this);
			jtxt.removeFocusListener(this);
		}
		jtxthsh.clear();
	}
	
	public void focusOnDefaultComponent()
	{
		
	}
	
	public void caretUpdate(CaretEvent e){
		storeLocation();
	}
	
	public void focusGained(FocusEvent e){
		storeLocation();
	}
	
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
				listenToTextArea(jEdit.getActiveView().getTextArea());
			}
			else if (bu.getWhat() == BufferUpdate.CLOSED) {
				synchronized(this)
				{
					JEditTextArea jtxt = jEdit.getActiveView().getTextArea();
					SporkKnuckle sp = (SporkKnuckle)jtxthsh.get(jtxt);
					Stack forward = sp.forward;
					Stack backward = sp.backward;
					
					Enumeration e = forward.elements();
					while(e.hasMoreElements()){
						Location l = (Location)e.nextElement();
						
						if(l.buffer.equals(buffer)){
							forward.remove(l);
						}
					}
					
					e = backward.elements();
					while(e.hasMoreElements()){
						Location l = (Location)e.nextElement();
						
						if(l.buffer.equals(buffer)){
							backward.remove(l);
						}
					}
				}
			}
			else if (bu.getWhat() == BufferUpdate.DIRTY_CHANGED) {
				
			}
			else if (bu.getWhat() == BufferUpdate.LOADED) {
				listenToTextArea(jEdit.getActiveView().getTextArea());
			}
			else if (bu.getWhat() == BufferUpdate.SAVED) {
				
			}
        } else if (msg instanceof EditPaneUpdate) {
            EditPaneUpdate epu = (EditPaneUpdate) msg;
            if (epu.getWhat() == EditPaneUpdate.BUFFER_CHANGED) {
				listenToTextArea(jEdit.getActiveView().getTextArea());
            }
        } else if (msg instanceof PropertiesChanged) {
			propertiesChanged();
		} else if (msg instanceof ViewUpdate) {
			ViewUpdate vu = (ViewUpdate) msg;
			View v = vu.getView();
			Object what = vu.getWhat();
			if (what == vu.CREATED || what == vu.EDIT_PANE_CHANGED)
				listenToTextArea(v.getTextArea());
			else if (what.equals(ViewUpdate.CLOSED))
				ignoreTextArea(v.getTextArea());
		}
    }

	private void listenToTextArea(JEditTextArea jtxt){
		if(jtxthsh.get(jtxt) == null){
			jtxt.removeCaretListener(this);
			jtxt.removeFocusListener(this);
			
			jtxt.addCaretListener(this);
			jtxt.addFocusListener(this);
			
			jtxthsh.put(jtxt, new SporkKnuckle());
		}
	}
	
	private void ignoreTextArea(JEditTextArea jtxt){
		if(jtxthsh.get(jtxt) != null){
			jtxt.removeCaretListener(this);
			jtxt.removeFocusListener(this);
			
			jtxthsh.remove(jtxt);
		}
	}

	private void propertiesChanged()
	{
		
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
	// SporkActions implementation
	//
	
	public synchronized void forward(){
		JEditTextArea jtxt = jEdit.getActiveView().getTextArea();
		SporkKnuckle sp = (SporkKnuckle)jtxthsh.get(jtxt);
		Stack forward = sp.forward;
		Stack backward = sp.backward;
		
		if(forward.size() == 0) return;
		
		Location l = (Location)forward.pop();
		backward.push(l);
		
		while(l.equals(currentLocation))
		{
			if(forward.size() == 0) return;
			
			l = (Location)forward.pop();
			backward.push(l);
		}
		
		setLocation(l);
		
		label.setText(backward.size() + " 4 " + l.toString());
	}
	
	public synchronized void backward(){
		JEditTextArea jtxt = jEdit.getActiveView().getTextArea();
		SporkKnuckle sp = (SporkKnuckle)jtxthsh.get(jtxt);
		Stack forward = sp.forward;
		Stack backward = sp.backward;
		
		if(backward.size() == 0) return;
		
		Location l = (Location)backward.pop();
		forward.push(l);
		
		while(l.equals(currentLocation))
		{
			if(backward.size() == 0) return;
			
			l = (Location)backward.pop();
			forward.push(l);
		}
		
		setLocation(l);
		
		label.setText(backward.size() + " 3 " + l.toString());
	}
	
	//
	// Private Functions...
	//
	
	private synchronized void setLocation(final Location l)
	{
		final Spork sp = this;
		currentLocation = l;
		
		SwingUtilities.invokeLater(new Thread(){
			public void run(){
				synchronized(sp){
					ignore = true;
					jEdit.getActiveView().setBuffer(l.buffer);
					
					jEdit.getActiveView().getTextArea().setCaretPosition(l.pos);
					ignore = false;
				}
			}
		});
	}
	
	private synchronized void storeLocation(){
		if(ignore) return;
		
		JEditTextArea jtxt = jEdit.getActiveView().getTextArea();
		SporkKnuckle sp = (SporkKnuckle)jtxthsh.get(jtxt);
		Stack forward = sp.forward;
		Stack backward = sp.backward;
		
		forward.clear();
		
		Location l = new Location(jEdit.getActiveView().getBuffer(), jtxt.getCaretPosition());
		
		if(jtxt.getSelection() != null)
		{
			if(jtxt.getSelection().length > 0)
			{
				return;
			}
		}
		
		if(backward.size() != 0)
		{
			Location prev = (Location)backward.peek();
			if(!prev.equals(l))
			{
				if(!prev.near(l))
				{
					backward.push(l);
					label.setText(backward.size() + " 1 " + l.toString());
				}
			}
		}
		else
		{
			backward.push(l);
			label.setText(backward.size() + " 2 " + l.toString());
		}
		
		currentLocation = l;
	}
	
	public void buildGUI(){
		if(floating){
			add(label, BorderLayout.NORTH);
			doLayout();
		}else{
			add(label, BorderLayout.NORTH);
			doLayout();
		}
	}
	
	private class Location{
		public Buffer buffer;
		public int pos;
		
		public Location(Buffer buffer, int pos)
		{
			this.buffer = buffer;
			this.pos = pos;
		}
		
		public boolean equals(Location l)
		{
			if(l != null)
			{
				return l.toString().equals(toString());
			}else{
				return false;
			}
		}
		
		public String toString(){
			return "[" + pos + "] " + "\"" + buffer.getPath() + "\"";
		}
		
		public boolean near(Location l){
			
			if(l != null)
			{
				if(buffer.equals(l.buffer) && Math.abs(pos - l.pos) < 128)
				{
					return true;
				}
			}
			
			return false;
		}
	}
	
	private class SporkKnuckle{
		public Stack forward = new Stack();
		public Stack backward = new Stack();
		
		public SporkKnuckle(){
			
		}
	}
}

