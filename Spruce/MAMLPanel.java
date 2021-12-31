import MAML.system.*;
import MAML.display.*;
import MAML.data.*;
import MAML.math.*;

import java.io.*;
import javax.imageio.*;
import java.awt.image.*;
import javax.swing.*;
import java.awt.*;
import javax.swing.event.*;
import java.awt.event.*;

public class MAMLPanel extends Panel{
	private static int WIDTH = 320, HEIGHT = 240;
	
	private Graphics display;
	private MAMLparser par;
	private Body body;
	private Image buf;
	private Graphics bufGraph;
	private Rectangle bounds;
	private float dx, oldx, dy, oldy;
	private Vertice vrot = new Vertice(0,0,0), vtrans = new Vertice(0,0,0);
	private boolean rotate = false, translate = false, fullscreen = false;
	private long start;
	private long end;
	private long frame;
	private long sleep = 0;
	private static long target = 1000 / 30;
	private static Vertice origin = new Vertice(0,0,0);
	private Robot robot;
	private MAMLToolkit toolkit;
	
	private Panel me = this;
	
	private boolean forward, backward, left, right;
	
	public MAMLPanel(){
		MouseList ml = new MouseList();
		KeyPressList kpl = new KeyPressList();		

		display = getGraphics();
		
		addMouseMotionListener(ml);
		addMouseListener(ml);
		addKeyListener(kpl);
		
		setBackground(Color.BLACK);
		
		toolkit = new MAMLLocalToolkit(this);
	}
	
	public void destroy()
	{
		synchronized(me){
			body.destroy();
			body = null;
		}
	}
	
	public void setDocumentBase(String url)
	{
		toolkit.setDocumentBase(url);
	}
	
	public void displayMAMLDocument(String file)
	{
		synchronized(me){
			if(buf == null)
			{
				buf = createImage(WIDTH,HEIGHT);
				
				bufGraph = buf.getGraphics();
				bounds = new Rectangle(0,0,WIDTH,HEIGHT);
				
				bufGraph.setColor(Color.BLACK);
				bufGraph.fillRect(0,0,WIDTH,HEIGHT);
				
				(new DrawThread()).start();
				(new MovementThread()).start();
			}
			
			if(body != null)
			{
				body.destroy();
				body = null;
			}
			
			par = new MAMLparser(file, toolkit);
			body = par.getBody();
		}
	}
	
	private void save(){
		// Save the graph as a png file...
		try{
			BufferedImage temp = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
			temp.getGraphics().drawImage(buf,0,0,null);
			ImageIO.write(temp, "png", new File("shot.png"));
		}catch(IOException e){
			System.err.println("Could not save image to png file.");
			return;
		}
	}
	
	private class DrawThread extends Thread{
		public void run(){
			try{
				Dimension d;
				Graphics g;
				g = getGraphics();
				
				while(true){
					synchronized(me){
						if(body == null)
						{
							sleep(500);
							continue;
						}
						
						d = getSize();
						
						if(translate || rotate){
							body.transformCamera(
									Matrix.prepareTransformationMatrix(	vtrans,
														vrot)
								);
								
							if(translate){
								translate = false;
								vtrans = new Vertice(0,0,0);
							}
							
							if(rotate){
								rotate = false;
								vrot = new Vertice(0,0,0);
							}
						}
						
						start = System.currentTimeMillis();
	
						body.renderFrame(bufGraph, bounds);
						int x = (d.width >> 1) - (bounds.width >> 1);
						int y = (d.height >> 1) - (bounds.height >> 1);
						if(buf != null) g.drawImage(buf, x, y, bounds.width, bounds.height, null);
						
						end = System.currentTimeMillis();
						frame = end - start;
			
						if (frame < target)
						{
							sleep = (sleep + target - frame) >> 1;
			
							try
							{
			
								Thread.currentThread().sleep(sleep);
							}
							catch (Exception e)
							{
								e.printStackTrace();
							}
						}
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	
	private class MouseList extends MouseAdapter
				implements MouseMotionListener{
		
		public void mousePressed(MouseEvent e){
			oldx = e.getX();
			oldy = e.getY();
		}
		
		public void mouseDragged(MouseEvent e){
			if(fullscreen){
				if(!rotate){
					dx = e.getX();
					dy = e.getY();
					oldx = 100;
					oldy = 100;
		
					if (dx != -1 || dy != -1) {
		
						float xscale = (float)(Math.PI / 500);
						float yscale = (float)(Math.PI / 500);
						vrot.y +=  (dx - oldx) * xscale;
						vrot.x -=  (dy - oldy) * yscale;
						oldx = dx;
						oldy = dy;
						
						rotate = true;
					}
					
					robot.mouseMove(100,100);
				}
			}else{
				if(!rotate){
					dx = e.getX();
					dy = e.getY();
		
					if (dx != -1 || dy != -1) {
		
						float xscale = (float)(Math.PI / 500);
						float yscale = (float)(Math.PI / 500);
						vrot.y +=  (dx - oldx) * xscale;
						vrot.x -=  (dy - oldy) * yscale;
						oldx = dx;
						oldy = dy;
						
						rotate = true;
					}
				}
			}
		}
		
		public void mouseMoved(MouseEvent e){
			if(!fullscreen) return;
			
			if(!rotate){
				dx = e.getX();
				dy = e.getY();
				oldx = 100;
				oldy = 100;
	
				if (dx != -1 || dy != -1) {
	
					float xscale = (float)(Math.PI / 500);
					float yscale = (float)(Math.PI / 500);
					vrot.y +=  (dx - oldx) * xscale;
					vrot.x -=  (dy - oldy) * yscale;
					oldx = dx;
					oldy = dy;
					
					rotate = true;
				}
			}
			
			robot.mouseMove(100,100);
		}
	}
	
	private class KeyPressList
		extends KeyAdapter {

		public void keyPressed(KeyEvent e) {
			switch (e.getKeyCode()) {
				case KeyEvent.VK_W :                                                   
				 {
					 forward = true;
					 break;
				 }
				 
				case KeyEvent.VK_S :
				 {
					 backward = true;
					 break;
				 }
				 
				case KeyEvent.VK_A :
				 {
					 left = true;
					 break;
				 }
				 
				case KeyEvent.VK_D :
				 {
					 right = true;
					 break;
				 }
				 
				case KeyEvent.VK_R :
				 {
					 save();
					 break;
				 }
				 
				case KeyEvent.VK_ESCAPE :
				 {
					 System.exit(0);
					 break;
				 }
				 
			}
		}
		
		public void keyReleased(KeyEvent e) {
			switch (e.getKeyCode()) {

				case KeyEvent.VK_W :                                                   
				 {
					 forward = false;
					 break;
				 }
				 
				case KeyEvent.VK_S :
				 {
					 backward = false;
					 break;
				 }
				 
				case KeyEvent.VK_A :
				 {
					 left = false;
					 break;
				 }
				 
				case KeyEvent.VK_D :
				 {
					 right = false;
					 break;
				 }
			}
		}
	}
	
	private class MovementThread extends Thread{
		public void run(){
			try{
				while(true){
					if(!translate)
					{
						if(forward)
						{
							vtrans.z = 10;
							translate = true;
						}
						else if(backward)
						{
							vtrans.z = -10;
							translate = true;
						}
						 
						if(left)
						{
							vtrans.x = -10;
							translate = true;
						}
						else if(right)
						{
							vtrans.x = +10;
							translate = true;
						}
					}
					
					sleep(20);
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}
}
