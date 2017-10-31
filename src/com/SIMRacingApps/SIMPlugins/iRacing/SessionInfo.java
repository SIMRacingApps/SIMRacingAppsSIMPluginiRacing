package com.SIMRacingApps.SIMPlugins.iRacing;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.yaml.snakeyaml.*;

import com.owlike.genson.Genson;
/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2017 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */

public class SessionInfo {

    private static final Genson genson = new Genson();
    private static final Yaml yaml = new Yaml();

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

//The parser does not like characters that are not within the ASCII range
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
            
            //If cameras have been customized, they sometimes contain invalid characters to yaml
            m_sessionstring = m_sessionstring.replaceAll("GroupName: (.*)", "GroupName: \"$1\"");

/* Testing the new Setup string David is going to be putting in here */
/**
m_data = yaml.load("---\n"
+ "CarSetup:\n"
+ " TiresAero:\n"
+ "  LeftFront:\n"
+ "   ColdPressure: 0 kPa\n"
+ "   LastHotPressure: 0 kPa\n"
+ "   LastTempsOMI: 0C, 0C, 0C\n"
+ "   TreadRemaining: 0%, 0%, 0%\n"
+ "  LeftRear:\n"
+ "   ColdPressure: 0 kPa\n"
+ "   LastHotPressure: 0 kPa\n"
+ "   LastTempsOMI: 0C, 0C, 0C\n"
+ "   TreadRemaining: 0%, 0%, 0%\n"
+ "  RightFront:\n"
+ "   ColdPressure: 0 kPa\n"
+ "   LastHotPressure: 0 kPa\n"
+ "   LastTempsIMO: 0C, 0C, 0C\n"
+ "   TreadRemaining: 0%, 0%, 0%\n"
+ "  RightRear:\n"
+ "   ColdPressure: 0 kPa\n"
+ "   LastHotPressure: 0 kPa\n"
+ "   LastTempsIMO: 0C, 0C, 0C\n"
+ "   TreadRemaining: 0%, 0%, 0%\n"
+ "  FrontAero:\n"
+ "   WingAngle: 0.00 deg\n"
+ "   WingWicker: 0 out of range\n"
+ "   UpperFlapAngle: 0 out of range\n"
+ "   UpperFlapWicker: 0 out of range\n"
+ "  UndersideAero:\n"
+ "   DiffuserExitWicker: No wicker\n"
+ "   DiffuserAddOns: 0 out of range\n"
+ "  RearAero:\n"
+ "   WingAngle: 0.0 deg\n"
+ "   WingWicker: No wicker\n" 
+ "   EndPlateWicker: Not available\n" 
+ "   BeamWicker: No wicker\n"
+ "   RadiatorInlet: OPEN\n"
+ "  AeroCalculator:\n"
+ "   FrontRhAtSpeed: 0.0 mm\n"
+ "   RearRhAtSpeed: 0.0 mm\n"
+ "   FrontDownforce: 0.00%\n"
+ "   DownforceToDrag: 0.000:1\n"
+ " Chassis:\n"
+ "  General:\n"
+ "   Wheelbase: 118\"\n"
+ "   BrakePressure: Low\n"
+ "   BrakePressureBias: 0.0%\n"
+ "   SteeringPinion: 0 tooth\n"
+ "   SteeringOffset: +0 deg\n"
+ "   BallastForward: 0 mm\n"
+ "   NoseWeight: 0.0%\n"
+ "   CrossWeight: 0 N to the left front\n"
+ "  LeftFront:\n"
+ "   CornerWeight: 0 N\n"
+ "   RideHeight: 0.0 mm\n"
+ "   PushrodLength: 0.0 mm\n"
+ "   SpringRate: 0 N/mm\n"
+ "   Camber: +0.00 deg\n"
+ "   Caster: +0.00 deg\n"
+ "   ToeIn: +0 mm\n"
+ "  LeftRear:\n"
+ "   CornerWeight: 0 N\n"
+ "   RideHeight: 0.0 mm\n"
+ "   PushrodLength: 0.0 mm\n"
+ "   SpringRate: 0 N/mm\n"
+ "   Camber: +0.00 deg\n"
+ "   ToeIn: +0 mm\n"
+ "  Rear:\n"
+ "   FuelLevel: 0.0 L\n"
+ "   RdSpring: None\n"
+ "   RdSpringGap: 0.0 mm\n"
+ "   WeightJacker: 0\n"
+ "  Front:\n"
+ "   RdSpring: None\n"
+ "   RdSpringGap: 0.0 mm\n"
+ "   BarDiameter: None\n"
+ "   BarBlades: Ti\n"
+ "   BarBladePosition: 0\n"
+ "   DropLinkPosition: Wide (Slow)\n"
+ "   ArbPreload: 0.0 Nm\n"
+ "  RightFront:\n"
+ "   CornerWeight: 0 N\n"
+ "   RideHeight: 0.0 mm\n"
+ "   PushrodLength: 0.0 mm\n"
+ "   SpringRate: 0 N/mm\n"
+ "   Camber: +0.00 deg\n"
+ "   Caster: +0.00 deg\n"
+ "   ToeIn: +0 mm\n"
+ "  RightRear:\n"
+ "   CornerWeight: 0 N\n"
+ "   PushrodLength: 0.0 mm\n"
+ "   SpringRate: 0 N/mm\n"
+ "   Camber: +0.00 deg\n"
+ "   ToeIn: +0 mm\n"
+ "  RearArb:\n"
+ "   ArbDiameter: None\n"
+ "   ArbDropLinkPosition: Wide (Slow)\n"
+ "   ArbBlades: 0\n"
+ "   ArbPreload: 0.0 Nm\n"
+ " Dampers:\n"
+ "  LeftFrontDamper:\n"
+ "   LowSpeedComp: 0 clicks\n"
+ "   HighSpeedComp: 0 clicks\n"
+ "   LowSpeedRebound: 0 clicks\n"
+ "   HighSpeedRebound: 0 clicks\n"
+ "  LeftRearDamper:\n"
+ "   LowSpeedComp: 0 clicks\n"
+ "   HighSpeedComp: 0 clicks\n"
+ "   LowSpeedRebound: 0 clicks\n"
+ "   HighSpeedRebound: 0 clicks\n"
+ "  RightFrontDamper:\n"
+ "   LowSpeedComp: 0 clicks\n"
+ "   HighSpeedComp: 0 clicks\n"
+ "   LowSpeedRebound: 0 clicks\n"
+ "   HighSpeedRebound: 0 clicks\n"
+ "  RightRearDamper:\n"
+ "   LowSpeedComp: 0 clicks\n"
+ "   HighSpeedComp: 0 clicks\n"
+ "   LowSpeedRebound: 0 clicks\n"
+ "   HighSpeedRebound: 0 clicks\n"
+ " Drivetrain:\n"
+ "  Engine:\n"
+ "   EngineMapSetting: 0 (MAP)\n"
+ "   TurboBoostPressure: 0 out of range\n"
+ "  Gearbox:\n"
+ "   FirstGear: 12/35, 0.0 Km/h\n"
+ "   SecondGear: 16/33, 0.0 Km/h\n"
+ "   ThirdGear: 16/33, 0.0 Km/h\n"
+ "   FourthGear: 16/33, 0.0 Km/h\n"
+ "   FifthGear: 16/33, 0.0 Km/h\n"
+ "   SixthGear: 16/33, 0.0 Km/h\n"
+ "   FinalDrive: 17/59\n"
+ "  Differential_RcOnly:\n"
+ "   ClutchPlates: 0\n"
+ "   Preload: 0 Nm\n"
+ "   RampAngles: 0 out of range\n"
+ "...");            
/**/
            m_data = yaml.load(m_sessionstring);
            if (m_data == null) {
            	m_data = new HashMap<String,Object>();
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
        else
            o = "";

        return o.toString();
    }

    public String getString(String ... args) {
        return getString(getObject(args));
    }
}
