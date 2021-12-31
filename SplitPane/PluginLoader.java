import org.gjt.sp.jedit.*;

import java.awt.*;
import java.net.*;
import java.util.*;
import java.lang.reflect.*;

import org.gjt.sp.jedit.Macros.*;

/**
 * PluginLoader - This is the class that loads node types from the defined jar
 * packages on a local disk as well as the built in node types.
 *
 * @author Christopher James Plant
 * @version 01/02/05
 */
public class PluginLoader{
	
	private PluginClassLoader classloader;
	private ClassLoader baseloader;
	
	/**
	 * Create a new PluginLoader
	 */
	public PluginLoader(){
		classloader = new PluginClassLoader();
		baseloader = getClass().getClassLoader();
	}
	
	/**
	 * Create a new instance of a named Component class.
	 * 
	 * @param className The 'binary name' of the class..
	 */
	public Component getInstanceOf(String className, View view, String position){
		Component inst = null;
		
		try{
			Class theClass = classloader.loadClass(className, true);
			Constructor constructor = null;
			
			if(theClass == null)
			{
				theClass = Class.forName(className, true, baseloader);
				if(theClass == null)
				{
					Macros.message(view, "ERROR: Can't find class called \"" + className + "\"");
					return null;
				}
			}
			
			try{
				constructor = theClass.getConstructor(new Class[]{view.getClass(), position.getClass()});
				if(constructor != null)
				{
					inst = (Component)constructor.newInstance(new Object[]{view, position});
				}
			}catch(NoSuchMethodException e){constructor = null;}
			
			if(constructor == null)
			{
				try{
					constructor = theClass.getConstructor(new Class[]{view.getClass()});
					
					if(constructor != null)
					{
						inst = (Component)constructor.newInstance(new Object[]{view});
					}
				}catch(NoSuchMethodException e){constructor = null;}
			}
			
			if(constructor == null)
			{
				try{
					constructor = theClass.getConstructor();
					
					if(constructor != null)
					{
						inst = (Component)constructor.newInstance();
					}
				}catch(NoSuchMethodException e){constructor = null;}
			}
			
			if(constructor == null)
			{
				Macros.message(view, "ERROR: Can't find a suitable constructor for \"" + className + "\"");
				return null;
			}
		}catch(Exception e){
			e.printStackTrace();
			Macros.message(view, "Loading \"" + className + "\" failed.\nReason: " + e.getMessage());
		}
		
		return inst;
	}
	
	private class PluginClassLoader extends ClassLoader{
		Hashtable cache = new Hashtable();
		
		public synchronized Class loadClass(String name, boolean resolve) {
			Class c = (Class)cache.get(name);
			if (c == null) {
				try{
					c = findSystemClass(name);
					cache.put(name, c);
				}catch(Exception e){
					return null;
				}
			}
			if(resolve) resolveClass(c);
			return c;
		}
	}
}
