package com.SIMRacingApps.SIMPlugins.iRacing;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.Server;
import com.SIMRacingApps.Util.State;
import com.owlike.genson.Genson;
/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2020 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */

public class SessionInfo {

    private static final Genson genson = new Genson();
    private static final Yaml_parser yaml = new Yaml_parser();
    private static boolean m_save_rawsessionstring = Server.getArg("rawsessionstring", false);
    private static double m_totalTime = 0.0;
    private static double m_count = 0.0;
    private static double m_maxTime = 0.0;
    
    byte m_buffer[] = null;
    Object m_data = null;
    String m_rawsessionstring = null;
//    String m_sessionstring = null;

    public SessionInfo (Header header, ByteBuffer bytebuffer) {
        m_buffer = new byte[bytebuffer.capacity()];
        bytebuffer.get(m_buffer);    //copy it
    }

//  public String getRawInfoString() { getData(); return m_rawsessionstring; }
    public Object getData() {

        if (m_data == null && m_buffer != null) {
            if (m_save_rawsessionstring)
                m_rawsessionstring = new String(m_buffer);            //convert it to string

            State s = new State("Parsing",System.currentTimeMillis() / 1000.0);
            
            m_data = yaml.load(m_buffer);

            if (Server.isLogLevelFinest()) {
                double time = s.getTime(System.currentTimeMillis() / 1000.0);
                m_totalTime += time;
                m_count++;
                m_maxTime = Math.max(m_maxTime, time);
                Server.logger().finest(String.format("%s: time = %f, max = %f, avg = %f, count = %.0f"
                        , "Yaml_parser"
                        , time
                        , m_maxTime
                        , m_totalTime / m_count
                        , m_count
                ));
            }
            m_buffer = null;    //free this memory, we don't need it
        }

        return m_data;
    }

    //use this if you only want to query the data if it has been parsed.
    //parsing is delayed until thing needs it.
    public boolean isDataParsed() { return m_data != null; }
    
//    public String toString() { getData(); return m_sessionstring; }

    @SuppressWarnings("unchecked")
    public Object getObject(String ... args) {
        Object d = getData();
        Object o = d;
        if (o != null) {
	        for(Object arg : args) {
	            if (!arg.toString().equals("")) {  //just ignore the blank ones.
	                if (d instanceof ArrayList) {
	                    try {
		                    int index = Integer.parseInt(arg.toString());
		                    ArrayList<Map<String,Object>> a = (ArrayList<Map<String,Object>>)d;
	                        o = a.get(index);
	                    }
	                    catch(NumberFormatException e) {
	                    	o = null;
	                    }
	                    catch(IndexOutOfBoundsException e) {
	                        o = null;
	                    }
	                }
	                else
	                if (d instanceof Map){
	                    if (((Map<String,Object>)d).containsKey(arg.toString()))
	                        o = ((Map<String,Object>)d).get(arg.toString());
	                    else
	                        o = null;
	                }
	                if (o == null) break;
	                if (o instanceof Map) {
	                    d = o;
	                }
	                else
	                if (o instanceof ArrayList) {
	                    d = o;
	                }
	                else {
	                    //System.err.printf("%s: object is %s%n", this.getClass().getName(),o.getClass().getName());
	                }
	            }
	        }
        }
        return o;
    }

    public String getString(Object p_o) {
        Object o = p_o;

        if (o != null) {

            if (o instanceof ArrayList || o instanceof Map) {
                o = genson.serialize(o);
            }
        }
        else
            o = "";

        return o.toString();
    }

    public String getString(String ... args) {
        return getString(getObject(args));
    }
    
    public int getInteger(Object p_o) {
        String s = getString(p_o);
        if (!s.isEmpty())
            return Integer.parseInt(s);
        return -1;
    }
    
    public int getInteger(String ... args) {
        return getInteger(getObject(args));
    }

    public boolean getBoolean(Object p_o) {
        String s = getString(p_o);
        if (!s.isEmpty())
            return new Data("",s).getBoolean();
        return false;
    }
    
    public boolean getBoolean(String ... args) {
        return getBoolean(getObject(args));
    }
}
