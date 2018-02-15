package com.SIMRacingApps.SIMPlugins.iRacing;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

//import org.yaml.snakeyaml.*;




import org.yaml.snakeyaml.Yaml;

import com.SIMRacingApps.Server;
import com.SIMRacingApps.Util.State;
import com.owlike.genson.Genson;
/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2017 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */

public class SessionInfo {

    private static final Genson genson = new Genson();
    private static final Yaml snakeYaml = new Yaml();
    private static final Yaml_parser yaml = new Yaml_parser();
    private static boolean m_useSnakeYaml = Server.getArg("snake-yaml", false);
    private static double m_totalTime = 0.0;
    private static double m_count = 0.0;
    private static double m_maxTime = 0.0;
    
    byte m_buffer[] = null;
    Object m_data = null;
//    String m_rawsessionstring = null;
    String m_sessionstring = null;

    public SessionInfo (Header header, ByteBuffer bytebuffer) {
        m_buffer = new byte[bytebuffer.capacity()];
        bytebuffer.get(m_buffer);    //copy it
    }

//  public String getRawInfoString() { getData(); return m_rawsessionstring; }
    public Object getData() {

        if (m_data == null && m_buffer != null) {
//            m_rawsessionstring = new String(m_buffer);            //convert it to string

            State s = new State("Parsing",System.currentTimeMillis() / 1000.0);
            
if (m_useSnakeYaml) {
//The Snake YAML parser does not like characters that are not within the ASCII range
//decided to convert to basic hex value with \x, then in the getData() method convert it back to a char.
//I chose this because the iRacing data is all bytes not double bytes. It would take double bytes to support 4 digit unicode sequences.
//This will support java clients running on the Windows platform which has direct access to the SIMulator data.
//for remote clients, the Servlet can choose to encode these to UTF-8 Unicode escapes if needed.

//            m_sessionstring = m_buffer.toString().replaceAll("[\000\200-\377]", "");    //clean up the data for the yaml parser
            StringBuffer out = new StringBuffer();
            for(int i=0; i < m_buffer.length; i++)
            {
                char c = (char) m_buffer[i];
                if(c > 127)
                {
//                    out.append("&#");
//                    out.append((int)c & 0xFF);
//                    out.append(";");
                    out.append(String.format("\\x%02x",(int)c & 0xFF));
                }
                else
                if(c != 0)
                {
                    out.append(c);
                }
            }
            m_sessionstring = out.toString();

            //try and protect car numbers with leading zeros. The parser strips them thinking there either an integer or octal string
            //if iRacing already has them quoted, then remove the double quotes
            m_sessionstring = m_sessionstring.replaceAll("CarNumber: ([0].*)", "CarNumber: \"$1\"");

            //some team names have a colon in them causing the parser to get confused. This adds quotes around them until iRacing fixes it.
            //if iRacing already has them quoted, then remove the double quotes
            m_sessionstring = m_sessionstring.replaceAll("TeamName: (.*)", "TeamName: \"$1\"");
            
            //some user names have invalid characters in leagues races. This adds quotes around them until iRacing fixes it.
            //if iRacing already has them quoted, then remove the double quotes
            m_sessionstring = m_sessionstring.replaceAll("UserName: (.*)", "UserName: \"$1\"");
            m_sessionstring = m_sessionstring.replaceAll("Initials: (.*)", "Initials: \"$1\"");
            m_sessionstring = m_sessionstring.replaceAll("AbbrevName: (.*)", "AbbrevName: \"$1\"");
            
            //If cameras have been customized, they sometimes contain invalid characters to yaml
            m_sessionstring = m_sessionstring.replaceAll("GroupName: (.*)", "GroupName: \"$1\"");

            m_data = snakeYaml.load(m_sessionstring);
            if (m_data == null) {
            	m_data = new HashMap<String,Object>();
            }
}
else {
            m_data = yaml.load(m_buffer);
}
            if (Server.isLogLevelFinest()) {
                double time = s.getTime(System.currentTimeMillis() / 1000.0);
                m_totalTime += time;
                m_count++;
                m_maxTime = Math.max(m_maxTime, time);
                Server.logger().finest(String.format("%s: time = %f, max = %f, avg = %f, count = %.0f"
                        , m_useSnakeYaml ? "SnakeYaml" : "Yaml_parser"
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

    public String toString() { getData(); return m_sessionstring; }

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
if (m_useSnakeYaml) {            
            //convert the hex codes back to their characters
            if (o.toString().contains("\\x")) {
                StringBuffer s = new StringBuffer();
                String os = o.toString();
                int position = 0;
                int index = os.indexOf("\\x",position);
                while (index > -1) {
                    s.append(os.substring(position, index));
                    s.append((char)Integer.parseInt(os.substring(index+2,index+4),16));
                    position = index + 4;
                    index = os.indexOf("\\x",position);
                }
                s.append(os.substring(position));
                o = s;
            }
}
        }
        else
            o = "";

        return o.toString();
    }

    public String getString(String ... args) {
        return getString(getObject(args));
    }
}
